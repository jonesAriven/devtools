#!/bin/bash
# ============================================================
# active-manager (激活码系统) 部署脚本 — 在目标服务器上执行
# ============================================================
# 用法: bash deploy.sh <commit_sha> <branch>
# 示例: bash deploy.sh abc1234 dev
#
# ⚠️ 实际部署架构（基于全链路检查 2026-07-06）:
#
#   访问地址: https://tools.marschat.online/activecode/
#   
#   ┌─────────────────────────────────────────────────────┐
#   │  公网用户                                           │
#   │  ↓                                                 │
#   │  腾讯云2号 Nginx (:443 SSL终结)                     │
#   │  ↓ tools.marschat.online                           │
#   │  反向代理到目标服务器 (FRP或直连)                    │
#   │  ↓                                                 │
#   │  目标服务器 Docker 容器                             │
#   │  activecode容器                                     │
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
COMPOSE_DIR="/root/devtools/active-manager/activation-code-server"
APP_PORT=18080              # 宿主机端口
CONTAINER_PORT=8080          # 容器内部端口
CONTAINER_NAME="activecode"

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

# ======== 1. 同步代码 ========
echo ""
echo ">>> [1/4] 同步代码 <<<"
cd /root/devtools

REMOTE_URL=$(git remote get-url origin 2>/dev/null || echo "")
if [ "$REMOTE_URL" != "https://gitee.com/jonesAriven/devtools.git" ]; then
  git remote set-url origin https://gitee.com/jonesAriven/devtools.git
fi

git fetch origin "${BRANCH}"
git reset --hard "origin/${BRANCH}"
echo "✅ 代码已同步到 $(git rev-parse --short HEAD)"

# 显示前端文件确认存在
echo ""
echo "--- 确认前端静态文件 ---"
if [ -f "${COMPOSE_DIR}/src/main/resources/static/activecode/login.html" ]; then
  echo "✅ 登录页存在: src/main/resources/static/activecode/login.html"
else
  echo "⚠️ 未找到登录页，将在构建时确认"
fi

# ======== 2. 停止并删除旧容器 ========
echo ""
echo ">>> [2/4] 停止旧服务 <<<"
cd "${COMPOSE_DIR}"

if docker ps -q --filter "name=${CONTAINER_NAME}" | grep -q .; then
  echo "--- 停止旧容器 ${CONTAINER_NAME} ---"
  # 使用 docker compose 停止（更优雅）
  docker compose -p "${PROJECT_NAME}" down 2>/dev/null || true
  sleep 5
  
  # 强制清理残留
  docker rm -f ${CONTAINER_NAME} 2>/dev/null || true
  
  # 确保端口释放
  if ss -tlnp | grep -q ":${APP_PORT} "; then
    echo "--- 等待端口释放 ---"
    sleep 3
  fi
  
  echo "✅ 旧容器已停止并删除"
else
  echo "ℹ️ 没有运行中的容器"
  
  # 如果端口被其他进程占用
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

MAX_RETRIES=12  # Java应用启动较慢，给更多时间
for i in $(seq 1 $MAX_RETRIES); do
  # 检查登录页面（静态资源）
  LOGIN_STATUS=$(curl -sf -o /dev/null -w "%{http_code}" http://localhost:${APP_PORT}/activecode/login.html 2>/dev/null || echo "000")
  
  # 检查API端点（后端）
  API_STATUS=$(curl -sf -o /dev/null -w "%{http_code}" -X POST http://localhost:${APP_PORT}/activecode/api/auth/login -H "Content-Type: application/json" -d '{}' 2>/dev/null || echo "000")
  
  if [ "${LOGIN_STATUS}" = "200" ] && [ "${API_STATUS}" != "000" ]; then
    echo "✅ 激活码服务完全健康!"
    echo "   - 登录页: HTTP ${LOGIN_STATUS} (尝试 $i/$MAX_RETRIES)"
    echo "   - API接口: HTTP ${API_STATUS}"
    break
  fi
  
  # 单独检查登录页
  if [ "${LOGIN_STATUS}" = "200" ]; then
    echo "✅ 登录页可访问 (HTTP ${LOGIN_STATUS})，等待API就绪... ($i/$MAX_RETRIES)"
  elif [ $i -eq $MAX_RETRIES ]; then
    echo "❌ 健康检查失败! ($i/$MAX_RETRIES)"
    echo "--- 最近日志 ---"
    docker logs --tail=40 ${CONTAINER_NAME}
    exit 1
  else
    echo "⏳ 等待服务启动... ($i/$MAX_RETRIES) [登录页: ${LOGIN_STATUS}, API: ${API_STATUS}]"
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
