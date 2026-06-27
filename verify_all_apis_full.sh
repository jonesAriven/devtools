#!/bin/bash
BASE="https://kb.marschat.online/kb/api"
PASS=0
FAIL=0
FAILED_TESTS=""

# 登录
LOGIN_RESP=$(curl -sk -X POST $BASE/auth/login -H 'Content-Type: application/json' -d '{"username":"admin","password":"admin123"}')
TOKEN=$(echo "$LOGIN_RESP" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["accessToken"])')
echo "Login OK, token: ${TOKEN:0:20}..."

# 辅助函数
test_api() {
    local name="$1"
    local method="$2"
    local path="$3"
    local body="$4"
    local expect_code="${5:-200}"
    
    local resp
    if [ -n "$body" ]; then
        resp=$(curl -sk -X "$method" "$BASE$path" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d "$body")
    else
        resp=$(curl -sk -X "$method" "$BASE$path" -H "Authorization: Bearer $TOKEN")
    fi
    
    local code=$(echo "$resp" | python3 -c 'import sys,json;print(json.load(sys.stdin).get("code",0))' 2>/dev/null)
    local http_code=$(curl -sk -o /dev/null -w "%{http_code}" -X "$method" "$BASE$path" -H "Authorization: Bearer $TOKEN" ${body:+-H 'Content-Type: application/json' -d "$body"})
    
    if [ "$code" = "200" ]; then
        echo "  ✅ $name -> code=200"
        PASS=$((PASS+1))
    else
        echo "  ❌ $name -> code=$code http=$http_code"
        echo "     resp=${resp:0:500}"
        FAIL=$((FAIL+1))
        FAILED_TESTS="$FAILED_TESTS\n  - $name: code=$code http=$http_code"
    fi
}

echo ""
echo "=== 准备测试数据 ==="

# 上传测试文件（不删除，用于测试）
echo "test content for full verification" > /tmp/test_full.txt
UPLOAD_RESP=$(curl -sk -X POST "$BASE/file/upload" -H "Authorization: Bearer $TOKEN" -F "file=@/tmp/test_full.txt" -F "folderId=0")
echo "  Upload resp: ${UPLOAD_RESP:0:200}"
TEST_FILE_ID=$(echo "$UPLOAD_RESP" | python3 -c 'import sys,json;print(json.load(sys.stdin).get("data",""))' 2>/dev/null)
echo "  测试文件ID: $TEST_FILE_ID"

# 创建测试文档（不删除，用于测试）
CREATE_DOC=$(curl -sk -X POST $BASE/doc -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"title":"API完整测试文档","content":"<p>这是用于API完整测试的文档内容</p>","folderId":0}')
TEST_DOC_ID=$(echo "$CREATE_DOC" | python3 -c 'import sys,json;print(json.load(sys.stdin).get("data",{}).get("id",0))' 2>/dev/null)
echo "  测试文档ID: $TEST_DOC_ID"

echo ""
echo "========================================="
echo "=== 【文件详情页 FileDetailView.vue 核心API测试】 ==="
echo "========================================="

echo ""
echo "--- 1. GET /file/{id} - 文件详情 ---"
test_api "GET /file/{id} (文件详情)" "GET" "/file/$TEST_FILE_ID"

echo ""
echo "--- 2. GET /file/{id}/content - 文件解析内容 (kb-file接口) ---"
echo "  详细响应："
CONTENT_RESP=$(curl -sk "$BASE/file/$TEST_FILE_ID/content" -H "Authorization: Bearer $TOKEN")
echo "  $CONTENT_RESP" | head -c 500
echo ""
CONTENT_CODE=$(echo "$CONTENT_RESP" | python3 -c 'import sys,json;print(json.load(sys.stdin).get("code",0))' 2>/dev/null)
if [ "$CONTENT_CODE" = "200" ]; then
    echo "  ✅ GET /file/{id}/content -> code=200"
    PASS=$((PASS+1))
else
    echo "  ❌ GET /file/{id}/content -> code=$CONTENT_CODE"
    FAIL=$((FAIL+1))
    FAILED_TESTS="$FAILED_TESTS\n  - GET /file/{id}/content: code=$CONTENT_CODE"
fi

echo ""
echo "--- 3. PUT /file/{id}/star - 切换收藏（第一次：收藏） ---"
test_api "PUT /file/{id}/star (收藏)" "PUT" "/file/$TEST_FILE_ID/star"

echo ""
echo "--- 4. PUT /file/{id}/star - 切换收藏（第二次：取消收藏） ---"
test_api "PUT /file/{id}/star (取消收藏)" "PUT" "/file/$TEST_FILE_ID/star"

echo ""
echo "--- 5. GET /file/{id}/download - 下载链接 ---"
test_api "GET /file/{id}/download (下载链接)" "GET" "/file/$TEST_FILE_ID/download"

echo ""
echo "--- 6. GET /version/list?resourceId={id}&resourceType=file - 版本列表 ---"
test_api "GET /version/list (文件版本列表)" "GET" "/version/list?resourceId=$TEST_FILE_ID&resourceType=file"

echo ""
echo "--- 7. GET /tag/list - 所有标签列表 ---"
test_api "GET /tag/list (所有标签)" "GET" "/tag/list"

echo ""
echo "--- 8. GET /tag/resource?resourceId={id}&resourceType=file - 资源标签 ---"
test_api "GET /tag/resource (文件标签)" "GET" "/tag/resource?resourceId=$TEST_FILE_ID&resourceType=file"

echo ""
echo "--- 9. POST /tag/resource - 添加标签 ---"
TAG_NAME="testtag_$(date +%s)"
CREATE_TAG=$(curl -sk -X POST $BASE/tag -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d "{\"name\":\"$TAG_NAME\"}")
TAG_ID=$(echo "$CREATE_TAG" | python3 -c 'import sys,json;print(json.load(sys.stdin).get("data",{}).get("id",0))' 2>/dev/null)
echo "  创建测试标签ID: $TAG_ID"
if [ "$TAG_ID" != "0" ] && [ "$TAG_ID" != "" ]; then
    test_api "POST /tag/resource (给文件添加标签)" "POST" "/tag/resource" "{\"tagId\":$TAG_ID,\"resourceId\":$TEST_FILE_ID,\"resourceType\":\"file\"}"
    
    echo ""
    echo "--- 10. 验证标签已添加到文件 ---"
    FILE_TAGS=$(curl -sk "$BASE/tag/resource?resourceId=$TEST_FILE_ID&resourceType=file" -H "Authorization: Bearer $TOKEN")
    FILE_TAG_COUNT=$(echo "$FILE_TAGS" | python3 -c 'import sys,json; d=json.load(sys.stdin); print(len(d.get("data",[])))' 2>/dev/null)
    echo "  文件当前标签数: $FILE_TAG_COUNT"
fi

echo ""
echo "--- 11. POST /share - 创建文件分享 ---"
test_api "POST /share (创建文件分享)" "POST" "/share" "{\"resourceType\":\"file\",\"resourceId\":$TEST_FILE_ID,\"expireDays\":7}"

echo ""
echo "--- 12. 获取分享code并测试 GET /share/detail/{code} ---"
SHARE_RESP=$(curl -sk -X POST "$BASE/share" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d "{\"resourceType\":\"file\",\"resourceId\":$TEST_FILE_ID,\"expireDays\":7}")
SHARE_CODE=$(echo "$SHARE_RESP" | python3 -c 'import sys,json;print(json.load(sys.stdin).get("data",{}).get("code",""))' 2>/dev/null)
echo "  分享code: $SHARE_CODE"
if [ -n "$SHARE_CODE" ] && [ "$SHARE_CODE" != "None" ]; then
    test_api "GET /share/detail/{code} (分享详情)" "GET" "/share/detail/$SHARE_CODE"
fi

echo ""
echo "========================================="
echo "=== 【文档编辑页 DocEditView.vue API测试】 ==="
echo "========================================="

echo ""
echo "--- 13. GET /doc/{id} - 文档详情（含content） ---"
test_api "GET /doc/{id} (文档详情含content)" "GET" "/doc/$TEST_DOC_ID"

echo ""
echo "--- 14. PUT /doc/{id} - 更新文档 ---"
test_api "PUT /doc/{id} (更新文档)" "PUT" "/doc/$TEST_DOC_ID" '{"title":"API完整测试文档-已更新","content":"<p>更新后的内容：这是修改后的文档</p>"}'

echo ""
echo "--- 15. 验证更新后content是否正确返回 ---"
GET_AFTER_UPDATE=$(curl -sk "$BASE/doc/$TEST_DOC_ID" -H "Authorization: Bearer $TOKEN")
CONTENT_AFTER=$(echo "$GET_AFTER_UPDATE" | python3 -c 'import sys,json; d=json.load(sys.stdin); c=d.get("data",{}).get("content",""); print(c[:100] if c else "EMPTY")' 2>/dev/null)
echo "  更新后content: $CONTENT_AFTER"
CONTENT_CHECK=$(echo "$GET_AFTER_UPDATE" | python3 -c 'import sys,json; d=json.load(sys.stdin); c=d.get("data",{}).get("content",""); print("YES" if c and "更新后的内容" in c else "NO")')
if [ "$CONTENT_CHECK" = "YES" ]; then
    echo "  ✅ content正确保存并返回"
    PASS=$((PASS+1))
else
    echo "  ❌ content未正确保存"
    FAIL=$((FAIL+1))
    FAILED_TESTS="$FAILED_TESTS\n  - 文档content持久化验证失败"
fi

echo ""
echo "--- 16. 文档标签 ---"
test_api "GET /tag/resource (文档标签)" "GET" "/tag/resource?resourceId=$TEST_DOC_ID&resourceType=doc"
if [ "$TAG_ID" != "0" ] && [ "$TAG_ID" != "" ]; then
    test_api "POST /tag/resource (给文档添加标签)" "POST" "/tag/resource" "{\"tagId\":$TAG_ID,\"resourceId\":$TEST_DOC_ID,\"resourceType\":\"doc\"}"
fi

echo ""
echo "--- 17. 文档版本列表 ---"
test_api "GET /version/list (文档版本列表)" "GET" "/version/list?resourceId=$TEST_DOC_ID&resourceType=doc"

echo ""
echo "--- 18. 文档分享 ---"
DOC_SHARE_RESP=$(curl -sk -X POST "$BASE/share" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d "{\"resourceType\":\"doc\",\"resourceId\":$TEST_DOC_ID,\"expireDays\":7}")
DOC_SHARE_CODE=$(echo "$DOC_SHARE_RESP" | python3 -c 'import sys,json;print(json.load(sys.stdin).get("data",{}).get("code",""))' 2>/dev/null)
echo "  文档分享code: $DOC_SHARE_CODE"

echo ""
echo "========================================="
echo "=== 【其他API验证】 ==="
echo "========================================="

echo ""
echo "--- 搜索API ---"
test_api "GET /search?q=test (搜索)" "GET" "/search?q=test"

echo ""
echo "--- 空间列表 ---"
test_api "GET /space/list (空间列表)" "GET" "/space/list"

echo ""
echo "--- 回收站列表 ---"
test_api "GET /trash/list (回收站列表)" "GET" "/trash/list?page=1&size=5"

echo ""
echo "--- 文件列表 ---"
test_api "GET /file/list (文件列表)" "GET" "/file/list?page=1&size=10"

echo ""
echo "--- 文档列表 ---"
test_api "GET /doc/list (文档列表)" "GET" "/doc/list?page=1&size=10"

echo ""
echo "========================================="
echo "=== 【版本回滚测试 (POST /version/{id}/rollback)】 ==="
echo "========================================="
# 获取文档版本列表，找到第一个版本进行回滚测试
VERSION_LIST=$(curl -sk "$BASE/version/list?resourceId=$TEST_DOC_ID&resourceType=doc" -H "Authorization: Bearer $TOKEN")
VERSION_ID=$(echo "$VERSION_LIST" | python3 -c 'import sys,json; d=json.load(sys.stdin); vs=d.get("data",[]); print(vs[0]["id"] if vs else 0)' 2>/dev/null)
echo "  尝试回滚文档版本ID: $VERSION_ID"
if [ "$VERSION_ID" != "0" ] && [ "$VERSION_ID" != "" ]; then
    test_api "POST /version/{id}/rollback (版本回滚)" "POST" "/version/$VERSION_ID/rollback"
fi

echo ""
echo "========================================="
echo "=== 【DELETE API测试】 ==="
echo "========================================="
echo "--- 解绑标签 ---"
if [ "$TAG_ID" != "0" ] && [ "$TAG_ID" != "" ]; then
    test_api "DELETE /tag/resource (解绑文件标签)" "DELETE" "/tag/resource" "{\"tagId\":$TAG_ID,\"resourceId\":$TEST_FILE_ID,\"resourceType\":\"file\"}"
    test_api "DELETE /tag/{id} (删除标签)" "DELETE" "/tag/$TAG_ID"
fi

echo ""
echo "========================================="
echo "=== 【最终测试结果汇总】 ==="
echo "========================================="
echo ""
echo "测试结果: PASS=$PASS FAIL=$FAIL"
echo "总测试数: $((PASS+FAIL))"
if [ $FAIL -gt 0 ]; then
    echo ""
    echo "❌ 失败项列表:"
    echo -e "$FAILED_TESTS"
else
    echo ""
    echo "🎉 所有API测试通过！"
fi
echo ""
echo "========================================="
echo "测试数据已保留以便复查："
echo "  测试文件ID: $TEST_FILE_ID"
echo "  测试文档ID: $TEST_DOC_ID"
echo "========================================="
