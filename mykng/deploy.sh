#!/bin/bash
# ============================================================
# mykng 知识库微服务一键部署脚本
# ============================================================
# 用法:
#   ./deploy.sh build    # 构建所有镜像
#   ./deploy.sh up       # 启动所有服务
#   ./deploy.sh down     # 停止所有服务
#   ./deploy.sh restart  # 重启所有服务
#   ./deploy.sh logs     # 查看所有服务日志
#   ./deploy.sh status   # 查看服务状态
#   ./deploy.sh rebuild  # 重新构建并启动
#   ./deploy.sh clean    # 停止并删除所有容器和数据卷
# ============================================================

set -e

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$PROJECT_DIR"

# 颜色
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

log()  { echo -e "${GREEN}[INFO]${NC}  $1"; }
warn() { echo -e "${YELLOW}[WARN]${NC}  $1"; }
err()  { echo -e "${RED}[ERROR]${NC} $1"; }

case "$1" in
  build)
    log "开始构建所有 Docker 镜像..."
    docker compose build
    log "构建完成"
    ;;

  up)
    log "启动所有服务..."
    docker compose up -d
    log "等待服务启动..."
    sleep 10
    log "服务状态:"
    docker compose ps
    echo ""
    log "网关地址: http://localhost:8080"
    log "查看日志: ./deploy.sh logs"
    ;;

  down)
    log "停止所有服务..."
    docker compose down
    log "已停止"
    ;;

  restart)
    log "重启所有服务..."
    docker compose restart
    sleep 5
    docker compose ps
    ;;

  rebuild)
    log "重新构建并启动..."
    docker compose down
    docker compose build
    docker compose up -d
    sleep 10
    docker compose ps
    ;;

  logs)
    if [ -z "$2" ]; then
      log "查看所有服务日志（最近100行）..."
      docker compose logs --tail=100 -f
    else
      log "查看 $2 日志..."
      docker compose logs --tail=100 -f "$2"
    fi
    ;;

  status)
    log "服务状态:"
    docker compose ps
    echo ""
    log "资源使用:"
    docker stats --no-stream --format "table {{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}" $(docker compose ps -q) 2>/dev/null || true
    ;;

  clean)
    warn "这将删除所有容器和数据卷！"
    read -p "确认删除？(yes/no): " confirm
    if [ "$confirm" = "yes" ]; then
      err "停止并删除所有容器和数据卷..."
      docker compose down -v
      log "清理完成"
    else
      log "已取消"
    fi
    ;;

  *)
    echo "用法: $0 {build|up|down|restart|rebuild|logs [service]|status|clean}"
    echo ""
    echo "  build    - 构建所有 Docker 镜像"
    echo "  up       - 启动所有服务"
    echo "  down     - 停止所有服务"
    echo "  restart  - 重启所有服务"
    echo "  rebuild  - 重新构建并启动"
    echo "  logs [s] - 查看日志（可选指定服务名）"
    echo "  status   - 查看服务状态"
    echo "  clean    - 停止并删除所有容器和数据卷"
    exit 1
    ;;
esac
