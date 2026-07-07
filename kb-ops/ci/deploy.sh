#!/bin/bash
# ============================================================
# kb-ops (运维平台) 部署脚本 — 在 mykng 上执行
# ============================================================
# 用法: bash deploy.sh <commit_sha> <branch>
# 示例: bash deploy.sh abc1234 dev
#
# ⚠️ 实际部署架构（基于服务器检查 2026-07-06）:
#
#   ┌──────────────────────────────────────────────────┐
#   │  Nginx (:80)                                     │
#   │  /ops/          → /var/www/kb-ops-web (前端静态)│
#   │  /ops-api/      → :8084/kb-ops/ (后端API)       │
#   │  /ops/auth-api/ → :8090/kb/api/auth/ (认证)     │
#   └────────┬─────────────────────┬───────────────────┘
#            │                     │
#            ▼                     ▼
#   ┌────────────────┐  ┌────────────────────┐
#   │ 前端静态文件    │  │ kb-ops 后端        │
#   │ /var/www/      │  │ Docker容器          │
#   │ kb-ops-web     │  │ :8084               │
#   │ (Vue3构建产物) │  │ 镜像: kb-ops:1.0.0 │
#   └────────────────┘  └────────────────────┘
#
# 部署流程:
#   1. 构建前端 (kb-ops-web)
#   2. 部署前端到 /var/www/kb-ops-web/
#   3. Maven 构建后端
#   4. 停止旧 Docker 容器 + 构建新镜像 + 启动
#   5. 健康检查
# ============================================================

set -e

COMMIT_SHA="${1:-unknown}"
BRANCH="${2:-dev}"

echo "============================================="
echo "  ⚙️ kb-ops 运维平台 — 自动部署"
echo "  时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo "  Commit: ${COMMIT_SHA}"
echo "  分支: ${BRANCH}"
echo "============================================="

# ======== 配置（基于实际服务器配置）========
APP_DIR="/root/devtools/kb-ops"
BACKEND_DIR="${APP_DIR}"              # 后端根目录（pom.xml在此）
FRONTEND_DIR="${APP_DIR}/kb-ops-web"  # 前端目录
CONTAINER_NAME="kb-ops"
APP_PORT=8084                         # 后端端口
WEB_DEPLOY_DIR="/var/www/kb-ops-web"  # 前端部署目录

# ======== 0. 环境准备 ========
echo ""
echo ">>> [0/5] 环境检查 <<<"

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

# 检查旧前端部署
if [ -d "${WEB_DEPLOY_DIR}" ]; then
  echo "ℹ️ 发现旧前端部署:"
  ls -la "${WEB_DEPLOY_DIR}/" | head -5
fi

# ======== 1. 同步代码 ========
echo ""
echo ">>> [1/5] 同步代码 <<<"
cd /root/devtools

REMOTE_URL=$(git remote get-url origin 2>/dev/null || echo "")
if [ "$REMOTE_URL" != "https://gitee.com/jonesAriven/devtools.git" ]; then
  git remote set-url origin https://gitee.com/jonesAriven/devtools.git
fi

git fetch origin "${BRANCH}"
git reset --hard "origin/${BRANCH}"
echo "✅ 代码已同步到 $(git rev-parse --short HEAD)"

# ======== 2. 构建前端 ========
echo ""
echo ">>> [2/5] 构建前端 <<<"

if [ -d "${FRONTEND_DIR}" ] && [ -f "${FRONTEND_DIR}/package.json" ]; then
  cd "${FRONTEND_DIR}"
  
  echo "--- 安装前端依赖 ---"
  if [ ! -d "node_modules" ]; then
    if [ -f "pnpm-lock.yaml" ]; then
      pnpm install --registry=https://registry.npmmirror.com
    else
      npm ci --registry=https://registry.npmmirror.com
    fi
  fi
  
  echo "--- 构建前端 ---"
  if [ -f "pnpm-lock.yaml" ]; then
    pnpm build
  else
    npm run build
  fi
  
  if [ ! -d "dist" ]; then
    echo "❌ 前端构建失败：dist 目录不存在"
    exit 1
  fi
  
  # 备份旧前端
  if [ -d "${WEB_DEPLOY_DIR}" ]; then
    echo "--- 备份旧前端 ---"
    mv "${WEB_DEPLOY_DIR}" "${WEB_DEPLOY_DIR}.bak.$(date +%s)" 2>/dev/null || true
  fi
  
  # 部署新前端
  echo "--- 部署前端到 ${WEB_DEPLOY_DIR} ---"
  mkdir -p "${WEB_DEPLOY_DIR}"
  cp -r dist/* "${WEB_DEPLOY_DIR}/"
  chmod -R 755 "${WEB_DEPLOY_DIR}"
  
  echo "✅ 前端已部署 (${WEB_DEPLOY_DIR})"
  
  cd "${APP_DIR}"
else
  echo "⚠️ 无前端项目或无需构建"
fi

# ======== 3. 检查并准备 Docker 配置 ========
echo ""
echo ">>> [3/5] Docker 配置准备 <<<"
cd "${APP_DIR}"

# 如果没有 docker-compose.yml，创建一个（基于实际配置）
if [ ! -f "docker-compose.yml" ]; then
  echo "⚠️ 首次部署：创建 docker-compose.yml..."
  cat > docker-compose.yml << EOF
version: '3.8'

services:
  kb-ops:
    build:
      context: .
      dockerfile: Dockerfile
    image: kb-ops:1.0.0
    container_name: kb-ops
    restart: unless-stopped
    ports:
      - "${APP_PORT}:${APP_PORT}"
    environment:
      - TZ=Asia/Shanghai
      - SPRING_PROFILES_ACTIVE=prod
      # 连接到 mykng 的 kb-mysql 容器（同一 Docker 网络）
      - SPRING_DATASOURCE_URL=jdbc:mysql://kb-mysql:3306/kb_ops?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
      - SPRING_DATASOURCE_USERNAME=root
      - SPRING_DATASOURCE_PASSWORD=kb123456
    networks:
      - kb-net  # 复用 mykng 的网络，可访问 kb-mysql
    extra_hosts:
      - "host.docker.internal:host-gateway"

networks:
  kb-net:
    external: true  # 使用 mykng 已有的 kb-net 网络
EOF
  echo "✅ docker-compose.yml 已创建（使用外部 kb-net 网络）"
else
  echo "✅ 使用现有 docker-compose.yml"
fi

# ======== 4. 停止旧服务 + 构建启动新服务（防止孤儿容器） ========
echo ""
echo ">>> [4/5] 停止旧服务 & 启动新服务 <<<"

# 检查所有状态的旧容器（运行中 + 已停止 + 僵尸）→ 防止孤儿容器
if docker ps -a --filter "name=${CONTAINER_NAME}" --format "{{.Names}}" | grep -q "${CONTAINER_NAME}"; then
  echo "--- 发现旧容器（任何状态），执行清理 ---"
  docker ps -a --filter "name=${CONTAINER_NAME}" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
  
  # 使用 docker compose 停止并删除（处理所有状态）
  docker compose -p "kb-ops" down --remove-orphans 2>/dev/null || true
  sleep 3
  
  # 强制清理残留（防止 compose down 没删干净，尤其是 Exited/Dead 状态）
  docker rm -f ${CONTAINER_NAME} 2>/dev/null || true
  
  # 确保端口释放
  if ss -tlnp | grep -q ":${APP_PORT} "; then
    echo "--- 等待端口 ${APP_PORT} 释放 ---"
    sleep 2
  fi
  
  echo "✅ 旧容器已清理（包括已停止的僵尸容器）"
else
  echo "ℹ️ 无旧容器（干净环境）"
  
  # 如果端口被其他进程占用（非 Docker 容器占用的情况）
  if ss -tlnp | grep -q ":${APP_PORT} "; then
    echo "⚠️ 端口 ${APP_PORT} 被非 Docker 进程占用，尝试清理..."
    fuser -k ${APP_PORT}/tcp 2>/dev/null || true
    sleep 2
  fi
fi

# 清理旧镜像
docker image prune -f --filter "until=24h" 2>/dev/null || true

# 构建新镜像
echo "--- 构建 kb-ops 镜像 ---"
ls -lh target/*.jar 2>/dev/null || echo "⚠️ jar 包不存在，将在Docker构建时编译"

docker compose -p "kb-ops" build --no-cache 2>&1 | tail -15

# 启动新容器
echo "--- 启动新容器 ---"
docker compose -p "kb-ops" up -d --force-recreate 2>&1

sleep 8

echo "✅ 服务已启动"
docker ps --filter "name=${CONTAINER_NAME}" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

# ======== 5. 健康检查 ========
echo ""
echo ">>> [5/5] 健康检查 <<<"

MAX_RETRIES=8
for i in $(seq 1 $MAX_RETRIES); do
  if curl -sf http://localhost:${APP_PORT}/kb-ops/actuator/health > /dev/null 2>&1 || \
     curl -sf http://localhost:${APP_PORT}/ > /dev/null 2>&1; then
    echo "✅ kb-ops 服务健康! 端口: ${APP_PORT} (尝试 $i/$MAX_RETRIES)"
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
echo "  ⚙️ kb-ops 运维平台 部署完成!"
echo "  Commit: $(cd /root/devtools && git rev-parse --short HEAD)"
echo "  时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo ""
echo "  📊 服务访问:"
echo "    前端页面: https://kb.marschat.online/ops/"
echo "    后端API:  http://localhost:${APP_PORT}/kb-ops/"
echo "    Nginx路由: /ops-api/ → :${APP_PORT}/kb-ops/"
echo ""
echo "  📁 部署位置:"
echo "    后端容器: ${CONTAINER_NAME}:${APP_PORT}"
echo "    前端目录: ${WEB_DEPLOY_DIR}"
echo "============================================="
