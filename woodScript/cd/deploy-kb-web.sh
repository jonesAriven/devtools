#!/bin/bash
# ============================================================
# deploy-kb-web.sh - kb 主前端部署
# ============================================================
# 用法: bash deploy-kb-web.sh <tar.gz文件名>
# 示例: bash deploy-kb-web.sh kb-web-latest.tar.gz
#
# 部署的服务: kb-web
# Compose:    docker-compose.web.yml (project: kb-web)
# 隔离性:     只影响 kb-web 容器，不影响其他前端
# ============================================================
set -euo pipefail
source /mnt/shared/woodScript/lib-deploy.sh

# ====== 配置 ======
TAR_FILE="${1:?missing param: usage deploy-kb-web.sh tar.gz}"
COMPOSE_PROJECT="kb-web"
# compose 文件由 sync-ci-scripts 统一同步到 /mnt/shared
COMPOSE_FILE="/mnt/shared/mykng/docker/docker-compose.web.yml"
SERVICES=("kb-web")
HEALTH_URL="http://localhost:8091/health"
APP_NAME="kb-web"

log_header "${APP_NAME}" "${TAR_FILE}"

# ====== Step 1: 验证产物 ======
log_step 1 5 "验证产物"
verify_artifact "${TAR_FILE}"

# ====== Step 2: 解压 & 分发 dist ======
log_step 2 5 "解压 & 分发前端产物"
# vite base=/kb/s/，容器 nginx alias /usr/share/nginx/html/kb/s/
# 需要 dist 目录结构为 dist/kb/s/*
mkdir -p "${DEPLOY_BASE}/kb-web/dist/kb/s" "${DEPLOY_BASE}/tmp-kb-web"
extract_artifact "${TAR_FILE}" "${DEPLOY_BASE}/tmp-kb-web"
rm -rf "${DEPLOY_BASE}/kb-web/dist"/*
mkdir -p "${DEPLOY_BASE}/kb-web/dist/kb/s"
cp -r "${DEPLOY_BASE}/tmp-kb-web/"* "${DEPLOY_BASE}/kb-web/dist/kb/s/"
rm -rf "${DEPLOY_BASE}/tmp-kb-web"
log_ok "kb-web dist 已更新 (结构: dist/kb/s/*)"

# ====== Step 3: 同步 compose 文件 & 确保 nginx.conf ======
log_step 3 5 "环境准备"
ensure_platform

# 确保目录存在
mkdir -p "${DEPLOY_BASE}/kb-web/dist"
touch "${DEPLOY_BASE}/kb-web/dist/.keep"

# 每次都覆盖 nginx.conf
# 注意：vite base=/kb/s/，dist 结构就是 dist/kb/s/*
NGINX_CONF="${DEPLOY_BASE}/kb-web/nginx.conf"
cat > "${NGINX_CONF}" << 'NGINXEOF'
server {
    listen 80;
    server_name _;

    # gzip 兜底（宿主机 nginx 已开 brotli）
    gzip on;
    gzip_types text/plain text/css application/json application/javascript text/xml application/xml application/xml+rss text/javascript;
    gzip_min_length 1k;

    # 健康检查
    location /health {
        access_log off;
        return 200 '{"status":"ok"}';
        add_header Content-Type application/json;
    }

    # 静态资源（vite base: /kb/s/）
    location /kb/s/ {
        alias /usr/share/nginx/html/kb/s/;
        expires 30d;
        add_header Cache-Control "public, immutable";
        try_files $uri =404;
    }

    # SPA 路由回退
    location /kb/ {
        alias /usr/share/nginx/html/kb/s/;
        index index.html;
        try_files $uri $uri/ /kb/s/index.html;
    }

    # 根路径重定向
    location = / {
        return 302 /kb/;
    }
}
NGINXEOF
log_ok "nginx.conf 已更新: kb-web"

# ====== Step 4: 停止旧服务 ======
log_step 4 5 "停止旧服务"
compose_stop_services "${DEPLOY_BASE}" "${COMPOSE_PROJECT}" "${COMPOSE_FILE}" "${SERVICES[@]}"

# ====== Step 5: 构建并启动 ======
log_step 5 5 "构建并启动"
compose_up_services "${DEPLOY_BASE}" "${COMPOSE_PROJECT}" "${COMPOSE_FILE}" "${SERVICES[@]}"
health_check "${HEALTH_URL}" "${SERVICES[@]}"
prune_images

log_footer "${APP_NAME}" "${TAR_FILE}" "  kb-web: http://localhost:8091"
