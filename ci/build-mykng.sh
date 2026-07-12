#!/bin/bash
# ============================================================
# build-mykng.sh — mykng 知识库微服务构建 (5个Java微服务)
# 运行环境: CI 容器 (maven:3.9-eclipse-temurin-21)
# 产物: mykng-latest.tar.gz
# ============================================================
set -euo pipefail
source ci/env.sh
source ci/lib-build.sh

echo ">>> [1/3] Maven build mykng (5 services) <<<"
cd mykng/kb-parent
mvn clean package -DskipTests -B -V -ntp -T 2C \
  -Djacoco.skip=true -Dmaven.repo.local=/root/.m2/repository
cd ../..

echo ">>> [2/3] Collect artifacts <<<"
collect_artifacts mykng \
  mykng/kb-gateway/target/kb-gateway.jar \
  mykng/kb-auth/target/kb-auth.jar \
  mykng/kb-file/target/kb-file.jar \
  mykng/kb-knowledge/target/kb-knowledge.jar \
  mykng/kb-intelligence/target/kb-intelligence.jar

echo ">>> [3/3] Publish <<<"
publish_artifact mykng
echo "OK mykng build done"
