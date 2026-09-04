"""规范中心：编写规范 + 截图规范全部配置化，存 cosmic_studio.spec_rules。

原则：规范即数据。linter/derive 每次执行时读表，改规范立即生效，不重启服务。
代码里只保留种子值（SEED_SPECS）做兜底——表里被删的键自动回落种子，
保证引擎永远有完整规范可用。评审反哺 = PUT /api/studio/specs/{key}。
"""
import json

from .. import config, db

SEED_SPECS = {
    # ──────────────── 编写规范 writing ────────────────
    "ewx_rules": {
        "category": "writing", "description": "FP 动词 → 数据移动类型组合（EWX 规范）",
        "value": {"新增": "EW", "修改": "EW", "删除": "EW", "查询": "ERX", "预览": "ERX"},
    },
    "allowed_verbs": {
        "category": "writing", "description": "FP 名允许的开头动词",
        "value": ["新增", "修改", "删除", "查询", "预览", "同步", "导出"],
    },
    "sub_move_types": {
        "category": "writing", "description": "合法数据移动类型",
        "value": ["E", "W", "R", "X"],
    },
    "trigger_event_template": {
        "category": "writing", "description": "E列触发事件模板，变量 {initiator} {fp_name}",
        "value": "{initiator}{fp_name}时触发",
    },
    "functional_user_map": {
        "category": "writing",
        "description": "F列功能用户推导：三级模块关键词→发起者；default_initiator 兜底",
        "value": {"matches": [{"keyword": "配置管理", "initiator": "一线坐席"},
                              {"keyword": "数据查询", "initiator": "终端用户"}],
                  "default_initiator": "终端用户",
                  "receiver": "多媒体卡片平台"},
    },
    "sub_desc_templates": {
        "category": "writing",
        "description": "H列子过程描述模板，变量 {initiator} {fp_name} {obj}；W 按动词细分，W_default 兜底",
        "value": {"E": "接收{initiator}发起{fp_name}请求",
                  "W_新增": "新增{obj}到数据库", "W_修改": "修改数据库中{obj}记录",
                  "W_删除": "从数据库中删除{obj}记录", "W_default": "保存{obj}到数据库",
                  "R": "读取{obj}详情", "X": "返回{fp_name}结果"},
    },
    "data_group_templates": {
        "category": "writing",
        "description": "J列数据组名模板，变量 {obj} {verb}；R/X 预览类特判；J列不合并每行独立",
        "value": {"E": "{obj}{verb}请求数据", "W": "{obj}{verb}数据",
                  "R": "{obj}查询数据", "R_预览": "{obj}预览查询数据",
                  "X": "{obj}查询结果", "X_预览": "{obj}预览结果数据"},
    },
    "e_desc_prefix": {"category": "writing", "description": "E类子过程描述必须以此开头",
                      "value": "接收"},
    "x_desc_prefix": {"category": "writing", "description": "X类子过程描述必须以此开头",
                      "value": "返回"},
    "min_fields_error": {"category": "writing", "description": "数据属性字段数下限（低于=error）",
                         "value": 3},
    "min_fields_warn": {"category": "writing", "description": "数据属性字段数建议值（低于=warn）",
                        "value": 4},
    "jaccard_same_module": {
        "category": "writing",
        "description": "同模块同动词 FP 首子过程属性集合 Jaccard 报警阈值",
        "value": 0.85},
    "sim_same_req": {"category": "writing",
                     "description": "同需求内 FP名/子过程描述 相似度阈值（difflib）", "value": 0.65},
    "sim_cross_archive": {"category": "writing",
                          "description": "编写库↔归档库 FP 名相似度阈值", "value": 0.85},
    "e_class_exempt": {"category": "writing",
                       "description": "E类子过程固定格式相似度豁免（稽核规范固定写法，禁改动降相似）",
                       "value": True},
    "crud_sibling_exempt": {"category": "writing",
                            "description": "同需求 FP 名：动词不同视为合法 CRUD 家族，不比对",
                            "value": True},
    "cross_archive_check": {"category": "writing",
                            "description": "是否启用编写库↔归档库跨库相似度检查", "value": True},
    "pool_coverage_check": {"category": "writing",
                            "description": "数据组未建字段池时是否告警（warn 级）", "value": True},
    # ──────────────── 截图规范 screenshot（P1 消费，先入表）────────────────
    "screenshot_arch_styles": {
        "category": "screenshot",
        "description": "代码骨架架构风格池，按 fp_id md5 稳定轮换，禁千篇一律",
        "value": {"rotation": "md5_fp_id", "styles": [
            {"name": "SpringBoot-PostController", "annotations": "@RestController + @PostMapping",
             "structure": "Controller层，@RequestBody 接参"},
            {"name": "SpringBoot-GetController", "annotations": "@RestController + @GetMapping/@DeleteMapping",
             "structure": "Controller层，@RequestParam/@PathVariable 接参"},
            {"name": "Service层", "annotations": "@Service + @Autowired",
             "structure": "接口 + 实现类，@Override"},
            {"name": "Mapper-DAO层", "annotations": "@Mapper + @Select/@Insert/@Update/@Delete",
             "structure": "MyBatis 注解，SQL 写在注解里"},
            {"name": "Controller+Service组合", "annotations": "@RestController + @Autowired Service",
             "structure": "Controller 调 Service 处理"},
            {"name": "MyBatis-Plus", "annotations": "@Service extends ServiceImpl",
             "structure": "this.save/updateById/removeById/list"}]},
    },
    "screenshot_comment_styles": {
        "category": "screenshot",
        "description": "每个 FP 代码必须覆盖的注释风格（8 种，可少不可无）",
        "value": {"required": ["javadoc", "block", "separator", "line_end",
                               "inline", "todo", "numbered_steps", "business_desc"],
                  "min_standalone": 1,
                  "content_rule": "注释只围绕数据属性写，禁止出现功能过程/子过程描述/FP名/步骤编号"},
    },
    "screenshot_comment_phrases": {
        "category": "screenshot",
        "description": "按 E/W/R/X 的注释句式池，md5(fp_id-seq) 稳定轮换避免千篇一律",
        "value": {"rotation": "md5_fp_id_seq", "pools": {
            "E": ["参数接收与对象封装", "读取请求参数并组装实体", "入参校验后构建数据对象"],
            "W": ["实体构建并写入库表", "数据持久化到数据库", "调用 Mapper 写入配置"],
            "R": ["按条件查询库表数据", "根据筛选条件检索记录", "从数据库读取匹配数据"],
            "X": ["组装返回结果并响应", "构建响应体返回前端", "打包结果数据供调用方使用"]}},
    },
    "screenshot_size_tiers": {
        "category": "screenshot", "description": "按代码行数自动选截图尺寸（宽×高）",
        "value": [{"max_lines": 9, "width": 500, "height": 125},
                  {"max_lines": 13, "width": 500, "height": 175},
                  {"max_lines": 20, "width": 500, "height": 225}],
    },
    "screenshot_render": {
        "category": "screenshot",
        "description": "渲染规范：IDEA Darcula 风格、无行号无 tab 栏仅代码本体、每FP一张嵌L列",
        "value": {"style": "idea-darcula", "line_numbers": False, "tab_bar": False,
                  "images_per_fp": 1, "column": "L",
                  "start_lineno_range": [42, 487]},
    },
    "screenshot_code_rules": {
        "category": "screenshot",
        "description": "代码密度与形态：真实代码>注释、MyBatis 不用 DataSource、完整方法体、英文短类名",
        "value": {"orm": "mybatis", "use_datasource": False, "code_lines_gt_comment_lines": True,
                  "complete_method_body": True, "english_short_classname": True, "pii_free": True},
    },
    "screenshot_ban_tokens": {
        "category": "screenshot",
        "description": "代码/注释禁出内容：正则或字面量（人名/日期/cosmic字眼/@author/邮箱/客户名/角色名）",
        "value": {"tokens": ["@author", "cosmic", r"20\d{2}[-/年]", r"[\w.]+@[\w.]+"],
                  "note": "另禁人名与角色名（一线坐席/操作员/客服/管理员），在注释句式层面保证"},
    },
}


def load_spec(spec_key: str):
    """读单条规范：表里有用表里的，没有回落种子。返回 value（反序列化后，含类型矫正）。"""
    try:
        row = db.query(config.DB_STUDIO, "SELECT value FROM spec_rules WHERE spec_key=%s",
                       (spec_key,), one=True)
        if row:
            v = row["value"] if not isinstance(row["value"], str) else json.loads(row["value"])
            return _coerce(spec_key, v)
    except Exception:
        pass
    if spec_key in SEED_SPECS:
        return SEED_SPECS[spec_key]["value"]
    return None


# 数值/布尔类规范键的类型契约（防脏值入表后 lint 崩溃）
_NUMERIC_SPECS = {"min_fields_error", "min_fields_warn", "jaccard_same_module",
                  "sim_same_req", "sim_cross_archive"}
_BOOL_SPECS = {"e_class_exempt", "crud_sibling_exempt", "cross_archive_check", "pool_coverage_check"}


def _coerce(spec_key: str, v):
    """读时矫正：数值键转 float，布尔键转 bool；矫正失败回落种子值。"""
    try:
        if spec_key in _NUMERIC_SPECS:
            f = float(v)
            return f
        if spec_key in _BOOL_SPECS:
            return bool(v) if not isinstance(v, bool) else v
    except (TypeError, ValueError):
        return SEED_SPECS[spec_key]["value"]
    return v


def validate_value(spec_key: str, v) -> str | None:
    """PUT / 对话 时校验：返回错误消息或 None。
    允许新增自由键（不在 SEED_SPECS）：只做基础类型校验（dict/list/str/number/bool 且非 None）。"""
    if spec_key not in SEED_SPECS:
        if v is None:
            return "规范值不能为 null"
        if not isinstance(v, (dict, list, str, int, float, bool)):
            return f"不支持的规范值类型: {type(v).__name__}（自由键仅接受 dict/list/str/number/bool）"
        return None
    if spec_key in _NUMERIC_SPECS:
        try:
            f = float(v)
            if spec_key.startswith("min_fields") and f < 1:
                return "字段数下限必须 ≥1"
            if spec_key.startswith("sim_") or spec_key == "jaccard_same_module":
                if not 0 < f < 1:
                    return "相似度/Jaccard 阈值必须在 (0,1) 区间"
        except (TypeError, ValueError):
            return f"{spec_key} 必须是数值，收到: {v!r}"
    if spec_key in _BOOL_SPECS and not isinstance(v, bool):
        return f"{spec_key} 必须是布尔值，收到: {v!r}"
    if spec_key in ("ewx_rules", "sub_desc_templates", "data_group_templates",
                    "functional_user_map") and not isinstance(v, dict):
        return f"{spec_key} 必须是对象"
    if spec_key in ("allowed_verbs", "sub_move_types") and not isinstance(v, list):
        return f"{spec_key} 必须是数组"
    return None


def load_all(category: str | None = None) -> dict:
    """读全部规范（含表里没有但种子有的键），附 _source: db/seed。"""
    rows = db.query(config.DB_STUDIO,
                    "SELECT spec_key, category, description, value FROM spec_rules")
    from_db = {r["spec_key"]: {"category": r["category"], "description": r["description"],
                               "value": r["value"] if not isinstance(r["value"], str)
                               else json.loads(r["value"]),
                               "_source": "db"} for r in rows}
    out = {}
    for key, seed in SEED_SPECS.items():
        if category and seed["category"] != category:
            continue
        out[key] = from_db.get(key) or {**seed, "_source": "seed"}
    # 追加自定义键（DB 有但不在种子里）
    for r in rows:
        k = r["spec_key"]
        if k in SEED_SPECS:
            continue
        if category and r["category"] != category:
            continue
        out[k] = {"category": r["category"], "description": r["description"],
                  "value": r["value"] if not isinstance(r["value"], str) else json.loads(r["value"]),
                  "_source": "db"}
    return out


def upsert_spec(spec_key: str, value, category: str = "writing", description: str = ""):
    if spec_key not in SEED_SPECS and not category:
        category = "custom"
    db.execute(config.DB_STUDIO, """
        INSERT INTO spec_rules (spec_key, category, description, value)
        VALUES (%s,%s,%s,%s)
        ON DUPLICATE KEY UPDATE value=VALUES(value), description=VALUES(description)
    """, (spec_key, category or SEED_SPECS.get(spec_key, {}).get("category", "custom"),
          description or SEED_SPECS.get(spec_key, {}).get("description", ""),
          json.dumps(value, ensure_ascii=False)))
    return {"spec_key": spec_key, "value": value}


# ════════════════ 自定义门禁规则（对话可录入，spec 驱动）════════════════

RULE_TYPES = {"fp_name_regex", "sub_desc_regex", "attr_regex",
              "fp_name_prefix", "sub_desc_min_len"}
RULE_SEVERITIES = {"error", "warn"}


def validate_rule(rule) -> str | None:
    """校验单条自定义规则，返回错误消息或 None。"""
    if not isinstance(rule, dict):
        return "规则必须是对象"
    if not rule.get("id"):
        return "规则缺少 id"
    if not rule.get("name"):
        return "规则缺少 name"
    if rule.get("type") not in RULE_TYPES:
        return f"未知规则类型: {rule.get('type')}（支持 {sorted(RULE_TYPES)}）"
    if rule.get("severity", "error") not in RULE_SEVERITIES:
        return f"severity 必须是 error/warn，收到: {rule.get('severity')}"
    if "pattern" not in rule:
        return "规则缺少 pattern"
    return None


def add_custom_rule(rule: dict) -> dict:
    """新增一条自定义门禁规则（对话工具 add_custom_rule 落点）。"""
    err = validate_rule(rule)
    if err:
        return {"error": err}
    rules = load_spec("custom_rules") or []
    if not isinstance(rules, list):
        rules = []
    if any(r.get("id") == rule["id"] for r in rules):
        return {"error": f"规则 id 已存在: {rule['id']}"}
    rules.append(rule)
    upsert_spec("custom_rules", rules, "custom", "对话录入的自定义门禁规则")
    return {"spec_key": "custom_rules", "count": len(rules), "added": rule["id"]}


def remove_custom_rule(rule_id: str) -> dict:
    """删除一条自定义门禁规则（按 id）。"""
    rules = load_spec("custom_rules") or []
    if not isinstance(rules, list):
        return {"error": "custom_rules 不是列表"}
    new = [r for r in rules if r.get("id") != rule_id]
    if len(new) == len(rules):
        return {"error": f"规则不存在: {rule_id}"}
    upsert_spec("custom_rules", new, "custom", "对话录入的自定义门禁规则")
    return {"spec_key": "custom_rules", "count": len(new), "removed": rule_id}
