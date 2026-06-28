#!/bin/bash
# ============================================================
# 故障注入: 模拟网络延迟（SOP附录F）
# ============================================================
# 用法:
#   bash inject-network-delay.sh                                    # 默认对 kb-gateway 注入 200ms 延迟 30s
#   bash inject-network-delay.sh --target kb-auth --delay 500ms     # 指定容器和延迟
#   bash inject-network-delay.sh --duration 60                      # 持续 60s
#   bash inject-network-delay.sh --jitter 100ms                     # 添加抖动
#   bash inject-network-delay.sh -help                              # 显示帮助
#
# 实现方式:
#   使用 tc qdisc netem 在容器内 eth0 接口注入延迟
#   要求容器具有 NET_ADMIN capability（默认 Docker 容器具备）
# ============================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;36m'
NC='\033[0m'

log()  { echo -e "${GREEN}[CHAOS]${NC} $1"; }
info() { echo -e "${BLUE}[INFO ]${NC} $1"; }
warn() { echo -e "${YELLOW}[WARN ]${NC} $1"; }
err()  { echo -e "${RED}[ERROR]${NC} $1" >&2; }

TARGET="kb-gateway"
DELAY="200ms"
JITTER=""
DURATION=30

show_help() {
    cat <<EOF
故障注入: 模拟网络延迟

用法:
  bash inject-network-delay.sh [options]

参数:
  --target <name>    目标容器名（默认: kb-gateway）
  --delay <time>     延迟时间，如 200ms / 1s（默认: 200ms）
  --jitter <time>    延迟抖动，如 100ms（可选）
  --duration <s>     持续时间（秒），到时自动清除（默认 30）
  -help              显示此帮助

示例:
  bash inject-network-delay.sh --target kb-auth --delay 500ms --jitter 100ms
EOF
}

while [ $# -gt 0 ]; do
    case "$1" in
        --target) TARGET="$2"; shift 2 ;;
        --delay) DELAY="$2"; shift 2 ;;
        --jitter) JITTER="$2"; shift 2 ;;
        --duration) DURATION="$2"; shift 2 ;;
        -help|--help|-h) show_help; exit 0 ;;
        *) err "未知参数: $1"; show_help; exit 1 ;;
    esac
done

echo "============================================================"
echo "  [CHAOS] 故障注入: 网络延迟"
echo "  时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo "  目标: $TARGET  延迟: $DELAY  抖动: ${JITTER:-无}  持续: ${DURATION}s"
echo "============================================================"
echo ""

# 检查容器
if ! docker ps --format '{{.Names}}' | grep -q "^${TARGET}$"; then
    err "$TARGET 未运行"
    exit 1
fi

# 检查 tc 命令
if ! docker exec "$TARGET" which tc >/dev/null 2>&1; then
    warn "$TARGET 容器内无 tc 命令，尝试安装 iproute2..."
    # alpine 系镜像
    if docker exec "$TARGET" which apk >/dev/null 2>&1; then
        docker exec "$TARGET" apk add --no-cache iproute2 2>&1 | tail -3
    elif docker exec "$TARGET" which apt-get >/dev/null 2>&1; then
        docker exec "$TARGET" bash -c "apt-get update -qq && apt-get install -y -qq iproute2" 2>&1 | tail -3
    else
        err "无法自动安装 tc，请手动在容器内安装 iproute2"
        exit 1
    fi
fi

# 清理已有规则（避免叠加）
warn ">>> 清理已有 tc 规则..."
docker exec "$TARGET" tc qdisc del dev eth0 root 2>/dev/null || true

# 构造 netem 参数
NETEM_ARGS="delay $DELAY"
if [ -n "$JITTER" ]; then
    NETEM_ARGS="delay $DELAY $JITTER"
fi

# 注入延迟
warn ">>> 注入故障: $TARGET eth0 网络延迟 $DELAY ${JITTER:-}"
if docker exec "$TARGET" tc qdisc add dev eth0 root netem $NETEM_ARGS 2>&1; then
    log "  ✓ 网络延迟已注入"
else
    err "  ✗ 注入失败（可能需要 NET_ADMIN capability）"
    err "  解决: 在 docker-compose.yml 中为 $TARGET 添加 cap_add: [NET_ADMIN]"
    exit 1
fi

# 验证
info "当前 tc 规则:"
docker exec "$TARGET" tc qdisc show dev eth0
echo ""

# 观察期
info "观察 ${DURATION}s..."
sleep "$DURATION"

# 清除规则
warn ">>> 恢复: 清除 tc 规则..."
docker exec "$TARGET" tc qdisc del dev eth0 root 2>/dev/null || true
log "  ✓ tc 规则已清除"

info "当前 tc 规则:"
docker exec "$TARGET" tc qdisc show dev eth0
echo ""
log "故障注入完成 ✓"
