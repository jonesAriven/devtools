#!/bin/bash
# ============================================================
# mykng 知识库微服务状态查看脚本（SOP附录G要求）
# ============================================================
# 用法:
#   bash scripts/status.sh              # 查看完整状态
#   bash scripts/status.sh -help        # 显示帮助
#
# 输出内容:
#   1. Docker 容器状态（docker ps）
#   2. 宿主机端口监听情况
#   3. 磁盘空间使用
#   4. 内存使用
#   5. Docker 资源占用
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

info() { echo -e "${BLUE}[INFO ]${NC} $1"; }
ok()   { echo -e "  ${GREEN}[✓]${NC} $1"; }
warn() { echo -e "  ${YELLOW}[!]${NC} $1"; }
bad()  { echo -e "  ${RED}[✗]${NC} $1"; }

show_help() {
    cat <<EOF
mykng 知识库微服务状态查看脚本

用法:
  bash scripts/status.sh
  bash scripts/status.sh -help

输出:
  1. Docker 容器状态
  2. 宿主机端口监听（3306/6379/27017/9000/9001/7700/8090）
  3. 磁盘空间使用
  4. 系统内存使用
  5. Docker 容器资源占用
EOF
}

if [ "$1" = "-help" ] || [ "$1" = "--help" ] || [ "$1" = "-h" ]; then
    show_help
    exit 0
fi

echo "============================================================"
echo "  mykng 知识库微服务状态"
echo "  时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo "  项目: $COMPOSE_PROJECT"
echo "============================================================"
echo ""

# 1. 容器状态
info "=== 1. Docker 容器状态 ==="
if docker compose -p "$COMPOSE_PROJECT" -f "$COMPOSE_FILE" ps --format "table {{.Name}}\t{{.Service}}\t{{.Status}}\t{{.Ports}}" 2>/dev/null | grep -q "NAME"; then
    docker compose -p "$COMPOSE_PROJECT" -f "$COMPOSE_FILE" ps --format "table {{.Name}}\t{{.Service}}\t{{.Status}}\t{{.Ports}}"
else
    warn "无运行中的 kb-deploy 容器"
fi
echo ""

# 2. 端口监听
info "=== 2. 宿主机端口监听 ==="
PORTS=(3306 6379 27017 9000 9001 7700 8090)
for port in "${PORTS[@]}"; do
    if ss -tlnp 2>/dev/null | grep -q ":${port} "; then
        ok "端口 ${port} 已监听"
    elif netstat -tlnp 2>/dev/null | grep -q ":${port} "; then
        ok "端口 ${port} 已监听"
    else
        bad "端口 ${port} 未监听"
    fi
done
echo ""

# 3. 磁盘空间
info "=== 3. 磁盘空间使用 ==="
df -h | awk 'NR==1 || /^\/dev\// || /^overlay/'
echo ""
# Docker 数据目录占用
if [ -d /data ]; then
    info "/data 目录占用:"
    du -sh /data/* 2>/dev/null | sort -hr | head -10 || warn "无法统计 /data"
fi
echo ""

# 4. 内存
info "=== 4. 系统内存使用 ==="
free -h 2>/dev/null || warn "free 命令不可用"
echo ""

# 5. Docker 资源占用
info "=== 5. Docker 容器资源占用 ==="
RUNNING_IDS=$(docker compose -p "$COMPOSE_PROJECT" -f "$COMPOSE_FILE" ps -q 2>/dev/null)
if [ -n "$RUNNING_IDS" ]; then
    docker stats --no-stream --format "table {{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}\t{{.NetIO}}\t{{.BlockIO}}" $RUNNING_IDS 2>/dev/null || warn "无法获取 docker stats"
else
    warn "无运行中的容器"
fi
echo ""

# 6. Docker 总览
info "=== 6. Docker 系统总览 ==="
docker system df 2>/dev/null || warn "docker system df 不可用"
echo ""

echo "============================================================"
info "状态查看完成"
echo "============================================================"
