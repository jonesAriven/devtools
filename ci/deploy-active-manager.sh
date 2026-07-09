#!/bin/bash
# ============================================================
# active-manager (激活码系统) — 部署脚本（在内网Debian上执行）
# ============================================================
# 用法: bash deploy-active-manager.sh <tar.gz文件名>
# 示例: bash deploy-active-manager.sh active-manager-latest.tar.gz
#
# 流程:
#   [0] 前置检查（产物存在、端口可用）
#   [1] 从 /mnt/shared/devtools 取 tar.gz 包
#   [2] 解压 jar 到 target/ 目录 (Dockerfile COPY 路径)
#   [3] docker compose down --remove-orphans (停旧服务，防孤儿)
#   [4] docker compose up -d --build --force-recreate (建新服务)
#   [5] 健康检查 (:18080/activecode/login.html)
#   [6] 清理旧悬空镜像
#
# 防孤儿设计:
#   - 唯一操作入口: docker compose -p activecode
#   - down --remove-orphans: 清理所有非声明容器
#   - 兜底: 强制清除 activecode 残留容器
#   - 幂等性: 首次部署 / 重复部署行为一致
#
# 访问地址:
#   本地:  http://localhost:18080/activecode/
#   公网:  https://tools.marschat.online/activecode/
#         (通过 FRP 18080 → 内网Debian 18080)
# ============================================================

set -euo pipefail

# 日志记录
LOG_FILE="/var/log/active-manager-deploy-$(date +%Y%m%d-%H%M%S).log"
exec 1> >(tee -a "$LOG_FILE")
exec 2>&1

echo "============================================="
echo "  🔑 active-manager 激活码系统 — 自动部署"
echo "  时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo "  日志: $LOG_FILE"
echo "============================================="

# ======================== 参数 ========================
TAR_FILE="${1:?❌ 缺少参数! 用法: $0 <tar.gz文件名>}"

# ======================== 配置 ========================
SHARED_DIR="/mnt/shared/devtools/publish"
DEPLOY_DIR="/root/devtools/active-manager/activation-code-server"
COMPOSE_PROJECT="activecode"
CONTAINER_NAME="activecode"
APP_PORT=18080              # 宿主机端口
HEALTH_MAX_RETRIES=24       # 24次 × 10秒 = 4分钟
HEALTH_INTERVAL=10

echo ""
echo "  产物: ${TAR_FILE}"
echo "============================================="

# ======================== [0] 前置检查 ========================
echo ""
echo ">>> [0/6] 前置检查 <<<"

TAR_PATH="${SHARED_DIR}/${TAR_FILE}"

# 检查产物存在
if [ ! -f "${TAR_PATH}" ]; then
  echo "❌ 产物不存在: ${TAR_PATH}"
  echo "   请确认 Woodpecker Build 步骤已成功将产物推送到共享目录"
  exit 1
fi
FILE_SIZE=$(ls -lh "${TAR_PATH}" | awk '{print $5}')
echo "✅ 找到产物: ${TAR_PATH} (${FILE_SIZE})"

# 检查端口占用（仅提示，不停旧服务由步骤 [3] docker compose down 处理）
if ss -tlnp | grep -q ":${APP_PORT} "; then
  echo "ℹ️ 端口 ${APP_PORT} 已被占用（旧服务运行中，将在步骤 [3] 停止）:"
  ss -tlnp | grep ":${APP_PORT} "
else
  echo "✅ 端口 ${APP_PORT} 可用（首次部署）"
fi

# ======================== [1] 取包 & 解压 ========================
echo ""
echo ">>> [1/6] 取包 & 解压 <<<"

# 确保目标目录存在
mkdir -p "${DEPLOY_DIR}/target"

# 清理旧 jar + 解压新 jar
rm -f "${DEPLOY_DIR}/target/"*.jar 2>/dev/null || true
tar xzf "${TAR_PATH}" -C "${DEPLOY_DIR}/target/"
JAR_COUNT=$(ls "${DEPLOY_DIR}/target/"*.jar 2>/dev/null | wc -l)
echo "✅ 解压完成: ${JAR_COUNT} 个 jar 文件"
ls -lh "${DEPLOY_DIR}/target/"

if [ "${JAR_COUNT}" -eq 0 ]; then
  echo "❌ tar 包中没有 jar 文件!"
  exit 1
fi

# ======================== [2] 校验 Dockerfile 和 docker-compose 存在 ========================
echo ""
echo ">>> [2/6] 校验部署环境 <<<"

for required_file in "${DEPLOY_DIR}/Dockerfile" "${DEPLOY_DIR}/docker-compose.yml"; do
  if [ -f "${required_file}" ]; then
    echo "  ✅ ${required_file}"
  else
    echo "❌ 关键文件不存在: ${required_file}"
    echo "   请确认代码仓库中包含完整的 Dockerfile 和 docker-compose.yml"
    exit 1
  fi
done

# 检查 Docker 是否可用
if ! command -v docker &>/dev/null; then
  echo "❌ Docker 未安装或不在 PATH 中"
  exit 1
fi
echo "  ✅ Docker: $(docker --version)"

# ======================== [3] 停止旧服务（防孤儿核心）========================
echo ""
echo ">>> [3/6] 停止旧服务 (防孤儿) <<<"

cd "${DEPLOY_DIR}"

# ---- 3a. 主路径：docker compose down（唯一标准入口） ----
if docker compose -p "${COMPOSE_PROJECT}" ps -q 2>/dev/null | grep -q .; then
  echo "--- docker compose -p ${COMPOSE_PROJECT} down --remove-orphans ---"
  docker compose -p "${COMPOSE_PROJECT}" down --remove-orphans --timeout 30 2>&1 || true
  echo "✅ compose down 完成"
  sleep 5
else
  echo "ℹ️ 无正在运行的 compose project (首次部署或已清理)"
fi

# ---- 3b. 兜底：强制清除任何残留的 activecode 容器 ----
echo "--- 兜底扫描残留容器 ---"
if docker ps -a --filter "name=${CONTAINER_NAME}" -q 2>/dev/null | grep -q .; then
  echo "  ⚠️ 发现残留容器: ${CONTAINER_NAME}，强制删除"
  docker rm -f $(docker ps -aq --filter "name=${CONTAINER_NAME}") 2>/dev/null || true
else
  echo "  ✅ 无残留容器，环境干净"
fi

# 等待端口释放
sleep 2
if ss -tlnp | grep -q ":${APP_PORT} "; then
  echo "  ⚠️ 端口 ${APP_PORT} 仍被占用，等待释放..."
  sleep 5
  # 如果还被非 Docker 进程占用，尝试清理
  if ss -tlnp | grep ":${APP_PORT} " | grep -qv docker; then
    echo "  ⚠️ 端口被非 Docker 进程占用，尝试释放..."
    fuser -k ${APP_PORT}/tcp 2>/dev/null || true
    sleep 2
  fi
fi

# ======================== [4] 构建并启动（原子操作）========================
echo ""
echo ">>> [4/6] 构建并启动新服务 <<<"
echo "--- Compose Project: ${COMPOSE_PROJECT} ---"

docker compose -p "${COMPOSE_PROJECT}" \
  up -d --build --force-recreate --remove-orphans 2>&1 | tail -30

echo ""
echo "✅ 服务启动命令执行完成"
echo ""
echo "--- 当前容器状态 ---"
docker compose -p "${COMPOSE_PROJECT}" ps 2>/dev/null || \
  docker ps --filter "name=${CONTAINER_NAME}" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

# ======================== [5] 健康检查 ========================
echo ""
echo ">>> [5/6] 健康检查 (最多等待 $(( HEALTH_MAX_RETRIES * HEALTH_INTERVAL / 60 )) 分钟) <<<"

check_http() {
  local url=$1
  local timeout=${2:-5}
  local code
  code=$(curl -sf -o /dev/null -w "%{http_code}" --max-time "${timeout}" "${url}" 2>/dev/null || echo "000")
  echo "${code}"
}

LOGIN_OK=false
API_OK=false

for i in $(seq 1 ${HEALTH_MAX_RETRIES}); do
  ERRORS=""
  NOW=$(date '+%H:%M:%S')

  # 检查登录页面（静态资源）
  LOGIN_CODE=$(check_http "http://localhost:${APP_PORT}/activecode/login.html" 5)
  if [ "${LOGIN_CODE}" = "200" ] || [ "${LOGIN_CODE}" = "201" ] || [ "${LOGIN_CODE}" = "302" ]; then
    LOGIN_OK=true
    echo "  ✅ [${NOW}] 登录页 HTTP ${LOGIN_CODE} ($i/${HEALTH_MAX_RETRIES})"
  else
    ERRORS="${ERRORS}LoginPage "
  fi

  # 检查 API 端点（后端）
  API_CODE=$(curl -sf -o /dev/null -w "%{http_code}" --max-time 5 \
    -X POST "http://localhost:${APP_PORT}/activecode/api/auth/login" \
    -H "Content-Type: application/json" -d '{}' 2>/dev/null || echo "000")
  if [ "${API_CODE}" != "000" ]; then
    API_OK=true
    echo "  ✅ [${NOW}] API 端点响应 HTTP ${API_CODE} ($i/${HEALTH_MAX_RETRIES})"
  else
    ERRORS="${ERRORS}API "
  fi

  # 全部通过 → 退出
  if ${LOGIN_OK} && ${API_OK}; then
    echo ""
    echo "  🎉 激活码服务完全健康! (第 $i/${HEALTH_MAX_RETRIES} 次)"
    break
  fi

  # 最后一次
  if [ $i -eq ${HEALTH_MAX_RETRIES} ]; then
    echo ""
    echo "  ❌ 健康检查超时，部署失败: ${ERRORS:-无}"
    echo ""
    echo "  --- 容器详细状态 ---"
    docker ps -a --filter "name=${CONTAINER_NAME}" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}" 2>/dev/null || echo "     容器未找到"
    echo ""
    echo "  --- 最近日志 (最后30行) ---"
    docker logs --tail=30 ${CONTAINER_NAME} 2>&1 || true
    echo ""
    echo "  ℹ️ Java 应用首次启动可能需要 1-2 分钟，请稍后手动验证:"
    echo "     curl http://localhost:${APP_PORT}/activecode/login.html"
    exit 1  # 健康检查失败，脚本退出码非0
  else
    echo "  ⏳ [${NOW}] 等待中... ($i/${HEALTH_MAX_RETRIES}) [待检查: ${ERRORS:-无}]"
  fi

  sleep ${HEALTH_INTERVAL}
done

# 最终验证：容器必须处于运行状态
if ! docker ps --filter "name=${CONTAINER_NAME}" --filter "status=running" | grep -q .; then
  echo ""
  echo "❌ 最终验证失败: 容器 ${CONTAINER_NAME} 未在运行状态"
  docker ps -a --filter "name=${CONTAINER_NAME}"
  exit 1
fi

# ======================== [6] 清理旧镜像 ========================
echo ""
echo ">>> [6/6] 清理旧资源 <<<"

DANGLING=$(docker images -f "dangling=true" -q 2>/dev/null | wc -l)
if [ "${DANGLING}" -gt 0 ]; then
  docker image prune -f 2>/dev/null || true
  echo "✅ 已清理 ${DANGLING} 个悬空镜像"
else
  echo "✅ 无悬空镜像"
fi

# ======================== 完成 ========================
echo ""
echo "============================================="
echo "  🔑 active-manager 部署完成!"
echo "  时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo "  产物: ${TAR_FILE}"
echo ""
echo "  📊 服务访问:"
echo "    登录页: https://tools.marschat.online/activecode/login.html"
echo "    管理后台: https://tools.marschat.online/activecode/main.html"
echo "    API:    https://tools.marschat.online/activecode/api/*"
echo "    本地测试: http://localhost:${APP_PORT}/activecode/login.html"
echo ""
echo "  🔐 默认账号: admin / admin123"
echo "============================================="
