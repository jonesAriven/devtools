#!/bin/bash
# ============================================================
# deploy-mykng.sh �?mykng 知识库微服务部署 (5个Java微服�?
# ============================================================
# 用法: bash deploy-mykng.sh <tar.gz文件�?
# 示例: bash deploy-mykng.sh mykng-latest.tar.gz
#
# 部署的服�? kb-gateway, kb-auth, kb-file, kb-knowledge, kb-intelligence
# Compose:    docker-compose.app.yml (project: kb-app)
# 前置条件:   platform 全局基础设施层已启动
# 隔离�?     只重建这5个服务，不影�?kb-ops 和前端容�?# ============================================================
set -euo pipefail
source /mnt/shared/woodDeploy/woodScript/lib-deploy.sh

# ====== 配置 ======
TAR_FILE="${1:?missing param: usage deploy-mykng.sh tar.gz}"
COMPOSE_PROJECT="kb-app"
COMPOSE_FILE="docker-compose.app.yml"
SERVICES=("kb-gateway" "kb-auth" "kb-file" "kb-knowledge" "kb-intelligence")
HEALTH_URL="http://localhost:8090/actuator/health"
APP_NAME="mykng"

# JAR 文件名映�?declare -A JAR_MAP=(
  ["kb-gateway"]="kb-gateway.jar"
  ["kb-auth"]="kb-auth.jar"
  ["kb-file"]="kb-file.jar"
  ["kb-knowledge"]="kb-knowledge.jar"
  ["kb-intelligence"]="kb-intelligence.jar"
)

log_header "${APP_NAME}" "${TAR_FILE}"

# ====== Step 1: 验证产物 ======
log_step 1 6 "验证产物"
verify_artifact "${TAR_FILE}"

# ====== Step 2: 解压 & 分发 JAR ======
log_step 2 6 "解压 & 分发 JAR"
mkdir -p "${DEPLOY_BASE}/jars-mykng"
extract_artifact "${TAR_FILE}" "${DEPLOY_BASE}/jars-mykng"

for module in "${!JAR_MAP[@]}"; do
  jar_file="${JAR_MAP[${module}]}"
  src="${DEPLOY_BASE}/jars-mykng/${jar_file}"
  target_dir="${GIT_REPO}/mykng/${module}/target"
  if [ -f "${src}" ]; then
    mkdir -p "${target_dir}"
    cp "${src}" "${target_dir}/"
    log_ok "${module} �?${jar_file}"
  else
    log_warn "${module}: ${jar_file} 不存�?
  fi
done

# ====== Step 3: 同步 compose 文件 & 检查网�?======
log_step 3 6 "环境准备"
sync_compose_files
ensure_platform

# ====== Step 4: 停止旧服�?(只停�?个，不影响其�? ======
log_step 4 6 "停止旧服�?
compose_stop_services "${DEPLOY_BASE}" "${COMPOSE_PROJECT}" "${COMPOSE_FILE}" "${SERVICES[@]}"

# ====== Step 5: 构建并启�?======
log_step 5 6 "构建并启动新服务"
compose_up_services "${DEPLOY_BASE}" "${COMPOSE_PROJECT}" "${COMPOSE_FILE}" "${SERVICES[@]}"

# ====== Step 6: 健康检�?& 清理 ======
log_step 6 6 "健康检�?& 清理"
health_check "${HEALTH_URL}" "${SERVICES[@]}"
prune_images

log_footer "${APP_NAME}" "${TAR_FILE}" "  Gateway: http://localhost:8090"
