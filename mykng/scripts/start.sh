#!/bin/bash
# ============================================================
# mykng 知识库微服务启动脚本（SOP附录G要求）
# ============================================================
# 用法:
#   bash scripts/start.sh              # 启动所有服务
#   bash scripts/start.sh kb-auth      # 启动单个服务
#   bash scripts/start.sh -help        # 显示帮助
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
mykng 知识库微服务启动脚本

用法:
  bash scripts/start.sh [service]

参数:
  service   可选服务名，不指定则启动全部
            可选: mysql / redis / minio / meilisearch / mongodb
                kb-gateway / kb-auth / kb-file / kb-knowledge / kb-intelligence

示例:
  bash scripts/start.sh
  bash scripts/start.sh kb-auth
EOF
}

# .env 检查
if [ ! -f "$PROJECT_ROOT/.env" ]; then
    if [ -f "$PROJECT_ROOT/.env.example" ]; then
        warn ".env 不存在，从 .env.example 复制..."
        cp "$PROJECT_ROOT/.env.example" "$PROJECT_ROOT/.env"
    else
        err "未找到 .env 与 .env.example"
        exit 1
    fi
fi

SERVICE="$1"

if [ "$SERVICE" = "-help" ] || [ "$SERVICE" = "--help" ] || [ "$SERVICE" = "-h" ]; then
    show_help
    exit 0
fi

log "启动 mykng 服务..."
log "  项目: $COMPOSE_PROJECT"
log "  时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo ""

if [ -n "$SERVICE" ]; then
    log "启动单个服务: $SERVICE"
    docker compose -p "$COMPOSE_PROJECT" -f "$COMPOSE_FILE" up -d "$SERVICE"
else
    log "启动所有服务..."
    docker compose -p "$COMPOSE_PROJECT" -f "$COMPOSE_FILE" up -d
fi

sleep 3
echo ""
log "当前服务状态:"
docker compose -p "$COMPOSE_PROJECT" -f "$COMPOSE_FILE" ps
echo ""
info "网关地址: http://localhost:8090${KB_CONTEXT:-/kb}"
info "查看日志: bash scripts/deploy.sh logs [service]"
info "健康检查: bash scripts/health-check.sh"
