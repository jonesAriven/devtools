#!/bin/bash
# ============================================================
# deploy-mykng.sh - mykng 知识库微服务部署 (4个Java微服务)
# ============================================================
# 用法: bash deploy-mykng.sh <tar.gz文件名>
# 示例: bash deploy-mykng.sh mykng-latest.tar.gz
#
# 部署的服务: kb-gateway, kb-file, kb-knowledge, kb-intelligence
#            （auth-center 已于 2026-09-11 拆到独立流水线，见 auth-center 仓库 .woodpecker.yml，
#              ADR-2026-09-10 §13.5/§14）
# Compose:    docker-compose.app.yml (project: kb-app)
# 前置条件:   platform 全局基础设施层已启动
# 隔离性:     只重建这4个服务，不影响kb-ops 和前端容器
# ============================================================
set -euo pipefail
source /mnt/shared/woodScript/lib-deploy.sh

# ====== 配置 ======
TAR_FILE="${1:?missing param: usage deploy-mykng.sh tar.gz}"
COMPOSE_PROJECT="kb-app"
# compose 文件由 sync-ci-scripts 统一同步到 /mnt/shared
COMPOSE_FILE="/mnt/shared/mykng/docker/docker-compose.app.yml"
SERVICES=("kb-gateway" "kb-file" "kb-knowledge" "kb-intelligence")
HEALTH_URL="http://localhost:8090/actuator/health"
APP_NAME="mykng"

# JAR 文件名映射 (兼容 Alpine/BusyBox，不使用关联数组)
get_jar_name() {
  case "$1" in
    kb-gateway)     echo "kb-gateway.jar" ;;
    kb-file)        echo "kb-file.jar" ;;
    kb-knowledge)   echo "kb-knowledge.jar" ;;
    kb-intelligence) echo "kb-intelligence.jar" ;;
    *)              echo "" ;;
  esac
}

log_header "${APP_NAME}" "${TAR_FILE}"

# (Step 0.5 已移除) auth-center 于 2026-09-11 拆到独立流水线：
#   - 流水线定义: auth-center 仓库 .woodpecker.yml（CI 构建产物 + 独立部署 + 8085 健康检查）
#   - 部署脚本:   auth-center 仓库 scripts/deploy.sh（落在 /mnt/shared/auth-center-build/）
#   - compose 里 auth-center 的 build context 已改为 /mnt/shared/auth-center-build
#   mykng 部署不再触碰 auth-center，两边彻底解耦。

# ====== Step 1: 验证产物 ======
log_step 1 6 "验证产物"
verify_artifact "${TAR_FILE}"

# ====== Step 2: 解压 & 分发 JAR ======
log_step 2 6 "解压 & 分发 JAR"
mkdir -p "${DEPLOY_BASE}/jars-mykng"
extract_artifact "${TAR_FILE}" "${DEPLOY_BASE}/jars-mykng"

for module in "${SERVICES[@]}"; do
  jar_file="$(get_jar_name "${module}")"
  if [ -z "${jar_file}" ]; then
    log_warn "${module}: 未知的JAR映射"
    continue
  fi
  src="${DEPLOY_BASE}/jars-mykng/${jar_file}"
  target_dir="${GIT_REPO}/mykng/${module}/target"
  if [ -f "${src}" ]; then
    mkdir -p "${target_dir}"
    cp "${src}" "${target_dir}/"
    log_ok "${module} - ${jar_file}"
  else
    log_warn "${module}: ${jar_file} 不存在"
  fi
done

# ====== Step 3: 同步 compose 文件 & 检查网络 ======
log_step 3 6 "环境准备"
ensure_platform

# ====== Step 4: 停止旧服务(只停4个，不影响其他 ======
# 2026-09-11 起 auth-center 已拆到独立流水线（auth-center 仓库自带 .woodpecker.yml），
# 不再随 mykng 部署（见 ADR-2026-09-10 §13.5/§14）。此处只管 4 个 mykng 服务。
log_step 4 6 "停止旧服务"
compose_stop_services "${DEPLOY_BASE}" "${COMPOSE_PROJECT}" "${COMPOSE_FILE}" "${SERVICES[@]}"

# ====== Step 5: 构建并启动 ======
log_step 5 6 "构建并启动新服务"
compose_up_services "${DEPLOY_BASE}" "${COMPOSE_PROJECT}" "${COMPOSE_FILE}" "${SERVICES[@]}"

# ====== Step 6: 健康检查 & 清理 ======
log_step 6 6 "健康检查 & 清理"
health_check "${HEALTH_URL}" "${SERVICES[@]}"
prune_images

log_footer "${APP_NAME}" "${TAR_FILE}" "  Gateway: http://localhost:8090"
