#!/bin/bash
# ============================================================
# deploy-mysql-cluster.sh — MySQL GR 集群重启脚本（流水线调用）
# ============================================================
# 由 .woodpecker.yml 的 platform-deploy 步骤调用
# 功能:
#   0. 检查集群健康状态，3/3 ONLINE 则跳过重启
#   1. 重启并引导 Node1 (mykng 105)
#   2. 等 Node1 ONLINE 后重启 Node2+Node3 (Debian 182)
#   3. 对 Node2+Node3 执行 START GROUP_REPLICATION 加入集群
#   4. 等待 3/3 节点 ONLINE
#
# 根据 MySQL 官方文档 20.5.2 "Restarting a Group":
#   - 全量重启后，引导节点用 bootstrap_group=ON 启动
#   - 其余节点必须显式 START GROUP_REPLICATION 加入（不会自动加入）
#   - 因为 node2.cnf/node3.cnf 中 group_replication_start_on_boot=OFF
#
# 注意: 集群搭建是一次性操作，用 mysql_cluster_manager.py add-node 完成。
#       此脚本只负责流水线中的重启操作。
#       集群健康时不重启，避免不必要的停机。
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

# ====== Step 0: 健康检查 — 集群已健康则跳过重启 ======
echo ""
echo ">>> [0/4] 检查集群健康状态 <<<"
member_count=$(docker exec platform-mysql-1 mysql -uroot -p${MYSQL_ROOT_PASSWORD} -N -e \
  "SELECT COUNT(*) FROM performance_schema.replication_group_members WHERE MEMBER_STATE='ONLINE'" 2>/dev/null || echo "0")
if [ "$member_count" = "3" ]; then
  log_ok "GR 集群已健康 (3/3 节点 ONLINE)，跳过重启"
  docker exec platform-mysql-1 mysql -uroot -p${MYSQL_ROOT_PASSWORD} -e \
    "SELECT MEMBER_HOST, MEMBER_PORT, MEMBER_STATE, MEMBER_ROLE FROM performance_schema.replication_group_members" 2>/dev/null
  echo ""
  echo "============================================="
  echo "  ✅ MySQL GR 集群已健康，无需重启!"
  echo "============================================="
  exit 0
else
  log_info "集群不健康 (${member_count}/3 ONLINE)，执行重启流程..."
fi

# ====== Step 1: 重启并引导 Node1 ======
echo ""
echo ">>> [1/4] 重启 Node1 (mykng ${MYKNG_HOST}) <<<"
# 清除 mysqld-auto.cnf（SET PERSIST 持久化的旧变量），确保读取 cluster.cnf
docker exec platform-mysql-1 rm -f /var/lib/mysql/mysqld-auto.cnf 2>/dev/null || true
# cluster.cnf 中 group_replication_start_on_boot=OFF，MySQL 启动后 GR 不会自动启动
# 避免了自动启动失败导致 GR 进入 ERROR 状态的问题（官方文档 20.5.2 推荐）
docker restart platform-mysql-1 2>&1
log_ok "Node1 已重启"

# 等待 MySQL 就绪
log_info "等待 Node1 MySQL 就绪..."
node1_ready=false
for i in $(seq 1 30); do
  if docker exec platform-mysql-1 mysql -uroot -p${MYSQL_ROOT_PASSWORD} -e "SELECT 1" &>/dev/null; then
    log_ok "Node1 MySQL 已就绪 (${i}s)"
    node1_ready=true
    break
  fi
  sleep 1
done
if [ "$node1_ready" = false ]; then
  log_err "Node1 MySQL 30s 内未就绪，退出"
  exit 1
fi

# 引导 Node1（全量重启时没有引导者，必须手动引导）
# 参考官方文档 20.5.2: bootstrap ON → START GROUP_REPLICATION → bootstrap OFF
# 注意: start_on_boot 已在重启前临时改为 OFF，所以 MySQL 启动后 GR 未自动启动
#       不需要先 STOP GROUP_REPLICATION（它根本没启动过）
log_info "引导 Node1 (bootstrap group)..."
docker exec platform-mysql-1 mysql -uroot -p${MYSQL_ROOT_PASSWORD} -e \
  "SET GLOBAL group_replication_bootstrap_group=ON; START GROUP_REPLICATION; SET GLOBAL group_replication_bootstrap_group=OFF;" 2>&1
bootstrap_rc=$?
if [ $bootstrap_rc -ne 0 ]; then
  log_err "Node1 bootstrap 失败 (rc=${bootstrap_rc})"
  # 尝试 STOP 后重新引导（万一 GR 自动启动残留）
  log_info "尝试 STOP GROUP_REPLICATION 后重新引导..."
  docker exec platform-mysql-1 mysql -uroot -p${MYSQL_ROOT_PASSWORD} -e \
    "STOP GROUP_REPLICATION; SET GLOBAL group_replication_bootstrap_group=ON; START GROUP_REPLICATION; SET GLOBAL group_replication_bootstrap_group=OFF;" 2>&1 || true
fi

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

# ====== Step 2: 重启 Node2+Node3 ======
echo ""
echo ">>> [2/4] 重启 Node2+Node3 (Debian ${DEBIAN_HOST}) <<<"
ssh -o StrictHostKeyChecking=no root@${DEBIAN_HOST} \
  "docker exec platform-mysql-2 rm -f /var/lib/mysql/mysqld-auto.cnf 2>/dev/null; docker exec platform-mysql-3 rm -f /var/lib/mysql/mysqld-auto.cnf 2>/dev/null; docker restart platform-mysql-2 platform-mysql-3" 2>&1
ssh_rc=$?
if [ $ssh_rc -ne 0 ]; then
  log_err "SSH 到 Debian (${DEBIAN_HOST}) 失败 (rc=${ssh_rc})，Node2+Node3 未重启"
  exit 1
fi
log_ok "Node2+Node3 已重启"

# ====== Step 3: 等待 Node2+Node3 MySQL 就绪后执行 START GROUP_REPLICATION ======
echo ""
echo ">>> [3/4] 等待 Node2+Node3 MySQL 就绪并加入集群 <<<"

# 等待 Node2 MySQL 就绪
log_info "等待 Node2 MySQL 就绪..."
node2_ready=false
for i in $(seq 1 30); do
  if ssh -o StrictHostKeyChecking=no root@${DEBIAN_HOST} \
    "docker exec platform-mysql-2 mysql -uroot -p${MYSQL_ROOT_PASSWORD} -e 'SELECT 1'" &>/dev/null; then
    log_ok "Node2 MySQL 已就绪 (${i}s)"
    node2_ready=true
    break
  fi
  sleep 1
done
if [ "$node2_ready" = false ]; then
  log_err "Node2 MySQL 30s 内未就绪"
  exit 1
fi

# 等待 Node3 MySQL 就绪
log_info "等待 Node3 MySQL 就绪..."
node3_ready=false
for i in $(seq 1 30); do
  if ssh -o StrictHostKeyChecking=no root@${DEBIAN_HOST} \
    "docker exec platform-mysql-3 mysql -uroot -p${MYSQL_ROOT_PASSWORD} -e 'SELECT 1'" &>/dev/null; then
    log_ok "Node3 MySQL 已就绪 (${i}s)"
    node3_ready=true
    break
  fi
  sleep 1
done
if [ "$node3_ready" = false ]; then
  log_err "Node3 MySQL 30s 内未就绪"
  exit 1
fi

# 对 Node2 执行 START GROUP_REPLICATION
# 参考官方文档 20.5.2: 非引导节点显式 START GROUP_REPLICATION 加入集群
log_info "Node2 执行 START GROUP_REPLICATION..."
ssh -o StrictHostKeyChecking=no root@${DEBIAN_HOST} \
  "docker exec platform-mysql-2 mysql -uroot -p${MYSQL_ROOT_PASSWORD} -e 'START GROUP_REPLICATION;'" 2>&1 || true
log_ok "Node2 START GROUP_REPLICATION 已发送"

# 对 Node3 执行 START GROUP_REPLICATION
log_info "Node3 执行 START GROUP_REPLICATION..."
ssh -o StrictHostKeyChecking=no root@${DEBIAN_HOST} \
  "docker exec platform-mysql-3 mysql -uroot -p${MYSQL_ROOT_PASSWORD} -e 'START GROUP_REPLICATION;'" 2>&1 || true
log_ok "Node3 START GROUP_REPLICATION 已发送"

# ====== Step 4: 等待 3/3 节点 ONLINE ======
echo ""
echo ">>> [4/4] 等待 GR 集群恢复 <<<"
sleep 5
max_wait=120
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
