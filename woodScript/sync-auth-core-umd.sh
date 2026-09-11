#!/bin/bash
# ============================================================
# sync-auth-core-umd.sh — 同步公共组件 UMD 内联副本
#
# 背景：activation-code-server 前端是手写静态 HTML + 原生 JS，无 npm/vite/vue，
#       无法 import 公共组件，因此把 @marschat/auth-components 的框架无关 UMD
#       构建产物内联到 static/activecode/ 下。
#
# 唯一真源 = marschat-components/packages/auth-components
# 本脚本负责：拷贝 → 重算 sha256 → 回写 VENDORED.md 的版本/大小/哈希
#
# 用法：
#   bash woodScript/sync-auth-core-umd.sh
#
# 前置：公共组件仓已执行 pnpm --filter @marschat/auth-components build:umd
# ============================================================
set -euo pipefail

# 组件仓路径（可由环境变量覆盖，便于 CI/其他机器）
COMPONENTS_DIR="${COMPONENTS_DIR:-/mnt/shared/marschat-components}"
SRC_DIST="${COMPONENTS_DIR}/packages/auth-components/dist"
DST_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/active-manager/activation-code-server/src/main/resources/static/activecode"

SRC_FILE="${SRC_DIST}/marschat-auth-core.umd.js"
DST_FILE="${DST_DIR}/marschat-auth-core.umd.js"
VENDORED_MD="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/active-manager/docs/VENDORED-auth-core-umd.md"

if [ ! -f "${SRC_FILE}" ]; then
  echo "ERROR: 找不到组件产物 ${SRC_FILE}" >&2
  echo "       请先在组件仓执行： pnpm --filter @marschat/auth-components build:umd" >&2
  exit 1
fi

# 版本号取组件 package.json
VERSION="$(sed -n 's/.*"version"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' "${COMPONENTS_DIR}/packages/auth-components/package.json" | head -1)"
[ -n "${VERSION}" ] || { echo "ERROR: 无法从 package.json 解析版本号" >&2; exit 1; }

cp -f "${SRC_FILE}" "${DST_FILE}"

SIZE="$(wc -c < "${DST_FILE}" | tr -d ' ')"
SHA="$(sha256sum "${DST_FILE}" | awk '{print $1}')"

echo ">>> 已内联 @marschat/auth-components ${VERSION}"
echo "    目标: ${DST_FILE}"
echo "    大小: ${SIZE} bytes"
echo "    sha256: ${SHA}"

# 回写 VENDORED.md 的三项易变字段（版本 / 大小 / sha256）
if [ -f "${VENDORED_MD}" ]; then
  sed -i -E "s/^\| 版本 \| \`.*\` \|$/| 版本 | \`${VERSION}\` |/" "${VENDORED_MD}"
  sed -i -E "s/^\| 文件大小 \| .* \|$/| 文件大小 | ${SIZE} bytes |/" "${VENDORED_MD}"
  sed -i -E "s/^\| sha256 \| \`.*\` \|$/| sha256 | \`${SHA}\` |/" "${VENDORED_MD}"
  echo "    已回写 VENDORED.md"
fi

# 自检：哈希必须一致
if [ "$(sha256sum "${SRC_FILE}" | awk '{print $1}')" != "${SHA}" ]; then
  echo "ERROR: 内联副本与组件产物哈希不一致，请重试" >&2
  exit 1
fi
echo "OK sync-auth-core-umd done"
