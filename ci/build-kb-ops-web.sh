#!/bin/bash
# ============================================================
# build-kb-ops-web.sh — 运维平台前端构建
# 运行环境: CI 容器 (node:20-alpine)
# 产物: kb-ops-web-latest.tar.gz
# ============================================================
set -euo pipefail
source ci/env.sh
source ci/lib-build.sh

setup_pnpm

echo ">>> [1/3] pnpm build kb-ops-web <<<"
cd kb-ops/kb-ops-web
pnpm install --no-frozen-lockfile || pnpm install --no-frozen-lockfile || pnpm install
pnpm build
cd ../..

echo ">>> [2/3] Collect artifacts <<<"
mkdir -p publish/kb-ops-web
cp -r kb-ops/kb-ops-web/dist/* publish/kb-ops-web/
ls -lh publish/kb-ops-web/

echo ">>> [3/3] Publish <<<"
publish_artifact kb-ops-web
echo "OK kb-ops-web build done"
