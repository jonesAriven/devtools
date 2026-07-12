#!/bin/bash
# ============================================================
# deploy-infra-monitor.sh - 基础设施监控系统部署
# ============================================================
# 用法: bash deploy-infra-monitor.sh <tar.gz文件名>
# 示例: bash deploy-infra-monitor.sh infra-monitor-latest.tar.gz
#
# 部署的服务: infra-monitor
# Compose:    项目自带 docker-compose.yml (project: infra-monitor)
# 前置条件:   (独立项目，自带MySQL连接)
# 隔离性:     完全独立，不影响其他应用
# ============================================================
set -euo pipefail
source /mnt/shared/woodScript/lib-deploy.sh

# ====== 配置 ======
TAR_FILE="${1:?missing param: usage deploy-infra-monitor.sh tar.gz}"
APP_DIR="${GIT_REPO}/infra-monitor/infra-monitor-server"
COMPOSE_PROJECT="infra-monitor"
COMPOSE_FILE="${APP_DIR}/docker-compose.yml"
SERVICES=("infra-monitor")
HEALTH_URL="http://localhost:8088/infra/actuator/health"
APP_NAME="infra-monitor"

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
compose_stop_services "${APP_DIR}" "${COMPOSE_PROJECT}" "$(basename ${COMPOSE_FILE})" "${SERVICES[@]}"

# ====== Step 5: 构建并启动 ======
log_step 5 6 "构建并启动"
compose_up_services "${APP_DIR}" "${COMPOSE_PROJECT}" "$(basename ${COMPOSE_FILE})" "${SERVICES[@]}"

# ====== Step 6: 健康检查 & 清理 ======
log_step 6 6 "健康检查 & 清理"
health_check "${HEALTH_URL}" "${SERVICES[@]}"
prune_images

log_footer "${APP_NAME}" "${TAR_FILE}" "  监控: http://localhost:8088/infra"
