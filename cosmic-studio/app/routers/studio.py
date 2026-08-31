"""studio 路由：规则管理、规范中心（spec_rules）、词库、菜单下发。"""
import json

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel

from .. import config, db, paging
from ..auth import require_role
from ..engines import spec, vocab_miner

# 路由级默认：登录（viewer 起）可见；变更端点在签名上升为 admin
r = APIRouter(prefix="/api", tags=["studio"], dependencies=[Depends(require_role("viewer"))])

# 公开路由（无登录要求）：健康检查供容器探活使用
pub = APIRouter(prefix="/api", tags=["health"])


@pub.get("/health")
def health():
    return {
        "status": "ok",
        "db_active": db.ping(config.DB_ACTIVE),
        "db_archive": db.ping(config.DB_ARCHIVE),
        "db_studio": db.ping(config.DB_STUDIO),
    }


# ── 规则：禁词 ──
class WordIn(BaseModel):
    word: str


@r.get("/studio/rules")
def get_rules():
    return {
        "forbidden_words": [x["word"] for x in db.query(config.DB_STUDIO, "SELECT word FROM rule_forbidden_words ORDER BY word")],
        "pseudo_fields": [{"word": x["word"], "kind": x["kind"]}
                          for x in db.query(config.DB_STUDIO, "SELECT word, kind FROM rule_pseudo_fields ORDER BY kind, word")],
        "pool_count": db.query(config.DB_STUDIO, "SELECT COUNT(*) AS n FROM attr_pools", one=True)["n"],
    }


@r.post("/studio/rules/forbidden", status_code=201)
def add_forbidden(body: WordIn, user: dict = Depends(require_role("admin"))):
    db.execute(config.DB_STUDIO, "INSERT IGNORE INTO rule_forbidden_words (word) VALUES (%s)", (body.word,))
    return {"added": body.word}


@r.delete("/studio/rules/forbidden/{word}")
def del_forbidden(word: str, user: dict = Depends(require_role("admin"))):
    db.execute(config.DB_STUDIO, "DELETE FROM rule_forbidden_words WHERE word=%s", (word,))
    return {"deleted": word}


@r.post("/studio/rules/pseudo", status_code=201)
def add_pseudo(body: WordIn, kind: str = "behavior", user: dict = Depends(require_role("admin"))):
    db.execute(config.DB_STUDIO, "INSERT IGNORE INTO rule_pseudo_fields (word, kind) VALUES (%s,%s)",
               (body.word, kind))
    return {"added": body.word, "kind": kind}


@r.delete("/studio/rules/pseudo/{word}")
def del_pseudo(word: str, user: dict = Depends(require_role("admin"))):
    db.execute(config.DB_STUDIO, "DELETE FROM rule_pseudo_fields WHERE word=%s", (word,))
    return {"deleted": word}


# ── 字段池 ──
class PoolIn(BaseModel):
    data_group: str
    fields: list[str]


@r.get("/studio/pools")
def list_pools(q: str = "", page: int = 1, page_size: int = 20):
    """分页字段池 → {list,total,page,page_size}。size = 该组去重字段数。"""
    cond, params = ["1=1"], []
    if q:
        cond.append("data_group LIKE %s")
        params.append(f"%{q.strip()}%")
    where = " AND ".join(cond)
    return paging.paginate(
        config.DB_STUDIO,
        f"SELECT id, data_group, fields, JSON_LENGTH(fields) AS size, updated_at "
        f"FROM attr_pools WHERE {where}",
        f"SELECT COUNT(*) AS total FROM attr_pools WHERE {where}",
        tuple(params), "ORDER BY data_group", page, page_size)


@r.put("/studio/pools")
def upsert_pool(body: PoolIn, user: dict = Depends(require_role("admin"))):
    """新增/覆盖字段池。

    两处修正：
      1. fields 是 JSON 列，必须 json.dumps —— 直接传 list 会被 pymysql 转义成
         ('a','b') 导致写入失败或存成非法 JSON（迁移脚本一直是对的，这里漏了）。
      2. 原来 <25 字段直接 422：现存量 16 个池里有 5 个本来就不足 25 字段，
         硬校验等于把补录通道焊死。改为 <3 才拒（子过程最少 3 字段的规矩），
         不足 25 只警告「差异化空间不足」。
    """
    if len(body.fields) < 3:
        raise HTTPException(422, f"字段池至少3个字段，当前{len(body.fields)}个")
    db.execute(config.DB_STUDIO, """
        INSERT INTO attr_pools (data_group, fields, updated_at) VALUES (%s,%s,NOW())
        ON DUPLICATE KEY UPDATE fields=VALUES(fields), updated_at=NOW()
    """, (body.data_group, json.dumps(body.fields, ensure_ascii=False)))
    return {"data_group": body.data_group, "size": len(body.fields),
            "warn": len(body.fields) < 25 and "字段不足25，差异化空间受限" or ""}


# ── 规范中心：编写规范 + 截图规范（spec_rules，即改即生效）──
class SpecIn(BaseModel):
    value: object
    category: str = ""
    description: str = ""


@r.get("/studio/specs")
def list_specs(category: str = ""):
    data = spec.load_all(category or None)
    return {"count": len(data), "specs": data}


@r.get("/studio/specs/{spec_key}")
def get_spec(spec_key: str):
    if spec_key not in spec.SEED_SPECS:
        raise HTTPException(404, f"未知规范键: {spec_key}")
    return {"spec_key": spec_key, **spec.load_all()[spec_key]}


@r.put("/studio/specs/{spec_key}")
def put_spec(spec_key: str, body: SpecIn, user: dict = Depends(require_role("admin"))):
    err = spec.validate_value(spec_key, body.value)
    if err:
        raise HTTPException(422, err)
    return spec.upsert_spec(spec_key, body.value, body.category, body.description)


@r.delete("/studio/specs/{spec_key}")
def reset_spec(spec_key: str, user: dict = Depends(require_role("admin"))):
    """删除自定义值，回落种子规范。"""
    if spec_key not in spec.SEED_SPECS:
        raise HTTPException(404, f"未知规范键: {spec_key}")
    db.execute(config.DB_STUDIO, "DELETE FROM spec_rules WHERE spec_key=%s", (spec_key,))
    return {"reset": spec_key, "value": spec.SEED_SPECS[spec_key]["value"]}


# ── 菜单下发（按角色过滤；扩展点：新页面在此注册一行）──
MENU_REGISTRY = [
    {"key": "chat", "title": "工作台", "icon": "ChatDotRound", "path": "/", "min_role": "viewer"},
    {"key": "projects", "title": "编写库", "icon": "EditPen", "path": "/projects", "min_role": "viewer"},
    {"key": "archive", "title": "归档库", "icon": "Box", "path": "/archive", "min_role": "viewer"},
    {"key": "lint", "title": "质量门禁", "icon": "CircleCheck", "path": "/lint", "min_role": "viewer"},
    {"key": "versions", "title": "版本管理", "icon": "Files", "path": "/versions", "min_role": "viewer"},
    {"key": "specs", "title": "规范中心", "icon": "Setting", "path": "/specs", "min_role": "viewer"},
    {"key": "vocab", "title": "业务词库", "icon": "Collection", "path": "/vocab", "min_role": "viewer"},
    {"key": "admin", "title": "系统管理", "icon": "User", "path": "/admin", "min_role": "admin"},
]


@r.get("/studio/menus")
def menus(user: dict = Depends(require_role("viewer"))):
    from ..auth import ROLE_RANK
    allow = ROLE_RANK.get(user["role"], 0)
    return [m for m in MENU_REGISTRY if allow >= ROLE_RANK[m["min_role"]]]


# ── 词库 ──
VOCAB_SORTABLE = {
    "frequency": "frequency DESC, id ASC",
    "term": "term ASC",
    "created_at": "created_at DESC, id DESC",
}


@r.get("/studio/vocab")
def vocab(q: str = "", status: str = "", category_id: int = 0,
          page: int = 1, page_size: int = 20, sort: str = "frequency"):
    """分页词库 → {list,total,page,page_size}。"""
    cond, params = ["1=1"], []
    if q:
        cond.append("term LIKE %s")
        params.append(f"%{q.strip()}%")
    if status:
        cond.append("status=%s")
        params.append(status)
    if category_id:
        cond.append("category_id=%s")
        params.append(category_id)
    where = " AND ".join(cond)
    order = VOCAB_SORTABLE.get(sort, VOCAB_SORTABLE["frequency"])
    return paging.paginate(
        config.DB_STUDIO,
        f"SELECT v.id, v.term, v.category_id, v.frequency, v.source, v.status, v.notes, "
        f"v.created_at, c.name AS category_name "
        f"FROM vocab_terms v LEFT JOIN vocab_categories c ON c.id = v.category_id "
        f"WHERE {where}",
        f"SELECT COUNT(*) AS total FROM vocab_terms WHERE {where}",
        tuple(params), f"ORDER BY {order}", page, page_size)


@r.get("/studio/vocab/categories")
def vocab_categories():
    """分类字典 + 每类词条数，供前端筛选下拉与统计条使用。"""
    return db.query(config.DB_STUDIO, """
        SELECT c.id, c.name, c.description, COUNT(v.id) AS term_count
        FROM vocab_categories c LEFT JOIN vocab_terms v ON v.category_id = c.id
        GROUP BY c.id, c.name, c.description ORDER BY term_count DESC, c.id
    """)


@r.get("/studio/vocab/stats")
def vocab_stats():
    """词库体检指标：待审候选数、零频词数、来源分布、状态分布。"""
    def _dist(col):
        return {r[col]: r["n"] for r in db.query(
            config.DB_STUDIO,
            f"SELECT {col}, COUNT(*) AS n FROM vocab_terms GROUP BY {col} ORDER BY n DESC")}

    return {
        "total": db.query(config.DB_STUDIO, "SELECT COUNT(*) AS n FROM vocab_terms", one=True)["n"],
        "by_status": _dist("status"),
        "by_source": _dist("source"),
        "zero_freq": db.query(config.DB_STUDIO,
                              "SELECT COUNT(*) AS n FROM vocab_terms WHERE frequency<=0",
                              one=True)["n"],
        "last_mined_at": (db.query(
            config.DB_STUDIO,
            "SELECT MAX(created_at) AS t FROM vocab_terms WHERE source='mined'",
            one=True) or {}).get("t"),
    }


@r.post("/studio/vocab/mine")
def mine_vocab(sync_pools: bool = True, user: dict = Depends(require_role("admin"))):
    """从编写库/归档库回采术语与词频（幂等，可重复执行）。

    新词落 candidate 待人审；已有词只刷新 frequency，不动 status/source。
    """
    return vocab_miner.mine(sync_pools=sync_pools)


class VocabIdsIn(BaseModel):
    ids: list[int]


class VocabFilterIn(BaseModel):
    q: str | None = None
    status: str | None = None
    category_id: int | None = None


class VocabTermIn(BaseModel):
    term: str
    category_id: int | None = None
    frequency: int | None = None
    notes: str | None = None


class VocabImportIn(BaseModel):
    terms: list[VocabTermIn]
    default_category_id: int | None = None


def _bulk_set_status(ids: list[int], from_status: str, to_status: str) -> int:
    if not ids:
        return 0
    ph = ",".join(["%s"] * len(ids))
    return db.execute(
        config.DB_STUDIO,
        f"UPDATE vocab_terms SET status=%s WHERE status=%s AND id IN ({ph})",
        (to_status, from_status, *ids))


@r.post("/studio/vocab/confirm")
def confirm_terms(body: VocabIdsIn, user: dict = Depends(require_role("admin"))):
    """候选词 → 已确认（进入 LLM search_vocab 与页面正式词库）。"""
    return {"confirmed": _bulk_set_status(body.ids, "candidate", "confirmed")}


@r.post("/studio/vocab/reject")
def reject_terms(body: VocabIdsIn, user: dict = Depends(require_role("admin"))):
    """候选词 → 已驳回（保留记录但不再展示）。"""
    return {"rejected": _bulk_set_status(body.ids, "candidate", "rejected")}


@r.post("/studio/vocab/{vid}/status")
def set_term_status(vid: int, status: str, user: dict = Depends(require_role("admin"))):
    if status not in ("candidate", "confirmed", "rejected"):
        raise HTTPException(422, "status 必须是 candidate/confirmed/rejected")
    db.execute(config.DB_STUDIO, "UPDATE vocab_terms SET status=%s WHERE id=%s", (status, vid))
    return {"id": vid, "status": status}


@r.post("/studio/vocab/batch-import")
def batch_import(body: VocabImportIn, user: dict = Depends(require_role("admin"))):
    """批量导入术语（粘贴 / CSV 经前端解析为 JSON 后调用）。

    - 每词先用 vocab_miner._clean 归一化（去不可见字符 / 空白 / 长度校验），
      utf8mb4_unicode_ci 下的 ZWNJ 变体一并归并 → 入库自动去重（#3）。
    - term 唯一索引 + INSERT IGNORE 双保险；已存在（库内或批内）的词 skip，返回 skipped。
    """
    default_cat = body.default_category_id
    if default_cat is None:
        atom = db.query(config.DB_STUDIO, "SELECT id FROM vocab_categories WHERE name=%s",
                        (vocab_miner.CAT_ATOM,), one=True)
        default_cat = atom["id"] if atom else 5
    valid_cats = {r["id"] for r in db.query(config.DB_STUDIO, "SELECT id FROM vocab_categories")}
    if default_cat not in valid_cats:
        raise HTTPException(422, f"默认分类不存在: {default_cat}")

    existing = {r["term"] for r in db.query(config.DB_STUDIO, "SELECT term FROM vocab_terms")}
    rows, skipped, errors, seen = [], 0, [], set()
    for item in body.terms:
        cleaned = vocab_miner._clean(item.term or "", vocab_miner.NOUN_MIN_LEN, vocab_miner.NOUN_MAX_LEN)
        if not cleaned:
            errors.append({"term": item.term, "reason": "empty_or_invalid_len"})
            continue
        if cleaned in existing or cleaned in seen:
            skipped += 1
            continue
        seen.add(cleaned)
        cat = item.category_id if (item.category_id in valid_cats) else default_cat
        freq = item.frequency if (item.frequency and item.frequency > 0) else 1
        rows.append((cleaned, cat, freq, item.notes or ""))

    inserted = 0
    if rows:
        inserted = db.executemany(
            config.DB_STUDIO,
            "INSERT IGNORE INTO vocab_terms (term, category_id, frequency, source, status, notes) "
            "VALUES (%s,%s,%s,'imported','confirmed',%s)",
            rows)
    return {"imported": inserted, "skipped": skipped, "errors": errors, "total": len(body.terms)}


@r.post("/studio/vocab/batch-delete")
def batch_delete(body: VocabIdsIn, user: dict = Depends(require_role("admin"))):
    """批量硬删除术语（永久移除，不可恢复）。vocab_terms 无外键引用，安全。"""
    if not body.ids:
        return {"deleted": 0}
    ph = ",".join(["%s"] * len(body.ids))
    deleted = 0
    with db.tx(config.DB_STUDIO) as cur:
        cur.execute(f"DELETE FROM vocab_terms WHERE id IN ({ph})", tuple(body.ids))
        deleted = cur.rowcount
    return {"deleted": deleted}


@r.post("/studio/vocab/batch-delete-by-filter")
def batch_delete_by_filter(body: VocabFilterIn, user: dict = Depends(require_role("admin"))):
    """按当前筛选条件批量删除全部匹配术语（跨分页，一次完成）。

    与 GET /studio/vocab 的筛选语义保持一致（q→LIKE %q%、status / category_id 精确匹配）。
    必须至少带一个筛选条件，避免无差别全表清空。
    """
    conds, params = [], []
    if body.q:
        conds.append("term LIKE %s")
        params.append(f"%{body.q.strip()}%")
    if body.status:
        conds.append("status=%s")
        params.append(body.status)
    if body.category_id:
        conds.append("category_id=%s")
        params.append(body.category_id)
    if not conds:
        raise HTTPException(422, "请至少设置一个筛选条件（搜索词 / 状态 / 分类）")
    where = " AND ".join(conds)
    deleted = 0
    with db.tx(config.DB_STUDIO) as cur:
        cur.execute(f"DELETE FROM vocab_terms WHERE {where}", tuple(params))
        deleted = cur.rowcount
    return {"deleted": deleted}


@r.post("/studio/vocab/batch-confirm-by-filter")
def batch_confirm_by_filter(body: VocabFilterIn, user: dict = Depends(require_role("admin"))):
    """按当前筛选条件批量确认全部匹配术语（跨分页，一次完成）。"""
    conds, params = [], []
    if body.q:
        conds.append("term LIKE %s")
        params.append(f"%{body.q}%")
    if body.status:
        conds.append("status = %s")
        params.append(body.status)
    if body.category_id:
        conds.append("category_id = %s")
        params.append(body.category_id)
    if not conds:
        raise HTTPException(422, "至少需要一个筛选条件，避免无差别全表操作")
    where = " AND ".join(conds)
    confirmed = 0
    with db.tx(config.DB_STUDIO) as cur:
        cur.execute(
            f"UPDATE vocab_terms SET status='confirmed' WHERE {where} AND status != 'confirmed'",
            tuple(params))
        confirmed = cur.rowcount
    return {"confirmed": confirmed}


@r.post("/studio/vocab/batch-reject-by-filter")
def batch_reject_by_filter(body: VocabFilterIn, user: dict = Depends(require_role("admin"))):
    """按当前筛选条件批量驳回全部匹配术语（跨分页，一次完成）。"""
    conds, params = [], []
    if body.q:
        conds.append("term LIKE %s")
        params.append(f"%{body.q}%")
    if body.status:
        conds.append("status = %s")
        params.append(body.status)
    if body.category_id:
        conds.append("category_id = %s")
        params.append(body.category_id)
    if not conds:
        raise HTTPException(422, "至少需要一个筛选条件，避免无差别全表操作")
    where = " AND ".join(conds)
    rejected = 0
    with db.tx(config.DB_STUDIO) as cur:
        cur.execute(
            f"UPDATE vocab_terms SET status='rejected' WHERE {where} AND status != 'rejected'",
            tuple(params))
        rejected = cur.rowcount
    return {"rejected": rejected}
