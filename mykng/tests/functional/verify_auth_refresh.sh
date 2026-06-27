#!/bin/bash
# auth/refresh 专项验证脚本
# 验证Bug1修复：selectOne→selectList.findFirst

set -e

BASE="https://kb.marschat.online/kb/api"
LOGIN_PAYLOAD='{"username":"admin","password":"admin123"}'

echo "=========================================="
echo "  auth/refresh 专项验证"
echo "=========================================="
echo ""

# 步骤1: 登录
echo "[1/3] 登录获取token..."
LOGIN_RESP=$(curl -sk -X POST "${BASE}/auth/login" -H 'Content-Type: application/json' -d "${LOGIN_PAYLOAD}")
echo "登录响应: ${LOGIN_RESP}"
echo ""

# 提取accessToken和refreshToken
ACCESS_TOKEN=$(echo "${LOGIN_RESP}" | python3 -c 'import sys,json;d=json.load(sys.stdin);print(d.get("data",{}).get("accessToken",""))')
REFRESH_TOKEN=$(echo "${LOGIN_RESP}" | python3 -c 'import sys,json;d=json.load(sys.stdin);print(d.get("data",{}).get("refreshToken",""))')

if [ -z "${ACCESS_TOKEN}" ] || [ -z "${REFRESH_TOKEN}" ]; then
    echo "[FAIL] 无法获取token, accessToken或refreshToken为空"
    exit 1
fi
echo "accessToken长度: ${#ACCESS_TOKEN}"
echo "refreshToken长度: ${#REFRESH_TOKEN}"
echo ""

# 步骤2: 调用auth/refresh
echo "[2/3] 调用 auth/refresh 刷新token..."
REFRESH_PAYLOAD="{\"refreshToken\":\"${REFRESH_TOKEN}\"}"
HTTP_CODE=$(curl -sk -o /tmp/refresh_resp.json -w "%{http_code}" -X POST "${BASE}/auth/refresh" -H 'Content-Type: application/json' -d "${REFRESH_PAYLOAD}")
REFRESH_RESP=$(cat /tmp/refresh_resp.json)
echo "HTTP状态码: ${HTTP_CODE}"
echo "响应: ${REFRESH_RESP}"
echo ""

# 步骤3: 验证结果
echo "[3/3] 验证结果..."
if [ "${HTTP_CODE}" = "200" ]; then
    echo "[PASS] auth/refresh 返回 200, Bug1修复确认!"
    # 检查响应中是否有新的token
    NEW_ACCESS=$(echo "${REFRESH_RESP}" | python3 -c 'import sys,json;d=json.load(sys.stdin);print(d.get("data",{}).get("accessToken","MISSING"))' 2>/dev/null || echo "PARSE_FAIL")
    if [ "${NEW_ACCESS}" != "MISSING" ] && [ "${NEW_ACCESS}" != "PARSE_FAIL" ] && [ -n "${NEW_ACCESS}" ]; then
        echo "  新accessToken长度: ${#NEW_ACCESS}"
        echo "  refresh接口正常返回新token"
    else
        echo "  [WARN] 响应中未找到新accessToken字段,请检查响应结构"
    fi
    exit 0
else
    echo "[FAIL] auth/refresh 返回 ${HTTP_CODE}, Bug1未修复或存在其他问题!"
    exit 1
fi
