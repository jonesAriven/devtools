#!/bin/bash
# ============================================================
# portal (门户系统) 部署脚本 — 在目标服务器上执行
# ============================================================
# 用法: bash deploy.sh <commit_sha> <branch>
# 示例: bash deploy.sh abc1234 dev
#
# 部署信息（基于实际服务器检查 2026-07-06）:
#   项目名: portal (门户系统，含前端+后端)
#   前端技术栈: Vue3 + Vite + Element Plus + TypeScript (pnpm)
#   后端技术栈: portal-server (Spring Boot, 端口 8087)
#   构建产物: 静态文件 (dist/)
#   部署方式: Nginx 静态部署
#
#   前端路径: /portal/ → alias /var/www/portal/
#   后端API: /portal/api/sys/ → proxy 127.0.0.1:8087
#   认证API: /portal/api/auth/ → proxy 127.0.0.1:8090 (Gateway)
# ============================================================

set -e

COMMIT_SHA="${1:-unknown}"
BRANCH="${2:-dev}"

echo "============================================="
echo "  🚪 portal 门户系统 — 自动部署"
echo "  时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo "  Commit: ${COMMIT_SHA}"
echo "  分支: ${BRANCH}"
echo "============================================="

# ======== 配置（基于实际服务器配置）=====
APP_DIR="/root/devtools/portal"
DIST_DIR="/root/devtools/portal/dist"
DEPLOY_DIR="/var/www/portal"  # Nginx 静态文件目录（已配置在 kb.conf）
BACKEND_PORT=8087            # portal-server 后端端口
CONTAINER_NAME="portal"
USE_NGINX=true                # 使用 Nginx 部署（默认）

# ======== 0. 环境准备 ========
echo ""
echo ">>> [0/4] 环境检查 <<<"

if [ ! -d /root/devtools ]; then
  echo "⚠️ 首次部署：克隆仓库..."
  git clone https://gitee.com/jonesAriven/devtools.git /root/devtools
fi

# 检查旧服务/容器
OLD_SERVICE=""
if [ -d "${DEPLOY_DIR}" ]; then
  OLD_SERVICE="nginx-static"
  echo "ℹ️ 发现旧静态部署目录: ${DEPLOY_DIR}"
  ls -la "${DEPLOY_DIR}/" | head -5
fi

# 检查 portal-server 是否运行
if pgrep -f "portal-server.*8087" > /dev/null 2>&1 || ss -tlnp | grep -q ":${BACKEND_PORT}"; then
  echo "ℹ️ 发现 portal-server 后端运行在端口 ${BACKEND_PORT}"
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

# ======== 2. 安装依赖 & 构建 ========
echo ""
echo ">>> [2/4] 安装依赖 & 构建 <<<"
cd "${APP_DIR}"

# 检查 Node.js
if ! command -v node &> /dev/null; then
  echo "❌ Node.js 未安装！请先安装 Node.js >= 18"
  exit 1
fi

echo "--- Node.js 版本 ---"
node -v
npm -v

# 安装依赖（使用 pnpm，因为项目有 pnpm-lock.yaml）
echo "--- 安装 pnpm 依赖 ---"
if [ ! -d "node_modules" ]; then
  if command -v pnpm &> /dev/null; then
    pnpm install --registry=https://registry.npmmirror.com
  else
    npm ci --registry=https://registry.npmmirror.com
  fi
else
  # 更新依赖
  if command -v pnpm &> /dev/null; then
    pnpm install --registry=https://registry.npmmirror.com 2>/dev/null || true
  else
    npm ci --registry=https://registry.npmmirror.com 2>/dev/null || true
  fi
fi

# 构建
echo "--- 构建 portal ---"
if command -v pnpm &> /dev/null; then
  pnpm build
else
  npm run build
fi

if [ ! -d "dist" ]; then
  echo "❌ 构建失败：dist 目录不存在"
  exit 1
fi

echo "✅ 构建完成"
ls -lh dist/ | head -10
du -sh dist/

# ======== 3. 停止旧服务 & 部署新版本 ========
echo ""
echo ">>> [3/4] 停止旧服务 & 部署新版本 <<<"

case "$OLD_SERVICE" in
  docker)
    echo "--- 停止旧 Docker 容器 ---"
    docker stop ${CONTAINER_NAME} 2>/dev/null || true
    docker rm -f ${CONTAINER_NAME} 2>/dev/null || true
    ;;
  process)
    echo "--- 停止旧进程 ---"
    pkill -f "portal.*serve" 2>/dev/null || true
    sleep 2
    ;;
  nginx-static)
    echo "--- 备份旧版本 ---"
    if [ -d "${DEPLOY_DIR}" ]; then
      mv "${DEPLOY_DIR}" "${DEPLOY_DIR}.bak.$(date +%s)" 2>/dev/null || true
    fi
    ;;
esac

# 部署新构建产物
if [ "$USE_NGINX" = true ]; then
  echo "--- 部署到 Nginx 目录 ---"
  
  # 创建部署目录
  mkdir -p "${DEPLOY_DIR}"
  
  # 复制构建产物
  cp -r dist/* "${DEPLOY_DIR}/"
  
  # 设置权限
  chmod -R 755 "${DEPLOY_DIR}"
  
  echo "✅ 已部署到 ${DEPLOY_DIR}"
else
  echo "--- 使用 Docker 部署 ---"
  
  # 创建临时 Dockerfile（如果没有的话）
  if [ ! -f "Dockerfile" ]; then
    cat > Dockerfile << 'EOF'
FROM node:20-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci --registry=https://registry.npmmirror.com
COPY . .
RUN npm run build

FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
EOF
  fi
  
  # 停止并删除旧容器
  docker stop ${CONTAINER_NAME} 2>/dev/null || true
  docker rm ${CONTAINER_NAME} 2>/dev/null || true
  
  # 构建并启动
  docker build -t portal:latest .
  docker run -d \
    --name ${CONTAINER_NAME} \
    --restart unless-stopped \
    -p "${APP_PORT}:80" \
    portal:latest
  
  sleep 3
  echo "✅ Docker 容器已启动"
fi

# ======== 4. 健康检查 ========
echo ""
echo ">>> [4/4] 健康检查 <<<"

if [ "$USE_NGINX" = true ]; then
  # Nginx 部署，检查静态文件
  CHECK_PORT=$(grep -r "listen" /etc/nginx/sites-enabled/* /etc/nginx/conf.d/* 2>/dev/null | grep -oP 'listen\s+\K\d+' | head -1 || echo "80")
  CHECK_URL="http://localhost:${CHECK_PORT:-80}"
else
  CHECK_URL="http://localhost:${APP_PORT}"
fi

MAX_RETRIES=5
for i in $(seq 1 $MAX_RETRIES); do
  if curl -sf "${CHECK_URL}/" > /dev/null 2>&1; then
    echo "✅ Portal 服务健康! (${CHECK_URL}) (尝试 $i/$MAX_RETRIES)"
    break
  fi
  
  if [ $i -eq $MAX_RETRIES ]; then
    echo "⚠️ 健康检查未通过，但部署已完成 ($i/$MAX_RETRIES)"
    echo "   请手动访问验证: ${CHECK_URL}"
    break
  fi
  
  echo "⏳ 等待服务就绪... ($i/$MAX_RETRIES)"
  sleep 5
done

echo ""
echo "============================================="
echo "  🚪 portal 门户系统 部署完成!"
echo "  Commit: $(cd /root/devtools && git rev-parse --short HEAD)"
echo "  时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo ""
echo "  📊 服务访问:"
if [ "$USE_NGINX" = true ]; then
  echo "    Portal: ${CHECK_URL} (Nginx代理)"
else
  echo "    Portal: http://localhost:${APP_PORT}"
fi
echo "============================================="
