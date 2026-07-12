#!/bin/bash
# ============================================================
# build-active-manager.sh — 激活码系统构建
# 运行环境: CI 容器 (maven:3.9-eclipse-temurin-21)
# 产物: active-manager-latest.tar.gz
# ============================================================
set -euo pipefail
source ci/env.sh
source ci/lib-build.sh

echo ">>> [1/3] Maven build active-manager <<<"
cd active-manager/activation-code-server
mvn clean package -DskipTests -B -V -ntp \
  -Djacoco.skip=true -Dmaven.repo.local=/root/.m2/repository
cd ../..

echo ">>> [2/3] Collect artifacts <<<"
collect_artifacts active-manager \
  active-manager/activation-code-server/target/activation-code-server-*.jar

echo ">>> [3/3] Publish <<<"
publish_artifact active-manager
echo "OK active-manager build done"
