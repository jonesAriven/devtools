#!/bin/bash
# ============================================================
# deploy-kb-web.sh — 前端容器层部署 (3个Nginx容器)
# ============================================================
# 用法: bash deploy-kb-web.sh <tar.gz文件名>
# 示例: bash deploy-kb-web.sh web-latest.tar.gz
#
# 部署的服务: kb-web, kb-ops-web, infra-monitor-web
# Compose:    docker-compose.web.yml (project: kb-web)
# 前置条件:   无 (前端容器独立运行，通过 Nginx 反代后端)
# 隔离性:     只影响前端容器，不影响后端微服务
# ============================================================
set -euo pipefail
source /mnt/shared/devtools/ci/lib-deploy.sh

# ====== 配置 ======
TAR_FILE="${1:?❌ 缺少参数! 用法: $0 <tar.gz文件名>}"
COMPOSE_PROJECT="kb-web"
COMPOSE_FILE="docker-compose.web.yml"
SERVICES=("kb-web" "kb-ops-web" "infra-monitor-web")
HEALTH_URL="http://localhost:8091/health"
APP_NAME="🌐 前端容器层"

log_header "${APP_NAME}" "${TAR_FILE}"

# ====== Step 1: 验证产物 ======
log_step 1 6 "验证产物"
verify_artifact "${TAR_FILE}"

# ====== Step 2: 解压 & 分发 dist ======
log_step 2 6 "解压 & 分发前端产物"
mkdir -p "${DEPLOY_BASE}/web-tmp"
extract_artifact "${TAR_FILE}" "${DEPLOY_BASE}/web-tmp"

# 分发到各前端目录
# kb-ops-web
if [ -d "${DEPLOY_BASE}/web-tmp/kb-ops-web" ]; then
  mkdir -p "${DEPLOY_BASE}/kb-ops-web/dist"
  rm -rf "${DEPLOY_BASE}/kb-ops-web/dist"/*
  cp -r "${DEPLOY_BASE}/web-tmp/kb-ops-web/"* "${DEPLOY_BASE}/kb-ops-web/dist/"
  log_ok "kb-ops-web dist 已更新"
fi

# infra-monitor-web
if [ -d "${DEPLOY_BASE}/web-tmp/infra-monitor-web" ]; then
  mkdir -p "${DEPLOY_BASE}/infra-monitor-web/dist"
  rm -rf "${DEPLOY_BASE}/infra-monitor-web/dist"/*
  cp -r "${DEPLOY_BASE}/web-tmp/infra-monitor-web/"* "${DEPLOY_BASE}/infra-monitor-web/dist/"
  log_ok "infra-monitor-web dist 已更新"
fi

# kb-web (无CI构建，保留现有dist)
if [ -d "${DEPLOY_BASE}/web-tmp/kb-web" ]; then
  mkdir -p "${DEPLOY_BASE}/kb-web/dist"
  rm -rf "${DEPLOY_BASE}/kb-web/dist"/*
  cp -r "${DEPLOY_BASE}/web-tmp/kb-web/"* "${DEPLOY_BASE}/kb-web/dist/"
  log_ok "kb-web dist 已更新"
else
  log_info "kb-web 无新产物，保留现有 dist (需手动更新)"
fi

# 清理解压临时目录
rm -rf "${DEPLOY_BASE}/web-tmp"

# ====== Step 3: 同步 compose 文件 & 确保 nginx.conf ======
log_step 3 6 "环境准备"
sync_compose_files

# 确保各前端目录有 dist
for web_dir in kb-web kb-ops-web infra-monitor-web; do
  mkdir -p "${DEPLOY_BASE}/${web_dir}/dist"
  touch "${DEPLOY_BASE}/${web_dir}/dist/.keep"
done

# 创建默认 nginx.conf (如果不存在)
ensure_nginx_config() {
  local web_dir="$1"
  local proxy_target="$2"
  local conf_file="${DEPLOY_BASE}/${web_dir}/nginx.conf"
  if [ ! -f "${conf_file}" ]; then
    mkdir -p "${DEPLOY_BASE}/${web_dir}"
    cat > "${conf_file}" << 'NGINXEOF'
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
        proxy_pass PROXY_TARGET;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }
}
NGINXEOF
    sed -i "s|PROXY_TARGET|${proxy_target}|" "${conf_file}"
    log_ok "创建默认 nginx.conf: ${web_dir} -> ${proxy_target}"
  fi
}

ensure_nginx_config "kb-web"              "http://host.docker.internal:8090/"
ensure_nginx_config "kb-ops-web"          "http://host.docker.internal:8084/kb-ops/"
ensure_nginx_config "infra-monitor-web"   "http://host.docker.internal:8088/infra/"

# ====== Step 4: 停止旧服务 ======
log_step 4 6 "停止旧服务"
compose_down_all "${DEPLOY_BASE}" "${COMPOSE_PROJECT}" "${COMPOSE_FILE}"

# ====== Step 5: 构建并启动 ======
log_step 5 6 "构建并启动前端容器"
compose_up_all "${DEPLOY_BASE}" "${COMPOSE_PROJECT}" "${COMPOSE_FILE}"

# ====== Step 6: 健康检查 & 清理 ======
log_step 6 6 "健康检查 & 清理"
health_check "${HEALTH_URL}" "${SERVICES[@]}"
prune_images

log_footer "${APP_NAME}" "${TAR_FILE}" \
  "  kb-web:       http://localhost:8091
  kb-ops-web:    http://localhost:8093
  infra-monitor: http://localhost:8094"
