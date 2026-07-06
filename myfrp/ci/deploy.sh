#!/bin/bash
# ============================================================
# myfrp (FRP管理面板) 部署脚本 — 在目标服务器上执行
# ============================================================
# 用法: bash deploy.sh <commit_sha> <branch>
# 示例: bash deploy.sh abc1234 dev
#
# ⚠️ 实际部署位置（基于全链路检查 2026-07-06）:
#
#   部署服务器: 内网Debian (192.168.31.182) [推测]
#   访问路径: tools.marschat.online → FRP:18082 → 内网Debian:18082
#
#   ⚠️ 注意: mykng 上未发现此服务（无容器、无端口监听）
#          推测部署在 内网Debian 或其他服务器
#
#   如果实际部署位置不同，请修改此脚本的目标服务器配置
#
# 部署信息:
#   项目名: frp-manager
#   端口: 18082 (宿主机和容器相同)
#   容器名: frp-manager
#   前端: Vue3 (可能内置在 JAR 中或独立部署)
# ============================================================

set -e

COMMIT_SHA="${1:-unknown}"
BRANCH="${2:-dev}"

echo "============================================="
echo "  🌐 myfrp FRP管理面板 — 自动部署"
echo "  时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo "  Commit: ${COMMIT_SHA}"
echo "  分支: ${BRANCH}"
echo "  📍 目标服务器: 待确认 (可能在内网Debian)"
echo "============================================="

# ======== 配置 ========
APP_DIR="/root/devtools/myfrp"
CONTAINER_NAME="frp-manager"
APP_PORT=18082

# ======== 0. 环境准备 ========
echo ""
echo ">>> [0/4] 环境检查 <<<"

if [ ! -d /root/devtools ]; then
  echo "⚠️ 首次部署：克隆仓库..."
  git clone https://gitee.com/jonesAriven/devtools.git /root/devtools
fi

# 检查旧容器
if docker ps -a --filter "name=${CONTAINER_NAME}" --format "{{.Names}}" | grep -q "${CONTAINER_NAME}"; then
  echo "ℹ️ 发现旧容器:"
  docker ps -a --filter "name=${CONTAINER_NAME}" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
  
  OLD_PORT=$(docker port ${CONTAINER_NAME} 2>/dev/null | head -1 | cut -d: -f2 || echo "未知")
  echo "ℹ️ 旧服务端口: ${OLD_PORT}"
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

# ======== 2. 检查前端 & 准备 ========
echo ""
echo ">>> [2/4] 部署准备 <<<"
cd "${APP_DIR}"

# 如果有前端项目，先构建前端
if [ -d "frontend" ] && [ -f "frontend/package.json" ]; then
  echo "--- 构建前端 ---"
  cd frontend
  
  if [ ! -d "node_modules" ]; then
    npm ci --registry=https://registry.npmmirror.com
  fi
  
  npm run build
  
  # 将前端构建产物复制到后端资源目录（如果需要）
  if [ -d "../src/main/resources/static" ] && [ -d "dist" ]; then
    rm -rf ../src/main/resources/static/*
    cp -r dist/* ../src/main/resources/static/
    echo "✅ 前端资源已复制到后端"
  fi
  
  cd ..
fi

# ======== 3. 停止旧服务 + 构建启动新服务 ========
echo ""
echo ">>> [3/4] 停止旧服务 & 启动新服务 <<<"

# 停止并删除旧容器
if docker ps -q --filter "name=${CONTAINER_NAME}" | grep -q .; then
  echo "--- 停止旧容器 ---"
  docker stop ${CONTAINER_NAME} || true
  sleep 3
  docker rm -f ${CONTAINER_NAME} || true
  echo "✅ 旧容器已删除"
else
  echo "ℹ️ 无运行中的容器"
fi

docker image prune -f --filter "until=24h" 2>/dev/null || true

# 构建镜像
echo "--- 构建 frp-manager 镜像 ---"
ls -lh target/*.jar 2>/dev/null || echo "⚠️ jar 包不存在"

docker compose build --no-cache 2>&1 | tail -15

# 启动容器
echo "--- 启动新容器 ---"
docker compose up -d --force-recreate 2>&1

sleep 8

echo "✅ 服务已启动"
docker ps --filter "name=${CONTAINER_NAME}" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

# ======== 4. 健康检查 ========
echo ""
echo ">>> [4/4] 健康检查 <<<"

MAX_RETRIES=8
for i in $(seq 1 $MAX_RETRIES); do
  if curl -sf http://localhost:${APP_PORT}/ > /dev/null 2>&1 || \
     curl -sf http://localhost:${APP_PORT}/actuator/health > /dev/null 2>&1; then
    echo "✅ FRP管理面板健康! 端口: ${APP_PORT} (尝试 $i/$MAX_RETRIES)"
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
echo "  🌐 myfrp FRP管理面板 部署完成!"
echo "  Commit: $(cd /root/devtools && git rev-parse --short HEAD)"
echo "  时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo ""
echo "  📊 服务访问:"
echo "    FRP面板: http://localhost:${APP_PORT}"
echo "    公网地址: https://tools.marschat.online/frp (通过FRP:18082)"
echo "============================================="
