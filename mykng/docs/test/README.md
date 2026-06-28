# MyKNG 知识库平台 — 测试文档索引

> **版本**：v1.1
> **更新日期**：2026-06-28
> **适用对象**：开发、测试、运维人员
> **维护说明**：本目录是 SOP V1.1「研发交付全流程标准作业」所要求的测试文档体系，所有提测/发布动作均以本目录文档为准入准出依据。

---

## 一、目录结构

```
docs/test/
├── README.md                              # 本文件：测试文档索引与流程概述
├── test-plan/                             # 测试计划（SOP 阶段 0）
│   └── 测试计划_v1.1.md                   # 6 层自测 + 业务功能专项策略
├── test-cases/                            # 测试用例
│   ├── 测试用例清单.md                    # 单元/集成/BDD/E2E/非功能用例全集
│   └── 业务状态机路径覆盖矩阵.md          # 文档/分享/文件解析状态机覆盖
├── features/                              # BDD feature 文件说明
│   └── README.md                          # .feature 文件清单与 Cucumber 执行方式
├── checklists/                            # 各阶段 Checklist
│   ├── 自测Checklist.md                   # SOP 阶段 2：6 层自测 + 业务功能专项
│   ├── 提测准入Checklist.md               # SOP 阶段 3：提测 DoR + 提测单模板
│   ├── 发布上线Checklist.md               # SOP 阶段 5：发布 DoD + 三阶段 + 回滚铁律
│   ├── CodeReview_Checklist.md            # SOP 附录 C：代码审查清单
│   ├── 安全Checklist.md                   # SOP 附录 D：安全检查清单
│   └── 性能Checklist.md                   # SOP 附录 E：性能检查清单
├── bug-reports/                           # 缺陷报告
│   └── bug-template.md                    # Bug 报告模板（含状态流转）
└── test-reports/                          # 测试报告归档
    └── README.md                          # 报告命名规范与内容模板
```

---

## 二、文档清单

| 序号 | 文档 | 路径 | SOP 阶段 | 用途 |
|------|------|------|---------|------|
| 1 | 测试计划 | `test-plan/测试计划_v1.1.md` | 阶段 0 | 测试目标、范围、6 层策略、环境、通过准则、风险 |
| 2 | 测试用例清单 | `test-cases/测试用例清单.md` | 阶段 0/1 | 单元/集成/BDD/E2E/非功能用例全集，可执行级别 |
| 3 | 状态机覆盖矩阵 | `test-cases/业务状态机路径覆盖矩阵.md` | SOP 2.5.2 | 文档/分享/文件解析状态机迁移全覆盖 |
| 4 | BDD feature 说明 | `features/README.md` | SOP 2.5.1 | .feature 文件位置、清单、Cucumber 执行 |
| 5 | 自测 Checklist | `checklists/自测Checklist.md` | 阶段 2 | 6 层自测 + 业务功能专项逐项打勾 |
| 6 | 提测准入 Checklist | `checklists/提测准入Checklist.md` | 阶段 3 | 提测 DoR + 提测单模板 |
| 7 | 发布上线 Checklist | `checklists/发布上线Checklist.md` | 阶段 5 | 发布 DoD + 三阶段 + 观测期 + 回滚铁律 |
| 8 | Code Review Checklist | `checklists/CodeReview_Checklist.md` | 附录 C | 命名/SQL/异常/事务/日志/幂等审查 |
| 9 | 安全 Checklist | `checklists/安全Checklist.md` | 附录 D | 权限/越权/SQL注入/XSS/加密/限流 |
| 10 | 性能 Checklist | `checklists/性能Checklist.md` | 附录 E | P99/EXPLAIN/N+1/并发/压测/泄漏 |
| 11 | Bug 报告模板 | `bug-reports/bug-template.md` | 阶段 4 | Bug 等级/复现/状态流转 |
| 12 | 测试报告归档说明 | `test-reports/README.md` | 阶段 6 | 报告命名规范与内容模板 |

---

## 三、测试流程概述

```
阶段0 测试计划 ──► 阶段1 用例设计 ──► 阶段2 开发自测 ──► 阶段3 提测准入(DoR)
      │                  │                  │                    │
      ▼                  ▼                  ▼                    ▼
 测试计划_v1.1    测试用例清单        自测Checklist         提测准入Checklist
                  状态机矩阵          (6层+2.5专项)         (含提测单)
                                                                  │
阶段4 测试执行 ◄──────────────────────────────────────────────────┘
      │
      ▼
 bug-reports/  +  test-reports/测试报告_YYYYMMDD_HHmm.md
      │
阶段5 发布上线(DoD) ──► 阶段6 上线观测
      │                      │
      ▼                      ▼
 发布上线Checklist      test-reports/ 归档
 (三阶段+回滚铁律)
```

### 3.1 关键节点

| 节点 | 入口文档 | 出口产物 |
|------|---------|---------|
| 测试计划评审 | `test-plan/测试计划_v1.1.md` | 评审通过的测试计划 |
| 用例评审 | `test-cases/测试用例清单.md` + `状态机矩阵` | 用例基线 |
| 开发自测 | `checklists/自测Checklist.md` | 自测报告（6 层全绿） |
| 提测准入 | `checklists/提测准入Checklist.md` | 提测单 + SIT 冒烟通过 |
| 测试执行 | `test-cases/` + `bug-reports/bug-template.md` | 测试报告 + Bug 列表 |
| 发布上线 | `checklists/发布上线Checklist.md` | 发布记录 + 回滚预案 |
| 上线观测 | `test-reports/` | 观测期报告 |

---

## 四、与 SOP V1.1 的对应关系

| SOP V1.1 阶段 | SOP 要求产物 | 本目录对应文档 |
|---------------|-------------|---------------|
| 阶段 0：测试计划 | 测试计划（目标/范围/策略/环境/准则/风险） | `test-plan/测试计划_v1.1.md` |
| 阶段 1：用例设计 | 用例清单 + 状态机覆盖矩阵 + BDD feature | `test-cases/*` + `features/README.md` |
| 阶段 2：开发自测 | 6 层自测 + 业务功能专项 Checklist | `checklists/自测Checklist.md` |
| 阶段 3：提测准入 | 提测 DoR + 提测单 | `checklists/提测准入Checklist.md` |
| 阶段 4：测试执行 | Bug 报告 + 测试报告 | `bug-reports/` + `test-reports/` |
| 阶段 5：发布上线 | 发布 DoD + 回滚预案 | `checklists/发布上线Checklist.md` |
| 附录 C：Code Review | CR Checklist | `checklists/CodeReview_Checklist.md` |
| 附录 D：安全 | 安全 Checklist | `checklists/安全Checklist.md` |
| 附录 E：性能 | 性能 Checklist | `checklists/性能Checklist.md` |

### 4.1 SOP V1.1 六层自测体系（本目录强制遵循）

| 层级 | 名称 | 工具 | 准入门槛 |
|------|------|------|---------|
| 层级 1 | 变异测试 | PITest | 变异覆盖率 ≥ 70% |
| 层级 2 | 编译静态检查 | Maven Compiler + SonarQube + 敏感信息扫描 | 0 error，SonarQube 0 阻断 |
| 层级 3 | 单元测试 | JUnit 5 + Mockito | 行覆盖 ≥ 85%，异常分支 ≥ 80% |
| 层级 4 | 接口集成测试 | Spring Boot Test + MockMvc | 正常+异常+幂等+并发+事务回滚全通过 |
| 层级 5 | 功能流程测试 | 终端实际操作 + Console 检查 | 关键流程闭环，Console 无报错 |
| 层级 6 | 业务场景验收 | Cucumber BDD | BDD 场景全绿 |
| 2.5 专项 | 业务功能专项 | BDD+状态机+数据工厂+断言库+E2E 录制 | 状态机 100% 覆盖，E2E 录制归档 |

---

## 五、测试范围速查

| 模块 | 端口 | API 数 | 单元用例 | 集成用例 | BDD 场景 |
|------|------|--------|---------|---------|---------|
| kb-gateway | 8090 | — | — | 8 | — |
| kb-auth | 8081 | 12 | 15 | 8 | 登录认证 |
| kb-file | 8082 | 13 | 7 | 8 | 文件上传搜索 |
| kb-knowledge | 8083 | 51 | 14 | 16 | 文档生命周期/分享访问 |
| kb-ops | 8084 | 47 | 5 | 8 | 运维看板 |
| kb-intelligence | 8086 | — | 9 | — | 知识解析 |
| kb-common | — | — | 7 | 7 | — |
| **合计** | — | **123** | **57** | **55** | **5** |

> 详细用例见 `test-cases/测试用例清单.md`。

---

## 六、参考文档

| 文档 | 用途 |
|------|------|
| [../测试方案_v1.md](../测试方案_v1.md) | 5 层测试金字塔基础方案（L1~L5） |
| [../接口规范清单_v1.md](../接口规范清单_v1.md) | API 契约（123 接口，v2.3） |
| [../知识库部署方案_20260628.md](../知识库部署方案_20260628.md) | 双层 Nginx 架构、KB_CONTEXT 配置 |
| [../私有化全端个人知识库_v7.md](../私有化全端个人知识库_v7.md) | 系统架构、服务拆分 |

---

## 七、变更记录

| 版本 | 日期 | 变更内容 |
|------|------|---------|
| v1.1 | 2026-06-28 | 初版：按 SOP V1.1 建立 6 层自测 + 业务功能专项测试文档体系，包含 12 份正式文档 + 本索引 |
