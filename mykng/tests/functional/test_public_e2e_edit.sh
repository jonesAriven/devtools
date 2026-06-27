#!/bin/bash
# 公网完整编辑流程 E2E 验证脚本
# 模拟用户操作: 创建→查询→编辑→查询→删除

# 注意: 不使用 set -e, 允许脚本在单步失败时继续执行并汇总结果

BASE="https://kb.marschat.online/kb/api"
LOGIN_PAYLOAD='{"username":"admin","password":"admin123"}'
TS=$(date +%s)
DOC_TITLE="E2E测试文档_${TS}"
DOC_CONTENT_INIT="这是初始内容,创建于$(date '+%Y-%m-%d %H:%M:%S')"
DOC_TITLE_EDIT="E2E测试文档_已编辑_${TS}"
DOC_CONTENT_EDIT="这是编辑后的内容,更新于$(date '+%Y-%m-%d %H:%M:%S')"

PASS=0
FAIL=0
RESULTS=""

log_pass() {
    PASS=$((PASS+1))
    RESULTS="${RESULTS}[PASS] $1\n"
    echo "[PASS] $1"
}

log_fail() {
    FAIL=$((FAIL+1))
    RESULTS="${RESULTS}[FAIL] $1\n"
    echo "[FAIL] $1"
}

echo "=========================================="
echo "  公网完整编辑流程 E2E 验证"
echo "  域名: ${BASE}"
echo "  时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo "=========================================="
echo ""

# 步骤1: 登录
echo "[步骤1] 登录获取token..."
LOGIN_RESP=$(curl -sk -X POST "${BASE}/auth/login" -H 'Content-Type: application/json' -d "${LOGIN_PAYLOAD}")
ACCESS_TOKEN=$(echo "${LOGIN_RESP}" | python3 -c 'import sys,json;d=json.load(sys.stdin);print(d.get("data",{}).get("accessToken",""))' 2>/dev/null || echo "")

if [ -z "${ACCESS_TOKEN}" ]; then
    echo "[FATAL] 登录失败,无法继续测试"
    echo "登录响应: ${LOGIN_RESP}"
    exit 1
fi
echo "登录成功, accessToken长度: ${#ACCESS_TOKEN}"
echo ""

AUTH_HEADER="Authorization: Bearer ${ACCESS_TOKEN}"

# 步骤2: 创建文档
echo "[步骤2] 创建文档 (POST /kb/api/doc)..."
DOC_PAYLOAD="{\"folderId\":0,\"title\":\"${DOC_TITLE}\",\"content\":\"${DOC_CONTENT_INIT}\"}"
CREATE_RESP=$(curl -sk -X POST "${BASE}/doc" -H 'Content-Type: application/json' -H "${AUTH_HEADER}" -d "${DOC_PAYLOAD}")
echo "创建响应: ${CREATE_RESP}"

DOC_ID=$(echo "${CREATE_RESP}" | python3 -c 'import sys,json;d=json.load(sys.stdin);
# 兼容多种响应结构, 处理 data:null 的情况
data=d.get("data")
if data is None:
    print("")
elif isinstance(data,dict):
    val = data.get("id") or data.get("docId") or data.get("doc_id") or ""
    print(val if val else "")
else:
    print(str(data) if data else "")' 2>/dev/null || echo "")

if [ -z "${DOC_ID}" ]; then
    echo "[FATAL] 创建文档失败,无法获取文档ID"
    log_fail "创建文档 - 无法获取ID"
    echo "原始响应: ${CREATE_RESP}"
    echo ""
    echo "=========================================="
    echo "  E2E结果: PASS=${PASS}, FAIL=${FAIL}"
    echo "=========================================="
    exit 1
fi
echo "文档创建成功, ID: ${DOC_ID}"
log_pass "创建文档 (ID: ${DOC_ID})"
echo ""

# 步骤3: 获取文档详情
echo "[步骤3] 获取文档详情 (GET /kb/api/doc/${DOC_ID})..."
GET_RESP=$(curl -sk -X GET "${BASE}/doc/${DOC_ID}" -H "${AUTH_HEADER}")
echo "详情响应: ${GET_RESP}"

# 解析content字段
GET_CONTENT=$(echo "${GET_RESP}" | python3 -c 'import sys,json;d=json.load(sys.stdin);
data=d.get("data",{})
if isinstance(data,dict):
    print(data.get("content","") or "")
else:
    print("")' 2>/dev/null || echo "")
GET_TITLE=$(echo "${GET_RESP}" | python3 -c 'import sys,json;d=json.load(sys.stdin);
data=d.get("data",{})
if isinstance(data,dict):
    print(data.get("title","") or "")
else:
    print("")' 2>/dev/null || echo "")

echo "获取到的title: ${GET_TITLE}"
echo "获取到的content: ${GET_CONTENT}"

if [ -n "${GET_CONTENT}" ]; then
    log_pass "获取文档详情 - content字段有值"
else
    log_fail "获取文档详情 - content字段为空"
fi

if [ "${GET_CONTENT}" = "${DOC_CONTENT_INIT}" ]; then
    log_pass "初始content值匹配"
else
    log_fail "初始content值不匹配 (期望: ${DOC_CONTENT_INIT}, 实际: ${GET_CONTENT})"
fi
echo ""

# 步骤4: 编辑文档
echo "[步骤4] 编辑文档 (PUT /kb/api/doc/${DOC_ID})..."
EDIT_PAYLOAD="{\"title\":\"${DOC_TITLE_EDIT}\",\"content\":\"${DOC_CONTENT_EDIT}\"}"
EDIT_RESP=$(curl -sk -X PUT "${BASE}/doc/${DOC_ID}" -H 'Content-Type: application/json' -H "${AUTH_HEADER}" -d "${EDIT_PAYLOAD}")
echo "编辑响应: ${EDIT_RESP}"
EDIT_CODE=$(echo "${EDIT_RESP}" | python3 -c 'import sys,json;d=json.load(sys.stdin);print(d.get("code",-1))' 2>/dev/null || echo "-1")
if [ "${EDIT_CODE}" = "0" ] || [ "${EDIT_CODE}" = "200" ]; then
    log_pass "编辑文档PUT成功 (code=${EDIT_CODE})"
else
    log_fail "编辑文档PUT失败 (code=${EDIT_CODE})"
fi
echo ""

# 步骤5: 再次获取文档详情, 验证修改生效
echo "[步骤5] 再次获取文档详情, 验证编辑生效..."
sleep 1
GET_RESP2=$(curl -sk -X GET "${BASE}/doc/${DOC_ID}" -H "${AUTH_HEADER}")
GET_CONTENT2=$(echo "${GET_RESP2}" | python3 -c 'import sys,json;d=json.load(sys.stdin);
data=d.get("data",{})
if isinstance(data,dict):
    print(data.get("content","") or "")
else:
    print("")' 2>/dev/null || echo "")
GET_TITLE2=$(echo "${GET_RESP2}" | python3 -c 'import sys,json;d=json.load(sys.stdin);
data=d.get("data",{})
if isinstance(data,dict):
    print(data.get("title","") or "")
else:
    print("")' 2>/dev/null || echo "")

echo "编辑后title: ${GET_TITLE2}"
echo "编辑后content: ${GET_CONTENT2}"

if [ "${GET_TITLE2}" = "${DOC_TITLE_EDIT}" ]; then
    log_pass "编辑后title已更新"
else
    log_fail "编辑后title未更新 (期望: ${DOC_TITLE_EDIT}, 实际: ${GET_TITLE2})"
fi

if [ "${GET_CONTENT2}" = "${DOC_CONTENT_EDIT}" ]; then
    log_pass "编辑后content已更新 - 修复确认!"
else
    log_fail "编辑后content未更新 (期望: ${DOC_CONTENT_EDIT}, 实际: ${GET_CONTENT2})"
fi
echo ""

# 步骤6: 删除文档
echo "[步骤6] 删除文档 (DELETE /kb/api/doc/${DOC_ID})..."
DEL_RESP=$(curl -sk -X DELETE "${BASE}/doc/${DOC_ID}" -H "${AUTH_HEADER}")
echo "删除响应: ${DEL_RESP}"
DEL_CODE=$(echo "${DEL_RESP}" | python3 -c 'import sys,json;d=json.load(sys.stdin);print(d.get("code",-1))' 2>/dev/null || echo "-1")

if [ "${DEL_CODE}" = "0" ] || [ "${DEL_CODE}" = "200" ]; then
    log_pass "删除文档成功"
else
    log_fail "删除文档可能失败 (code: ${DEL_CODE})"
fi
echo ""

# 验证删除生效
echo "[验证] 确认文档已删除..."
GET_AFTER_DEL=$(curl -sk -X GET "${BASE}/doc/${DOC_ID}" -H "${AUTH_HEADER}")
AFTER_DEL_CODE=$(echo "${GET_AFTER_DEL}" | python3 -c 'import sys,json;d=json.load(sys.stdin);print(d.get("code",-1))' 2>/dev/null || echo "-1")
if [ "${AFTER_DEL_CODE}" != "0" ] && [ "${AFTER_DEL_CODE}" != "200" ]; then
    log_pass "删除后文档不可访问,删除生效"
else
    log_fail "删除后文档仍可访问,删除可能未生效"
fi
echo ""

echo "=========================================="
echo "  E2E测试结果汇总"
echo "=========================================="
echo "通过: ${PASS}"
echo "失败: ${FAIL}"
echo "总计: $((PASS+FAIL))"
echo ""
echo "详细结果:"
echo -e "${RESULTS}"
echo "=========================================="

if [ ${FAIL} -eq 0 ]; then
    echo "  [全部通过] 公网编辑流程E2E验证成功!"
    exit 0
else
    echo "  [存在失败] 共 ${FAIL} 项失败"
    exit 1
fi
