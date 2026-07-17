#!/bin/bash
# ============================================================
# build-kb-web.sh - kb 主前端构建
# 运行环境: CI 容器 (node:20-alpine)
# 产物: kb-web-latest.tar.gz
# ============================================================
set -euo pipefail
source woodScript/env.sh
source woodScript/lib-build.sh

setup_pnpm

echo ">>> [1/3] pnpm build kb-web <<<"
cd mykng/kb-web
pnpm install --no-frozen-lockfile || pnpm install --no-frozen-lockfile || pnpm install
pnpm build
cd ../..

echo ">>> [2/3] Collect artifacts <<<"
mkdir -p publish/kb-web
cp -r mykng/kb-web/dist/* publish/kb-web/
ls -lh publish/kb-web/

echo ">>> [3/3] Publish <<<"
publish_artifact kb-web
echo "OK kb-web build done"
