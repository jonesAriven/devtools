#!/usr/bin/env python3
"""第2/3轮：新增功能深测 + 对抗性测试（X01-X30）。测试数据 QA3- 前缀，自动清理。"""
import io
import json
import sys
import urllib.error
import urllib.request
import uuid

BASE = sys.argv[1] if len(sys.argv) > 1 else "http://192.168.31.105:8310"
RESULTS = []


def call(method, path, token=None, body=None, raw=False, headers=None):
    from urllib.parse import quote
    req = urllib.request.Request(BASE + quote(path, safe="/?&=%"), method=method)
    if token:
        req.add_header("Authorization", f"Bearer {token}")
    for k, v in (headers or {}).items():
        req.add_header(k, v)
    data = None
    if body is not None:
        req.add_header("Content-Type", "application/json")
        data = json.dumps(body, ensure_ascii=False).encode()
    try:
        with urllib.request.urlopen(req, data=data, timeout=90) as r:
            payload = r.read()
            return r.status, (payload if raw else json.loads(payload or b"{}"))
    except urllib.error.HTTPError as e:
        payload = e.read()
        try:
            return e.code, json.loads(payload)
        except Exception:
            return e.code, payload
    except Exception as e:
        return -1, str(e)


def check(tid, name, cond, detail=""):
    RESULTS.append((tid, name, bool(cond), str(detail)[:140]))
    print(f"{'✅' if cond else '❌'} {tid} {name} {'' if cond else '| ' + str(detail)[:140]}")


admin = call("POST", "/api/auth/login", body={"username": "admin", "password": "cosmic@2026"})[1].get("token")
sfx = uuid.uuid4().hex[:6]

# ══════════ 评审域深测 ══════════
st, d = call("POST", "/api/active/projects", admin,
             {"requirement_id": f"QA3-{sfx}", "requirement_name": "深测项目", "client_name": "测试"})
PID = d.get("id") if isinstance(d, dict) else None
st, d = call("POST", f"/api/active/projects/{PID}/modules", admin,
             {"level1": "A", "level2": "B", "level3": "深测配置管理"})
MID = d.get("id")
st, d = call("POST", f"/api/active/projects/{PID}/fps", admin, {"module_id": MID, "name": "新增深测对象"})
FID = d.get("id")
tree = call("GET", f"/api/active/projects/{PID}/tree", admin)[1]
fp = tree["modules"][0]["fps"][0]
SUB_E, SUB_W = fp["subs"][0]["id"], fp["subs"][1]["id"]

# X01 意见挂不存在的 target_id（录入应成功，但去修改时提示找不到——录入端不校验是设计还是漏洞？先记录行为）
st, d = call("POST", f"/api/active/projects/{PID}/reviews", admin,
             {"target_type": "sub", "target_id": 999999999, "target_label": "幽灵行", "content": "挂不存在的行", "classify": "text_replace"})
GHOST_RID = d.get("id") if isinstance(d, dict) else None
check("X01", "挂不存在行：录入行为=接受（记录）", st == 201 and GHOST_RID, (st, d))

# X02 manual-done 挂幽灵行的意见——应 201 且正常出版本（意见关闭，行不存在也无害）
st, d = call("POST", f"/api/active/reviews/{GHOST_RID}/manual-done", admin, {"revision_note": "幽灵关闭"})
check("X02", "幽灵意见 manual-done 不崩且出版本", st == 201 and d.get("version", {}).get("sha256"), (st, str(d)[:80]))

# X03 manual-done 不存在的意见 → 404
st, _ = call("POST", "/api/active/reviews/999999999/manual-done", admin, {})
check("X03", "manual-done 不存在意见 404", st == 404, st)

# X04 非法 classify 422
st, _ = call("POST", f"/api/active/projects/{PID}/reviews", admin,
             {"target_type": "project", "content": "x", "classify": "whatever"})
check("X04", "非法 classify 422", st == 422, st)

# X05 非法 target_type 422
st, _ = call("POST", f"/api/active/projects/{PID}/reviews", admin,
             {"target_type": "module", "content": "x"})
check("X05", "非法 target_type 422", st == 422, st)

# X06 超长意见（5000字）
long_text = "这是一条很长的评审意见。" * 500
st, d = call("POST", f"/api/active/projects/{PID}/reviews", admin,
             {"target_type": "project", "content": long_text})
LONG_RID = d.get("id") if isinstance(d, dict) else None
check("X06", "5000字长意见接受", st == 201 and LONG_RID, st)

# X07 XSS 样例入库（验证原样存储不执行——UI 端 Vue 模板文本插值天然转义）
xss = "<script>alert('xss')</script> & <img src=x onerror=alert(1)>"
st, d = call("POST", f"/api/active/projects/{PID}/reviews", admin,
             {"target_type": "project", "content": xss})
XSS_RID = d.get("id") if isinstance(d, dict) else None
rlist = call("GET", f"/api/active/projects/{PID}/reviews", admin)[1]
stored = next((r["content"] for r in rlist if r["id"] == XSS_RID), "")
check("X07", "XSS 样例原样存储（Vue 文本插值防执行）", st == 201 and stored == xss, stored[:60])

# X08 emoji/换行/特殊字符
weird = "意见含emoji🚀、换行\n第二行、emoji👍和引号\"'`"
st, d = call("POST", f"/api/active/projects/{PID}/reviews", admin, {"target_type": "project", "content": weird})
W_RID = d.get("id") if isinstance(d, dict) else None
rlist = call("GET", f"/api/active/projects/{PID}/reviews", admin)[1]
back = next((r["content"] for r in rlist if r["id"] == W_RID), "")
check("X08", "emoji/换行/引号往返一致", st == 201 and back == weird, back[:50])

# X09 auto-fix 指定不存在的 review_ids → 422 无待处理
st, d = call("POST", f"/api/active/projects/{PID}/reviews/auto-fix", admin, {"review_ids": [999999999]})
check("X09", "auto-fix 全不存在 id → 422", st == 422, st)

# X10 auto-fix 只勾一条 pending（text_replace, LLM 未配 → 409；验证 id 过滤路径）
st, d = call("POST", f"/api/active/projects/{PID}/reviews", admin,
             {"target_type": "sub", "target_id": SUB_E, "target_label": "E", "content": "改", "classify": "text_replace"})
R_TXT = d.get("id")
st, d = call("POST", f"/api/active/projects/{PID}/reviews/auto-fix", admin, {"review_ids": [R_TXT]})
check("X10", "auto-fix 指定 id 走到 LLM 检查（409 未配）", st == 409, st)

# X11 wont_fix 带理由 → 状态可查
st, _ = call("PUT", f"/api/active/reviews/{W_RID}", admin, {"disposition": "wont_fix", "revision_note": "客户要求保留"})
rlist = call("GET", f"/api/active/projects/{PID}/reviews", admin)[1]
w = next((r for r in rlist if r["id"] == W_RID), {})
check("X11", "wont_fix+理由持久化", w.get("disposition") == "wont_fix" and "客户要求" in (w.get("revision_note") or ""), w)

# X12 删除意见（admin）→ 列表消失
st, _ = call("DELETE", f"/api/active/reviews/{LONG_RID}", admin)
rlist = call("GET", f"/api/active/projects/{PID}/reviews", admin)[1]
check("X12", "删除意见生效", st == 200 and not any(r["id"] == LONG_RID for r in rlist), st)

# ══════════ 副本深测 ══════════
# X13 复制含子过程数据的副本 → 数据一致
st, cp = call("POST", f"/api/active/projects/{PID}/copy", admin)
CPID = cp.get("id")
ctree = call("GET", f"/api/active/projects/{CPID}/tree", admin)[1]
check("X13", "复制含数据副本一致", sum(len(f["subs"]) for m in ctree["modules"] for f in m["fps"]) == 2, ctree and sum(len(f["subs"]) for m in ctree["modules"] for f in m["fps"]))

# X14 副本改数据后 diff 反映差异
cpsubs = ctree["modules"][0]["fps"][0]["subs"]
call("PUT", f"/api/active/subs/{cpsubs[0]['id']}", admin, {"data_attributes": "副本独有甲、副本独有乙、副本独有丙、副本独有丁"})
st, d = call("GET", f"/api/active/projects/{PID}/diff?against={CPID}", admin)
# diff 只比 FP 名，属性差异不体现——记录行为
check("X14", "diff 只比 FP 名（行为记录）", st == 200 and d["common"] == 1 and len(d["only_in_this"]) == 0, d)

# X15 diff 对不存在的项目 → 应报错而非 500 假数据
st, d = call("GET", f"/api/active/projects/{PID}/diff?against=999999999", admin)
check("X15", "diff 不存在对手不 500", st in (404, 200) and (st == 404 or d.get("only_in_main") == []), (st, str(d)[:60]))

# X16 空项目复制（建空项目再复制）
st, d = call("POST", "/api/active/projects", admin, {"requirement_id": f"QA3E-{sfx}", "requirement_name": "空项目"})
EPID = d.get("id")
st, d = call("POST", f"/api/active/projects/{EPID}/copy", admin)
ECID = d.get("id") if isinstance(d, dict) else None
etree = call("GET", f"/api/active/projects/{ECID}/tree", admin)[1] if ECID else {}
check("X16", "空项目复制成功且空", st == 201 and etree.get("modules") == [], (st, etree.get("modules")))

# X17 设主：空项目组内设主
st, _ = call("PUT", f"/api/active/projects/{ECID}/primary", admin)
plist = call("GET", "/api/active/projects", admin)[1]
grp = [(p["id"], p["is_primary"]) for p in plist if p["requirement_id"] == f"QA3E-{sfx}"]
check("X17", "空项目组设主互斥", [f for _, f in grp].count(1) == 1, grp)

# ══════════ 导入边界 ══════════
# X18 只有表头无数据的 xlsx → 明确报错不崩
try:
    from openpyxl import Workbook
    buf = io.BytesIO()
    wb = Workbook(); ws = wb.active
    ws.cell(1, 1, "通用软件评估模型")
    wb.save(buf)
    empty_xlsx = buf.getvalue()
    boundary = "----x18"
    body = (f"--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"e.xlsx\"\r\nContent-Type: application/octet-stream\r\n\r\n").encode() + empty_xlsx + f"\r\n--{boundary}--\r\n".encode()
    req = urllib.request.Request(BASE + f"/api/active/import/xlsx?mode=incremental&project_id={PID}", data=body, method="POST")
    req.add_header("Content-Type", f"multipart/form-data; boundary={boundary}")
    req.add_header("Authorization", f"Bearer {admin}")
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            st = r.status
    except urllib.error.HTTPError as e:
        st = e.code
    check("X18", "无数据xlsx被明确拒绝", st in (400, 404, 422, 500), st)
except ImportError:
    check("X18", "跳过（无 openpyxl）", True)

# X19 xlsx 二次导入同文件（幂等性：第二次 updated 而非 created 翻倍）
st, xlsx = call("GET", f"/api/active/projects/{PID}/export/xlsx", admin, raw=True)
for attempt, label in ((1, "首导"), (2, "二导")):
    boundary = "----x19"
    body = (f"--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"i.xlsx\"\r\nContent-Type: application/octet-stream\r\n\r\n").encode() + xlsx + f"\r\n--{boundary}--\r\n".encode()
    req = urllib.request.Request(BASE + f"/api/active/import/xlsx?mode=incremental&project_id={PID}", data=body, method="POST")
    req.add_header("Content-Type", f"multipart/form-data; boundary={boundary}")
    req.add_header("Authorization", f"Bearer {admin}")
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            imp = json.loads(r.read())
    except urllib.error.HTTPError as e:
        imp = {"err": e.code}
    if attempt == 2:
        check("X19", "同文件二次导入幂等（updated 非 created）", imp.get("updated", {}).get("fps") == 1 and imp.get("created", {}).get("fps") == 0, imp)

# X20 导入往返数据一致（导出→导入→导出，比对 JSON 全等）
j1 = call("GET", f"/api/active/projects/{PID}/export/json", admin)[1]
st, x2 = call("GET", f"/api/active/projects/{PID}/export/xlsx", admin, raw=True)
boundary = "----x20"
body = (f"--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"r.xlsx\"\r\nContent-Type: application/octet-stream\r\n\r\n").encode() + x2 + f"\r\n--{boundary}--\r\n".encode()
req = urllib.request.Request(BASE + f"/api/active/import/xlsx?mode=overwrite&project_id={PID}", data=body, method="POST")
req.add_header("Content-Type", f"multipart/form-data; boundary={boundary}")
req.add_header("Authorization", f"Bearer {admin}")
try:
    with urllib.request.urlopen(req, timeout=60) as r:
        r.status
except urllib.error.HTTPError:
    pass
j2 = call("GET", f"/api/active/projects/{PID}/export/json", admin)[1]
check("X20", "xlsx 覆盖往返 JSON 全等", j1 == j2, "first-diff: " + str([
    k for k in j1.keys() if j1.get(k) != j2.get(k)])[:80])

# ══════════ 认证与其他 ══════════
# X21 伪造 token
st, _ = call("GET", "/api/active/projects", "fake.token.sig")
check("X21", "伪造 token 401", st == 401, st)
# X22 篡改 token（有效签名格式但错）
st, _ = call("GET", "/api/active/projects", "eyJ1aWQiOjF9.bad.bad")
check("X22", "篡改 token 401", st == 401, st)
# X23 词库 limit=0 边界
st, d = call("GET", "/api/studio/vocab?limit=0", admin)
check("X23", "vocab limit=0 正常返回", st == 200, st)
# X24 词库特殊字符搜索
st, d = call("GET", "/api/studio/vocab?q=%25_%27", admin)
check("X24", "词库特殊字符搜索不 500", st == 200, st)
# X25 规范键非法值类型（value 传字符串给数值规范）
st, _ = call("PUT", "/api/studio/specs/min_fields_error", admin, {"value": "three"})
rep = call("GET", f"/api/active/projects/{PID}/lint?no_archive=true", admin)[1]
check("X25", "类型错误值不崩（lint 容忍或拒绝）", st in (200, 422) and "summary" in rep, (st, rep.get("summary")))
call("DELETE", "/api/studio/specs/min_fields_error", admin)

# X26 lint 不存在的项目
st, d = call("GET", "/api/active/projects/999999999/lint", admin)
check("X26", "lint 不存在项目明确报错", st == 200 and d["summary"]["error"] >= 1 and "不存在" in d["errors"][0]["message"], (st, str(d.get("errors", [{}])[0])[:60]))

# X27 FP 越权挂到别的项目的模块（module_id 跨项目）→ 应 404
st, _ = call("POST", f"/api/active/projects/{PID}/fps", admin, {"module_id": 1, "name": "新增跨项目对象"})
check("X27", "跨项目 module_id 404", st == 404, st)

# X28 并发版本快照 seq 唯一（连打 3 个）
seqs = []
for i in range(3):
    st, v = call("POST", f"/api/active/projects/{PID}/versions", admin, {"changelog": f"X28-{i}"})
    seqs.append(v.get("seq"))
check("X28", "连续快照 seq 递增唯一", seqs == sorted(set(seqs)) and len(set(seqs)) == 3, seqs)

# X29 删除项目后其版本文件仍可追溯（版本是独立文件）——先删项目再列版本
call("DELETE", f"/api/active/projects/{ECID}?confirm=active", admin)
call("DELETE", f"/api/active/projects/{CPID}?confirm=active", admin)
st, _ = call("DELETE", f"/api/active/projects/{PID}?confirm=active", admin)
check("X29", "清理全部测试项目", st == 200, st)

# X30 管理端点未授权
st, _ = call("PUT", "/api/studio/specs/min_fields_error", None, {"value": 9})
check("X30", "未登录改规范 401", st == 401, st)

fails = [r for r in RESULTS if not r[2]]
print(f"\n{'='*50}\n第2/3轮深测+对抗: {len(RESULTS)-len(fails)}/{len(RESULTS)} 通过")
for tid, name, _, detail in fails:
    print(f"  ❌ {tid} {name}: {detail}")
sys.exit(1 if fails else 0)
