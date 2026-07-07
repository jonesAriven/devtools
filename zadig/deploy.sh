#!/bin/bash
# ============================================================
# Zadig 部署脚本 — 一键部署到 mykng 服务器
# ============================================================
#
# 📌 部署架构:
#   用户 → https://zadig.marschat.online (腾讯云2号 Nginx:443/SSL)
#     → Tailscale VPN → mykng (100.93.36.113):8080 (Zadig 容器)
#
# 🔧 选项:
#   --init        首次初始化（创建目录、下载配置）
#   --start       启动服务
#   --stop        停止服务
#   --restart     重启服务
#   --status      查看状态
#   --logs        查看日志
#   --clean       清理数据（⚠️ 危险操作）
#   --nginx       显示 Nginx 配置说明（用于腾讯云2号）
#   --help        显示帮助信息
#
# 🎯 目标服务器: mykng 虚拟机 (100.93.36.113)
# 📦 包含组件: Zadig + MySQL + Redis
# 🌐 公网访问: https://zadig.marschat.online
# 🔗 内网访问: http://100.93.36.113:8080 (Tailscale)
#
# ⚠️ 注意事项:
#   - 需要 Docker 和 Docker Compose
#   - 首次启动需要 2-3 分钟初始化数据库
#   - 默认账号: admin / zadig（请立即修改！）
#   - 确保腾讯云2号已配置 Nginx 反向代理
#   - 确保 Tailscale 网络连通
# ============================================================

set -e

# ================================
# 配置变量
# ================================
COMPOSE_DIR="/opt/zadig"
COMPOSE_FILE="${COMPOSE_DIR}/docker-compose.yml"
PROJECT_NAME="zadig"

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# ================================
# 辅助函数
# ================================
log_info() {
    echo -e "${BLUE}[INFO]${NC} $(date '+%Y-%m-%d %H:%M:%S') $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $(date '+%Y-%m-%d %H:%M:%S') $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $(date '+%Y-%m-%d %H:%M:%S') $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $(date '+%Y-%m-%d %H:%M:%S') $1"
}

show_help() {
    cat << EOF
🚀 Zadig CI/CD 平台 — 部署管理脚本

📌 部署架构:
   用户 → https://zadig.marschat.online (腾讯云2号 Nginx:443/SSL)
     → Tailscale VPN → mykng (100.93.36.113):8080 (Zadig 容器)

用法: bash $0 [选项]

选项:
  --init        首次初始化（创建目录、生成配置文件）
  --start       启动所有服务
  --stop        停止所有服务
  --restart     重启所有服务
  --status      查看服务运行状态
  --logs        查看实时日志 (Ctrl+C 退出)
  --clean       清理所有数据和服务（⚠️ 不可恢复）
  --nginx       显示腾讯云2号 Nginx 配置说明
  --help        显示本帮助信息

示例:
  bash $0 --init          # 首次部署
  bash $0 --start         # 启动服务
  bash $0 --logs          # 查看日志
  bash $0 --nginx         # 查看 Nginx 配置指南
  bash $0 --clean         # 完全卸载

🌐 访问地址:
   公网: https://zadig.marschat.online (需配置 Nginx + DNS)
   内网: http://100.93.36.113:8080 (Tailscale)
   本地: http://localhost:8080

🔑 默认账号: admin / zadig（请立即修改！）

📖 文档:
   - 使用指南: docs/zadig-setup.md
   - 官方文档: https://docs.zadig.kl7e.com/
EOF
}

# ================================
# 前置检查
# ================================
check_prerequisites() {
    log_info "检查前置条件..."
    
    # 检查 Docker
    if ! command -v docker &> /dev/null; then
        log_error "Docker 未安装！请先安装 Docker"
        exit 1
    fi
    
    # 检查 Docker Compose
    if ! docker compose version &> /dev/null; then
        log_error "Docker Compose 未安装！请先安装 Docker Compose V2"
        exit 1
    fi
    
    # 检查端口占用
    if netstat -tuln | grep -q ":8080 "; then
        log_warn "端口 8080 已被占用，请检查或修改 docker-compose.yml"
    fi
    
    log_success "前置检查通过 ✅"
}

# ================================
# 初始化
# ================================
do_init() {
    log_info "开始初始化 Zadig..."
    
    # 创建目录
    mkdir -p ${COMPOSE_DIR}
    
    # 生成环境变量配置
    cat > ${COMPOSE_DIR}/.env << 'EOF'
# ============================================
# Zadig 环境变量配置
# ============================================
# 
# ⚠️ 请根据实际情况修改以下配置！
# 特别是密码类变量，不要使用默认值！
# ============================================

# MySQL 密码（建议修改为强密码）
MYSQL_PASSWORD=zadig2024

# Redis 密码（可选，留空则无密码）
REDIS_PASSWORD=

# Zadig 访问域名/IP
ZADIG_DOMAIN=http://localhost:8080

# Zadig 加密密钥（建议修改为随机字符串）
ZADIG_SECRET=your-secret-key-change-me-please

# 日志级别 (debug/info/warn/error)
LOG_LEVEL=info
EOF
    
    # 复制 docker-compose.yml（如果不存在）
    if [ ! -f "${COMPOSE_FILE}" ]; then
        log_info "复制 docker-compose.yml..."
        cp "$(dirname "$0")/docker-compose.yml" "${COMPOSE_FILE}"
    fi
    
    # 设置权限
    chmod 600 ${COMPOSE_DIR}/.env
    
    log_success "初始化完成 ✅"
    log_info "配置文件位置: ${COMPOSE_DIR}"
    log_info "下一步: 运行 bash $0 --start 启动服务"
}

# ================================
# 启动服务
# ================================
do_start() {
    log_info "启动 Zadig 服务..."
    
    cd ${COMPOSE_DIR}
    
    # 拉取镜像（首次较慢）
    log_info "拉取最新镜像（可能需要几分钟）..."
    docker compose pull
    
    # 启动服务
    docker compose up -d
    
    # 等待就绪
    log_info "等待服务启动（约 2-3 分钟）..."
    sleep 30
    
    # 检查状态
    do_status
    
    log_success "============================================="
    log_success "  🎉 Zadig 启动成功!"
    log_success ""
    log_success "  📍 访问地址:"
    log_success "     🌐 公网: https://zadig.marschat.online (需配置 Nginx)"
    log_success "     🔗 内网: http://100.93.36.113:8080 (Tailscale)"
    log_success "     🏠 本地: http://localhost:8080"
    log_success ""
    log_success "  🔑 默认账号:"
    log_success "     用户名: admin"
    log_success "     密码:   zadig"
    log_success ""
    log_success "  ⚠️  请立即修改默认密码!"
    log_success ""
    log_success "  📋 下一步:"
    log_success "     1. 配置腾讯云2号 Nginx 反向代理（见 zadig-nginx.conf）"
    log_success "     2. DNS 解析: zadig.marschat.online → 腾讯云2号 IP"
    log_success "     3. 访问 https://zadig.marschat.online 测试"
    log_success "============================================="
}

# ================================
# 停止服务
# ================================
do_stop() {
    log_info "停止 Zadig 服务..."
    
    cd ${COMPOSE_DIR}
    docker compose down
    
    log_success "服务已停止 ✅"
}

# ================================
# 重启服务
# ================================
do_restart() {
    log_info "重启 Zadig 服务..."
    
    do_stop
    sleep 5
    do_start
}

# ================================
# 查看状态
# ================================
do_status() {
    log_info "Zadig 服务状态:"
    echo ""
    
    cd ${COMPOSE_DIR}
    docker compose ps
    
    echo ""
    
    # 检查各服务健康状态
    log_info "--- 健康检查 ---"
    
    # MySQL
    if docker exec zadig-mysql mysqladmin ping -uroot -p${MYSQL_PASSWORD:-zadig2024} &>/dev/null; then
        log_success "MySQL: ✅ 健康"
    else
        log_warn "MySQL: ⏳ 启动中..."
    fi
    
    # Redis
    if docker exec zadig-redis redis-cli ping 2>/dev/null | grep -q PONG; then
        log_success "Redis: ✅ 健康"
    else
        log_warn "Redis: ⏳ 启动中..."
    fi
    
    # Zadig Server
    if curl -sf http://localhost:8080 > /dev/null 2>&1; then
        log_success "Zadig: ✅ 可访问"
    else
        log_warn "Zadig: ⏳ 启动中..."
    fi
}

# ================================
# 查看日志
# ================================
do_logs() {
    log_info "查看 Zadig 日志 (Ctrl+C 退出)..."
    echo ""
    
    cd ${COMPOSE_DIR}
    docker compose logs -f --tail=100
}

# ================================
# 清理数据
# ================================
do_clean() {
    log_warn "⚠️  此操作将删除所有 Zadig 数据和服务！"
    log_warn "包括：数据库、配置、构建历史等"
    echo ""
    read -p "确认继续？(输入 YES 确认): " confirm
    
    if [ "$confirm" != "YES" ]; then
        log_info "已取消"
        exit 0
    fi
    
    log_info "清理 Zadig 数据..."
    
    cd ${COMPOSE_DIR}
    docker compose down -v --remove-orphans
    
    # 删除镜像（可选）
    read -p "是否同时删除 Docker 镜像？(y/N): " del_images
    if [ "$del_images" = "y" ] || [ "$del_images" = "Y" ]; then
        docker rmi koderover/zadig:latest mysql:8.0 redis:7-alpine 2>/dev/null || true
        log_info "镜像已删除"
    fi
    
    log_success "清理完成 ✅"
}

# ================================
# 主程序
# ================================
main() {
    case "${1:-}" in
        --init)
            check_prerequisites
            do_init
            ;;
        --start)
            check_prerequisites
            do_start
            ;;
        --stop)
            do_stop
            ;;
        --restart)
            do_restart
            ;;
        --status)
            do_status
            ;;
        --logs)
            do_logs
            ;;
        --clean)
            do_clean
            ;;
        --help|-h)
            show_help
            ;;
        *)
            echo "未知参数: ${1:-}"
            echo "使用 --help 查看帮助信息"
            exit 1
            ;;
    esac
}

# 执行主程序
main "$@"
