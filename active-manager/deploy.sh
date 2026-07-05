#!/bin/bash
# ============================================================
# active-manager 部署脚本 — 在目标服务器上执行
# ============================================================
# 用法: bash deploy.sh <commit_sha> <branch>
# 示例: bash deploy.sh abc1234 dev
# ============================================================

set -e

COMMIT_SHA="${1:-unknown}"
BRANCH="${2:-dev}"

echo "============================================="
echo "  active-manager 自动部署开始"
echo "  时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo "  Commit: ${COMMIT_SHA}"
echo "  分支: ${BRANCH}"
echo "============================================="

# ======== 0. 环境准备（首次部署） ========
echo ""
echo ">>> [0/3] 环境检查 <<<"
if [ ! -d /root/devtools ]; then
  echo "⚠️ 首次部署：/root/devtools 不存在，开始克隆仓库..."
  git clone https://gitee.com/jonesAriven/devtools.git /root/devtools
fi

# 检查旧容器是否在跑
if docker ps --format '{{.Names}}' | grep -q '^activecode$'; then
  echo "ℹ️ 检测到旧容器 activecode 正在运行，部署时会重建"
  docker ps --filter "name=activecode" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
fi

# ======== 1. 同步代码 ========
echo ""
echo ">>> [1/3] 同步代码 <<<"
cd /root/devtools
REMOTE_URL=$(git remote get-url origin 2>/dev/null || echo "")
if [ "$REMOTE_URL" != "https://gitee.com/jonesAriven/devtools.git" ]; then
  git remote set-url origin https://gitee.com/jonesAriven/devtools.git
fi
git fetch origin "${BRANCH}"
git reset --hard "origin/${BRANCH}"
echo "✅ 代码已同步到 $(git rev-parse --short HEAD)"

# ======== 2. Docker Compose 构建 & 部署（端口配置在 docker-compose.yml） ========
echo ""
echo ">>> [2/3] Docker Compose 构建 & 部署 <<<"
cd /root/devtools/active-manager/activation-code-server

echo "--- JAR 包信息 ---"
ls -lh target/activation-code-server-1.0.0.jar

echo "--- 端口映射（来自 docker-compose.yml）---"
grep -A2 "ports:" docker-compose.yml | head -3

echo "--- 构建镜像并重建容器 ---"
docker compose -p activecode build --no-cache 2>&1 | tail -10
docker compose -p activecode up -d --force-recreate

echo "✅ 容器已启动（端口、环境变量均由 docker-compose.yml 管理）"

# ======== 3. 健康检查 ========
echo ""
echo ">>> [3/3] 健康检查 <<<"
sleep 10

echo "--- 容器状态 ---"
docker ps | grep activecode

echo "--- 健康检查 (端口 18080) ---"
for i in $(seq 1 8); do
  if curl -sf http://localhost:18080/activecode/login.html > /dev/null 2>&1; then
    echo "✅ 激活码服务健康! (尝试 $i)"
    break
  fi
  echo "⏳ 等待服务启动... ($i/8)"
  sleep 5
done

echo ""
echo "============================================="
echo "  active-manager 部署完成!"
echo "  内网: http://192.168.31.182:18080/activecode/login.html"
echo "  公网: https://tools.marschat.online/activecode/login.html"
echo "  时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo "============================================="
