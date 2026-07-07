#!/bin/bash
# ============================================================
# Woodpecker CI 部署管理脚本 — 一键部署到 mykng 服务器
# ============================================================
#
# 📌 部署架构:
#   用户 → https://ci.marschat.online (腾讯云2号 Nginx:443/SSL)
#     → Tailscale VPN → mykng (100.93.36.113):8000 (Woodpecker 容器)
#
# 🔧 选项:
#   --init        首次初始化（创建目录、生成配置、生成密钥）
#   --start       启动服务
#   --stop        停止服务
#   --restart     重启服务
#   --status      查看状态
#   --logs        查看日志
#   --clean       清理数据（⚠️ 危险操作）
#   --nginx       显示 Nginx 配置说明（用于腾讯云2号）
#   --prepull     预下载所有需要的 Docker 镜像
#   --help        显示帮助信息
#
# 🎯 目标服务器: mykng 虚拟机 (100.93.36.113)
# 📦 包含组件: Woodpecker Server + Agent + PostgreSQL
# 🌐 公网访问: https://ci.marschat.online
# 🔗 内网访问: http://100.93.36.113:8000 (Tailscale)
#
# ⚠️ 注意事项:
#   - 需要 Docker 和 Docker Compose
#   - 首次启动需要配置 OAuth 应用（Gitee/GitHub）
#   - 默认管理员：通过 OAuth 登录的第一个用户
#   - 确保腾讯云2号已配置 Nginx 反向代理
#   - 确保 Tailscale 网络连通
# ============================================================

set -e

# ================================
# 配置变量
# ================================
COMPOSE_DIR="/opt/woodpecker"
COMPOSE_FILE="${COMPOSE_DIR}/docker-compose.yml"
ENV_FILE="${COMPOSE_DIR}/.env"
PROJECT_NAME="woodpecker"

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
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

show_banner() {
    echo -e "${CYAN}"
    cat << 'EOF'
╔═══════════════════════════════════════════════════════════╗
║                                                           ║
║     🪵 Woodpecker CI — 轻量级 CI/CD 平台                  ║
║                                                           ║
║     📍 部署位置: mykng 虚拟机                             ║
║     🌐 访问地址: https://ci.marschat.online              ║
║     🔗 内网地址: http://100.93.36.113:8000               ║
║                                                           ║
╚═══════════════════════════════════════════════════════════╝
EOF
    echo -e "${NC}"
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
        log_error "Docker Compose 未安装！请先安装 docker-compose-plugin"
        exit 1
    fi
    
    # 检查端口占用（如果使用 ss 或 netstat）
    if command -v ss &> /dev/null; then
        if ss -tlnp | grep -q ":8000 "; then
            log_warn "端口 8000 已被占用，请检查是否有冲突"
        fi
        if ss -tlnp | grep -q ":9000 "; then
            log_warn "端口 9000 已被占用，请检查是否有冲突"
        fi
        if ss -tlnp | grep -q ":5433 "; then
            log_warn "端口 5433 已被占用，请检查是否有冲突"
        fi
    elif command -v netstat &> /dev/null; then
        if netstat -tlnp 2>/dev/null | grep -q ":8000 "; then
            log_warn "端口 8000 已被占用，请检查是否有冲突"
        fi
    fi
    
    # 检查磁盘空间（至少需要 5GB 可用空间）
    AVAILABLE_SPACE=$(df -BG "$COMPOSE_DIR" | tail -1 | awk '{print $4}' | tr -d 'G')
    if [ "$AVAILABLE_SPACE" -lt 5 ] 2>/dev/null; then
        log_warn "磁盘可用空间不足 5GB（当前: ${AVAILABLE_SPACE}GB）"
    else
        log_success "磁盘空间充足 (${AVAILABLE_SPACE}GB 可用)"
    fi
    
    # 检查内存（至少需要 2GB 可用内存）
    if [ -f /proc/meminfo ]; then
        AVAILABLE_MEM=$(grep MemAvailable /proc/meminfo | awk '{print int($2/1024)}')
        if [ "$AVAILABLE_MEM" -lt 2048 ] 2>/dev/null; then
            log_warn "可用内存不足 2GB（当前: ${AVAILABLE_MEM}MB）"
        else
            log_success "内存充足 (${AVAILABLE_MEM}MB 可用)"
        fi
    fi
    
    log_success "前置检查通过 ✅"
}

# ================================
# 初始化
# ================================
do_init() {
    show_banner
    check_prerequisites
    
    log_info "开始初始化 Woodpecker..."
    
    # 创建目录
    mkdir -p ${COMPOSE_DIR}
    log_success "创建目录: ${COMPOSE_DIR}"
    
    # 生成随机密钥
    WOODPECKER_SECRET=$(openssl rand -hex 32)
    WOODPECKER_AGENT_SECRET=$(openssl rand -hex 32)
    WOODPECKER_DB_PASSWORD=$(openssl rand -hex 16)
    
    log_info "生成安全密钥..."
    
    # 创建 .env 文件
    cat > ${ENV_FILE} << EOF
# ============================================
# Woodpecker CI 环境变量配置
# ============================================
# ⚠️ 请勿将此文件提交到版本控制！包含敏感信息！

# ---- 访问地址（重要！）----
WOODPECKER_HOST=https://ci.marschat.online

# ---- 安全密钥（自动生成，请勿修改）----
WOODPECKER_SECRET=${WOODPECKER_SECRET}
WOODPECKER_AGENT_SECRET=${WOODPECKER_AGENT_SECRET}
WOODPECKER_DB_PASSWORD=${WOODPECKER_DB_PASSWORD}

# ---- Gitee OAuth 配置（推荐国内环境）----
WOODPECKER_GITEE=true
# GITEE_CLIENT_ID=你的Gitee_Client_ID
# GITEE_CLIENT_SECRET=你的Gitee_Client_Secret

# ---- GitHub OAuth 配置（备选）----
WOODPECKER_GITHUB=false
# GITHUB_CLIENT_ID=你的GitHub_Client_ID
# GITHUB_CLIENT_SECRET=你的GitHub_Client_Secret

# ---- 管理员用户名（可选，留空则第一个登录用户为管理员）----
# WOODPECKER_ADMIN=admin

# ---- 日志级别 (debug/info/warn/error)----
LOG_LEVEL=info
EOF
    
    chmod 600 ${ENV_FILE}
    log_success "创建环境变量文件: ${ENV_FILE}"
    
    # 复制 docker-compose.yml（如果不存在）
    if [ ! -f "${COMPOSE_FILE}" ]; then
        SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
        cp "${SCRIPT_DIR}/docker-compose.yml" "${COMPOSE_FILE}"
        log_success "复制 docker-compose.yml"
    fi
    
    echo ""
    log_success "初始化完成 ✅"
    echo ""
    log_info "配置文件位置: ${COMPOSE_DIR}"
    log_info "下一步: 运行 bash $0 --start 启动服务"
    echo ""
    log_info "⚠️  启动后需要配置 OAuth 应用："
    echo "   1. 访问 https://ci.marschat.online"
    echo "   2. 选择 Gitee/GitHub 进行 OAuth 授权"
    echo "   3. 在 Gitee/GitHub 创建 OAuth 应用并填写 Client ID/Secret"
    echo "   4. 编辑 ${ENV_FILE} 填写 OAuth 凭据"
    echo "   5. 重启服务: bash $0 --restart"
    echo ""
}

# ================================
# 启动服务
# ================================
do_start() {
    show_banner
    check_prerequisites
    
    log_info "启动 Woodpecker 服务..."
    
    cd ${COMPOSE_DIR}
    
    # 加载 .env 文件（如果存在）
    if [ -f "${ENV_FILE}" ]; then
        set -a
        source ${ENV_FILE}
        set +a
        log_info "加载环境变量: ${ENV_FILE}"
    else
        log_warn ".env 文件未存在，使用默认值"
    fi
    
    # 拉取最新镜像（后台运行，避免超时）
    log_info "拉取最新镜像（可能需要几分钟）..."
    docker compose pull || true
    
    # 启动服务
    log_info "启动容器..."
    docker compose up -d
    
    # 等待服务就绪
    log_info "等待服务启动..."
    sleep 10
    
    # 显示状态
    do_status
    
    echo ""
    log_success "Woodpecker 启动完成 ✅"
    echo ""
    log_info "📍 访问地址:"
    echo "   • 内网: http://$(hostname -I | awk '{print $1}'):8000"
    echo "   • 公网: https://ci.marschat.online（需配置 Nginx 反向代理）"
    echo ""
    log_info "⏳  首次启动可能需要 30-60 秒初始化数据库..."
    echo ""
}

# ================================
# 停止服务
# ================================
do_stop() {
    log_info "停止 Woodpecker 服务..."
    
    cd ${COMPOSE_DIR}
    docker compose down
    
    log_success "Woodpecker 已停止 ✅"
}

# ================================
# 重启服务
# ================================
do_restart() {
    log_info "重启 Woodpecker 服务..."
    
    cd ${COMPOSE_DIR}
    docker compose restart
    
    sleep 5
    do_status
    
    log_success "Woodpecker 已重启 ✅"
}

# ================================
# 查看状态
# ================================
do_status() {
    echo ""
    echo -e "${CYAN}━━━ Woodpecker 服务状态 ━━━${NC}"
    echo ""
    
    cd ${COMPOSE_DIR}
    docker compose ps
    
    echo ""
    echo -e "${CYAN}━━━ 资源使用情况 ━━━${NC}"
    echo ""
    docker stats --no-stream --format "table {{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}\t{{.NetIO}}" 2>/dev/null || \
        docker stats --no-stream || true
    
    echo ""
    echo -e "${CYAN}━━━ 健康检查 ━━━${NC}"
    echo ""
    
    # PostgreSQL
    if docker exec woodpecker-db pg_isready -U woodpecker &>/dev/null; then
        log_success "PostgreSQL: ✅ 健康"
    else
        log_warn "PostgreSQL: ⏳ 启动中..."
    fi
    
    # Woodpecker Server
    if curl -sf http://localhost:8000 > /dev/null 2>&1; then
        log_success "Woodpecker Server: ✅ 可访问"
    else
        log_warn "Woodpecker Server: ⏳ 启动中..."
    fi
    
    # Woodpecker Agent
    AGENT_STATUS=$(docker logs woodpecker-agent 2>&1 | tail -5 | grep -i "connected\|ready\|error" || true)
    if echo "$AGENT_STATUS" | grep -qi "connected\|ready"; then
        log_success "Woodpecker Agent: ✅ 已连接"
    elif echo "$AGENT_STATUS" | grep -qi "error"; then
        log_error "Woodpecker Agent: ❌ 有错误"
        echo "$AGENT_STATUS"
    else
        log_warn "Woodpecker Agent: ⏳ 连接中..."
    fi
    
    echo ""
}

# ================================
# 查看日志
# ================================
do_logs() {
    log_info "查看 Woodpecker 日志 (Ctrl+C 退出)..."
    echo ""
    
    cd ${COMPOSE_DIR}
    docker compose logs -f --tail=100
}

# ================================
# 清理数据
# ================================
do_clean() {
    log_warn "⚠️  此操作将删除所有 Woodpecker 数据和服务！"
    log_warn "包括：数据库、配置、构建历史等"
    echo ""
    read -p "确认继续？(输入 YES 确认): " confirm
    
    if [ "$confirm" != "YES" ]; then
        log_info "已取消"
        exit 0
    fi
    
    log_info "清理 Woodpecker 数据..."
    
    cd ${COMPOSE_DIR}
    docker compose down -v --remove-orphans
    
    # 删除镜像（可选）
    read -p "是否同时删除 Docker 镜像？(y/N): " del_images
    if [ "$del_images" = "y" ] || [ "$del_images" = "Y" ]; then
        docker rmi woodpeckerci/woodpecker-server:latest \
                 woodpeckerci/woodpecker-agent:latest \
                 postgres:16-alpine 2>/dev/null || true
        log_info "镜像已删除"
    fi
    
    # 删除 .env 文件（可选）
    read -p "是否同时删除 .env 配置文件？(y/N): " del_env
    if [ "$del_env" = "y" ] || [ "$del_env" = "Y" ]; then
        rm -f ${ENV_FILE}
        log_info ".env 文件已删除"
    fi
    
    log_success "清理完成 ✅"
}

# ================================
# 预下载镜像
# ================================
do_prepull() {
    log_info "预下载 Woodpecker 所需的 Docker 镜像..."
    echo ""
    
    images=(
        "woodpeckerci/woodpecker-server:latest"
        "woodpeckerci/woodpecker-agent:latest"
        "postgres:16-alpine"
        # 常用构建镜像（可选）
        "maven:3.9-eclipse-temurin-17-alpine"
        "node:20-alpine"
        "alpine:3.19"
    )
    
    for image in "${images[@]}"; do
        log_info "拉取镜像: ${image}"
        if docker pull "${image}"; then
            log_success "✅ ${image}"
        else
            log_error "❌ ${image} 拉取失败"
        fi
        echo ""
    done
    
    echo ""
    log_info "查看已缓存的镜像："
    docker images | grep -E "(woodpecker|postgres|maven|node|alpine)" | head -20
    
    echo ""
    log_success "预下载完成 ✅"
}

# ================================
# 显示 Nginx 配置说明
# ================================
do_nginx() {
    echo ""
    echo -e "${CYAN}━━━ Nginx 反向代理配置说明 ━━━${NC}"
    echo ""
    echo "在腾讯云2号服务器上配置 Nginx 反向代理："
    echo ""
    echo "📁 配置文件位置: /etc/nginx/conf.d/ci.marschat.online.conf"
    echo ""
    cat << 'NGINX_EOF'
server {
    listen 443 ssl;
    server_name ci.marschat.online;
    
    # SSL 证书（使用泛域名证书）
    ssl_certificate     /path/to/certs/marschat.online.pem;
    ssl_certificate_key /path/to/certs/marschat.online.key;
    
    # SSL 优化
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers HIGH:!aNULL:!MD5;
    
    client_max_body_size 100M;  # 支持大文件上传
    
    location / {
        proxy_pass http://100.93.36.113:8000;  # Tailscale 内网地址
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        
        # WebSocket 支持（Woodpecker 需要）
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        
        # 超时时间（构建可能耗时较长）
        proxy_read_timeout 300s;
        proxy_send_timeout 300s;
        proxy_connect_timeout 60s;
    }
}
NGINX_EOF
    echo ""
    echo -e "${YELLOW}注意:${NC}"
    echo "  1. 将上面的配置保存到腾讯云2号的 Nginx 配置目录"
    echo "  2. 修改 SSL 证书路径为实际证书位置"
    echo "  3. 确保 Tailscale 网络可达（ping 100.93.36.113）"
    echo "  4. 测试配置: nginx -t && systemctl reload nginx"
    echo "  5. DNS 解析: ci.marschat.online → 腾讯云2号 IP"
    echo ""
}

# ================================
# 显示帮助信息
# ================================
show_help() {
    show_banner
    cat << 'EOF'
用法: bash $0 [选项]

选项:
  --init        首次初始化（创建目录、生成配置和密钥）
  --start       启动所有服务
  --stop        停止所有服务
  --restart     重启所有服务
  --status      查看服务运行状态和资源使用情况
  --logs        查看实时日志 (Ctrl+C 退出)
  --clean       清理所有数据和服务（⚠️ 危险操作）
  --nginx       显示 Nginx 反向代理配置说明
  --prepull     预下载所有需要的 Docker 镜像（加速首次启动）
  --help/-h     显示本帮助信息

示例:
  # 首次部署
  bash $0 --init
  bash $0 --start
  
  # 日常运维
  bash $0 --status          # 查看状态
  bash $0 --logs            # 查看日志
  bash $0 --restart         # 重启服务
  
  # 故障排查
  bash $0 --stop            # 停止服务
  bash $0 --clean           # 清理重装

资源占用:
  空闲时: ~350 MB 内存, ~0.1 CPU
  构建时: ~1.5-2 GB 内存, ~2-2.5 CPU

更多信息:
  - 文档: docs/woodpecker-setup.md
  - 官网: https://woodpecker-ci.org/
  - GitHub: https://github.com/woodpeckerci/woodpecker

EOF
}

# ================================
# 主程序
# ================================
main() {
    case "${1:-}" in
        --init)
            do_init
            ;;
        --start)
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
        --nginx)
            do_nginx
            ;;
        --prepull)
            do_prepull
            ;;
        --help|-h)
            show_help
            ;;
        "")
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
