# ADR-2026-08-29 · cosmic-studio 系统建设

- **日期**：2026-08-29（设计与 P0）～ 2026-08-29（平台化+测试+P1 完成）
- **状态**：已实施（P0+规范中心+平台化+P1 全部上线，测试基线全绿）
- **归属**：cosmic-studio
- **关联**：Hermes cosmic 技能族（7 技能）、agent-share-memory、platform-mysql、Woodpecker CI

---

## 1. 背景

### 1.1 痛点

COSMIC 度量表生产此前完全依赖 Hermes 的 7 个 cosmic 技能（cosmic-writing-workflow 等）+ 990 行 cosmic_cli.py + 一批 Python 脚本，核心问题：

1. **铁律靠 prompt 自觉**：禁词/格式/相似度阈值等十几条硬规则写在技能正文里，LLM 遵守与否看运气，踩坑靠用户当校验器（Hermes 历史教训：评审修订曾 4 个版本才收敛，用户当了 3 次校验器）。
2. **技能不可移植**（铁律 skill_not_portable）：绑定 Hermes 运行时（Linux bash + execute_code），zcode 等其他 agent 无法复用同一套质量门禁。
3. **数据资产分散**：活跃数据在 SQLite、归档在另一个 SQLite、词库在第三个 SQLite，无统一视图与版本管理。
4. **规范变更成本高**：改一条禁词要 patch 技能正文，多次小改还会击穿 prompt cache。

### 1.2 目标

把"LLM 记铁律"变成"系统拦门禁"：LLM 只做创造性内容（FP 命名、属性设计、评审理解），确定性规则全部下沉为系统代码+配置强制执行。

---

## 2. 考虑过的方案与决策

### 2.1 技术栈：Python FastAPI（✅）vs Java SpringBoot vs Go

| 方案 | 评估结论 |
|---|---|
| **Python FastAPI（选中）** | openpyxl/difflib/Pillow 生态全在 Python；Hermes 存量脚本（四步硬门/渲染/导入导出）可直接移植，避免重新踩坑；P1 的 LLM/NLP 生态碾压；低并发内部工具性能无虞 |
| Java SpringBoot | 团队主栈，但 POI 处理 cellimages.xml 要手撸 XML、difflib 无等价物、截图渲染要 Java2D 重写；换语言=重踩全部 xlsx 坑，收益为零 |
| Go | xlsx 生态最弱，直接排除 |

**边界约定**：若未来并入公司 Java 微服务体系，不重写——HTTP 边界已干净，Java 侧加薄 BFF 路由即可；引擎层（xlsx/截图/相似度）永远留在 Python。

### 2.2 系统形态：API-first（✅）vs 一上来全栈 Web vs 纯 CLI

- 决策：P0 纯 API（agent 为第一用户）→ 平台化阶段补 Vue3+Element Plus 前端（响应式+多菜单+权限+对话式）。
- 理由：核心价值在引擎和门禁，UI 不是瓶颈；HTTP 边界先定型，UI 后补不伤架构。实际演进验证了该路径（前端两周内叠加完成，后端零改动）。

### 2.3 数据模型：两库+元数据库（✅）

```
cosmic_active   编写库（系统唯一读写工作库，状态机 draft→confirmed→exported）
cosmic_archive  归档库（只读；唯一写入口=人工导入，增量 upsert / 全量覆盖）
cosmic_studio   元数据（规范/禁词/伪字段/字段池/词库/版本/导入任务/用户/LLM配置）
```

- 归档不自动化是良哥明确要求：归档数据人工增量导入或全量覆盖导入，系统不做自动归档。
- 元数据独立成库的原因：全量覆盖导入会清库，规则/词库绝不能跟着陪葬。

### 2.4 规则引擎：规范即数据（✅）

- 三层来源（优先级）：`spec_rules` 表（25 条编写+截图规范，PUT 即生效）> 独立词表（禁词/伪字段，API 增删）> 代码内种子兜底（表清空不失守）。
- 评审反哺 = 一次 PUT 调用，LLM/agent 可自助反哺；无需改代码、无需重启。
- 实测验证：min_fields_error 3→5 lint 立即多拦 35 条，改回即恢复，行为零漂移。

### 2.5 部署：mykng 手动 docker compose（✅，过渡态）

- 流水线不可用期，良哥授权手动部署，铁律约束：只新增服务，不动现有容器与 docker 后台进程。
- 踩坑记录：Nexus docker-public 回源 docker.io 仅 ~100KB/s（29.79MB 拖 289s，12MB 层卡死 880s）；pypi 回源（aliyun/tuna 上游）正常（20 包 44s）。基础镜像最终靠 Nexus 重试机制拉完；Dockerfile 固化 `pip --timeout 60 --retries 10`。
- 后续流水线就绪后接入 Woodpecker（代码双侧同步：mykng `/root/devtools/cosmic-studio` + Windows `D:\huliang\java\ideaworkspace\cosmic-studio`）。

### 2.6 权限：轻量自研 JWT + 三角色（✅）

- viewer(只读) < editor(编写/导入/版本) < admin(全量)；pbkdf2 密码 + HMAC token，零第三方依赖。
- 关键映射：归档导入 = admin 专属（对应"归档人工维护"定位）；规范修改 = admin；health 探活豁免登录（容器健康检查依赖）。

### 2.7 对话式：工具注册表（✅）

- 每个系统能力注册为 tool（name/description/parameters/min_role/executor），LLM（OpenAI 兼容接口，key 在系统管理页配置）决策调用，5 轮上限，全程留痕。
- 扩展契约：新能力 = 注册一个 tool，对话自动可用。viewer 调写工具在执行器层二次鉴权。

---

## 3. 落地清单

| 阶段 | 内容 | 验证基线 |
|---|---|---|
| P0 | 三库+存量迁移（2+40 项目/75+3780 FP/177 子过程/6593 词/16 字段池，与 SQLite 逐一对账）+ CRUD + derive + 12 类门禁 + 导入导出矩阵 + 版本管理 | 导入导出回环一致、导出对齐空白模板（91 行/214 合并区） |
| 规范中心 | 25 条规范全量入表，代码零硬编码 | 改阈值 lint 即时变化，无行为漂移 |
| 平台化 | Vue3 前端 8 菜单 + JWT 三角色 + 对话式工作台 + 手机自适应 | 浏览器实测截图确认 |
| 测试 | API 53 用例 + 对话工具直测 13 + Playwright UI 冒烟 16 | 全绿；累计修复 5 个 bug |
| P1 增强 | 操作 loading 态 + 面包屑 + Playwright 冒烟固化（入 CI 就绪） | 16/16 + 53/53 回归 |

**测试累计修复的 5 个 bug**（每个都是测试体系抓出来的，不是用户报的）：

1. 项目级覆盖导入 `_wipe` 误删 projects 行 → 模块孤儿、项目"消失"
2. vite `base:'./'` + history 嵌套路由 → 动态 chunk 404 二级页全白屏（一级路由正常，极易漏测）
3. 空/纯空格 requirement_id 建空壳项目（社区技能 ux-heuristics 评估实测抓出）
4. 422 detail 数组直接进 ElMessage 显示异常对象
5. 新建项目对话框表单残留上次输入（冒烟用例 U2.3 作为回归哨兵）

---

## 4. 后果

**正面**：
- 质量门禁从"prompt 自觉"变"系统强制"，errors≠0 出不了门；规则变更零成本。
- 运行时无关：Hermes/zcode/任何 agent 通过同一套 API+对话接口生产，skill_not_portable 难题化解。
- 数据资产统一入 MySQL（platform-mysql），版本链+sha256+自动备份目录齐备。
- 测试双脚本（API+UI 冒烟）可重复回归，改版有安全网。

**负面/风险**：
- 单实例 MySQL（GR 集群已不存在，8-24 重构后仅剩 platform-mysql 单节点），无冗余。
- Nexus docker 回源 ~100KB/s，镜像重建慢（已用本地构建缓解，根治需换上游或预热）。
- JWT 密钥无轮换机制、MySQL 无定时备份（P2 候选项，未拍板）。
- 手动部署是过渡态，流水线接入前存在"服务器代码与 git 不同步"风险（双侧 rsync 缓解）。

**顺手修复的基础设施问题**（非本项目范围但必须记录）：platform-mysql `init_connect` 值含引号导致全部非 root 用户连接被拒（hive 用户重连循环的根因），已 SET GLOBAL 修复 + 四处配置源头去引号（devtools git 工作树改动**未提交**，待良哥入库）。

---

## 5. 演进日志

| 日期 | 事件 |
|---|---|
| 08-29 上午 | 需求拍板（两库/导入导出矩阵/LLM 编写/评审 LLM 化/词库自动晋升）；P0 设计与实现；发现并修复 platform-mysql init_connect 引号 bug |
| 08-29 午后 | 存量迁移完成；docker compose 手动部署（Nexus 回源慢，基础镜像局域网 save/load 绕过）；P0 验证全绿 |
| 08-29 | 规范中心（25 条 spec_rules，规则即数据）；良哥补充平台化需求（UI/对话/权限/菜单/扩展性） |
| 08-29 | 平台化上线：Vue3 前端+JWT+对话工具注册表；浏览器实测 |
| 08-29 | 全量测试：API 53 + 工具 13 + UI 走查，修复 bug #1 #2；社区技能评估（ux-heuristics/webapp-testing 方法论），修复 bug #3 #4 |
| 08-29 | P1（loading/面包屑/Playwright 冒烟），冒烟挖出并修复 bug #5；最终基线 API 53/53 + UI 16/16 |

## 6. 待办与演进方向

- P1 未做部分：LLM 自动编写引擎（需求文档→自动写 cosmic 入编写库）、词库自动晋升管道、代码骨架+截图嵌入（截图规范已配置化预置）
- P2 候选（未拍板）：前端分包、JWT 密钥轮换、MySQL 定时备份、命令面板
- 接入 Woodpecker 流水线；devtools 配置修复入库（等良哥 commit）
