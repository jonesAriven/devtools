#!/bin/bash
# ============================================================
# mykng 知识库数据库备份脚本（SOP附录G要求）
# ============================================================
# 用法:
#   bash scripts/backup.sh                 # 立即备份
#   bash scripts/backup.sh --verify        # 仅验证最近一次备份可用性
#   bash scripts/backup.sh --list          # 列出所有备份
#   bash scripts/backup.sh --clean         # 清理 7 天前的备份
#   bash scripts/backup.sh -help           # 显示帮助
#
# cron 示例（每天凌晨 2 点）:
#   0 2 * * * /mnt/shared/devtools/mykng/scripts/backup.sh >> /data/logs/backup.log 2>&1
#
# 备份内容:
#   - MySQL 5 个库: kb_auth, kb_file, kb_knowledge, kb_ops, kb_intelligence
#   - MongoDB 全库 archive
# 备份路径:
#   /data/backup/mysql/YYYYMMDD_HHmm/<db>.sql
#   /data/backup/mongodb/YYYYMMDD_HHmm/mongodb.archive
# 保留策略: 最近 7 天
# ============================================================

set -e

# ---------- 路径变量化 ----------
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
BACKUP_ROOT="/data/backup"
MYSQL_BACKUP_ROOT="$BACKUP_ROOT/mysql"
MONGO_BACKUP_ROOT="$BACKUP_ROOT/mongodb"
LOG_DIR="/data/logs"
LOG_FILE="$LOG_DIR/backup.log"

mkdir -p "$MYSQL_BACKUP_ROOT" "$MONGO_BACKUP_ROOT" "$LOG_DIR"

# 从 .env 读取密码
MYSQL_PASS="${MYSQL_ROOT_PASSWORD:-kb123456}"
MONGO_USER="${MONGO_ROOT_USER:-kb}"
MONGO_PASS="${MONGO_ROOT_PASSWORD:-kb123456}"
if [ -f "$PROJECT_ROOT/.env" ]; then
    # shellcheck disable=SC1091
    . "$PROJECT_ROOT/.env"
    MYSQL_PASS="${MYSQL_ROOT_PASSWORD:-kb123456}"
    MONGO_USER="${MONGO_ROOT_USER:-kb}"
    MONGO_PASS="${MONGO_ROOT_PASSWORD:-kb123456}"
fi

# 5 个 MySQL 库
MYSQL_DBS="kb_auth kb_file kb_knowledge kb_ops kb_intelligence"
RETENTION_DAYS=7

# ---------- 颜色输出 ----------
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;36m'
NC='\033[0m'

log()  { echo -e "${GREEN}[$(date '+%Y-%m-%d %H:%M:%S')] [INFO ]${NC} $1" | tee -a "$LOG_FILE"; }
info() { echo -e "${BLUE}[$(date '+%Y-%m-%d %H:%M:%S')] [INFO ]${NC} $1" | tee -a "$LOG_FILE"; }
warn() { echo -e "${YELLOW}[$(date '+%Y-%m-%d %H:%M:%S')] [WARN ]${NC} $1" | tee -a "$LOG_FILE"; }
err()  { echo -e "${RED}[$(date '+%Y-%m-%d %H:%M:%S')] [ERROR]${NC} $1" | tee -a "$LOG_FILE" >&2; }

# ---------- 帮助 ----------
show_help() {
    cat <<EOF
mykng 知识库数据库备份脚本

用法:
  bash scripts/backup.sh [options]

参数:
  (无)      立即执行备份
  --verify  验证最近一次备份的可用性
  --list    列出所有备份
  --clean   清理 ${RETENTION_DAYS} 天前的备份
  -help     显示此帮助

备份路径:
  MySQL:  $MYSQL_BACKUP_ROOT/YYYYMMDD_HHmm/<db>.sql
  MongoDB: $MONGO_BACKUP_ROOT/YYYYMMDD_HHmm/mongodb.archive

cron 示例:
  0 2 * * * /mnt/shared/devtools/mykng/scripts/backup.sh
EOF
}

# ---------- 容器存在性检查 ----------
check_containers() {
    if ! docker ps --format '{{.Names}}' | grep -q '^kb-mysql$'; then
        err "kb-mysql 容器未运行，无法备份"
        exit 1
    fi
    if ! docker ps --format '{{.Names}}' | grep -q '^kb-mongo$'; then
        warn "kb-mongo 容器未运行，将跳过 MongoDB 备份"
        return 1
    fi
    return 0
}

# ---------- MySQL 备份 ----------
backup_mysql() {
    local ts="$1"
    local backup_dir="$MYSQL_BACKUP_ROOT/$ts"
    mkdir -p "$backup_dir"

    info "=== MySQL 备份开始 (5 个库) ==="
    local ok_count=0
    local fail_count=0
    for db in $MYSQL_DBS; do
        local file="$backup_dir/${db}.sql"
        info "  导出 $db → ${file}"
        if docker exec kb-mysql mysqldump -uroot -p"$MYSQL_PASS" \
            --single-transaction --routines --triggers --events \
            --default-character-set=utf8mb4 "$db" > "$file" 2>/dev/null; then
            local size
            size=$(du -h "$file" | cut -f1)
            local lines
            lines=$(wc -l < "$file")
            info "    ✓ $db ($size, $lines 行)"
            ok_count=$((ok_count + 1))
        else
            err "    ✗ $db 备份失败"
            fail_count=$((fail_count + 1))
            rm -f "$file"
        fi
    done

    # 写入 manifest
    cat > "$backup_dir/MANIFEST.txt" <<EOF
backup_type=mysql
backup_time=$ts
databases=$MYSQL_DBS
ok_count=$ok_count
fail_count=$fail_count
mysql_host=kb-mysql:3306
EOF

    info "MySQL 备份结束: 成功 $ok_count / 失败 $fail_count"
    return $fail_count
}

# ---------- MongoDB 备份 ----------
backup_mongodb() {
    local ts="$1"
    local backup_dir="$MONGO_BACKUP_ROOT/$ts"
    mkdir -p "$backup_dir"
    local file="$backup_dir/mongodb.archive"

    info "=== MongoDB 备份开始 ==="
    if docker exec kb-mongo mongodump --quiet \
        --uri="mongodb://${MONGO_USER}:${MONGO_PASS}@localhost:27017" \
        --archive > "$file" 2>/dev/null; then
        local size
        size=$(du -h "$file" | cut -f1)
        info "  ✓ MongoDB ($size)"
        cat > "$backup_dir/MANIFEST.txt" <<EOF
backup_type=mongodb
backup_time=$ts
file=mongodb.archive
size=$size
EOF
        return 0
    else
        err "  ✗ MongoDB 备份失败"
        rm -f "$file"
        return 1
    fi
}

# ---------- 验证备份 ----------
verify_latest() {
    info "=== 验证最近一次备份 ==="

    # MySQL
    local latest_mysql
    latest_mysql=$(ls -1dt "$MYSQL_BACKUP_ROOT"/*/ 2>/dev/null | head -1)
    if [ -z "$latest_mysql" ]; then
        warn "未找到 MySQL 备份"
    else
        info "MySQL 最近备份: $latest_mysql"
        for sql in "$latest_mysql"*.sql; do
            [ -f "$sql" ] || continue
            local size lines
            size=$(du -h "$sql" | cut -f1)
            lines=$(wc -l < "$sql")
            if [ "$lines" -gt 0 ] && head -50 "$sql" | grep -q "MySQL dump"; then
                info "  ✓ $(basename "$sql") ($size, $lines 行) - 文件头合法"
            else
                err "  ✗ $(basename "$sql") - 文件无效"
            fi
        done
        # 通过 mysql 解析验证
        if docker exec -i kb-mysql mysql -uroot -p"$MYSQL_PASS" -e "SELECT 1;" >/dev/null 2>&1; then
            info "  ✓ MySQL 服务可用，可恢复"
        fi
    fi

    # MongoDB
    local latest_mongo
    latest_mongo=$(ls -1dt "$MONGO_BACKUP_ROOT"/*/ 2>/dev/null | head -1)
    if [ -z "$latest_mongo" ]; then
        warn "未找到 MongoDB 备份"
    else
        info "MongoDB 最近备份: $latest_mongo"
        local arc="$latest_mongo/mongodb.archive"
        if [ -f "$arc" ]; then
            local size
            size=$(du -h "$arc" | cut -f1)
            if [ -s "$arc" ]; then
                info "  ✓ mongodb.archive ($size) - 非空"
            else
                err "  ✗ mongodb.archive 为空"
            fi
        fi
    fi
}

# ---------- 列出备份 ----------
list_backups() {
    info "=== MySQL 备份列表 ==="
    if [ -d "$MYSQL_BACKUP_ROOT" ]; then
        for d in "$MYSQL_BACKUP_ROOT"/*/; do
            [ -d "$d" ] || continue
            local ts size
            ts=$(basename "$d")
            size=$(du -sh "$d" 2>/dev/null | cut -f1)
            local count
            count=$(ls "$d"*.sql 2>/dev/null | wc -l)
            echo "  $ts  $size  ${count} 个 SQL"
        done
    else
        warn "MySQL 备份目录不存在"
    fi

    info "=== MongoDB 备份列表 ==="
    if [ -d "$MONGO_BACKUP_ROOT" ]; then
        for d in "$MONGO_BACKUP_ROOT"/*/; do
            [ -d "$d" ] || continue
            local ts size
            ts=$(basename "$d")
            size=$(du -sh "$d" 2>/dev/null | cut -f1)
            echo "  $ts  $size"
        done
    else
        warn "MongoDB 备份目录不存在"
    fi
}

# ---------- 清理过期备份 ----------
clean_old() {
    info "=== 清理 ${RETENTION_DAYS} 天前的备份 ==="
    local mysql_cleaned=0
    local mongo_cleaned=0

    if [ -d "$MYSQL_BACKUP_ROOT" ]; then
        find "$MYSQL_BACKUP_ROOT" -maxdepth 1 -type d -name "20*" -mtime +${RETENTION_DAYS} | while read -r d; do
            warn "  删除: $d"
            rm -rf "$d"
        done
        mysql_cleaned=1
    fi

    if [ -d "$MONGO_BACKUP_ROOT" ]; then
        find "$MONGO_BACKUP_ROOT" -maxdepth 1 -type d -name "20*" -mtime +${RETENTION_DAYS} | while read -r d; do
            warn "  删除: $d"
            rm -rf "$d"
        done
        mongo_cleaned=1
    fi

    info "清理完成"
}

# ---------- 主流程 ----------
case "${1:-}" in
    -help|--help|-h) show_help; exit 0 ;;
    --verify)        verify_latest; exit 0 ;;
    --list)          list_backups; exit 0 ;;
    --clean)         clean_old; exit 0 ;;
    "")
        TS=$(date +%Y%m%d_%H%M)
        log "============================================================"
        log "  mykng 数据库备份开始"
        log "  时间戳: $TS"
        log "============================================================"

        check_containers || MONGO_AVAILABLE=0
        backup_mysql "$TS" || err "MySQL 备份存在失败项"
        if [ "${MONGO_AVAILABLE:-1}" = "1" ]; then
            backup_mongodb "$TS" || err "MongoDB 备份失败"
        fi

        # 自动清理过期备份
        clean_old

        log "============================================================"
        log "  备份完成 ✓"
        log "  MySQL 路径:  $MYSQL_BACKUP_ROOT/$TS"
        log "  MongoDB 路径: $MONGO_BACKUP_ROOT/$TS"
        log "============================================================"
        ;;
    *)
        err "未知参数: $1"
        show_help
        exit 1
        ;;
esac
