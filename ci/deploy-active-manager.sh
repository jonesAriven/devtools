#!/bin/bash
# ============================================================
# deploy-active-manager.sh — 激活码管理系统部署
# ============================================================
# 用法: bash deploy-active-manager.sh <tar.gz文件名>
# 示例: bash deploy-active-manager.sh active-manager-latest.tar.gz
#
# 部署的服务: activation-code-server
# Compose:    项目自带 docker-compose.yml (project: activecode)
# 目标主机:   内网 Debian (192.168.31.182)
# 隔离性:     完全独立，不影响其他应用
# ============================================================
set -euo pipefail
source /mnt/shared/devtools/ci/lib-deploy.sh

# ====== 配置 ======
TAR_FILE="${1:?❌ 缺少参数! 用法: $0 <tar.gz文件名>}"
APP_DIR="${GIT_REPO}/active-manager/activation-code-server"
COMPOSE_PROJECT="activecode"
COMPOSE_FILE="${APP_DIR}/docker-compose.yml"
SERVICES=("activation-code-server")
HEALTH_URL="http://localhost:18080/activecode/login.html"
APP_NAME="🔑 激活码管理系统"

log_header "${APP_NAME}" "${TAR_FILE}"

# ====== Step 1: 验证产物 ======
log_step 1 6 "验证产物"
verify_artifact "${TAR_FILE}"

# ====== Step 2: 解压 JAR ======
log_step 2 6 "解压 & 分发 JAR"
mkdir -p "${APP_DIR}/target"
extract_artifact "${TAR_FILE}" "${APP_DIR}/target"

# ====== Step 3: 检查 compose 文件 ======
log_step 3 6 "环境准备"
if [ ! -f "${COMPOSE_FILE}" ]; then
  log_err "compose 文件不存在: ${COMPOSE_FILE}"
  exit 1
fi
log_ok "compose 文件就绪: ${COMPOSE_FILE}"

# ====== Step 4: 停止旧服务 ======
log_step 4 6 "停止旧服务"
cd "${APP_DIR}"
if docker compose -p "${COMPOSE_PROJECT}" ps -q 2>/dev/null | grep -q .; then
  _heartbeat_start "docker compose down" &
  _HB_PID=$!
  docker compose -p "${COMPOSE_PROJECT}" down --remove-orphans --timeout 30 2>&1 || true
  _heartbeat_stop
  log_ok "已停止旧服务"
  sleep 3
else
  log_info "无正在运行的服务"
fi

# ====== Step 5: 构建并启动 ======
log_step 5 6 "构建并启动"
cd "${APP_DIR}"
_heartbeat_start "docker compose up" &
_HB_PID=$!
docker compose -p "${COMPOSE_PROJECT}" \
  up -d --build --force-recreate --remove-orphans 2>&1
_heartbeat_stop

echo ""
log_ok "服务启动完成"
docker compose -p "${COMPOSE_PROJECT}" ps 2>/dev/null || \
  docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

# ====== Step 6: 健康检查 & 清理 ======
log_step 6 6 "健康检查 & 清理"
health_check "${HEALTH_URL}" "${SERVICES[@]}"
prune_images

log_footer "${APP_NAME}" "${TAR_FILE}" \
  "  激活码: http://localhost:18080/activecode/login.html"
