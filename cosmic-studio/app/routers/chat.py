"""对话式使用入口：/api/chat + 工具注册表 + LLM 配置（OpenAI 兼容）。

扩展点：系统新能力 = 在 TOOLS 注册一个 {name, description, parameters, executor}，
对话自动可用，无需改对话引擎本身。工具执行走权限校验（viewer 只读工具，写工具 admin/editor）。
LLM 未配置时返回明确提示，不崩。
"""
import json
import urllib.request

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel

from .. import config, db
from ..auth import require_role
from ..engines import derive, linter, spec
from ..services import versioning

r = APIRouter(prefix="/api", tags=["chat"])

# ══════════════════ 工具注册表（扩展点：注册即对话可用）══════════════════

TOOLS = []


def tool(name, description, parameters, min_role="viewer"):
    def deco(fn):
        TOOLS.append({"name": name, "description": description,
                      "parameters": parameters, "min_role": min_role, "executor": fn})
        return fn
    return deco


@tool("list_projects", "列出指定维度（active编写库/archive归档库）的项目清单及模块/FP/子过程统计",
      {"type": "object", "properties": {
          "dimension": {"type": "string", "enum": ["active", "archive"]}},
       "required": ["dimension"]})
def t_list_projects(args, user):
    db_name = _dim(args.get("dimension", "active"))
    return db.query(db_name, """
        SELECT p.id, p.requirement_id, p.requirement_name, p.status,
          (SELECT COUNT(*) FROM modules m WHERE m.project_id=p.id) module_count,
          (SELECT COUNT(*) FROM fps f JOIN modules m ON m.id=f.module_id WHERE m.project_id=p.id) fp_count
        FROM projects p ORDER BY p.id""")


@tool("get_project_tree", "查看项目完整结构树（三级模块/功能过程/子过程/数据属性）",
      {"type": "object", "properties": {
          "dimension": {"type": "string", "enum": ["active", "archive"]},
          "project_id": {"type": "integer"}}, "required": ["dimension", "project_id"]})
def t_tree(args, user):
    from ..services.xlsx_export import load_project_tree
    tree = load_project_tree(_dim(args["dimension"]), args["project_id"])
    if not tree:
        return {"error": "项目不存在"}
    return {"requirement": f"{tree['requirement_id']} {tree['requirement_name']}",
            "modules": [{"level3": m["level3"],
                         "fps": [{"fp_name": f["fp_name"],
                                  "subs": [{"move": s["data_move_type"], "group": s["data_group_name"],
                                            "attrs": s["data_attributes"]} for s in f["subs"]]}
                                 for f in m["fps"]]} for m in tree["modules"]]}


@tool("lint_project", "对项目跑全量质量门禁（禁词/EWX/相似度/属性池化等），返回错误与警告清单",
      {"type": "object", "properties": {
          "dimension": {"type": "string", "enum": ["active", "archive"]},
          "project_id": {"type": "integer"}}, "required": ["dimension", "project_id"]})
def t_lint(args, user):
    report = linter.lint_project(_dim(args["dimension"]), args["project_id"])
    return {"summary": report["summary"],
            "errors": report["errors"][:30], "warnings": report["warnings"][:10],
            "error_total": len(report["errors"])}


@tool("derive_project", "检查/一键修复可推导列（F功能用户/E触发事件/子过程描述/数据组名），fix=true 时直接修正",
      {"type": "object", "properties": {
          "dimension": {"type": "string", "enum": ["active"]},
          "project_id": {"type": "integer"}, "fix": {"type": "boolean"}},
       "required": ["dimension", "project_id"]}, min_role="editor")
def t_derive(args, user):
    issues = derive.derive_all(config.DB_ACTIVE, args["project_id"], fix=bool(args.get("fix")))
    return {"issue_count": len(issues), "fixed": bool(args.get("fix")), "issues": issues[:30]}


@tool("export_project", "导出标准 COSMIC xlsx，返回下载链接",
      {"type": "object", "properties": {
          "dimension": {"type": "string", "enum": ["active", "archive"]},
          "project_id": {"type": "integer"}}, "required": ["dimension", "project_id"]},
      min_role="editor")
def t_export(args, user):
    return {"download": f"/api/{args['dimension']}/projects/{args['project_id']}/export/xlsx",
            "note": "浏览器打开该地址即可下载"}


@tool("snapshot_version", "为编写库项目创建版本快照（xlsx+sha256）",
      {"type": "object", "properties": {
          "project_id": {"type": "integer"}, "label": {"type": "string"},
          "changelog": {"type": "string"}}, "required": ["project_id"]}, min_role="editor")
def t_snapshot(args, user):
    return versioning.snapshot(config.DB_ACTIVE, args["project_id"],
                               args.get("label", ""), args.get("changelog", ""))


@tool("get_spec", "查看编写规范/截图规范当前值（如 ewx_rules、sim_same_req、screenshot_render）",
      {"type": "object", "properties": {"spec_key": {"type": "string"}}, "required": ["spec_key"]})
def t_get_spec(args, user):
    return {"spec_key": args["spec_key"], "value": spec.load_spec(args["spec_key"])}


@tool("update_spec", "修改规范（评审反哺落点），改完立即生效。慎用，改前先 get_spec 看当前值",
      {"type": "object", "properties": {
          "spec_key": {"type": "string"}, "value": {}}, "required": ["spec_key", "value"]},
      min_role="admin")
def t_update_spec(args, user):
    return spec.upsert_spec(args["spec_key"], args["value"])


@tool("search_vocab", "按关键词搜业务词库（含频次），写 FP 名/属性时先查词库保证命名一致",
      {"type": "object", "properties": {"q": {"type": "string"}, "limit": {"type": "integer"}}})
def t_vocab(args, user):
    q = f"%{args.get('q', '')}%"
    return db.query(config.DB_STUDIO,
                    "SELECT term, frequency, source, status FROM vocab_terms WHERE term LIKE %s "
                    "ORDER BY frequency DESC LIMIT %s", (q, min(args.get("limit", 20), 100)))


def _dim(d: str) -> str:
    return config.DB_ACTIVE if d == "active" else config.DB_ARCHIVE


def tools_schema(role: str) -> list:
    from ..auth import ROLE_RANK
    return [{"type": "function", "function": {k: t[k] for k in ("name", "description", "parameters")}}
            for t in TOOLS if ROLE_RANK.get(role, 0) >= ROLE_RANK[t["min_role"]]]


def _exec_tool(name: str, args: dict, user: dict):
    t = next((x for x in TOOLS if x["name"] == name), None)
    if not t:
        return {"error": f"未知工具 {name}"}
    from ..auth import ROLE_RANK
    if ROLE_RANK.get(user["role"], 0) < ROLE_RANK[t["min_role"]]:
        return {"error": f"权限不足，{name} 需要 {t['min_role']}"}
    try:
        return t["executor"](args, user)
    except Exception as e:
        return {"error": str(e)}


# ══════════════════ LLM 配置 ══════════════════

def get_llm_config() -> dict:
    row = db.query(config.DB_STUDIO, "SELECT * FROM llm_config WHERE id=1", one=True)
    return dict(row) if row else {}


class LlmIn(BaseModel):
    provider: str = "openai-compatible"
    base_url: str
    model: str
    api_key: str = ""
    enabled: bool


@r.get("/studio/llm-config")
def get_llm(user: dict = Depends(require_role("admin"))):
    cfg = get_llm_config()
    if cfg.get("api_key"):
        cfg["api_key"] = cfg["api_key"][:4] + "****"
    return cfg


@r.put("/studio/llm-config")
def put_llm(body: LlmIn, user: dict = Depends(require_role("admin"))):
    vals = body.model_dump()
    if not vals["api_key"]:
        vals["api_key"] = get_llm_config().get("api_key", "")  # 不回传时保留旧 key
    db.execute(config.DB_STUDIO, """
        INSERT INTO llm_config (id, provider, base_url, model, api_key, enabled)
        VALUES (1,%s,%s,%s,%s,%s)
        ON DUPLICATE KEY UPDATE provider=VALUES(provider), base_url=VALUES(base_url),
          model=VALUES(model), api_key=VALUES(api_key), enabled=VALUES(enabled)
    """, (vals["provider"], vals["base_url"], vals["model"], vals["api_key"], vals["enabled"]))
    return {"saved": True, "enabled": vals["enabled"]}


# ══════════════════ 对话接口 ══════════════════

class ChatIn(BaseModel):
    message: str
    history: list = []


@r.post("/chat")
def chat(body: ChatIn, user: dict = Depends(require_role("viewer"))):
    cfg = get_llm_config()
    if not cfg.get("enabled") or not cfg.get("base_url") or not cfg.get("model"):
        raise HTTPException(409, "LLM 未配置：请到 系统管理→LLM配置 填写 base_url/model/api_key 并启用")
    schema = tools_schema(user["role"])
    messages = [{"role": "system", "content": SYSTEM_PROMPT}]
    messages += body.history[-10:]
    messages.append({"role": "user", "content": body.message})

    tools_used = []
    for _ in range(5):
        resp = _llm_call(cfg, messages, schema)
        msg = resp["choices"][0]["message"]
        messages.append(msg)
        calls = msg.get("tool_calls") or []
        if not calls:
            _log(body.message, msg.get("content", ""), tools_used, user)
            return {"reply": msg.get("content", ""), "tools_used": tools_used}
        for c in calls:
            fn = c["function"]["name"]
            try:
                args = json.loads(c["function"].get("arguments") or "{}")
            except json.JSONDecodeError:
                args = {}
            result = _exec_tool(fn, args, user)
            # 工具结果摘要透出给用户（错误/权限拒绝可见，成功给关键计数）
            if isinstance(result, dict) and result.get("error"):
                summary = {"error": result["error"]}
            elif isinstance(result, list):
                summary = {"rows": len(result)}
            elif isinstance(result, dict):
                summary = {k: result[k] for k in ("summary", "issue_count", "download", "label", "spec_key") if k in result}
            else:
                summary = {"ok": True}
            tools_used.append({"tool": fn, "args": args, "result": summary})
            messages.append({"role": "tool", "tool_call_id": c["id"],
                             "content": json.dumps(result, ensure_ascii=False)[:4000]})
    _log(body.message, "(达到工具调用轮次上限)", tools_used, user)
    return {"reply": "工具调用轮次达上限（5轮），请拆小问题重试。", "tools_used": tools_used}


SYSTEM_PROMPT = """你是 cosmic-studio 系统的助手，帮良哥操作 COSMIC 度量表生产系统（编写库/归档库、质量门禁、规范中心、词库、版本管理）。
规则：
1. 优先调用工具获取真实数据，禁止凭空编造项目/FP/统计数字（数值零脑补）。
2. 写操作工具（update_spec/derive fix/snapshot）调用前先向用户确认关键参数。
3. 回答用中文，简洁直接，给结论和关键数字。
4. 修改规范(update_spec)是评审反哺的高危操作，必须复述用户意图并确认 spec_key 与新值。"""


def _llm_call(cfg: dict, messages: list, tools_schema_list: list) -> dict:
    import urllib.error
    import urllib.request
    url = cfg["base_url"].rstrip("/") + "/chat/completions"
    payload = {"model": cfg["model"], "messages": messages, "temperature": 0.3}
    if tools_schema_list:
        payload["tools"] = tools_schema_list
    req = urllib.request.Request(
        url, data=json.dumps(payload, ensure_ascii=False).encode(),
        headers={"Content-Type": "application/json", "Authorization": f"Bearer {cfg['api_key']}"})
    try:
        with urllib.request.urlopen(req, timeout=120) as resp:
            return json.loads(resp.read().decode())
    except urllib.error.HTTPError as e:
        raise HTTPException(502, f"LLM 返回错误 {e.code}: {e.read()[:200]}")
    except urllib.error.URLError as e:
        raise HTTPException(502, f"LLM 端点不可达（{e.reason}），请检查 系统管理→LLM配置 的 Base URL")


def _log(message, reply, tools_used, user):
    try:
        db.execute(config.DB_STUDIO,
                   "INSERT INTO chat_logs (user_id, message, reply, tools_used) VALUES (%s,%s,%s,%s)",
                   (user["id"], message, reply, json.dumps(tools_used, ensure_ascii=False)))
    except Exception:
        pass
