#!/bin/bash
# 导入龙虾记忆仓库到 kb-intelligence 知识库
# 执行：bash import-openclaw.sh

set -e

BASE_URL="http://localhost:8090/kb/api"
IMPORT_PATH="/root/openclaw-work-space"

echo "=== Step 1: 登录获取 token ==="
LOGIN_RESP=$(curl -s -X POST ${BASE_URL}/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}')
echo "登录响应: ${LOGIN_RESP}"

TOKEN=$(echo "${LOGIN_RESP}" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data',{}).get('accessToken',''))" 2>/dev/null || echo "")
if [ -z "${TOKEN}" ]; then
    echo "ERROR: 获取 token 失败"
    exit 1
fi
echo "TOKEN 获取成功 (长度: ${#TOKEN})"

echo ""
echo "=== Step 2: 导入前统计 ==="
echo "记忆仓库路径: ${IMPORT_PATH}"
FILE_COUNT=$(find "${IMPORT_PATH}" -type f -name '*.md' | wc -l)
echo "待导入 .md 文件数: ${FILE_COUNT}"

echo ""
echo "=== Step 3: 调用导入接口 ==="
echo "POST ${BASE_URL}/intelligence/import/path"
echo "Body: {\"path\":\"${IMPORT_PATH}\",\"incremental\":false}"
echo "开始时间: $(date '+%Y-%m-%d %H:%M:%S')"

IMPORT_RESP=$(curl -s -X POST ${BASE_URL}/intelligence/import/path \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${TOKEN}" \
  -d "{\"path\":\"${IMPORT_PATH}\",\"incremental\":false}" \
  --max-time 300)

echo "结束时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo "导入响应: ${IMPORT_RESP}"

echo ""
echo "=== Step 4: 导入后验证 ==="
# 查询知识库统计
STATS_RESP=$(curl -s -X GET ${BASE_URL}/intelligence/machine/stats \
  -H "Authorization: Bearer ${TOKEN}")
echo "统计信息: ${STATS_RESP}"

# 查询文档列表（前 10 条）
DOCS_RESP=$(curl -s -X GET "${BASE_URL}/intelligence/machine/docs?page=1&size=10" \
  -H "Authorization: Bearer ${TOKEN}")
echo ""
echo "文档列表（前 10 条）: ${DOCS_RESP}"

echo ""
echo "=== Step 5: 数据库直查 ==="
# 直接查询 MySQL 确认数据落库
mysql -h 192.168.31.182 -P 3306 -uroot -pHwx@1120930 -e "USE kb_intelligence; SELECT COUNT(*) AS doc_count FROM kn_doc; SELECT COUNT(*) AS host_count FROM kn_host; SELECT COUNT(*) AS service_count FROM kn_service; SELECT COUNT(*) AS command_count FROM kn_command; SELECT COUNT(*) AS timeline_count FROM kn_timeline;" 2>/dev/null || echo "MySQL 查询失败（可能需要在内网 Debian 上执行）"

echo ""
echo "=== 导入流程完成 ==="
