#!/bin/bash
# ============================================================
# deploy-infra-monitor-web.sh — 监控前端部署
# ============================================================
# 用法: bash deploy-infra-monitor-web.sh <tar.gz文件名>
# 示例: bash deploy-infra-monitor-web.sh infra-monitor-web-latest.tar.gz
#
# 部署的服务: infra-monitor-web
# Compose:    docker-compose.web.yml (project: kb-web)
# 前置条件:   无 (前端容器独立运行)
# 隔离性:     只影响 infra-monitor-web 容器，不影响其他前端
# ============================================================
set -euo pipefail
source /mnt/shared/devtools/ci/lib-deploy.sh

# ====== 配置 ======
TAR_FILE="${1:?❌ 缺少参数! 用法: $0 <tar.gz文件名>}"
COMPOSE_PROJECT="kb-web"
COMPOSE_FILE="docker-compose.web.yml"
SERVICES=("infra-monitor-web")
HEALTH_URL="http://localhost:8094/health"
APP_NAME="🌐 监控前端 (infra-monitor-web)"

log_header "${APP_NAME}" "${TAR_FILE}"

# ====== Step 1: 验证产物 ======
log_step 1 5 "验证产物"
verify_artifact "${TAR_FILE}"

# ====== Step 2: 解压 & 分发 dist ======
log_step 2 5 "解压 & 分发前端产物"
mkdir -p "${DEPLOY_BASE}/infra-monitor-web/dist" "${DEPLOY_BASE}/web-tmp"
extract_artifact "${TAR_FILE}" "${DEPLOY_BASE}/web-tmp"
rm -rf "${DEPLOY_BASE}/infra-monitor-web/dist"/*
cp -r "${DEPLOY_BASE}/web-tmp/"* "${DEPLOY_BASE}/infra-monitor-web/dist/"
rm -rf "${DEPLOY_BASE}/web-tmp"
log_ok "infra-monitor-web dist 已更新"

# ====== Step 3: 同步 compose 文件 & 确保 nginx.conf ======
log_step 3 5 "环境准备"
sync_compose_files

# 确保目录存在
mkdir -p "${DEPLOY_BASE}/infra-monitor-web/dist"
touch "${DEPLOY_BASE}/infra-monitor-web/dist/.keep"

# 创建默认 nginx.conf (如果不存在)
NGINX_CONF="${DEPLOY_BASE}/infra-monitor-web/nginx.conf"
if [ ! -f "${NGINX_CONF}" ]; then
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
        proxy_pass http://host.docker.internal:8088/infra/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }
}
NGINXEOF
  log_ok "创建默认 nginx.conf: infra-monitor-web"
fi

# ====== Step 4: 停止旧服务 ======
log_step 4 5 "停止旧服务"
compose_stop_services "${DEPLOY_BASE}" "${COMPOSE_PROJECT}" "${COMPOSE_FILE}" "${SERVICES[@]}"

# ====== Step 5: 构建并启动 ======
log_step 5 5 "构建并启动"
compose_up_services "${DEPLOY_BASE}" "${COMPOSE_PROJECT}" "${COMPOSE_FILE}" "${SERVICES[@]}"
health_check "${HEALTH_URL}" "${SERVICES[@]}"
prune_images

log_footer "${APP_NAME}" "${TAR_FILE}" \
  "  infra-monitor-web: http://localhost:8094"
