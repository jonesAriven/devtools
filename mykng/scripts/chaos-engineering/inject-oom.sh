#!/bin/bash
# ============================================================
# 故障注入: 模拟 OOM（SOP附录F）
# ============================================================
# 用法:
#   bash inject-oom.sh                              # 默认对 kb-file 注入 OOM
#   bash inject-oom.sh --target kb-intelligence     # 指定目标容器
#   bash inject-oom.sh --mem 256m                   # 限制内存为 256MB
#   bash inject-oom.sh -help                        # 显示帮助
#
# 实现方式:
#   1. 通过 docker update 动态降低容器内存上限
#   2. 在容器内执行 stress / tail /dev/zero 触发内存压力
#   3. 容器被 OOM Killer 杀死后，因 restart: unless-stopped 自动重启
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

TARGET="kb-file"
LIMIT_MEM="128m"
WAIT_RECOVER=60

show_help() {
    cat <<EOF
故障注入: 模拟 OOM

用法:
  bash inject-oom.sh [options]

参数:
  --target <name>    目标容器名（默认: kb-file）
  --mem <size>       限制内存上限（默认: 128m），将触发 OOM
  --wait <s>         等待容器自动重启的最长时间（默认: 60s）
  -help              显示此帮助

示例:
  bash inject-oom.sh --target kb-intelligence --mem 256m
EOF
}

while [ $# -gt 0 ]; do
    case "$1" in
        --target) TARGET="$2"; shift 2 ;;
        --mem) LIMIT_MEM="$2"; shift 2 ;;
        --wait) WAIT_RECOVER="$2"; shift 2 ;;
        -help|--help|-h) show_help; exit 0 ;;
        *) err "未知参数: $1"; show_help; exit 1 ;;
    esac
done

echo "============================================================"
echo "  [CHAOS] 故障注入: OOM"
echo "  时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo "  目标: $TARGET  限制内存: $LIMIT_MEM  等待恢复: ${WAIT_RECOVER}s"
echo "============================================================"
echo ""

if ! docker ps --format '{{.Names}}' | grep -q "^${TARGET}$"; then
    err "$TARGET 未运行"
    exit 1
fi

# 记录原内存上限
ORIG_MEM=$(docker inspect --format='{{.HostConfig.Memory}}' "$TARGET" 2>/dev/null || echo "0")
log "原始内存上限: ${ORIG_MEM} bytes"
log "原始状态: $(docker inspect --format='{{.State.Status}}' "$TARGET")"
echo ""

# 1. 降低内存上限
warn ">>> 步骤 1: 降低 $TARGET 内存上限到 $LIMIT_MEM ..."
if docker update --memory="$LIMIT_MEM" --memory-swap="$LIMIT_MEM" "$TARGET" >/dev/null 2>&1; then
    log "  ✓ 内存上限已调整"
else
    err "  ✗ 调整内存失败"
    exit 1
fi

# 2. 触发内存压力
warn ">>> 步骤 2: 在 $TARGET 内触发内存压力..."
# 优先使用 stress，没有则用 tail /dev/zero
if docker exec "$TARGET" which stress >/dev/null 2>&1; then
    info "  使用 stress 触发..."
    docker exec -d "$TARGET" stress --vm 1 --vm-bytes "${LIMIT_MEM}" --timeout 30s 2>&1 || true
else
    info "  使用 tail /dev/zero 触发（无需安装额外工具）..."
    docker exec -d "$TARGET" sh -c "tail /dev/zero > /tmp/oom" 2>&1 || true
fi

# 3. 等待 OOM 发生
info ">>> 步骤 3: 等待 OOM 发生（最多 30s）..."
oom_occurred=false
for i in $(seq 1 30); do
    sleep 1
    local_status=$(docker inspect --format='{{.State.Status}}' "$TARGET" 2>/dev/null || echo "missing")
    local_oom=$(docker inspect --format='{{.State.OOMKilled}}' "$TARGET" 2>/dev/null || echo "false")
    if [ "$local_oom" = "true" ] || [ "$local_status" = "exited" ]; then
        warn "  [$i s] OOM 已触发 (status=$local_status, OOMKilled=$local_oom)"
        oom_occurred=true
        break
    fi
    printf "  [%d s] 等待 OOM... (status=%s)\r" "$i" "$local_status"
done
echo ""

if [ "$oom_occurred" = "false" ]; then
    warn "  30s 内未触发 OOM，可能内存上限仍过高，或压力工具未生效"
fi

# 4. 等待自动重启
info ">>> 步骤 4: 等待 Docker 自动重启 $TARGET（最多 ${WAIT_RECOVER}s）..."
recovered=false
for i in $(seq 1 "$WAIT_RECOVER"); do
    sleep 1
    local_status=$(docker inspect --format='{{.State.Status}}' "$TARGET" 2>/dev/null || echo "missing")
    if [ "$local_status" = "running" ]; then
        local_health=$(docker inspect --format='{{.State.Health.Status}}' "$TARGET" 2>/dev/null || echo "no-healthcheck")
        if [ "$local_health" = "healthy" ] || [ "$local_health" = "no-healthcheck" ]; then
            log "  [$i s] $TARGET 已恢复运行 (status=$local_status, health=$local_health)"
            recovered=true
            break
        fi
    fi
    printf "  [%d s] 等待恢复... (status=%s)\r" "$i" "$local_status"
done
echo ""

# 5. 恢复内存上限
warn ">>> 步骤 5: 恢复 $TARGET 原始内存上限..."
if [ "$ORIG_MEM" != "0" ] && [ "$ORIG_MEM" != "" ]; then
    # 转回 MB
    orig_mb=$((ORIG_MEM / 1024 / 1024))
    if [ "$orig_mb" -gt 0 ]; then
        docker update --memory="${orig_mb}m" --memory-swap="${orig_mb}m" "$TARGET" >/dev/null 2>&1 || warn "  恢复内存上限失败"
    fi
fi
log "  ✓ 内存上限已恢复"

# 清理临时文件
docker exec "$TARGET" sh -c "rm -f /tmp/oom" 2>/dev/null || true

echo ""
if [ "$recovered" = "true" ]; then
    log "故障注入完成 ✓  容器已自动恢复"
else
    err "故障注入完成  ⚠  容器未自动恢复，请手动检查: docker logs $TARGET"
    exit 1
fi
