#!/bin/bash
# ============================================================
# build-infra-monitor-web.sh �?监控前端构建
# 运行环境: CI 容器 (node:20-alpine)
# 产物: infra-monitor-web-latest.tar.gz
# ============================================================
set -euo pipefail
source woodScript/env.sh
source woodScript/lib-build.sh

setup_pnpm

echo ">>> [1/3] pnpm build infra-monitor-web <<<"
cd infra-monitor/infra-monitor-web
pnpm install --frozen-lockfile
pnpm build
cd ../..

echo ">>> [2/3] Collect artifacts <<<"
mkdir -p publish/infra-monitor-web
cp -r infra-monitor/infra-monitor-web/dist/* publish/infra-monitor-web/
ls -lh publish/infra-monitor-web/

echo ">>> [3/3] Publish <<<"
publish_artifact infra-monitor-web
echo "OK infra-monitor-web build done"
