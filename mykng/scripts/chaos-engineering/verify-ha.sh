#!/bin/bash
# ============================================================
# 高可用综合验证脚本（SOP附录F）
# ============================================================
# 用法:
#   bash verify-ha.sh                    # 执行完整混沌演练
#   bash verify-ha.sh --recover          # 仅恢复所有服务
#   bash verify-ha.sh --skip-oom         # 跳过 OOM 测试
#   bash verify-ha.sh --skip-network     # 跳过网络延迟测试
#   bash verify-ha.sh -help              # 显示帮助
#
# 演练流程:
#   1. 健康检查基线
#   2. 注入 MySQL 宕机 → 等待恢复 → 健康检查
#   3. 注入 Redis 宕机 → 等待恢复 → 健康检查
#   4. 注入网络延迟 → 等待恢复 → 健康检查
#   5. 注入 OOM → 等待恢复 → 健康检查
#   6. 输出综合报告
# ============================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
REPORT_DIR="/data/logs/chaos-reports"

mkdir -p "$REPORT_DIR"

# 从 .env 读取密码
MYSQL_PASS="${MYSQL_ROOT_PASSWORD:-kb123456}"
if [ -f "$PROJECT_ROOT/.env" ]; then
    # shellcheck disable=SC1091
    . "$PROJECT_ROOT/.env"
    MYSQL_PASS="${MYSQL_ROOT_PASSWORD:-kb123456}"
fi

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

TS=$(date +%Y%m%d_%H%M%S)
REPORT_FILE="$REPORT_DIR/chaos-${TS}.log"

PASS=0
FAIL=0
ok()   { echo -e "  ${GREEN}[✓ PASS]${NC} $1"; PASS=$((PASS + 1)); }
bad()  { echo -e "  ${RED}[✗ FAIL]${NC} $1"; FAIL=$((FAIL + 1)); }

SKIP_OOM=false
SKIP_NETWORK=false
RECOVER_ONLY=false

show_help() {
    cat <<EOF
mykng 高可用综合验证脚本（SOP附录F）

用法:
  bash verify-ha.sh [options]

参数:
  --recover          仅恢复所有服务（停止的启动、tc 规则清除）
  --skip-oom         跳过 OOM 测试
  --skip-network     跳过网络延迟测试
  -help              显示此帮助

报告输出: $REPORT_DIR/chaos-<ts>.log
EOF
}

while [ $# -gt 0 ]; do
    case "$1" in
        --recover) RECOVER_ONLY=true; shift ;;
        --skip-oom) SKIP_OOM=true; shift ;;
        --skip-network) SKIP_NETWORK=true; shift ;;
        -help|--help|-h) show_help; exit 0 ;;
        *) err "未知参数: $1"; show_help; exit 1 ;;
    esac
done

# ---------- 写报告 ----------
write_report() {
    echo "$1" | tee -a "$REPORT_FILE"
}

# ---------- 健康检查 ----------
health_check() {
    local label="$1"
    info "[$label] 健康检查..."
    local all_ok=true
    for svc in kb-mysql kb-redis kb-mongo kb-minio kb-meilisearch kb-gateway kb-auth kb-file kb-knowledge kb-ops kb-intelligence; do
        local status health
        status=$(docker inspect --format='{{.State.Status}}' "$svc" 2>/dev/null || echo "not-found")
        health=$(docker inspect --format='{{.State.Health.Status}}' "$svc" 2>/dev/null || echo "no-healthcheck")
        if [ "$status" = "running" ] && { [ "$health" = "healthy" ] || [ "$health" = "no-healthcheck" ]; }; then
            ok "$svc: $status/$health"
        else
            bad "$svc: $status/$health"
            all_ok=false
        fi
    done
    if [ "$all_ok" = "true" ]; then
        write_report "[$(date '+%H:%M:%S')] [$label] 健康检查通过"
        return 0
    else
        write_report "[$(date '+%H:%M:%S')] [$label] 健康检查存在异常"
        return 1
    fi
}

# ---------- 恢复所有服务 ----------
recover_all() {
    warn ">>> 恢复所有服务..."
    # 启动所有停止的容器
    for svc in kb-mysql kb-redis kb-mongo kb-minio kb-meilisearch kb-gateway kb-auth kb-file kb-knowledge kb-ops kb-intelligence; do
        local status
        status=$(docker inspect --format='{{.State.Status}}' "$svc" 2>/dev/null || echo "not-found")
        if [ "$status" != "running" ]; then
            info "  启动 $svc ..."
            docker start "$svc" >/dev/null 2>&1 || true
        fi
    done

    # 清除所有 tc 规则
    for svc in kb-gateway kb-auth kb-file kb-knowledge kb-ops kb-intelligence; do
        docker exec "$svc" tc qdisc del dev eth0 root 2>/dev/null || true
    done

    info "  等待 30s 让服务恢复..."
    sleep 30
    health_check "恢复后"
}

# ---------- 主流程 ----------
{
    echo "============================================================"
    echo "  mykng 高可用综合验证（混沌演练）"
    echo "  时间: $(date '+%Y-%m-%d %H:%M:%S')"
    echo "  报告: $REPORT_FILE"
    echo "  跳过 OOM: $SKIP_OOM  跳过网络: $SKIP_NETWORK"
    echo "============================================================"
    echo ""
} | tee "$REPORT_FILE"

# 仅恢复模式
if [ "$RECOVER_ONLY" = "true" ]; then
    recover_all
    exit 0
fi

# 0. 基线健康检查
write_report ""
write_report "========== [0/4] 基线健康检查 =========="
health_check "基线" || {
    err "基线健康检查未通过，请先确保所有服务正常运行"
    exit 1
}
write_report ""

# 1. MySQL 宕机测试
write_report "========== [1/4] MySQL 宕机测试 =========="
log "[1/4] 注入 MySQL 宕机（持续 30s）..."
if bash "$SCRIPT_DIR/inject-mysql-down.sh" --duration 30 2>&1 | tee -a "$REPORT_FILE"; then
    ok "MySQL 宕机测试通过"
else
    bad "MySQL 宕机测试失败"
fi
write_report ""

# 2. Redis 宕机测试
write_report "========== [2/4] Redis 宕机测试 =========="
log "[2/4] 注入 Redis 宕机（持续 30s）..."
if bash "$SCRIPT_DIR/inject-redis-down.sh" --duration 30 2>&1 | tee -a "$REPORT_FILE"; then
    ok "Redis 宕机测试通过"
else
    bad "Redis 宕机测试失败"
fi
write_report ""

# 3. 网络延迟测试
if [ "$SKIP_NETWORK" = "false" ]; then
    write_report "========== [3/4] 网络延迟测试 =========="
    log "[3/4] 注入网络延迟（kb-gateway 200ms，持续 30s）..."
    if bash "$SCRIPT_DIR/inject-network-delay.sh" --target kb-gateway --delay 200ms --duration 30 2>&1 | tee -a "$REPORT_FILE"; then
        ok "网络延迟测试通过"
    else
        bad "网络延迟测试失败"
    fi
    write_report ""
else
    write_report "========== [3/4] 网络延迟测试（已跳过） =========="
fi

# 4. OOM 测试
if [ "$SKIP_OOM" = "false" ]; then
    write_report "========== [4/4] OOM 测试 =========="
    log "[4/4] 注入 OOM（kb-file，内存限制 128m）..."
    if bash "$SCRIPT_DIR/inject-oom.sh" --target kb-file --mem 128m 2>&1 | tee -a "$REPORT_FILE"; then
        ok "OOM 测试通过"
    else
        bad "OOM 测试失败"
    fi
    write_report ""
else
    write_report "========== [4/4] OOM 测试（已跳过） =========="
fi

# 最终健康检查
write_report "========== 最终健康检查 =========="
health_check "最终"
write_report ""

# 汇总
write_report "============================================================"
write_report "  混沌演练汇总: ${GREEN}通过 $PASS${NC} / ${RED}失败 $FAIL${NC}"
write_report "  报告: $REPORT_FILE"
write_report "============================================================"

echo ""
log "混沌演练完成 ✓"
log "报告已保存: $REPORT_FILE"

[ $FAIL -gt 0 ] && exit 1
exit 0
