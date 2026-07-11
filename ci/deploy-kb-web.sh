#!/bin/bash
# ============================================================
# deploy-kb-web.sh — 前端容器层部署 (3个Nginx容器)
# ============================================================
# 用法: bash deploy-kb-web.sh <tar.gz文件名>
# 示例: bash deploy-kb-web.sh web-latest.tar.gz
#
# 部署的服务: kb-web, kb-ops-web, infra-monitor-web
# Compose:    docker-compose.web.yml (project: kb-web)
# 前置条件:   无 (前端容器独立运行，通过 Nginx 反代后端)
# 隔离性:     只影响前端容器，不影响后端微服务
# ============================================================
set -euo pipefail
source /mnt/shared/devtools/ci/lib-deploy.sh

# ====== 配置 ======
TAR_FILE="${1:?❌ 缺少参数! 用法: $0 <tar.gz文件名>}"
COMPOSE_PROJECT="kb-web"
COMPOSE_FILE="docker-compose.web.yml"
SERVICES=("kb-web" "kb-ops-web" "infra-monitor-web")
HEALTH_URL="http://localhost:8091/health"
APP_NAME="🌐 前端容器层"

log_header "${APP_NAME}" "${TAR_FILE}"

# ====== Step 1: 验证产物 ======
log_step 1 6 "验证产物"
verify_artifact "${TAR_FILE}"

# ====== Step 2: 解压 & 分发 dist ======
log_step 2 6 "解压 & 分发前端产物"
mkdir -p "${DEPLOY_BASE}/web-tmp"
extract_artifact "${TAR_FILE}" "${DEPLOY_BASE}/web-tmp"

# 分发到各前端目录
# kb-ops-web
if [ -d "${DEPLOY_BASE}/web-tmp/kb-ops-web" ]; then
  mkdir -p "${DEPLOY_BASE}/kb-ops-web/dist"
  rm -rf "${DEPLOY_BASE}/kb-ops-web/dist"/*
  cp -r "${DEPLOY_BASE}/web-tmp/kb-ops-web/"* "${DEPLOY_BASE}/kb-ops-web/dist/"
  log_ok "kb-ops-web dist 已更新"
fi

# infra-monitor-web
if [ -d "${DEPLOY_BASE}/web-tmp/infra-monitor-web" ]; then
  mkdir -p "${DEPLOY_BASE}/infra-monitor-web/dist"
  rm -rf "${DEPLOY_BASE}/infra-monitor-web/dist"/*
  cp -r "${DEPLOY_BASE}/web-tmp/infra-monitor-web/"* "${DEPLOY_BASE}/infra-monitor-web/dist/"
  log_ok "infra-monitor-web dist 已更新"
fi

# kb-web (无CI构建，保留现有dist)
if [ -d "${DEPLOY_BASE}/web-tmp/kb-web" ]; then
  mkdir -p "${DEPLOY_BASE}/kb-web/dist"
  rm -rf "${DEPLOY_BASE}/kb-web/dist"/*
  cp -r "${DEPLOY_BASE}/web-tmp/kb-web/"* "${DEPLOY_BASE}/kb-web/dist/"
  log_ok "kb-web dist 已更新"
else
  log_info "kb-web 无新产物，保留现有 dist (需手动更新)"
fi

# 清理解压临时目录
rm -rf "${DEPLOY_BASE}/web-tmp"

# ====== Step 3: 同步 compose 文件 ======
log_step 3 6 "环境准备"
sync_compose_files

# ====== Step 4: 停止旧服务 ======
log_step 4 6 "停止旧服务"
compose_down_all "${COMPOSE_PROJECT}" "${COMPOSE_FILE}"

# ====== Step 5: 构建并启动 ======
log_step 5 6 "构建并启动前端容器"
compose_up_all "${COMPOSE_PROJECT}" "${COMPOSE_FILE}"

# ====== Step 6: 健康检查 & 清理 ======
log_step 6 6 "健康检查 & 清理"
health_check "${HEALTH_URL}" "${SERVICES[@]}"
prune_images

log_footer "${APP_NAME}" "${TAR_FILE}" \
  "  kb-web:       http://localhost:8091
  kb-ops-web:    http://localhost:8093
  infra-monitor: http://localhost:8094"
