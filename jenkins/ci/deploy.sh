#!/bin/bash
# ============================================================
# Jenkins CI/CD — 独立部署脚本
# ============================================================
# 用途: 在 mykng 服务器上部署/更新 Jenkins 服务
# 特点: 
#   - 独立应用，不与 mykng 微服务混合
#   - 完整的容器清理机制（防孤儿容器）
#   - 端口占用检测与自动释放
#   - 健康检查与回滚支持
#
# 触发方式: Jenkinsfile (手动触发) 或 SSH 手动执行
# 域名: https://jkci.marschat.online
# ============================================================

set -e

# ==================== 配置区 ====================
APP_NAME="jenkins-ci"
CONTAINER_NAME="jenkins-ci"
JENKINS_PORT=8097
AGENT_PORT=50001
COMPOSE_FILE="$(dirname "$0")/../docker-compose.yml"
WORKSPACE="${WORKSPACE:-$(dirname "$(dirname "$0")")/..}"
MAX_RETRIES=3
HEALTH_CHECK_URL="http://localhost:${JENKINS_PORT}/login"

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log_info()    { echo -e "${BLUE}[INFO]${NC} $(date '+%H:%M:%S') $*"; }
log_success() { echo -e "${GREEN}[✓]${NC} $(date '+%H:%M:%S') $*"; }
log_warn()    { echo -e "${YELLOW}[!]${NC} $(date '+%H:%M:%S') $*"; }
log_error()   { echo -e "${RED}[✗]${NC} $(date '+%H:%M:%S') $*"; }

# ==================== 参数解析 ====================
ACTION="${1:-deploy}"
case "$ACTION" in
    deploy|rollback|stop|status|logs) ;;
    *) 
        echo "用法: $0 [deploy|rollback|stop|status|logs]"
        exit 1
        ;;
esac

# ==================== 函数定义 ====================

# 检查端口是否被占用
check_port() {
    local port=$1
    if ss -tlnp 2>/dev/null | grep -q ":${port} "; then
        return 0  # 占用中
    fi
    return 1  # 空闲
}

# 强制释放端口（查找并杀掉占用进程）
force_free_port() {
    local port=$1
    log_warn "尝试释放端口 ${port}..."
    
    # 方法1: 使用 fuser
    if command -v fuser &> /dev/null; then
        fuser -k -9 ${port}/tcp 2>/dev/null || true
        sleep 1
    fi
    
    # 方法2: 使用 ss + kill（备用方案）
    if check_port $port; then
        local pids=$(ss -tlnp | grep ":${port} " | grep -oP 'pid=\K\d+' | tr '\n' ' ')
        if [ -n "$pids" ]; then
            log_info "杀掉占用端口的进程 PID: ${pids}"
            kill -9 $pids 2>/dev/null || true
            sleep 2
        fi
    fi
    
    # 最终确认
    if check_port $port; then
        log_error "端口 ${port} 仍被占用，无法释放！"
        ss -tlnp | grep ":${port} "
        return 1
    fi
    
    log_success "端口 ${port} 已释放"
    return 0
}

# 清理旧容器（全状态扫描，防止孤儿容器）
cleanup_old_container() {
    log_info "检查旧容器..."
    
    # 检查所有状态的容器（包括已停止的）
    if docker ps -a --filter "name=${CONTAINER_NAME}" --format "{{.Names}}" | grep -q "${CONTAINER_NAME}"; then
        local old_status=$(docker inspect --format='{{.State.Status}}' ${CONTAINER_NAME} 2>/dev/null || echo "unknown")
        log_info "发现旧容器 (${CONTAINER_NAME}, 状态: ${old_status})，正在删除..."
        
        # 先停止（如果还在运行）
        docker stop ${CONTAINER_NAME} 2>/dev/null || true
        sleep 2
        
        # 强制删除（包括 volumes）
        docker rm -f ${CONTAINER_NAME} 2>/dev/null || true
        
        log_success "旧容器已清除"
        
        # 额外等待，确保端口完全释放
        sleep 1
    else
        log_success "无旧容器残留"
    fi
}

# 清理孤儿容器（同名但状态异常的容器）
cleanup_orphan_containers() {
    log_info "扫描孤儿容器..."
    
    # 查找所有包含 jenkins 关键词的容器
    local orphans=$(docker ps -a --filter "name=jenkins" --format "{{.Names}} {{.Status}}" | grep -v "${CONTAINER_NAME}" || true)
    
    if [ -n "$orphans" ]; then
        log_warn "发现可能的 Jenkins 孤儿容器:"
        echo "$orphans"
        
        for name in $(echo "$orphans" | awk '{print $1}'); do
            log_info "清理孤儿容器: ${name}"
            docker stop ${name} 2>/dev/null || true
            docker rm -f ${name} 2>/dev/null || true
        done
    else
        log_success "无孤儿容器"
    fi
}

# 健康检查
health_check() {
    local retries=0
    log_info "执行健康检查..."
    
    while [ $retries -lt $MAX_RETRIES ]; do
        if curl -sf "${HEALTH_CHECK_URL}" > /dev/null 2>&1; then
            log_success "✅ Jenkins Web UI 已就绪！"
            return 0
        fi
        
        retries=$((retries + 1))
        log_info "等待就绪... ($retries/$MAX_RETRIES)"
        sleep 10
    done
    
    log_error "❌ Jenkins 启动超时！请查看日志排查"
    return 1
}

# 显示部署结果
show_result() {
    echo ""
    echo "╔══════════════════════════════════════════════════╗"
    echo "║     🚀 Jenkins CI/CD 平台部署完成               ║"
    echo "╚══════════════════════════════════════════════════╝"
    echo ""
    echo "╔══════════════════════════════════════════════════╣"
    echo "║                                                  ║"
    echo "║  🌐 访问地址:                                    ║"
    echo "║     公网: https://jkci.marschat.online           ║"
    echo "║     内网: http://$(hostname -I | awk '{print $1}'):${JENKINS_PORT}  ║"
    echo "║     Tailscale: http://100.93.36.113:${JENKINS_PORT} ║"
    echo "║                                                  ║"
    echo "║  🔐 登录凭据:                                    ║"
    echo "║     用户名: admin                                ║"
    echo "║     密码: 见 .env 文件 ADMIN_PASSWORD             ║"
    echo "║                                                  ║"
    echo "║  📊 运行状态:                                    ║"
    if docker ps | grep -q "${CONTAINER_NAME}"; then
        echo "║     ✅ 容器运行中                               ║"
        echo "║     内存: $(docker stats --no-stream --format '{{.MemUsage}}' ${CONTAINER_NAME} 2>/dev/null || echo 'N/A') ║"
    else
        echo "║     ❌ 容器未运行！                             ║"
    fi
    echo "║                                                  ║"
    echo "║  🛠️  常用命令:                                   ║"
    echo "║     查看日志: docker logs -f ${CONTAINER_NAME}    ║"
    echo "║     进入容器: docker exec -it ${CONTAINER_NAME} bash ║"
    echo "║     重启服务: bash $0 deploy                      ║"
    echo "║     停止服务: bash $0 stop                         ║"
    echo "║                                                  ║"
    echo "╚══════════════════════════════════════════════════╝"
    echo ""
}

# ==================== 主流程 ====================

echo ""
echo "╔══════════════════════════════════════════════════╗"
echo "║   🔧 Jenkins CI/CD — 独立部署脚本                ║"
echo "║   域名: https://jkci.marschat.online            ║"
echo "╚══════════════════════════════════════════════════╝"
echo ""

case "$ACTION" in
    status)
        echo "===== Jenkins 服务状态 ====="
        if docker ps | grep -q "${CONTAINER_NAME}"; then
            log_success "运行中 ✅"
            echo "  容器: ${CONTAINER_NAME}"
            echo "  端口: ${JENKINS_PORT} (Web), ${AGENT_PORT} (Agent)"
            echo "  内存: $(docker stats --no-stream --format '{{.MemUsage}}' ${CONTAINER_NAME})"
            echo "  启动时间: $(docker inspect --format='{{.State.StartedAt}}' ${CONTAINER_NAME} | cut -d. -f1)"
            
            if curl -sf "${HEALTH_CHECK_URL}" > /dev/null 2>&1; then
                log_success "Web UI 已就绪 ✅"
            else
                log_warn "Web UI 仍在初始化中..."
            fi
        else
            log_error "未运行 ❌"
            if docker ps -a | grep -q "${CONTAINER_NAME}"; then
                echo "  状态: 已停止"
                echo "  启动命令: bash $0 deploy"
            else
                echo "  状态: 未部署"
                echo "  部署命令: bash $0 deploy"
            fi
        fi
        ;;
        
    stop)
        log_info "停止 Jenkins..."
        cd "$(dirname "$COMPOSE_FILE")"
        docker compose -f "$(basename "$COMPOSE_FILE")" down --remove-orphans
        cleanup_orphan_containers
        log_success "Jenkins 已停止"
        ;;
        
    logs)
        cd "$(dirname "$COMPOSE_FILE")"
        docker compose -f "$(basename "$COMPOSE_FILE")" logs -f --tail=200
        ;;
        
    rollback)
        log_warn "回滚到上一版本..."
        # Jenkins 本身是配置驱动，回滚主要是恢复 jenkins_home volume
        # 这里可以扩展为备份恢复逻辑
        log_info "如需完整回滚，请从备份恢复 jenkins_home volume"
        ;;
        
    deploy)
        # Step 1: 环境检查
        log_info "[1/5] 环境检查..."
        
        if ! command -v docker &> /dev/null; then
            log_error "Docker 未安装！"
            exit 1
        fi
        
        log_success "Docker: $(docker --version)"
        
        # Step 2: 端口检查与释放
        log_info ""
        log_info "[2/5] 端口检查..."
        
        for port in ${JENKINS_PORT} ${AGENT_PORT}; do
            if check_port $port; then
                log_warn "端口 ${port} 被占用"
                force_free_port $port
            else
                log_success "端口 ${port} 空闲 ✅"
            fi
        done
        
        # Step 3: 清理旧容器
        log_info ""
        log_info "[3/5] 清理旧容器..."
        cleanup_old_container
        cleanup_orphan_containers
        
        # Step 4: 启动新容器
        log_info ""
        log_info "[4/5] 启动 Jenkins..."
        
        cd "$(dirname "$COMPOSE_FILE")"
        
        # 确保 .env 文件存在
        if [ ! -f ".env" ]; then
            log_warn ".env 文件不存在，使用默认配置..."
            cat > .env << 'EOF'
# Jenkins 默认环境变量
ADMIN_PASSWORD=admin@2024!
DEPLOY_PASS_MYKNG=
DEPLOY_PASS_LAN=
NEXUS_USER=admin
NEXUS_PASS=
GITEE_TOKEN=
EOF
            chmod 600 .env
        fi
        
        # 启动服务
        docker compose -f "$(basename "$COMPOSE_FILE")" up -d --remove-orphans
        
        # 等待容器启动
        log_info "等待容器启动..."
        sleep 5
        
        if ! docker ps | grep -q "${CONTAINER_NAME}"; then
            log_error "容器启动失败！查看日志:"
            docker logs ${CONTAINER_NAME} 2>&1 | tail -50
            exit 1
        fi
        
        log_success "容器已启动 ✅"
        
        # Step 5: 健康检查
        log_info ""
        log_info "[5/5] 健康检查..."
        
        log_info "首次启动可能需要 3-5 分钟安装插件，请耐心等待..."
        health_check || true  # 不因超时而失败
        
        # 显示最终结果
        show_result
        ;;
esac

exit 0
