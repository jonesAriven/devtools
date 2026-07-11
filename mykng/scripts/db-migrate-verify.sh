#!/bin/bash
# ============================================================
# mykng 知识库数据迁移校验脚本（SOP V1.1 要求）
# ============================================================
# 用法:
#   bash scripts/db-migrate-verify.sh                    # 执行 sql/*_verify.sql 校验
#   bash scripts/db-migrate-verify.sh --snapshot         # 生成迁移前数据快照
#   bash scripts/db-migrate-verify.sh --compare <dir>    # 与指定快照对比数据条数
#   bash scripts/db-migrate-verify.sh -help              # 显示帮助
#
# 工作流（迁移前后对比）:
#   1. 迁移前: bash scripts/db-migrate-verify.sh --snapshot
#      → 生成 /data/backup/migrate-snapshot/<ts>/counts.txt
#   2. 执行 ALTER TABLE / 数据迁移脚本
#   3. 迁移后: bash scripts/db-migrate-verify.sh --compare /data/backup/migrate-snapshot/<ts>
#   4. 单独执行校验 SQL: bash scripts/db-migrate-verify.sh
# ============================================================

set -e

# ---------- 路径变量化 ----------
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
SQL_DIR="$PROJECT_ROOT/sql"
SNAPSHOT_ROOT="/data/backup/migrate-snapshot"
REPORT_DIR="/data/logs/migrate-reports"

mkdir -p "$SNAPSHOT_ROOT" "$REPORT_DIR"

# 从 .env 读取密码
MYSQL_PASS="${MYSQL_ROOT_PASSWORD:-kb123456}"
if [ -f "$PROJECT_ROOT/.env" ]; then
    # shellcheck disable=SC1091
    . "$PROJECT_ROOT/.env"
    MYSQL_PASS="${MYSQL_ROOT_PASSWORD:-kb123456}"
fi

# 5 个数据库
MYSQL_DBS="kb_auth kb_file kb_knowledge kb_ops kb_intelligence"

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

# ---------- 帮助 ----------
show_help() {
    cat <<EOF
mykng 知识库数据迁移校验脚本（SOP V1.1 要求）

用法:
  bash scripts/db-migrate-verify.sh [options]

参数:
  (无)                       执行 sql/*_verify.sql 校验脚本
  --snapshot                 生成迁移前数据快照（按表行数）
  --compare <snapshot_dir>   与指定快照目录对比表行数
  -help                      显示此帮助

工作流:
  1. 迁移前: bash scripts/db-migrate-verify.sh --snapshot
  2. 执行迁移 SQL
  3. 迁移后: bash scripts/db-migrate-verify.sh --compare <生成的快照目录>
  4. 校验表结构: bash scripts/db-migrate-verify.sh

快照路径: $SNAPSHOT_ROOT/<ts>/counts.txt
报告路径: $REPORT_DIR/migrate-verify-<ts>.log
EOF
}

# ---------- 容器检查 ----------
check_mysql() {
    if ! docker ps --format '{{.Names}}' | grep -q '^platform-mysql$'; then
        err "platform-mysql 容器未运行"
        exit 1
    fi
}

# ---------- 执行 verify SQL ----------
run_verify_sql() {
    info "=== 执行 sql/*_verify.sql 校验脚本 ==="
    local report_file="$REPORT_DIR/migrate-verify-$(date +%Y%m%d_%H%M%S).log"
    local verify_count=0

    if [ ! -d "$SQL_DIR" ]; then
        err "未找到 sql 目录: $SQL_DIR"
        exit 1
    fi

    {
        echo "============================================================"
        echo "  mykng 数据迁移校验报告"
        echo "  时间: $(date '+%Y-%m-%d %H:%M:%S')"
        echo "  SQL 目录: $SQL_DIR"
        echo "============================================================"
        echo ""
    } | tee "$report_file"

    for sql in "$SQL_DIR"/*_verify.sql; do
        [ -f "$sql" ] || continue
        local name
        name=$(basename "$sql")
        info "执行: $name"
        echo ">>> $name" | tee -a "$report_file"
        if docker exec -i platform-mysql mysql -uroot -p"$MYSQL_PASS" --table < "$sql" 2>&1 | tee -a "$report_file"; then
            ok "$name 执行成功"
            verify_count=$((verify_count + 1))
        else
            bad "$name 执行失败"
        fi
        echo "" | tee -a "$report_file"
    done

    if [ $verify_count -eq 0 ]; then
        warn "未找到 *_verify.sql 脚本，仅执行数据条数对比"
    fi

    info "校验报告已保存: $report_file"
}

# ---------- 生成快照 ----------
take_snapshot() {
    local ts
    ts=$(date +%Y%m%d_%H%M%S)
    local snap_dir="$SNAPSHOT_ROOT/$ts"
    mkdir -p "$snap_dir"
    local counts_file="$snap_dir/counts.txt"

    info "=== 生成迁移前数据快照 ==="
    info "快照目录: $snap_dir"

    {
        echo "# mykng 数据迁移前快照"
        echo "# 时间: $(date '+%Y-%m-%d %H:%M:%S')"
        echo "# 格式: <db>.<table> <row_count>"
        echo ""
    } > "$counts_file"

    for db in $MYSQL_DBS; do
        info "  扫描 $db ..."
        # 获取该库所有表
        local tables
        tables=$(docker exec platform-mysql mysql -uroot -p"$MYSQL_PASS" -N -e "SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA='$db' AND TABLE_TYPE='BASE TABLE' ORDER BY TABLE_NAME;" 2>/dev/null)
        for tbl in $tables; do
            local cnt
            cnt=$(docker exec platform-mysql mysql -uroot -p"$MYSQL_PASS" -N -e "SELECT COUNT(*) FROM \`$db\`.\`$tbl\`;" 2>/dev/null || echo "ERROR")
            echo "${db}.${tbl} ${cnt}" >> "$counts_file"
            info "    ${db}.${tbl} = ${cnt}"
        done
    done

    # 同时备份数据库
    info "  同时备份数据库..."
    for db in $MYSQL_DBS; do
        docker exec platform-mysql mysqldump -uroot -p"$MYSQL_PASS" --single-transaction "$db" > "$snap_dir/${db}.sql" 2>/dev/null
    done

    ok "快照完成: $counts_file"
    echo ""
    info "请执行迁移后运行: bash scripts/db-migrate-verify.sh --compare $snap_dir"
}

# ---------- 对比快照 ----------
compare_snapshot() {
    local snap_dir="$1"
    local snap_counts="$snap_dir/counts.txt"

    info "=== 与快照对比数据条数 ==="
    info "快照目录: $snap_dir"

    if [ ! -f "$snap_counts" ]; then
        err "快照文件不存在: $snap_counts"
        exit 1
    fi

    local diff_count=0
    local same_count=0

    while read -r line; do
        # 跳过注释和空行
        [[ "$line" =~ ^#.* ]] && continue
        [ -z "$line" ] && continue

        local key expected
        key=$(echo "$line" | awk '{print $1}')
        expected=$(echo "$line" | awk '{print $2}')

        local db tbl
        db="${key%%.*}"
        tbl="${key#*.}"

        local actual
        actual=$(docker exec platform-mysql mysql -uroot -p"$MYSQL_PASS" -N -e "SELECT COUNT(*) FROM \`$db\`.\`$tbl\`;" 2>/dev/null || echo "ERROR")

        if [ "$actual" = "$expected" ]; then
            ok "$key: $actual (一致)"
            same_count=$((same_count + 1))
        else
            bad "$key: 期望=$expected 实际=$actual (不一致)"
            diff_count=$((diff_count + 1))
        fi
    done < "$snap_counts"

    echo ""
    info "对比完成: 一致 $same_count / 不一致 $diff_count"
    if [ $diff_count -gt 0 ]; then
        err "存在数据条数不一致项，请人工核对（迁移可能涉及数据修改，需具体分析）"
        exit 1
    fi
}

# ---------- 主流程 ----------
case "${1:-}" in
    -help|--help|-h) show_help; exit 0 ;;
    --snapshot)      check_mysql; take_snapshot; exit 0 ;;
    --compare)
        check_mysql
        if [ -z "$2" ]; then
            err "请指定快照目录: --compare <snapshot_dir>"
            exit 1
        fi
        compare_snapshot "$2"
        exit $?
        ;;
    "")
        check_mysql
        run_verify_sql
        echo ""
        echo "============================================================"
        echo -e "  校验结果: ${GREEN}通过 $PASS${NC} / ${RED}失败 $FAIL${NC}"
        echo "============================================================"
        exit $FAIL
        ;;
    *)
        err "未知参数: $1"
        show_help
        exit 1
        ;;
esac
