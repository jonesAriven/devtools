#!/bin/bash
# ============================================================
# build-portal-server.sh — Portal 门户后端构建
# 运行环境: CI 容器 (maven:3.9-eclipse-temurin-21)
# 产物: portal-server-latest.tar.gz
# ============================================================
set -euo pipefail
source ci/env.sh
source ci/lib-build.sh

echo ">>> [1/3] Maven build portal-server <<<"
cd portal/portal-server
mvn clean package -DskipTests -B -V -ntp \
  -Djacoco.skip=true -Dmaven.repo.local=/root/.m2/repository
cd ../..

echo ">>> [2/3] Collect artifacts <<<"
collect_artifacts portal-server portal/portal-server/target/portal-server.jar

echo ">>> [3/3] Publish <<<"
publish_artifact portal-server
echo "OK portal-server build done"
