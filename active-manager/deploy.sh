#!/bin/bash
# ============================================================
# active-manager 部署脚本 — 在目标服务器上执行
# ============================================================
# 用法: bash deploy.sh <commit_sha> <branch>
# 示例: bash deploy.sh abc1234 dev
# ============================================================

set -e

COMMIT_SHA="${1:-unknown}"
BRANCH="${2:-dev}"

echo "============================================="
echo "  active-manager 自动部署开始"
echo "  时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo "  Commit: ${COMMIT_SHA}"
echo "  分支: ${BRANCH}"
echo "============================================="

# ======== 1. 同步代码 ========
echo ""
echo ">>> [1/3] 同步代码 <<<"
if [ ! -d /root/devtools ]; then
  echo "⚠️ 首次部署，克隆仓库..."
  git clone https://gitee.com/jonesAriven/devtools.git /root/devtools
fi
cd /root/devtools
git fetch origin "${BRANCH}"
git reset --hard "origin/${BRANCH}"
echo "✅ 代码已同步到 $(git rev-parse --short HEAD)"

# ======== 2. 构建 Docker 镜像 + 重启容器 ========
echo ""
echo ">>> [2/3] 构建 & 部署 <<<"
cd /root/devtools/active-manager/activation-code-server

echo "--- JAR 包信息 ---"
ls -lh target/activation-code-server-1.0.0.jar

echo "--- 停止旧容器 ---"
docker stop activecode 2>/dev/null || true
docker rm activecode 2>/dev/null || true

echo "--- 构建新镜像 ---"
docker build -t activecode . 2>&1 | tail -10

echo "--- 启动新容器 ---"
docker run -d \
  --name activecode \
  -p 18080:8080 \
  --restart unless-stopped \
  activecode

echo "✅ 容器已启动"

# ======== 3. 健康检查 ========
echo ""
echo ">>> [3/3] 健康检查 <<<"
sleep 10

echo "--- 容器状态 ---"
docker ps | grep activecode

echo "--- 健康检查 (端口 18080) ---"
for i in $(seq 1 8); do
  if curl -sf http://localhost:18080/activecode/login.html > /dev/null 2>&1; then
    echo "✅ 激活码服务健康! (尝试 $i)"
    break
  fi
  echo "⏳ 等待服务启动... ($i/8)"
  sleep 5
done

echo ""
echo "============================================="
echo "  active-manager 部署完成!"
echo "  内网: http://192.168.31.182:18080/activecode/login.html"
echo "  公网: https://tools.marschat.online/activecode/login.html"
echo "  时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo "============================================="
