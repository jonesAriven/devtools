#!/bin/bash
# ============================================================
# deploy-mysql-cluster.sh — MySQL GR 集群恢复/重启脚本（流水线调用）
# ============================================================
# 由 .woodpecker.yml 的 platform-deploy 步骤调用
#
# 模式判定（2026-08-29 修复：多数派感知，防脑裂丢数据）：
#   A. 3/3 ONLINE                       → 健康，跳过
#   B. Node2+Node3 已构成多数派组(≥2)但 Node1 不在组内
#                                       → Node1 仅 START GROUP_REPLICATION 加入
#                                         （增量恢复补数据，禁止 bootstrap：
#                                          bootstrap 会造出第二个组，Node1 的
#                                          旧数据成为新主，Node2/3 加入后有
#                                          丢数据/认证冲突风险）
#   C. 无多数派组（0/1 节点 ONLINE）      → 官方 20.5.2 全量重启：
#                                         重启 Node1 + bootstrap → 重启
#                                         Node2/3 + START 加入
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

mysql_n1() { docker exec platform-mysql-1 mysql -uroot -p${MYSQL_ROOT_PASSWORD} "$@" 2>/dev/null; }
mysql_n2() { ssh -o StrictHostKeyChecking=no -o ConnectTimeout=5 root@${DEBIAN_HOST} \
  "docker exec platform-mysql-2 mysql -uroot -p${MYSQL_ROOT_PASSWORD} $*" 2>/dev/null; }

echo "============================================="
echo "  🔄 MySQL GR 集群 — 恢复/重启"
echo "  时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo "============================================="

# ====== Step 0: 探测集群拓扑，判定恢复模式 ======
echo ""
echo ">>> [0/4] 探测集群状态 <<<"

# Node1 侧 ONLINE 成员数（Node1 GR OFFLINE 时为 0）
n1_count=$(mysql_n1 -N -e \
  "SELECT COUNT(*) FROM performance_schema.replication_group_members WHERE MEMBER_STATE='ONLINE'" || echo "0")

# Node2 侧 ONLINE 成员数与成员列表（Node2/3 自组成组时以它为准）
n2_count=$(mysql_n2 -N -e \
  "SELECT COUNT(*) FROM performance_schema.replication_group_members WHERE MEMBER_STATE='ONLINE'" || echo "0")
n2_members=$(mysql_n2 -N -e \
  "SELECT CONCAT(MEMBER_HOST,':',MEMBER_PORT,'=',MEMBER_STATE) FROM performance_schema.replication_group_members" || echo "")
n1_in_group=$(mysql_n2 -N -e \
  "SELECT COUNT(*) FROM performance_schema.replication_group_members \
   WHERE MEMBER_STATE='ONLINE' AND MEMBER_HOST='192.168.31.105'" || echo "0")

log_info "Node1 视角 ONLINE: ${n1_count}/3 | Node2 视角 ONLINE: ${n2_count}/3"
[ -n "$n2_members" ] && log_info "Node2 视角成员: ${n2_members//$'\n'/ | }"

if [ "$n1_count" = "3" ]; then
  log_ok "GR 集群已健康 (3/3 节点 ONLINE)，跳过"
  mysql_n1 -e "SELECT MEMBER_HOST, MEMBER_PORT, MEMBER_STATE, MEMBER_ROLE FROM performance_schema.replication_group_members"
  echo ""
  echo "============================================="
  echo "  ✅ MySQL GR 集群已健康，无需恢复!"
  echo "============================================="
  exit 0
fi

if [ "$n2_count" -ge 2 ] && [ "$n1_in_group" = "0" ]; then
  # ====== 模式 B: Node2/3 已构成多数派，Node1 仅加入（禁 bootstrap）======
  echo ""
  echo ">>> [模式B] Node2+Node3 已构成多数派 (${n2_count}/3)，Node1 加入现有组 <<<"

  # 确保 Node1 mysqld 就绪（容器一般 Up，GR 只是没启动）
  if ! docker exec platform-mysql-1 mysql -uroot -p${MYSQL_ROOT_PASSWORD} -e "SELECT 1" &>/dev/null; then
    log_info "Node1 MySQL 未就绪，尝试启动容器..."
    docker start platform-mysql-1
    for i in $(seq 1 30); do
      docker exec platform-mysql-1 mysql -uroot -p${MYSQL_ROOT_PASSWORD} -e "SELECT 1" &>/dev/null && break
      sleep 1
    done
  fi

  log_info "Node1 执行 START GROUP_REPLICATION（加入，不 bootstrap）..."
  if docker exec platform-mysql-1 mysql -uroot -p${MYSQL_ROOT_PASSWORD} \
    -e "START GROUP_REPLICATION;" 2>&1; then
    log_ok "START GROUP_REPLICATION 已发送"
  else
    log_err "START GROUP_REPLICATION 失败"
    docker logs platform-mysql-1 --tail 20 2>&1 | grep -iE "group_replication|ERROR" || true
    exit 1
  fi

  # 等待 Node1 完成分布式恢复加入组
  max_wait=300
  elapsed=0
  while [ $elapsed -lt $max_wait ]; do
    n1_count=$(mysql_n1 -N -e \
      "SELECT COUNT(*) FROM performance_schema.replication_group_members WHERE MEMBER_STATE='ONLINE'" || echo "0")
    if [ "$n1_count" = "3" ]; then
      log_ok "Node1 已完成恢复加入 (3/3 ONLINE)"
      mysql_n1 -e "SELECT MEMBER_HOST, MEMBER_PORT, MEMBER_STATE, MEMBER_ROLE FROM performance_schema.replication_group_members"
      echo ""
      echo "============================================="
      echo "  ✅ MySQL GR 集群恢复完成 (Node1 增量加入)!"
      echo "============================================="
      exit 0
    fi
    echo "  ⏳ 等待分布式恢复... (${n1_count}/3 ONLINE, ${elapsed}s/${max_wait}s)"
    sleep 5
    elapsed=$((elapsed + 5))
  done

  log_err "Node1 加入超时 (${max_wait}s)"
  docker logs platform-mysql-1 --tail 30 2>&1 | grep -iE "group_replication|ERROR" || true
  exit 1
fi

# ====== 模式 C: 无多数派组，走官方 20.5.2 全量重启 ======
echo ""
echo ">>> [模式C] 无多数派组 (${n2_count}/3)，执行全量重启流程 <<<"

# ------ Step 1: 重启并引导 Node1 ------
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
member_count=$(mysql_n1 -N -e \
  "SELECT COUNT(*) FROM performance_schema.replication_group_members WHERE MEMBER_STATE='ONLINE'" || echo "0")
if [ "$member_count" -ge 1 ]; then
  log_ok "Node1 已 ONLINE (集群成员: ${member_count})"
else
  log_err "Node1 未 ONLINE，集群无法恢复"
  exit 1
fi

# ------ Step 2: 重启 Node2+Node3 ------
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

# ------ Step 3: 等待就绪后 START GROUP_REPLICATION ------
echo ""
echo ">>> [3/4] 等待 Node2+Node3 MySQL 就绪并加入集群 <<<"

for node in platform-mysql-2 platform-mysql-3; do
  log_info "等待 ${node} MySQL 就绪..."
  ready=false
  for i in $(seq 1 30); do
    if ssh -o StrictHostKeyChecking=no root@${DEBIAN_HOST} \
      "docker exec ${node} mysql -uroot -p${MYSQL_ROOT_PASSWORD} -e 'SELECT 1'" &>/dev/null; then
      log_ok "${node} MySQL 已就绪 (${i}s)"
      ready=true
      break
    fi
    sleep 1
  done
  if [ "$ready" = false ]; then
    log_err "${node} MySQL 30s 内未就绪"
    exit 1
  fi
  log_info "${node} 执行 START GROUP_REPLICATION..."
  ssh -o StrictHostKeyChecking=no root@${DEBIAN_HOST} \
    "docker exec ${node} mysql -uroot -p${MYSQL_ROOT_PASSWORD} -e 'START GROUP_REPLICATION;'" 2>&1 || true
  log_ok "${node} START GROUP_REPLICATION 已发送"
done

# ------ Step 4: 等待 3/3 ONLINE ------
echo ""
echo ">>> [4/4] 等待 GR 集群恢复 <<<"
sleep 5
max_wait=120
elapsed=0
while [ $elapsed -lt $max_wait ]; do
  member_count=$(mysql_n1 -N -e \
    "SELECT COUNT(*) FROM performance_schema.replication_group_members WHERE MEMBER_STATE='ONLINE'" || echo "0")

  if [ "$member_count" = "3" ]; then
    log_ok "GR 集群恢复完成 (3/3 节点 ONLINE)"
    mysql_n1 -e "SELECT MEMBER_HOST, MEMBER_PORT, MEMBER_STATE, MEMBER_ROLE FROM performance_schema.replication_group_members"
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
mysql_n1 -e "SELECT MEMBER_HOST, MEMBER_PORT, MEMBER_STATE, MEMBER_ROLE FROM performance_schema.replication_group_members" || true
exit 1
