#!/bin/bash
# ============================================================
# infra-monitor (基础设施监控) 部署脚本 — 在 mykng 上执行
# ============================================================
# 用法: bash deploy.sh <commit_sha> <branch>
# 示例: bash deploy.sh abc1234 dev
#
# ⚠️ 实际部署架构（基于服务器检查 2026-07-06）:
#
#   ┌─────────────────────────────────────────────────┐
#   │  Nginx (:80)                                    │
#   │  /infra/api/     → 127.0.0.1:8088 (后端API)    │
#   │  /infra/         → /data/infra-monitor-web (前端)│
#   │  /infra/assets/  → /data/infra-monitor-web/assets│
#   └────────────┬────────────────┬───────────────────┘
#                │                │
#                ▼                ▼
#   ┌─────────────────┐  ┌────────────────────┐
#   │ infra-monitor   │  │ 前端静态文件        │
#   │ :8088 (host网络) │  │ /data/infra-monitor-web│
#   │ Docker容器       │  │ (Vue3构建产物)      │
#   │ JAR: /data/      │  │                    │
#   │ infra-monitor/   │  │                    │
#   │ app.jar          │  │                    │
#   └─────────────────┘  └────────────────────┘
#
# 部署流程:
#   1. 构建前端 (infra-monitor-web)
#   2. Maven 构建后端 (infra-monitor-server)
#   3. 部署前端到 /data/infra-monitor-web/
#   4. 停止旧容器 + 复制新JAR到 /data/infra-monitor/
#   5. 启动新容器 (host网络, 端口8088)
#   6. 健康检查
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

# ======== 配置（基于实际服务器配置）========
APP_DIR="/root/devtools/infra-monitor"
BACKEND_DIR="${APP_DIR}/infra-monitor-server"
FRONTEND_DIR="${APP_DIR}/infra-monitor-web"
CONTAINER_NAME="infra-monitor"
DATA_DIR="/data/infra-monitor"           # 后端JAR目录
WEB_DIR="/data/infra-monitor-web"         # 前端静态文件目录
APP_PORT=8088                             # 后端端口（host网络模式）
JAR_NAME="app.jar"

# ======== 0. 环境准备 ========
echo ""
echo ">>> [0/6] 环境检查 <<<"

if [ ! -d /root/devtools ]; then
  echo "⚠️ 首次部署：克隆仓库..."
  git clone https://gitee.com/jonesAriven/devtools.git /root/devtools
fi

# 检查旧容器
if docker ps -a --filter "name=${CONTAINER_NAME}" --format "{{.Names}}" | grep -q "${CONTAINER_NAME}"; then
  echo "ℹ️ 发现旧容器:"
  docker ps -a --filter "name=${CONTAINER_NAME}" --format "table {{.Names}}\t{{.Status}}\t{{.Networks}}"
fi

# 确保 data 目录存在
mkdir -p "${DATA_DIR}"
mkdir -p "${WEB_DIR}"

# ======== 1. 同步代码 ========
echo ""
echo ">>> [1/6] 同步代码 <<<"
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
echo ">>> [2/6] 构建前端 <<<"

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
  if [ -d "${WEB_DIR}" ]; then
    echo "--- 备份旧前端 ---"
    mv "${WEB_DIR}" "${WEB_DIR}.bak.$(date +%s)" 2>/dev/null || true
  fi
  
  # 部署新前端
  echo "--- 部署前端到 ${WEB_DIR} ---"
  mkdir -p "${WEB_DIR}"
  cp -r dist/* "${WEB_DIR}/"
  chmod -R 755 "${WEB_DIR}"
  
  echo "✅ 前端已部署 (${WEB_DIR})"
  ls -la "${WEB_DIR}/" | head -5
  
  cd "${APP_DIR}"
else
  echo "⚠️ 无前端项目，跳过构建"
fi

# ======== 3. Maven 构建后端 JAR ========
echo ""
echo ">>> [3/6] Maven 构建后端 <<<"
cd "${BACKEND_DIR}"

echo "--- 检查 Maven ---"
if command -v mvn &> /dev/null; then
  echo "--- 本地 Maven 构建 ---"
  mvn clean package -DskipTests -q
else
  echo "❌ Maven 未安装！请安装 Maven 或使用 Docker 构建"
  exit 1
fi

# 查找生成的 JAR
JAR_FILE=$(find target -name "*.jar" ! -name "*sources.jar" ! -name "*javadoc.jar" | head -1)

if [ -z "$JAR_FILE" ]; then
  echo "❌ 未找到构建产物 JAR 文件"
  exit 1
fi

echo "✅ 后端构建完成: ${JAR_FILE}"
ls -lh "${JAR_FILE}"

# ======== 4. 停止旧服务 + 部署新 JAR（防止孤儿容器和端口冲突） ========
echo ""
echo ">>> [4/6] 停止旧服务 & 部署新版本 <<<"

# 检查所有状态的旧容器（运行中 + 已停止 + 僵尸）
# ⚠️ 必须检查所有状态！因为后面 docker run --name 遇到同名容器会报错冲突
if docker ps -a --filter "name=${CONTAINER_NAME}" --format "{{.Names}}" | grep -q "${CONTAINER_NAME}"; then
  echo "--- 发现旧容器（任何状态），执行清理 ---"
  docker ps -a --filter "name=${CONTAINER_NAME}" --format "table {{.Names}}\t{{.Status}}\t{{.Networks}}"
  
  # 先尝试优雅停止
  docker stop ${CONTAINER_NAME} 2>/dev/null || true
  sleep 3
  
  # 强制删除（不管当前状态：Running/Exited/Dead）
  docker rm -f ${CONTAINER_NAME} 2>/dev/null || true
  echo "✅ 旧容器已删除"
else
  echo "ℹ️ 无旧容器（干净环境）"
fi

# 二次确认：如果容器删了但端口还被占用（可能是残留进程或非 Docker 进程）
if ss -tlnp | grep -q ":${APP_PORT} "; then
  echo "⚠️ 端口 ${APP_PORT} 仍被占用，尝试清理残留进程..."
  fuser -k ${APP_PORT}/tcp 2>/dev/null || true
  sleep 3
  
  # 最终确认
  if ss -tlnp | grep -q ":${APP_PORT} "; then
    echo "❌ 端口 ${APP_PORT} 无法释放！请手动排查:"
    ss -tlnp | grep ":${APP_PORT} "
    exit 1
  fi
  echo "✅ 端口已释放"
fi

# 备份旧 JAR
if [ -f "${DATA_DIR}/${JAR_NAME}" ]; then
  echo "--- 备份旧 JAR ---"
  cp "${DATA_DIR}/${JAR_NAME}" "${DATA_DIR}/${JAR_NAME}.bak.$(date +%s)" 2>/dev/null || true
fi

# 复制新 JAR
echo "--- 部署新 JAR 到 ${DATA_DIR} ---"
cp "${JAR_FILE}" "${DATA_DIR}/${JAR_NAME}"
chmod 644 "${DATA_DIR}/${JAR_NAME}"
echo "✅ 新版本已部署"

# 清理旧镜像
docker image prune -f --filter "until=24h" 2>/dev/null || true

# ======== 5. 启动新容器（host网络模式）=====
echo ""
echo ">>> [5/6] 启动新容器 <<<"

echo "--- 使用 host 网络模式启动 infra-monitor (端口 ${APP_PORT}) ---"
docker run -d \
  --name ${CONTAINER_NAME} \
  --restart unless-stopped \
  --network host \
  -v "${DATA_DIR}:/app" \
  -e TZ=Asia/Shanghai \
  -e SPRING_PROFILES_ACTIVE=prod \
  --memory="256m" \
  eclipse-temurin:21-jre \
  java -jar /app/app.jar --spring.profiles.active=prod

sleep 8

echo "✅ 容器已启动"
docker ps --filter "name=${CONTAINER_NAME}" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

# 验证端口是否监听
if ss -tlnp | grep -q ":${APP_PORT} "; then
  echo "✅ 端口 ${APP_PORT} 已监听"
else
  echo "⚠️ 端口 ${APP_PORT} 未监听，等待中..."
fi

# ======== 6. 健康检查 ========
echo ""
echo ">>> [6/6] 健康检查 <<<"

MAX_RETRIES=10
for i in $(seq 1 $MAX_RETRIES); do
  # 检查容器状态
  if ! docker ps --filter "name=${CONTAINER_NAME}" --format "{{.Status}}" | grep -q "Up"; then
    echo "❌ 容器未运行! ($i/$MAX_RETRIES)"
    echo "--- 最近日志 ---"
    docker logs --tail=30 ${CONTAINER_NAME}
    exit 1
  fi
  
  # 检查端口和 HTTP 响应
  if curl -sf http://localhost:${APP_PORT}/infra/actuator/health > /dev/null 2>&1 || \
     curl -sf http://localhost:${APP_PORT}/ > /dev/null 2>&1; then
    echo "✅ Infra Monitor 服务健康! 端口: ${APP_PORT} (尝试 $i/$MAX_RETRIES)"
    break
  fi
  
  if [ $i -eq $MAX_RETRIES ]; then
    echo "⚠️ 健康检查超时，但容器正在运行 ($i/$MAX_RETRIES)"
    echo "--- 最近日志 ---"
    docker logs --tail=30 ${CONTAINER_NAME}
    echo ""
    echo "⚠️ 请手动访问 http://localhost:${APP_PORT}/infra/ 验证"
    break
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
echo "  📊 服务信息:"
echo "    后端API: http://localhost:${APP_PORT}/infra/"
echo "    前端页面: https://kb.marschat.online/infra/"
echo "    Nginx路由: /infra/api/ → :${APP_PORT}/infra/"
echo "    数据目录: ${DATA_DIR}"
echo "    前端目录: ${WEB_DIR}"
echo "    日志查看: docker logs -f ${CONTAINER_NAME}"
echo "============================================="
