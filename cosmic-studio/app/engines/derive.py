"""推导引擎：从 Hermes cosmic_derive.py / cosmic_cli.py 移植。

可推导列规则（与校验器 linter.py 保持同一套约定，derive 产出必须能过 check）：
  F列(功能用户)  配置管理类→一线坐席 / 数据查询类→终端用户 / 其他→终端用户（cli 默认）
  E列(触发事件)  {发起者}{FP名}时触发
  EWX           新增/修改/删除=EW，查询/预览=ERX
  H列(子过程描述) E=接收{发起者}发起{FP名}请求 / W按动词 / R=读取{对象}详情 / X=返回{FP名}结果
  J列(数据组)    {业务对象}{动词}{后缀}，后缀按 E/W/R/X + 预览特判
  K列(数据属性)  字段池差异化（md5(fp_id) 种子，可复现）
"""
import hashlib
import random
import re

from .. import config, db

VERB_RE = re.compile(r"^(新增|修改|删除|查询|预览)")
SUFFIX_RE = re.compile(r"(查询请求数据|查询数据|查询结果|预览查询数据|预览结果数据|数据)$")
EWX_RULES = {"新增": "EW", "修改": "EW", "删除": "EW", "查询": "ERX", "预览": "ERX"}
ALLOWED_VERBS = ["新增", "修改", "删除", "查询", "预览", "同步", "导出"]


def fp_verb(fp_name: str):
    m = VERB_RE.match(fp_name or "")
    if m:
        return m.group(1), (fp_name or "")[2:]
    return None, fp_name or ""


def expected_ewx(fp_name: str) -> str:
    verb, _ = fp_verb(fp_name)
    return EWX_RULES.get(verb, "")


def derive_functional_user(level3: str) -> str:
    if "配置管理" in (level3 or ""):
        initiator = "一线坐席"
    elif "数据查询" in (level3 or ""):
        initiator = "终端用户"
    else:
        initiator = "终端用户"  # cli add-fp 默认
    return f"发起者：{initiator}\n接收者：{config.DEFAULT_RECEIVER}"


def initiator_of(functional_user: str) -> str:
    fu = functional_user or ""
    if "\n" in fu and "发起者：" in fu:
        return fu.split("\n")[0].split("：", 1)[1]
    return config.DEFAULT_INITIATOR


def derive_trigger_event(fp_name: str, initiator: str) -> str:
    return f"{initiator}{fp_name}时触发"


def derive_sub_columns(fp_name: str, initiator: str, move_type: str):
    """返回 (H列描述, J列数据组名)，业务对象=FP名去掉动词。"""
    verb, obj = fp_verb(fp_name)
    if move_type == "E":
        return f"接收{initiator}发起{fp_name}请求", f"{obj}{verb}请求数据"
    if move_type == "W":
        if verb == "新增":
            desc = f"新增{obj}到数据库"
        elif verb == "修改":
            desc = f"修改数据库中{obj}记录"
        elif verb == "删除":
            desc = f"从数据库中删除{obj}记录"
        else:
            desc = f"保存{obj}到数据库"
        return desc, f"{obj}{verb}数据"
    if move_type == "R":
        suffix = "预览查询数据" if verb == "预览" else "查询数据"
        return f"读取{obj}详情", f"{obj}{suffix}"
    if move_type == "X":
        suffix = "预览结果数据" if verb == "预览" else "查询结果"
        return f"返回{fp_name}结果", f"{obj}{suffix}"
    return "", ""


def standard_subs_for(fp_name: str, initiator: str):
    """按 EWX 规范生成标准子过程序列 [(move_type, desc, group)]。"""
    types = expected_ewx(fp_name)
    out = []
    for mt in types:
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
    with tx_ctx(dim_db) as cur:
        for sp in subs:
            fields = diversify_sub_attributes(sp["data_move_type"], fp_fields, rng, used)
            cur.execute("UPDATE sub_processes SET data_attributes=%s WHERE id=%s",
                        ("、".join(fields), sp["id"]))
    return True


def tx_ctx(db_name: str):
    from ..db import tx
    return tx(db_name)


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
    """检查/修复可推导列，返回 issues 列表（与 cosmic_derive.py 同口径）。"""
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
        with tx_ctx(dim_db) as cur:
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
