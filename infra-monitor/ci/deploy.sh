#!/bin/bash
# ============================================================
# infra-monitor (基础设施监控) 部署脚本 — 在目标服务器上执行
# ============================================================
# 用法: bash deploy.sh <commit_sha> <branch>
# 示例: bash deploy.sh abc1234 dev
#
# 部署信息:
#   后端: infra-monitor-server (Java/Spring Boot)
#   前端: infra-monitor-web (Vue3 + Vite)
#   端口: 待确认（默认 8085 或由配置决定）
#
# 特点:
#   - 前后端分离部署
#   - 前端构建产物可嵌入后端或独立 Nginx 部署
# ============================================================

set -e

COMMIT_SHA="${1:-unknown}"
BRANCH="${2:-dev}"

echo "============================================="
echo "  📊 infra-monitor 基础设施监控 — 自动部署"
echo "  时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo "  Commit: ${COMMIT_SHA}"
echo "  分支: ${BRANCH}"
echo "============================================="

# ======== 配置 ========
APP_DIR="/root/devtools/infra-monitor"
BACKEND_DIR="${APP_DIR}/infra-monitor-server"
FRONTEND_DIR="${APP_DIR}/infra-monitor-web"
CONTAINER_NAME="infra-monitor"
APP_PORT=8085  # 默认端口，可根据实际修改

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

# ======== 2. 构建前端（如果有） ========
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
  
  # 将前端构建产物复制到后端静态资源目录
  if [ -d "dist" ] && [ -d "${BACKEND_DIR}/src/main/resources/static" ]; then
    echo "--- 复制前端资源到后端 ---"
    rm -rf "${BACKEND_DIR}/src/main/resources/static/"*
    cp -r dist/* "${BACKEND_DIR}/src/main/resources/static/"
    echo "✅ 前端已集成到后端 JAR"
  elif [ -d "dist" ]; then
    echo "ℹ️ 前端构建完成，但未找到后端静态资源目录"
    echo "   前端将独立部署"
  fi
  
  cd "${APP_DIR}"
else
  echo "ℹ️ 无前端项目或无需构建"
fi

# ======== 3. 检查并准备 Docker 配置 ========
echo ""
echo ">>> [3/5] Docker 配置准备 <<<"
cd "${BACKEND_DIR}"

# 如果没有 Dockerfile，创建一个
if [ ! -f "Dockerfile" ]; then
  echo "⚠️ 创建 Dockerfile..."
  cat > Dockerfile << 'EOF'
FROM eclipse-temurin:21-jre-alpine

LABEL maintainer="kb-team"

WORKDIR /app

# 复制构建产物
COPY target/infra-monitor.jar /app/infra-monitor.jar

# 时区设置
RUN apk add --no-cache tzdata && \
    cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo "Asia/Shanghai" > /etc/timezone && \
    apk del tzdata

EXPOSE 8085

ENV JAVA_OPTS="-Xms128m -Xmx256m -XX:+UseG1GC -Dfile.encoding=UTF-8"
ENV SPRING_PROFILES_ACTIVE=prod

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/infra-monitor.jar"]
EOF
  echo "✅ Dockerfile 已创建"
fi

# 如果没有 docker-compose.yml，创建一个
if [ ! -f "docker-compose.yml" ]; then
  echo "⚠️ 创建 docker-compose.yml..."
  cat > docker-compose.yml << EOF
version: '3.8'

services:
  infra-monitor:
    build: .
    image: infra-monitor:latest
    container_name: infra-monitor
    restart: unless-stopped
    ports:
      - "${APP_PORT}:8085"
    environment:
      - TZ=Asia/Shanghai
      - SPRING_PROFILES_ACTIVE=prod
    networks:
      - infra-net

networks:
  infra-net:
    driver: bridge
EOF
  echo "✅ docker-compose.yml 已创建"
fi

# ======== 4. 停止旧服务 + 构建启动新服务 ========
echo ""
echo ">>> [4/5] 停止旧服务 & 启动新服务 <<<"

# 停止旧容器
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

# 构建
echo "--- 构建 infra-monitor 镜像 ---"
ls -lh target/*.jar 2>/dev/null || echo "⚠️ jar 包不存在"

docker compose build --no-cache 2>&1 | tail -15

# 启动
echo "--- 启动新容器 ---"
docker compose up -d --force-recreate 2>&1

sleep 8

echo "✅ 服务已启动"
docker ps --filter "name=${CONTAINER_NAME}" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

# ======== 5. 健康检查 ========
echo ""
echo ">>> [5/5] 健康检查 <<<"

MAX_RETRIES=8
for i in $(seq 1 $MAX_RETRIES); do
  if curl -sf http://localhost:${APP_PORT}/actuator/health > /dev/null 2>&1 || \
     curl -sf http://localhost:${APP_PORT}/ > /dev/null 2>&1; then
    echo "✅ Infra Monitor 服务健康! 端口: ${APP_PORT} (尝试 $i/$MAX_RETRIES)"
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
echo "  📊 infra-monitor 基础设施监控 部署完成!"
echo "  Commit: $(cd /root/devtools && git rev-parse --short HEAD)"
echo "  时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo ""
echo "  📊 服务访问:"
echo "    监控面板: http://localhost:${APP_PORT}"
echo "============================================="
