#!/bin/bash
# ============================================================
# kb-ops (运维平台) 部署脚本 — 在目标服务器上执行
# ============================================================
# 用法: bash deploy.sh <commit_sha> <branch> <deploy_target>
# 示例: bash deploy.sh abc1234 dev production
#
# 部署信息（基于实际服务器检查 2026-07-06）:
#   项目名: kb-ops (运维平台)
#   端口: 8084(宿主机) → 8084(容器)
#   Docker Compose Project: kb-ops
#   容器名: kb-ops
#   镜像: kb-ops:1.0.0 (当前运行版本)
#   数据库: 使用 kb-mysql (kb_ops 库)
#
# 注意: 此脚本会自动检测是否首次部署，
#       首次部署时自动创建 docker-compose.yml
# ============================================================

set -e

COMMIT_SHA="${1:-unknown}"
BRANCH="${2:-dev}"
DEPLOY_TARGET="${3:-production}"

echo "============================================="
echo "  ⚙️ kb-ops 运维平台 — 自动部署"
echo "  时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo "  Commit: ${COMMIT_SHA}"
echo "  分支: ${BRANCH}"
echo "  目标: ${DEPLOY_TARGET}"
echo "============================================="

# ======== 配置 ========
PROJECT_NAME="kb-ops"
APP_DIR="/root/devtools/kb-ops"
CONTAINER_NAME="kb-ops"
APP_PORT=8084

# ======== 0. 环境准备 ========
echo ""
echo ">>> [0/4] 环境检查 <<<"

if [ ! -d /root/devtools ]; then
  echo "⚠️ 首次部署：克隆仓库..."
  git clone https://gitee.com/jonesAriven/devtools.git /root/devtools
fi

# 检查旧容器/服务
if docker ps -a --filter "name=${CONTAINER_NAME}" --format "{{.Names}}" | grep -q "${CONTAINER_NAME}"; then
  echo "ℹ️ 发现旧容器:"
  docker ps -a --filter "name=${CONTAINER_NAME}" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
  
  # 显示旧服务的端口占用
  OLD_PORT=$(docker port ${CONTAINER_NAME} 2>/dev/null | cut -d: -f2 || echo "未知")
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

# ======== 2. 首次部署检查 & 准备 ========
echo ""
echo ">>> [2/4] 部署准备 <<<"
cd "${APP_DIR}"

# 检查是否有 docker-compose.yml，没有则创建（基于实际配置）
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
      - "8084:8084"
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

# ======== 3. 停止旧服务 + 构建启动新服务 ========
echo ""
echo ">>> [3/4] 停止旧服务 & 启动新服务 <<<"

# 停止并删除旧容器
if docker ps -q --filter "name=${CONTAINER_NAME}" | grep -q .; then
  echo "--- 停止旧容器 ---"
  docker compose -p "${PROJECT_NAME}" down 2>/dev/null || true
  sleep 3
  docker rm -f ${CONTAINER_NAME} 2>/dev/null || true
  echo "✅ 旧容器已删除"
else
  echo "ℹ️ 无运行中的容器"
fi

# 清理旧镜像
docker image prune -f --filter "until=24h" 2>/dev/null || true

# 构建新镜像
echo "--- 构建 kb-ops 镜像 ---"
ls -lh target/*.jar 2>/dev/null || echo "⚠️ jar 包不存在"

docker compose -p "${PROJECT_NAME}" build --no-cache 2>&1 | tail -15

# 启动新容器
echo "--- 启动新容器 ---"
docker compose -p "${PROJECT_NAME}" up -d --force-recreate 2>&1

sleep 8

echo "✅ 服务已启动"
docker ps --filter "name=${CONTAINER_NAME}" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

# ======== 4. 健康检查 ========
echo ""
echo ">>> [4/4] 健康检查 <<<"

MAX_RETRIES=8
for i in $(seq 1 $MAX_RETRIES); do
  if curl -sf http://localhost:${APP_PORT}/actuator/health > /dev/null 2>&1 || \
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
echo "  ⚙️ kb-ops 部署完成!"
echo "  Commit: $(cd /root/devtools && git rev-parse --short HEAD)"
echo "  时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo ""
echo "  📊 服务访问:"
echo "    kb-ops: http://localhost:${APP_PORT}"
echo "============================================="
