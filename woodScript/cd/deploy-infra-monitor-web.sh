#!/bin/bash
# ============================================================
# deploy-infra-monitor-web.sh - 监控前端部署
# ============================================================
# 用法: bash deploy-infra-monitor-web.sh <tar.gz文件名>
# 示例: bash deploy-infra-monitor-web.sh infra-monitor-web-latest.tar.gz
#
# 部署的服务: infra-monitor-web
# Compose:    docker-compose.web.yml (project: kb-web)
# 前置条件:   (前端容器独立运行)
# 隔离性:     只影响 infra-monitor-web 容器，不影响其他前端
# ============================================================
set -euo pipefail
source /mnt/shared/woodScript/lib-deploy.sh

# ====== 配置 ======
TAR_FILE="${1:?missing param: usage deploy-infra-monitor-web.sh tar.gz}"
COMPOSE_PROJECT="kb-web"
# compose 文件由 sync-ci-scripts 统一同步到 /mnt/shared
COMPOSE_FILE="/mnt/shared/mykng/docker/docker-compose.web.yml"
SERVICES=("infra-monitor-web")
HEALTH_URL="http://localhost:8094/health"
APP_NAME="infra-monitor-web"

log_header "${APP_NAME}" "${TAR_FILE}"

# ====== Step 1: 验证产物 ======
log_step 1 5 "验证产物"
verify_artifact "${TAR_FILE}"

# ====== Step 2: 解压 & 分发 dist（保留可读备份，避免中途失败全站 404）======
log_step 2 5 "解压 & 分发前端产物"
# vite base=/infra/，容器 nginx alias /usr/share/nginx/html/infra/
# 需要 dist 目录结构为 dist/infra/* （对齐 kb-web 的 dist/kb/s/ 范式）
SVC_DIR="${DEPLOY_BASE}/infra-monitor-web"
if [ -d "${SVC_DIR}/dist/infra" ]; then
  rm -rf "${SVC_DIR}/dist/infra.bak"
  cp -a "${SVC_DIR}/dist/infra" "${SVC_DIR}/dist/infra.bak"
fi
mkdir -p "${SVC_DIR}/dist/infra" "${DEPLOY_BASE}/tmp-infra-monitor-web"
extract_artifact "${TAR_FILE}" "${DEPLOY_BASE}/tmp-infra-monitor-web"
cp -a "${DEPLOY_BASE}/tmp-infra-monitor-web/." "${SVC_DIR}/dist/infra/"
rm -rf "${DEPLOY_BASE}/tmp-infra-monitor-web"
log_ok "infra-monitor-web dist 已更新 (结构: dist/infra/*) [保留 infra.bak 用于回滚]"

# ====== Step 3: 同步 compose 文件 & 渲染 nginx.conf ======
log_step 3 5 "环境准备"

# 确保目录存在
mkdir -p "${DEPLOY_BASE}/infra-monitor-web/dist"
touch "${DEPLOY_BASE}/infra-monitor-web/dist/.keep"

# 每次都渲染 nginx.conf（统一模板，确保与 vite base=/infra/ 对齐）
# 前端 base=/infra/、API base=/infra/api ；此前 nginx 只配 location / + /api/
# 导致 /infra/assets 回退成 text/html(SPA 不挂载) 且 /infra/api 无代理(登录 404)
NGINX_CONF="${DEPLOY_BASE}/infra-monitor-web/nginx.conf"
render_spa_nginx "${NGINX_CONF}" "/infra" "/infra/api" "http://172.17.0.1:8088/infra/"

# ====== Step 4: 停止旧服务 ======
log_step 4 5 "停止旧服务"
# 回滚准备：记录当前运行镜像（部署后打 :previous 标签保留）
OLD_IMAGE=$(docker inspect --format='{{.Image}}' "${SERVICES[0]}" 2>/dev/null || true)
compose_stop_services "${DEPLOY_BASE}" "${COMPOSE_PROJECT}" "${COMPOSE_FILE}" "${SERVICES[@]}"

# ====== Step 5: 构建并启动 ======
log_step 5 5 "构建并启动"
compose_up_services "${DEPLOY_BASE}" "${COMPOSE_PROJECT}" "${COMPOSE_FILE}" "${SERVICES[@]}"
health_check "${HEALTH_URL}" "${SERVICES[@]}"
# 保留上一版镜像用于回滚（prune_images 只清 dangling，:previous 有标签不会被清）
if [ -n "${OLD_IMAGE}" ]; then
  docker tag "${OLD_IMAGE}" "${SERVICES[0]}:previous" 2>/dev/null || true
  log_ok "已保留回滚镜像 ${SERVICES[0]}:previous"
fi
prune_images

log_footer "${APP_NAME}" "${TAR_FILE}" "  infra-monitor-web: http://localhost:8094"
