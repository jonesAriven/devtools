#!/bin/bash
set -e
echo "=== 删除旧 collection ==="
curl -s -X DELETE http://localhost:6333/collections/memory
echo ""
echo "=== 重建 collection (512 维 Cosine) ==="
curl -s -X PUT http://localhost:6333/collections/memory \
  -H "Content-Type: application/json" \
  -d '{"vectors": {"size": 512, "distance": "Cosine"}}'
echo ""
echo "=== 验证 ==="
curl -s http://localhost:6333/collections/memory | python3 -m json.tool
