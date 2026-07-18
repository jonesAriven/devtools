#!/bin/bash
# ============================================================
# cleanup-pnpm-store.sh - pnpm store 清理未引用的包
# ============================================================
# 用法: bash cleanup-pnpm-store.sh
# 运行位置: mykng 主机 (通过 drone-ssh 从 CI 触发)
# 作用: 清理 /mnt/shared/.pnpm-store 里所有前端项目不再引用的包
#       避免 store 目录随时间无限膨胀
# 触发时机: 每次流水线 cleanup step (全量部署完成后)
# ============================================================
set -euo pipefail

STORE_DIR="/var/cache/pnpm-store"
NODE_IMAGE="node:20-slim"

echo ">>> pnpm store 清理 <<<"

if [ ! -d "${STORE_DIR}" ]; then
  echo "  ℹ️  store 目录 ${STORE_DIR} 不存在，跳过（首次运行是正常的）"
  exit 0
fi

# 记录清理前大小
size_before=$(du -sh "${STORE_DIR}" 2>/dev/null | awk '{print $1}')
echo "  清理前: ${size_before}"

# 用 node:20-slim + npx 跑 pnpm store prune
# --yes: 不交互确认；pnpm@latest: 走 Nexus npm-public 里的最新版
# 失败不阻塞流水线（|| true）
docker run --rm \
  -v "${STORE_DIR}:/root/.pnpm-store" \
  "${NODE_IMAGE}" \
  npx --yes pnpm@latest store prune 2>&1 | tail -20 || true

# 记录清理后大小
size_after=$(du -sh "${STORE_DIR}" 2>/dev/null | awk '{print $1}')
echo "  清理后: ${size_after}"
echo "  ✅ pnpm store 清理完成"
