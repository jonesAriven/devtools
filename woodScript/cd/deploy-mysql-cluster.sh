#!/bin/bash
# ============================================================
# deploy-mysql-cluster.sh — MySQL GR 集群重启脚本（流水线调用）
# ============================================================
# 由 .woodpecker.yml 的 platform-deploy 步骤调用
# 功能:
#   1. 重启并引导 Node1 (mykng 105)
#   2. 等 Node1 ONLINE 后重启 Node2+Node3 (Debian 182)
#   3. 等待 3/3 节点 ONLINE
#
# 注意: 集群搭建是一次性操作，用 mysql_cluster_manager.py add-node 完成。
#       此脚本只负责流水线中的重启操作。
# ============================================================
set -uo pipefail

MYKNG_HOST="192.168.31.105"
DEBIAN_HOST="192.168.31.182"
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

# ====== Step 1: 重启并引导 Node1 ======
echo ""
echo ">>> [1/3] 重启 Node1 (mykng ${MYKNG_HOST}) <<<"
# 清除 mysqld-auto.cnf（SET PERSIST 持久化的旧变量），确保读取 cluster.cnf
docker exec platform-mysql-1 rm -f /var/lib/mysql/mysqld-auto.cnf 2>/dev/null || true
docker restart platform-mysql-1 2>&1
log_ok "Node1 已重启"

# 等待 MySQL 就绪
log_info "等待 Node1 MySQL 就绪..."
for i in $(seq 1 30); do
  if docker exec platform-mysql-1 mysql -uroot -p${MYSQL_ROOT_PASSWORD} -e "SELECT 1" &>/dev/null; then
    log_ok "Node1 MySQL 已就绪 (${i}s)"
    break
  fi
  sleep 1
done

# 引导 Node1（全量重启时没有引导者，必须手动引导）
# 先 STOP（如果 GR 已在运行），再 bootstrap
log_info "引导 Node1..."
docker exec platform-mysql-1 mysql -uroot -p${MYSQL_ROOT_PASSWORD} -e \
  "STOP GROUP_REPLICATION; SET GLOBAL group_replication_bootstrap_group=ON; START GROUP_REPLICATION; SET GLOBAL group_replication_bootstrap_group=OFF;" 2>&1 || true
log_ok "Node1 引导完成"

# 确认 Node1 已 ONLINE
sleep 3
member_count=$(docker exec platform-mysql-1 mysql -uroot -p${MYSQL_ROOT_PASSWORD} -N -e \
  "SELECT COUNT(*) FROM performance_schema.replication_group_members WHERE MEMBER_STATE='ONLINE'" 2>/dev/null || echo "0")
if [ "$member_count" -ge 1 ]; then
  log_ok "Node1 已 ONLINE (集群成员: ${member_count})"
else
  log_err "Node1 未 ONLINE，集群无法恢复"
  exit 1
fi

# ====== Step 2: 重启 Node2+Node3（Node1 已在线，它们会自动加入） ======
echo ""
echo ">>> [2/3] 重启 Node2+Node3 (Debian ${DEBIAN_HOST}) <<<"
ssh -o StrictHostKeyChecking=no root@${DEBIAN_HOST} \
  "docker exec platform-mysql-2 rm -f /var/lib/mysql/mysqld-auto.cnf 2>/dev/null; docker exec platform-mysql-3 rm -f /var/lib/mysql/mysqld-auto.cnf 2>/dev/null; docker restart platform-mysql-2 platform-mysql-3" 2>&1
log_ok "Node2+Node3 已重启"

# ====== Step 3: 等待 3/3 节点 ONLINE ======
echo ""
echo ">>> [3/3] 等待 GR 集群恢复 <<<"
sleep 10
max_wait=90
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
