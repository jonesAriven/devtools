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
compose_stop_services "${APP_DIR}" "${COMPOSE_PROJECT}" "$(basename ${COMPOSE_FILE})" "${SERVICES[@]}"

# ====== Step 5: 构建并启动 ======
log_step 5 6 "构建并启动"
compose_up_services "${APP_DIR}" "${COMPOSE_PROJECT}" "$(basename ${COMPOSE_FILE})" "${SERVICES[@]}"

# ====== Step 6: 健康检查 & 清理 ======
log_step 6 6 "健康检查 & 清理"
health_check "${HEALTH_URL}" "${SERVICES[@]}"
prune_images

log_footer "${APP_NAME}" "${TAR_FILE}"
