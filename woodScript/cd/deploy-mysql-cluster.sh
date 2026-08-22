#!/bin/bash
# ============================================================
# deploy-mysql-cluster.sh — MySQL GR 集群重启脚本（流水线调用）
# ============================================================
# 由 .woodpecker.yml 的 platform-deploy 步骤调用
# 功能:
#   1. 重启 mykng (105) 上的 Node1 (platform-mysql-1)
#   2. SSH 重启 Debian (182) 上的 Node2+Node3
#   3. 等待 GR 集群恢复（3/3 节点 ONLINE）
#
# 注意: 集群搭建是一次性操作，用 mysql_cluster_manager.py add-node 完成。
#       此脚本只负责流水线中的重启操作。
# ============================================================
set -euo pipefail

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

# ====== Step 1: 重启 mykng 上的 Node1 ======
echo ""
echo ">>> [1/3] 重启 Node1 (mykng ${MYKNG_HOST}) <<<"
# 清除 mysqld-auto.cnf（SET PERSIST 持久化的旧变量），确保读取 cluster.cnf
docker exec platform-mysql-1 rm -f /var/lib/mysql/mysqld-auto.cnf 2>/dev/null || true
docker restart platform-mysql-1 2>&1
log_ok "Node1 已重启"

# ====== Step 2: 重启 Debian 上的 Node2+Node3 ======
echo ""
echo ">>> [2/3] 重启 Node2+Node3 (Debian ${DEBIAN_HOST}) <<<"
ssh -o StrictHostKeyChecking=no root@${DEBIAN_HOST} \
  "docker exec platform-mysql-2 rm -f /var/lib/mysql/mysqld-auto.cnf; docker exec platform-mysql-3 rm -f /var/lib/mysql/mysqld-auto.cnf; docker restart platform-mysql-2 platform-mysql-3" 2>&1
log_ok "Node2+Node3 已重启"

# ====== Step 3: 等待集群恢复 ======
echo ""
echo ">>> [3/3] 等待 GR 集群恢复 <<<"
sleep 10

# 全量重启时需要引导 Node1（因为所有节点同时重启，没有引导者）
# 等待 20s，如果还是 0 ONLINE，说明需要引导
max_wait=90
elapsed=0
bootstrap_done=false
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
  
  # 全量重启后 20s 仍然 0 ONLINE，引导 Node1
  if [ "$member_count" = "0" ] && [ "$bootstrap_done" = "false" ] && [ $elapsed -ge 20 ]; then
    log_info "全量重启检测：引导 Node1..."
    docker exec platform-mysql-1 mysql -uroot -p${MYSQL_ROOT_PASSWORD} -e \
      "STOP GROUP_REPLICATION; SET GLOBAL group_replication_bootstrap_group=ON; START GROUP_REPLICATION; SET GLOBAL group_replication_bootstrap_group=OFF;" 2>/dev/null || true
    bootstrap_done=true
    log_ok "Node1 引导已执行，等待其他节点加入..."
    sleep 5
    continue
  fi
  
  echo "  ⏳ 等待中... (${member_count}/3 ONLINE, ${elapsed}s/${max_wait}s)"
  sleep 5
  elapsed=$((elapsed + 5))
done

log_err "GR 集群恢复超时 (${max_wait}s)"
docker exec platform-mysql-1 mysql -uroot -p${MYSQL_ROOT_PASSWORD} -e \
  "SELECT MEMBER_HOST, MEMBER_PORT, MEMBER_STATE, MEMBER_ROLE FROM performance_schema.replication_group_members" 2>/dev/null || true
exit 1
