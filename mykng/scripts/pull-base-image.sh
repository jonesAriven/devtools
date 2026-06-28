#!/bin/bash
# ============================================================
# mykng 知识库微服务基础镜像拉取脚本（SOP附录G要求）
# ============================================================
# 用法:
#   bash scripts/pull-base-image.sh              # 拉取所有基础镜像
#   bash scripts/pull-base-image.sh --no-mirror  # 不使用镜像加速器
#   bash scripts/pull-base-image.sh --list       # 仅列出基础镜像
#   bash scripts/pull-base-image.sh -help        # 显示帮助
#
# 基础镜像清单:
#   - eclipse-temurin:21-jre-alpine   (微服务运行时)
#   - mysql:8.0                        (MySQL 数据库)
#   - redis:7-alpine                   (Redis 缓存)
#   - minio/minio:latest               (MinIO 对象存储)
#   - getmeili/meilisearch:v1.12       (MeiliSearch 搜索引擎)
#   - mongo:7.0                        (MongoDB 文档数据库)
# ============================================================

set -e

# ---------- 路径变量化 ----------
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

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

# 基础镜像清单
IMAGES=(
    "eclipse-temurin:21-jre-alpine"
    "mysql:8.0"
    "redis:7-alpine"
    "minio/minio:latest"
    "getmeili/meilisearch:v1.12"
    "mongo:7.0"
)

# 私服镜像加速器
MIRROR_CONF="/etc/docker/daemon.json"
MIRROR_URL="https://nexus.marschat.online/repository/docker-public"

show_help() {
    cat <<EOF
mykng 知识库微服务基础镜像拉取脚本

用法:
  bash scripts/pull-base-image.sh [options]

参数:
  (无)          拉取所有基础镜像（自动配置加速器）
  --no-mirror   不使用镜像加速器，直接拉取
  --list        仅列出需要拉取的镜像清单
  --configure   仅配置镜像加速器，不拉取
  -help         显示此帮助

镜像清单:
$(printf '  - %s\n' "${IMAGES[@]}")
EOF
}

# ---------- 配置加速器 ----------
configure_mirror() {
    info "=== 配置 Docker 镜像加速器 ==="
    if [ ! -d /etc/docker ]; then
        mkdir -p /etc/docker
    fi

    if [ -f "$MIRROR_CONF" ] && grep -q "$MIRROR_URL" "$MIRROR_CONF"; then
        log "  镜像加速器已配置: $MIRROR_URL"
        return 0
    fi

    warn "  写入 $MIRROR_CONF ..."
    cat > "$MIRROR_CONF" <<EOF
{
  "registry-mirrors": [
    "$MIRROR_URL"
  ],
  "max-concurrent-downloads": 1,
  "max-download-attempts": 10
}
EOF

    warn "  重启 Docker 服务..."
    if systemctl restart docker; then
        sleep 3
        log "  Docker 已重启"
    else
        err "  Docker 重启失败，请手动执行: systemctl restart docker"
        return 1
    fi

    log "  当前镜像加速器:"
    docker info 2>/dev/null | grep -A3 "Registry Mirrors" || true
}

# ---------- 拉取单个镜像（带重试） ----------
pull_image() {
    local image="$1"
    local max_retry=3
    info "  拉取: $image"
    for i in $(seq 1 $max_retry); do
        if docker pull "$image" 2>&1; then
            log "    ✓ 第 $i 次尝试成功"
            return 0
        else
            warn "    第 $i 次尝试失败，5s 后重试..."
            sleep 5
        fi
    done
    err "    ✗ 拉取失败（已重试 $max_retry 次）: $image"
    return 1
}

# ---------- 列出镜像 ----------
list_images() {
    info "=== 基础镜像清单 ==="
    for img in "${IMAGES[@]}"; do
        local exists
        if docker image inspect "$img" >/dev/null 2>&1; then
            local size
            size=$(docker images --format "{{.Size}}" "$img" | head -1)
            echo -e "  ${GREEN}[✓ 已存在]${NC} $img ($size)"
        else
            echo -e "  ${YELLOW}[! 未拉取]${NC} $img"
        fi
    done
}

# ---------- 主流程 ----------
USE_MIRROR=true
ONLY_LIST=false
ONLY_CONFIGURE=false

for arg in "$@"; do
    case "$arg" in
        -help|--help|-h) show_help; exit 0 ;;
        --no-mirror)     USE_MIRROR=false ;;
        --list)          ONLY_LIST=true ;;
        --configure)     ONLY_CONFIGURE=true ;;
        *) err "未知参数: $arg"; show_help; exit 1 ;;
    esac
done

echo "============================================================"
echo "  mykng 基础镜像拉取"
echo "  时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo "============================================================"
echo ""

# 列出
if [ "$ONLY_LIST" = "true" ]; then
    list_images
    exit 0
fi

# 配置加速器
if [ "$USE_MIRROR" = "true" ]; then
    configure_mirror || warn "加速器配置失败，将直接拉取"
fi

if [ "$ONLY_CONFIGURE" = "true" ]; then
    log "仅配置模式，跳过拉取"
    exit 0
fi

# 检查 Docker
if ! docker info >/dev/null 2>&1; then
    err "Docker daemon 未运行"
    exit 1
fi

# 拉取
info "=== 拉取基础镜像 ==="
local_pass=0
local_fail=0
for img in "${IMAGES[@]}"; do
    if pull_image "$img"; then
        local_pass=$((local_pass + 1))
    else
        local_fail=$((local_fail + 1))
    fi
done

echo ""
info "=== 拉取结果 ==="
log "  成功: $local_pass / ${#IMAGES[@]}"
[ $local_fail -gt 0 ] && err "  失败: $local_fail"

echo ""
info "=== 当前镜像列表 ==="
list_images

echo ""
log "基础镜像拉取完成 ✓"
[ $local_fail -gt 0 ] && exit 1
exit 0
