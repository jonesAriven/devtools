#!/bin/bash
# ============================================================
# mykng 知识库微服务构建脚本（SOP附录G要求）
# ============================================================
# 用法:
#   bash scripts/build.sh                    # Maven 编译 + Docker 构建所有服务
#   bash scripts/build.sh kb-intelligence    # 仅构建单个微服务镜像
#   bash scripts/build.sh --no-cache         # 不使用缓存构建
#   bash scripts/build.sh --skip-mvn         # 跳过 Maven 编译，仅 docker build
#   bash scripts/build.sh -help              # 显示帮助
# ============================================================

set -e

# ---------- 路径变量化 ----------
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
COMPOSE_PROJECT="kb-deploy"
COMPOSE_FILE="$PROJECT_ROOT/docker-compose.yml"
PARENT_DIR="$PROJECT_ROOT/kb-parent"

cd "$PROJECT_ROOT"

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

# ---------- 参数解析 ----------
NO_CACHE=""
SKIP_MVN=0
TARGET=""

for arg in "$@"; do
    case "$arg" in
        --no-cache)  NO_CACHE="--no-cache" ;;
        --skip-mvn)  SKIP_MVN=1 ;;
        -help|--help|-h)
            cat <<EOF
mykng 知识库微服务构建脚本

用法:
  bash scripts/build.sh [options] [service]

参数:
  --no-cache    不使用 Docker 缓存构建
  --skip-mvn    跳过 Maven 编译步骤，仅执行 docker build
  -help         显示此帮助

服务名:
  kb-gateway / kb-auth / kb-file / kb-knowledge / kb-ops / kb-intelligence
  不指定则构建全部

示例:
  bash scripts/build.sh
  bash scripts/build.sh kb-intelligence
  bash scripts/build.sh --no-cache kb-auth
EOF
            exit 0
            ;;
        *) TARGET="$arg" ;;
    esac
done

# 所有微服务（按依赖顺序）
ALL_SERVICES="kb-gateway kb-auth kb-file kb-knowledge kb-ops kb-intelligence"

# ---------- Maven 编译 ----------
maven_build() {
    if [ $SKIP_MVN -eq 1 ]; then
        warn "已跳过 Maven 编译（--skip-mvn）"
        return 0
    fi
    if [ ! -d "$PARENT_DIR" ]; then
        err "未找到 kb-parent 目录: $PARENT_DIR"
        exit 1
    fi
    if ! command -v mvn >/dev/null 2>&1; then
        err "未找到 mvn 命令，请安装 Maven 或使用 --skip-mvn"
        exit 1
    fi
    step "[Maven] 编译 kb-parent 全工程 (mvn clean package -DskipTests)..."
    cd "$PARENT_DIR"
    mvn clean package -DskipTests -q
    cd "$PROJECT_ROOT"
    log "Maven 编译完成"
}

# ---------- Docker 构建 ----------
docker_build_all() {
    step "[Docker] 构建所有微服务镜像..."
    docker compose -p "$COMPOSE_PROJECT" -f "$COMPOSE_FILE" build $NO_CACHE $ALL_SERVICES
}

docker_build_one() {
    local svc="$1"
    step "[Docker] 构建单个服务: $svc"
    docker compose -p "$COMPOSE_PROJECT" -f "$COMPOSE_FILE" build $NO_CACHE "$svc"
}

# ---------- 主流程 ----------
START_TS=$(date +%s)
maven_build

if [ -n "$TARGET" ]; then
    docker_build_one "$TARGET"
else
    docker_build_all
fi

END_TS=$(date +%s)
DURATION=$((END_TS - START_TS))

echo ""
log "构建完成 ✓ (耗时 ${DURATION}s)"
echo ""
log "镜像列表:"
docker images --format "table {{.Repository}}\t{{.Tag}}\t{{.Size}}\t{{.CreatedAt}}" | grep -E "REPOSITORY|kb-" || true
