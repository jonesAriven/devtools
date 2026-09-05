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

# ====== Step 2: 解压 & 分发 dist ======
log_step 2 5 "解压 & 分发前端产物"
# vite base=/infra/，容器 nginx alias /usr/share/nginx/html/infra/
# 需要 dist 目录结构为 dist/infra/* （对齐 kb-web 的 dist/kb/s/ 范式）
mkdir -p "${DEPLOY_BASE}/infra-monitor-web/dist/infra" "${DEPLOY_BASE}/tmp-infra-monitor-web"
extract_artifact "${TAR_FILE}" "${DEPLOY_BASE}/tmp-infra-monitor-web"
rm -rf "${DEPLOY_BASE}/infra-monitor-web/dist"/*
mkdir -p "${DEPLOY_BASE}/infra-monitor-web/dist/infra"
cp -r "${DEPLOY_BASE}/tmp-infra-monitor-web/"* "${DEPLOY_BASE}/infra-monitor-web/dist/infra/"
rm -rf "${DEPLOY_BASE}/tmp-infra-monitor-web"
log_ok "infra-monitor-web dist 已更新 (结构: dist/infra/*)"

# ====== Step 3: 同步 compose 文件 & 确保 nginx.conf ======
log_step 3 5 "环境准备"
ensure_platform

# 确保目录存在
mkdir -p "${DEPLOY_BASE}/infra-monitor-web/dist"
touch "${DEPLOY_BASE}/infra-monitor-web/dist/.keep"

# 每次都覆盖 nginx.conf (确保配置与 vite base=/infra/ 对齐)
# 前端 base=/infra/、API base=/infra/api ；此前 nginx 只配 location / + /api/
# 导致 /infra/assets 回退成 text/html(MIME 报错 SPA 不挂载) 且 /infra/api 无代理(登录 404)
NGINX_CONF="${DEPLOY_BASE}/infra-monitor-web/nginx.conf"
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

    # SPA + 静态资源（vite base: /infra/，dist 解压到 /usr/share/nginx/html/infra/）
    location /infra/ {
        alias /usr/share/nginx/html/infra/;
        index index.html;
        try_files $uri $uri/ /infra/index.html;
    }

    # infra-monitor 后端 API（前端 API base: /infra/api → 后端 context /infra/）
    location /infra/api/ {
        proxy_pass http://172.17.0.1:8088/infra/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }

    # 根路径重定向到 /infra/
    location = / {
        return 302 /infra/;
    }
}
NGINXEOF
log_ok "nginx.conf 已更新: infra-monitor-web"

# ====== Step 4: 停止旧服务 ======
log_step 4 5 "停止旧服务"
compose_stop_services "${DEPLOY_BASE}" "${COMPOSE_PROJECT}" "${COMPOSE_FILE}" "${SERVICES[@]}"

# ====== Step 5: 构建并启动 ======
log_step 5 5 "构建并启动"
compose_up_services "${DEPLOY_BASE}" "${COMPOSE_PROJECT}" "${COMPOSE_FILE}" "${SERVICES[@]}"
health_check "${HEALTH_URL}" "${SERVICES[@]}"
prune_images

log_footer "${APP_NAME}" "${TAR_FILE}" "  infra-monitor-web: http://localhost:8094"
