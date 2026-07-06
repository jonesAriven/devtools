#!/bin/bash
# ============================================================
# active-manager (激活码系统) 部署脚本 — 在目标服务器上执行
# ============================================================
# 用法: bash deploy.sh <commit_sha> <branch>
# 示例: bash deploy.sh abc1234 dev
#
# 部署信息（基于实际服务器检查 2026-07-06）:
#   项目名: activation-code-server
#   端口: 18080(宿主机) → 8080(容器)
#   Docker Compose Project: activecode
#   容器名: activecode
#   数据库: 宿主机 MySQL (host.docker.internal:3306/tools)
#   DB凭据: tools / toolsmarschat
#
# 部署目标:
#   mykng-debain (当前运行在此服务器)
# ============================================================

set -e

COMMIT_SHA="${1:-unknown}"
BRANCH="${2:-dev}"

echo "============================================="
echo "  🔑 active-manager 激活码系统 — 自动部署"
echo "  时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo "  Commit: ${COMMIT_SHA}"
echo "  分支: ${BRANCH}"
echo "============================================="

# ======== 配置 ========
PROJECT_NAME="activecode"
COMPOSE_DIR="/root/devtools/active-manager/activation-code-server"
APP_PORT=18080
CONTAINER_NAME="activecode"

# ======== 0. 环境准备 ========
echo ""
echo ">>> [0/4] 环境检查 <<<"

if [ ! -d /root/devtools ]; then
  echo "⚠️ 首次部署：/root/devtools 不存在，开始克隆..."
  git clone https://gitee.com/jonesAriven/devtools.git /root/devtools
fi

# 检查旧容器
if docker ps -a --filter "name=${CONTAINER_NAME}" --format "{{.Names}}" | grep -q "${CONTAINER_NAME}"; then
  echo "ℹ️ 发现旧容器:"
  docker ps -a --filter "name=${CONTAINER_NAME}" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
fi

# ======== 1. 同步代码 ========
echo ""
echo ">>> [1/4] 同步代码 <<<"
cd /root/devtools

REMOTE_URL=$(git remote get-url origin 2>/dev/null || echo "")
if [ "$REMOTE_URL" != "https://gitee.com/jonesAriven/devtools.git" ]; then
  git remote set-url origin https://gitee.com/jonesAriven/devtools.git
fi

git fetch origin "${BRANCH}"
git reset --hard "origin/${BRANCH}"
echo "✅ 代码已同步到 $(git rev-parse --short HEAD)"

# ======== 2. 停止并删除旧容器 ========
echo ""
echo ">>> [2/4] 停止旧服务 <<<"
cd "${COMPOSE_DIR}"

if docker ps -q --filter "name=${CONTAINER_NAME}" | grep -q .; then
  echo "--- 停止旧容器 ${CONTAINER_NAME} ---"
  # 使用 docker compose 停止（更优雅）
  docker compose -p "${PROJECT_NAME}" down 2>/dev/null || true
  sleep 5
  # 强制清理残留
  docker rm -f ${CONTAINER_NAME} 2>/dev/null || true
  echo "✅ 旧容器已删除"
else
  echo "ℹ️ 没有运行中的容器，跳过停止"
fi

# 清理旧镜像
docker image prune -f --filter "until=24h" 2>/dev/null || true

# ======== 3. 构建并启动新容器 ========
echo ""
echo ">>> [3/4] 构建并启动 <<<"

echo "--- jar 包信息 ---"
ls -lh target/*.jar 2>/dev/null || echo "⚠️ jar 包不存在，需要先编译"

# 使用 Docker Compose 构建 + 启动
echo "--- 构建镜像 ---"
docker compose -p "${PROJECT_NAME}" build --no-cache 2>&1 | tail -20

echo "--- 启动容器 ---"
docker compose -p "${PROJECT_NAME}" up -d --force-recreate 2>&1

sleep 10

echo "✅ 容器已启动"
docker ps --filter "name=${CONTAINER_NAME}" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

# ======== 4. 健康检查 ========
echo ""
echo ">>> [4/4] 健康检查 <<<"

MAX_RETRIES=10
for i in $(seq 1 $MAX_RETRIES); do
  if curl -sf http://localhost:${APP_PORT}/actuator/health > /dev/null 2>&1 || \
     curl -sf http://localhost:${APP_PORT}/ > /dev/null 2>&1; then
    echo "✅ 激活码服务健康! 端口: ${APP_PORT} (尝试 $i/$MAX_RETRIES)"
    break
  fi
  
  if [ $i -eq $MAX_RETRIES ]; then
    echo "❌ 健康检查失败! ($i/$MAX_RETRIES)"
    echo "--- 最近日志 ---"
    docker logs --tail=30 ${CONTAINER_NAME}
    exit 1
  fi
  
  echo "⏳ 等待服务启动... ($i/$MAX_RETRIES)"
  sleep 8
done

echo ""
echo "============================================="
echo "  🔑 active-manager 部署完成!"
echo "  Commit: $(cd /root/devtools && git rev-parse --short HEAD)"
echo "  时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo ""
echo "  📊 服务访问:"
echo "    激活码API: http://localhost:${APP_PORT}"
echo "============================================="
