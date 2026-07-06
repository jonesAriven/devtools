#!/bin/bash
# ============================================================
# mykng 部署脚本 — 在目标服务器(mykng)上执行
# ============================================================
# 用法: bash deploy.sh <commit_sha> <branch> <deploy_target>
# 示例: bash deploy.sh abc1234 dev production
#
# 部署目标:
#   production → mykng-debain (Docker Compose project: kb-deploy)
#   test       → 测试环境 (Docker Compose project: kb-test)
#
# 服务列表 & 端口（基于实际服务器检查 2026-07-06）:
#   kb-gateway    :8090(宿主机)→8080(容器) → API网关(唯一对外暴露)
#   kb-auth       :8081(容器内网) → 认证服务
#   kb-file       :8082(容器内网) → 文件服务
#   kb-knowledge  :8083(容器内网) → 知识库服务
#   kb-intelligence:8086(容器内网) → AI智能服务
#
# 基础设施（常驻运行，不在此脚本管理）:
#   kb-mysql      :3306, kb-redis:6379, kb-nacos:8848
#   kb-mongo      :27017, kb-minio:9000, kb-meilisearch:7700
#   kb-web(Nginx) :8091
# ============================================================

set -e

COMMIT_SHA="${1:-unknown}"
BRANCH="${2:-dev}"
DEPLOY_TARGET="${3:-production}"

echo "============================================="
echo "  📘 mykng 知识库微服务 — 自动部署"
echo "  时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo "  Commit: ${COMMIT_SHA}"
echo "  分支: ${BRANCH}"
echo "  目标: ${DEPLOY_TARGET}"
echo "============================================="

# ======== 根据部署目标选择配置 ========
case "${DEPLOY_TARGET}" in
  production)
    PROJECT_NAME="kb-deploy"
    COMPOSE_DIR="/root/devtools/mykng"
    COMPOSE_PROFILE="prod"  # 使用 prod profile
    echo "📍 部署目标: 生产环境 (mykng) - Profile: prod"
    ;;
  test)
    PROJECT_NAME="kb-test"
    COMPOSE_DIR="/root/devtools/mykng"
    COMPOSE_PROFILE="test"  # 使用 test profile
    echo "📍 部署目标: 测试环境 - Profile: test"
    ;;
  dev)
    PROJECT_NAME="kb-dev"
    COMPOSE_DIR="/root/devtools/mykng"
    COMPOSE_PROFILE="dev"  # 使用 dev profile（最小化）
    echo "📍 部署目标: 开发环境 - Profile: dev"
    ;;
  *)
    echo "❌ 未知的部署目标: ${DEPLOY_TARGET}"
    echo "   可用选项: production, test, dev"
    exit 1
    ;;
esac

# ======== 0. 环境准备（首次部署） ========
echo ""
echo ">>> [0/5] 环境检查 <<<"

if [ ! -d /root/devtools ]; then
  echo "⚠️ 首次部署：/root/devtools 不存在，开始克隆仓库..."
  git clone https://gitee.com/jonesAriven/devtools.git /root/devtools
fi

# 检查旧容器
OLD_CONTAINERS=$(docker ps -a --filter "name=${PROJECT_NAME}*" -q 2>/dev/null || true)
if [ -n "$OLD_CONTAINERS" ]; then
  echo "ℹ️ 检测到旧容器:"
  docker ps -a --filter "name=${PROJECT_NAME}*" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
fi

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

# ======== 2. 停止并删除旧容器（仅微服务，保留基础设施）=====
echo ""
echo ">>> [2/5] 停止旧微服务 <<<"
cd "${COMPOSE_DIR}"

# 注意：只重启微服务，不停止基础设施(mysql/redis/nacos等)
if docker compose -p "${PROJECT_NAME}" --profile "${COMPOSE_PROFILE}" ps -q 2>/dev/null | grep -q .; then
  echo "--- 停止旧微服务容器 ---"
  # 只停止微服务，不移除基础设施
  docker compose -p "${PROJECT_NAME}" --profile "${COMPOSE_PROFILE}" stop \n    kb-gateway kb-auth kb-file kb-knowledge kb-intelligence 2>/dev/null || true
  sleep 5
  echo "✅ 旧微服务已停止（基础设施保留）"
else
  echo "ℹ️ 没有运行中的微服务容器"
fi

# 清理悬空镜像（可选，节省磁盘）
docker image prune -f --filter "until=24h" 2>/dev/null || true

# ======== 3. 构建 Docker 镜像 ========
echo ""
echo ">>> [3/5] 构建 Docker 镜像 <<<"

echo "--- 当前 commit: $(git rev-parse HEAD) ---"
echo "--- 各服务 jar 包时间戳 ---"
ls -lh kb-gateway/target/*.jar kb-auth/target/*.jar kb-file/target/*.jar \
       kb-knowledge/target/*.jar kb-intelligence/target/*.jar \
       2>/dev/null || echo "⚠️ jar 包不存在，需要先编译"

# 构建所有微服务镜像（使用 profile）
echo "--- 使用 Profile: ${COMPOSE_PROFILE} ---"
docker compose -p "${PROJECT_NAME}" --profile "${COMPOSE_PROFILE}" build --no-cache \
  kb-gateway kb-auth kb-file kb-knowledge kb-intelligence \
  2>&1 | tail -30

echo "✅ 镜像构建完成（--no-cache 全新构建）"
docker images | grep -E "(kb-gateway|kb-auth|kb-file|kb-knowledge|kb-intelligence)" | head -10

# ======== 4. 启动新容器 ========
echo ""
echo ">>> [4/5] 启动新服务 <<<"

COMPOSE_ARGS="-p ${PROJECT_NAME} --profile ${COMPOSE_PROFILE} up -d --no-deps --force-recreate"

# 按依赖顺序启动（重要！auth → file/knowledge → intelligence → gateway）
echo "--- [1/5] 启动 kb-auth (认证服务) ---"
docker compose ${COMPOSE_ARGS} kb-auth && sleep 20

echo "--- [2/5] 启动 kb-file (文件服务) ---"
docker compose ${COMPOSE_ARGS} kb-file && sleep 15

echo "--- [3/5] 启动 kb-knowledge (知识库服务) ---"
docker compose ${COMPOSE_ARGS} kb-knowledge && sleep 15

echo "--- [4/5] 启动 kb-intelligence (AI智能服务) ---"
docker compose ${COMPOSE_ARGS} kb-intelligence && sleep 15

echo "--- [5/5] 启动 kb-gateway (API网关入口) ---"
docker compose ${COMPOSE_ARGS} kb-gateway && sleep 25

echo "✅ 所有服务已启动"

# ======== 5. 健康检查 ========
echo ""
echo ">>> [5/5] 健康检查 <<<"
sleep 10

echo "--- 服务状态 ---"
docker compose -p "${PROJECT_NAME}" ps

echo ""
echo "--- Gateway 健康检查 (端口 8090) ---"
MAX_RETRIES=6
for i in $(seq 1 $MAX_RETRIES); do
  if curl -sf http://localhost:8090/kb/actuator/health > /dev/null 2>&1; then
    echo "✅ Gateway 健康! (尝试 $i/$MAX_RETRIES)"
    break
  fi
  if [ $i -eq $MAX_RETRIES ]; then
    echo "❌ Gateway 健康检查失败! ($i/$MAX_RETRIES)"
    echo "--- 最近日志 ---"
    docker compose -p "${PROJECT_NAME}" logs --tail=30 kb-gateway
    exit 1
  fi
  echo "⏳ 等待 Gateway 启动... ($i/$MAX_RETRIES)"
  sleep 10
done

echo ""
echo "============================================="
echo "  📘 mykng 部署完成!"
echo "  环境: ${DEPLOY_TARGET}"
echo "  Commit: $(git rev-parse --short HEAD)"
echo "  时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo ""
echo "  📊 服务访问:"
echo "    Gateway: http://localhost:8090"
echo "    Nacos:   http://localhost:8848/nacos (nacos/nacos)"
echo "============================================="
