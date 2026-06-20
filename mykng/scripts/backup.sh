#!/bin/bash
# ============================================================
# mykng 知识库数据备份脚本
# ============================================================
# 用法: 手动执行或加入 crontab
# cron: 0 2 * * * /root/devtools/mykng/scripts/backup.sh >> /var/log/kb-backup.log 2>&1
# ============================================================

set -e

BACKUP_DIR="/mnt/0000sharebak/kb-backup"
DATE=$(date +%Y%m%d_%H%M%S)
MYSQL_PASS="kb123456"
MONGO_USER="kb"
MONGO_PASS="kb123456"

# 创建目录
mkdir -p "$BACKUP_DIR/mysql" "$BACKUP_DIR/mongodb"

echo "[$(date '+%Y-%m-%d %H:%M:%S')] 开始备份..."

# === MySQL 备份（按 schema 分别导出）===
for db in kb_auth kb_file kb_knowledge kb_ops; do
  echo "  导出 $db..."
  docker exec kb-mysql mysqldump -uroot -p"$MYSQL_PASS" --single-transaction "$db" \
    > "$BACKUP_DIR/mysql/${db}_${DATE}.sql" 2>/dev/null
done

# === MongoDB 备份 ===
echo "  导出 MongoDB..."
docker exec kb-mongodb mongodump --uri="mongodb://${MONGO_USER}:${MONGO_PASS}@localhost:27017" \
  --archive > "$BACKUP_DIR/mongodb/mongodb_${DATE}.archive" 2>/dev/null

# === 清理过期备份 ===
echo "  清理 7 天前的备份..."
find "$BACKUP_DIR/mysql" -name "*.sql" -mtime +7 -delete 2>/dev/null || true
find "$BACKUP_DIR/mongodb" -name "*.archive" -mtime +7 -delete 2>/dev/null || true

# 统计
MYSQL_COUNT=$(ls "$BACKUP_DIR/mysql/"*.sql 2>/dev/null | wc -l)
MONGO_COUNT=$(ls "$BACKUP_DIR/mongodb/"*.archive 2>/dev/null | wc -l)
TOTAL_SIZE=$(du -sh "$BACKUP_DIR" | cut -f1)

echo "[$(date '+%Y-%m-%d %H:%M:%S')] 备份完成 — MySQL: ${MYSQL_COUNT}份, MongoDB: ${MONGO_COUNT}份, 总占用: ${TOTAL_SIZE}"
