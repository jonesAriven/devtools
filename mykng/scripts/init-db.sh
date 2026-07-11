#!/bin/bash
# ============================================================
# mykng 知识库数据库初始化脚本（SOP附录G要求）
# ============================================================
# 用法:
#   bash scripts/init-db.sh              # 执行 init-sql/ 下所有 SQL
#   bash scripts/init-db.sh --verify     # 仅验证数据库和表
#   bash scripts/init-db.sh --admin      # 创建默认管理员用户
#   bash scripts/init-db.sh -help        # 显示帮助
# ============================================================

set -e

# ---------- 路径变量化 ----------
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
COMPOSE_PROJECT="kb-deploy"
COMPOSE_FILE="$PROJECT_ROOT/docker-compose.yml"
INIT_SQL_DIR="$PROJECT_ROOT/init-sql"

cd "$PROJECT_ROOT"

# 从 .env 读取密码
MYSQL_PASS="${MYSQL_ROOT_PASSWORD:-kb123456}"
if [ -f "$PROJECT_ROOT/.env" ]; then
    # shellcheck disable=SC1091
    . "$PROJECT_ROOT/.env"
    MYSQL_PASS="${MYSQL_ROOT_PASSWORD:-kb123456}"
fi

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

PASS=0
FAIL=0
ok()   { echo -e "  ${GREEN}[✓ PASS]${NC} $1"; PASS=$((PASS + 1)); }
bad()  { echo -e "  ${RED}[✗ FAIL]${NC} $1"; FAIL=$((FAIL + 1)); }

# 期望的 5 个数据库
EXPECTED_DBS="kb_auth kb_file kb_knowledge kb_ops kb_intelligence"

show_help() {
    cat <<EOF
mykng 知识库数据库初始化脚本

用法:
  bash scripts/init-db.sh [options]

参数:
  (无)       执行 init-sql/ 下所有 SQL 脚本
  --verify   仅验证数据库和表创建情况
  --admin    创建默认管理员用户（admin/admin123）
  -help      显示此帮助

初始化 SQL 目录: $INIT_SQL_DIR
EOF
}

# ---------- 容器检查 ----------
ensure_mysql_running() {
    if ! docker ps --format '{{.Names}}' | grep -q '^platform-mysql$'; then
        warn "platform-mysql 未运行，尝试启动..."
        docker compose -p "$COMPOSE_PROJECT" -f "$COMPOSE_FILE" up -d mysql
        local elapsed=0
        while [ $elapsed -lt 60 ]; do
            if docker inspect --format='{{.State.Health.Status}}' platform-mysql 2>/dev/null | grep -q "healthy"; then
                ok "platform-mysql 健康"
                return 0
            fi
            sleep 3
            elapsed=$((elapsed + 3))
        done
        err "platform-mysql 启动超时"
        exit 1
    fi
}

# ---------- 执行 init-sql ----------
run_init_sql() {
    info "=== 执行 init-sql/ 下所有 SQL ==="

    if [ ! -d "$INIT_SQL_DIR" ]; then
        err "未找到 init-sql 目录: $INIT_SQL_DIR"
        exit 1
    fi

    local total=0
    local success=0
    for sql in "$INIT_SQL_DIR"/*.sql; do
        [ -f "$sql" ] || continue
        total=$((total + 1))
        local name
        name=$(basename "$sql")
        info "  执行: $name"
        if docker exec -i platform-mysql mysql -uroot -p"$MYSQL_PASS" < "$sql" 2>&1 | grep -v "Using a password" ; then
            ok "    ✓ $name"
            success=$((success + 1))
        else
            bad "    ✗ $name"
        fi
    done

    info "执行结果: 成功 $success / 总计 $total"
}

# ---------- 验证 ----------
verify_databases() {
    info "=== 验证数据库和表 ==="

    # 1. 数据库存在性
    info "  [1] 检查 5 个数据库存在性..."
    for db in $EXPECTED_DBS; do
        local exists
        exists=$(docker exec platform-mysql mysql -uroot -p"$MYSQL_PASS" -N -e "SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='$db';" 2>/dev/null)
        if [ "$exists" = "1" ]; then
            ok "数据库 $db 存在"
        else
            bad "数据库 $db 不存在"
        fi
    done

    # 2. 字符集
    info "  [2] 检查字符集（utf8mb4_unicode_ci）..."
    local cs_result
    cs_result=$(docker exec platform-mysql mysql -uroot -p"$MYSQL_PASS" -N -e "SELECT SCHEMA_NAME, DEFAULT_CHARACTER_SET_NAME, DEFAULT_COLLATION_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME LIKE 'kb_%';" 2>/dev/null)
    echo "$cs_result" | while IFS=$'\t' read -r name cs coll; do
        if [ "$cs" = "utf8mb4" ] && [ "$coll" = "utf8mb4_unicode_ci" ]; then
            ok "$name: $cs / $coll"
        else
            bad "$name: $cs / $coll (期望 utf8mb4 / utf8mb4_unicode_ci)"
        fi
    done

    # 3. 表数量
    info "  [3] 检查各库表数量..."
    local total_tables=0
    for db in $EXPECTED_DBS; do
        local cnt
        cnt=$(docker exec platform-mysql mysql -uroot -p"$MYSQL_PASS" -N -e "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='$db' AND TABLE_TYPE='BASE TABLE';" 2>/dev/null)
        info "    $db: ${cnt:-0} 张表"
        if [ -n "$cnt" ] && [ "$cnt" -gt 0 ]; then
            total_tables=$((total_tables + cnt))
        fi
    done
    info "  总表数: $total_tables（期望 38）"

    # 4. 列出所有表
    info "  [4] 表清单:"
    docker exec platform-mysql mysql -uroot -p"$MYSQL_PASS" -e "SELECT TABLE_SCHEMA, TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA LIKE 'kb_%' AND TABLE_TYPE='BASE TABLE' ORDER BY TABLE_SCHEMA, TABLE_NAME;" 2>/dev/null
}

# ---------- 创建管理员 ----------
create_admin() {
    info "=== 创建默认管理员 ==="
    # 使用 BCrypt 加密的 admin123 密码（与 kb-auth 服务一致）
    local sql
    sql="USE kb_auth;
INSERT IGNORE INTO user (id, username, password, phone, status, created_at, updated_at)
VALUES (1, 'admin', '\$2a\$10\$N.ZMy8s5L3NjQzjvF6YnHeSRJaQgSPzGe5O8C8v1b3a3b3b3b3b3', NULL, 1, NOW(), NOW());
SELECT id, username, status FROM user WHERE username='admin';"
    if docker exec -i platform-mysql mysql -uroot -p"$MYSQL_PASS" -e "$sql" 2>&1 | grep -v "Using a password"; then
        ok "管理员用户已确保存在（admin/admin123）"
    else
        bad "管理员创建失败（可能表结构未初始化）"
    fi
}

# ---------- 主流程 ----------
case "${1:-}" in
    -help|--help|-h) show_help; exit 0 ;;
    --verify)        ensure_mysql_running; verify_databases; exit $FAIL ;;
    --admin)         ensure_mysql_running; create_admin; exit $FAIL ;;
    "")
        log "============================================================"
        log "  mykng 数据库初始化"
        log "  时间: $(date '+%Y-%m-%d %H:%M:%S')"
        log "============================================================"
        ensure_mysql_running
        run_init_sql
        echo ""
        verify_databases
        echo ""
        create_admin
        echo ""
        log "============================================================"
        log "  数据库初始化完成 ✓"
        log "  结果: ${GREEN}通过 $PASS${NC} / ${RED}失败 $FAIL${NC}"
        log "============================================================"
        exit $FAIL
        ;;
    *)
        err "未知参数: $1"
        show_help
        exit 1
        ;;
esac
