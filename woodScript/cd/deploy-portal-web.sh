#!/bin/bash
# ============================================================
# deploy-portal-web.sh - Portal 门户前端部署
# ============================================================
# 用法: bash deploy-portal-web.sh <tar.gz文件名>
# 示例: bash deploy-portal-web.sh portal-web-latest.tar.gz
#
# 部署的服务: portal-web
# Compose:    docker-compose.web.yml (project: kb-web)
# 前置条件:   (前端容器独立运行)
# 隔离性:     只影响 portal-web 容器，不影响其他前端
# ============================================================
set -euo pipefail
source /mnt/shared/woodScript/lib-deploy.sh

# ====== 配置 ======
TAR_FILE="${1:?missing param: usage deploy-portal-web.sh tar.gz}"
COMPOSE_PROJECT="kb-web"
# compose 文件由 sync-ci-scripts 统一同步到 /mnt/shared
COMPOSE_FILE="/mnt/shared/mykng/docker/docker-compose.web.yml"
SERVICES=("portal-web")
HEALTH_URL="http://localhost:8095/health"
APP_NAME="portal-web"

log_header "${APP_NAME}" "${TAR_FILE}"

# ====== Step 1: 验证产物 ======
log_step 1 5 "验证产物"
verify_artifact "${TAR_FILE}"

# ====== Step 2: 解压 & 分发 dist（保留可读备份，避免中途失败全站 404）======
log_step 2 5 "解压 & 分发前端产物"
# vite base=/portal/，容器 nginx alias /usr/share/nginx/html/portal/
# 需要 dist 目录结构为 dist/portal/* （对齐 kb-web 的 dist/kb/s/ 范式）
SVC_DIR="${DEPLOY_BASE}/portal-web"
if [ -d "${SVC_DIR}/dist/portal" ]; then
  rm -rf "${SVC_DIR}/dist/portal.bak"
  cp -a "${SVC_DIR}/dist/portal" "${SVC_DIR}/dist/portal.bak"
fi
mkdir -p "${SVC_DIR}/dist/portal" "${DEPLOY_BASE}/tmp-portal-web"
extract_artifact "${TAR_FILE}" "${DEPLOY_BASE}/tmp-portal-web"
cp -a "${DEPLOY_BASE}/tmp-portal-web/." "${SVC_DIR}/dist/portal/"
rm -rf "${DEPLOY_BASE}/tmp-portal-web"
log_ok "portal-web dist 已更新 (结构: dist/portal/*) [保留 portal.bak 用于回滚]"

# ====== Step 3: 同步 compose 文件 & 渲染 nginx.conf ======
log_step 3 5 "环境准备"

# 确保目录存在
mkdir -p "${DEPLOY_BASE}/portal-web/dist"
touch "${DEPLOY_BASE}/portal-web/dist/.keep"

# 每次都渲染 nginx.conf（统一模板，确保与 vite base=/portal/ 对齐）
# 前端 base=/portal/、API base=/portal/api ；此前 nginx 只配 location / + /api/
# 导致 /portal/assets 回退成 text/html(SPA 不挂载) 且 /portal/api 无代理(登录 404)
NGINX_CONF="${DEPLOY_BASE}/portal-web/nginx.conf"
render_spa_nginx "${NGINX_CONF}" "/portal" "/portal/api" "http://172.17.0.1:8087/portal/"

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

log_footer "${APP_NAME}" "${TAR_FILE}" "  portal-web: http://localhost:8095"
