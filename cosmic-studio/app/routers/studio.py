"""studio 路由：健康检查、规则管理（禁词/伪字段/字段池）、规范中心（spec_rules）、词库查询。"""
from fastapi import APIRouter, HTTPException
from pydantic import BaseModel

from .. import config, db
from ..engines import spec

r = APIRouter(prefix="/api", tags=["studio"])


@r.get("/health")
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
def add_forbidden(body: WordIn):
    db.execute(config.DB_STUDIO, "INSERT IGNORE INTO rule_forbidden_words (word) VALUES (%s)", (body.word,))
    return {"added": body.word}


@r.delete("/studio/rules/forbidden/{word}")
def del_forbidden(word: str):
    db.execute(config.DB_STUDIO, "DELETE FROM rule_forbidden_words WHERE word=%s", (word,))
    return {"deleted": word}


@r.post("/studio/rules/pseudo", status_code=201)
def add_pseudo(body: WordIn, kind: str = "behavior"):
    db.execute(config.DB_STUDIO, "INSERT IGNORE INTO rule_pseudo_fields (word, kind) VALUES (%s,%s)",
               (body.word, kind))
    return {"added": body.word, "kind": kind}


@r.delete("/studio/rules/pseudo/{word}")
def del_pseudo(word: str):
    db.execute(config.DB_STUDIO, "DELETE FROM rule_pseudo_fields WHERE word=%s", (word,))
    return {"deleted": word}


# ── 字段池 ──
class PoolIn(BaseModel):
    data_group: str
    fields: list[str]


@r.get("/studio/pools")
def list_pools(q: str = ""):
    like = f"%{q}%" if q else "%"
    return db.query(config.DB_STUDIO,
                    "SELECT id, data_group, fields, updated_at FROM attr_pools WHERE data_group LIKE %s ORDER BY data_group",
                    (like,))


@r.put("/studio/pools")
def upsert_pool(body: PoolIn):
    if len(body.fields) < 25:
        raise HTTPException(422, f"字段池至少25个字段，当前{len(body.fields)}个（池化差异化空间不足）")
    db.execute(config.DB_STUDIO, """
        INSERT INTO attr_pools (data_group, fields, updated_at) VALUES (%s,%s,NOW())
        ON DUPLICATE KEY UPDATE fields=VALUES(fields), updated_at=NOW()
    """, (body.data_group, body.fields))
    return {"data_group": body.data_group, "size": len(body.fields)}


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
def put_spec(spec_key: str, body: SpecIn):
    if spec_key not in spec.SEED_SPECS:
        raise HTTPException(404, f"未知规范键（不允许新增自由键）: {spec_key}")
    return spec.upsert_spec(spec_key, body.value, body.category, body.description)


@r.delete("/studio/specs/{spec_key}")
def reset_spec(spec_key: str):
    """删除自定义值，回落种子规范。"""
    if spec_key not in spec.SEED_SPECS:
        raise HTTPException(404, f"未知规范键: {spec_key}")
    db.execute(config.DB_STUDIO, "DELETE FROM spec_rules WHERE spec_key=%s", (spec_key,))
    return {"reset": spec_key, "value": spec.SEED_SPECS[spec_key]["value"]}


# ── 词库 ──
@r.get("/studio/vocab")
def vocab(q: str = "", status: str = "", limit: int = 50):
    cond, params = ["1=1"], []
    if q:
        cond.append("term LIKE %s")
        params.append(f"%{q}%")
    if status:
        cond.append("status=%s")
        params.append(status)
    params.append(min(limit, 500))
    return db.query(config.DB_STUDIO,
                    f"SELECT id, term, category_id, frequency, source, status, notes FROM vocab_terms "
                    f"WHERE {' AND '.join(cond)} ORDER BY frequency DESC, id LIMIT %s", tuple(params))
