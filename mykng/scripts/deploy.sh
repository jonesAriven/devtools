#!/bin/bash
# ============================================================
# mykng 知识库微服务一键部署脚本（SOP附录G要求）
# ============================================================
# 用法（在项目根目录执行）:
#   bash scripts/deploy.sh build      # 构建所有镜像
#   bash scripts/deploy.sh up         # 启动基础设施 → 等待健康 → 启动微服务
#   bash scripts/deploy.sh down       # 停止并移除所有容器
#   bash scripts/deploy.sh restart    # 重启所有服务
#   bash scripts/deploy.sh all        # build + up + health-check 全流程
#   bash scripts/deploy.sh logs [svc] # 查看日志
#   bash scripts/deploy.sh status     # 查看服务状态
#   bash scripts/deploy.sh -help      # 显示帮助
# ============================================================

set -e

# ---------- 路径变量化 ----------
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
step() { echo -e "${BLUE}[STEP ]${NC} $1"; }

# ---------- .env 文件检查 ----------
ensure_env_file() {
    if [ ! -f "$PROJECT_ROOT/.env" ]; then
        if [ -f "$PROJECT_ROOT/.env.example" ]; then
            warn ".env 不存在，从 .env.example 复制..."
            cp "$PROJECT_ROOT/.env.example" "$PROJECT_ROOT/.env"
            log "已生成 .env，请按需修改后重新部署"
        else
            err "未找到 .env 与 .env.example，无法部署"
            exit 1
        fi
    fi
}

# ---------- docker compose 命令统一封装 ----------
dc() {
    docker compose -p "$COMPOSE_PROJECT" -f "$COMPOSE_FILE" "$@"
}

# ---------- 等待容器健康 ----------
wait_healthy() {
    local container="$1"
    local timeout="${2:-120}"
    local elapsed=0
    while [ $elapsed -lt $timeout ]; do
        local health
        health=$(docker inspect --format='{{.State.Health.Status}}' "$container" 2>/dev/null || echo "missing")
        if [ "$health" = "healthy" ]; then
            log "  ✓ $container 健康"
            return 0
        elif [ "$health" = "no-healthcheck" ]; then
            # 无健康检查的容器，检查运行状态即可
            local status
            status=$(docker inspect --format='{{.State.Status}}' "$container" 2>/dev/null || echo "missing")
            if [ "$status" = "running" ]; then
                log "  ✓ $container 运行中（无健康检查）"
                return 0
            fi
        fi
        printf "  等待 %s 健康 (%ds/%ds)\r" "$container" "$elapsed" "$timeout"
        sleep 5
        elapsed=$((elapsed + 5))
    done
    echo ""
    err "$container 在 ${timeout}s 内未健康"
    return 1
}

# ---------- 子命令 ----------
cmd_build() {
    step "开始构建所有 Docker 镜像..."
    ensure_env_file
    dc build
    log "构建完成"
    echo ""
    log "镜像列表:"
    docker images --format "table {{.Repository}}\t{{.Tag}}\t{{.Size}}\t{{.CreatedAt}}" | grep -E "REPOSITORY|kb-" || true
}

cmd_up() {
    step "启动基础设施（mysql/redis/minio/meilisearch/mongodb）..."
    ensure_env_file
    dc up -d mysql redis minio meilisearch mongodb

    step "等待基础设施健康..."
    wait_healthy kb-mysql 120
    wait_healthy kb-redis 60
    wait_healthy kb-minio 60
    wait_healthy kb-meilisearch 60
    wait_healthy kb-mongo 60

    step "启动微服务（gateway/auth/file/knowledge/intelligence）..."
    dc up -d kb-auth kb-file kb-knowledge kb-intelligence kb-gateway

    step "等待微服务就绪（最多 180s）..."
    for svc in kb-auth kb-file kb-knowledge kb-intelligence kb-gateway; do
        wait_healthy "$svc" 180 || warn "$svc 未在规定时间内就绪，请检查日志: docker logs $svc"
    done

    echo ""
    log "部署完成 ✓"
    echo ""
    dc ps
    echo ""
    info "网关地址: http://localhost:8090${KB_CONTEXT:-/kb}"
    info "查看日志: bash scripts/deploy.sh logs [service]"
}

cmd_down() {
    step "停止并移除所有容器..."
    dc down
    log "已停止并移除所有容器（数据卷保留）"
}

cmd_restart() {
    step "重启所有服务..."
    dc restart
    sleep 5
    dc ps
    log "重启完成"
}

cmd_all() {
    step "[1/3] 构建镜像..."
    cmd_build
    step "[2/3] 启动服务..."
    cmd_up
    step "[3/3] 执行健康检查..."
    if [ -x "$SCRIPT_DIR/health-check.sh" ]; then
        bash "$SCRIPT_DIR/health-check.sh" || warn "健康检查存在异常项"
    else
        warn "未找到 health-check.sh，跳过健康检查"
    fi
    log "全流程部署完成 ✓"
}

cmd_logs() {
    local svc="$1"
    if [ -z "$svc" ]; then
        log "查看所有服务日志（最近 200 行，跟随输出）..."
        dc logs --tail=200 -f
    else
        log "查看 $svc 日志..."
        dc logs --tail=200 -f "$svc"
    fi
}

cmd_status() {
    if [ -x "$SCRIPT_DIR/status.sh" ]; then
        bash "$SCRIPT_DIR/status.sh"
    else
        dc ps
    fi
}

show_help() {
    cat <<EOF
mykng 知识库微服务部署脚本

用法:
  bash scripts/deploy.sh <command> [options]

命令:
  build           构建所有 Docker 镜像（docker compose -p kb-deploy build）
  up              启动基础设施 → 等待健康 → 启动微服务
  down            停止并移除所有容器（保留数据卷）
  restart         重启所有服务
  all             完整流程: build + up + health-check
  logs [service]  查看日志（可选指定服务名）
  status          查看服务状态
  -help           显示此帮助信息

环境变量:
  COMPOSE_PROJECT  Docker Compose 项目名（默认: kb-deploy）
  KB_CONTEXT       网关上下文路径（默认: /kb）

示例:
  bash scripts/deploy.sh all
  bash scripts/deploy.sh up
  bash scripts/deploy.sh logs kb-auth
EOF
}

# ---------- 入口 ----------
case "${1:-}" in
    build)   cmd_build ;;
    up)      cmd_up ;;
    down)    cmd_down ;;
    restart) cmd_restart ;;
    all)     cmd_all ;;
    logs)    shift; cmd_logs "$1" ;;
    status)  cmd_status ;;
    -help|--help|-h) show_help ;;
    "")
        err "未指定命令"
        show_help
        exit 1
        ;;
    *)
        err "未知命令: $1"
        show_help
        exit 1
        ;;
esac
