# ADR 反向索引

> ⚠️ 原说明「AUTO-GENERATED，由 adr-registry.py 扫描生成」**已失效** —— 该脚本已不存在于本机与 mykng（2026-09-14 复核）。本索引现为**手工维护**，与各 ADR 头部状态行保持一致；新增 ADR 时请同步本表。
>
> ⚠️ **路径约定**：下表「位置」列多为 **mykng 服务器路径**（`/root/...`）。本地 Windows 工作区对应关系：
> `/root/devtools` → `D:\huliang\java\ideaworkspace\devtools`｜`/root/auth-center` → `D:\huliang\java\ideaworkspace\auth-center`。
> `/root/hermes-workspace/adr/**` **未随本仓库版本化**（在 mykng 上，本地无副本）。
>
> 🧭 **查"当前该怎么设计/怎么接入"** → 请看 `marschat-components/docs/README.md`（权威手册），**不要**从 ADR 反推现行态；ADR 只回答"当初为何这样定"。

---

## 一、本目录（`devtools/docs/adr/`）· 认证平台重构主线

| 日期 | ADR | 状态 | 位置 |
|---|---|---|---|
| 2026-09-10 | 平台重构：Phase 0 清死代码 → Phase 10 权限收口（含 Phase 1–9 全部过程） | ✅ Phase 0–10 全闭环（§12–§37）<br>⚠️ 但其 §37「无 P0/P1 遗留」「初衷达成」**经 Phase 11/12 复核判定为高估** —— 发现「独立账密仍为应用本地校验（D1–D18）」与「服务端未真正区分平台/应用管理员（C1–C5）」两类遗留。**现行结论请看 `docs/STATUS.md` 与下方两份 Phase ADR** | `ADR-2026-09-10-平台重构Phase0清死代码.md` |
| 2026-09-15 | Phase 11：统一登录与用户/权限管理收敛（双用户管理菜单 + 账密真源归一） | 🟡 实施中 → **主体已落地**：账密真源归一（六应用，含 §1.2 **D8 更正**：kb 系无需改造）· 双用户管理菜单定型 · D9–D18 全部修复。剩余待办见 `STATUS.md` | `ADR-2026-09-15-Phase11-统一登录与用户管理收敛.md` |
| 2026-09-16 | Phase 12：统一认证权限治理（三层权限 API + 两类菜单边界下沉 + activecode 闸门） | 🟡 实施中（P0-1/P0-2/P0-3 已上线；三层 API 地基已交付，**应用侧 path 化收窄待执行**） | `ADR-2026-09-16-Phase12-统一认证权限治理.md` |

## 二、其它仓库内的 ADR

| 日期 | ADR | 状态 | 位置 |
|---|---|---|---|
| 2026-08-23 | kb 统一日志架构（三路输出 + Kafka 总线） | 已实施（kb 六模块 + portal-server + activecode 八应用接入；Kafka→Loki 消费端已建成） | `mykng/docs/统一日志架构_ADR_20260823.md` |
| 2026-08-29 | cosmic-studio 系统建设 | 已实施（P0 / 规范中心 / 平台化 / 需求副本管理 / 评审修订域 / 多轮全量测试 全绿） | `cosmic-studio/docs/adr/ADR-2026-08-29-cosmic-studio-system.md` |

## 三、外部 ADR（mykng `/root/hermes-workspace/adr/**`，未版本化到本仓）

| 日期 | ADR | 状态 | 归属 | 位置 |
|---|---|---|---|---|
| 2026-07-18 | Portal 入口看板清理 | 已实施 | mykng | `.../mykng/ADR-2026-07-18-portal-cleanup.md` |
| 2026-07-19 | Portal 三入口架构改造 | 已实施 | mykng | `.../mykng/ADR-2026-07-19-portal-three-entries.md` |
| 2026-07-20 | C++ QR 码中文乱码 BOM 头修复 | 已实施 | cross-project | `.../cross-project/ADR-2026-07-20-cpp-qr-bom-fix.md` |
| 2026-07-24 | COSMIC 技能重构 | 已实施 | cosmic | `.../cosmic/ADR-2026-07-24-cosmic-skill-restructure.md` |
| 2026-07-26 | 记忆仓库与开发仓库分离 | 已实施 | cross-project | `.../cross-project/ADR-2026-07-26-memory-vs-dev-repo.md` |
| 2026-07-26 | source.yaml 内容审计与结构扩展 | 已实施 | cross-project | `.../cross-project/ADR-2026-07-26-source-yaml-schema-expansion.md` |
| 2026-08-02 | 激活码时间戳修复三阶段 | 已实施 | cross-project | `.../cross-project/ADR-2026-08-02-activation-timestamp-fix.md` |
| 2026-08-02 | 记忆提炼系统 | 已实施 | hermes-agent | `.../hermes-agent/ADR-2026-08-02-memory-extract.md` |
| 2026-08-05 | CIFS bind mount 路径设计约束 | 已实施 | cross-project | `.../cross-project/ADR-2026-08-05-cifs-bind-mount-constraint.md` |
| 2026-08-05 | Docker daemon 重启防护与 Pip | 已实施 | infrastructure | `.../infrastructure/ADR-2026-08-05-docker-daemon-restart-guard.md` |
| 2026-08-07 | Docker 容器重启恢复策略：@reboot | 已实施 | mykng | `.../mykng/ADR-2026-08-07-docker-restart-after-boot.md` |
| 2026-08-12 | cosmic-system：我的工具箱，不是替代品（原始设计文档） | 部分实施（2026-09-10 从 cosmic/ 归档入册；未按标准 ADR 模板补全「考虑过的方案 / 回滚」章节） | cosmic | `.../cosmic/ADR-2026-08-12-cosmic-skill-system-architecture.md` |
| 2026-08-23 | memory-extract 面板空转修复 | 已实施 | hermes-agent | `.../hermes-agent/ADR-2026-08-23-memory-extract-panel-fix.md` |
| 2026-09-08 | **统一认证单点登录（auth-center + OIDC）** | ✅ 已实施 → **已并入归档**：实现形态被终态取代（IdP 会话 Cookie + OIDC PKCE + localStorage 存票）。决策推演归档于 ADR-2026-09-10 §38；**现行设计/接入口径 = `marschat-components/docs/README.md`** | cross-project | ~~本目录原文件已删~~ → ADR-2026-09-10 §38（原文存 git 历史） |
| 2026-09-08 | 自研应用仓库地图与公共模块关联机制核实固化 | 已实施 | cross-project | `.../cross-project/ADR-2026-09-08-repo-map-and-shared-modules.md` |
| 2026-09-09 | **公共组件发布到 Nexus npm 私服** | ✅ 已实施（持续有效）→ **已并入归档**：决策与教训归档于 ADR-2026-09-10 §38；**现行发版流程 = `marschat-components/docs/README.md` 第三篇 §5** | cross-project | ~~本目录原文件已删~~ → ADR-2026-09-10 §38（原文存 git 历史） |

## 四、相关文档（非 ADR，但同属认证平台文档体系）

| 文档 | 定位（权威级别） | 位置 |
|---|---|---|
| **统一认证平台手册** | 🥇 **现行设计与接入的唯一权威**（设计 / 接入 / 使用运维） | `marschat-components/docs/README.md` |
| **平台状态与遗留待办** | 🥇 **现行态**：P0/P1/P2 待办 + 待拍板 + 已知取舍（接手先读） | `marschat-components/docs/STATUS.md` |
| **配置项与环境变量全表** | 🥇 现行态：apps-registry 字段 / env / 端点 / 已注册 client | `marschat-components/docs/CONFIG-REFERENCE.md` |
| 排查手册（症状 → 定位 → 根因） | 🥈 现行态：含取证命令集与「已知误判清单」 | `marschat-components/docs/TROUBLESHOOTING.md` |
| Phase 12 全量验证报告（102 用例 / 0 真实失败） | 🥉 快照（2026-09-17） | `marschat-components/docs/VERIFY-REPORT-2026-09-17.md` |
| Phase 12 R6 多轮浏览器回归与缺陷修复 | 🥉 快照（2026-09-18） | `marschat-components/docs/PHASE12-ROUND6-2026-09-18.md` |
| Phase 12 收口汇总 | ⏳ 历史快照（结论已吸收进 `STATUS.md`，勿单独引用待办） | `marschat-components/docs/PHASE12-SUMMARY-2026-09-17.md` |
| Phase 12 进度交接快照 | ⏳ 历史快照（已被 SUMMARY 取代） | `marschat-components/docs/PHASE12-PROGRESS-2026-09-16.md` |
| CI/CD 配置指南（Gitee Go / Woodpecker / Zadig） | 环境配置，与认证主题无关 | `docs/gitee-go-setup.md`、`docs/woodpecker-cicd-setup.md`、`docs/zadig-setup.md` |

---

> **登记情况**：本目录 3 份 ADR + 2 份其它仓库 ADR + 16 份外部 ADR 均已登记（2026-09-19 核对，无遗漏）。
> ⚠️ 表内状态行若与 ADR 头部状态不一致，以 **ADR 头部 + `STATUS.md`** 为准，并顺手回写本表。
