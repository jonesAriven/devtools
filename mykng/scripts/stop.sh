#!/bin/bash
# ============================================================
# mykng 知识库微服务停止脚本（SOP附录G要求）
# ============================================================
# 用法:
#   bash scripts/stop.sh              # 停止所有服务（保留容器）
#   bash scripts/stop.sh kb-auth      # 停止单个服务
#   bash scripts/stop.sh --down       # 停止并移除容器（保留数据卷）
#   bash scripts/stop.sh -help        # 显示帮助
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
mykng 知识库微服务停止脚本

用法:
  bash scripts/stop.sh [options] [service]

参数:
  (无)         停止所有容器（容器保留，可重新 start）
  --down       停止并移除所有容器（数据卷保留）
  --down -v    停止并移除所有容器和数据卷（危险！）
  service      可选服务名，仅停止单个服务

示例:
  bash scripts/stop.sh
  bash scripts/stop.sh kb-auth
  bash scripts/stop.sh --down
EOF
}

# 参数解析
REMOVE=false
REMOVE_VOLUMES=false
SERVICE=""

for arg in "$@"; do
    case "$arg" in
        -help|--help|-h) show_help; exit 0 ;;
        --down)          REMOVE=true ;;
        -v)              REMOVE_VOLUMES=true ;;
        *)               SERVICE="$arg" ;;
    esac
done

log "停止 mykng 服务..."
log "  项目: $COMPOSE_PROJECT"
log "  时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo ""

if [ "$REMOVE_VOLUMES" = "true" ]; then
    warn "⚠ 即将停止并删除所有容器和数据卷（不可恢复）！"
    read -r -p "确认？(yes/no): " confirm
    if [ "$confirm" != "yes" ]; then
        log "已取消"
        exit 0
    fi
    docker compose -p "$COMPOSE_PROJECT" -f "$COMPOSE_FILE" down -v
    log "已停止并删除所有容器和数据卷"
elif [ "$REMOVE" = "true" ]; then
    if [ -n "$SERVICE" ]; then
        docker compose -p "$COMPOSE_PROJECT" -f "$COMPOSE_FILE" stop "$SERVICE"
        docker compose -p "$COMPOSE_PROJECT" -f "$COMPOSE_FILE" rm -f "$SERVICE"
        log "已停止并移除 $SERVICE"
    else
        docker compose -p "$COMPOSE_PROJECT" -f "$COMPOSE_FILE" down
        log "已停止并移除所有容器（数据卷保留）"
    fi
else
    if [ -n "$SERVICE" ]; then
        docker compose -p "$COMPOSE_PROJECT" -f "$COMPOSE_FILE" stop "$SERVICE"
        log "已停止 $SERVICE（容器保留）"
    else
        docker compose -p "$COMPOSE_PROJECT" -f "$COMPOSE_FILE" stop
        log "已停止所有服务（容器保留，可 start 重新启动）"
    fi
fi

echo ""
log "当前服务状态:"
docker compose -p "$COMPOSE_PROJECT" -f "$COMPOSE_FILE" ps 2>/dev/null || echo "  无运行中的容器"
