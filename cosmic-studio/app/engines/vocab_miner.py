"""词库自动挖掘引擎。

背景（2026-08-30 实测）：
    vocab_terms 此前只有**一处**写入 —— scripts/migrate_from_hermes.py 的一次性迁移。
    此后既无录入 API、无前端入口、也无定时任务，frequency 永久冻结：
    6379 条里 4807 条（75.4%）frequency<=0，真实有词频的仅约 100 条。

本引擎从 cosmic_active / cosmic_archive 的真实度量数据回采术语与词频：

    fps.fp_name                    → FP名参考          （原样）
                                   → 业务名词          （_pool_key 去动词/后缀后按词频分档）
    modules.level3                 → 三级模块名
    sub_processes.data_group_name  → 数据组名          （_pool_key 归一化）
    sub_processes.data_attributes  → 数据属性字段      （按中文顿号切分）
    fps.functional_user            → 用户角色          （解析「发起者：X」）
    fps.trigger_event              → 触发器模式        （仅收短句）

写入策略（幂等）：
  - 新词：status='candidate', source='mined' —— 一律待人审，不自动确认
  - 已存在词（uk_term）：**只刷 frequency，不动 status/source** ——
    既修好了 4807 条零频词，又不会覆盖人工已确认的结果
  - 重复执行结果一致，可放心挂定时任务

同时回灌 attr_pools：按归一化后的数据组聚合去重字段并集，修复字段数不足的池。
"""

import json
import re
from collections import Counter, defaultdict

from .. import config, db
from . import derive
from .derive import _pool_key

SOURCE_MINED = "mined"
STATUS_CANDIDATE = "candidate"

NOUN_MIN_LEN, NOUN_MAX_LEN = 2, 40
ATTR_MIN_LEN, ATTR_MAX_LEN = 2, 24
POOL_MIN_FIELDS = 3

# 业务名词按词频归档：[(阈值, 分类名)]，从高到低匹配，兜底低频。
# 阈值按当前语料规模标定（active 79 FP / 183 子过程，archive 3780 FP）：
# 实测名词词频集中在 1~8，用 100/20 会让全部落进低频、分档失去意义。
# 语料规模上一个数量级后应上调这里。
NOUN_BANDS = ((8, "高频核心名词"), (3, "中频业务名词"))
NOUN_BAND_DEFAULT = "低频业务名词"

# 占位键：业务名词要先统计完词频才能定档
_NOUN_PENDING = "__noun__"

# 数据属性分隔符：中文顿号（导入接口已强制校验「,」非法）
_ATTR_SEP = re.compile(r"[、,，;；]+")
_NOISE = re.compile(r"^[\d\s\W_]+$")
_WS = re.compile(r"\s+")

# 零宽 / 不可见字符：实际数据里混进了 ZWNJ(U+200C) 之类（多半来自从网页/Excel 复制）。
# 坑：MySQL utf8mb4_unicode_ci 把这些字符的权重当作 0，
# 于是「自助视频春节卡片‍线上业务介绍配置信息」与「自助视频春节卡片线上业务介绍配置信息」
# 在 Python 侧是两个不同字符串、在 uk_term 唯一索引上却撞车报 1062。
# 必须在入库前剥干净。
_INVISIBLE = re.compile(
    "[%s]" % "".join(re.escape(chr(c)) for c in (
        0x00AD,                                    # soft hyphen
        0x200B, 0x200C, 0x200D, 0x200E, 0x200F,    # 零宽空格/ZWNJ/ZWJ/LRM/RLM
        0x202A, 0x202B, 0x202C, 0x202D, 0x202E,    # 双向文本控制符
        0x2060, 0xFEFF,                            # word joiner / BOM
    )))
_BATCH = 500


def _clean(s, lo: int, hi: int) -> str:
    """去不可见字符 + 去空白 + 长度过滤 + 噪声过滤，返回空串表示丢弃。"""
    s = _INVISIBLE.sub("", s or "")
    s = _WS.sub("", s.strip())
    if not s or len(s) < lo or len(s) > hi:
        return ""
    if _NOISE.match(s):
        return ""
    return s


def _split_attrs(raw: str) -> list:
    out = []
    for f in _ATTR_SEP.split(raw or ""):
        f = _clean(f, ATTR_MIN_LEN, ATTR_MAX_LEN)
        if f:
            out.append(f)
    return out


def _parse_roles(functional_user: str) -> list:
    """functional_user 形如「发起者：一线坐席\\n接收者：多媒体卡片平台」。"""
    out = []
    for line in re.split(r"[\n\r]+", functional_user or ""):
        val = line.split("：")[-1].split(":")[-1]
        val = _clean(val, 2, 16)
        if val:
            out.append(val)
    return out


# 尾部 CRUD 后缀：derive._pool_key 只剥「前缀动词 + 一组固定后缀」，
# 处理不了动词在尾部的「已订购卡片排序规则修改请求」——
# 结果同一个数据组被拆成 新增/修改/删除/查询 四个碎片池，每组字段数被摊薄。
_TRAIL_CRUD = re.compile(r"(新增|修改|删除|查询|预览)(请求|结果|响应|数据)*$")
MIN_GROUP_LEN = 4


def _group_key(name: str) -> str:
    """数据组聚合键：在 derive._pool_key 之上再剥掉尾部的 CRUD 后缀。

    只用于「数据组名」分类与字段池聚合，**不去改 derive.py** ——
    pool_for() 走的是子串匹配，聚合后的短键反而更容易命中，没必要动它。
    """
    s = _pool_key(name)
    for _ in range(3):                      # 反复剥，处理「…新增请求数据」这类多层后缀
        stripped = _TRAIL_CRUD.sub("", s).strip()
        if not stripped or stripped == s or len(stripped) < MIN_GROUP_LEN:
            break
        s = stripped
    return s


def _noun_band(freq: int) -> str:
    for threshold, name in NOUN_BANDS:
        if freq >= threshold:
            return name
    return NOUN_BAND_DEFAULT


def detect_layout(dim_db: str) -> dict:
    """探测该功能过程表的列语义是否错位。

    实测（2026-08-30）：归档库 cosmic_archive 导入时列整体错位 ——

        fp_name        装的是「输入…新增请求」      实为子过程描述/数据组
        functional_user 装的是「操作员…时触发」     实为触发器事件
        trigger_event  装的是「新增视频流量…信息」  实为真正的 FP 名
        modules.level3 装的是触发器/功能用户（还带 &#10; 这类 HTML 转义）

    编写库 cosmic_active 完全正常（fp_name 79/79 以动词开头）。

    这里**不硬编码「archive 就是坏的」**，而是统计 fp_name 以允许动词开头的比例，
    低于 50% 即判定列错位、改用 trigger_event 作为 FP 名来源。库修好后会自动走回正路。
    """
    total = db.query(dim_db, "SELECT COUNT(*) AS n FROM fps", one=True)["n"]
    if not total:
        return {"total": 0, "verb_prefixed": 0, "shifted": False,
                "fp_col": "fp_name", "aux_ok": True}
    verbs = tuple(derive.allowed_verbs())
    rows = db.query(dim_db, "SELECT fp_name FROM fps")
    hit = sum(1 for r in rows if (r["fp_name"] or "").startswith(verbs))
    shifted = hit * 2 < total
    return {
        "total": total,
        "verb_prefixed": hit,
        "shifted": shifted,
        "fp_col": "trigger_event" if shifted else "fp_name",
        "aux_ok": not shifted,     # 错位时 functional_user / trigger_event / level3 全是脏的
    }


def scan(dim_db: str) -> tuple[dict, dict]:
    """扫描一个维度库，返回 ({分类名: Counter(term -> freq)}, layout)。

    业务名词统一落在 _NOUN_PENDING 占位键下，由 _finalize 按词频分档后摊平。
    """
    layout = detect_layout(dim_db)
    out = defaultdict(Counter)

    fp_col = layout["fp_col"]
    for row in db.query(dim_db, f"SELECT {fp_col} AS name FROM fps"):
        name = _clean(row["name"], NOUN_MIN_LEN, NOUN_MAX_LEN + 20)
        if not name:
            continue
        out["FP名参考"][name] += 1
        obj = _clean(_pool_key(name), NOUN_MIN_LEN, NOUN_MAX_LEN)
        if obj and obj != name:
            out[_NOUN_PENDING][obj] += 1

    if layout["aux_ok"]:
        for row in db.query(dim_db, "SELECT functional_user, trigger_event FROM fps"):
            for role in _parse_roles(row["functional_user"]):
                out["用户角色"][role] += 1
            ev = _clean(row["trigger_event"], 4, 24)
            if ev:
                out["触发器模式"][ev] += 1
        for row in db.query(dim_db,
                            "SELECT level3 FROM modules WHERE level3 IS NOT NULL AND level3<>''"):
            lv3 = _clean(row["level3"], NOUN_MIN_LEN, NOUN_MAX_LEN)
            if lv3:
                out["三级模块名"][lv3] += 1

    for row in db.query(dim_db,
                        "SELECT data_group_name, data_attributes FROM sub_processes"):
        group = _clean(_group_key(row["data_group_name"]), NOUN_MIN_LEN, NOUN_MAX_LEN)
        if group:
            out["数据组名"][group] += 1
        for f in _split_attrs(row["data_attributes"]):
            out["数据属性字段"][f] += 1

    return _finalize(out), layout


def _finalize(scanned: dict) -> dict:
    """把 _NOUN_PENDING 按词频分档摊平到 高频/中频/低频业务名词。"""
    pending = scanned.pop(_NOUN_PENDING, None)
    if pending:
        for term, freq in pending.items():
            scanned[_noun_band(freq)][term] += freq
    return dict(scanned)


def _cat_ids() -> dict:
    rows = db.query(config.DB_STUDIO, "SELECT id, name FROM vocab_categories")
    return {r["name"]: r["id"] for r in rows}


def _chunked(seq: list, fn) -> int:
    """分批执行，返回累计影响行数。"""
    total = 0
    for i in range(0, len(seq), _BATCH):
        total += fn(seq[i:i + _BATCH]) or 0
    return total


def mine(dimensions=("cosmic_active", "cosmic_archive"), sync_pools: bool = True) -> dict:
    """执行一次全量挖掘，返回统计报告。可重复执行（幂等）。"""
    cats = _cat_ids()
    merged: dict[str, tuple] = {}       # term -> (category_id, freq, notes)
    per_cat: Counter = Counter()
    layouts = {}

    for dim in dimensions:
        scanned, layout = scan(dim)
        layouts[dim] = layout
        for cat_name, counter in scanned.items():
            cid = cats.get(cat_name)
            if cid is None:
                continue                      # 分类字典里没有就跳过，不臆造分类
            for term, freq in counter.items():
                prev = merged.get(term)
                if prev is None or freq > prev[1]:
                    merged[term] = (cid, freq, "auto-mined")
                per_cat[cat_name] += 1

    if not merged:
        return {"scanned": 0, "new": 0, "updated": 0, "by_category": {},
                "pools": 0, "layouts": layouts}

    known = {r["term"] for r in db.query(config.DB_STUDIO, "SELECT term FROM vocab_terms")}
    new_rows = [(t, c, f, n) for t, (c, f, n) in merged.items() if t not in known]
    upd_rows = [(f, t) for t, (c, f, n) in merged.items() if t in known]

    # INSERT IGNORE 兜底：即便清洗后仍有在 utf8mb4_unicode_ci 下同权的奇异构词，
    # 也只是跳过该条，不会让整批挖掘因 1062 中断
    inserted = _chunked(new_rows, lambda batch: db.executemany(config.DB_STUDIO, """
        INSERT IGNORE INTO vocab_terms (term, category_id, frequency, source, status, notes)
        VALUES (%s,%s,%s,'mined','candidate',%s)
    """, batch))
    updated = _chunked(upd_rows, lambda batch: db.executemany(config.DB_STUDIO, """
        UPDATE vocab_terms SET frequency=%s WHERE term=%s
    """, batch))

    pools = 0
    if sync_pools:
        pools = _sync_pools(dimensions)

    still_zero = db.query(config.DB_STUDIO,
                          "SELECT COUNT(*) AS n FROM vocab_terms WHERE frequency<=0", one=True)["n"]
    candidates = db.query(config.DB_STUDIO,
                          "SELECT COUNT(*) AS n FROM vocab_terms WHERE status='candidate'",
                          one=True)["n"]

    return {
        "scanned": len(merged),
        "new": inserted,                      # 实际落库条数（IGNORE 掉的未计入）
        "skipped": len(new_rows) - inserted,  # 被 IGNORE 的（唯一键同权冲突）
        "updated": updated,                   # 刷新了词频的存量词条
        "by_category": dict(per_cat),
        "pools": pools,
        "pending_review": candidates,
        "still_zero_freq": still_zero,
        "layouts": layouts,
    }


def _sync_pools(dimensions) -> int:
    """按归一化数据组聚合去重字段，与库里已有字段**取并集**回灌。

    ⚠️ 必须是并集。第一版写成 ON DUPLICATE KEY UPDATE fields=VALUES(fields) 直接覆盖，
    把 Hermes 迁移来的 16 个 curated 池（最大 30 字段）压成了 7~14 字段，
    池化差异化空间直接腰斩 —— 已回滚并改为并集，此处留档。
    """
    agg = defaultdict(list)
    for dim in dimensions:
        for row in db.query(dim, "SELECT data_group_name, data_attributes FROM sub_processes"):
            key = _clean(_group_key(row["data_group_name"]), NOUN_MIN_LEN, NOUN_MAX_LEN)
            if not key:
                continue
            for f in _split_attrs(row["data_attributes"]):
                if f not in agg[key]:
                    agg[key].append(f)

    # 必须走 db.json_list：JSON 列取回来是 str，list(str) 会拆成单个字符
    existing = {r["data_group"]: db.json_list(r["fields"])
                for r in db.query(config.DB_STUDIO,
                                  "SELECT data_group, fields FROM attr_pools")}
    rows = []
    for key, fields in agg.items():
        if len(fields) < POOL_MIN_FIELDS:
            continue
        merged = list(existing.get(key, []))
        for f in fields:
            if f not in merged:
                merged.append(f)
        if set(merged) == set(existing.get(key, [])):
            continue                     # 无新增字段就别刷 updated_at，保持可观测
        rows.append((key, json.dumps(merged, ensure_ascii=False)))

    _chunked(rows, lambda batch: db.executemany(config.DB_STUDIO, """
        INSERT INTO attr_pools (data_group, fields) VALUES (%s,%s)
        ON DUPLICATE KEY UPDATE fields=VALUES(fields)
    """, batch))
    return len(rows)
