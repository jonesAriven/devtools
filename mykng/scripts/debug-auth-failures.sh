#!/bin/bash
# 排查 E2E 失败用例：黑名单 + /auth/me 路由
set +e
BASE="http://100.93.36.113:8090/kb/api"

echo "=== 1. 登录获取 token A ==="
RESP_A=$(curl -s -X POST "$BASE/auth/login" -H "Content-Type: application/json" -d '{"username":"admin","password":"admin123"}')
echo "$RESP_A" | python3 -c "import sys,json; d=json.load(sys.stdin); print('code=',d.get('code'),'accessToken=',d.get('data',{}).get('accessToken','')[:50]+'...')" 2>/dev/null
TOKEN_A=$(echo "$RESP_A" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['accessToken'])" 2>/dev/null)
echo "TOKEN_A=${TOKEN_A:0:50}..."

echo ""
echo "=== 2. 用 token A 调用 /auth/me（kb-auth 接口）==="
curl -s -X GET "$BASE/auth/me" -H "Authorization: Bearer $TOKEN_A" | head -c 300
echo ""

echo ""
echo "=== 3. 用 token A 调用 /space/list（kb-knowledge 接口）==="
curl -s -X GET "$BASE/space/list" -H "Authorization: Bearer $TOKEN_A" | head -c 300
echo ""

echo ""
echo "=== 4. 登出（把 token A 加入黑名单）==="
curl -s -X POST "$BASE/auth/logout" -H "Authorization: Bearer $TOKEN_A" | head -c 200
echo ""

echo ""
echo "=== 5. 登出后用 token A 调用 /auth/me（应被黑名单拒绝 401）==="
curl -s -o /dev/null -w "HTTP_STATUS=%{http_code}\n" -X GET "$BASE/auth/me" -H "Authorization: Bearer $TOKEN_A"
curl -s -X GET "$BASE/auth/me" -H "Authorization: Bearer $TOKEN_A" | head -c 300
echo ""

echo ""
echo "=== 6. 登出后用 token A 调用 /space/list（kb-gateway 不检查黑名单，可能 200）==="
curl -s -o /dev/null -w "HTTP_STATUS=%{http_code}\n" -X GET "$BASE/space/list" -H "Authorization: Bearer $TOKEN_A"
curl -s -X GET "$BASE/space/list" -H "Authorization: Bearer $TOKEN_A" | head -c 300
echo ""

echo ""
echo "=== 7. 重新登录获取 token C ==="
RESP_C=$(curl -s -X POST "$BASE/auth/login" -H "Content-Type: application/json" -d '{"username":"admin","password":"admin123"}')
TOKEN_C=$(echo "$RESP_C" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['accessToken'])" 2>/dev/null)
echo "TOKEN_C=${TOKEN_C:0:50}..."

echo ""
echo "=== 8. 用 token C 调用 /auth/me（新 token，应成功 200）==="
curl -s -o /dev/null -w "HTTP_STATUS=%{http_code}\n" -X GET "$BASE/auth/me" -H "Authorization: Bearer $TOKEN_C"
curl -s -X GET "$BASE/auth/me" -H "Authorization: Bearer $TOKEN_C" | head -c 300
echo ""

echo ""
echo "=== 9. 用 token C 调用 /space/list（应成功 200）==="
curl -s -o /dev/null -w "HTTP_STATUS=%{http_code}\n" -X GET "$BASE/space/list" -H "Authorization: Bearer $TOKEN_C"
echo ""

echo "=== 完成 ==="
