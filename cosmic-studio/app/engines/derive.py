"""推导引擎：从 Hermes cosmic_derive.py / cosmic_cli.py 移植。

可推导列规则（与校验器 linter.py 保持同一套约定，derive 产出必须能过 check）。
全部规则从 spec_rules 规范表读取（ewx_rules / functional_user_map /
trigger_event_template / sub_desc_templates / data_group_templates），
改规范立即生效，代码不再硬编码任何业务规则。
"""
import hashlib
import random
import re

from .. import config, db
from . import spec

VERB_RE = re.compile(r"^(新增|修改|删除|查询|预览)")
SUFFIX_RE = re.compile(r"(查询请求数据|查询数据|查询结果|预览查询数据|预览结果数据|数据)$")


def fp_verb(fp_name: str):
    """提取 FP 动词与业务对象：(动词, 去动词后的对象名)。"""
    m = VERB_RE.match(fp_name or "")
    if m:
        return m.group(1), (fp_name or "")[2:]
    return None, fp_name or ""


def allowed_verbs() -> list:
    return spec.load_spec("allowed_verbs")


def expected_ewx(fp_name: str) -> str:
    verb, _ = fp_verb(fp_name)
    return spec.load_spec("ewx_rules").get(verb, "")


def allowed_sub_moves() -> list:
    return spec.load_spec("sub_move_types")


def derive_functional_user(level3: str) -> str:
    m = spec.load_spec("functional_user_map")
    initiator = m.get("default_initiator", "终端用户")
    for item in m.get("matches", []):
        if item["keyword"] in (level3 or ""):
            initiator = item["initiator"]
            break
    return f"发起者：{initiator}\n接收者：{m.get('receiver', '多媒体卡片平台')}"


def initiator_of(functional_user: str) -> str:
    fu = functional_user or ""
    if "\n" in fu and "发起者：" in fu:
        return fu.split("\n")[0].split("：", 1)[1]
    return spec.load_spec("functional_user_map").get("default_initiator", "一线坐席")


def derive_trigger_event(fp_name: str, initiator: str) -> str:
    return spec.load_spec("trigger_event_template").format(
        initiator=initiator, fp_name=fp_name)


def derive_sub_columns(fp_name: str, initiator: str, move_type: str):
    """返回 (H列描述, J列数据组名)，业务对象 obj=FP名去掉动词。模板见 spec_rules。"""
    verb, obj = fp_verb(fp_name)
    tpls = spec.load_spec("sub_desc_templates")
    desc = tpls.get(f"W_{verb}", tpls.get("W_default", "")) if move_type == "W" \
        else tpls.get(move_type, "")
    groups = spec.load_spec("data_group_templates")
    gkey = f"{move_type}_预览" if verb == "预览" and f"{move_type}_预览" in groups else move_type
    group = groups.get(gkey, "").format(obj=obj, verb=verb)
    return desc.format(initiator=initiator, fp_name=fp_name, obj=obj), group


def standard_subs_for(fp_name: str, initiator: str):
    """按 EWX 规范生成标准子过程序列 [(move_type, desc, group)]。"""
    out = []
    for mt in expected_ewx(fp_name):
        desc, group = derive_sub_columns(fp_name, initiator, mt)
        out.append((mt, desc, group))
    return out


# ─────────────────── 字段池（cosmic_studio.attr_pools）───────────────────

def _pool_key(name: str) -> str:
    s = VERB_RE.sub("", name or "").strip()
    return SUFFIX_RE.sub("", s).strip()


def pool_for(name: str, pools: dict):
    """子串匹配池键，无则 None。pools: {key: [fields]}"""
    if not name:
        return None
    cleaned = _pool_key(name)
    if cleaned in pools:
        return pools[cleaned]
    for key, pool in pools.items():
        if key and key in cleaned:
            return pools[key]
    return None


def load_pools() -> dict:
    rows = db.query(config.DB_STUDIO,
                    "SELECT data_group, fields FROM attr_pools")
    return {r["data_group"]: r["fields"] for r in rows}


def diversify_fp_attributes(pool: list, rng: random.Random):
    core = rng.sample(pool, min(4, len(pool)))
    ext = [f for f in pool if f not in core]
    n_ext = min(len(ext), rng.randint(2, 4))
    chosen = list(core) + (rng.sample(ext, n_ext) if ext else [])
    rng.shuffle(chosen)
    return chosen


def diversify_sub_attributes(move_type: str, fp_fields: list, rng: random.Random,
                             used_sets: set | None = None):
    n = rng.randint(4, 5) if move_type in ("E", "W") else rng.randint(5, 6)
    n = min(n, len(fp_fields))
    for _ in range(50):
        chosen = rng.sample(fp_fields, n)
        fs = frozenset(chosen)
        if used_sets is None or fs not in used_sets:
            if used_sets is not None:
                used_sets.add(fs)
            rng.shuffle(chosen)
            return chosen
    chosen = rng.sample(fp_fields, n)
    rng.shuffle(chosen)
    return chosen


def pool_rng_for(fp_id: int) -> random.Random:
    seed = int(hashlib.md5(f"fp-{fp_id}".encode()).hexdigest(), 16)
    return random.Random(seed)


def auto_diversify_fp(dim_db: str, fp_id: int) -> bool:
    """池存在时对 FP 全部子过程执行属性差异化。返回是否命中池。"""
    fp = db.query(dim_db, "SELECT id, fp_name FROM fps WHERE id=%s", (fp_id,), one=True)
    if not fp:
        return False
    pools = load_pools()
    pool = pool_for(fp["fp_name"], pools)
    if not pool or len(pool) < 4:
        return False
    rng = pool_rng_for(fp_id)
    fp_fields = diversify_fp_attributes(pool, rng)
    subs = db.query(dim_db, "SELECT id, data_move_type FROM sub_processes WHERE fp_id=%s ORDER BY sort_order", (fp_id,))
    used: set = set()
    from ..db import tx
    with tx(dim_db) as cur:
        for sp in subs:
            fields = diversify_sub_attributes(sp["data_move_type"], fp_fields, rng, used)
            cur.execute("UPDATE sub_processes SET data_attributes=%s WHERE id=%s",
                        ("、".join(fields), sp["id"]))
    return True


def project_fps(dim_db: str, project_id: int):
    return db.query(dim_db, """
        SELECT f.id, f.module_id, f.sort_order, f.functional_user, f.trigger_event,
               f.fp_name, m.level3, m.sort_order AS mod_sort
        FROM fps f JOIN modules m ON m.id = f.module_id
        WHERE m.project_id = %s
        ORDER BY m.sort_order, f.sort_order
    """, (project_id,))


def fp_subs(dim_db: str, fp_id: int):
    return db.query(dim_db, "SELECT * FROM sub_processes WHERE fp_id=%s ORDER BY sort_order", (fp_id,))


def derive_all(dim_db: str, project_id: int, fix: bool = False):
    """检查/修复可推导列，返回 issues 列表。期望值全部由规范模板实时计算。"""
    issues = []
    fps = project_fps(dim_db, project_id)
    for fp in fps:
        expected_fu = derive_functional_user(fp["level3"])
        if (fp["functional_user"] or "") != expected_fu:
            issues.append({"fp_id": fp["id"], "fp_name": fp["fp_name"],
                           "col": "F列(功能用户)", "actual": fp["functional_user"], "expected": expected_fu})
        initiator = initiator_of(expected_fu)
        expected_te = derive_trigger_event(fp["fp_name"], initiator)
        if (fp["trigger_event"] or "") != expected_te:
            issues.append({"fp_id": fp["id"], "fp_name": fp["fp_name"],
                           "col": "E列(触发事件)", "actual": fp["trigger_event"], "expected": expected_te})
        subs = fp_subs(dim_db, fp["id"])
        actual_types = "".join(sp["data_move_type"] for sp in subs)
        expected_types = expected_ewx(fp["fp_name"])
        if expected_types and actual_types != expected_types:
            issues.append({"fp_id": fp["id"], "fp_name": fp["fp_name"],
                           "col": "EWX类型", "actual": actual_types, "expected": expected_types})
        for sp in subs:
            exp_desc, exp_group = derive_sub_columns(fp["fp_name"], initiator, sp["data_move_type"])
            if (sp["description"] or "") != exp_desc:
                issues.append({"fp_id": fp["id"], "fp_name": fp["fp_name"],
                               "col": f"子过程描述[{sp['sort_order']}:{sp['data_move_type']}]",
                               "actual": sp["description"], "expected": exp_desc})
            if (sp["data_group_name"] or "") != exp_group:
                issues.append({"fp_id": fp["id"], "fp_name": fp["fp_name"],
                               "col": f"数据组名[{sp['sort_order']}:{sp['data_move_type']}]",
                               "actual": sp["data_group_name"], "expected": exp_group})
    if fix and issues:
        from ..db import tx
        with tx(dim_db) as cur:
            for fp in fps:
                expected_fu = derive_functional_user(fp["level3"])
                initiator = initiator_of(expected_fu)
                cur.execute("UPDATE fps SET functional_user=%s, trigger_event=%s WHERE id=%s",
                            (expected_fu, derive_trigger_event(fp["fp_name"], initiator), fp["id"]))
                for sp in fp_subs(dim_db, fp["id"]):
                    exp_desc, exp_group = derive_sub_columns(fp["fp_name"], initiator, sp["data_move_type"])
                    cur.execute("UPDATE sub_processes SET description=%s, data_group_name=%s WHERE id=%s",
                                (exp_desc, exp_group, sp["id"]))
    return issues
