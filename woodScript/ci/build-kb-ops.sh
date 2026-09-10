#!/bin/bash
# ============================================================
# build-kb-ops.sh — kb-ops 运维平台构建
# 运行环境: CI 容器 (maven:3.9-eclipse-temurin-21)
# 产物: kb-ops-latest.tar.gz
# ============================================================
set -euo pipefail
source woodScript/env.sh
source woodScript/lib-build.sh

echo ">>> [1/3] Maven build kb-ops <<<"
cd kb-ops
# 2026-09-10 Phase 0.5：去掉 -DskipTests。kb-ops 的单测是本次改造中唯一被实测证明
# 常年全绿的套件（130 用例 / 本机 mvn test 全过），因此先从这里放开回归信号。
mvn clean package -B -V -ntp \
  -Djacoco.skip=true -Dmaven.repo.local=/root/.m2/repository
cd ..

echo ">>> [2/3] Collect artifacts <<<"
collect_artifacts kb-ops kb-ops/target/kb-ops.jar

echo ">>> [3/3] Publish <<<"
publish_artifact kb-ops
echo "OK kb-ops build done"
