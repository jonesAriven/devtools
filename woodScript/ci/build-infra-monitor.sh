#!/bin/bash
# ============================================================
# build-infra-monitor.sh �?基础设施监控后端构建
# 运行环境: CI 容器 (maven:3.9-eclipse-temurin-21)
# 产物: infra-monitor-latest.tar.gz
# ============================================================
set -euo pipefail
source woodScript/env.sh
source woodScript/lib-build.sh

echo ">>> [1/3] Maven build infra-monitor <<<"
cd infra-monitor/infra-monitor-server
mvn clean package -DskipTests -B -V -ntp \
  -Djacoco.skip=true -Dmaven.repo.local=/root/.m2/repository
cd ../..

echo ">>> [2/3] Collect artifacts <<<"
collect_artifacts infra-monitor \
  infra-monitor/infra-monitor-server/target/infra-monitor.jar

echo ">>> [3/3] Publish <<<"
publish_artifact infra-monitor
echo "OK infra-monitor build done"
