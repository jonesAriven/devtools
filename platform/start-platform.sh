#!/bin/bash
# ============================================================
# start-platform.sh — 全局基础设施层启动脚本
# ============================================================
# 用法: bash start-platform.sh
# 功能:
#   1. 清理占用 platform 容器名的残留容器（解决 Conflict 错误）
#   2. 启动基础设施服务 (Redis/MongoDB/MinIO/MeiliSearch/Nacos)
#      注意: MySQL 已迁移到 GR 集群，由 platform/mysql/ 单独管理
#   3. 等待所有服务健康检查通过
#
# 注意: 不归任何流水线管理，手动执行，持久运行
#       所有项目(mykng/kb-ops/infra-monitor等)共享此基础设施
# ============================================================
set -euo pipefail

# ====== 颜色 ======
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log_ok()   { echo -e "  ${GREEN}✅${NC} $1"; }
log_warn() { echo -e "  ${YELLOW}⚠️${NC} $1"; }
log_err()  { echo -e "  ${RED}❌${NC} $1"; }
log_info() { echo -e "  ${BLUE}ℹ️${NC} $1"; }

# ====== 配置 ======
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
COMPOSE_FILE="${SCRIPT_DIR}/docker-compose.platform.yml"
COMPOSE_PROJECT="platform"

# 基础设施容器名列表（MySQL 已迁移到 GR 集群，单独管理）
PLATFORM_SERVICES=(
  "platform-redis"
  "platform-mongo"
  "platform-minio"
  "platform-meilisearch"
  "platform-nacos"
)

echo ""
echo "============================================="
echo "  🏗️  全局基础设施层 — 启动"
echo "  时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo "  项目: ${COMPOSE_PROJECT}"
echo "============================================="
echo ""

# ====== Step 1: 清理残留容器 ======
echo ">>> [1/4] 清理残留容器 <<<"
cleaned=0
for svc in "${PLATFORM_SERVICES[@]}"; do
  # 查找占用该名称但不是 compose 管理的残留容器
  orphan=$(docker ps -a --filter "name=^${svc}$" --format "{{.ID}} {{.Status}}" 2>/dev/null || true)
  if [ -n "$orphan" ]; then
    # 检查是否是 compose 管理的容器（有 compose project label）
    is_compose=$(docker inspect "$svc" 2>/dev/null | grep -q '"com.docker.compose.project"' && echo "yes" || echo "no")
    if [ "$is_compose" = "no" ]; then
      log_warn "发现残留容器: ${svc}，正在删除..."
      docker rm -f "$svc" 2>/dev/null || true
      log_ok "已删除残留: ${svc}"
      cleaned=$((cleaned + 1))
    fi
  fi
done
if [ $cleaned -eq 0 ]; then
  log_ok "无残留容器，环境干净"
fi
echo ""

# ====== Step 2: 同步数据卷（旧 kb- 前缀迁移） ======
echo ">>> [2/4] 检查数据卷兼容性 <<<"
# 如果旧 kb-* 卷存在但新 platform-* 卷不存在，做一次迁移提示
for old_vol in "kb-mysql-data" "kb-redis-data" "kb-mongo-data" "kb-minio-data" "kb-meili-data" "kb-nacos-data" "kb-nacos-logs"; do
  new_vol=$(echo "$old_vol" | sed 's/^kb-/platform-/')
  if docker volume ls --format '{{.Name}}' | grep -q "^${old_vol}$" && ! docker volume ls --format '{{.Name}}' | grep -q "^${new_vol}$"; then
    log_warn "发现旧卷 ${old_vol}，新卷 ${new_vol} 不存在。如需迁移数据请手动操作:"
    log_info "  docker volume create ${new_vol} && docker run --rm -v ${old_vol}:/src -v ${new_vol}:/dst alpine cp -a /src/. /dst/"
  fi
done
log_ok "数据卷检查完成"
echo ""

# ====== Step 3: 启动基础设施 ======
echo ">>> [3/4] 启动基础设施服务 <<<"
cd "${SCRIPT_DIR}"

# 注意：不要手动 docker network create platform-net！
# compose 文件已声明 external: true + name: platform-net
# 手动创建的裸网络没有 compose label，会导致后续 compose up 报错：
#   "network platform-net was found but has incorrect label"
# 让 compose 自己管理网络即可

docker compose -p "${COMPOSE_PROJECT}" -f "${COMPOSE_FILE}" up -d 2>&1

echo ""
log_ok "compose up 完成"
echo ""

# ====== Step 4: 等待健康检查 ======
echo ">>> [4/4] 等待健康检查 <<<"
max_wait=120  # 最大等待 2 分钟
interval=5
elapsed=0

while [ $elapsed -lt $max_wait ]; do
  all_healthy=true
  for svc in "${PLATFORM_SERVICES[@]}"; do
    status=$(docker inspect --format='{{.State.Status}}' "$svc" 2>/dev/null || echo "not-found")
    health=$(docker inspect --format='{{.State.Health.Status}}' "$svc" 2>/dev/null || echo "no-healthcheck")
    
    if [ "$status" != "running" ]; then
      all_healthy=false
      break
    fi
    
    if [ "$health" = "starting" ]; then
      all_healthy=false
      break
    fi
    
    if [ "$health" = "unhealthy" ]; then
      log_err "${svc} 状态: unhealthy"
      all_healthy=false
      break
    fi
  done
  
  if $all_healthy; then
    echo ""
    log_ok "所有基础设施服务已就绪!"
    break
  fi
  
  sleep $interval
  elapsed=$((elapsed + interval))
  
  if [ $((elapsed % 15)) -eq 0 ]; then
    log_info "等待中... (${elapsed}s/${max_wait}s)"
  fi
done

if [ $elapsed -ge $max_wait ]; then
  log_warn "超时 (${max_wait}s)，部分服务可能仍在启动中"
fi

echo ""
echo "============================================="
echo "  📊 当前基础设施状态"
echo "============================================="
docker compose -p "${COMPOSE_PROJECT}" -f "${COMPOSE_FILE}" ps 2>/dev/null || \
  docker ps --filter "name=platform-" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
echo ""
echo "============================================="
echo "  ✅ 基础设施层启动完成!"
echo "  MySQL:     GR 集群 (platform-mysql-1/2/3，见 platform/mysql/)"
echo "  Redis:     localhost:6379"
echo "  MongoDB:   localhost:27017"
echo "  MinIO:     localhost:9000 (控制台: 9001)"
echo "  MeiliSearch: localhost:7700"
echo "  Nacos:     localhost:8848 (gRPC: 9848)"
echo "============================================="
