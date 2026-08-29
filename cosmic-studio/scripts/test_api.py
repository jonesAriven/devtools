#!/usr/bin/env python3
"""cosmic-studio API 全量测试（T01-T53）：认证/权限矩阵/CRUD/门禁/导入导出/版本/规范/对话。
用法： python scripts/test_api.py [base_url]   默认 http://192.168.31.105:8310
测试数据前缀 QA-，结束自动清理。"""
import io
import json
import sys
import urllib.error
import urllib.request
import uuid

BASE = sys.argv[1] if len(sys.argv) > 1 else "http://192.168.31.105:8310"
RESULTS = []


def call(method, path, token=None, body=None, raw=False):
    from urllib.parse import quote
    req = urllib.request.Request(BASE + quote(path, safe="/?&=%"), method=method)
    if token:
        req.add_header("Authorization", f"Bearer {token}")
    data = None
    if body is not None:
        req.add_header("Content-Type", "application/json")
        data = json.dumps(body, ensure_ascii=False).encode()
    try:
        with urllib.request.urlopen(req, data=data, timeout=60) as r:
            payload = r.read()
            return r.status, (payload if raw else json.loads(payload or b"{}"))
    except urllib.error.HTTPError as e:
        payload = e.read()
        try:
            return e.code, json.loads(payload)
        except Exception:
            return e.code, payload


def check(tid, name, cond, detail=""):
    RESULTS.append((tid, name, bool(cond), str(detail)[:120]))
    print(f"{'✅' if cond else '❌'} {tid} {name} {'' if cond else '| ' + str(detail)[:120]}")


def login(u, p):
    st, d = call("POST", "/api/auth/login", body={"username": u, "password": p})
    return (st, d.get("token"), d) if st == 200 else (st, None, d)


# ═══════════ T01-T05 认证 ═══════════
st, admin_token, admin_info = login("admin", "cosmic@2026")
check("T01", "admin 正确登录", st == 200 and admin_token, st)
st2, _ = call("POST", "/api/auth/login", body={"username": "admin", "password": "wrong"})
check("T02", "错误密码 401", st2 == 401, st2)
st3, _ = call("GET", "/api/active/projects")
check("T03", "无 token 访问 401", st3 == 401, st3)
st4, _ = call("GET", "/api/health")
check("T04", "health 免登录 200", st4 == 200, st4)
st5, me = call("GET", "/api/auth/me", admin_token)
check("T05", "me 返回 admin", st5 == 200 and me.get("role") == "admin", me)

# ═══════════ T06-T15 用户管理与权限矩阵 ═══════════
suffix = uuid.uuid4().hex[:6]
st6, d6 = call("POST", "/api/auth/users", admin_token,
               {"username": f"qa_ed_{suffix}", "password": "Qa@12345", "role": "editor", "display_name": "测试编辑"})
check("T06", "建 editor 用户", st6 == 201, d6)
st7, d7 = call("POST", "/api/auth/users", admin_token,
               {"username": f"qa_vw_{suffix}", "password": "Qa@12345", "role": "viewer", "display_name": "测试只读"})
check("T07", "建 viewer 用户", st7 == 201, d7)
ed_st, ed_tok, _ = login(f"qa_ed_{suffix}", "Qa@12345")
vw_st, vw_tok, _ = login(f"qa_vw_{suffix}", "Qa@12345")
check("T08", "editor 登录", ed_st == 200 and ed_tok)
check("T09", "viewer 登录", vw_st == 200 and vw_tok)
st10, _ = call("POST", "/api/active/projects", vw_tok,
               {"requirement_id": "QA-X", "requirement_name": "x"})
check("T10", "viewer 建项目 403", st10 == 403, st10)

st11, d11 = call("POST", "/api/active/projects", ed_tok,
                 {"requirement_id": f"QA-{suffix}", "requirement_name": "自动化测试项目QA",
                  "project_code": "qa", "client_name": "测试客户"})
qa_pid = d11.get("id") if isinstance(d11, dict) else None
check("T11", "editor 建项目", st11 == 201 and qa_pid, d11)
st12, _ = call("GET", "/api/auth/users", vw_tok)
check("T12", "viewer 看用户列表 403", st12 == 403, st12)
st13, _ = call("GET", "/api/auth/users", ed_tok)
check("T13", "editor 看用户列表 403", st13 == 403, st13)
users = call("GET", "/api/auth/users", admin_token)[1]
vw_id = next((u["id"] for u in users if u["username"] == f"qa_vw_{suffix}"), None)
call("PUT", f"/api/auth/users/{vw_id}", admin_token,
     {"username": f"qa_vw_{suffix}", "role": "viewer", "enabled": False})
st14, _ = call("GET", "/api/active/projects", vw_tok)
check("T14", "禁用用户 token 失效", st14 == 401, st14)
st15, _ = call("POST", "/api/auth/users", admin_token,
               {"username": f"qa_bad_{suffix}", "password": "x", "role": "superman"})
check("T15", "非法角色 422", st15 == 422, st15)

# ═══════════ T16-T27 项目/FP/子过程 CRUD ═══════════
st16, d16 = call("POST", f"/api/active/projects/{qa_pid}/modules", ed_tok,
                 {"level1": "测试一级", "level2": "测试二级", "level3": "测试配置管理"})
mod_id = d16.get("id") if isinstance(d16, dict) else None
check("T16", "建模块", st16 == 201 and mod_id, d16)
st17, d17 = call("POST", f"/api/active/projects/{qa_pid}/fps", ed_tok,
                 {"module_id": mod_id, "name": "新增资费标准测试字段配置"})
qa_fp1 = d17.get("id") if isinstance(d17, dict) else None
check("T17", "建 FP（新增类）", st17 == 201 and qa_fp1, d17)
tree = call("GET", f"/api/active/projects/{qa_pid}/tree", ed_tok)[1]
fp1 = tree["modules"][0]["fps"][0]
fu, te, subs = fp1["functional_user"], fp1["trigger_event"], fp1["subs"]
ok18 = ("发起者：终端用户" in fu or "发起者：一线坐席" in fu) and "接收者：多媒体卡片平台" in fu
check("T18", "F列自动推导格式", ok18, fu)
check("T19", "E列推导=发起者+FP名+时触发", te.endswith("时触发") and "新增资费标准测试字段配置" in te, te)
check("T20", "EW 类自动展开2子过程", [s["data_move_type"] for s in subs] == ["E", "W"],
      [s["data_move_type"] for s in subs])
check("T21", "E类描述模板", subs[0]["description"] == "接收终端用户发起新增资费标准测试字段配置请求" or subs[0]["description"].startswith("接收"), subs[0]["description"])
check("T22", "数据组名模板", subs[0]["data_group_name"].endswith("新增请求数据") and subs[1]["data_group_name"].endswith("新增数据"),
      [s["data_group_name"] for s in subs])
st23, _ = call("POST", f"/api/active/fps/{qa_fp1}/subs", ed_tok,
               {"move_type": "E", "attributes": "字段A,字段B,字段C"})
check("T23", "逗号分隔属性 422", st23 == 422, st23)
st24, _ = call("POST", f"/api/active/fps/{qa_fp1}/subs", ed_tok,
               {"move_type": "E", "attributes": "字段A、字段B"})
check("T24", "字段数<3 422", st24 == 422, st24)
st25, _ = call("POST", f"/api/active/fps/{qa_fp1}/subs", ed_tok,
               {"move_type": "Z", "attributes": "字段A、字段B、字段C"})
check("T25", "非法 move_type 422", st25 == 422, st25)
st26, d26 = call("POST", f"/api/active/projects/{qa_pid}/fps", ed_tok,
                 {"module_id": mod_id, "name": "新增临时验证对象配置"})
qa_fp3 = d26.get("id") if isinstance(d26, dict) else None
st26b, d26b = call("POST", f"/api/active/fps/{qa_fp3}/subs", ed_tok,
                   {"move_type": "W", "attributes": "字段甲、字段乙、字段丙、字段丁"})
check("T26", "临时FP加W子过程（5字段，模板推导）", st26b == 201 and d26b.get("description", "").startswith("新增"), d26b)
call("DELETE", f"/api/active/fps/{qa_fp3}?cascade=true", ed_tok)
st27, _ = call("PUT", f"/api/active/fps/{qa_fp1}", ed_tok, {"name": "新增资费标准测试字段配置"})
check("T27", "FP 更新", st27 == 200, st27)

# EWX 违规
st28, _ = call("POST", f"/api/active/fps/{qa_fp1}/subs", ed_tok,
               {"move_type": "R", "attributes": "字段甲、字段乙、字段丙"})
check("T28", "EW 类 FP 加 R 违规 422", st28 == 422, st28)

# 查询类 FP
st29, d29 = call("POST", f"/api/active/projects/{qa_pid}/fps", ed_tok,
                 {"module_id": mod_id, "name": "查询资费标准测试字段配置"})
qa_fp2 = d29.get("id") if isinstance(d29, dict) else None
tree2 = call("GET", f"/api/active/projects/{qa_pid}/tree", ed_tok)[1]
fp2 = next(f for m in tree2["modules"] for f in m["fps"] if f["id"] == qa_fp2)
check("T29", "查询类自动展开 ERX 3子过程", [s["data_move_type"] for s in fp2["subs"]] == ["E", "R", "X"],
      [s["data_move_type"] for s in fp2["subs"]])

# ═══════════ T30-T33 derive ═══════════
call("PUT", f"/api/active/fps/{qa_fp1}", ed_tok, {"event": "故意改错的触发事件"})
issues = call("POST", f"/api/active/projects/{qa_pid}/derive", ed_tok)[1]
check("T30", "derive 发现破坏的 E 列", issues["count"] >= 1, issues["count"])
fixed = call("POST", f"/api/active/projects/{qa_pid}/derive?fix=true", ed_tok)[1]
after = call("POST", f"/api/active/projects/{qa_pid}/derive", ed_tok)[1]
check("T31", "fix 修复后 0 差异", after["count"] == 0, f"before={issues['count']} after={after['count']}")

# ═══════════ T32-T35 lint 门禁 ═══════════
call("PUT", f"/api/active/subs/{fp2['subs'][1]['id']}", ed_tok,
     {"data_attributes": "客户姓名、证件号码、联系电话"})
report = call("GET", f"/api/active/projects/{qa_pid}/lint?no_archive=true", admin_token)[1]
checks = {e["check"] for e in report["errors"]}
check("T32", "lint 拦截 PII 伪字段", "伪字段" in checks, checks)
st33, _ = call("POST", "/api/studio/rules/forbidden", admin_token, {"word": "测试字段"})
report2 = call("GET", f"/api/active/projects/{qa_pid}/lint?no_archive=true", admin_token)[1]
check("T33", "新增禁词立即生效", any("测试字段" in e["message"] and e["check"] == "禁词" for e in report2["errors"]),
      [e["message"] for e in report2["errors"] if e["check"] == "禁词"][:2])
call("DELETE", "/api/studio/rules/forbidden/测试字段", admin_token)
report3 = call("GET", f"/api/active/projects/{qa_pid}/lint?no_archive=true", admin_token)[1]
check("T34", "删除禁词恢复", not any(e["check"] == "禁词" for e in report3["errors"]))
call("PUT", f"/api/active/subs/{fp2['subs'][1]['id']}", ed_tok,
     {"data_attributes": "测试属性甲、测试属性乙、测试属性丙、测试属性丁、测试属性戊"})
st35, _ = call("PUT", "/api/studio/specs/min_fields_error", ed_tok, {"value": 9})
check("T35", "editor 改规范 403", st35 == 403, st35)

# ═══════════ T36-T41 导入导出矩阵 ═══════════
st36, xlsx_bytes = call("GET", f"/api/active/projects/{qa_pid}/export/xlsx", ed_tok, raw=True)
check("T36", "导出 xlsx", st36 == 200 and xlsx_bytes[:2] == b"PK", (st36, len(xlsx_bytes)))
st37, j37 = call("GET", f"/api/active/projects/{qa_pid}/export/json", ed_tok)
check("T37", "导出 JSON 结构", st37 == 200 and j37["modules"] and j37["modules"][0]["fps"], st37)

# 增量导入 upsert：改属性后重导，验证更新不重复
fp2_json = j37["modules"][0]["fps"][1]
fp2_json["subs"][1]["data_attributes"] = "增量字段一、增量字段二、增量字段三、增量字段四"
import copy
payload = copy.deepcopy(j37)
payload["modules"][0]["fps"] = [payload["modules"][0]["fps"][1]]  # 只带1个FP
st38, d38 = call("POST", f"/api/active/import/json?mode=incremental&project_id={qa_pid}", ed_tok, payload)
tree3 = call("GET", f"/api/active/projects/{qa_pid}/tree", ed_tok)[1]
fp2_after = next(f for m in tree3["modules"] for f in m["fps"] if f["id"] == qa_fp2)
n_fps = sum(len(m["fps"]) for m in tree3["modules"])
check("T38", "增量导入 upsert 生效", st38 == 200 and d38["updated"]["fps"] == 1 and n_fps == 2,
      (st38, d38.get("updated"), n_fps))
check("T39", "upsert 后属性已更新", "增量字段一" in fp2_after["subs"][1]["data_attributes"],
      fp2_after["subs"][1]["data_attributes"])

# 覆盖导入（项目级）
st40, d40 = call("POST", f"/api/active/import/json?mode=overwrite&project_id={qa_pid}", ed_tok, j37)
check("T40", "项目级覆盖导入 + 自动备份", st40 == 200 and d40.get("backup", "").endswith(".json"), (st40, d40.get("backup")))
jobs = call("GET", "/api/active/import/jobs", ed_tok)[1]
check("T41", "导入任务留痕", len(jobs) >= 2, len(jobs))

# ═══════════ T42-T45 版本 ═══════════
st42, d42 = call("POST", f"/api/active/projects/{qa_pid}/versions", ed_tok, {"label": "qa-test", "changelog": "自动化测试"})
check("T42", "创建版本快照", st42 == 201 and d42.get("sha256"), d42)
vers = call("GET", f"/api/active/projects/{qa_pid}/versions", ed_tok)[1]
check("T43", "版本列表", any(v["label"] == "qa-test" for v in vers), len(vers))
req = urllib.request.Request(BASE + f"/api/active/versions/{d42['id']}/download")
req.add_header("Authorization", f"Bearer {ed_tok}")
with urllib.request.urlopen(req) as r:
    blob = r.read()
check("T44", "版本下载 xlsx", r.status == 200 and blob[:2] == b"PK", (r.status, len(blob)))
st45, _ = call("POST", f"/api/active/projects/{qa_pid}/versions", vw_tok, {})
check("T45", "viewer 建版本 401(禁用)/403", st45 in (401, 403), st45)

# ═══════════ T46-T49 规范中心 ═══════════
specs = call("GET", "/api/studio/specs", ed_tok)[1]
check("T46", "规范 25 条", specs["count"] == 25, specs["count"])
st47, _ = call("PUT", "/api/studio/specs/min_fields_error", admin_token, {"value": 9})
rep9 = call("GET", f"/api/active/projects/{qa_pid}/lint?no_archive=true", admin_token)[1]
n_field_err9 = sum(1 for e in rep9["errors"] if e["check"] == "数据属性")
call("PUT", "/api/studio/specs/min_fields_error", admin_token, {"value": 3})
rep3 = call("GET", f"/api/active/projects/{qa_pid}/lint?no_archive=true", admin_token)[1]
n_field_err3 = sum(1 for e in rep3["errors"] if e["check"] == "数据属性")
check("T47", "改阈值 lint 立即变化", st47 == 200 and n_field_err9 > n_field_err3,
      f"min=9时{n_field_err9}条 min=3时{n_field_err3}条")
st48, d48 = call("DELETE", "/api/studio/specs/min_fields_error", admin_token)
check("T48", "还原规范回落种子", st48 == 200 and d48["value"] == 3, d48)
st49, _ = call("GET", "/api/studio/specs/screenshot_render", ed_tok)
check("T49", "截图规范可读", st49 == 200, st49)

# ═══════════ T50-T51 词库 ═══════════
vocab = call("GET", "/api/studio/vocab?q=资费", ed_tok)[1]
check("T50", "词库搜索", isinstance(vocab, list) and len(vocab) > 0, len(vocab) if isinstance(vocab, list) else vocab)
check("T51", "词库按频次排序", all(vocab[i]["frequency"] >= vocab[i+1]["frequency"] for i in range(len(vocab)-1)))

# ═══════════ T52-T53 归档库 ═══════════
arch = call("GET", "/api/archive/projects", ed_tok)[1]
check("T52", "归档库 40 项目", len(arch) == 40, len(arch))
st53, _ = call("POST", "/api/archive/projects", admin_token, {"requirement_id": "X", "requirement_name": "x"})
check("T53", "归档库直接写入 403", st53 == 403, st53)

# ═══════════ 清理 ═══════════
stc, _ = call("DELETE", f"/api/active/projects/{qa_pid}?confirm=active", admin_token)
print(f"\n清理测试项目 {qa_pid}: {stc}")
users = call("GET", "/api/auth/users", admin_token)[1]
for u in users:
    if u["username"].startswith(f"qa_ed_{suffix}"):
        call("PUT", f"/api/auth/users/{u['id']}", admin_token,
             {"username": u["username"], "role": "editor", "enabled": False})
        print(f"禁用测试用户 {u['username']}")

# ═══════════ 汇总 ═══════════
fails = [r for r in RESULTS if not r[2]]
print(f"\n{'='*50}\nAPI 测试汇总: {len(RESULTS)-len(fails)}/{len(RESULTS)} 通过")
if fails:
    print("失败用例:")
    for tid, name, _, detail in fails:
        print(f"  ❌ {tid} {name}: {detail}")
sys.exit(1 if fails else 0)
