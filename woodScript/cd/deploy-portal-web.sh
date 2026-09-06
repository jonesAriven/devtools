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

# ====== Step 3: 同步 compose 文件 & 确保 nginx.conf ======
log_step 3 5 "环境准备"
ensure_platform

# 确保目录存在
mkdir -p "${DEPLOY_BASE}/portal-web/dist"
touch "${DEPLOY_BASE}/portal-web/dist/.keep"

# 每次都覆盖 nginx.conf (确保配置与 vite base=/portal/ 对齐)
# 前端 base=/portal/、API base=/portal/api ；此前 nginx 只配 location / + /api/
# 导致 /portal/assets 回退成 text/html(MIME 报错 SPA 不挂载) 且 /portal/api 无代理(登录 404)
NGINX_CONF="${DEPLOY_BASE}/portal-web/nginx.conf"
cat > "${NGINX_CONF}" << 'NGINXEOF'
server {
    listen 80;
    server_name _;
    server_tokens off;

    gzip on;
    gzip_types text/plain text/css application/json application/javascript text/xml application/xml application/xml+rss text/javascript image/svg+xml;
    gzip_min_length 1k;
    gzip_vary on;

    # 健康检查：校验 SPA 入口存在；dist 为空则返回 503（避免假阳性绿灯）
    location /health {
        access_log off;
        default_type application/json;
        if (!-f /usr/share/nginx/html/portal/index.html) {
            return 503 '{"status":"degraded","reason":"index.html missing"}';
        }
        return 200 '{"status":"ok"}';
    }

    # 无尾斜杠补 301，避免 /portal 直接 404
    location = /portal {
        return 301 /portal/;
    }

    # 静态资源：缺失即 404（不兜底成 index.html，杜绝 MIME 错误 / 白屏复发）
    location /portal/assets/ {
        alias /usr/share/nginx/html/portal/assets/;
        expires 30d;
        add_header Cache-Control "public, immutable";
        try_files $uri =404;
    }

    # SPA + 静态资源（vite base: /portal/，dist 解压到 /usr/share/nginx/html/portal/）
    location /portal/ {
        alias /usr/share/nginx/html/portal/;
        index index.html;
        try_files $uri $uri/ /portal/index.html;
    }

    # portal 后端 API（前端 API base: /portal/api → 后端 context /portal/）
    location /portal/api/ {
        proxy_pass http://172.17.0.1:8087/portal/;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_connect_timeout 5s;
        proxy_read_timeout 30s;
        proxy_send_timeout 30s;
    }

    # 根路径重定向到 /portal/
    location = / {
        return 302 /portal/;
    }
}
NGINXEOF
log_ok "nginx.conf 已更新: portal-web [双 location / 301 / 缓存头 / 代理头]"

# ====== Step 4: 停止旧服务 ======
log_step 4 5 "停止旧服务"
compose_stop_services "${DEPLOY_BASE}" "${COMPOSE_PROJECT}" "${COMPOSE_FILE}" "${SERVICES[@]}"

# ====== Step 5: 构建并启动 ======
log_step 5 5 "构建并启动"
compose_up_services "${DEPLOY_BASE}" "${COMPOSE_PROJECT}" "${COMPOSE_FILE}" "${SERVICES[@]}"
health_check "${HEALTH_URL}" "${SERVICES[@]}"
prune_images

log_footer "${APP_NAME}" "${TAR_FILE}" "  portal-web: http://localhost:8095"
