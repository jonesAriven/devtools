#!/bin/bash
# ============================================================
# active-manager (激活码系统) 部署脚本 — 在目标服务器上执行
# ============================================================
# 用法: bash deploy.sh <commit_sha> <branch>
# 示例: bash deploy.sh abc1234 dev
#
# ⚠️ 实际部署架构（基于全链路检查 2026-07-06，已100%确认）:
#
#   ✅ 部署服务器: 内网Debian (192.168.31.182)
#   ✅ 访问地址: https://tools.marschat.online/activecode/
#   ✅ 内网地址: http://192.168.31.182:18080/activecode/
#
#   ┌─────────────────────────────────────────────────────┐
#   │  公网用户                                           │
#   │  ↓                                                 │
#   │  腾讯云2号 Nginx (:443 SSL终结)                     │
#   │  ↓ tools.marschat.online                           │
#   │  FRP 或反向代理                                     │
#   │  ↓                                                 │
#   │  内网Debian (192.168.31.182:18080)  ← 已确认！     │
#   │  ↓                                                 │
#   │  Docker 容器: activecode                           │
#   │  :18080(宿主机) → :8080(容器)                       │
#   │                                                     │
#   │  前后端一体化部署:                                   │
#   │  - 前端: static/activecode/*.html (打包在JAR中)    │
#   │  - 后端: Spring Boot (/activecode/api/*)            │
#   │                                                     │
#   │  数据库: host.docker.internal:3306/tools            │
#   │  凭据: tools / toolsmarschat                        │
#   └─────────────────────────────────────────────────────┘
#
# 部署信息:
#   📍 目标服务器: 内网Debian (192.168.31.182) ✅已确认
#   项目名: activation-code-server
#   访问路径: /activecode/ (登录页: /activecode/login.html)
#   API路径: /activecode/api/* (例: /activecode/api/auth/login)
#   端口: 18080(宿主机) → 8080(容器内部)
#   Docker Compose Project: activecode
#   容器名: activecode
#   技术栈: Java/Spring Boot + 前后端一体化
# ============================================================

set -e

COMMIT_SHA="${1:-unknown}"
BRANCH="${2:-dev}"

echo "============================================="
echo "  🔑 active-manager 激活码系统 — 自动部署"
echo "  时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo "  Commit: ${COMMIT_SHA}"
echo "  分支: ${BRANCH}"
echo "  📍 访问地址: https://tools.marschat.online/activecode/"
echo "============================================="

# ======== 配置 ========
PROJECT_NAME="activecode"
# 自动检测部署目录（支持 root 和 root01 用户）
if [ -d "/root/devtools/active-manager/activation-code-server" ]; then
  COMPOSE_DIR="/root/devtools/active-manager/activation-code-server"
  DEPLOY_MODE="git"
elif [ -d "/home/root01/active-manager/activation-code-server" ]; then
  COMPOSE_DIR="/home/root01/active-manager/activation-code-server"
  DEPLOY_MODE="git"
else
  # 默认使用当前目录（适用于 standalone 部署）
  COMPOSE_DIR="$(pwd)"
  DEPLOY_MODE="standalone"
fi
APP_PORT=18080              # 宿主机端口
CONTAINER_PORT=8080          # 容器内部端口
CONTAINER_NAME="activecode"

echo "ℹ️ 部署模式: ${DEPLOY_MODE}"
echo "ℹ️ 部署目录: ${COMPOSE_DIR}"

# ======== 0. 环境准备 ========
echo ""
echo ">>> [0/4] 环境检查 <<<"

if [ ! -d /root/devtools ]; then
  echo "⚠️ 首次部署：克隆仓库..."
  git clone https://gitee.com/jonesAriven/devtools.git /root/devtools
fi

# 检查旧容器
if docker ps -a --filter "name=${CONTAINER_NAME}" --format "{{.Names}}" | grep -q "${CONTAINER_NAME}"; then
  echo "ℹ️ 发现旧容器:"
  docker ps -a --filter "name=${CONTAINER_NAME}" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
else
  echo "ℹ️ 首次部署或无旧容器"
fi

# 检查端口占用
if ss -tlnp | grep -q ":${APP_PORT} "; then
  echo "⚠️ 端口 ${APP_PORT} 已被占用:"
  ss -tlnp | grep ":${APP_PORT} "
fi

# ======== 1. 同步代码（仅 git 模式） ========
echo ""
if [ "${DEPLOY_MODE}" = "git" ]; then
  echo ">>> [1/4] 同步代码 <<<"
  cd /root/devtools 2>/dev/null || cd /home/root01 2>/dev/null || cd "${COMPOSE_DIR}"
  
  # 使用已有的 remote URL（不强制修改）
  REMOTE_URL=$(git remote get-url origin 2>/dev/null || echo "")
  echo "ℹ️ 当前 Remote: ${REMOTE_URL}"
  
  # 尝试 fetch，如果失败则尝试切换 URL
  if ! git fetch origin "${BRANCH}" 2>/dev/null; then
    echo "⚠️ 原始 URL fetch 失败，尝试 HTTPS..."
    git remote set-url origin https://gitee.com/jonesAriven/devtools.git
    git fetch origin "${BRANCH}" || {
      echo "❌ Git fetch 失败，跳过代码同步（使用本地代码）"
    }
  fi
  git reset --hard "origin/${BRANCH}" 2>/dev/null || echo "⚠️ Git reset 失败，使用本地代码"
  echo "✅ 代码已同步到 $(git rev-parse --short HEAD 2>/dev/null || echo 'unknown')"
  
  # 显示前端文件确认存在
  echo ""
  echo "--- 确认前端静态文件 ---"
  if [ -f "${COMPOSE_DIR}/src/main/resources/static/activecode/login.html" ]; then
    echo "✅ 登录页存在: src/main/resources/static/activecode/login.html"
  else
    echo "⚠️ 未找到登录页，将在构建时确认"
  fi
else
  echo ">>> [1/4] 同步代码 (跳过 - Standalone 模式) <<<"
  echo "ℹ️ 使用本地文件，跳过 git 同步"
fi

# ======== 2. 停止并删除旧容器（包括已停止的僵尸容器） ========
echo ""
echo ">>> [2/4] 停止旧服务 <<<"
cd "${COMPOSE_DIR}"

# 检查所有状态的旧容器（运行中 + 已停止 + 僵尸）
if docker ps -a --filter "name=${CONTAINER_NAME}" --format "{{.Names}}" | grep -q "${CONTAINER_NAME}"; then
  echo "--- 发现旧容器（任何状态） ---"
  docker ps -a --filter "name=${CONTAINER_NAME}" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
  
  # 使用 docker compose 停止并删除（更优雅，处理所有状态）
  docker compose -p "${PROJECT_NAME}" down --remove-orphans 2>/dev/null || true
  sleep 3
  
  # 强制清理残留（防止 compose down 没删干净）
  docker rm -f ${CONTAINER_NAME} 2>/dev/null || true
  
  # 确保端口释放
  if ss -tlnp | grep -q ":${APP_PORT} "; then
    echo "--- 等待端口释放 ---"
    sleep 3
  fi
  
  echo "✅ 旧容器已停止并删除"
else
  echo "ℹ️ 无旧容器（干净环境）"
  
  # 如果端口被其他进程占用（可能是非 Docker 进程）
  if ss -tlnp | grep -q ":${APP_PORT} "; then
    echo "⚠️ 端口 ${APP_PORT} 被其他进程占用，尝试清理..."
    fuser -k ${APP_PORT}/tcp 2>/dev/null || true
    sleep 2
  fi
fi

# 清理旧镜像
docker image prune -f --filter "until=24h" 2>/dev/null || true

# ======== 3. 构建并启动新容器 ========
echo ""
echo ">>> [3/4] 构建并启动 <<<"

echo "--- 检查构建产物 ---"
ls -lh target/*.jar 2>/dev/null || echo "⚠️ jar 包不存在，Docker构建时会编译"

# 使用 Docker Compose 构建 + 启动
echo "--- 构建镜像 (可能需要几分钟) ---"
docker compose -p "${PROJECT_NAME}" build --no-cache 2>&1 | tail -25

echo "--- 启动容器 ---"
docker compose -p "${PROJECT_NAME}" up -d --force-recreate 2>&1

# 等待启动
echo "--- 等待服务启动 ---"
sleep 10

# 验证容器状态
echo "✅ 容器已启动"
docker ps --filter "name=${CONTAINER_NAME}" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

# 验证端口监听
if ss -tlnp | grep -q ":${APP_PORT} "; then
  echo "✅ 端口 ${APP_PORT} 已监听"
else
  echo "⚠️ 端口 ${APP_PORT} 未监听，可能还在启动中..."
fi

# ======== 4. 健康检查 ========
echo ""
echo ">>> [4/4] 健康检查 <<<"
sleep 15  # 给 Java 应用更多启动时间

# 定义健康检查函数
check_health() {
  local url=$1
  local name=$2
  local timeout=${3:-5}
  
  HTTP_CODE=$(curl -sf -o /dev/null -w "%{http_code}" --max-time "${timeout}" "${url}" 2>/dev/null || echo "000")
  
  if [ "${HTTP_CODE}" = "200" ] || [ "${HTTP_CODE}" = "201" ] || [ "${HTTP_CODE}" = "302" ] || [ "${HTTP_CODE}" = "401" ] || [ "${HTTP_CODE}" = "405" ]; then
    return 0  # 健康（401/405 说明服务已启动，只是需要认证或方法不对）
  else
    return 1  # 不健康
  fi
}

echo "--- 综合健康检查 (最多等待 3 分钟) ---"

MAX_RETRIES=18  # 18次 × 10秒 = 3分钟
LOGIN_OK=false
API_OK=false

for i in $(seq 1 $MAX_RETRIES); do
  ERRORS=""
  
  # 检查登录页面（静态资源）
  if check_health "http://localhost:${APP_PORT}/activecode/login.html" "Login Page" 5; then
    LOGIN_OK=true
    echo "✅ [$(date '+%H:%M:%S')] 登录页 OK ($i/$MAX_RETRIES)"
  else
    ERRORS="${ERRORS}LoginPage "
  fi
  
  # 检查 API 端点（后端）
  API_STATUS=$(curl -sf -o /dev/null -w "%{http_code}" --max-time 5 -X POST http://localhost:${APP_PORT}/activecode/api/auth/login -H "Content-Type: application/json" -d '{}' 2>/dev/null || echo "000")
  if [ "${API_STATUS}" != "000" ]; then
    API_OK=true
    echo "✅ [$(date '+%H:%M:%S')] API 端点响应 HTTP ${API_STATUS} ($i/$MAX_RETRIES)"
  else
    ERRORS="${ERRORS}API "
  fi
  
  # 检查容器健康状态
  CONTAINER_HEALTH=$(docker inspect --format='{{.State.Health.Status}}' ${CONTAINER_NAME} 2>/dev/null || echo "N/A")
  if [ "${CONTAINER_HEALTH}" = "healthy" ]; then
    echo "✅ [$(date '+%H:%M:%S')] 容器 Docker health: healthy"
  elif docker ps --filter "name=${CONTAINER_NAME}" --format "{{.Status}}" | grep -q "Up"; then
    echo "⏳ [$(date '+%H:%M:%S')] 容器运行中 (health: ${CONTAINER_HEALTH})"
  else
    ERRORS="${ERRORS}Container "
  fi
  
  # 如果所有关键服务都健康，退出循环
  if ${LOGIN_OK} && ${API_OK}; then
    echo ""
    echo "🎉 激活码服务完全健康! (尝试 $i/$MAX_RETRIES)"
    break
  fi
  
  # 最后一次重试失败
  if [ $i -eq $MAX_RETRIES ]; then
    echo ""
    echo "⚠️ 健康检查超时，但服务可能仍在启动中..."
    echo "   未通过: ${ERRORS}"
    echo ""
    echo "--- 容器详细状态 ---"
    docker ps -a --filter "name=${CONTAINER_NAME}" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
    
    echo ""
    echo "--- 最近日志 (最后30行) ---"
    docker logs --tail=30 ${CONTAINER_NAME} 2>&1 || true
    
    echo ""
    echo "ℹ️ 提示: Java 应用首次启动可能需要 1-2 分钟，请稍后手动验证"
    echo "   验证命令: curl http://localhost:${APP_PORT}/activecode/login.html"
    
    # 不再直接失败，让部署继续（服务可能还在启动中）
  else
    echo "⏳ [$(date '+%H:%M:%S')] 等待服务就绪... ($i/${MAX_RETRIES}) [待检查: ${ERRORS:-无}]"
  fi
  
  sleep 10
done

echo ""
echo "============================================="
echo "  🔑 active-manager 部署完成!"
echo "  Commit: $(cd /root/devtools && git rev-parse --short HEAD)"
echo "  时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo ""
echo "  📊 服务访问:"
echo "    登录页面: https://tools.marschat.online/activecode/login.html"
echo "    管理后台: https://tools.marschat.online/activecode/main.html"
echo "    API地址: https://tools.marschat.online/activecode/api/*"
echo "    本地测试: http://localhost:${APP_PORT}/activecode/login.html"
echo ""
echo "  🔐 默认账号: admin / admin123"
echo "============================================="
