"""词库自动挖掘引擎（v2：四维度 + jieba 原子切分）。

背景（2026-08-30 实测）：
    vocab_terms 此前只有一处写入 —— scripts/migrate_from_hermes.py 的一次性迁移，
    此后 frequency 永久冻结。v1 引擎从 cosmic_active / cosmic_archive 回采术语与词频，
    但把 **整条 FP 名 / 数据组名 / 模块名** 当成了「业务词」入库，
    导致「已订购业务展示时段配置新增请求数据」这类复合串混进词库（2026-08-31 良哥指出）。

v2 改造（2026-08-31）：
    把词库拆成**四个清晰维度**，整句复合串不再进「业务词库」：

      ┌ 原子业务词元  ← 真正的词库主体
      │     · 来源1：sub_processes.data_attributes（顿号切分，本就是原子字段）
      │     · 来源2：用 jieba 把「业务对象 / 数据组名」复合串切成原子名词
      │               （以 data_attributes + 领域词典播种用户词典，保证切分尊重已知原子）
      ├ 业务对象      ← FP 名去动词后的中层对象（"已订购业务套餐"），参考级、非原子
      ├ 结构参考      ← 整条 FP 名 / 数据组名 / 三级模块名，纯导航/结构，非词库
      └ 功能维度      ← 用户角色 / 触发器模式

    维度(category_id) 无外键约束，可安全 UPDATE 重新归类；迁移脚本负责把旧分类术语
    重映射到新维度（保留 status/source/frequency，仅移 category_id）。

写入策略（幂等）：
  - 新词：status='candidate', source='mined' —— 一律待人审，不自动确认
  - 已存在词（uk_term）：只刷 frequency + category_id，不动 status/source
  - 重复执行结果一致，可放心挂定时任务
"""

import json
import re
from collections import Counter, defaultdict

try:
    import jieba
    jieba.setLogLevel(60)  # 安静：关掉 Building prefix dict 等日志
    _HAS_JIEBA = True
except Exception:                                          # pragma: no cover
    _HAS_JIEBA = False

from .. import config, db
from . import derive
from .derive import _pool_key

SOURCE_MINED = "mined"
STATUS_CANDIDATE = "candidate"

NOUN_MIN_LEN, NOUN_MAX_LEN = 2, 40
ATTR_MIN_LEN, ATTR_MAX_LEN = 2, 24
POOL_MIN_FIELDS = 3

# ── v2 四维度分类名（须与 vocab_categories 表 + 迁移脚本一致）──
CAT_ATOM = "原子业务词元"          # 真正的业务词库主体
CAT_OBJECT = "业务对象"            # FP 名去动词后的中层对象（参考）
CAT_STRUCT = "结构参考"            # 整条 FP 名 / 数据组名 / 三级模块名
CAT_FUNC = "功能维度"              # 用户角色 / 触发器模式

# 领域原子词表：播种给 jieba 的用户词典，让复合串切分时优先保留这些已知原子。
# （运行时还会动态并入 data_attributes 的全部字段，故此处只补 data_attributes 里没有的复合原子）
CURATED_ATOMS = {
    "已订购业务", "套餐", "归属地", "增值服务", "资费标签", "短信内容",
    "展示时段", "展示位置", "排序权重", "适用业务", "包含业务", "业务名称",
    "卡片类型", "标签颜色", "标签优先级", "规则状态", "规则名称", "分组名称",
    "审核状态", "签名标识", "排序字段", "用户号码", "业务编码", "配置记录编号",
    "生效时间", "失效时间", "更新时间", "创建时间", "创建人", "备注说明",
    "到期提醒", "订购产品包", "多月份展示", "业务短信内容", "业务排序规则",
    "套餐生效规则", "分组设置", "卡片业务", "用户订购", "产品包",
}

# 通用词素/结构后缀：单独出现不算业务词，切分时剔除。
# 这些大多是「信息/数据/设置/配置/请求/结果」类包装词，或 CRUD 动词。
STOP_MORPHEMES = {
    "信息", "数据", "设置", "配置", "请求", "结果", "响应",
    "记录", "列表", "详情", "效果", "缓存", "导入", "日志", "明细",
    "新增", "修改", "删除", "查询", "预览",
    "名称", "编号", "编码", "状态", "时间",
    "业务", "展示",            # 仅作词素出现时过泛，单独成词无业务词库价值
}

# 数据属性分隔符：中文顿号（导入接口已强制校验「,」非法）
_ATTR_SEP = re.compile(r"[、,，;；]+")
_NOISE = re.compile(r"^[\d\s\W_]+$")
_WS = re.compile(r"\s+")

# 零宽 / 不可见字符：实际数据里混进了 ZWNJ(U+200C) 之类（多半来自从网页/Excel 复制）。
# 坑：MySQL utf8mb4_unicode_ci 把这些字符的权重当作 0，
# 于是「自助视频春节卡片‍线上业务介绍配置信息」与「自助视频春节卡片线上业务介绍配置信息」
# 在 Python 侧是两个不同字符串、在 uk_term 唯一索引上却撞车报 1062。必须在入库前剥干净。
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


def _collect_atomic_terms(dimensions: tuple) -> set:
    """跨维度收集 sub_processes.data_attributes（顿号切分后的原子字段），
    作为 jieba 用户词典的播种集 + 原子词元来源。"""
    atoms: set = set(CURATED_ATOMS)
    for dim in dimensions:
        for row in db.query(dim, "SELECT data_attributes FROM sub_processes"):
            for f in _split_attrs(row["data_attributes"]):
                atoms.add(f)
    return atoms


def _seed_jieba(atoms: set):
    """把已知原子词播种进 jieba 用户词典，使复合串切分时优先保留这些原子。"""
    if not _HAS_JIEBA:
        return
    for w in atoms:
        if 1 < len(w) <= 16:
            # freq 给很高，压过 jieba 默认词频，确保「已订购业务套餐」切成 已订购业务+套餐 而非 已订购/业务/套餐
            jieba.add_word(w, freq=100000)


def _segment_atoms(text: str) -> set:
    """用 jieba 把复合业务串切成原子名词集合（供「原子业务词元」）。

    过滤规则：
      · 无 jieba 时退化为「整串若不在停用词素里就整体保留」（不丢信息，但粒度偏粗）
      · 长度 2~16
      · 剔除通用词素 STOP_MORPHEMES（信息/数据/设置/配置/请求/结果…）
      · 剔除纯 CRUD 动词/后缀
    播种了领域词典后，jieba 多产出已知原子，质量远好于朴素切分。
    """
    text = _clean(text, ATTR_MIN_LEN, ATTR_MAX_LEN + 20)
    if not text:
        return set()
    if not _HAS_JIEBA:
        return {text} if text not in STOP_MORPHEMES else set()
    out = set()
    for piece in jieba.lcut(text):
        piece = piece.strip()
        if len(piece) < ATTR_MIN_LEN or len(piece) > 16:
            continue
        if piece in STOP_MORPHEMES:
            continue
        if _NOISE.match(piece):
            continue
        out.add(piece)
    return out


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

    v2 四维度：原子业务词元 / 业务对象 / 结构参考 / 功能维度。
    调用方须先 _seed_jieba（在 mine() 里统一播种，跨维度共享词典）。
    """
    layout = detect_layout(dim_db)
    out = defaultdict(Counter)

    fp_col = layout["fp_col"]

    # 1) FP：整条名 → 结构参考；去动词后的对象 → 业务对象 + 原子切分 → 原子业务词元
    for row in db.query(dim_db, f"SELECT {fp_col} AS name FROM fps"):
        raw = _clean(row["name"], NOUN_MIN_LEN, NOUN_MAX_LEN + 20)
        if not raw:
            continue
        out[CAT_STRUCT][raw] += 1
        obj = _clean(_pool_key(raw), NOUN_MIN_LEN, NOUN_MAX_LEN)
        if obj and obj != raw:
            out[CAT_OBJECT][obj] += 1
            for atom in _segment_atoms(obj):
                out[CAT_ATOM][atom] += 1

    # 2) 数据属性字段 → 原子业务词元（本就是原子，直接收，不再二次切分以免破坏）
    #    数据组名 → 结构参考 + 原子切分 → 原子业务词元
    for row in db.query(dim_db,
                        "SELECT data_group_name, data_attributes FROM sub_processes"):
        for f in _split_attrs(row["data_attributes"]):
            out[CAT_ATOM][f] += 1
        group = _clean(_group_key(row["data_group_name"]), NOUN_MIN_LEN, NOUN_MAX_LEN)
        if group:
            out[CAT_STRUCT][group] += 1
            for atom in _segment_atoms(group):
                out[CAT_ATOM][atom] += 1

    # 3) 错位库时 functional_user / trigger_event / level3 是脏的，跳过
    if layout["aux_ok"]:
        for row in db.query(dim_db, "SELECT functional_user, trigger_event FROM fps"):
            for role in _parse_roles(row["functional_user"]):
                out[CAT_FUNC][role] += 1
            ev = _clean(row["trigger_event"], 4, 24)
            if ev:
                out[CAT_FUNC][ev] += 1
        for row in db.query(dim_db,
                            "SELECT level3 FROM modules WHERE level3 IS NOT NULL AND level3<>''"):
            lv3 = _clean(row["level3"], NOUN_MIN_LEN, NOUN_MAX_LEN)
            if lv3:
                out[CAT_STRUCT][lv3] += 1

    return dict(out), layout


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
    """执行一次全量挖掘，返回统计报告。可重复执行（幂等）。

    v2：先跨维度收集原子词播种 jieba，再逐维度 scan 出四维度词表。
    """
    _seed_jieba(_collect_atomic_terms(dimensions))

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

    known = {r["term"]: r["category_id"]
             for r in db.query(config.DB_STUDIO, "SELECT term, category_id FROM vocab_terms")}
    new_rows = [(t, c, f, n) for t, (c, f, n) in merged.items() if t not in known]
    # 已存在术语：刷新 frequency + 重归类 category_id 到新维度（v2 四维度迁移靠这一步落地）
    upd_rows = [(f, c, t) for t, (c, f, n) in merged.items() if t in known]

    # INSERT IGNORE 兜底：即便清洗后仍有在 utf8mb4_unicode_ci 下同权的奇异构词，
    # 也只是跳过该条，不会让整批挖掘因 1062 中断
    inserted = _chunked(new_rows, lambda batch: db.executemany(config.DB_STUDIO, """
        INSERT IGNORE INTO vocab_terms (term, category_id, frequency, source, status, notes)
        VALUES (%s,%s,%s,'mined','candidate',%s)
    """, batch))
    updated = _chunked(upd_rows, lambda batch: db.executemany(config.DB_STUDIO, """
        UPDATE vocab_terms SET frequency=%s, category_id=%s WHERE term=%s
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
        "new": inserted,
        "skipped": len(new_rows) - inserted,
        "updated": updated,
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
