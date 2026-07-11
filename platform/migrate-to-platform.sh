#!/bin/bash
# ============================================================
# migrate-to-platform.sh — 基础设施从 kb-* 迁移到 platform-*
# ============================================================
# ⚠️  重要: 在首次启动 start-platform.sh 之前，必须先执行此脚本！
#
# 执行流程:
#   1. 停止所有旧 kb-* 基础设施容器
#   2. 迁移数据卷 (kb-*-data → platform-*-data)
#   3. 删除旧容器（避免孤儿）
#   4. 清理旧网络 (kb-infra-net / kb-net)
#   5. 验证迁移结果
#
# 用法: bash migrate-to-platform.sh
#       bash migrate-to-platform.sh --dry-run   # 仅预览不执行
# ============================================================
set -euo pipefail

# ====== 颜色 ======
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

log_ok()   { echo -e "  ${GREEN}✅${NC} $1"; }
log_warn() { echo -e "  ${YELLOW}⚠️${NC} $1"; }
log_err()  { echo -e "  ${RED}❌${NC} $1"; }
log_info() { echo -e "  ${BLUE}ℹ️${NC} $1"; }
log_step() { echo -e "\n${CYAN}>>> [$1/$2] $3 <<<${NC}"; }

DRY_RUN=false
if [ "${1:-}" = "--dry-run" ]; then
  DRY_RUN=true
  log_warn "🔍 DRY-RUN 模式：仅预览，不会执行任何操作"
fi

# ====== 映射表 ======
# 旧容器名 -> 新容器名
declare -A CONTAINER_MAP=(
  ["kb-mysql"]="platform-mysql"
  ["kb-redis"]="platform-redis"
  ["kb-mongo"]="platform-mongo"
  ["kb-minio"]="platform-minio"
  ["kb-meilisearch"]="platform-meilisearch"
  ["kb-nacos"]="platform-nacos"
)

# 旧卷名 -> 新卷名
declare -A VOLUME_MAP=(
  ["kb-mysql-data"]="platform-mysql-data"
  ["kb-redis-data"]="platform-redis-data"
  ["kb-mongo-data"]="platform-mongo-data"
  ["kb-minio-data"]="platform-minio-data"
  ["kb-meili-data"]="platform-meili-data"
  ["kb-nacos-data"]="platform-nacos-data"
  ["kb-nacos-logs"]="platform-nacos-logs"
)

echo ""
echo "============================================================="
echo "  🔄 基础设施迁移: kb-* → platform-*"
echo "  时间: $(date '+%Y-%m-%d %H:%M:%S')"
if $DRY_RUN; then
  echo "  模式: DRY-RUN (预览)"
else
  echo "  模式: 实际执行"
fi
echo "============================================================="

# ====== Step 0: 前置检查 ======
log_step 0 5 "前置检查"

# 检查新容器是否已经在运行
new_running=0
for new_name in "${CONTAINER_MAP[@]}"; do
  if docker ps --format '{{.Names}}' | grep -q "^${new_name}$"; then
    log_warn "新容器已在运行: ${new_name}（可能已迁移过）"
    new_running=$((new_running + 1))
  fi
done
if [ $new_running -gt 0 ]; then
  log_info "已有 $new_running 个新容器在运行，将跳过这些"
fi

# 检查旧容器是否存在
old_exists=0
for old_name in "${!CONTAINER_MAP[@]}"; do
  if docker ps -a --format '{{.Names}}' | grep -q "^${old_name}$"; then
    old_exists=$((old_exists + 1))
  fi
done

if [ $old_exists -eq 0 ]; then
  log_ok "没有发现旧 kb-* 容器，无需迁移"
  # 但还要检查旧卷是否需要迁移
  old_vols=0
  for old_vol in "${!VOLUME_MAP[@]}"; do
    if docker volume ls --format '{{.Name}}' | grep -q "^${old_vol}$"; then
      old_vols=$((old_vols + 1))
    fi
  done
  if [ $old_vols -eq 0 ]; then
    log_ok "也没有旧卷，完全无需迁移"
    exit 0
  else
    log_warn "没有旧容器，但有 $old_vols 个旧卷需要迁移"
  fi
else
  log_info "发现 $old_exists 个旧容器需要处理"
fi

# ====== Step 1: 停止旧容器 ======
log_step 1 5 "停止旧容器"

stopped=0
for old_name in "${!CONTAINER_MAP[@]}"; do
  new_name="${CONTAINER_MAP[$old_name]}"
  
  # 新容器已在运行则跳过
  if docker ps --format '{{.Names}}' | grep -q "^${new_name}$"; then
    log_info "跳过 ${old_name}（新容器 ${new_name} 已在运行）"
    continue
  fi
  
  if docker ps --format '{{.Names}}' | grep -q "^${old_name}$"; then
    log_info "停止: ${old_name}"
    if ! $DRY_RUN; then
      docker stop "$old_name" 2>/dev/null || true
    fi
    stopped=$((stopped + 1))
    log_ok "已停止: ${old_name}"
  else
    log_info "${old_name}: 未运行（可能已停止或不存在）"
  fi
done
log_ok "停止完成: ${stopped} 个容器"

# ====== Step 2: 迁移数据卷 ======
log_step 2 5 "迁移数据卷"

migrated=0
skipped=0
for old_vol in "${!VOLUME_MAP[@]}"; do
  new_vol="${VOLUME_MAP[$old_vol]}"
  
  # 检查旧卷是否存在
  if ! docker volume ls --format '{{.Name}}' | grep -q "^${old_vol}$"; then
    log_info "${old_vol}: 不存在，跳过"
    skipped=$((skipped + 1))
    continue
  fi
  
  # 检查新卷是否已存在
  if docker volume ls --format '{{.Name}}' | grep -q "^${new_vol}$"; then
    log_warn "${new_vol}: 已存在！跳过迁移（避免覆盖已有数据）"
    log_info "如需使用旧卷数据，请手动: docker rm ${new_vol} 后重新执行"
    skipped=$((skipped + 1))
    continue
  fi
  
  log_info "迁移卷: ${old_vol} → ${new_vol}"
  
  if ! $DRY_RUN; then
    # 创建新卷
    docker volume create "$new_vol" >/dev/null 2>&1
    
    # 用临时容器复制数据
    case "$old_vol" in
      *mysql-data|*mongo-data)
        # 数据库卷：直接 cp 文件
        docker run --rm \
          -v "${old_vol}:/src" \
          -v "${new_vol}:/dst" \
          alpine sh -c "cp -a /src/. /dst/ && echo 'OK'"
        ;;
      *redis-data)
        # Redis: 可能有 RDB/AOF 文件
        docker run --rm \
          -v "${old_vol}:/src" \
          -v "${new_vol}:/dst" \
          alpine sh -c "cp -a /src/. /dst/ && echo 'OK'"
        ;;
      *minio-data|*meili-data|*nacos*)
        # 对象存储/索引/日志
        docker run --rm \
          -v "${old_vol}:/src" \
          -v "${new_vol}:/dst" \
          alpine sh -c "cp -a /src/. /dst/ && echo 'OK'"
        ;;
      *)
        # 默认通用方式
        docker run --rm \
          -v "${old_vol}:/src" \
          -v "${new_vol}:/dst" \
          alpine sh -c "cp -a /src/. /dst/ && echo 'OK'"
        ;;
    esac
  fi
  
  migrated=$((migrated + 1))
  log_ok "已迁移: ${old_vol} (${new_vol})"
done
log_ok "卷迁移完成: ${migrated} 个迁移, ${skipped} 个跳过"

# ====== Step 3: 删除旧容器 ======
log_step 3 5 "删除旧容器（防止孤儿容器）"

removed=0
for old_name in "${!CONTAINER_MAP[@]}"; do
  new_name="${CONTAINER_MAP[$old_name]}"
  
  # 新容器在运行说明已经替换过了
  if docker ps --format '{{.Names}}' | grep -q "^${new_name}$"; then
    continue
  fi
  
  if docker ps -a --format '{{.Names}}' | grep -q "^${old_name}$"; then
    log_info "删除旧容器: ${old_name}"
    if ! $DRY_RUN; then
      docker rm -f "$old_name" 2>/dev/null || true
    fi
    removed=$((removed + 1))
    log_ok "已删除: ${old_name}"
  fi
done
log_ok "删除完成: ${removed} 个旧容器"

# ====== Step 4: 清理旧网络 ======
log_step 4 5 "清理旧网络"

for old_net in "kb-infra-net" "kb-net"; do
  if docker network ls --format '{{.Name}}' | grep -q "^${old_net}$"; then
    # 检查是否还有容器连接此网络
    connected=$(docker network inspect "$old_net" --format='{{range .Containers}}{{.Name}} {{end}}' 2>/dev/null | wc -w)
    if [ "$connected" -gt 0 ]; then
      log_warn "旧网络 ${old_net} 还有 ${connected} 个容器连接，暂不删除"
    else
      log_info "删除旧网络: ${old_net}"
      if ! $DRY_RUN; then
        docker network rm "$old_net" 2>/dev/null || true
      fi
      log_ok "已删除: ${old_net}"
    fi
  else
    log_info "${old_net}: 不存在"
  done
done

# ====== Step 5: 验证 ======
log_step 5 5 "验证迁移结果"

echo ""
echo "  --- 旧容器残留检查 ---"
orphan=0
for old_name in "${!CONTAINER_MAP[@]}"; do
  if docker ps -a --format '{{.Names}}' | grep -q "^${old_name}$"; then
    log_err "孤儿容器: ${old_name}"
    orphan=$((orphan + 1))
  fi
done
if [ $orphan -eq 0 ]; then
  log_ok "无旧容器残留"
fi

echo ""
echo "  --- 新卷数据检查 ---"
for new_vol in redis-data mongo-data mysql-data minio-data meili-data nacos-data nacos-logs; do
  full_name="platform-${new_vol}"
  if docker volume ls --format '{{.Name}}' | grep -q "^${full_name}$"; then
    # 用 alpine 检查卷是否有内容
    files=$(docker run --rm -v "${full_name}:/data" alpine sh -c "ls -la /data/ 2>/dev/null | wc -l" 2>/dev/null || echo "0")
    if [ "$files" -gt 1 ]; then
      log_ok "${full_name}: 有数据 (${files} 项)"
    else
      log_warn "${full_name}: 可能为空"
    fi
  else
    log_info "${full_name}: 不存在（对应服务可能未启用）"
  fi
done

echo ""
echo "============================================================="
if $DRY_RUN; then
  echo "  🔍 DRY-RUN 完成！以上为预览结果"
  echo "  确认无误后执行: bash migrate-to-platform.sh（不带 --dry-run）"
else
  echo "  ✅ 迁移完成!"
  echo "  下一步: bash start-platform.sh"
fi
echo "============================================================="
