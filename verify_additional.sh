#!/bin/bash
BASE="https://kb.marschat.online/kb/api"
LOGIN_RESP=$(curl -sk -X POST $BASE/auth/login -H 'Content-Type: application/json' -d '{"username":"admin","password":"admin123"}')
TOKEN=$(echo "$LOGIN_RESP" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["accessToken"])')
echo "Login OK"

echo ""
echo "=== 1. 测试 DELETE /file/{id} ==="
echo "先上传一个新文件用于删除测试..."
echo "delete test" > /tmp/del_test.txt
UPLOAD=$(curl -sk -X POST "$BASE/file/upload" -H "Authorization: Bearer $TOKEN" -F "file=@/tmp/del_test.txt" -F "folderId=0")
DEL_FILE_ID=$(echo "$UPLOAD" | python3 -c 'import sys,json;print(json.load(sys.stdin).get("data",""))')
echo "  待删除文件ID: $DEL_FILE_ID"
if [ -n "$DEL_FILE_ID" ]; then
    DEL_RESP=$(curl -sk -X DELETE "$BASE/file/$DEL_FILE_ID" -H "Authorization: Bearer $TOKEN")
    echo "  DELETE resp: $DEL_RESP"
    DEL_CODE=$(echo "$DEL_RESP" | python3 -c 'import sys,json;print(json.load(sys.stdin).get("code",0))' 2>/dev/null)
    if [ "$DEL_CODE" = "200" ]; then
        echo "  ✅ DELETE /file/{id} -> code=200"
    else
        echo "  ❌ DELETE /file/{id} FAIL code=$DEL_CODE"
    fi
fi

echo ""
echo "=== 2. 测试 DELETE /doc/{id} ==="
CREATE_DOC=$(curl -sk -X POST $BASE/doc -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"title":"删除测试文档","content":"<p>to delete</p>","folderId":0}')
DEL_DOC_ID=$(echo "$CREATE_DOC" | python3 -c 'import sys,json;print(json.load(sys.stdin).get("data",{}).get("id",0))')
echo "  待删除文档ID: $DEL_DOC_ID"
if [ "$DEL_DOC_ID" != "0" ]; then
    DEL_DOC_RESP=$(curl -sk -X DELETE "$BASE/doc/$DEL_DOC_ID" -H "Authorization: Bearer $TOKEN")
    echo "  DELETE doc resp: $DEL_DOC_RESP"
    DEL_DOC_CODE=$(echo "$DEL_DOC_RESP" | python3 -c 'import sys,json;print(json.load(sys.stdin).get("code",0))' 2>/dev/null)
    if [ "$DEL_DOC_CODE" = "200" ]; then
        echo "  ✅ DELETE /doc/{id} -> code=200"
    else
        echo "  ❌ DELETE /doc/{id} FAIL code=$DEL_DOC_CODE"
    fi
fi

echo ""
echo "=== 3. 详细查看分享创建返回的完整数据 ==="
SHARE_FULL=$(curl -sk -X POST "$BASE/share" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"resourceType":"doc","resourceId":60,"expireDays":7}')
echo "  创建分享完整响应: $SHARE_FULL"
echo ""
CODE=$(echo "$SHARE_FULL" | python3 -c 'import sys,json;print(json.load(sys.stdin).get("data",{}).get("code",""))')
SHARE_ID=$(echo "$SHARE_FULL" | python3 -c 'import sys,json;print(json.load(sys.stdin).get("data",{}).get("id",""))')
echo "  分享code: $CODE, 分享id: $SHARE_ID"
echo ""
echo "  尝试访问 /share/detail/$CODE (不带提取码):"
DETAIL1=$(curl -sk "$BASE/share/detail/$CODE" -H "Authorization: Bearer $TOKEN")
echo "  resp: $DETAIL1"
echo ""
echo "  查看分享列表:"
SHARE_LIST=$(curl -sk "$BASE/share/list?page=1&size=5" -H "Authorization: Bearer $TOKEN")
echo "  share/list: $SHARE_LIST"

echo ""
echo "=== 4. 再次确认 GET /file/9/content (保留的测试文件) ==="
CONTENT_RESP=$(curl -sk "$BASE/file/9/content" -H "Authorization: Bearer $TOKEN")
echo "  /file/9/content resp: $CONTENT_RESP"
echo ""
echo "  尝试不同类型文件 - 上传一个 .md 文件测试 content 接口:"
echo "# Test Markdown" > /tmp/test.md
echo "Hello world" >> /tmp/test.md
UPLOAD_MD=$(curl -sk -X POST "$BASE/file/upload" -H "Authorization: Bearer $TOKEN" -F "file=@/tmp/test.md" -F "folderId=0")
MD_ID=$(echo "$UPLOAD_MD" | python3 -c 'import sys,json;print(json.load(sys.stdin).get("data",""))')
echo "  上传md文件ID: $MD_ID"
if [ -n "$MD_ID" ]; then
    MD_CONTENT=$(curl -sk "$BASE/file/$MD_ID/content" -H "Authorization: Bearer $TOKEN")
    echo "  /file/$MD_ID/content resp: $MD_CONTENT"
    curl -sk -X DELETE "$BASE/file/$MD_ID" -H "Authorization: Bearer $TOKEN" > /dev/null
fi

echo ""
echo "  尝试上传一个 .txt 文件 (明确text/plain)测试 content:"
echo "plain text content" > /tmp/test2.txt
UPLOAD_TXT=$(curl -sk -X POST "$BASE/file/upload" -H "Authorization: Bearer $TOKEN" -F "file=@/tmp/test2.txt" -F "folderId=0")
TXT_ID=$(echo "$UPLOAD_TXT" | python3 -c 'import sys,json;print(json.load(sys.stdin).get("data",""))')
echo "  上传txt文件ID: $TXT_ID"
if [ -n "$TXT_ID" ]; then
    TXT_CONTENT=$(curl -sk "$BASE/file/$TXT_ID/content" -H "Authorization: Bearer $TOKEN")
    echo "  /file/$TXT_ID/content resp: $TXT_CONTENT"
    TXT_DETAIL=$(curl -sk "$BASE/file/$TXT_ID" -H "Authorization: Bearer $TOKEN")
    echo "  /file/$TXT_ID detail: $TXT_DETAIL" | head -c 500
    curl -sk -X DELETE "$BASE/file/$TXT_ID" -H "Authorization: Bearer $TOKEN" > /dev/null
fi

echo ""
echo "=== 5. 验证现有文件列表，确认文件9存在 ==="
FILE9=$(curl -sk "$BASE/file/9" -H "Authorization: Bearer $TOKEN")
echo "  /file/9: $FILE9"
