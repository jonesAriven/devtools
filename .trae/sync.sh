#!/bin/bash
# RAG 数据自动同步脚本
# 部署在 内网Debian /home/root01/rag-tools/sync.sh
# 功能: git pull → 检测变更 → 增量灌入 + 清理删除 → 记录日志
# Cron: */30 * * * * /home/root01/rag-tools/sync.sh >> /home/root01/rag-tools/sync.log 2>&1

set -u
SCRIPT_DIR="/home/root01/rag-tools"
REPO_PATH="/home/root01/openclaw-work-space"
PYTHON="python3"
LOG_DATE=$(date '+%Y-%m-%d %H:%M:%S')

echo "========================================"
echo "[$LOG_DATE] RAG sync 开始"
echo "========================================"

cd "$REPO_PATH" || {
    echo "[$LOG_DATE] ERROR: 无法进入 $REPO_PATH"
    exit 1
}

# 记录 pull 前的 commit
BEFORE=$(git rev-parse HEAD 2>/dev/null || echo "none")

# git pull
echo "[$LOG_DATE] git pull..."
git pull --ff-only 2>&1 | sed 's/^/  /'

AFTER=$(git rev-parse HEAD 2>/dev/null || echo "none")

if [ "$BEFORE" = "$AFTER" ]; then
    echo "[$LOG_DATE] 无变更 (commit=$AFTER)"
    # 仍然输出 stats
    cd "$SCRIPT_DIR"
    $PYTHON ingest.py --stats 2>&1 | sed 's/^/  /'
    echo "[$LOG_DATE] sync 完成（无变更）"
    exit 0
fi

echo "[$LOG_DATE] 检测到变更: $BEFORE -> $AFTER"

# 增量灌入变更文件
echo "[$LOG_DATE] 增量灌入变更文件..."
cd "$SCRIPT_DIR"
$PYTHON ingest.py --changed 2>&1 | sed 's/^/  /'

# 清理删除文件
echo "[$LOG_DATE] 清理删除文件..."
$PYTHON ingest.py --deleted 2>&1 | sed 's/^/  /'

# 输出最终统计
echo "[$LOG_DATE] 最终统计:"
$PYTHON ingest.py --stats 2>&1 | sed 's/^/  /'

echo "[$LOG_DATE] sync 完成"
