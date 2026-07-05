#!/bin/bash
# ============================================================
# mykng 监控平台 - 启动脚本
# ============================================================
# 用法：
#   ./start-monitoring.sh [选项]
#
# 选项：
#   start     - 启动所有监控服务（默认）
#   stop      - 停止所有监控服务
#   restart   - 重启所有监控服务
#   status    - 查看服务状态
#   logs      - 查看日志（可指定服务名）
#   reset     - 清除所有数据并重新启动（⚠️ 危险操作）
#   help      - 显示帮助信息
#
# 示例：
#   ./start-monitoring.sh start           # 启动全部
#   ./start-monitoring.sh logs grafana    # 查看 Grafana 日志
#   ./start-monitoring.sh status          # 查看状态
# ============================================================

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 脚本所在目录
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_FILE="${SCRIPT_DIR}/docker-compose.monitoring.yml"

# ============================================================
# 辅助函数
# ============================================================

print_header() {
  echo ""
  echo -e "${BLUE}══════════════════════════════════════════${NC}"
  echo -e "${BLUE}  mykng 监控平台管理工具${NC}"
  echo -e "${BLUE}══════════════════════════════════════════${NC}"
  echo ""
}

print_success() {
  echo -e "${GREEN}✅ $1${NC}"
}

print_warning() {
  echo -e "${YELLOW}⚠️  $1${NC}"
}

print_error() {
  echo -e "${RED}❌ $1${NC}"
}

check_docker() {
  if ! command -v docker &> /dev/null; then
    print_error "Docker 未安装！请先安装 Docker。"
    exit 1
  fi
  
  if ! docker compose version &> /dev/null; then
    print_error "Docker Compose 未安装或版本过低！"
    exit 1
  fi
  
  if ! docker info &> /dev/null; then
    print_error "Docker 服务未运行！请先启动 Docker。"
    exit 1
  fi
}

check_compose_file() {
  if [ ! -f "$COMPOSE_FILE" ]; then
    print_error "找不到配置文件: $COMPOSE_FILE"
    exit 1
  fi
}

show_access_info() {
  echo ""
  echo -e "${GREEN}🎉 监控平台已启动！${NC}"
  echo ""
  echo -e "  ${BLUE}访问地址：${NC}"
  echo -e "    📊 Grafana 看板:    ${YELLOW}http://$(hostname -I | awk '{print $1}'):3000${NC}  (admin/admin)"
  echo -e "    📈 Prometheus:    ${YELLOW}http://$(hostname -I | awk '{print $1}'):9090${NC}"
  echo -e "    🔔 AlertManager:   ${YELLOW}http://$(hostname -I | awk '{print $1}'):9093${NC}"
  echo ""
  echo -e "  ${BLUE}推荐 Dashboard（在 Grafana 中导入）：${NC}"
  echo "    • 服务器监控:  ID 1860 (Node Exporter Full)"
  echo "    • 容器监控:    ID 193 (Docker & Container Monitoring)"
  echo "    • JVM 监控:     ID 4701 (JVM Micrometer)"
  echo "    • 日志查看:     ID 13639 (Loki Logging)"
  echo "    • 告警总览:     ID 9578 (AlertManager Overview)"
  echo ""
  echo -e "  ${BLUE}快速开始：${NC}"
  echo "    1. 打开 Grafana (http://localhost:3000)"
  echo "    2. 登录 (admin / admin)"
  echo "    3. 左侧菜单 → Dashboards → Import"
  echo "    4. 输入 Dashboard ID (如 1860) → Load"
  echo "    5. 选择 Prometheus 数据源 → Import"
  echo ""
}

# ============================================================
# 命令处理
# ============================================================

case "${1:-start}" in

  # ----------------------------------------------------------
  # 启动服务
  # ----------------------------------------------------------
  start)
    print_header
    check_docker
    check_compose_file
    
    print_success "正在启动 mykng 监控平台..."
    echo ""
    
    cd "$SCRIPT_DIR"
    
    # 创建必要的目录（如果不存在）
    mkdir -p config/grafana/provisioning/datasources
    mkdir -p config/grafana/provisioning/dashboards
    mkdir -p config/grafana/dashboards
    
    # 启动服务
    docker compose -f "$COMPOSE_FILE" up -d
    
    # 等待服务就绪
    print_success "等待服务启动..."
    sleep 10
    
    # 检查各服务状态
    echo ""
    print_success "检查服务状态："
    docker compose -f "$COMPOSE_FILE" ps
    
    show_access_info
    ;;

  # ----------------------------------------------------------
  # 停止服务
  # ----------------------------------------------------------
  stop)
    print_header
    check_docker
    check_compose_file
    
    print_warning "正在停止 mykng 监控平台..."
    cd "$SCRIPT_DIR"
    docker compose -f "$COMPOSE_FILE" down
    
    print_success "监控平台已停止。"
    ;;

  # ----------------------------------------------------------
  # 重启服务
  # ----------------------------------------------------------
  restart)
    print_header
    check_docker
    check_compose_file
    
    print_warning "正在重启 mykng 监控平台..."
    cd "$SCRIPT_DIR"
    docker compose -f "$COMPOSE_FILE" restart
    
    sleep 5
    print_success "重启完成！"
    show_access_info
    ;;

  # ----------------------------------------------------------
  # 查看状态
  # ----------------------------------------------------------
  status)
    print_header
    check_docker
    check_compose_file
    
    cd "$SCRIPT_DIR"
    echo -e "${BLUE}容器状态：${NC}"
    docker compose -f "$COMPOSE_FILE" ps
    
    echo ""
    echo -e "${BLUE}资源使用情况：${NC}"
    docker stats --no-stream --format "table {{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}\t{{.NetIO}}" \
      $(docker compose -f "$COMPOSE_FILE" ps -q)
    
    echo ""
    echo -e "${BLUE}数据卷使用情况：${NC}"
    docker volume ls --filter "name=kb-*"
    ;;

  # ----------------------------------------------------------
  # 查看日志
  # ----------------------------------------------------------
  logs)
    shift  # 移除 logs 参数
    SERVICE="${1:-}"  # 可选的服务名
    
    check_docker
    check_compose_file
    
    cd "$SCRIPT_DIR"
    
    if [ -n "$SERVICE" ]; then
      print_success "查看 $SERVICE 的最近 100 行日志："
      docker compose -f "$COMPOSE_FILE" logs --tail=100 -f "$SERVICE"
    else
      print_success "查看所有服务的最近 50 行日志："
      docker compose -f "$COMPOSE_FILE" logs --tail=50
    fi
    ;;

  # ----------------------------------------------------------
  # 重置（清除所有数据）⚠️ 危险操作
  # ----------------------------------------------------------
  reset)
    print_header
    print_error "⚠️  此操作将清除所有监控数据和配置！"
    read -p "确定要继续吗？(输入 YES 确认): " confirm
    
    if [ "$confirm" = "YES" ]; then
      check_docker
      check_compose_file
      
      cd "$SCRIPT_DIR"
      
      # 停止并删除容器、网络、数据卷
      docker compose -f "$COMPOSE_FILE" down -v --remove-orphans
      
      # 删除命名卷
      docker volume rm kb-prometheus-data kb-grafana-data kb-loki-data kb-alertmanager-data 2>/dev/null || true
      
      print_success "重置完成！所有数据已清除。"
      print_success "运行 '$0 start' 重新启动。"
    else
      print_warning "操作已取消。"
    fi
    ;;

  # ----------------------------------------------------------
  # 显示帮助
  # ----------------------------------------------------------
  help|*)
    print_header
    echo -e "  ${BLUE}用法：${NC} $0 <命令>"
    echo ""
    echo -e "  ${BLUE}命令：${NC}"
    echo "    start     启动所有监控服务（默认）"
    echo "    stop      停止所有监控服务"
    echo "    restart   重启所有监控服务"
    echo "    status    查看服务和资源状态"
    echo "    logs      查看日志 [服务名]"
    echo "    reset     ⚠️ 清除所有数据并重新启动"
    echo "    help      显示此帮助信息"
    echo ""
    echo -e "  ${BLUE}示例：${NC}"
    echo "    $0 start              # 启动全部"
    echo "    $0 logs grafana       # 查看 Grafana 日志"
    echo "    $0 status             # 查看状态"
    echo ""
    ;;

esac
