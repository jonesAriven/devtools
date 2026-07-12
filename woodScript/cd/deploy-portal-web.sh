#!/bin/bash
# ============================================================
# deploy-portal-web.sh �?Portal 门户前端部署
# ============================================================
# 用法: bash deploy-portal-web.sh <tar.gz文件�?
# 示例: bash deploy-portal-web.sh portal-web-latest.tar.gz
#
# 部署的服�? portal-web
# Compose:    docker-compose.web.yml (project: kb-web)
# 前置条件:   �?(前端容器独立运行)
# 隔离�?     只影�?portal-web 容器，不影响其他前端
# ============================================================
set -euo pipefail
source /mnt/shared/woodScript/lib-deploy.sh

# ====== 配置 ======
TAR_FILE="${1:?missing param: usage deploy-portal-web.sh tar.gz}"
COMPOSE_PROJECT="kb-web"
COMPOSE_FILE="docker-compose.web.yml"
SERVICES=("portal-web")
HEALTH_URL="http://localhost:8095/health"
APP_NAME="portal-web"

log_header "${APP_NAME}" "${TAR_FILE}"

# ====== Step 1: 验证产物 ======
log_step 1 5 "验证产物"
verify_artifact "${TAR_FILE}"

# ====== Step 2: 解压 & 分发 dist ======
log_step 2 5 "解压 & 分发前端产物"
mkdir -p "${DEPLOY_BASE}/portal-web/dist" "${DEPLOY_BASE}/tmp-portal-web"
extract_artifact "${TAR_FILE}" "${DEPLOY_BASE}/tmp-portal-web"
rm -rf "${DEPLOY_BASE}/portal-web/dist"/*
cp -r "${DEPLOY_BASE}/tmp-portal-web/"* "${DEPLOY_BASE}/portal-web/dist/"
rm -rf "${DEPLOY_BASE}/tmp-portal-web"
log_ok "portal-web dist 已更�?

# ====== Step 3: 同步 compose 文件 & 确保 nginx.conf ======
log_step 3 5 "环境准备"
sync_compose_files

# 确保目录存在
mkdir -p "${DEPLOY_BASE}/portal-web/dist"
touch "${DEPLOY_BASE}/portal-web/dist/.keep"

# 每次都覆�?nginx.conf
NGINX_CONF="${DEPLOY_BASE}/portal-web/nginx.conf"
cat > "${NGINX_CONF}" << 'NGINXEOF'
server {
    listen 80;
    server_name _;
    root /usr/share/nginx/html;
    index index.html;
    client_max_body_size 100m;

    location / {
        try_files $uri $uri/ /index.html;
    }

    location /api/ {
        proxy_pass http://172.17.0.1:8087/portal/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }
}
NGINXEOF
log_ok "nginx.conf 已更�? portal-web"

# ====== Step 4: 停止旧服�?======
log_step 4 5 "停止旧服�?
compose_stop_services "${DEPLOY_BASE}" "${COMPOSE_PROJECT}" "${COMPOSE_FILE}" "${SERVICES[@]}"

# ====== Step 5: 构建并启�?======
log_step 5 5 "构建并启�?
compose_up_services "${DEPLOY_BASE}" "${COMPOSE_PROJECT}" "${COMPOSE_FILE}" "${SERVICES[@]}"
health_check "${HEALTH_URL}" "${SERVICES[@]}"
prune_images

log_footer "${APP_NAME}" "${TAR_FILE}" "  portal-web: http://localhost:8095"
