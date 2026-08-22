#!/bin/bash
# ============================================================
# deploy-mysql-cluster.sh — MySQL GR 集群重启脚本（流水线调用）
# ============================================================
# 由 .woodpecker.yml 的 platform-deploy 步骤调用
# 功能:
#   0. 首次执行时迁移旧 volume
#   1. 清理旧容器
#   2. 启动 Node1 并引导集群（bootstrap group）
#   3. 启动 Node2+Node3（自动加入集群）
#   4. 等待集群恢复，检查成员状态
#
# 注意: 全量重启时需要先引导一个节点，否则所有节点同时启动
#       会互相找不到对方，GR 无法形成集群
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
echo ">>> [0/5] 数据卷迁移检查 <<<"

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

# Debian: 旧卷迁移
ssh -o StrictHostKeyChecking=no root@${DEBIAN_HOST} bash << 'MIGRATE_EOF'
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

# ====== Step 1: 清理旧容器 ======
echo ""
echo ">>> [1/5] 清理旧容器 <<<"

if docker ps -a --format '{{.Names}}' | grep -q '^platform-mysql$'; then
  log_info "移除旧容器 platform-mysql"
  docker rm -f platform-mysql 2>/dev/null || true
fi

ssh -o StrictHostKeyChecking=no root@${DEBIAN_HOST} bash << 'CLEANUP_EOF'
set -euo pipefail
for c in mysql-cluster-node2 mysql-cluster-node3; do
  if docker ps -a --format "{{.Names}}" | grep -q "^${c}$"; then
    echo "  ℹ️ 移除旧容器 ${c}"
    docker rm -f ${c} 2>/dev/null || true
  fi
done
CLEANUP_EOF

log_ok "旧容器清理完成"

# ====== Step 2: 启动 Node1 并引导集群 ======
echo ""
echo ">>> [2/5] 启动 Node1 (mykng ${MYKNG_HOST}) <<<"
if [ -f "${CLUSTER_COMPOSE_DIR}/docker-compose.mysql-cluster.yml" ]; then
  docker compose -p mysql-cluster -f "${CLUSTER_COMPOSE_DIR}/docker-compose.mysql-cluster.yml" up -d --force-recreate 2>&1
  log_ok "Node1 容器已启动"
else
  log_err "compose 文件不存在: ${CLUSTER_COMPOSE_DIR}/docker-compose.mysql-cluster.yml"
  exit 1
fi

# 等待 Node1 MySQL ready
log_info "等待 Node1 MySQL 就绪..."
max_wait_mysql=30
elapsed=0
while [ $elapsed -lt $max_wait_mysql ]; do
  if docker exec platform-mysql-1 mysqladmin ping -uroot -p${MYSQL_ROOT_PASSWORD} 2>/dev/null | grep -q "alive"; then
    log_ok "Node1 MySQL 已就绪"
    break
  fi
  echo "  ⏳ MySQL 启动中... (${elapsed}s/${max_wait_mysql}s)"
  sleep 3
  elapsed=$((elapsed + 3))
done

if [ $elapsed -ge $max_wait_mysql ]; then
  log_err "Node1 MySQL 启动超时"
  exit 1
fi

# 引导集群：先清除持久化的旧 GR 变量，停止 GR，设置 bootstrap=ON，启动 GR
log_info "清除持久化的旧 GR 变量..."
docker exec platform-mysql-1 mysql -uroot -p${MYSQL_ROOT_PASSWORD} -e \
  "RESET PERSIST group_replication_group_seeds; RESET PERSIST group_replication_start_on_boot;" 2>/dev/null || true

log_info "引导 GR 集群..."
docker exec platform-mysql-1 mysql -uroot -p${MYSQL_ROOT_PASSWORD} -e \
  "STOP GROUP_REPLICATION; SET GLOBAL group_replication_bootstrap_group=ON; START GROUP_REPLICATION; SET GLOBAL group_replication_bootstrap_group=OFF;" 2>&1 || true

sleep 3

# 检查 Node1 是否成功加入集群
member_state=$(docker exec platform-mysql-1 mysql -uroot -p${MYSQL_ROOT_PASSWORD} -N -e \
  "SELECT MEMBER_STATE FROM performance_schema.replication_group_members WHERE MEMBER_HOST='192.168.31.105'" 2>/dev/null || echo "ERROR")

if [ "$member_state" = "ONLINE" ]; then
  log_ok "Node1 引导成功，状态: ONLINE"
else
  log_err "Node1 引导失败，状态: ${member_state}"
  log_info "查看 Node1 GR 日志..."
  docker exec platform-mysql-1 mysql -uroot -p${MYSQL_ROOT_PASSWORD} -e \
    "SELECT * FROM performance_schema.replication_group_members" 2>/dev/null || true
  exit 1
fi

# ====== Step 3: 启动 Node2+Node3 ======
echo ""
echo ">>> [3/5] 启动 Node2+Node3 (Debian ${DEBIAN_HOST}) <<<"
ssh -o StrictHostKeyChecking=no root@${DEBIAN_HOST} \
  "docker compose -p mysql-cluster -f ${CLUSTER_COMPOSE_DIR}/docker-compose.mysql-cluster.debian.yml up -d --force-recreate" 2>&1
log_ok "Node2+Node3 容器已启动"

# 等待 Node2/Node3 MySQL ready，然后手动启动 GR（因为 start_on_boot=OFF）
log_info "等待 Node2/Node3 MySQL 就绪并启动 GR..."
sleep 15

# 清除持久化的旧 GR 变量并启动 GR
ssh -o StrictHostKeyChecking=no root@${DEBIAN_HOST} bash << 'START_GR_EOF'
set -euo pipefail
for node in platform-mysql-2 platform-mysql-3; do
  echo "  ℹ️ 清除 ${node} 旧 GR 变量并启动 GR..."
  docker exec ${node} mysql -uroot -pkb123456 -e \
    "RESET PERSIST group_replication_group_seeds; RESET PERSIST group_replication_start_on_boot; STOP GROUP_REPLICATION; START GROUP_REPLICATION;" 2>/dev/null || true
  echo "  ✅ ${node} GR 已启动"
done
START_GR_EOF

# ====== Step 4: 等待集群恢复 ======
echo ""
echo ">>> [4/5] 等待 Node2+Node3 加入集群 <<<"
sleep 10
max_wait=90
elapsed=0
while [ $elapsed -lt $max_wait ]; do
  member_count=$(docker exec platform-mysql-1 mysql -uroot -p${MYSQL_ROOT_PASSWORD} -N -e \
    "SELECT COUNT(*) FROM performance_schema.replication_group_members WHERE MEMBER_STATE='ONLINE'" 2>/dev/null || echo "0")
  
  if [ "$member_count" = "3" ]; then
    log_ok "GR 集群恢复完成 (3/3 节点 ONLINE)"
    break
  fi
  
  echo "  ⏳ 等待中... (${member_count}/3 ONLINE, ${elapsed}s/${max_wait}s)"
  sleep 5
  elapsed=$((elapsed + 5))
done

if [ "$member_count" != "3" ]; then
  log_err "GR 集群恢复超时 (${max_wait}s)"
  docker exec platform-mysql-1 mysql -uroot -p${MYSQL_ROOT_PASSWORD} -e \
    "SELECT MEMBER_HOST, MEMBER_PORT, MEMBER_STATE, MEMBER_ROLE FROM performance_schema.replication_group_members" 2>/dev/null || true
  exit 1
fi

# ====== Step 5: 最终状态确认 ======
echo ""
echo ">>> [5/5] 集群状态确认 <<<"
docker exec platform-mysql-1 mysql -uroot -p${MYSQL_ROOT_PASSWORD} -e \
  "SELECT MEMBER_HOST, MEMBER_PORT, MEMBER_STATE, MEMBER_ROLE FROM performance_schema.replication_group_members" 2>/dev/null

echo ""
echo "============================================="
echo "  ✅ MySQL GR 集群重启完成!"
echo "============================================="
