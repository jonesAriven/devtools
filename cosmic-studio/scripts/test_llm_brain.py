#!/usr/bin/env python3
"""LLM 大脑接线测试：把当前模型（ZCode agent）的决策接到系统的两个 LLM 调用层上，
端到端验证"大模型使用这个系统"：
  B1 对话工作台：LLM 读工具 schema → 决策调 list_projects + search_vocab → 汇总回答
  B2 对话权限：LLM 决策调 update_spec（viewer）→ 执行器拒绝
  B3 评审批次自动优化（happy path）：LLM 读评审意见+行上下文 → 生成修改 → 门禁通过 → 一轮一版本
  B4 门禁保险丝：LLM 给出违规修改（属性含逗号）→ 整批回滚 + 422
前置：在 cosmic-api 容器内执行（in-process 补丁 + 本地 8001 起 uvicorn）。
"""
import json
import sys
import threading
import time
import urllib.error
import urllib.request

sys.path.insert(0, "/app")
import uvicorn

from app.main import app
import app.routers.chat as chat_mod
import app.routers.reviews as reviews_mod

RESULTS = []


def check(tid, name, cond, detail=""):
    RESULTS.append((tid, name, bool(cond), str(detail)[:140]))
    print(f"{'✅' if cond else '❌'} {tid} {name} {'' if cond else '| ' + str(detail)[:140]}")


# ── 起 8001 测试服务（同进程，模块补丁生效）──
cfg = uvicorn.Config(app, host="127.0.0.1", port=8001, log_level="error")
server = uvicorn.Server(cfg)
threading.Thread(target=server.run, daemon=True).start()
time.sleep(2)
BASE = "http://127.0.0.1:8001"


def call(method, path, token=None, body=None):
    req = urllib.request.Request(BASE + path, method=method)
    if token:
        req.add_header("Authorization", f"Bearer {token}")
    data = None
    if body is not None:
        req.add_header("Content-Type", "application/json")
        data = json.dumps(body, ensure_ascii=False).encode()
    try:
        with urllib.request.urlopen(req, data=data, timeout=60) as r:
            return r.status, json.loads(r.read() or b"{}")
    except urllib.error.HTTPError as e:
        try:
            return e.code, json.loads(e.read())
        except Exception:
            return e.code, {}


admin = call("POST", "/api/auth/login", body={"username": "admin", "password": "cosmic@2026"})[1]["token"]

# 启用一个假端点 LLM 配置（_llm_call 已被补丁替换，base_url 不会被真实访问）
call("PUT", "/api/studio/llm-config", admin,
     {"provider": "openai-compatible", "base_url": "http://127.0.0.1:9",
      "model": "zcode-agent-as-llm", "api_key": "patched", "enabled": True})

# ══════════ B1/B2：对话工作台（我当大脑）══════════
orig_chat_llm = chat_mod._llm_call


def brain_chat(cfg, messages, schema):
    """读对话历史决策：首轮并行调两个只读工具；拿到工具结果后汇总成回答。"""
    has_tool_result = any(m.get("role") == "tool" for m in messages)
    if not has_tool_result:
        return {"choices": [{"message": {"role": "assistant", "content": None, "tool_calls": [
            {"id": "c1", "type": "function",
             "function": {"name": "list_projects", "arguments": json.dumps({"dimension": "active"})}},
            {"id": "c2", "type": "function",
             "function": {"name": "search_vocab", "arguments": json.dumps({"q": "资费", "limit": 3})}},
        ]}}]}
    # 第二轮：读工具真实结果（像真 LLM 一样从 tool content 提取数字）
    tool_out = [m["content"] for m in messages if m.get("role") == "tool"]
    n_projects = len(json.loads(tool_out[0]))
    vocab = json.loads(tool_out[1])
    return {"choices": [{"message": {"role": "assistant", "content":
            f"编写库当前有 {n_projects} 个项目；'资费'相关词库术语前3条：{', '.join(t['term'] for t in vocab)}。"}}]}


chat_mod._llm_call = brain_chat

st, d = call("POST", "/api/chat", admin, {"message": "看看编写库有哪些项目，顺便查下资费词库前3条"})
reply = d.get("reply", "")
# 项目数动态对账：回答里的数字必须等于真实项目数（LLM 不许编数）
real_n = len(call("GET", "/api/active/projects", admin)[1])
check("B1", "对话：LLM 决策双工具调用并基于真实数据回答",
      st == 200 and f"{real_n} 个项目" in reply and len(d.get("tools_used", [])) == 2, (st, reply[:60]))

# B2 权限：造一个 viewer，让"大脑"决策调 update_spec → 执行器层拒绝
call("POST", "/api/auth/users", admin,
     {"username": "qa_llm_v", "password": "Qa@12345", "role": "viewer"})
users = call("GET", "/api/auth/users", admin)[1]
for u in users:
    if u["username"] == "qa_llm_v" and not u["enabled"]:
        call("PUT", f"/api/auth/users/{u['id']}", admin,
             {"username": u["username"], "role": "viewer", "enabled": True})
vw = call("POST", "/api/auth/login", body={"username": "qa_llm_v", "password": "Qa@12345"})[1]["token"]


def brain_chat_write(cfg, messages, schema):
    """两轮：先发 update_spec 工具调用；收到工具错误结果后如实回答。"""
    if not any(m.get("role") == "tool" for m in messages):
        return {"choices": [{"message": {"role": "assistant", "content": None, "tool_calls": [
            {"id": "c9", "type": "function",
             "function": {"name": "update_spec",
                          "arguments": json.dumps({"spec_key": "min_fields_error", "value": 9})}}]}}]}
    err = next((m["content"] for m in messages if m.get("role") == "tool"), "")
    return {"choices": [{"message": {"role": "assistant", "content": f"修改被拒绝：{err}"}}]}


chat_mod._llm_call = brain_chat_write
st, d = call("POST", "/api/chat", vw, {"message": "把字段数下限改成9"})
tool_reply = (d.get("tools_used") or [{}])[0]
spec_now = call("GET", "/api/studio/specs/min_fields_error", admin)[1]["value"]
check("B2", "对话：viewer 调写工具被拒且拒绝原因透出给用户（规范未被改）",
      "权限不足" in str(tool_reply.get("result", {})) and spec_now == 3 and "权限不足" in d.get("reply", ""),
      (tool_reply, spec_now, d.get("reply", "")[:50]))

# ══════════ B3/B4：评审批次自动优化（我当大脑）══════════
# 造真实数据：项目+FP+子过程（属性已知）+一条 text_replace 评审意见
st, d = call("POST", "/api/active/projects", admin,
             {"requirement_id": "QA-LLM-BRAIN", "requirement_name": "LLM大脑接线测试"})
PID = d["id"]
st, d = call("POST", f"/api/active/projects/{PID}/modules", admin,
             {"level1": "A", "level2": "B", "level3": "接线配置管理"})
MID = d["id"]
st, d = call("POST", f"/api/active/projects/{PID}/fps", admin, {"module_id": MID, "name": "新增接线验证对象"})
FID = d["id"]
tree = call("GET", f"/api/active/projects/{PID}/tree", admin)[1]
fp = tree["modules"][0]["fps"][0]
SUB_E = fp["subs"][0]["id"]
OLD_ATTRS = "测试字段甲、测试字段乙、测试字段丙、测试字段丁"
call("PUT", f"/api/active/subs/{SUB_E}", admin, {"data_attributes": OLD_ATTRS})
st, d = call("POST", f"/api/active/projects/{PID}/reviews", admin,
             {"target_type": "sub", "target_id": SUB_E, "target_label": "E子过程",
              "content": "把第2个字段'测试字段乙'替换为'生效日期'", "classify": "text_replace"})
RID = d["id"]

orig_af_llm = reviews_mod._call_llm


def brain_autofix(cfg, messages):
    """像真 LLM 一样：读系统给的评审上下文 JSON，逐条决策修改。"""
    contexts = json.loads(messages[-1]["content"])
    changes = []
    for c in contexts:
        sub_id = c.get("target_id")
        cur = c.get("现状") or {}
        attrs = next((s["data_attributes"] for s in cur.get("subs", []) if s["id"] == sub_id), "")
        new_attrs = attrs.replace("测试字段乙", "生效日期")
        changes.append({"review_id": c["review_id"], "target_type": "sub", "target_id": sub_id,
                        "fields": {"data_attributes": new_attrs}, "reason": "按意见替换第2字段"})
    return {"choices": [{"message": {"content": json.dumps(changes, ensure_ascii=False)}}]}


reviews_mod._call_llm = brain_autofix

st, d = call("POST", f"/api/active/projects/{PID}/reviews/auto-fix", admin, {})
applied = d.get("applied", [])
ver = d.get("version") or {}
check("B3a", "auto-fix：LLM 修改应用成功", st == 200 and len(applied) == 1 and ver.get("sha256"), (st, str(d)[:100]))
tree2 = call("GET", f"/api/active/projects/{PID}/tree", admin)[1]
new_attrs = tree2["modules"][0]["fps"][0]["subs"][0]["data_attributes"]
check("B3b", "auto-fix：数据真实变更", "生效日期" in new_attrs and "测试字段乙" not in new_attrs, new_attrs[:50])
rlist = call("GET", f"/api/active/projects/{PID}/reviews", admin)[1]
r = next(x for x in rlist if x["id"] == RID)
check("B3c", "auto-fix：意见 auto_done 且关联新版本", r["disposition"] == "auto_done" and r["version_id"] == ver.get("id"), r)

# B4 保险丝：意见要求加字段，但"LLM"给出含逗号的违规修改 → 门禁回滚
st, d = call("POST", f"/api/active/projects/{PID}/reviews", admin,
             {"target_type": "sub", "target_id": SUB_E, "target_label": "E子过程",
              "content": "希望增加一个字段", "classify": "text_replace"})
RID2 = d["id"]
attrs_before = call("GET", f"/api/active/projects/{PID}/tree", admin)[1]["modules"][0]["fps"][0]["subs"][0]["data_attributes"]


def brain_bad(cfg, messages):
    contexts = json.loads(messages[-1]["content"])
    changes = [{"review_id": c["review_id"], "target_type": "sub", "target_id": c["target_id"],
                "fields": {"data_attributes": "坏,数据,含逗号,只三个"}, "reason": "违规修改"}
               for c in contexts]
    return {"choices": [{"message": {"content": json.dumps(changes, ensure_ascii=False)}}]}


reviews_mod._call_llm = brain_bad
st, d = call("POST", f"/api/active/projects/{PID}/reviews/auto-fix", admin, {})
attrs_after = call("GET", f"/api/active/projects/{PID}/tree", admin)[1]["modules"][0]["fps"][0]["subs"][0]["data_attributes"]
rlist = call("GET", f"/api/active/projects/{PID}/reviews", admin)[1]
r2 = next(x for x in rlist if x["id"] == RID2)
check("B4", "保险丝：违规修改整批回滚（数据不变+意见仍待处理）",
      st == 422 and attrs_after == attrs_before and r2["disposition"] == "pending",
      (st, attrs_after == attrs_before, r2["disposition"]))

# ══════════ 清理 ══════════════════
chat_mod._llm_call = orig_chat_llm
reviews_mod._call_llm = orig_af_llm
call("DELETE", f"/api/active/projects/{PID}?confirm=active", admin)
users = call("GET", "/api/auth/users", admin)[1]
for u in users:
    if u["username"] == "qa_llm_v":
        call("PUT", f"/api/auth/users/{u['id']}", admin, {"username": u["username"], "role": "viewer", "enabled": False})
call("PUT", "/api/studio/llm-config", admin,
     {"provider": "openai-compatible", "base_url": "", "model": "", "api_key": "", "enabled": False})

fails = [x for x in RESULTS if not x[2]]
print(f"\n{'='*50}\nLLM 大脑接线测试: {len(RESULTS)-len(fails)}/{len(RESULTS)} 通过")
for tid, name, _, detail in fails:
    print(f"  ❌ {tid} {name}: {detail}")
sys.exit(1 if fails else 0)
