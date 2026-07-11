#!/bin/bash
# ============================================================
# 故障注入: 模拟 MySQL 宕机（SOP附录F）
# ============================================================
# 用法:
#   bash inject-mysql-down.sh                       # 默认停止 30s 后自动恢复
#   bash inject-mysql-down.sh --duration 60         # 停止 60s 后恢复
#   bash inject-mysql-down.sh --no-recover          # 不自动恢复（需手动 start）
#   bash inject-mysql-down.sh -help                 # 显示帮助
#
# 故障效果:
#   - platform-mysql 容器被停止
#   - 微服务的数据库操作将失败（连接池报错）
#   - 验证微服务是否能优雅降级 / 自动重连
# ============================================================

set -e

# ---------- 路径变量化 ----------
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
COMPOSE_PROJECT="kb-deploy"
COMPOSE_FILE="$PROJECT_ROOT/docker-compose.yml"

# ---------- 颜色输出 ----------
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;36m'
NC='\033[0m'

log()  { echo -e "${GREEN}[CHAOS]${NC} $1"; }
info() { echo -e "${BLUE}[INFO ]${NC} $1"; }
warn() { echo -e "${YELLOW}[WARN ]${NC} $1"; }
err()  { echo -e "${RED}[ERROR]${NC} $1" >&2; }

CONTAINER="platform-mysql"
DURATION=30
NO_RECOVER=false

show_help() {
    cat <<EOF
故障注入: 模拟 MySQL 宕机

用法:
  bash inject-mysql-down.sh [options]

参数:
  --duration <s>   故障持续时间（秒），到时自动恢复（默认 30）
  --no-recover     不自动恢复，需手动执行 docker start $CONTAINER
  -help            显示此帮助
EOF
}

while [ $# -gt 0 ]; do
    case "$1" in
        --duration) DURATION="$2"; shift 2 ;;
        --no-recover) NO_RECOVER=true; shift ;;
        -help|--help|-h) show_help; exit 0 ;;
        *) err "未知参数: $1"; show_help; exit 1 ;;
    esac
done

echo "============================================================"
echo "  [CHAOS] 故障注入: MySQL 宕机"
echo "  时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo "  持续: ${DURATION}s  自动恢复: $([ "$NO_RECOVER" = "false" ] && echo "是" || echo "否")"
echo "============================================================"
echo ""

# 检查容器
if ! docker ps --format '{{.Names}}' | grep -q "^${CONTAINER}$"; then
    err "$CONTAINER 未运行，无法注入故障"
    exit 1
fi

# 注入前状态
log "注入前 $CONTAINER 状态:"
docker inspect --format='  状态: {{.State.Status}}, 健康: {{.State.Health.Status}}' "$CONTAINER"
echo ""

# 注入故障
warn ">>> 注入故障: 停止 $CONTAINER ..."
docker stop "$CONTAINER" >/dev/null
log "  ✓ $CONTAINER 已停止"

# 观察微服务反应
info "观察微服务反应（10s）..."
sleep 10
info "微服务容器状态:"
for svc in kb-auth kb-file kb-knowledge kb-intelligence kb-gateway; do
    local_status=$(docker inspect --format='{{.State.Status}}' "$svc" 2>/dev/null || echo "not-found")
    local_health=$(docker inspect --format='{{.State.Health.Status}}' "$svc" 2>/dev/null || echo "no-healthcheck")
    echo "  $svc: status=$local_status, health=$local_health"
done
echo ""

# 等待恢复
if [ "$NO_RECOVER" = "true" ]; then
    warn "未启用自动恢复，请手动执行: docker start $CONTAINER"
    exit 0
fi

info "等待 ${DURATION}s 后自动恢复..."
sleep "$DURATION"

# 恢复
warn ">>> 恢复: 启动 $CONTAINER ..."
docker start "$CONTAINER" >/dev/null
log "  ✓ $CONTAINER 已启动，等待健康..."

elapsed=0
while [ $elapsed -lt 60 ]; do
    health=$(docker inspect --format='{{.State.Health.Status}}' "$CONTAINER" 2>/dev/null || echo "missing")
    if [ "$health" = "healthy" ]; then
        log "  ✓ $CONTAINER 已健康"
        break
    fi
    sleep 3
    elapsed=$((elapsed + 3))
done

echo ""
log "故障注入完成 ✓"
log "请检查微服务是否已自动重连 MySQL（查看日志）"
