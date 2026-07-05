#!/bin/bash
# ============================================================
# mykng 部署脚本 — 在目标服务器上执行
# ============================================================
# 用法: bash deploy.sh <commit_sha> <branch> <deploy_target>
# 示例: bash deploy.sh abc1234 dev production
# ============================================================

set -e

COMMIT_SHA="${1:-unknown}"
BRANCH="${2:-dev}"
DEPLOY_TARGET="${3:-production}"

echo "============================================="
echo "  mykng 自动部署开始"
echo "  时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo "  Commit: ${COMMIT_SHA}"
echo "  分支: ${BRANCH}"
echo "  目标: ${DEPLOY_TARGET}"
echo "============================================="

# ======== 根据部署目标选择配置 ========
case "${DEPLOY_TARGET}" in
  production)
    PROJECT_NAME="kb-deploy"
    echo "📍 部署目标: 生产环境 (mykng-debain)"
    ;;
  test)
    PROJECT_NAME="kb-test"
    echo "📍 部署目标: 测试环境"
    ;;
  *)
    echo "❌ 未知的部署目标: ${DEPLOY_TARGET}"
    echo "   可用选项: production, test"
    exit 1
    ;;
esac

# ======== 1. 同步代码 ========
echo ""
echo ">>> [1/4] 同步代码 <<<"
cd /root/devtools
git fetch origin "${BRANCH}"
git reset --hard "origin/${BRANCH}"
echo "✅ 代码已同步到 $(git rev-parse --short HEAD)"

# ======== 2. 构建 Docker 镜像 ========
echo ""
echo ">>> [2/4] 构建 Docker 镜像 <<<"
cd /root/devtools/mykng

echo "--- 当前 commit: $(git rev-parse HEAD) ---"
echo "--- 各服务 jar 包时间戳 ---"
ls -lh kb-gateway/target/*.jar kb-auth/target/*.jar kb-file/target/*.jar \
       kb-knowledge/target/*.jar kb-intelligence/target/*.jar \
       2>/dev/null || echo "⚠️ jar 包不存在，需要先编译"

docker compose -p "${PROJECT_NAME}" build --no-cache \
  kb-gateway kb-auth kb-file kb-knowledge kb-intelligence \
  2>&1 | tail -30

echo "✅ 镜像构建完成（--no-cache 全新构建）"
docker images | grep "${PROJECT_NAME}" | head -10

# ======== 3. 滚动重启服务 ========
echo ""
echo ">>> [3/4] 滚动重启服务 <<<"

COMPOSE_ARGS="-p ${PROJECT_NAME} up -d --no-deps --force-recreate"

echo "--- [1/5] 重启 kb-auth ---"
docker compose ${COMPOSE_ARGS} kb-auth && sleep 20

echo "--- [2/5] 重启 kb-file ---"
docker compose ${COMPOSE_ARGS} kb-file && sleep 15

echo "--- [3/5] 重启 kb-knowledge ---"
docker compose ${COMPOSE_ARGS} kb-knowledge && sleep 15

echo "--- [4/5] 重启 kb-intelligence ---"
docker compose ${COMPOSE_ARGS} kb-intelligence && sleep 15

echo "--- [5/5] 重启 kb-gateway (入口) ---"
docker compose ${COMPOSE_ARGS} kb-gateway && sleep 25

echo "✅ 所有服务已重启"

# ======== 4. 健康检查 ========
echo ""
echo ">>> [4/4] 健康检查 <<<"
sleep 15

echo "--- 服务状态 ---"
docker compose -p "${PROJECT_NAME}" ps

echo "--- Gateway 健康检查 ---"
for i in $(seq 1 6); do
  if curl -sf http://localhost:8090/kb/actuator/health > /dev/null 2>&1; then
    echo "✅ Gateway 健康! (尝试 $i)"
    break
  fi
  echo "⏳ 等待 Gateway 启动... ($i/6)"
  sleep 10
done

echo ""
echo "============================================="
echo "  mykng 部署完成!"
echo "  环境: ${DEPLOY_TARGET}"
echo "  Commit: $(git rev-parse --short HEAD)"
echo "  时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo "============================================="
