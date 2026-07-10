#!/bin/bash
# ============================================================
# infra-monitor (基础设施监控) - 部署脚本（在 mykng 上执行）
# ============================================================
# 用法: bash deploy-infra-monitor.sh <tar.gz文件名>
# 示例: bash deploy-infra-monitor.sh infra-monitor-latest.tar.gz
#
# 流程:
#   [0] 前置检查（产物存在）
#   [1] 从 /mnt/shared/devtools 取 tar.gz 包，解压 jar
#   [2] 校验 Dockerfile 和 docker-compose.yml
#   [3] 构建前端（如需）并部署到 /data/infra-monitor-web/
#   [4] docker compose down --remove-orphans (停旧服务)
#   [5] docker compose up -d --build --force-recreate (建新服务)
#   [6] 健康检查 (:8088/infra/actuator/health)
#   [7] 清理旧镜像
#
# 访问地址:
#   本地:  http://localhost:8088/infra/
#   公网:  https://monitor.marschat.online/infra/
#         (通过 Nginx 反代 /infra/api/ -> :8088/infra/)
# ============================================================

set -euo pipefail

# ======================== 心跳函数 ========================
# 防止 SSH 长时间无输出导致 FRP 隧道断连
heartbeat() {
  local msg="${1:-操作进行中}"
  while true; do
    echo "  ⏳ $(date '+%H:%M:%S') ${msg}..."
    sleep 15
  done
}

# 日志记录
LOG_FILE="/var/log/infra-monitor-deploy-$(date +%Y%m%d-%H%M%S).log"
echo "  日志: $LOG_FILE"

echo "============================================="
echo "  📊 infra-monitor 基础设施监控 - 自动部署"
echo "  时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo "  日志: $LOG_FILE"
echo "============================================="

# ======================== 参数 ========================
TAR_FILE="${1:?❌ 缺少参数! 用法: $0 <tar.gz文件名>}"

# ======================== 配置 ========================
SHARED_DIR="/mnt/shared/devtools/publish"
REPO_DIR="/root/devtools/infra-monitor"
BACKEND_DIR="${REPO_DIR}/infra-monitor-server"
FRONTEND_DIR="${REPO_DIR}/infra-monitor-web"
COMPOSE_PROJECT="infra-monitor"
CONTAINER_NAME="infra-monitor"
APP_PORT=8088
WEB_DIR="/data/infra-monitor-web"
HEALTH_MAX_RETRIES=24
HEALTH_INTERVAL=10

echo ""
echo "  产物: ${TAR_FILE}"
echo "============================================="

# ======================== [0] 前置检查 ========================
echo ""
echo ">>> [0/7] 前置检查 <<<"

TAR_PATH="${SHARED_DIR}/${TAR_FILE}"

if [ ! -f "${TAR_PATH}" ]; then
  echo "❌ 产物不存在: ${TAR_PATH}"
  echo "   请确认 Woodpecker Build 步骤已成功将产物推送到共享目录"
  exit 1
fi
FILE_SIZE=$(ls -lh "${TAR_PATH}" | awk '{print $5}')
echo "✅ 找到产物: ${TAR_PATH} (${FILE_SIZE})"

# 检查端口占用（仅提示，旧服务在步骤 [4] 停止）
if ss -tlnp | grep -q ":${APP_PORT} "; then
  echo "ℹ️ 端口 ${APP_PORT} 已被占用（旧服务运行中，将在步骤 [4] 停止）:"
  ss -tlnp | grep ":${APP_PORT} "
else
  echo "✅ 端口 ${APP_PORT} 可用（首次部署）"
fi

# ======================== [1] 同步代码 & 解压产物 ========================
echo ""
echo ">>> [1/7] 同步代码 & 解压产物 <<<"

# 同步代码仓库（获取 Dockerfile、docker-compose.yml、前端源码）
if [ -d "/root/devtools/.git" ]; then
  cd /root/devtools
  git fetch origin dev 2>/dev/null || true
  git reset --hard origin/dev 2>/dev/null || true
  echo "✅ 代码已同步到 $(git rev-parse --short HEAD)"
else
  echo "⚠️ /root/devtools 不是 git 仓库，跳过代码同步"
fi

# 解压 jar 包
mkdir -p "${BACKEND_DIR}/target"
rm -f "${BACKEND_DIR}/target/"*.jar 2>/dev/null || true
tar xzf "${TAR_PATH}" -C "${BACKEND_DIR}/target/"
JAR_COUNT=$(ls "${BACKEND_DIR}/target/"*.jar 2>/dev/null | wc -l)
echo "✅ 解压完成: ${JAR_COUNT} 个 jar 文件"
ls -lh "${BACKEND_DIR}/target/"

if [ "${JAR_COUNT}" -eq 0 ]; then
  echo "❌ tar 包中没有 jar 文件!"
  exit 1
fi

# ======================== [2] 校验部署环境 ========================
echo ""
echo ">>> [2/7] 校验部署环境 <<<"

for required_file in "${BACKEND_DIR}/Dockerfile" "${BACKEND_DIR}/docker-compose.yml"; do
  if [ -f "${required_file}" ]; then
    echo "  ✅ ${required_file}"
  else
    echo "❌ 关键文件不存在: ${required_file}"
    exit 1
  fi
done

if ! command -v docker &>/dev/null; then
  echo "❌ Docker 未安装或不在 PATH 中"
  exit 1
fi
echo "  ✅ Docker: $(docker --version)"

# ======================== [3] 构建并部署前端 ========================
echo ""
echo ">>> [3/7] 构建并部署前端 <<<"

if [ -d "${FRONTEND_DIR}" ] && [ -f "${FRONTEND_DIR}/package.json" ]; then
  cd "${FRONTEND_DIR}"

  echo "--- 安装前端依赖 ---"
  if [ -f "pnpm-lock.yaml" ]; then
    pnpm install --registry=https://registry.npmmirror.com 2>&1 | tail -5
  else
    npm ci --registry=https://registry.npmmirror.com 2>&1 | tail -5
  fi

  echo "--- 构建前端 ---"
  if [ -f "pnpm-lock.yaml" ]; then
    pnpm build 2>&1 | tail -10
  else
    npm run build 2>&1 | tail -10
  fi

  if [ ! -d "dist" ]; then
    echo "❌ 前端构建失败：dist 目录不存在"
    exit 1
  fi

  # 备份旧前端
  if [ -d "${WEB_DIR}" ] && [ "$(ls -A ${WEB_DIR} 2>/dev/null)" ]; then
    echo "--- 备份旧前端 ---"
    mv "${WEB_DIR}" "${WEB_DIR}.bak.$(date +%s)" 2>/dev/null || true
  fi

  # 部署新前端
  echo "--- 部署前端到 ${WEB_DIR} ---"
  mkdir -p "${WEB_DIR}"
  cp -r dist/* "${WEB_DIR}/"
  chmod -R 755 "${WEB_DIR}"
  echo "✅ 前端已部署"
  ls -la "${WEB_DIR}/" | head -5

  cd "${REPO_DIR}"
else
  echo "⚠️ 无前端项目，跳过构建"
fi

# ======================== [4] 停止旧服务（防孤儿）========================
echo ""
echo ">>> [4/7] 停止旧服务 (防孤儿) <<<"

cd "${BACKEND_DIR}"

# ---- 4a. 主路径：docker compose down ----
if docker compose -p "${COMPOSE_PROJECT}" ps -q 2>/dev/null | grep -q .; then
  echo "--- docker compose -p ${COMPOSE_PROJECT} down --remove-orphans ---"
  heartbeat "docker compose down 停止容器" &
  HB_PID=$!
  docker compose -p "${COMPOSE_PROJECT}" down --remove-orphans --timeout 30 2>&1 || true
  kill ${HB_PID} 2>/dev/null; wait ${HB_PID} 2>/dev/null
  echo "✅ compose down 完成"
  sleep 3
else
  echo "ℹ️ 无正在运行的 compose project (首次部署或已清理)"
fi

# ---- 4b. 兜底：强制清除残留容器 ----
echo "--- 兜底扫描残留容器 ---"
if docker ps -a --filter "name=${CONTAINER_NAME}" -q 2>/dev/null | grep -q .; then
  echo "  ⚠️ 发现残留容器: ${CONTAINER_NAME}，强制删除"
  docker rm -f $(docker ps -aq --filter "name=${CONTAINER_NAME}") 2>/dev/null || true
else
  echo "  ✅ 无残留容器，环境干净"
fi

# 等待端口释放
sleep 2
if ss -tlnp | grep -q ":${APP_PORT} "; then
  echo "  ⚠️ 端口 ${APP_PORT} 仍被占用，等待释放..."
  sleep 5
  if ss -tlnp | grep -q ":${APP_PORT} "; then
    echo "  ⚠️ 端口被非 Docker 进程占用，尝试释放..."
    fuser -k ${APP_PORT}/tcp 2>/dev/null || true
    sleep 2
  fi
fi

# ======================== [5] 构建并启动新服务 ========================
echo ""
echo ">>> [5/7] 构建并启动新服务 <<<"
echo "--- Compose Project: ${COMPOSE_PROJECT} ---"

# 不使用 --build：jar 通过 volume 挂载，force-recreate 即可生效
heartbeat "docker compose up 启动服务" &
HB_PID=$!
docker compose -p "${COMPOSE_PROJECT}" \
  up -d --force-recreate --remove-orphans 2>&1
kill ${HB_PID} 2>/dev/null; wait ${HB_PID} 2>/dev/null

echo ""
echo "✅ 服务启动命令执行完成"
echo ""
echo "--- 当前容器状态 ---"
docker compose -p "${COMPOSE_PROJECT}" ps 2>/dev/null || \
  docker ps --filter "name=${CONTAINER_NAME}" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

# ======================== [6] 健康检查 ========================
echo ""
echo ">>> [6/7] 健康检查 (最多等待 $(( HEALTH_MAX_RETRIES * HEALTH_INTERVAL / 60 )) 分钟) <<<"

for i in $(seq 1 ${HEALTH_MAX_RETRIES}); do
  NOW=$(date '+%H:%M:%S')

  # 检查容器状态
  if ! docker ps --filter "name=${CONTAINER_NAME}" --filter "status=running" | grep -q .; then
    echo "❌ 容器未运行! ($i/${HEALTH_MAX_RETRIES})"
    echo "--- 最近日志 ---"
    docker logs --tail=30 ${CONTAINER_NAME} 2>&1 || true
    exit 1
  fi

  # 检查 health 端点
  HEALTH_CODE=$(curl -sf -o /dev/null -w "%{http_code}" --max-time 5 \
    "http://localhost:${APP_PORT}/infra/actuator/health" 2>/dev/null || echo "000")

  if [ "${HEALTH_CODE}" = "200" ]; then
    echo "  ✅ [${NOW}] Health: HTTP ${HEALTH_CODE} ($i/${HEALTH_MAX_RETRIES})"
    echo ""
    echo "  🎉 infra-monitor 服务健康!"
    break
  fi

  if [ $i -eq ${HEALTH_MAX_RETRIES} ]; then
    echo ""
    echo "  ⚠️ 健康检查超时，但容器正在运行 ($i/${HEALTH_MAX_RETRIES})"
    echo "  --- 最近日志 ---"
    docker logs --tail=30 ${CONTAINER_NAME} 2>&1 || true
    echo ""
    echo "  ℹ️ Java 应用首次启动可能需要 1-2 分钟，请手动验证:"
    echo "     curl http://localhost:${APP_PORT}/infra/actuator/health"
    break
  fi

  echo "  ⏳ [${NOW}] 等待服务启动... ($i/${HEALTH_MAX_RETRIES})"
  sleep ${HEALTH_INTERVAL}
done

# ======================== [7] 清理旧资源 ========================
echo ""
echo ">>> [7/7] 清理旧资源 <<<"

DANGLING=$(docker images -f "dangling=true" -q 2>/dev/null | wc -l)
if [ "${DANGLING}" -gt 0 ]; then
  docker image prune -f 2>/dev/null || true
  echo "✅ 已清理 ${DANGLING} 个悬空镜像"
else
  echo "✅ 无悬空镜像"
fi

# 清理旧前端备份（保留最近2个）
BACKUP_COUNT=$(ls -d "${WEB_DIR}.bak."* 2>/dev/null | wc -l)
if [ "${BACKUP_COUNT}" -gt 2 ]; then
  ls -dt "${WEB_DIR}.bak."* | tail -n +3 | xargs rm -rf 2>/dev/null || true
  echo "✅ 已清理旧前端备份（保留最近2个）"
fi

# ======================== 完成 ========================
echo ""
echo "============================================="
echo "  📊 infra-monitor 部署完成!"
echo "  时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo "  产物: ${TAR_FILE}"
echo ""
echo "  📊 服务访问:"
echo "    前端: https://monitor.marschat.online/infra/"
echo "    API:  https://monitor.marschat.online/infra/api/"
echo "    本地: http://localhost:${APP_PORT}/infra/"
echo "    健康: http://localhost:${APP_PORT}/infra/actuator/health"
echo ""
echo "  🔐 默认账号: admin / admin123"
echo "============================================="
