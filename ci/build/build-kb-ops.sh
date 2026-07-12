#!/bin/bash
# ============================================================
# build-kb-ops.sh — kb-ops 运维平台构建
# 运行环境: CI 容器 (maven:3.9-eclipse-temurin-21)
# 产物: kb-ops-latest.tar.gz
# ============================================================
set -euo pipefail
source ci/env.sh
source ci/lib-build.sh

echo ">>> [1/3] Maven build kb-ops <<<"
cd kb-ops
mvn clean package -DskipTests -B -V -ntp \
  -Djacoco.skip=true -Dmaven.repo.local=/root/.m2/repository
cd ..

echo ">>> [2/3] Collect artifacts <<<"
collect_artifacts kb-ops kb-ops/target/kb-ops.jar

echo ">>> [3/3] Publish <<<"
publish_artifact kb-ops
echo "OK kb-ops build done"
