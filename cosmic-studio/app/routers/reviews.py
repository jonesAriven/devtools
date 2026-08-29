"""评审修订域（编写库专属）：意见录入（行级）→ 手动修订 / 批次 AI 自动优化 → 每次修订自动出新版本。

铁律落点：
  - 每次（手动/AI）修订落地自动打版本快照，版本不可覆盖
  - 批次自动优化一轮=一个版本，changelog 记录意见→改动映射
  - 结构调整型意见 AI 无权直接改（标 needs_manual，出方案待人工）
  - AI 批次修订后跑门禁：新增 error 整批回滚
  - 使用时才调 LLM：未配置返回明确提示，功能建设与 key 解耦
"""
import json

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel

from .. import config, db
from ..auth import require_role
from ..engines import linter
from ..services import versioning

r = APIRouter(prefix="/api/active", tags=["reviews"])

ALLOWED_CLASSIFY = ("text_replace", "structure", "question")
ALLOWED_DISPOSITION = ("pending", "manual_done", "auto_done", "needs_manual", "wont_fix")

# AI 可改字段白名单（结构调整类操作不在其中）
FP_FIELDS = {"functional_user", "trigger_event", "fp_name"}
SUB_FIELDS = {"description", "data_move_type", "data_group_name", "data_attributes"}


class ReviewIn(BaseModel):
    target_type: str  # fp / sub / project
    target_id: int | None = None
    target_label: str = ""
    content: str
    classify: str = "text_replace"


class ReviewUpdate(BaseModel):
    content: str | None = None
    classify: str | None = None
    disposition: str | None = None
    revision_note: str | None = None


@r.get("/projects/{pid}/reviews")
def list_reviews(pid: int, user: dict = Depends(require_role("viewer"))):
    return db.query(config.DB_ACTIVE,
                    "SELECT * FROM review_items WHERE project_id=%s ORDER BY id DESC", (pid,))


@r.post("/projects/{pid}/reviews", status_code=201)
def create_review(pid: int, body: ReviewIn, user: dict = Depends(require_role("editor"))):
    if body.target_type not in ("fp", "sub", "project"):
        raise HTTPException(422, "target_type 必须是 fp/sub/project")
    if body.classify not in ALLOWED_CLASSIFY:
        raise HTTPException(422, f"classify 必须是 {'/'.join(ALLOWED_CLASSIFY)}")
    if not body.content.strip():
        raise HTTPException(422, "评审意见内容不能为空")
    rid = db.execute(config.DB_ACTIVE, """INSERT INTO review_items
        (project_id, target_type, target_id, target_label, content, classify)
        VALUES (%s,%s,%s,%s,%s,%s)""",
        (pid, body.target_type, body.target_id, body.target_label, body.content.strip(), body.classify))
    return {"id": rid}


@r.put("/reviews/{rid}")
def update_review(rid: int, body: ReviewUpdate, user: dict = Depends(require_role("editor"))):
    sets, params = [], []
    for col, v in (("content", body.content), ("classify", body.classify),
                   ("disposition", body.disposition), ("revision_note", body.revision_note)):
        if v is not None:
            if col == "classify" and v not in ALLOWED_CLASSIFY:
                raise HTTPException(422, "非法分类")
            if col == "disposition" and v not in ALLOWED_DISPOSITION:
                raise HTTPException(422, "非法处置状态")
            sets.append(f"{col}=%s")
            params.append(v)
    if not sets:
        raise HTTPException(422, "无更新字段")
    params.append(rid)
    db.execute(config.DB_ACTIVE, f"UPDATE review_items SET {', '.join(sets)} WHERE id=%s", tuple(params))
    return {"updated": True}


@r.delete("/reviews/{rid}")
def delete_review(rid: int, user: dict = Depends(require_role("admin"))):
    db.execute(config.DB_ACTIVE, "DELETE FROM review_items WHERE id=%s", (rid,))
    return {"deleted": rid}


# ─────────────── 手动修订完成：自动版本化 ───────────────

class ManualDoneIn(BaseModel):
    revision_note: str = ""


@r.post("/reviews/{rid}/manual-done", status_code=201)
def manual_done(rid: int, body: ManualDoneIn, user: dict = Depends(require_role("editor"))):
    """手动修订完成：对当前项目数据打新版本快照，意见标记 manual_done。"""
    item = db.query(config.DB_ACTIVE, "SELECT * FROM review_items WHERE id=%s", (rid,), one=True)
    if not item:
        raise HTTPException(404, "评审意见不存在")
    snap = versioning.snapshot(config.DB_ACTIVE, item["project_id"],
                               label="", changelog=f"评审意见#{rid} 手动修订：{(body.revision_note or item['content'])[:80]}",
                               author=user["username"])
    db.execute(config.DB_ACTIVE,
               "UPDATE review_items SET disposition=%s, revision_note=%s, version_id=%s WHERE id=%s",
               ("manual_done", (body.revision_note or "手动修订")[:500], snap["id"], rid))
    return {"version": snap, "disposition": "manual_done"}


# ─────────────── 批次 AI 自动优化（一轮=一个版本） ───────────────

class AutoFixIn(BaseModel):
    review_ids: list[int] = []  # 空=全部 pending


def _llm_cfg():
    row = db.query(config.DB_STUDIO, "SELECT * FROM llm_config WHERE id=1", one=True)
    return dict(row) if row else {}


def _call_llm(cfg: dict, messages: list) -> dict:
    import urllib.request
    url = cfg["base_url"].rstrip("/") + "/chat/completions"
    payload = {"model": cfg["model"], "messages": messages, "temperature": 0.2}
    req = urllib.request.Request(url, data=json.dumps(payload, ensure_ascii=False).encode(),
                                 headers={"Content-Type": "application/json",
                                          "Authorization": f"Bearer {cfg['api_key']}"})
    with urllib.request.urlopen(req, timeout=180) as resp:
        return json.loads(resp.read().decode())


def _fp_context(cur, fp_id: int):
    cur.execute("SELECT f.*, m.level3 FROM fps f JOIN modules m ON m.id=f.module_id WHERE f.id=%s", (fp_id,))
    fp = cur.fetchone()
    if not fp:
        return None
    cur.execute("SELECT * FROM sub_processes WHERE fp_id=%s ORDER BY sort_order", (fp_id,))
    subs = cur.fetchall()
    return {"fp": {k: fp[k] for k in ("id", "fp_name", "functional_user", "trigger_event")},
            "level3": fp["level3"],
            "subs": [{k: s[k] for k in ("id", "sort_order", "description", "data_move_type",
                                        "data_group_name", "data_attributes")} for s in subs]}


@r.post("/projects/{pid}/reviews/auto-fix")
def auto_fix(pid: int, body: AutoFixIn, user: dict = Depends(require_role("editor"))):
    cfg = _llm_cfg()
    if not cfg.get("enabled") or not cfg.get("base_url") or not cfg.get("model"):
        raise HTTPException(409, "LLM 未配置：请到 系统管理→LLM配置 填写并启用后再使用自动优化")

    if body.review_ids:
        ph = ",".join(["%s"] * len(body.review_ids))
        items = db.query(config.DB_ACTIVE,
                         f"SELECT * FROM review_items WHERE project_id=%s AND id IN ({ph}) "
                         "AND disposition='pending'", (pid, *body.review_ids))
    else:
        items = db.query(config.DB_ACTIVE,
                         "SELECT * FROM review_items WHERE project_id=%s AND disposition='pending'", (pid,))
    if not items:
        raise HTTPException(422, "没有待处理的评审意见")

    before_lint = linter.lint_project(config.DB_ACTIVE, pid)
    applied, skipped, llm_changes = [], [], []

    struct = [it for it in items if it["classify"] == "structure"]
    todo = [it for it in items if it["classify"] != "structure"]

    # LLM 批次修订（文本替换/内容质疑类）；sub 类意见先定位所属 FP 再取上下文
    if todo:
        with db.connect(config.DB_ACTIVE) as conn:
            with conn.cursor() as cur:
                contexts = []
                for it in todo:
                    ctx = None
                    tid = it["target_id"]
                    if it["target_type"] == "sub" and tid:
                        sub = db.query(config.DB_ACTIVE, "SELECT fp_id FROM sub_processes WHERE id=%s", (tid,), one=True)
                        ctx = _fp_context(cur, sub["fp_id"]) if sub else None
                        if ctx:
                            ctx["focus_sub_id"] = tid
                    elif it["target_type"] == "fp" and tid:
                        ctx = _fp_context(cur, tid)
                    contexts.append({"review_id": it["id"], "target_type": it["target_type"],
                                     "target_id": tid, "意见": it["content"], "现状": ctx})
        sys_prompt = (
            "你是 COSMIC 度量表评审修订专家。根据评审意见修改对应行，严格遵守规范：\n"
            "1. FP名以动词开头（新增/修改/删除/查询/预览），禁含禁词（记录/日志/导入/缓存/明细/列表/详情/效果）\n"
            "2. E列触发事件格式：{发起者}{FP名}时触发；F列功能用户两行：发起者：X\\n接收者：Y\n"
            "3. 子过程描述：E类以「接收」开头，X类以「返回」开头，无逗号断句\n"
            "4. 数据组名后缀：E→请求数据，W→数据，R→查询数据，X→查询结果（预览类前缀预览）\n"
            "5. 数据属性用「、」分隔≥3个字段，字段为真实数据库列名，禁PII（客户姓名/证件号/电话等）\n"
            "只输出 JSON 数组，元素：{\"review_id\":int,\"target_type\":\"fp|sub\",\"target_id\":int,"
            "\"fields\":{字段:新值},\"reason\":\"修改理由\"}。不改的不要输出。"
        )
        resp = _call_llm(cfg, [{"role": "system", "content": sys_prompt},
                               {"role": "user", "content": json.dumps(contexts, ensure_ascii=False)}])
        raw = resp["choices"][0]["message"].get("content", "")
        raw = raw[raw.find("["): raw.rfind("]") + 1] if "[" in raw else ""
        try:
            llm_changes = json.loads(raw or "[]")
        except json.JSONDecodeError:
            raise HTTPException(502, f"LLM 返回无法解析为修改清单：{raw[:200]}")

    # 白名单校验 + 事务应用（结构类跳过）；先备份原值，门禁保险丝失败时补偿回滚
    backups = []  # (table, pk, field, old_value)
    with db.tx(config.DB_ACTIVE) as cur:
        for ch in llm_changes:
            it = next((x for x in todo if x["id"] == ch.get("review_id")), None)
            if not it:
                continue
            fields = ch.get("fields") or {}
            tt, tid = ch.get("target_type"), ch.get("target_id")
            allowed = FP_FIELDS if tt == "fp" else SUB_FIELDS if tt == "sub" else set()
            fields = {k: v for k, v in fields.items() if k in allowed and v not in (None, "")}
            if not fields or not tid:
                skipped.append({"review_id": it["id"], "reason": "无可应用的安全字段"})
                continue
            table = "fps" if tt == "fp" else "sub_processes"
            row = db.query(config.DB_ACTIVE, f"SELECT * FROM {table} WHERE id=%s", (tid,), one=True)
            if not row:
                skipped.append({"review_id": it["id"], "reason": f"目标行不存在 {tt}#{tid}"})
                continue
            for k, v in fields.items():
                backups.append((table, tid, k, row[k]))
            sets = ", ".join(f"{k}=%s" for k in fields)
            cur.execute(f"UPDATE {table} SET {sets} WHERE id=%s", (*fields.values(), tid))
            applied.append({"review_id": it["id"], "applied": fields, "reason": ch.get("reason", "")})
        for it in struct:
            cur.execute("""UPDATE review_items SET disposition='needs_manual',
                           revision_note=%s WHERE id=%s""",
                        ("结构调整型：AI 出方案需人工确认，意见原文见列表", it["id"]))
            skipped.append({"review_id": it["id"], "reason": "结构调整型，需人工确认"})

    # 门禁保险丝：新增 error → 补偿回滚全部本批修改，数据保持修订前状态
    after_lint = linter.lint_project(config.DB_ACTIVE, pid)
    delta = after_lint["summary"]["error"] - before_lint["summary"]["error"]
    if delta > 0 and backups:
        with db.tx(config.DB_ACTIVE) as cur:
            for table, pk, field, old in backups:
                cur.execute(f"UPDATE {table} SET {field}=%s WHERE id=%s", (old, pk))
        raise HTTPException(422, {
            "message": f"AI 修改引入 {delta} 个新门禁错误，本轮已全部回滚（数据保持修订前状态）",
            "new_errors": after_lint["errors"][-delta:],
            "llm_wanted": applied,
        })

    # 一轮一个版本
    if applied:
        ids = "、#".join(str(a["review_id"]) for a in applied)
        snap = versioning.snapshot(config.DB_ACTIVE, pid, label="",
                                   changelog=f"评审批次自动修订（意见#{ids}），共 {len(applied)} 处",
                                   author=user["username"])
        with db.tx(config.DB_ACTIVE) as cur:
            for a in applied:
                note = (a.get("reason") or "")[:200] + " | " + json.dumps(a["applied"], ensure_ascii=False)[:200]
                cur.execute("UPDATE review_items SET disposition=%s, revision_note=%s, version_id=%s WHERE id=%s",
                            ("auto_done", note, snap["id"], a["review_id"]))
        version = snap
    else:
        version = None

    return {"version": version, "applied": applied,
            "skipped": skipped, "needs_manual": [{"review_id": s["review_id"]} for s in skipped if s["reason"].startswith("结构调整")],
            "lint": {"before": before_lint["summary"], "after": after_lint["summary"]}}
