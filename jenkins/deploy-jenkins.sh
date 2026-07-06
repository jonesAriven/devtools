#!/bin/bash
# ============================================================
# Jenkins 一键部署脚本 — 在 mykng 主机上执行
# ============================================================
# 用法:
#   bash deploy-jenkins.sh              # 交互式（提示输入密码）
#   bash deploy-jenkins.sh --quick      # 快速模式（使用默认配置）
#   bash deploy-jenkins.sh --stop       # 停止 Jenkins
#   bash deploy-jenkins.sh --logs       # 查看日志
#   bash deploy-jkins.sh --reset        # 重置 Jenkins（⚠️ 删除所有数据）
# ============================================================

set -e

# ==================== 配置区 ====================
JENKINS_DIR="$(cd "$(dirname "$0")" && pwd)"
DEVTOOLS_DIR="$(dirname "$JENKINS_DIR")"
COMPOSE_FILE="$JENKINS_DIR/docker-compose.yml"
ENV_FILE="$JENKINS_DIR/.env"
CONTAINER_NAME="kb-jenkins"
JENKINS_PORT=8096
JENKINS_URL="http://localhost:${JENKINS_PORT}"

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

log_info()    { echo -e "${BLUE}[INFO]${NC} $*"; }
log_success() { echo -e "${GREEN}[✓]${NC} $*"; }
log_warn()    { echo -e "${YELLOW}[!]${NC} $*"; }
log_error()   { echo -e "${RED}[✗]${NC} $*"; }

# ==================== 命令路由 ====================
case "${1:-}" in
    --stop)
        log_info "停止 Jenkins..."
        docker compose -f "$COMPOSE_FILE" down
        log_success "Jenkins 已停止"
        exit 0
        ;;
    --logs)
        docker compose -f "$COMPOSE_FILE" logs -f --tail=100
        exit 0
        ;;
    --reset)
        log_warn "⚠️  这将删除所有 Jenkins 数据（配置、构建历史、凭据）！"
        read -p "确认继续？(yes/no): " confirm
        if [ "$confirm" = "yes" ]; then
            docker compose -f "$COMPOSE_FILE" down -v
            rm -f "$ENV_FILE"
            log_success "Jenkins 已重置，下次启动将全新初始化"
        else
            log_info "已取消"
        fi
        exit 0
        ;;
    --status)
        if docker ps | grep -q "$CONTAINER_NAME"; then
            log_success "Jenkins 运行中"
            echo "  URL: $JENKINS_URL"
            echo "  容器: $(docker inspect --format='{{.State.Status}}' $CONTAINER_NAME)"
            echo "  启动时间: $(docker inspect --format='{{.State.StartedAt}}' $CONTAINER_NAME | cut -d. -f1)"
            echo "  内存: $(docker stats --no-stream --format '{{.MemUsage}}' $CONTAINER_NAME)"
            # 检查是否就绪
            if curl -sf "${JENKINS_URL}/login" > /dev/null 2>&1; then
                log_success "Web UI 已就绪 ✅"
            else
                log_warn "Web UI 仍在启动中...（首次需3-5分钟安装插件）"
            fi
        else
            log_error "Jenkins 未运行"
            echo "  启动命令: bash $0"
        fi
        exit 0
        ;;
esac

# ==================== 主流程：部署/启动 ====================
echo ""
echo "╔══════════════════════════════════════════════════╗"
echo "║     🚀 kb-cicd (Jenkins Edition) 部署工具       ║"
echo "╚══════════════════════════════════════════════════╝"
echo ""

# Step 1: 环境检查
log_info "[1/6] 环境检查..."

if ! command -v docker &> /dev/null; then
    log_error "Docker 未安装！请先安装 Docker"
    exit 1
fi

if ! docker compose version &> /dev/null; then
    log_error "Docker Compose 未安装！请安装 Docker Compose v2+"
    exit 1
fi

log_success "Docker: $(docker --version)"
log_success "Docker Compose: $(docker compose version)"

# 检查端口占用
if netstat -tlnp 2>/dev/null | grep -q ":${JENKINS_PORT} " || ss -tlnp 2>/dev/null | grep -q ":${JENKNS_PORT} "; then
    log_warn "端口 ${JENKINS_PORT} 已被占用"
    log_info "检查中..."
    if docker ps | grep -q "$CONTAINER_NAME"; then
        log_info "Jenkins 已在运行，如需重启请先执行: bash $0 --stop"
        exit 0
    fi
fi

# Step 2: 同步代码
log_info ""
log_info "[2/6] 同步代码..."
cd "$DEVTOOLS_DIR"

if [ ! -d ".git" ]; then
    log_warn "不是 Git 仓库，尝试克隆..."
    git clone https://gitee.com/jonesAriven/devtools.git /tmp/devtools-sync
    rsync -a /tmp/devtools-sync/ "$DEVTOOLS_DIR/"
    rm -rf /tmp/devtools-sync
else
    REMOTE_URL=$(git remote get-url origin 2>/dev/null || echo "")
    if [ "$REMOTE_URL" != "https://gitee.com/jonesAriven/devtools.git" ]; then
        git remote set-url origin https://gitee.com/jonesAriven/devtools.git
    fi
    
    # 默认拉取 dev 分支
    BRANCH="${2:-dev}"
    log_info "拉取分支: ${BRANCH}"
    git fetch origin "${BRANCH}"
    git reset --hard "origin/${BRANCH}"
fi

log_success "代码已同步: $(git rev-parse --short HEAD)"

# Step 3: 创建 .env 文件
log_info ""
log_info "[3/6] 配置环境变量..."

if [ -f "$ENV_FILE" ] && [ "${1:-}" != "--quick" ]; then
    log_warn "发现已有 .env 文件"
    read -p "是否重新配置？(y/n, 默认n): " reconfig
    reconfig="${reconfig:-n}"
else
    reconfig="y"
fi

if [ "$reconfig" = "y" ] || [ ! -f "$ENV_FILE" ]; then
    echo ""
    echo "===== Jenkins 管理员密码 ====="
    read -p "管理员密码 (默认: admin@2024!): " admin_pass
    ADMIN_PASSWORD="${admin_pass:-admin@2024!}"
    
    echo ""
    echo "===== SSH 部署密码 ====="
    echo "mykng 主机 (100.93.36.113) 的 root 密码:"
    read -s -p "Password: " pass_mykng
    echo ""
    
    echo "内网 Debian (192.168.31.182) 的 root 密码:"
    read -s -p "Password: " pass_lan
    echo ""
    
    echo ""
    echo "===== Nexus 私服凭据 ====="
    read -p "Nexus 用户名 (默认: admin): " nexus_user
    NEXUS_USER="${nexus_user:-admin}"
    read -s -p "Nexus 密码: " nexus_pass
    echo ""
    
    echo ""
    echo "===== Gitee Token (可选，用于 Webhook) ====="
    read -p "Gitee Token (留空跳过): " gitee_token
    
    # 写入 .env
    cat > "$ENV_FILE" << EOF
# ====== Jenkins CI/CD 环境变量 ======
# 生成时间: $(date '+%Y-%m-%d %H:%M:%S')

# 管理员登录密码
ADMIN_PASSWORD=${ADMIN_PASSWORD}

# SSH 部署密码
DEPLOY_PASS_MYKNG=${pass_mykng}
DEPLOY_PASS_LAN=${pass_lan}

# Nexus 私服凭据
NEXUS_USER=${NEXUS_USER}
NEXUS_PASS=${nexus_pass}

# Gitee Token（可选，用于 Webhook 自动触发）
GITEE_TOKEN=${gitee_token}
EOF
    
    chmod 600 "$ENV_FILE"
    log_success ".env 文件已创建（权限 600）"
else
    log_success "使用现有 .env 文件"
fi

# Step 4: 启动 Jenkins
log_info ""
log_info "[4/6] 启动 Jenkins..."

cd "$JENKINS_DIR"

# 检查是否已运行
if docker ps | grep -q "$CONTAINER_NAME"; then
    log_info "Jenkins 已在运行，重新加载配置..."
    docker compose up -d --force-recreate
else
    log_info "首次启动，预计需要 3-5 分钟..."
    docker compose up -d
fi

# Step 5: 等待就绪
log_info ""
log_info "[5/6] 等待 Jenkins 就绪..."
echo ""

MAX_WAIT=300  # 最大等待5秒
WAITED=0
INTERVAL=10

while [ $WAITED -lt $MAX_WAIT ]; do
    if curl -sf "${JENKINS_URL}/login" > /dev/null 2>&1; then
        log_success "✅ Jenkins Web UI 已就绪！"
        break
    fi
    
    # 检查容器状态
    if ! docker ps | grep -q "$CONTAINER_NAME"; then
        log_error "Jenkins 容器已退出！查看日志: bash $0 --logs"
        exit 1
    fi
    
    ELAPSED=$((WAITED + INTERVAL))
    printf "\r  ⏳  等待中... %ds/%ds (%s)" "$ELAPSED" "$MAX_WAIT" "$(date +%H:%M:%S)"
    
    sleep $INTERVAL
    WAITED=$ELAPSED
done

echo ""

if [ $WAITED -ge $MAX_WAIT ]; then
    log_warn "等待超时，但 Jenkins 可能仍在初始化插件..."
    log_info "查看日志: bash $0 --logs"
    log_info "或稍后访问: ${JENKINS_URL}"
fi

# Step 6: 显示结果
log_info ""
log_info "[6/6] 部署完成！"
echo ""
echo "╔══════════════════════════════════════════════════╗"
echo "║                                                ║"
echo "║  🎉 Jenkins CI/CD 平台已就绪                    ║"
echo "║                                                ║"
echo "╠══════════════════════════════════════════════════╣"
echo "║                                                ║"
echo "║  🌐 访问地址:                                   ║"
echo "║     本地: http://localhost:${JENKINS_PORT}          ║"
echo "║     内网: http://$(hostname -I | awk '{print $1}'):${JENKINS_PORT}  ║"
echo "║     Tailscale: http://100.93.36.113:${JENKINS_PORT}     ║"
echo "║                                                ║"
echo "║  🔐 登录凭据:                                   ║"
echo "║     用户名: admin                               ║"
echo "║     密码码: (.env 中 ADMIN_PASSWORD)             ║"
echo "║                                                ║"
echo "║  📋 下一步操作:                                 ║"
echo "║     1. 打开浏览器访问上面的 URL                  ║"
echo "║     2. 登录 Jenkins                             ║"
echo "║     3. Configure System → SSH Servers → 添加目标主机 ║"
echo "║     4. 选择任务 → Build with Parameters → 构建! ║"
echo "║                                                ║"
echo "║  🛠️  常用命令:                                  ║"
echo "║     查看状态: bash $0 --status                   ║"
echo "║     查看日志: bash $0 --logs                     ║"
echo "║     停止服务: bash $0 --stop                      ║"
echo "║     重置系统: bash $0 --reset                     ║"
echo "║                                                ║"
echo "╚══════════════════════════════════════════════════╝"
echo ""
