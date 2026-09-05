"""质量门禁引擎：全量规范检查（移植 Hermes cosmic_cli.check_all / preflight）。

规范全部配置化，三处来源（优先级从高到低）：
  1. spec_rules 表（writing 类键值，PUT /api/studio/specs/{key} 即改即生效）
  2. rule_forbidden_words / rule_pseudo_fields 独立词表（可 API 增删）
  3. spec.py 种子值兜底（表被清空也不失守）
检查输出统一结构：{check, level, ref, message}，errors 非空 = 门禁不通过。
"""
from collections import defaultdict
import re

from . import derive, similarity, spec
from .. import config, db

FALLBACK_FORBIDDEN = ["记录", "日志", "导入", "缓存", "明细", "列表", "详情", "效果"]
FALLBACK_PSEUDO = [
    "点击事件", "切换方式", "自动播放", "加载方式", "冲突处理", "叠加规则", "互斥规则",
    "默认排序", "手动排序", "自动排序", "系统分组", "自定义分组", "自动续费",
    "客户姓名", "证件号码", "联系电话", "联系地址", "邮政编码", "客户邮箱", "客户性别",
    "客户年龄", "客户职业",
]


def forbidden_words() -> list:
    rows = db.query(config.DB_STUDIO, "SELECT word FROM rule_forbidden_words")
    return [r["word"] for r in rows] or FALLBACK_FORBIDDEN


def pseudo_fields() -> list:
    rows = db.query(config.DB_STUDIO, "SELECT word FROM rule_pseudo_fields")
    return [r["word"] for r in rows] or FALLBACK_PSEUDO


def _issue(check, level, ref, message):
    return {"check": check, "level": level, "ref": ref, "message": message}


def _split_fields(attrs: str) -> list:
    return [f.strip() for f in (attrs or "").split("、") if f.strip()]


def lint_project(dim_db: str, project_id: int, include_archive_similarity: bool = True) -> dict:
    issues = []
    warnings = []
    add = lambda *a: issues.append(_issue(*a))  # noqa: E731
    warn = lambda *a: warnings.append(_issue(*a))  # noqa: E731

    # ── 一次性载入规范 ──
    ewx_rules = spec.load_spec("ewx_rules")
    e_prefix = spec.load_spec("e_desc_prefix")
    x_prefix = spec.load_spec("x_desc_prefix")
    min_err = spec.load_spec("min_fields_error")
    min_warn = spec.load_spec("min_fields_warn")
    jac_threshold = spec.load_spec("jaccard_same_module")
    sim_same = spec.load_spec("sim_same_req")
    sim_cross = spec.load_spec("sim_cross_archive")
    e_exempt = spec.load_spec("e_class_exempt")
    crud_exempt = spec.load_spec("crud_sibling_exempt")
    cross_check = spec.load_spec("cross_archive_check") and include_archive_similarity \
        and dim_db == config.DB_ACTIVE
    pool_check = spec.load_spec("pool_coverage_check")

    proj = db.query(dim_db, "SELECT * FROM projects WHERE id=%s", (project_id,), one=True)
    if not proj:
        return {"errors": [_issue("项目", "error", f"project#{project_id}", "项目不存在")],
                "warnings": [], "summary": {"error": 1, "warn": 0, "pass": False}}
    fps = derive.project_fps(dim_db, project_id)
    if not fps:
        return {"errors": [_issue("数据", "error", f"project#{project_id}", "无FP数据")],
                "warnings": [], "summary": {"error": 1, "warn": 0, "pass": False}}
    subs_by_fp = {fp["id"]: derive.fp_subs(dim_db, fp["id"]) for fp in fps}
    all_subs = [sp for sps in subs_by_fp.values() for sp in sps]
    ban = forbidden_words()
    pseudo = pseudo_fields()

    # 1. F列格式
    for fp in fps:
        fu = fp["functional_user"] or ""
        ref = f"FP#{fp['id']} {fp['fp_name']}"
        if "\n" not in fu:
            add("F列格式", "error", ref, "缺少换行（发起者/接收者两行）")
        elif "发起者" not in fu or "接收者" not in fu:
            add("F列格式", "error", ref, f"缺少发起者/接收者: {fu[:50]!r}")
        else:
            lines = fu.split("\n")
            if len(lines) != 2:
                add("F列格式", "error", ref, f"行数={len(lines)}，应为2行")
            else:
                for line in lines:
                    if "：" not in line:
                        add("F列格式", "error", ref, f"缺冒号: {line}")

    # 2. E列格式
    for fp in fps:
        te = fp["trigger_event"] or ""
        ref = f"FP#{fp['id']} {fp['fp_name']}"
        if not te.endswith("时触发"):
            add("E列格式", "error", ref, f"不以'时触发'结尾: {te[:50]}")
        if "接收者" in te:
            add("E列格式", "error", ref, "含'接收者'")
        if "\n" in te:
            add("E列格式", "error", ref, "含换行")

    # 3. EWX 规范
    for fp in fps:
        types = "".join(sp["data_move_type"] for sp in subs_by_fp[fp["id"]])
        expected = ewx_rules.get(derive.fp_verb(fp["fp_name"])[0], "")
        if expected and types != expected:
            add("EWX规范", "error", f"FP#{fp['id']} {fp['fp_name']}",
                f"期望={expected} 实际={types or '无子过程'}")

    # 4. 子过程描述
    for sp in all_subs:
        desc = sp["description"] or ""
        ref = f"子过程#{sp['id']} {desc[:20]}"
        if "，" in desc:
            add("子过程描述", "error", ref, "含断句'，'")
        if sp["data_move_type"] == "E" and e_prefix and not desc.startswith(e_prefix):
            add("子过程描述", "error", ref, f"E类不以'{e_prefix}'开头")
        if sp["data_move_type"] == "X" and x_prefix and not desc.startswith(x_prefix):
            add("子过程描述", "error", ref, f"X类不以'{x_prefix}'开头")

    # 5. 数据组名后缀
    for sp in all_subs:
        dgn = sp["data_group_name"] or ""
        dmt = sp["data_move_type"]
        fp_name = next((f["fp_name"] for f in fps if f["id"] == sp["fp_id"]), "")
        verb = fp_name[:2]
        ref = f"子过程#{sp['id']} {dgn}"
        groups = spec.load_spec("data_group_templates")
        # 期望后缀 = 模板去掉 {obj}{verb} 后的固定部分
        gkey = f"{dmt}_预览" if verb == "预览" and f"{dmt}_预览" in groups else dmt
        suffix = groups.get(gkey, "").format(obj="", verb="").replace("{obj}", "").replace("{verb}", "")
        if suffix and not dgn.endswith(suffix):
            add("数据组后缀", "error", ref, f"{dmt}类后缀应为'{suffix}'")

    # 6/7. 数据属性：分隔符 + 字段数
    for sp in all_subs:
        attrs = sp["data_attributes"] or ""
        ref = f"子过程#{sp['id']} {fp_name_of(fps, sp['fp_id'])}"
        if "," in attrs:
            add("数据属性", "error", ref, "含逗号分隔符，必须用、")
        n = len(_split_fields(attrs))
        if n < min_err:
            add("数据属性", "error", ref, f"字段数={n}，至少{min_err}个")
        elif min_warn and n < min_warn:
            warn("数据属性", "warn", ref, f"字段数={n}，建议≥{min_warn}")

    # 8. 禁词（仅 FP 名）
    for fp in fps:
        for w in ban:
            if w in fp["fp_name"]:
                add("禁词", "error", f"FP#{fp['id']} {fp['fp_name']}", f"FP名含禁词'{w}'")

    # 9. 伪字段/PII（K列字段级）
    for sp in all_subs:
        for f in _split_fields(sp["data_attributes"]):
            if f in pseudo:
                add("伪字段", "error", f"子过程#{sp['id']}", f"字段'{f}'命中黑名单（行为/统计/PII）")

    # 10. 属性池化差异化
    fp_attrs = {fid: [sp["data_attributes"] for sp in sps] for fid, sps in subs_by_fp.items()}
    for fid, sps in subs_by_fp.items():
        fp_name = fp_name_of(fps, fid)
        attr_sets = [tuple(sorted(_split_fields(sp["data_attributes"]))) for sp in sps]
        if len(set(attr_sets)) < len(attr_sets):
            verb = fp_name[:2]
            if verb == "查询" and len(sps) == 3 and attr_sets[0] != attr_sets[1] and attr_sets[0] != attr_sets[2]:
                pass  # 查询FP：E 与 R/X 部分重叠可豁免
            else:
                add("属性池化", "error", f"FP#{fid} {fp_name}", "存在完全重复的属性集合")
    module_fps = defaultdict(list)
    for fp in fps:
        module_fps[fp["module_id"]].append(fp)
    for _mid, mfps in module_fps.items():
        if len(mfps) < 2:
            continue
        for i in range(len(mfps)):
            for j in range(i + 1, len(mfps)):
                a, b = mfps[i], mfps[j]
                if a["fp_name"][:2] != b["fp_name"][:2]:
                    continue
                sa = set((fp_attrs.get(a["id"], [""])[0] or "").split("、")) - {""}
                sb = set((fp_attrs.get(b["id"], [""])[0] or "").split("、")) - {""}
                if sa and sb:
                    jac = similarity.jaccard(sa, sb)
                    if jac >= jac_threshold:
                        add("属性池化", "error", f"FP#{a['id']} ↔ FP#{b['id']}",
                            f"同模块同动词属性Jaccard={jac:.2f}≥{jac_threshold}: {a['fp_name']} ↔ {b['fp_name']}")

    # 11. 相似度：同需求内 FP 名 + 子过程描述
    def fp_crud_exempt(a, b):
        if not crud_exempt:
            return False
        return a["verb"] != b["verb"]

    fp_items = [{"key": f"FP#{f['id']}", "text": f["fp_name"], "verb": f["fp_name"][:2],
                 "exempt": False} for f in fps]
    for a, b, r in similarity.pairwise_same_project(fp_items, sim_same, exempt_pair=fp_crud_exempt):
        add("相似度", "error", f"{a['key']} ↔ {b['key']}",
            f"FP名相似度 {r:.0%} 超同需求阈值 {sim_same:.0%}")
    sub_items = [{"key": f"子过程#{sp['id']}", "text": sp["description"],
                  "exempt": e_exempt and sp["data_move_type"] == "E"}
                 for sp in all_subs if sp["description"]]
    for a, b, r in similarity.pairwise_same_project(sub_items, sim_same):
        add("相似度", "error", f"{a['key']} ↔ {b['key']}",
            f"子过程描述相似度 {r:.0%} 超同需求阈值 {sim_same:.0%}")

    # 12. 跨库相似度（编写库 ↔ 归档库）
    if cross_check:
        arch_fps = db.query(config.DB_ARCHIVE, """
            SELECT f.fp_name, p.requirement_id FROM fps f
            JOIN modules m ON m.id = f.module_id
            JOIN projects p ON p.id = m.project_id
            ORDER BY p.id, m.sort_order, f.sort_order
        """)
        arch_items = [{"key": f"归档[{r['requirement_id']}]", "text": r["fp_name"]} for r in arch_fps]
        for a, b, r in similarity.cross_archive(fp_items, arch_items, sim_cross):
            add("跨库相似度", "warn", f"{a['key']} ↔ {b['key']}",
                f"与归档FP'{b['text'][:30]}'相似度 {r:.0%} 超跨批次阈值 {sim_cross:.0%}")

    # 13. 字段池覆盖（preflight 同款，warn 级）
    if pool_check and dim_db == config.DB_ACTIVE:
        pools = derive.load_pools()
        for sp in all_subs:
            if sp["data_group_name"] and not derive.pool_for(sp["data_group_name"], pools):
                warn("字段池", "warn", f"子过程#{sp['id']}",
                     f"数据组'{sp['data_group_name']}'未在字段池中定义")

    # 14. 自定义门禁规则（对话可录入，spec 驱动，改完立即生效）
    _eval_custom_rules(spec.load_spec("custom_rules"), fps, all_subs, add, warn, _split_fields)

    summary = {"error": len(issues), "warn": len(warnings), "pass": not issues}
    return {"errors": issues, "warnings": warnings, "summary": summary}


def fp_name_of(fps, fp_id):
    for f in fps:
        if f["id"] == fp_id:
            return f["fp_name"]
    return ""


def _eval_custom_rules(rules, fps, all_subs, add, warn, split_fields):
    """评估对话录入的自定义门禁规则（spec.custom_rules 列表）。
    规则类型：fp_name_regex / sub_desc_regex / attr_regex / fp_name_prefix / sub_desc_min_len。
    """
    if not isinstance(rules, list):
        return
    for rule in rules:
        if not isinstance(rule, dict):
            continue
        rid = rule.get("id", "?")
        rname = rule.get("name", rid)
        rtype = rule.get("type")
        pattern = rule.get("pattern")
        severity = rule.get("severity", "error")
        emit = add if severity == "error" else warn
        try:
            if rtype == "fp_name_regex" and pattern:
                rx = re.compile(pattern)
                for fp in fps:
                    if rx.search(fp["fp_name"] or ""):
                        emit("自定义规则", severity, f"FP#{fp['id']} {fp['fp_name']}",
                             f"[{rname}] FP名命中正则 {pattern!r}")
            elif rtype == "sub_desc_regex" and pattern:
                rx = re.compile(pattern)
                for sp in all_subs:
                    if rx.search(sp["description"] or ""):
                        emit("自定义规则", severity, f"子过程#{sp['id']}",
                             f"[{rname}] 子过程描述命中正则 {pattern!r}")
            elif rtype == "attr_regex" and pattern:
                rx = re.compile(pattern)
                for sp in all_subs:
                    for f in split_fields(sp["data_attributes"]):
                        if rx.search(f):
                            emit("自定义规则", severity, f"子过程#{sp['id']}",
                                 f"[{rname}] 字段'{f}'命中正则 {pattern!r}")
                            break
            elif rtype == "fp_name_prefix" and pattern:
                prefixes = [p.strip() for p in str(pattern).split(",") if p.strip()]
                for fp in fps:
                    if any((fp["fp_name"] or "").startswith(p) for p in prefixes):
                        emit("自定义规则", severity, f"FP#{fp['id']} {fp['fp_name']}",
                             f"[{rname}] FP名命中禁用前缀 {prefixes}")
            elif rtype == "sub_desc_min_len" and pattern:
                n = int(pattern)
                for sp in all_subs:
                    d = sp["description"] or ""
                    if len(d) < n:
                        emit("自定义规则", severity, f"子过程#{sp['id']}",
                             f"[{rname}] 子过程描述长度 {len(d)} < {n}")
        except re.error:
            warn("自定义规则", "warn", f"规则#{rid}", f"[{rname}] 正则非法: {pattern!r}")
        except (ValueError, TypeError):
            warn("自定义规则", "warn", f"规则#{rid}", f"[{rname}] 参数非法: {pattern!r}")
