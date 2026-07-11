#!/bin/bash
# ============================================================
# deploy-kb-ops-web.sh — 运维平台前端部署
# ============================================================
# 用法: bash deploy-kb-ops-web.sh <tar.gz文件名>
# 示例: bash deploy-kb-ops-web.sh kb-ops-web-latest.tar.gz
#
# 部署的服务: kb-ops-web
# Compose:    docker-compose.web.yml (project: kb-web)
# 前置条件:   无 (前端容器独立运行)
# 隔离性:     只影响 kb-ops-web 容器，不影响其他前端
# ============================================================
set -euo pipefail
source /mnt/shared/devtools/ci/lib-deploy.sh

# ====== 配置 ======
TAR_FILE="${1:?❌ 缺少参数! 用法: $0 <tar.gz文件名>}"
COMPOSE_PROJECT="kb-web"
COMPOSE_FILE="docker-compose.web.yml"
SERVICES=("kb-ops-web")
HEALTH_URL="http://localhost:8093/health"
APP_NAME="🌐 运维平台前端 (kb-ops-web)"

log_header "${APP_NAME}" "${TAR_FILE}"

# ====== Step 1: 验证产物 ======
log_step 1 5 "验证产物"
verify_artifact "${TAR_FILE}"

# ====== Step 2: 解压 & 分发 dist ======
log_step 2 5 "解压 & 分发前端产物"
mkdir -p "${DEPLOY_BASE}/kb-ops-web/dist" "${DEPLOY_BASE}/web-tmp"
extract_artifact "${TAR_FILE}" "${DEPLOY_BASE}/web-tmp"
# 先清空目标目录，再用 rsync/cp -a 安全复制（保留子目录结构）
rm -rf "${DEPLOY_BASE}/kb-ops-web/dist/"
mkdir -p "${DEPLOY_BASE}/kb-ops-web/dist"
cp -a "${DEPLOY_BASE}/web-tmp"/. "${DEPLOY_BASE}/kb-ops-web/dist/"
rm -rf "${DEPLOY_BASE}/web-tmp"
# 验证复制结果
local file_count=$(find "${DEPLOY_BASE}/kb-ops-web/dist" -type f | wc -l)
if [ "${file_count}" -eq 0 ]; then
  log_err "dist 目录为空! 解压可能失败"
  ls -laR "${DEPLOY_BASE}/web-tmp/" 2>/dev/null || true
  exit 1
fi
log_ok "kb-ops-web dist 已更新 (${file_count} 个文件)"

# ====== Step 3: 同步 compose 文件 & 确保 nginx.conf ======
log_step 3 5 "环境准备"
sync_compose_files

# 确保目录存在
mkdir -p "${DEPLOY_BASE}/kb-ops-web/dist"
touch "${DEPLOY_BASE}/kb-ops-web/dist/.keep"

# 创建默认 nginx.conf (如果不存在)
NGINX_CONF="${DEPLOY_BASE}/kb-ops-web/nginx.conf"
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
        proxy_pass http://host.docker.internal:8084/kb-ops/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }
}
NGINXEOF
  log_ok "创建默认 nginx.conf: kb-ops-web"
fi

# ====== Step 4: 停止旧服务 ======
log_step 4 5 "停止旧服务"
compose_stop_services "${DEPLOY_BASE}" "${COMPOSE_PROJECT}" "${COMPOSE_FILE}" "${SERVICES[@]}"

# ====== Step 5: 构建并启动 ======
log_step 5 5 "构建并启动"
compose_up_services "${DEPLOY_BASE}" "${COMPOSE_PROJECT}" "${COMPOSE_FILE}" "${SERVICES[@]}"
health_check "${HEALTH_URL}" "${SERVICES[@]}"
prune_images

log_footer "${APP_NAME}" "${TAR_FILE}"
