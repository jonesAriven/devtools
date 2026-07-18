#!/bin/bash
# ============================================================
# cleanup-pnpm-store.sh - pnpm store 清理未引用的包
# ============================================================
# 用法: bash cleanup-pnpm-store.sh [--force]
# 运行位置: mykng 主机 (通过 drone-ssh 从 CI 触发)
# 作用: 清理 /var/cache/pnpm-store 里不再引用的包，防止 store 目录无限膨胀
#
# 策略 (2026-07-18 v2):
#   1) 频率控制: 距上次清理不足 7 天则跳过（避免每次 CI 都清，浪费缓存收益）
#      用时间戳文件 /var/cache/pnpm-store/.last-prune 记录
#      传 --force 强制清理
#   2) 版本兼容: 用 pnpm@8 而不是 @latest
#      项目 lockfile 有 v6/v9 两代，pnpm@8 都能读，@latest(v10) 会误删活跃包
# ============================================================
set -euo pipefail

STORE_DIR="/var/cache/pnpm-store"
NODE_IMAGE="node:20-slim"
PNPM_VERSION="8.15.9"        # 匹配 portal 用的 v8，能读 v6/v9 lockfile
PRUNE_INTERVAL_DAYS=7        # 每 7 天清一次
STAMP_FILE="${STORE_DIR}/.last-prune"

FORCE=0
if [ "${1:-}" = "--force" ]; then
  FORCE=1
fi

echo ">>> pnpm store 清理 <<<"

if [ ! -d "${STORE_DIR}" ]; then
  echo "  ℹ️  store 目录 ${STORE_DIR} 不存在，跳过（首次运行是正常的）"
  exit 0
fi

# 频率控制：未到 7 天且非 --force 直接跳过
if [ ${FORCE} -eq 0 ] && [ -f "${STAMP_FILE}" ]; then
  last_ts=$(cat "${STAMP_FILE}" 2>/dev/null || echo 0)
  now_ts=$(date +%s)
  elapsed_days=$(( (now_ts - last_ts) / 86400 ))
  if [ ${elapsed_days} -lt ${PRUNE_INTERVAL_DAYS} ]; then
    echo "  ⏭️  距上次清理 ${elapsed_days} 天，未满 ${PRUNE_INTERVAL_DAYS} 天，跳过"
    echo "     (传 --force 参数可强制清理)"
    exit 0
  fi
  echo "  ⏰ 距上次清理 ${elapsed_days} 天 ≥ ${PRUNE_INTERVAL_DAYS} 天，执行清理"
else
  echo "  🆕 首次清理（或 --force）"
fi

# 记录清理前大小
size_before=$(du -sh "${STORE_DIR}" 2>/dev/null | awk '{print $1}')
echo "  清理前: ${size_before}"

# 用 node:20-slim + npx 跑 pnpm@8 store prune
# 失败不阻塞流水线（|| true）
docker run --rm \
  -v "${STORE_DIR}:/root/.pnpm-store" \
  "${NODE_IMAGE}" \
  npx --yes "pnpm@${PNPM_VERSION}" store prune 2>&1 | tail -20 || true

# 记录清理后大小 + 更新时间戳
size_after=$(du -sh "${STORE_DIR}" 2>/dev/null | awk '{print $1}')
date +%s > "${STAMP_FILE}"

echo "  清理后: ${size_after}"
echo "  ✅ pnpm store 清理完成（下次清理时间: $(date -d "@$(($(date +%s) + PRUNE_INTERVAL_DAYS * 86400))" '+%Y-%m-%d')）"
