#!/bin/bash
# 测试 ingest.py
cd /home/root01/rag-tools
echo "===STATS==="
python3 ingest.py --stats
echo "===TEST_FILE==="
python3 ingest.py --file /home/root01/openclaw-work-space/小桉工作规则.md
echo "===COUNT==="
curl -s -X POST http://localhost:6333/collections/memory/points/count \
  -H "Content-Type: application/json" \
  -d '{"exact": true}'
echo
echo "===DONE==="
