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
        echo "  ❌ $name -> code=$code http=$http_code resp=${resp:0:200}"
        FAIL=$((FAIL+1))
        FAILED_TESTS="$FAILED_TESTS\n  - $name: code=$code"
    fi
}

# 找一个存在的文件ID
FILE_LIST=$(curl -sk "$BASE/file/list?page=1&size=5" -H "Authorization: Bearer $TOKEN")
FILE_ID=$(echo "$FILE_LIST" | python3 -c 'import sys,json; d=json.load(sys.stdin); rs=d.get("data",{}).get("records",[]); print(rs[0]["id"] if rs else 0)')
echo "Using fileId=$FILE_ID"

# 找一个存在的文档ID
DOC_LIST=$(curl -sk "$BASE/doc/list?page=1&size=5" -H "Authorization: Bearer $TOKEN")
DOC_ID=$(echo "$DOC_LIST" | python3 -c 'import sys,json; d=json.load(sys.stdin); rs=d.get("data",{}).get("records",[]); print(rs[0]["id"] if rs else 0)')
echo "Using docId=$DOC_ID"

echo ""
echo "=== 文件相关API ==="
if [ "$FILE_ID" != "0" ]; then
    test_api "GET /file/{id} (文件详情)" "GET" "/file/$FILE_ID"
    test_api "GET /file/{id}/download (下载链接)" "GET" "/file/$FILE_ID/download"
    test_api "PUT /file/{id}/star (收藏)" "PUT" "/file/$FILE_ID/star"
    test_api "GET /tag/resource?resourceId=fileId&resourceType=file (文件标签)" "GET" "/tag/resource?resourceId=$FILE_ID&resourceType=file"
    test_api "GET /version/list?resourceId=fileId&resourceType=file (文件版本)" "GET" "/version/list?resourceId=$FILE_ID&resourceType=file"
else
    echo "  ⚠️ 无文件数据，跳过文件API"
fi

echo ""
echo "=== 文件上传测试 ==="
echo "test content for upload" > /tmp/test_upload.txt
UPLOAD_RESP=$(curl -sk -X POST "$BASE/file/upload" -H "Authorization: Bearer $TOKEN" -F "file=@/tmp/test_upload.txt" -F "folderId=0")
echo "  Upload resp: ${UPLOAD_RESP:0:200}"
NEW_FILE_ID=$(echo "$UPLOAD_RESP" | python3 -c 'import sys,json;print(json.load(sys.stdin).get("data",""))' 2>/dev/null)
if [ -n "$NEW_FILE_ID" ] && [ "$NEW_FILE_ID" != "None" ] && [ "$NEW_FILE_ID" != "" ]; then
    echo "  新文件ID: $NEW_FILE_ID"
    test_api "GET /file/{id}/content (新文件内容)" "GET" "/file/$NEW_FILE_ID/content"
    curl -sk -X DELETE "$BASE/file/$NEW_FILE_ID" -H "Authorization: Bearer $TOKEN" > /dev/null
else
    echo "  ⚠️ 文件上传返回异常"
fi

echo ""
echo "=== 文档相关API ==="
if [ "$DOC_ID" != "0" ]; then
    test_api "GET /doc/{id} (文档详情含content)" "GET" "/doc/$DOC_ID"
    test_api "GET /tag/resource?resourceId=docId&resourceType=doc (文档标签)" "GET" "/tag/resource?resourceId=$DOC_ID&resourceType=doc"
    test_api "GET /version/list?resourceId=docId&resourceType=doc (文档版本)" "GET" "/version/list?resourceId=$DOC_ID&resourceType=doc"
fi

echo ""
echo "=== 创建-编辑-查看文档完整流程 ==="
CREATE_DOC=$(curl -sk -X POST $BASE/doc -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"title":"API测试文档","content":"<p>测试内容</p>","folderId":0}')
NEW_DOC_ID=$(echo "$CREATE_DOC" | python3 -c 'import sys,json;print(json.load(sys.stdin).get("data",{}).get("id",0))' 2>/dev/null)
echo "创建文档 docId=$NEW_DOC_ID"
if [ "$NEW_DOC_ID" != "0" ] && [ "$NEW_DOC_ID" != "" ]; then
    test_api "GET /doc/{id} (新文档详情)" "GET" "/doc/$NEW_DOC_ID"
    test_api "PUT /doc/{id} (更新文档)" "PUT" "/doc/$NEW_DOC_ID" '{"title":"API测试文档-已编辑","content":"<p>编辑后的内容</p>"}'
    GET_AFTER=$(curl -sk "$BASE/doc/$NEW_DOC_ID" -H "Authorization: Bearer $TOKEN")
    CONTENT_VAL=$(echo "$GET_AFTER" | python3 -c 'import sys,json; d=json.load(sys.stdin); c=d.get("data",{}).get("content",""); print("YES" if c and len(c)>0 else "NO")')
    if [ "$CONTENT_VAL" = "YES" ]; then
        echo "  ✅ 编辑后content有值"
        PASS=$((PASS+1))
    else
        echo "  ❌ 编辑后content为空"
        FAIL=$((FAIL+1))
    fi
    curl -sk -X DELETE "$BASE/doc/$NEW_DOC_ID" -H "Authorization: Bearer $TOKEN" > /dev/null
fi

echo ""
echo "=== 标签CRUD ==="
test_api "GET /tag/list (标签列表)" "GET" "/tag/list"
TAG_NAME="verify_$(date +%s)"
CREATE_TAG=$(curl -sk -X POST $BASE/tag -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d "{\"name\":\"$TAG_NAME\"}")
TAG_ID=$(echo "$CREATE_TAG" | python3 -c 'import sys,json;print(json.load(sys.stdin).get("data",{}).get("id",0))' 2>/dev/null)
if [ "$TAG_ID" != "0" ] && [ "$TAG_ID" != "" ]; then
    test_api "POST /tag/resource (绑定标签)" "POST" "/tag/resource" "{\"tagId\":$TAG_ID,\"resourceId\":$DOC_ID,\"resourceType\":\"doc\"}"
    test_api "DELETE /tag/resource (解绑标签)" "DELETE" "/tag/resource" "{\"tagId\":$TAG_ID,\"resourceId\":$DOC_ID,\"resourceType\":\"doc\"}"
    test_api "DELETE /tag/{id} (删除标签)" "DELETE" "/tag/$TAG_ID"
fi

echo ""
echo "=== 分享API ==="
if [ "$DOC_ID" != "0" ]; then
    test_api "POST /share (创建分享)" "POST" "/share" '{"resourceType":"doc","resourceId":'"$DOC_ID"',"expireDays":7}'
fi

echo ""
echo "=== 搜索API ==="
test_api "GET /search?q=test (搜索)" "GET" "/search?q=test"

echo ""
echo "=== 空间/文件夹API ==="
test_api "GET /space/list (空间列表)" "GET" "/space/list"

echo ""
echo "=== 回收站API ==="
test_api "GET /trash/list (回收站列表)" "GET" "/trash/list?page=1&size=5"

echo ""
echo "========================================="
echo "测试结果: PASS=$PASS FAIL=$FAIL"
if [ $FAIL -gt 0 ]; then
    echo -e "失败项:$FAILED_TESTS"
fi
echo "========================================="
