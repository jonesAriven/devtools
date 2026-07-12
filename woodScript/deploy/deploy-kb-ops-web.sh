#!/bin/bash
# ============================================================
# deploy-kb-ops-web.sh �?运维平台前端部署
# ============================================================
# 用法: bash deploy-kb-ops-web.sh <tar.gz文件�?
# 示例: bash deploy-kb-ops-web.sh kb-ops-web-latest.tar.gz
#
# 部署的服�? kb-ops-web
# Compose:    docker-compose.web.yml (project: kb-web)
# 前置条件:   �?(前端容器独立运行)
# 隔离�?     只影�?kb-ops-web 容器，不影响其他前端
# ============================================================
set -euo pipefail
source /mnt/shared/woodDeploy/woodScript/lib-deploy.sh

# ====== 配置 ======
TAR_FILE="${1:?missing param: usage deploy-kb-ops-web.sh tar.gz}"
COMPOSE_PROJECT="kb-web"
COMPOSE_FILE="docker-compose.web.yml"
SERVICES=("kb-ops-web")
HEALTH_URL="http://localhost:8093/health"
APP_NAME="kb-ops-web"

log_header "${APP_NAME}" "${TAR_FILE}"

# ====== Step 1: 验证产物 ======
log_step 1 5 "验证产物"
verify_artifact "${TAR_FILE}"

# ====== Step 2: 解压 & 分发 dist ======
log_step 2 5 "解压 & 分发前端产物"
mkdir -p "${DEPLOY_BASE}/kb-ops-web/dist" "${DEPLOY_BASE}/tmp-kb-ops-web"
extract_artifact "${TAR_FILE}" "${DEPLOY_BASE}/tmp-kb-ops-web"
rm -rf "${DEPLOY_BASE}/kb-ops-web/dist"/*
cp -r "${DEPLOY_BASE}/tmp-kb-ops-web/"* "${DEPLOY_BASE}/kb-ops-web/dist/"
rm -rf "${DEPLOY_BASE}/tmp-kb-ops-web"
log_ok "kb-ops-web dist 已更�?

# ====== Step 3: 同步 compose 文件 & 确保 nginx.conf ======
log_step 3 5 "环境准备"
sync_compose_files

# 确保目录存在
mkdir -p "${DEPLOY_BASE}/kb-ops-web/dist"
touch "${DEPLOY_BASE}/kb-ops-web/dist/.keep"

# 每次都覆�?nginx.conf (确保 host.docker.internal 不残�?
NGINX_CONF="${DEPLOY_BASE}/kb-ops-web/nginx.conf"
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
        proxy_pass http://172.17.0.1:8084/kb-ops/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }
}
NGINXEOF
log_ok "nginx.conf 已更�? kb-ops-web"

# ====== Step 4: 停止旧服�?======
log_step 4 5 "停止旧服�?
compose_stop_services "${DEPLOY_BASE}" "${COMPOSE_PROJECT}" "${COMPOSE_FILE}" "${SERVICES[@]}"

# ====== Step 5: 构建并启�?======
log_step 5 5 "构建并启�?
compose_up_services "${DEPLOY_BASE}" "${COMPOSE_PROJECT}" "${COMPOSE_FILE}" "${SERVICES[@]}"
health_check "${HEALTH_URL}" "${SERVICES[@]}"
prune_images

log_footer "${APP_NAME}" "${TAR_FILE}" "  kb-ops-web: http://localhost:8093"
