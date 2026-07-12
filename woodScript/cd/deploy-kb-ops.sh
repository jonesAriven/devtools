#!/bin/bash
# ============================================================
# deploy-kb-ops.sh - kb-ops 运维平台部署 (独立应用)
# ============================================================
# 用法: bash deploy-kb-ops.sh <tar.gz文件名>
# 示例: bash deploy-kb-ops.sh kb-ops-latest.tar.gz
#
# 部署的服务: kb-ops
# Compose:    docker-compose.app.yml (project: kb-app, 复用同一个compose文件)
# 前置条件:   platform 全局基础设施层已启动
# 隔离性:     只重建 kb-ops，不影响 mykng 5个微服务和前端容器
# ============================================================
set -euo pipefail
source /mnt/shared/woodScript/lib-deploy.sh

# ====== 配置 ======
TAR_FILE="${1:?missing param: usage deploy-kb-ops.sh tar.gz}"
APP_DIR="${GIT_REPO}/kb-ops"
COMPOSE_PROJECT="kb-app"
COMPOSE_FILE="docker-compose.app.yml"
SERVICES=("kb-ops")
HEALTH_URL="http://localhost:8084/kb-ops/actuator/health"
APP_NAME="kb-ops"

log_header "${APP_NAME}" "${TAR_FILE}"

# ====== Step 1: 验证产物 ======
log_step 1 6 "验证产物"
verify_artifact "${TAR_FILE}"

# ====== Step 2: 解压 & 分发 JAR ======
log_step 2 6 "解压 & 分发 JAR"
mkdir -p "${APP_DIR}/target"
extract_artifact "${TAR_FILE}" "${APP_DIR}/target"

# ====== Step 3: 同步 compose 文件 & 检查网络 ======
log_step 3 6 "环境准备"
sync_compose_files
ensure_platform

# ====== Step 4: 停止旧服务(只停 kb-ops，不影响其他) ======
log_step 4 6 "停止旧服务"
compose_stop_services "${DEPLOY_BASE}" "${COMPOSE_PROJECT}" "${COMPOSE_FILE}" "${SERVICES[@]}"

# ====== Step 5: 构建并启动 ======
log_step 5 6 "构建并启动新服务"
compose_up_services "${DEPLOY_BASE}" "${COMPOSE_PROJECT}" "${COMPOSE_FILE}" "${SERVICES[@]}"

# ====== Step 6: 健康检查 & 清理 ======
log_step 6 6 "健康检查 & 清理"
health_check "${HEALTH_URL}" "${SERVICES[@]}"
prune_images

log_footer "${APP_NAME}" "${TAR_FILE}" "Backend: http://localhost:8084"
