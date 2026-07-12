#!/bin/bash
# ============================================================
# build-portal-web.sh �?Portal 门户前端构建
# 运行环境: CI 容器 (node:20-slim)
# 产物: portal-web-latest.tar.gz
# ============================================================
set -euo pipefail
source woodScript/env.sh
source woodScript/lib-build.sh

setup_pnpm

echo ">>> [1/3] pnpm build portal-web <<<"
cd portal
pnpm install --no-frozen-lockfile || pnpm install --no-frozen-lockfile || pnpm install
pnpm build
cd ..

echo ">>> [2/3] Collect artifacts <<<"
mkdir -p publish/portal-web
cp -r portal/dist/* publish/portal-web/
ls -lh publish/portal-web/

echo ">>> [3/3] Publish <<<"
publish_artifact portal-web
echo "OK portal-web build done"
