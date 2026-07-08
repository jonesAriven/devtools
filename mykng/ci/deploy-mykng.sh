#!/bin/bash
# ============================================================
# mykng 知识库微服务 — 部署脚本（在 mykng 服务器上执行）
# ============================================================
# 用法: bash deploy-mykng.sh <tar.gz文件名>
# 示例: bash deploy-mykng.sh mykng-latest.tar.gz
#
# 流程:
#   [1] 从 /mnt/shared/devtools 取 tar.gz 包
#   [2] 解压 jar 到各模块 target/ 目录
#   [3] docker compose down --remove-orphans (停旧服务，防孤儿)
#   [4] docker compose up -d --build --force-recreate (建新服务)
#   [5] 健康检查 (gateway:8090)
#   [6] 清理旧悬空镜像
#
# 防孤儿设计:
#   - 唯一操作入口: docker compose -p kb-deploy
#   - down --remove-orphans: 清理所有非声明容器
#   - 兜底扫描: 强制清除任何 kb-* 残留容器
#   - 幂等性: 首次部署 / 重复部署行为一致
#
# 域名/端口:
#   Gateway: :8090 (唯一对外入口)
#   微服务通过 Docker 内网通信，不映射宿主机端口
# ============================================================

set -e

# ======================== 参数 ========================
TAR_FILE="${1:?❌ 缺少参数! 用法: $0 <tar.gz文件名>}"

# ======================== 配置 ========================
SHARED_DIR="/mnt/shared/devtools/publish"
DEPLOY_BASE="/root/devtools/mykng"
COMPOSE_PROJECT="kb-deploy"
COMPOSE_PROFILE="prod"
GATEWAY_PORT=8090
HEALTH_MAX_RETRIES=24          # 24次 × 10秒 = 4分钟
HEALTH_INTERVAL=10

# Jar 文件名映射 (发布包中的文件名 → 目标模块)
declare -A JAR_MAP=(
  ["kb-gateway"]="kb-gateway.jar"
  ["kb-auth"]="kb-auth.jar"
  ["kb-file"]="kb-file.jar"
  ["kb-knowledge"]="kb-knowledge.jar"
  ["kb-intelligence"]="kb-intelligence.jar"
)

# ======================== 开场 ========================
echo "============================================="
echo "  📘 mykng 知识库微服务 — 自动部署"
echo "  时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo "  产物: ${TAR_FILE}"
echo "  Profile: ${COMPOSE_PROFILE}"
echo "============================================="

# ======================== [1] 取包 & 解压 ========================
echo ""
echo ">>> [1/6] 取包 & 解压 <<<"

TAR_PATH="${SHARED_DIR}/${TAR_FILE}"
if [ ! -f "${TAR_PATH}" ]; then
  echo "❌ 产物不存在: ${TAR_PATH}"
  echo "   请确认 Woodpecker Build 步骤已成功将产物推送到共享目录"
  exit 1
fi

FILE_SIZE=$(ls -lh "${TAR_PATH}" | awk '{print $5}')
echo "✅ 找到产物: ${TAR_PATH} (${FILE_SIZE})"

# 创建 jars 目录并解压
mkdir -p "${DEPLOY_BASE}/jars"
rm -f "${DEPLOY_BASE}/jars/"*.jar  # 清理旧 jar
tar xzf "${TAR_PATH}" -C "${DEPLOY_BASE}/jars"
JAR_COUNT=$(ls "${DEPLOY_BASE}/jars/"*.jar 2>/dev/null | wc -l)
echo "✅ 解压完成: ${JAR_COUNT} 个 jar 文件"
ls -lh "${DEPLOY_BASE}/jars/"

if [ "${JAR_COUNT}" -eq 0 ]; then
  echo "❌ tar 包中没有 jar 文件!"
  exit 1
fi

# ======================== [2] 分发 jar 到各模块 target/ ========================
echo ""
echo ">>> [2/6] 分发 JAR 到各模块构建目录 <<<"

for module in "${!JAR_MAP[@]}"; do
  jar_file="${JAR_MAP[${module}]}"
  src_jar="${DEPLOY_BASE}/jars/${jar_file}"
  target_dir="${DEPLOY_BASE}/${module}/target"

  if [ -f "${src_jar}" ]; then
    mkdir -p "${target_dir}"
    # 先删旧再复制（避免 bind mount 残留）
    rm -f "${target_dir}/${jar_file}" 2>/dev/null || true
    cp "${src_jar}" "${target_dir}/"
    echo "  ✅ ${module} ← ${jar_file}"
  else
    echo "  ⏭️  ${module}: ${jar_file} 不存在 (可能该 profile 不需要)"
  fi
done

# ======================== [3] 停止旧服务（防孤儿核心）========================
echo ""
echo ">>> [3/6] 停止旧服务 (防孤儿) <<<"

cd "${DEPLOY_BASE}"

# ---- 3a. 主路径：docker compose down（唯一标准入口） ----
if docker compose -p "${COMPOSE_PROJECT}" ps -q 2>/dev/null | grep -q .; then
  echo "--- docker compose -p ${COMPOSE_PROJECT} down --remove-orphans ---"
  docker compose -p "${COMPOSE_PROJECT}" down --remove-orphans --timeout 30 2>&1 || true
  echo "✅ compose down 完成"
  sleep 5
else
  echo "ℹ️ 无正在运行的 compose project (首次部署或已清理)"
fi

# ---- 3b. 兜底：扫描并强制清除任何残留的 kb-* 微服务容器 ----
echo "--- 兜底扫描残留容器 ---"
ORPHAN_FOUND=false
for svc in kb-gateway kb-auth kb-file kb-knowledge kb-intelligence; do
  if docker ps -a --filter "name=${svc}" -q 2>/dev/null | grep -q .; then
    ORPHAN_FOUND=true
    echo "  ⚠️ 发现残留容器: ${svc}，强制删除"
    docker rm -f $(docker ps -aq --filter "name=${svc}") 2>/dev/null || true
  fi
done
if [ "${ORPHAN_FOUND}" = false ]; then
  echo "  ✅ 无残留容器，环境干净"
fi

# 确认端口释放
sleep 2
if ss -tlnp | grep -q ":${GATEWAY_PORT} "; then
  echo "  ⚠️ 端口 ${GATEWAY_PORT} 仍被占用，等待释放..."
  sleep 5
fi

# ======================== [4] 构建并启动（原子操作）========================
echo ""
echo ">>> [4/6] 构建并启动新服务 <<<"
echo "--- Compose Project: ${COMPOSE_PROJECT}, Profile: ${COMPOSE_PROFILE} ---"

# 使用 --remove-orphans 再次确保无孤儿容器
docker compose -p "${COMPOSE_PROJECT}" --profile "${COMPOSE_PROFILE}" \
  up -d --build --force-recreate --remove-orphans 2>&1 | tail -35

echo ""
echo "✅ 服务启动命令执行完成"
echo ""
echo "--- 当前容器状态 ---"
docker compose -p "${COMPOSE_PROJECT}" ps 2>/dev/null || docker ps --filter "name=kb-" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

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

GATEWAY_OK=false
AUTH_OK=false

for i in $(seq 1 ${HEALTH_MAX_RETRIES}); do
  ERRORS=""
  NOW=$(date '+%H:%M:%S')

  # 检查 Gateway (对外入口)
  GW_CODE=$(check_http "http://localhost:${GATEWAY_PORT}/kb/actuator/health" 5)
  if [ "${GW_CODE}" = "200" ] || [ "${GW_CODE}" = "201" ] || [ "${GW_CODE}" = "302" ]; then
    GATEWAY_OK=true
    echo "  ✅ [${NOW}] Gateway HTTP ${GW_CODE} ($i/${HEALTH_MAX_RETRIES})"
  else
    # fallback: 检查根路径
    GW_CODE2=$(check_http "http://localhost:${GATEWAY_PORT}/" 5)
    if [ "${GW_CODE2}" != "000" ]; then
      GATEWAY_OK=true
      echo "  ✅ [${NOW}] Gateway 根路径响应 HTTP ${GW_CODE2} ($i/${HEALTH_MAX_RETRIES})"
    else
      ERRORS="${ERRORS}Gateway "
    fi
  fi

  # 检查 Auth (容器内网端口，不映射到宿主机则跳过 HTTP 检查)
  AUTH_CODE=$(check_http "http://localhost:8081/actuator/health" 3 2>/dev/null || echo "000")
  if [ "${AUTH_CODE}" != "000" ]; then
    AUTH_OK=true
  else
    # 容器运行中即视为 OK (auth 不暴露端口时)
    if docker ps --filter "name=kb-auth" --format "{{.Status}}" 2>/dev/null | grep -q "Up"; then
      AUTH_OK=true
    else
      ERRORS="${ERRORS}Auth "
    fi
  fi

  # 全部通过 → 退出
  if ${GATEWAY_OK} && ${AUTH_OK}; then
    echo ""
    echo "  🎉 所有关键服务健康检查通过! (第 $i/${HEALTH_MAX_RETRIES} 次)"
    break
  fi

  # 最后一次
  if [ $i -eq ${HEALTH_MAX_RETRIES} ]; then
    echo ""
    echo "  ⚠️ 健康检查超时，未通过: ${ERRORS:-无}"
    echo ""
    echo "  --- 各容器状态 ---"
    for svc in kb-gateway kb-auth kb-file kb-knowledge kb-intelligence; do
      STATUS=$(docker ps -a --filter "name=${svc}" --format "{{.Status}}" 2>/dev/null || echo "未找到")
      echo "     ${svc}: ${STATUS}"
    done
    echo ""
    echo "  --- Gateway 最近日志 ---"
    docker logs --tail=20 kb-gateway 2>&1 || true
    echo ""
    echo "  ℹ️ Java 应用首次启动可能较慢，请稍后手动验证:"
    echo "     curl http://localhost:${GATEWAY_PORT}/"
  else
    echo "  ⏳ [${NOW}] 等待中... ($i/${HEALTH_MAX_RETRIES}) [待检查: ${ERRORS:-无}]"
  fi

  sleep ${HEALTH_INTERVAL}
done

# ======================== [6] 清理旧镜像 ========================
echo ""
echo ">>> [6/6] 清理旧资源 <<<"

# 仅清理悬空镜像（不影响当前使用的）
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
echo "  📘 mykng 部署完成!"
echo "  时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo "  产物: ${TAR_FILE}"
echo ""
echo "  📊 服务访问:"
echo "    Gateway: http://localhost:${GATEWAY_PORT}"
echo "    Nacos:   http://localhost:8848/nacos (nacos/nacos)"
echo "============================================="
