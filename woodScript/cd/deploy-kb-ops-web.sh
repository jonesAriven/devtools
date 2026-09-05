#!/bin/bash
# ============================================================
# deploy-kb-ops-web.sh - 运维平台前端部署
# ============================================================
# 用法: bash deploy-kb-ops-web.sh <tar.gz文件名>
# 示例: bash deploy-kb-ops-web.sh kb-ops-web-latest.tar.gz
#
# 部署的服务: kb-ops-web
# Compose:    docker-compose.web.yml (project: kb-web)
# 前置条件:   (前端容器独立运行)
# 隔离性:     只影响 kb-ops-web 容器，不影响其他前端
# ============================================================
set -euo pipefail
source /mnt/shared/woodScript/lib-deploy.sh

# ====== 配置 ======
TAR_FILE="${1:?missing param: usage deploy-kb-ops-web.sh tar.gz}"
COMPOSE_PROJECT="kb-web"
# compose 文件由 sync-ci-scripts 统一同步到 /mnt/shared
COMPOSE_FILE="/mnt/shared/mykng/docker/docker-compose.web.yml"
SERVICES=("kb-ops-web")
HEALTH_URL="http://localhost:8093/health"
APP_NAME="kb-ops-web"

log_header "${APP_NAME}" "${TAR_FILE}"

# ====== Step 1: 验证产物 ======
log_step 1 5 "验证产物"
verify_artifact "${TAR_FILE}"

# ====== Step 2: 解压 & 分发 dist ======
log_step 2 5 "解压 & 分发前端产物"
# vite base=/ops/，容器 nginx alias /usr/share/nginx/html/ops/
# 需要 dist 目录结构为 dist/ops/* （对齐 kb-web 的 dist/kb/s/ 范式）
mkdir -p "${DEPLOY_BASE}/kb-ops-web/dist/ops" "${DEPLOY_BASE}/tmp-kb-ops-web"
extract_artifact "${TAR_FILE}" "${DEPLOY_BASE}/tmp-kb-ops-web"
rm -rf "${DEPLOY_BASE}/kb-ops-web/dist"/*
mkdir -p "${DEPLOY_BASE}/kb-ops-web/dist/ops"
cp -r "${DEPLOY_BASE}/tmp-kb-ops-web/"* "${DEPLOY_BASE}/kb-ops-web/dist/ops/"
rm -rf "${DEPLOY_BASE}/tmp-kb-ops-web"
log_ok "kb-ops-web dist 已更新 (结构: dist/ops/*)"

# ====== Step 3: 同步 compose 文件 & 确保 nginx.conf ======
log_step 3 5 "环境准备"
ensure_platform

# 确保目录存在
mkdir -p "${DEPLOY_BASE}/kb-ops-web/dist"
touch "${DEPLOY_BASE}/kb-ops-web/dist/.keep"

# 每次都覆盖 nginx.conf (确保配置与 vite base=/ops/ 对齐)
# 前端 base=/ops/、API base=/ops-api ；此前 nginx 只配 location / + /api/
# 导致 /ops/assets 回退成 text/html(MIME 报错 SPA 不挂载) 且 /ops-api 无代理(登录 404)
NGINX_CONF="${DEPLOY_BASE}/kb-ops-web/nginx.conf"
cat > "${NGINX_CONF}" << 'NGINXEOF'
server {
    listen 80;
    server_name _;

    gzip on;
    gzip_types text/plain text/css application/json application/javascript text/xml application/xml application/xml+rss text/javascript;
    gzip_min_length 1k;

    # 健康检查（显式端点，避免依赖 SPA 文件存在）
    location /health {
        access_log off;
        return 200 '{"status":"ok"}';
        add_header Content-Type application/json;
    }

    # SPA + 静态资源（vite base: /ops/，dist 解压到 /usr/share/nginx/html/ops/）
    location /ops/ {
        alias /usr/share/nginx/html/ops/;
        index index.html;
        try_files $uri $uri/ /ops/index.html;
    }

    # kb-ops 后端 API（前端 API base: /ops-api → 后端 context /kb-ops/）
    location /ops-api/ {
        proxy_pass http://172.17.0.1:8084/kb-ops/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }

    # 根路径重定向到 /ops/
    location = / {
        return 302 /ops/;
    }
}
NGINXEOF
log_ok "nginx.conf 已更新: kb-ops-web"

# ====== Step 4: 停止旧服务 ======
log_step 4 5 "停止旧服务"
compose_stop_services "${DEPLOY_BASE}" "${COMPOSE_PROJECT}" "${COMPOSE_FILE}" "${SERVICES[@]}"

# ====== Step 5: 构建并启动 ======
log_step 5 5 "构建并启动"
compose_up_services "${DEPLOY_BASE}" "${COMPOSE_PROJECT}" "${COMPOSE_FILE}" "${SERVICES[@]}"
health_check "${HEALTH_URL}" "${SERVICES[@]}"
prune_images

log_footer "${APP_NAME}" "${TAR_FILE}" "  kb-ops-web: http://localhost:8093"
