#!/bin/bash
# ============================================================
# mykng 知识库微服务健康检查脚本（SOP附录G要求）
# ============================================================
# 用法:
#   bash scripts/health-check.sh                 # 全量检查
#   bash scripts/health-check.sh kb-auth         # 仅检查单个服务
#   bash scripts/health-check.sh -help           # 显示帮助
#
# 检查内容:
#   1. 所有容器运行状态（docker ps）
#   2. 各微服务 actuator/health 端点
#   3. 基础设施连通性（MySQL/Redis/MongoDB/MinIO/MeiliSearch）
#   4. 输出绿/红状态汇总
# ============================================================

set -e

# ---------- 路径变量化 ----------
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
COMPOSE_PROJECT="kb-deploy"

cd "$PROJECT_ROOT"

# ---------- 颜色输出 ----------
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;36m'
NC='\033[0m'

PASS=0
WARN_COUNT=0
FAIL=0

ok()   { echo -e "  ${GREEN}[✓ PASS]${NC} $1"; PASS=$((PASS + 1)); }
warn() { echo -e "  ${YELLOW}[! WARN]${NC} $1"; WARN_COUNT=$((WARN_COUNT + 1)); }
bad()  { echo -e "  ${RED}[✗ FAIL]${NC} $1"; FAIL=$((FAIL + 1)); }
info() { echo -e "${BLUE}[INFO ]${NC} $1"; }

# 微服务: name=port
SERVICES=(
    "kb-gateway=8080"
    "kb-auth=8081"
    "kb-file=8082"
    "kb-knowledge=8083"
    "kb-ops=8084"
    "kb-intelligence=8086"
)

# 基础设施: name=container_name
INFRA_CONTAINERS=("kb-mysql" "kb-redis" "kb-mongo" "kb-minio" "kb-meilisearch")

# 从 .env 读取密码
MYSQL_PASS="${MYSQL_ROOT_PASSWORD:-kb123456}"
MONGO_USER="${MONGO_ROOT_USER:-kb}"
MONGO_PASS="${MONGO_ROOT_PASSWORD:-kb123456}"
MINIO_USER="${MINIO_ROOT_USER:-minioadmin}"
MINIO_PASS="${MINIO_ROOT_PASSWORD:-minioadmin}"
MEILI_KEY="${MEILI_MASTER_KEY:-kbMeiliKey2026!!Secure}"
if [ -f "$PROJECT_ROOT/.env" ]; then
    # shellcheck disable=SC1091
    . "$PROJECT_ROOT/.env"
    MYSQL_PASS="${MYSQL_ROOT_PASSWORD:-kb123456}"
    MONGO_USER="${MONGO_ROOT_USER:-kb}"
    MONGO_PASS="${MONGO_ROOT_PASSWORD:-kb123456}"
    MINIO_USER="${MINIO_ROOT_USER:-minioadmin}"
    MINIO_PASS="${MINIO_ROOT_PASSWORD:-minioadmin}"
    MEILI_KEY="${MEILI_MASTER_KEY:-kbMeiliKey2026!!Secure}"
fi

# ---------- 帮助 ----------
show_help() {
    cat <<EOF
mykng 知识库微服务健康检查脚本

用法:
  bash scripts/health-check.sh [service]
  bash scripts/health-check.sh -help

参数:
  service   可选服务名: kb-gateway / kb-auth / kb-file / kb-knowledge / kb-ops / kb-intelligence
            不指定则全量检查

示例:
  bash scripts/health-check.sh
  bash scripts/health-check.sh kb-auth
EOF
}

# ---------- 容器状态检查 ----------
check_container() {
    local name="$1"
    local status health
    status=$(docker inspect --format='{{.State.Status}}' "$name" 2>/dev/null || echo "not-found")
    health=$(docker inspect --format='{{.State.Health.Status}}' "$name" 2>/dev/null || echo "no-healthcheck")

    if [ "$status" = "running" ]; then
        if [ "$health" = "healthy" ] || [ "$health" = "no-healthcheck" ]; then
            ok "$name (status=$status, health=${health})"
        else
            warn "$name (status=$status, health=$health)"
        fi
    else
        bad "$name (status=$status)"
    fi
}

# ---------- actuator/health 端点检查 ----------
check_actuator() {
    local name="$1"
    local port="$2"
    local result
    result=$(docker exec "$name" wget -qO- --timeout=5 "http://localhost:$port/actuator/health" 2>/dev/null || echo "FAILED")

    if echo "$result" | grep -q '"status":"UP"'; then
        ok "$name actuator/health = UP"
    elif echo "$result" | grep -q '"status":"DOWN"'; then
        bad "$name actuator/health = DOWN"
    else
        bad "$name actuator/health 不可达: $result"
    fi
}

# ---------- MySQL 连通性 ----------
check_mysql() {
    info "检查 MySQL 连通性..."
    if docker exec kb-mysql mysqladmin -uroot -p"$MYSQL_PASS" ping 2>/dev/null | grep -q "mysqld is alive"; then
        ok "MySQL ping 成功"
        local db_count
        db_count=$(docker exec kb-mysql mysql -uroot -p"$MYSQL_PASS" -N -e "SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME LIKE 'kb_%';" 2>/dev/null)
        if [ "$db_count" = "5" ]; then
            ok "MySQL kb_* 数据库数量 = 5"
        else
            warn "MySQL kb_* 数据库数量 = ${db_count:-0}（期望 5）"
        fi
    else
        bad "MySQL ping 失败"
    fi
}

# ---------- Redis 连通性 ----------
check_redis() {
    info "检查 Redis 连通性..."
    local result
    result=$(docker exec kb-redis redis-cli ping 2>/dev/null || echo "FAILED")
    if [ "$result" = "PONG" ]; then
        ok "Redis PING = PONG"
    else
        bad "Redis PING 失败: $result"
    fi
}

# ---------- MongoDB 连通性 ----------
check_mongodb() {
    info "检查 MongoDB 连通性..."
    local result
    result=$(docker exec kb-mongo mongosh --quiet --eval "db.adminCommand('ping').ok" 2>/dev/null || echo "FAILED")
    if [ "$result" = "1" ]; then
        ok "MongoDB ping = 1"
    else
        bad "MongoDB ping 失败: $result"
    fi
}

# ---------- MinIO 连通性 ----------
check_minio() {
    info "检查 MinIO 连通性..."
    local code
    code=$(docker exec kb-minio wget --spider --server-response "http://localhost:9000/minio/health/live" 2>&1 | grep "HTTP/" | tail -1 | awk '{print $2}')
    if [ "$code" = "200" ]; then
        ok "MinIO /minio/health/live = 200"
    else
        bad "MinIO 健康检查失败 (HTTP ${code:-unknown})"
    fi
}

# ---------- MeiliSearch 连通性 ----------
check_meilisearch() {
    info "检查 MeiliSearch 连通性..."
    local code
    code=$(docker exec kb-meilisearch wget --spider --server-response "http://localhost:7700/health" 2>&1 | grep "HTTP/" | tail -1 | awk '{print $2}')
    if [ "$code" = "200" ]; then
        ok "MeiliSearch /health = 200"
    else
        bad "MeiliSearch 健康检查失败 (HTTP ${code:-unknown})"
    fi
}

# ---------- 主流程 ----------
TARGET="$1"

if [ "$TARGET" = "-help" ] || [ "$TARGET" = "--help" ] || [ "$TARGET" = "-h" ]; then
    show_help
    exit 0
fi

echo "============================================================"
echo "  mykng 知识库健康检查"
echo "  时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo "  项目: $COMPOSE_PROJECT"
echo "============================================================"
echo ""

# 1. 容器列表概览
info "=== Docker 容器列表 ==="
docker compose -p "$COMPOSE_PROJECT" ps --format "table {{.Name}}\t{{.Status}}\t{{.Ports}}" 2>/dev/null || docker ps --filter "name=kb-" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
echo ""

# 2. 微服务容器状态
info "=== 微服务容器状态 ==="
for entry in "${SERVICES[@]}"; do
    name="${entry%%=*}"
    if [ -z "$TARGET" ] || [ "$TARGET" = "$name" ]; then
        check_container "$name"
    fi
done
echo ""

# 3. 基础设施容器状态
if [ -z "$TARGET" ]; then
    info "=== 基础设施容器状态 ==="
    for name in "${INFRA_CONTAINERS[@]}"; do
        check_container "$name"
    done
    echo ""
fi

# 4. 微服务 actuator/health 端点
info "=== 微服务 actuator/health 端点 ==="
for entry in "${SERVICES[@]}"; do
    name="${entry%%=*}"
    port="${entry##*=}"
    if [ -z "$TARGET" ] || [ "$TARGET" = "$name" ]; then
        # 仅当容器在运行时才检查
        status=$(docker inspect --format='{{.State.Status}}' "$name" 2>/dev/null || echo "not-found")
        if [ "$status" = "running" ]; then
            check_actuator "$name" "$port"
        else
            warn "$name 容器未运行，跳过 actuator 检查"
        fi
    fi
done
echo ""

# 5. 基础设施连通性
if [ -z "$TARGET" ]; then
    info "=== 基础设施连通性 ==="
    check_mysql
    check_redis
    check_mongodb
    check_minio
    check_meilisearch
    echo ""
fi

# 6. 汇总
echo "============================================================"
echo -e "  检查结果汇总: ${GREEN}通过 $PASS${NC} / ${YELLOW}警告 $WARN_COUNT${NC} / ${RED}失败 $FAIL${NC}"
echo "============================================================"

if [ $FAIL -gt 0 ]; then
    exit 1
fi
exit 0
