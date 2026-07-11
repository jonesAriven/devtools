#!/bin/bash
# ============================================================
# mykng 知识库微服务环境初始化脚本（SOP附录G要求，首次部署用）
# ============================================================
# 用法:
#   bash scripts/init-env.sh           # 完整初始化
#   bash scripts/init-env.sh --check   # 仅检查环境
#   bash scripts/init-env.sh -help     # 显示帮助
#
# 执行内容:
#   1. 检查 Docker / Docker Compose 版本
#   2. 创建必要目录（/data/kb-web, /data/logs, /data/backup, /data/import）
#   3. 复制 .env.example 到 .env（如不存在）
#   4. 初始化数据库（执行 init-sql/）
# ============================================================

set -e

# ---------- 路径变量化 ----------
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
COMPOSE_PROJECT="kb-deploy"
COMPOSE_FILE="$PROJECT_ROOT/docker-compose.yml"
INIT_SQL_DIR="$PROJECT_ROOT/init-sql"

# 需要创建的数据目录
DATA_DIRS=(
    "/data/kb-web"
    "/data/logs"
    "/data/backup/mysql"
    "/data/backup/mongodb"
    "/data/import"
)

# 从 .env 读取密码（先以默认值初始化，后续 .env 创建后可重读）
MYSQL_PASS="${MYSQL_ROOT_PASSWORD:-kb123456}"

# ---------- 颜色输出 ----------
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;36m'
NC='\033[0m'

log()  { echo -e "${GREEN}[INFO ]${NC} $1"; }
info() { echo -e "${BLUE}[INFO ]${NC} $1"; }
warn() { echo -e "${YELLOW}[WARN ]${NC} $1"; }
err()  { echo -e "${RED}[ERROR]${NC} $1" >&2; }
step() { echo -e "${BLUE}[STEP ]${NC} $1"; }

PASS=0
FAIL=0
ok()   { echo -e "  ${GREEN}[✓ PASS]${NC} $1"; PASS=$((PASS + 1)); }
bad()  { echo -e "  ${RED}[✗ FAIL]${NC} $1"; FAIL=$((FAIL + 1)); }

# ---------- 帮助 ----------
show_help() {
    cat <<EOF
mykng 知识库环境初始化脚本（首次部署使用）

用法:
  bash scripts/init-env.sh [options]

参数:
  (无)      完整初始化（检查 + 建目录 + 生成 .env + 初始化数据库）
  --check   仅检查环境依赖，不做任何修改
  -help     显示此帮助

初始化内容:
  1. Docker >= 20.10 / Docker Compose >= v2
  2. 数据目录: /data/{kb-web,logs,backup,import}
  3. .env 文件（从 .env.example 复制）
  4. MySQL 数据库（执行 init-sql/）
EOF
}

# ---------- 1. 环境依赖检查 ----------
check_docker() {
    info "=== Docker 环境检查 ==="
    if ! command -v docker >/dev/null 2>&1; then
        bad "未安装 docker"
        return 1
    fi
    local docker_ver
    docker_ver=$(docker --version | awk '{print $3}' | sed 's/,//')
    ok "Docker 已安装: $docker_ver"

    if ! docker compose version >/dev/null 2>&1; then
        bad "未安装 docker compose v2"
        return 1
    fi
    local compose_ver
    compose_ver=$(docker compose version --short 2>/dev/null || echo "unknown")
    ok "Docker Compose v2: $compose_ver"

    # 检查 docker 服务运行
    if docker info >/dev/null 2>&1; then
        ok "Docker daemon 运行中"
    else
        bad "Docker daemon 未运行（systemctl start docker）"
        return 1
    fi

    # 检查磁盘空间（/data 至少 10GB）
    if [ -d /data ]; then
        local free_gb
        free_gb=$(df -BG /data 2>/dev/null | awk 'NR==2{print $4}' | sed 's/G//')
        if [ -n "$free_gb" ] && [ "$free_gb" -ge 10 ]; then
            ok "/data 可用空间: ${free_gb}GB"
        else
            warn "/data 可用空间不足: ${free_gb:-unknown}GB（建议 >= 10GB）"
        fi
    fi
}

# ---------- 2. 创建数据目录 ----------
create_dirs() {
    step "创建数据目录..."
    for d in "${DATA_DIRS[@]}"; do
        if [ -d "$d" ]; then
            info "  已存在: $d"
        else
            mkdir -p "$d"
            ok "  已创建: $d"
        fi
    done
}

# ---------- 3. 生成 .env ----------
ensure_env() {
    step "检查 .env 文件..."
    if [ -f "$PROJECT_ROOT/.env" ]; then
        ok ".env 已存在"
    elif [ -f "$PROJECT_ROOT/.env.example" ]; then
        cp "$PROJECT_ROOT/.env.example" "$PROJECT_ROOT/.env"
        warn "已从 .env.example 复制生成 .env，请按需修改后重新执行"
    else
        err "未找到 .env.example，无法生成 .env"
        return 1
    fi

    # 重新加载 .env 以便后续使用
    if [ -f "$PROJECT_ROOT/.env" ]; then
        # shellcheck disable=SC1091
        . "$PROJECT_ROOT/.env"
        MYSQL_PASS="${MYSQL_ROOT_PASSWORD:-kb123456}"
    fi
}

# ---------- 4. 初始化数据库 ----------
init_database() {
    step "初始化 MySQL 数据库..."

    # 先启动 mysql 容器（如果未启动）
    if ! docker ps --format '{{.Names}}' | grep -q '^platform-mysql$'; then
        info "  platform-mysql 未运行，先启动 mysql 容器..."
        docker compose -p "$COMPOSE_PROJECT" -f "$COMPOSE_FILE" up -d mysql
        # 等待健康
        local elapsed=0
        while [ $elapsed -lt 60 ]; do
            if docker inspect --format='{{.State.Health.Status}}' platform-mysql 2>/dev/null | grep -q "healthy"; then
                break
            fi
            sleep 3
            elapsed=$((elapsed + 3))
        done
    fi
    ok "platform-mysql 容器就绪"

    if [ ! -d "$INIT_SQL_DIR" ]; then
        err "未找到 init-sql 目录: $INIT_SQL_DIR"
        return 1
    fi

    info "  执行 init-sql/ 下所有 SQL 脚本..."
    local sql_count=0
    for sql in "$INIT_SQL_DIR"/*.sql; do
        [ -f "$sql" ] || continue
        local name
        name=$(basename "$sql")
        info "  执行: $name"
        if docker exec -i platform-mysql mysql -uroot -p"$MYSQL_PASS" < "$sql" 2>/dev/null; then
            ok "    ✓ $name"
            sql_count=$((sql_count + 1))
        else
            bad "    ✗ $name"
        fi
    done

    # 验证数据库
    info "  验证数据库创建结果..."
    local db_count
    db_count=$(docker exec platform-mysql mysql -uroot -p"$MYSQL_PASS" -N -e "SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME LIKE 'kb_%';" 2>/dev/null)
    if [ "$db_count" = "5" ]; then
        ok "MySQL kb_* 数据库数量 = 5"
    else
        warn "MySQL kb_* 数据库数量 = ${db_count:-0}（期望 5）"
    fi

    for db in kb_auth kb_file kb_knowledge kb_ops kb_intelligence; do
        local table_count
        table_count=$(docker exec platform-mysql mysql -uroot -p"$MYSQL_PASS" -N -e "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='$db';" 2>/dev/null)
        info "    $db: ${table_count:-0} 张表"
    done
}

# ---------- 主流程 ----------
CHECK_ONLY=0
case "${1:-}" in
    -help|--help|-h) show_help; exit 0 ;;
    --check) CHECK_ONLY=1 ;;
    "") ;;
    *) err "未知参数: $1"; show_help; exit 1 ;;
esac

echo "============================================================"
echo "  mykng 知识库环境初始化"
echo "  时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo "  项目根: $PROJECT_ROOT"
echo "============================================================"
echo ""

# 1. 检查 Docker
check_docker || err "Docker 环境检查未通过"

if [ $CHECK_ONLY -eq 1 ]; then
    info "仅检查模式（--check），跳过修改操作"
    echo ""
    echo "============================================================"
    echo -e "  结果: ${GREEN}通过 $PASS${NC} / ${RED}失败 $FAIL${NC}"
    echo "============================================================"
    exit $FAIL
fi

# 2. 创建数据目录
create_dirs

# 3. 生成 .env
ensure_env || err ".env 生成失败"

# 4. 初始化数据库
init_database || err "数据库初始化失败"

echo ""
echo "============================================================"
log "环境初始化完成 ✓"
log "下一步:"
log "  1. 修改 $PROJECT_ROOT/.env 中的密码（如有需要）"
log "  2. 执行: bash scripts/pull-base-image.sh"
log "  3. 执行: bash scripts/build.sh"
log "  4. 执行: bash scripts/deploy.sh up"
echo "============================================================"
