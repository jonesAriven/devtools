#!/bin/bash
# deploy.sh — cosmic-studio 流水线部署脚本（在 mykng 上由 Woodpecker drone-ssh 执行）
# 流程：git pull → docker compose build → up → health check
set -e
APP_DIR="/root/devtools/cosmic-studio"
HEALTH_URL="http://127.0.0.1:8310/api/health"

cd "$APP_DIR"
echo "==> git pull（$(git rev-parse --abbrev-ref HEAD) @ $(date '+%F %T')）"
git pull --ff-only origin main || { echo "❌ git pull 失败"; exit 1; }
echo "==> commit: $(git log -1 --oneline)"

echo "==> docker compose build + up"
docker compose up -d --build --remove-orphans

echo "==> health check（最多 60s）"
for i in $(seq 1 12); do
  sleep 5
  BODY=$(curl -s "$HEALTH_URL" || true)
  if echo "$BODY" | grep -q '"status":"ok"'; then
    echo "✅ 部署成功: $BODY"
    echo "   commit: $(git log -1 --format='%h %s')"
    exit 0
  fi
  echo "  [$i/12] waiting: $BODY"
done
echo "❌ 健康检查超时"
docker compose logs --tail 20 cosmic-api
exit 1
