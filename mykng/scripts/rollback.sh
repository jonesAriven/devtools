#!/bin/bash
# ============================================================
# mykng 知识库微服务回滚脚本（SOP附录G要求）
# ============================================================
# 用法:
#   bash scripts/rollback.sh <service>           # 回滚单个服务到上一版本镜像
#   bash scripts/rollback.sh <service> <tag>     # 回滚到指定镜像 tag
#   bash scripts/rollback.sh all                 # 回滚所有服务（含数据备份）
#   bash scripts/rollback.sh -help               # 显示帮助
#
# 镜像版本策略:
#   - 每次构建会生成 kb-<svc>:latest 与 kb-<svc>:<timestamp> 两个 tag
#   - 回滚单服务: 使用上一 timestamp tag 重新 up -d --no-deps
#   - 回滚所有: 先备份 5 个数据库，再逐个服务回滚到上一 tag
# ============================================================

set -e

# ---------- 路径变量化 ----------
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
COMPOSE_PROJECT="kb-deploy"
COMPOSE_FILE="$PROJECT_ROOT/docker-compose.yml"
BACKUP_ROOT="/data/backup/mysql"

cd "$PROJECT_ROOT"

# 从 .env 读取 MySQL 密码（兼容默认值）
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
step() { echo -e "${BLUE}[STEP ]${NC} $1"; }

# 服务到数据库的映射
declare -A SVC_DB_MAP=(
    [kb-auth]="kb_auth"
    [kb-file]="kb_file"
    [kb-knowledge]="kb_knowledge"
    [kb-intelligence]="kb_intelligence"
)

ALL_SERVICES="kb-gateway kb-auth kb-file kb-knowledge kb-intelligence"

# ---------- 帮助 ----------
show_help() {
    cat <<EOF
mykng 知识库微服务回滚脚本

用法:
  bash scripts/rollback.sh <service> [tag]
  bash scripts/rollback.sh all

参数:
  service   服务名: kb-gateway / kb-auth / kb-file / kb-knowledge / kb-intelligence / all
  tag       可选镜像 tag（默认使用上一 timestamp 版本）

示例:
  bash scripts/rollback.sh kb-intelligence
  bash scripts/rollback.sh kb-auth 20260628_103000
  bash scripts/rollback.sh all
EOF
}

# ---------- 备份数据库 ----------
backup_db() {
    local db_name="$1"
    local ts
    ts=$(date +%Y%m%d_%H%M%S)
    local backup_dir="$BACKUP_ROOT/${ts}_rollback"
    mkdir -p "$backup_dir"
    local backup_file="$backup_dir/${db_name}.sql"

    log "备份数据库 $db_name → $backup_file ..."
    if docker exec platform-mysql mysqldump -uroot -p"$MYSQL_PASS" --single-transaction "$db_name" > "$backup_file" 2>/dev/null; then
        local size
        size=$(du -h "$backup_file" | cut -f1)
        log "  ✓ 备份完成 ($size)"
    else
        err "  ✗ 备份失败: $db_name"
        return 1
    fi
}

# ---------- 列出镜像历史版本 ----------
list_image_tags() {
    local svc="$1"
    # 镜像名为 kb-deploy-<svc>（docker compose 默认前缀）
    local img_name="kb-deploy-${svc}"
    log "$svc 的镜像历史版本:"
    docker images --format "{{.Tag}}\t{{.CreatedAt}}\t{{.Size}}" "$img_name" | grep -v "<none>" | head -20
    echo ""
}

# ---------- 找到上一版本 tag ----------
get_previous_tag() {
    local svc="$1"
    local img_name="kb-deploy-${svc}"
    # 跳过 latest，取第二行（即上一 timestamp 版本）
    local prev_tag
    prev_tag=$(docker images --format "{{.Tag}}" "$img_name" 2>/dev/null | grep -v "<none>" | grep -v "^latest$" | sed -n '2p')
    echo "$prev_tag"
}

# ---------- 回滚单个服务 ----------
rollback_one() {
    local svc="$1"
    local tag="${2:-}"

    if [ -z "$tag" ]; then
        tag=$(get_previous_tag "$svc")
        if [ -z "$tag" ]; then
            warn "$svc 没有上一版本镜像，列出当前可用 tag:"
            list_image_tags "$svc"
            warn "可手动指定 tag: bash scripts/rollback.sh $svc <tag>"
            return 1
        fi
    fi

    step "回滚 $svc 到镜像 tag=$tag ..."

    # 备份对应数据库（gateway 无数据库）
    if [ -n "${SVC_DB_MAP[$svc]:-}" ]; then
        backup_db "${SVC_DB_MAP[$svc]}" || warn "数据库备份失败，继续回滚"
    fi

    # 重新拉起服务（--no-deps 不影响依赖）
    # 使用 COMPOSE_PROJECT 确保镜像前缀一致
    local img_name="kb-deploy-${svc}"
    log "切换镜像: $img_name:$tag"
    docker tag "$img_name:$tag" "$img_name:rollback-prev"
    docker compose -p "$COMPOSE_PROJECT" -f "$COMPOSE_FILE" up -d --no-deps "$svc"
    log "  ✓ $svc 已回滚到 $tag"
}

# ---------- 回滚所有服务 ----------
rollback_all() {
    warn "即将回滚所有服务到上一版本镜像"
    warn "将先备份全部 5 个数据库到 $BACKUP_ROOT"
    read -r -p "确认回滚？(yes/no): " confirm
    if [ "$confirm" != "yes" ]; then
        log "已取消"
        exit 0
    fi

    step "[1/2] 备份所有数据库..."
    for db in kb_auth kb_file kb_knowledge kb_ops kb_intelligence; do
        backup_db "$db" || err "备份 $db 失败"
    done

    step "[2/2] 逐个回滚服务..."
    for svc in $ALL_SERVICES; do
        rollback_one "$svc" || warn "$svc 回滚失败，继续下一个"
    done

    log "所有服务回滚完成 ✓"
}

# ---------- 入口 ----------
SERVICE="$1"
TAG="$2"

if [ -z "$SERVICE" ] || [ "$SERVICE" = "-help" ] || [ "$SERVICE" = "--help" ] || [ "$SERVICE" = "-h" ]; then
    show_help
    [ -z "$SERVICE" ] && exit 1
    exit 0
fi

case "$SERVICE" in
    all)
        rollback_all
        ;;
    kb-gateway|kb-auth|kb-file|kb-knowledge|kb-intelligence)
        rollback_one "$SERVICE" "$TAG"
        ;;
    *)
        err "未知服务: $SERVICE"
        show_help
        exit 1
        ;;
esac

echo ""
log "当前服务状态:"
docker compose -p "$COMPOSE_PROJECT" -f "$COMPOSE_FILE" ps
