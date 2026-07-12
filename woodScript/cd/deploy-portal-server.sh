#!/bin/bash
# ============================================================
# deploy-portal-server.sh �?Portal 门户后端部署
# ============================================================
# 用法: bash deploy-portal-server.sh <tar.gz文件�?
# 示例: bash deploy-portal-server.sh portal-server-latest.tar.gz
#
# 部署的服�? portal-server
# Compose:    docker-compose.app.yml (project: kb-app, 复用同一个compose文件)
# 前置条件:   platform 全局基础设施层已启动
# 隔离�?     只重�?portal-server，不影响其他服务
# ============================================================
set -euo pipefail
source /mnt/shared/woodScript/lib-deploy.sh

# ====== 配置 ======
TAR_FILE="${1:?missing param: usage deploy-portal-server.sh tar.gz}"
APP_DIR="${GIT_REPO}/portal/portal-server"
COMPOSE_PROJECT="kb-app"
COMPOSE_FILE="docker-compose.app.yml"
SERVICES=("portal-server")
HEALTH_URL="http://localhost:8087/portal/actuator/health"
APP_NAME="portal-server"

log_header "${APP_NAME}" "${TAR_FILE}"

# ====== Step 1: 验证产物 ======
log_step 1 6 "验证产物"
verify_artifact "${TAR_FILE}"

# ====== Step 2: 解压 & 分发 JAR ======
log_step 2 6 "解压 & 分发 JAR"
mkdir -p "${APP_DIR}/target"
extract_artifact "${TAR_FILE}" "${APP_DIR}/target"

# ====== Step 3: 同步 compose 文件 & 检查网�?======
log_step 3 6 "环境准备"
sync_compose_files
ensure_platform

# ====== Step 4: 停止旧服�?======
log_step 4 6 "停止旧服�?
compose_stop_services "${DEPLOY_BASE}" "${COMPOSE_PROJECT}" "${COMPOSE_FILE}" "${SERVICES[@]}"

# ====== Step 5: 构建并启�?======
log_step 5 6 "构建并启动新服务"
compose_up_services "${DEPLOY_BASE}" "${COMPOSE_PROJECT}" "${COMPOSE_FILE}" "${SERVICES[@]}"

# ====== Step 6: 健康检�?& 清理 ======
log_step 6 6 "健康检�?& 清理"
health_check "${HEALTH_URL}" "${SERVICES[@]}"
prune_images

log_footer "${APP_NAME}" "${TAR_FILE}" "Backend: http://localhost:8087"
