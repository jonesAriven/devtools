#!/bin/bash
# ============================================================
# infra-monitor (基础设施监控) 部署脚本 — 在目标服务器上执行
# ============================================================
# 用法: bash deploy.sh <commit_sha> <branch>
# 示例: bash deploy.sh abc1234 dev
#
# 部署信息（基于实际服务器检查 2026-07-06）:
#   后端: infra-monitor-server (Java/Spring Boot)
#   前端: infra-monitor-web (Vue3 + Vite)
#   容器名: infra-monitor
#   镜像: eclipse-temurin:21-jre (非自定义构建)
#
# ⚠️ 特殊部署方式:
#   - 不使用 Docker 构建！JAR 包直接运行
#   - 数据目录: /data/infra-monitor → 挂载到容器 /app
#   - 无端口映射（仅内部访问或通过其他方式暴露）
#   - 启动命令: java -jar /app/app.jar --spring.profiles.active=prod
#
# 部署流程:
#   1. 构建前端（如果有）
#   2. Maven 构建后端 JAR
#   3. 停止旧容器
#   4. 复制 JAR 到 /data/infra-monitor/
#   5. 启动新容器
# ============================================================

set -e

COMMIT_SHA="${1:-unknown}"
BRANCH="${2:-dev}"

echo "============================================="
echo "  📊 infra-monitor 基础设施监控 — 自动部署"
echo "  时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo "  Commit: ${COMMIT_SHA}"
echo "  分支: ${BRANCH}"
echo "============================================="

# ======== 配置 ========
APP_DIR="/root/devtools/infra-monitor"
BACKEND_DIR="${APP_DIR}/infra-monitor-server"
FRONTEND_DIR="${APP_DIR}/infra-monitor-web"
CONTAINER_NAME="infra-monitor"
DATA_DIR="/data/infra-monitor"  # 实际数据目录
JAR_NAME="app.jar"             # 容器内的 JAR 名

# ======== 0. 环境准备 ========
echo ""
echo ">>> [0/5] 环境检查 <<<"

if [ ! -d /root/devtools ]; then
  echo "⚠️ 首次部署：克隆仓库..."
  git clone https://gitee.com/jonesAriven/devtools.git /root/devtools
fi

# 检查旧容器
if docker ps -a --filter "name=${CONTAINER_NAME}" --format "{{.Names}}" | grep -q "${CONTAINER_NAME}"; then
  echo "ℹ️ 发现旧容器:"
  docker ps -a --filter "name=${CONTAINER_NAME}" --format "table {{.Names}}\t{{.Status}}\t{{.Image}}"
  
  # 显示挂载信息
  echo ""
  echo "ℹ️ 旧容器挂载:"
  docker inspect ${CONTAINER_NAME} --format='{{range .Mounts}}{{.Source}} -> {{.Destination}}{{"\n"}}{{end}}'
fi

# 确保 data 目录存在
mkdir -p "${DATA_DIR}"

# ======== 1. 同步代码 ========
echo ""
echo ">>> [1/5] 同步代码 <<<"
cd /root/devtools

REMOTE_URL=$(git remote get-url origin 2>/dev/null || echo "")
if [ "$REMOTE_URL" != "https://gitee.com/jonesAriven/devtools.git" ]; then
  git remote set-url origin https://gitee.com/jonesAriven/devtools.git
fi

git fetch origin "${BRANCH}"
git reset --hard "origin/${BRANCH}"
echo "✅ 代码已同步到 $(git rev-parse --short HEAD)"

# ======== 2. 构建前端（如果有） ========
echo ""
echo ">>> [2/5] 构建前端 <<<"

if [ -d "${FRONTEND_DIR}" ] && [ -f "${FRONTEND_DIR}/package.json" ]; then
  cd "${FRONTEND_DIR}"
  
  echo "--- 安装前端依赖 ---"
  if [ ! -d "node_modules" ]; then
    if [ -f "pnpm-lock.yaml" ]; then
      pnpm install --registry=https://registry.npmmirror.com
    else
      npm ci --registry=https://registry.npmmirror.com
    fi
  fi
  
  echo "--- 构建前端 ---"
  if [ -f "pnpm-lock.yaml" ]; then
    pnpm build
  else
    npm run build
  fi
  
  # 将前端构建产物复制到后端静态资源目录
  if [ -d "dist" ] && [ -d "${BACKEND_DIR}/src/main/resources/static" ]; then
    echo "--- 复制前端资源到后端 ---"
    rm -rf "${BACKEND_DIR}/src/main/resources/static/"*
    cp -r dist/* "${BACKEND_DIR}/src/main/resources/static/"
    echo "✅ 前端已集成到后端 JAR"
  elif [ -d "dist" ]; then
    echo "ℹ️ 前端构建完成，但未找到后端静态资源目录"
    echo "   前端将独立部署"
  fi
  
  cd "${APP_DIR}"
else
  echo "ℹ️ 无前端项目或无需构建"
fi

# ======== 3. Maven 构建后端 JAR ========
echo ""
echo ">>> [3/5] Maven 构建后端 <<<"
cd "${BACKEND_DIR}"

echo "--- 检查 Maven ---"
if ! command -v mvn &> /dev/null; then
  echo "❌ Maven 未安装！尝试使用 Docker 内的 Maven..."
  # 使用 Docker 运行 Maven 构建
  docker run --rm \
    -v "${BACKEND_DIR}:/app" \
    -v /root/.m2:/root/.m2 \
    -w /app \
    maven:3.9-eclipse-temurin-21 \
    mvn clean package -DskipTests -q
else
  echo "--- 本地 Maven 构建 ---"
  mvn clean package -DskipTests -q
fi

# 查找生成的 JAR
JAR_FILE=$(find target -name "*.jar" ! -name "*sources.jar" ! -name "*javadoc.jar" | head -1)

if [ -z "$JAR_FILE" ]; then
  echo "❌ 未找到构建产物 JAR 文件"
  exit 1
fi

echo "✅ 构建完成: ${JAR_FILE}"
ls -lh "${JAR_FILE}"

# ======== 4. 停止旧服务 + 部署新版本 ========
echo ""
echo ">>> [4/5] 停止旧服务 & 部署新版本 <<<"

# 停止旧容器
if docker ps -q --filter "name=${CONTAINER_NAME}" | grep -q .; then
  echo "--- 停止旧容器 ---"
  docker stop ${CONTAINER_NAME} || true
  sleep 3
  docker rm -f ${CONTAINER_NAME} || true
  echo "✅ 旧容器已删除"
else
  echo "ℹ️ 无运行中的容器"
fi

# 备份旧 JAR（如果存在）
if [ -f "${DATA_DIR}/${JAR_NAME}" ]; then
  echo "--- 备份旧版本 ---"
  mv "${DATA_DIR}/${JAR_NAME}" "${DATA_DIR}/${JAR_NAME}.bak.$(date +%s)" 2>/dev/null || true
fi

# 复制新 JAR 到数据目录
echo "--- 部署新 JAR 到 ${DATA_DIR} ---"
cp "${JAR_FILE}" "${DATA_DIR}/${JAR_NAME}"
chmod 644 "${DATA_DIR}/${JAR_NAME}"
echo "✅ 新版本已部署"

# 清理旧镜像（可选）
docker image prune -f --filter "until=24h" 2>/dev/null || true

# ======== 5. 启动新容器 ========
echo ""
echo ">>> [5/5] 启动新容器 <<<"

echo "--- 启动 infra-monitor 容器 ---"
docker run -d \
  --name ${CONTAINER_NAME} \
  --restart unless-stopped \
  -v "${DATA_DIR}:/app" \
  -e TZ=Asia/Shanghai \
  -e SPRING_PROFILES_ACTIVE=prod \
  --memory="256m" \
  eclipse-temurin:21-jre \
  java -jar /app/app.jar --spring.profiles.active=prod

sleep 8

echo "✅ 容器已启动"
docker ps --filter "name=${CONTAINER_NAME}" --format "table {{.Names}}\t{{.Status}}\t{{.Image}}"

# ======== 6. 健康检查 ========
echo ""
echo ">>> [6/6] 健康检查 <<<"

MAX_RETRIES=8
for i in $(seq 1 $MAX_RETRIES); do
  # 检查容器是否在运行
  if ! docker ps --filter "name=${CONTAINER_NAME}" --format "{{.Status}}" | grep -q "Up"; then
    echo "❌ 容器未运行! ($i/$MAX_RETRIES)"
    echo "--- 最近日志 ---"
    docker logs --tail=30 ${CONTAINER_NAME}
    exit 1
  fi
  
  # 检查 Java 进程是否存活
  if docker exec ${CONTAINER_NAME} pgrep -f "app.jar" > /dev/null 2>&1; then
    echo "✅ Infra Monitor 服务健康! (尝试 $i/$MAX_RETRIES)"
    break
  fi
  
  if [ $i -eq $MAX_RETRIES ]; then
    echo "⚠️ 健康检查超时，但容器正在运行 ($i/$MAX_RETRIES)"
    echo "--- 最近日志 ---"
    docker logs --tail=30 ${CONTAINER_NAME}
    break
  fi
  
  echo "⏳ 等待服务启动... ($i/$MAX_RETRIES)"
  sleep 8
done

echo ""
echo "============================================="
echo "  📊 infra-monitor 基础设施监控 部署完成!"
echo "  Commit: $(cd /root/devtools && git rev-parse --short HEAD)"
echo "  时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo ""
echo "  📊 服务信息:"
echo "    容器: ${CONTAINER_NAME}"
echo "    数据目录: ${DATA_DIR}"
echo "    JAR文件: ${DATA_DIR}/${JAR_NAME}"
echo "    日志查看: docker logs -f ${CONTAINER_NAME}"
echo "============================================="
