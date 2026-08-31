#!/bin/bash
# deploy.sh — cosmic-studio 流水线部署脚本（在 mykng 上由 Woodpecker drone-ssh 执行）
# 流程：git 同步 → 前端构建(dist) → docker compose build → up → health check
# ⚠️ 关键：frontend/Dockerfile.web 只 COPY frontend/dist，不构建。
#    必须先在宿主机 npm run build 生成新 dist，否则 web 容器每次都只搬运旧产物，
#    前端改动永远不生效（2026-08-31 踩坑：连续多次"部署"前端无变化）。
set -e
APP_DIR="/root/devtools/cosmic-studio"
HEALTH_URL="http://127.0.0.1:8310/api/health"

cd "$APP_DIR"
echo "==> git 同步到 origin/main（$(date '+%F %T')）"
# 部署目录语义 = 镜像仓库：fetch + reset --hard，自愈 scp/调试残留的脏树
# （git pull 遇未提交修改会拒绝合并，正是流水线 #5/#7 失败的根因）
git fetch origin main || { echo "❌ git fetch 失败"; exit 1; }
git reset --hard origin/main || { echo "❌ git reset 失败"; exit 1; }
echo "==> commit: $(git log -1 --oneline)"

echo "==> 前端构建（重新生成 frontend/dist）"
cd "$APP_DIR/frontend"
[ -f package-lock.json ] && npm ci || npm install
npm run build
cd "$APP_DIR"

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
