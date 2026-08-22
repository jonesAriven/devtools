#!/bin/bash
# ============================================================
# deploy-mysql-cluster.sh — MySQL GR 集群重启脚本（流水线调用）
# ============================================================
# 由 .woodpecker.yml 的 platform-deploy 步骤调用
# 功能:
#   0. 首次执行时迁移旧 volume（platform_platform-mysql-data → mysql-cluster_platform-mysql-1-data）
#   1. 在 mykng (105) 重启 Node1 (platform-mysql-1)
#   2. 在 Debian (182) 重启 Node2+Node3 (platform-mysql-2/3)
#   3. 等待集群恢复，检查成员状态
#
# 注意: MySQL GR 集群重启时，因为 group_replication_start_on_boot=ON 且数据已初始化，
#       重启容器后 GR 会自动重新加入集群
# ============================================================
set -euo pipefail

MYKNG_HOST="192.168.31.105"
DEBIAN_HOST="192.168.31.182"
CLUSTER_COMPOSE_DIR="/mnt/shared/platform/mysql"
MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-kb123456}"

# ====== 颜色 ======
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_ok()   { echo -e "  ${GREEN}✅${NC} $1"; }
log_err()  { echo -e "  ${RED}❌${NC} $1"; }
log_info() { echo -e "  ${YELLOW}ℹ️${NC} $1"; }

echo "============================================="
echo "  🔄 MySQL GR 集群 — 重启"
echo "  时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo "============================================="

# ====== Step 0: 数据卷迁移检查（仅首次需要） ======
echo ""
echo ">>> [0/4] 数据卷迁移检查 <<<"

# mykng: 旧卷 platform_platform-mysql-data → 新卷 mysql-cluster_platform-mysql-1-data
OLD_VOL_MYKNG="platform_platform-mysql-data"
NEW_VOL_MYKNG="mysql-cluster_platform-mysql-1-data"

if docker volume inspect "${OLD_VOL_MYKNG}" >/dev/null 2>&1; then
  if docker volume inspect "${NEW_VOL_MYKNG}" >/dev/null 2>&1; then
    log_info "新卷 ${NEW_VOL_MYKNG} 已存在，跳过迁移"
  else
    log_info "迁移 ${OLD_VOL_MYKNG} → ${NEW_VOL_MYKNG}"
    docker volume create "${NEW_VOL_MYKNG}" >/dev/null
    docker run --rm -v "${OLD_VOL_MYKNG}:/from" -v "${NEW_VOL_MYKNG}:/to" alpine sh -c "cp -a /from/. /to/" 2>&1
    log_ok "mykng 数据卷迁移完成"
  fi
else
  log_info "旧卷 ${OLD_VOL_MYKNG} 不存在（已迁移或首次部署）"
fi

# Debian: 旧卷 mysql-cluster_node2-data/node3-data → 新卷 mysql-cluster_platform-mysql-2-data/3-data
# 由 SSH 在 Debian 上执行迁移
ssh -o StrictHostKeyChecking=no root@${DEBIAN_HOST} bash -s << 'MIGRATE_EOF'
set -euo pipefail

migrate_vol() {
  local old="$1" new="$2"
  if docker volume inspect "${old}" >/dev/null 2>&1; then
    if docker volume inspect "${new}" >/dev/null 2>&1; then
      echo "  ℹ️ 新卷 ${new} 已存在，跳过迁移"
    else
      echo "  ℹ️ 迁移 ${old} → ${new}"
      docker volume create "${new}" >/dev/null
      docker run --rm -v "${old}:/from" -v "${new}:/to" alpine sh -c "cp -a /from/. /to/" 2>&1
      echo "  ✅ ${new} 迁移完成"
    fi
  else
    echo "  ℹ️ 旧卷 ${old} 不存在（已迁移或首次部署）"
  fi
}

migrate_vol "mysql-cluster_node2-data" "mysql-cluster_platform-mysql-2-data"
migrate_vol "mysql-cluster_node3-data" "mysql-cluster_platform-mysql-3-data"
MIGRATE_EOF

log_ok "数据卷迁移检查完成"

# ====== Step 1: 清理旧容器（如果存在） ======
echo ""
echo ">>> [1/4] 清理旧容器 <<<"

# 清理 mykng 上的旧 platform-mysql 容器
if docker ps -a --format '{{.Names}}' | grep -q '^platform-mysql$'; then
  log_info "移除旧容器 platform-mysql"
  docker rm -f platform-mysql 2>/dev/null || true
fi

# 清理 Debian 上的旧 mysql-cluster-node2/node3 容器
ssh -o StrictHostKeyChecking=no root@${DEBIAN_HOST} bash -c '
  for c in mysql-cluster-node2 mysql-cluster-node3; do
    if docker ps -a --format "{{.Names}}" | grep -q "^${c}$"; then
      echo "  ℹ️ 移除旧容器 ${c}"
      docker rm -f ${c} 2>/dev/null || true
    fi
  done
' 2>&1

log_ok "旧容器清理完成"

# ====== Step 2: 重启 mykng 上的 Node1 ======
echo ""
echo ">>> [2/4] 重启 Node1 (mykng ${MYKNG_HOST}) <<<"
if [ -f "${CLUSTER_COMPOSE_DIR}/docker-compose.mysql-cluster.yml" ]; then
  docker compose -p mysql-cluster -f "${CLUSTER_COMPOSE_DIR}/docker-compose.mysql-cluster.yml" up -d --force-recreate 2>&1
  log_ok "Node1 重启完成"
else
  log_err "compose 文件不存在: ${CLUSTER_COMPOSE_DIR}/docker-compose.mysql-cluster.yml"
  exit 1
fi

# ====== Step 3: SSH 重启 Debian 上的 Node2+Node3 ======
echo ""
echo ">>> [3/4] 重启 Node2+Node3 (Debian ${DEBIAN_HOST}) <<<"
ssh -o StrictHostKeyChecking=no root@${DEBIAN_HOST} \
  "docker compose -p mysql-cluster -f ${CLUSTER_COMPOSE_DIR}/docker-compose.mysql-cluster.debian.yml up -d --force-recreate" 2>&1
log_ok "Node2+Node3 重启完成"

# ====== Step 4: 等待集群恢复 ======
echo ""
echo ">>> [4/4] 等待 GR 集群恢复 <<<"
sleep 10
max_wait=60
elapsed=0
while [ $elapsed -lt $max_wait ]; do
  member_count=$(docker exec platform-mysql-1 mysql -uroot -p${MYSQL_ROOT_PASSWORD} -N -e \
    "SELECT COUNT(*) FROM performance_schema.replication_group_members WHERE MEMBER_STATE='ONLINE'" 2>/dev/null || echo "0")
  
  if [ "$member_count" = "3" ]; then
    log_ok "GR 集群恢复完成 (3/3 节点 ONLINE)"
    docker exec platform-mysql-1 mysql -uroot -p${MYSQL_ROOT_PASSWORD} -e \
      "SELECT MEMBER_HOST, MEMBER_PORT, MEMBER_STATE, MEMBER_ROLE FROM performance_schema.replication_group_members" 2>/dev/null
    echo ""
    echo "============================================="
    echo "  ✅ MySQL GR 集群重启完成!"
    echo "============================================="
    exit 0
  fi
  
  echo "  ⏳ 等待中... (${member_count}/3 ONLINE, ${elapsed}s/${max_wait}s)"
  sleep 5
  elapsed=$((elapsed + 5))
done

log_err "GR 集群恢复超时 (${max_wait}s)"
docker exec platform-mysql-1 mysql -uroot -p${MYSQL_ROOT_PASSWORD} -e \
  "SELECT MEMBER_HOST, MEMBER_PORT, MEMBER_STATE, MEMBER_ROLE FROM performance_schema.replication_group_members" 2>/dev/null || true
exit 1
