#!/bin/bash
# ============================================================
# portal (门户系统) 部署脚本 — 在 mykng 上执行
# ============================================================
# 用法: bash deploy.sh <commit_sha> <branch>
# 示例: bash deploy.sh abc1234 dev
#
# ⚠️ 实际部署架构（基于服务器检查 2026-07-06）:
#
#   ┌──────────────────────────────────────────────────┐
#   │  Nginx (:80)                                     │
#   │  /portal/          → /var/www/portal (前端静态)  │
#   │  /portal/api/auth/ → :8090/kb/api/auth/ (Gateway)│
#   │  /portal/api/sys/  → :8087 (后端API)             │
#   └────────┬─────────────────────┬───────────────────┘
#            │                     │
#            ▼                     ▼
#   ┌────────────────┐  ┌────────────────────┐
#   │ 前端静态文件    │  │ portal-server      │
#   │ /var/www/portal│  │ :8087 (直接进程)    │
#   │ (Vue3构建产物) │  │ JAR: /opt/portal-   │
#   │                │  │ server/portal-server│
#   │                │  │ .jar               │
#   └────────────────┘  └────────────────────┘
#
# 部署流程:
#   1. 构建前端 (pnpm build)
#   2. 部署前端到 /var/www/portal/
#   3. Maven 构建后端 portal-server
#   4. 停止旧后端进程 + 复制新JAR到 /opt/portal-server/
#   5. 启动新后端进程 (端口8087)
#   6. 健康检查
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

# ======== 配置（基于实际服务器配置）========
APP_DIR="/root/devtools/portal"
FRONTEND_DIR="${APP_DIR}"              # 前端根目录（package.json在此）
BACKEND_DIR="${APP_DIR}/portal-server"  # 后端目录
DEPLOY_DIR="/var/www/portal"            # 前端部署目录（Nginx alias）
BACKEND_DEPLOY_DIR="/opt/portal-server"  # 后端JAR部署目录
BACKEND_PORT=8087                       # 后端端口
JAR_NAME="portal-server.jar"

# ======== 0. 环境准备 ========
echo ""
echo ">>> [0/6] 环境检查 <<<"

if [ ! -d /root/devtools ]; then
  echo "⚠️ 首次部署：克隆仓库..."
  git clone https://gitee.com/jonesAriven/devtools.git /root/devtools
fi

# 检查旧前端部署
if [ -d "${DEPLOY_DIR}" ]; then
  echo "ℹ️ 发现旧前端部署:"
  ls -la "${DEPLOY_DIR}/" | head -5
fi

# 检查旧后端进程
OLD_BACKEND_PID=""
if ss -tlnp | grep -q ":${BACKEND_PORT} "; then
  OLD_BACKEND_PID=$(ss -tlnp | grep ":${BACKEND_PORT} " | grep -oP 'pid=\K\d+' | head -1)
  echo "ℹ️ 发现旧后端进程运行在端口 ${BACKEND_PORT} (PID: ${OLD_BACKEND_PID})"
fi

# ======== 1. 同步代码 ========
echo ""
echo ">>> [1/6] 同步代码 <<<"
cd /root/devtools

REMOTE_URL=$(git remote get-url origin 2>/dev/null || echo "")
if [ "$REMOTE_URL" != "https://gitee.com/jonesAriven/devtools.git" ]; then
  git remote set-url origin https://gitee.com/jonesAriven/devtools.git
fi

git fetch origin "${BRANCH}"
git reset --hard "origin/${BRANCH}"
echo "✅ 代码已同步到 $(git rev-parse --short HEAD)"

# ======== 2. 构建前端 ========
echo ""
echo ">>> [2/6] 构建前端 <<<"
cd "${FRONTEND_DIR}"

# 检查 Node.js
if ! command -v node &> /dev/null; then
  echo "❌ Node.js 未安装！请先安装 Node.js >= 18"
  exit 1
fi

echo "--- Node.js 版本 ---"
node -v
npm -v

# 安装依赖（使用 pnpm，项目有 pnpm-lock.yaml）
echo "--- 安装前端依赖 ---"
if [ ! -d "node_modules" ]; then
  if command -v pnpm &> /dev/null && [ -f "pnpm-lock.yaml" ]; then
    pnpm install --registry=https://registry.npmmirror.com
  else
    npm ci --registry=https://registry.npmmirror.com
  fi
else
  if command -v pnpm &> /dev/null && [ -f "pnpm-lock.yaml" ]; then
    pnpm install --registry=https://registry.npmmirror.com 2>/dev/null || true
  else
    npm ci --registry=https://registry.npmmirror.com 2>/dev/null || true
  fi
fi

# 构建
echo "--- 构建 portal 前端 ---"
if command -v pnpm &> /dev/null && [ -f "pnpm-lock.yaml" ]; then
  pnpm build
else
  npm run build
fi

if [ ! -d "dist" ]; then
  echo "❌ 前端构建失败：dist 目录不存在"
  exit 1
fi

echo "✅ 前端构建完成"
ls -lh dist/ | head -10
du -sh dist/

# ======== 3. 部署前端 ========
echo ""
echo ">>> [3/6] 部署前端 <<<"

# 备份旧版本
if [ -d "${DEPLOY_DIR}" ]; then
  echo "--- 备份旧前端版本 ---"
  mv "${DEPLOY_DIR}" "${DEPLOY_DIR}.bak.$(date +%s)" 2>/dev/null || true
fi

# 部署新版本
echo "--- 部署新前端到 ${DEPLOY_DIR} ---"
mkdir -p "${DEPLOY_DIR}"
cp -r dist/* "${DEPLOY_DIR}/"
chmod -R 755 "${DEPLOY_DIR}"

echo "✅ 前端已部署到 ${DEPLOY_DIR}"

# ======== 4. Maven 构建后端 ========
echo ""
echo ">>> [4/6] 构建后端 portal-server <<<"

if [ -d "${BACKEND_DIR}" ] && [ -f "${BACKEND_DIR}/pom.xml" ]; then
  cd "${BACKEND_DIR}"
  
  echo "--- 检查 Maven ---"
  if command -v mvn &> /dev/null; then
    echo "--- Maven 构建后端 ---"
    mvn clean package -DskipTests -q
    
    # 查找 JAR
    BACKEND_JAR=$(find target -name "portal-server*.jar" ! -name "*sources.jar" | head -1)
    
    if [ -z "$BACKEND_JAR" ]; then
      echo "⚠️ 未找到 portal-server JAR，跳过后端部署"
    else
      echo "✅ 后端构建完成: ${BACKEND_JAR}"
      ls -lh "${BACKEND_JAR}"
    fi
  else
    echo "⚠️ Maven 未安装，跳过后端构建"
    BACKEND_JAR=""
  fi
else
  echo "⚠️ 后端目录不存在或无 pom.xml，跳过后端构建"
  BACKEND_JAR=""
fi

# ======== 5. 部署后端（如果有JAR） — 防止孤儿Java进程 ========
echo ""
echo ">>> [5/6] 部署后端 <<<"

if [ -n "$BACKEND_JAR" ] && [ -f "$BACKEND_JAR" ]; then
  echo "--- 清理旧后端进程（防止孤儿 Java 进程） ---"
  
  # 方法1: 使用记录的 PID
  if [ -n "$OLD_BACKEND_PID" ] && ps -p ${OLD_BACKEND_PID} > /dev/null 2>&1; then
    echo "--- 停止旧后端进程 (PID: ${OLD_BACKEND_PID}) ---"
    kill ${OLD_BACKEND_PID} 2>/dev/null || true
    sleep 3
  fi
  
  # 方法2: 按端口查找（更可靠，覆盖 PID 变化的情况）
  if ss -tlnp | grep -q ":${BACKEND_PORT} "; then
    echo "--- 端口 ${BACKEND_PORT} 仍被占用，按端口清理 ---"
    # 获取占用端口的进程信息
    PORT_INFO=$(ss -tlnp | grep ":${BACKEND_PORT} ")
    echo "  ${PORT_INFO}"
    
    # 提取并杀掉所有占用该端口的 PID
    PORT_PIDS=$(ss -tlnp | grep ":${BACKEND_PORT} " | grep -oP 'pid=\K\d+' | tr '\n' ' ')
    if [ -n "$PORT_PIDS" ]; then
      echo "  杀掉进程 PIDs: ${PORT_PIDS}"
      for pid in $PORT_PIDS; do
        kill ${pid} 2>/dev/null || true
      done
      sleep 3
    fi
    
    # 强制清理（如果还在占用）
    if ss -tlnp | grep -q ":${BACKEND_PORT} "; then
      echo "  强制终止残留 (-9)..."
      fuser -k -9 ${BACKEND_PORT}/tcp 2>/dev/null || true
      sleep 2
    fi
    
    # 最终确认端口释放
    if ss -tlnp | grep -q ":${BACKEND_PORT} "; then
      echo "❌ 端口 ${BACKEND_PORT} 无法释放！请手动排查:"
      ss -tlnp | grep ":${BACKEND_PORT} "
      # 不退出，让用户知道，但继续部署前端
    else
      echo "✅ 端口 ${BACKEND_PORT} 已释放"
    fi
  fi
  
  # 确保 deploy 目录存在
  mkdir -p "${BACKEND_DEPLOY_DIR}"
  
  # 备份旧 JAR
  if [ -f "${BACKEND_DEPLOY_DIR}/${JAR_NAME}" ]; then
    echo "--- 备份旧后端 JAR ---"
    cp "${BACKEND_DEPLOY_DIR}/${JAR_NAME}" "${BACKEND_DEPLOY_DIR}/${JAR_NAME}.bak.$(date +%s)" 2>/dev/null || true
  fi
  
  # 复制新 JAR
  echo "--- 部署新后端 JAR 到 ${BACKEND_DEPLOY_DIR} ---"
  cp "${BACKEND_JAR}" "${BACKEND_DEPLOY_DIR}/${JAR_NAME}"
  chmod 644 "${BACKEND_DEPLOY_DIR}/${JAR_NAME}"
  
  # 启动新进程（后台运行）
  echo "--- 启动 portal-server (端口 ${BACKEND_PORT}) ---"
  nohup java -Xms128m -Xmx256m -XX:+UseG1GC -Dfile.encoding=UTF-8 \
    -jar "${BACKEND_DEPLOY_DIR}/${JAR_NAME}" \
    > "${BACKEND_DEPLOY_DIR}/portal-server.log" 2>&1 &
  
  NEW_PID=$!
  echo "✅ 后端已启动 (PID: ${NEW_PID})"
  sleep 5
  
  # 验证启动
  if ps -p ${NEW_PID} > /dev/null 2>&1; then
    echo "✅ 进程存活"
  else
    echo "❌ 进程启动失败!"
    cat "${BACKEND_DEPLOY_DIR}/portal-server.log" | tail -20
  fi
else
  echo "ℹ️ 无后端 JAR，跳过后端部署"
fi

# ======== 6. 健康检查 ========
echo ""
echo ">>> [6/6] 健康检查 <<<"

# 检查前端
MAX_RETRIES=5
for i in $(seq 1 $MAX_RETRIES); do
  if curl -sf http://localhost/portal/ > /dev/null 2>&1; then
    echo "✅ Portal 前端健康! http://localhost/portal/ (尝试 $i/$MAX_RETRIES)"
    break
  fi
  
  if [ $i -eq $MAX_RETRIES ]; then
    echo "⚠️ 前端健康检查未通过 ($i/$MAX_RETRIES)"
    break
  fi
  
  echo "⏳ 等待 Nginx 就绪... ($i/$MAX_RETRIES)"
  sleep 3
done

# 检查后端（如果部署了）
if [ -n "$BACKEND_JAR" ] && ss -tlnp | grep -q ":${BACKEND_PORT} "; then
  echo "✅ Portal 后端运行在端口 ${BACKEND_PORT}"
elif [ -z "$BACKEND_JAR" ]; then
  echo "ℹ️ 后端未部署（本次只更新前端）"
else
  echo "⚠️ 后端可能未成功启动，请检查日志: ${BACKEND_DEPLOY_DIR}/portal-server.log"
fi

echo ""
echo "============================================="
echo "  🚪 portal 门户系统 部署完成!"
echo "  Commit: $(cd /root/devtools && git rev-parse --short HEAD)"
echo "  时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo ""
echo "  📊 服务访问:"
echo "    前端页面: https://main.marschat.online/portal/"
echo "    后端API:  :${BACKEND_PORT} (Nginx: /portal/api/sys/)"
echo "    认证API:  :8090 (Nginx: /portal/api/auth/ → Gateway)"
echo ""
echo "  📁 部署位置:"
echo "    前端: ${DEPLOY_DIR}"
echo "    后端: ${BACKEND_DEPLOY_DIR}/${JAR_NAME}"
echo "============================================="
