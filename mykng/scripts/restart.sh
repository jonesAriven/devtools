#!/bin/bash
# ============================================================
# mykng 知识库微服务重启脚本（SOP附录G要求）
# ============================================================
# 用法:
#   bash scripts/restart.sh              # 重启所有服务
#   bash scripts/restart.sh kb-auth      # 重启单个服务
#   bash scripts/restart.sh -help        # 显示帮助
# ============================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
COMPOSE_PROJECT="kb-deploy"
COMPOSE_FILE="$PROJECT_ROOT/docker-compose.yml"

cd "$PROJECT_ROOT"

# ---------- 颜色输出 ----------
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;36m'
NC='\033[0m'

log()  { echo -e "${GREEN}[INFO ]${NC} $1"; }
info() { echo -e "${BLUE}[INFO ]${NC} $1"; }
warn() { echo -e "${YELLOW}[WARN ]${NC} $1"; }
err()  { echo -e "${RED}[ERROR]${NC} $1" >&2; }

show_help() {
    cat <<EOF
mykng 知识库微服务重启脚本

用法:
  bash scripts/restart.sh [service]

参数:
  service   可选服务名，不指定则重启全部
            可选: mysql / redis / minio / meilisearch / mongodb
                 kb-gateway / kb-auth / kb-file / kb-knowledge / kb-ops / kb-intelligence

示例:
  bash scripts/restart.sh
  bash scripts/restart.sh kb-auth
EOF
}

SERVICE="$1"

if [ "$SERVICE" = "-help" ] || [ "$SERVICE" = "--help" ] || [ "$SERVICE" = "-h" ]; then
    show_help
    exit 0
fi

log "重启 mykng 服务..."
log "  项目: $COMPOSE_PROJECT"
log "  时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo ""

if [ -n "$SERVICE" ]; then
    log "重启单个服务: $SERVICE"
    docker compose -p "$COMPOSE_PROJECT" -f "$COMPOSE_FILE" restart "$SERVICE"
else
    log "重启所有服务..."
    docker compose -p "$COMPOSE_PROJECT" -f "$COMPOSE_FILE" restart
fi

# 等待服务就绪
sleep 5

echo ""
log "当前服务状态:"
docker compose -p "$COMPOSE_PROJECT" -f "$COMPOSE_FILE" ps
echo ""
info "网关地址: http://localhost:8090${KB_CONTEXT:-/kb}"
info "建议执行健康检查: bash scripts/health-check.sh"
