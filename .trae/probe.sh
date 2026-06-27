#!/bin/bash
# RAG 架构探查脚本（精简版，避免 find / 耗时）
echo "===HOSTNAME==="
hostname
echo "===QDRANT_COLLECTION==="
curl -s --max-time 3 http://localhost:6333/collections/memory
echo
echo "===QDRANT_COUNT==="
curl -s --max-time 3 -X POST http://localhost:6333/collections/memory/points/count \
  -H "Content-Type: application/json" -d '{"exact": true}'
echo
echo "===RAG_DIR_OPT==="
ls -la /opt/rag-mcp/ 2>/dev/null
echo "===RAG_DIR_HOME==="
ls -la /home/root01/ 2>/dev/null | head -20
echo "===RAG_DIR_TOOLS==="
ls -la /home/root01/tools/ 2>/dev/null
echo "===PYTHON_SCRIPTS==="
ls /opt/rag-mcp/*.py 2>/dev/null
ls /home/root01/*.py 2>/dev/null
ls /home/root01/tools/*.py 2>/dev/null
echo "===CRON==="
crontab -l 2>/dev/null
echo "===GIT_REPO==="
ls -d /home/root01/openclaw* 2>/dev/null
ls -d /opt/openclaw* 2>/dev/null
ls -d /root/openclaw* 2>/dev/null
ls -d /srv/openclaw* 2>/dev/null
echo "===PYTHON==="
python3 --version
echo "===PIP_PACKAGES==="
pip3 list 2>/dev/null | grep -iE 'sentence|qdrant|fastapi|flask|torch|transformers' | head -10
echo "===DOCKER==="
docker ps --format '{{.Names}}|{{.Image}}|{{.Status}}' 2>/dev/null
echo "===EMBEDDING_PROCESS==="
ps aux | grep -E 'embed|sentence|bge' | grep -v grep | head -3
echo "===DONE==="
