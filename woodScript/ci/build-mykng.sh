#!/bin/bash
# ============================================================
# build-mykng.sh — mykng 知识库微服务构建（4 个 Java 模块）
# 运行环境: CI 容器 (maven:3.9-eclipse-temurin-21)
# 产物: mykng-latest.tar.gz
# ============================================================
set -euo pipefail
source woodScript/env.sh
source woodScript/lib-build.sh

echo ">>> [1/3] Maven build mykng (4 modules) + registry consistency gate <<<"
cd mykng/kb-parent
# 注册表漂移门禁：这里放开 surefire 但用 -Dtest 只跑一致性用例。
# 目的在于拦住「改了 mykng/module-registry.yml 却没跑 gen-registry.py」这类漂移 ——
# 它过去只会表现为「菜单静默消失」，现在是构建失败。
#
# ⚠️ 属性名必须是 surefire.failIfNoSpecifiedTests（surefire 3.x 的 user property），
#    写成裸 failIfNoSpecifiedTests 会被静默忽略 —— 该用例只存在于 kb-gateway，
#    其余 3 个模块匹配不到用例即报 "No tests matching pattern ... were executed!"，
#    门禁反而把构建打红。2026-09-10 本地按 CI 同命令实测踩到（见 ADR §12.11）。
#
# 为什么没有去掉 -DskipTests 跑全量：本仓库的完整单测里含需要 Nacos 的 @SpringBootTest
# 与绑定 verify 阶段的 pitest(mutationThreshold=70)，全量放开是独立决策，需先实测稳定。
# 被排除在这道门禁之外的回归信号，见 ADR Phase 0.5 的「未覆盖」清单。
mvn clean package -B -V -ntp -T 2C \
  -Dtest=ModuleManifestConsistencyTest -Dsurefire.failIfNoSpecifiedTests=false \
  -Djacoco.skip=true -Dmaven.repo.local=/root/.m2/repository \
  -Dmykng.registry.file="$(cd .. && pwd)/module-registry.yml"
cd ../..

echo ">>> [2/3] Collect artifacts <<<"
collect_artifacts mykng \
  mykng/kb-gateway/target/kb-gateway.jar \
  mykng/kb-file/target/kb-file.jar \
  mykng/kb-knowledge/target/kb-knowledge.jar \
  mykng/kb-intelligence/target/kb-intelligence.jar

echo ">>> [3/3] Publish <<<"
publish_artifact mykng
echo "OK mykng build done"
