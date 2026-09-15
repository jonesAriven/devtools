# ADR 反向索引

> ⚠️ 原说明「AUTO-GENERATED，由 adr-registry.py 扫描生成」已失效——该脚本已不存在于本机与 mykng（2026-09-14 复核）。
> 本索引现为**手工维护**，与各 ADR 头部状态行保持一致；新增 ADR 时请同步本表。

本仓库 `/root/devtools` 涉及的 ADR（按日期倒序见文末原始清单；外部路径 `/root/hermes-workspace/adr/**` 位于 mykng，未随本仓库版本化）：

| 日期 | ADR | 状态 | 归属 | 位置 |
|---|---|---|---|---|
| 2026-07-18 | ADR-2026-07-18 · Portal 入口看板清理 | 已实施 | mykng | `/root/hermes-workspace/adr/mykng/ADR-2026-07-18-portal-cleanup.md` |
| 2026-07-19 | ADR-2026-07-19 · Portal 三入口架构改造 | 已实施 | mykng | `/root/hermes-workspace/adr/mykng/ADR-2026-07-19-portal-three-entries.md` |
| 2026-07-20 | ADR-2026-07-20 · C++ QR码中文乱码 BOM 头修复 | 已实施 | cross-project | `/root/hermes-workspace/adr/cross-project/ADR-2026-07-20-cpp-qr-bom-fix.md` |
| 2026-07-24 | ADR-2026-07-24 · COSMIC 技能重构 | 已实施 | cosmic | `/root/hermes-workspace/adr/cosmic/ADR-2026-07-24-cosmic-skill-restructure.md` |
| 2026-07-26 | ADR-2026-07-26 · 记忆仓库与开发仓库分离 | 已实施 | cross-project | `/root/hermes-workspace/adr/cross-project/ADR-2026-07-26-memory-vs-dev-repo.md` |
| 2026-07-26 | ADR-2026-07-26 · source.yaml 内容审计与结构扩展 | 已实施 | cross-project | `/root/hermes-workspace/adr/cross-project/ADR-2026-07-26-source-yaml-schema-expansion.md` |
| 2026-08-02 | ADR-2026-08-02 · 激活码时间戳修复三阶段 | 已实施 | cross-project | `/root/hermes-workspace/adr/cross-project/ADR-2026-08-02-activation-timestamp-fix.md` |
| 2026-08-02 | ADR-2026-08-02 · 记忆提炼系统 | 已实施 | hermes-agent | `/root/hermes-workspace/adr/hermes-agent/ADR-2026-08-02-memory-extract.md` |
| 2026-08-05 | ADR-2026-08-05 · CIFS bind mount 路径设计约束 | 已实施 | cross-project | `/root/hermes-workspace/adr/cross-project/ADR-2026-08-05-cifs-bind-mount-constraint.md` |
| 2026-08-05 | ADR-2026-08-05 · Docker daemon 重启防护与 Pip | 已实施 | infrastructure | `/root/hermes-workspace/adr/infrastructure/ADR-2026-08-05-docker-daemon-restart-guard.md` |
| 2026-08-07 | ADR-2026-08-07 · Docker 容器重启恢复策略：@reboot | 已实施 | mykng | `/root/hermes-workspace/adr/mykng/ADR-2026-08-07-docker-restart-after-boot.md` |
| 2026-08-12 | ADR-2026-08-12 · cosmic-system：我的工具箱，不是替 | 部分实施（原始设计文档，2026-09-10 从 cosmic/ 归档入册；未按标准 ADR 模板补全"考虑过的方案/回滚"章节） | cosmic | `/root/hermes-workspace/adr/cosmic/ADR-2026-08-12-cosmic-skill-system-architecture.md` |
| 2026-08-23 | ADR: kb 统一日志架构（三路输出 + Kafka 总线） | 已实施（kb 六模块+portal-server+activecode 八应用接入；Kafka→Loki 消费端已建成） | cross-project | `/root/devtools/mykng/docs/统一日志架构_ADR_20260823.md` |
| 2026-08-23 | ADR-2026-08-23 · memory-extract 面板空转修复 | 已实施 | hermes-agent | `/root/hermes-workspace/adr/hermes-agent/ADR-2026-08-23-memory-extract-panel-fix.md` |
| 2026-08-29 | ADR-2026-08-29 · cosmic-studio 系统建设 | 已实施（P0/规范中心/平台化/需求副本管理/评审修订域/多轮全量测试 全绿） | cross-project | `/root/devtools/cosmic-studio/docs/adr/ADR-2026-08-29-cosmic-studio-system.md` |
| 2026-09-08 | ADR-2026-09-08 · 统一认证单点登录（auth-center +  | 已全面实施 | cross-project | `/root/devtools/docs/adr/ADR-2026-09-08-统一认证单点登录.md` |
| 2026-09-08 | ADR-2026-09-08 · 自研应用仓库地图与公共模块关联机制核实固化 | 已实施 | cross-project | `/root/hermes-workspace/adr/cross-project/ADR-2026-09-08-repo-map-and-shared-modules.md` |
| 2026-09-09 | ADR-2026-09-09 · 公共组件发布到 Nexus npm 私服 | 已实施 | cross-project | `/root/devtools/docs/adr/ADR-2026-09-09-公共组件发布到Nexus私服.md` |
| 2026-09-10 | ADR-2026-09-10 · 平台重构：Phase 0 清死代码 → Pha | ✅ **Phase 0-9 全闭环**（§12-§34：清死代码 / 认证地基 / RBAC / 菜单数据化 / 六应用紧密接入 / 双作用域用户管理 / 五大缺口 + 终态全量回归 §33.13 + 组件接入指南 §34）<br>✅ **Phase 10 权限重构已上线收口**（§35-§36）：默认最小权限 strict 全站生效（平台 user 36→**4**）· superadmin 落库 · infra/portal 密钥分离 E2E 互拒 401 · 邮箱码+忘记密码真发码端到端取证 · SSO 双前缀浮点 404 根治（auth-components 0.8.6 全量部署）。<br>✅ **Phase 10 全部收口（§37，无 P0/P1 遗留）**：另加 kb-web 接口级权限闸门（网关收口，10 个 api 点，普通用户写 403 实测）· activecode 接入完成（UMD 0.8.6 + 邮箱码登录 E2E + 忘记密码 + 账号上报打通 + 越权默认值收敛）· 组件收敛（kb-ops/portal 配置真源统一）· 登录事件静默丢失修复。<br>🎯 **六个自研应用（portal/activecode/kb-web/cosmic/kb-ops/infra）全部接入统一登录·统一鉴权·用户统一管理·权限统一管理·账号映射 —— 初衷达成**（§37.8） | cross-project | `/root/devtools/docs/adr/ADR-2026-09-10-平台重构Phase0清死代码.md` |
