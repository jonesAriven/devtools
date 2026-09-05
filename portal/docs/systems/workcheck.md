# 工作量管理系统（WorkCheck）

> 面向研发团队的「工作量自检 + COSMIC 功能点度量」统一平台：管理员做日报/周报/工时/功能点清单管理与系统配置，员工端填报工作日报并由 AI 生成内容、自动产出 COSMIC 拆分表。由「工作量查重」与「COSMIC 自动生成」两系统合并而成，FastAPI 单仓双端。

## 基本信息

| 项 | 值 |
|----|----|
| 分类 | Web 系统（自研） |
| 版本 | FastAPI 应用（`app/main.py` 标注 `title="WorkCheck System", version="1.0"`）；前端 Vue3 + jQuery/layui 静态托管 |
| 部署位置 | 主机 mykng-debain（192.168.31.105）；容器 `workcheck-python`，`0.0.0.0:8010->8000`；启动命令 `uvicorn app.main:app --host 0.0.0.0 --port 8000`；数据卷 `/mnt/shared/workcheck-python/data → /app/data` |
| 源码位置 | mykng 机 `/root/workcheck_python`（独立仓库，Gitee 双推）；本地仅有 `.env` 与 `data/` 镜像，非完整源码 |
| CI/CD | 独立仓库，**不走 woodScript**；自带 docker compose，手动 `docker compose up -d --build` 发布（2026-09-05 实采确认容器与挂载） |
| 编排 | `/root/workcheck_python/docker-compose.yml`（材料包无此文件，SSH 实采） |

## 访问入口

- 公网：`https://workcheck.marschat.online/`（腾讯云2号 nginx `workcheck.marschat.online` → mykng :80 → `127.0.0.1:8010`）
- 内网：`http://192.168.31.105/workcheck/`（mykng nginx 路径 `/workcheck/`、`/workcheck/api/`、`/static/` 均反代 127.0.0.1:8010）
- Tailscale：`http://100.93.36.113/workcheck/`
- 直接容器端口：`http://192.168.31.105:8010/`（2026-09-05 探活 200）

## 全链路

```
浏览器（员工端 / 管理端）
  → https://workcheck.marschat.online (腾讯云2号 nginx :443)
  → http://100.93.36.113:80 (mykng nginx)
       /workcheck/、/workcheck/api/、/static/ → 127.0.0.1:8010 (workcheck-python)
  → FastAPI 应用（静态页在 /app/static，API 在 /api 与各路由前缀）
       DB: SQLite（WAL）或 MySQL（由 DATABASE_URL 切换）
```

## 系统设计

### 总体架构

FastAPI 单体应用 + 双静态前端（无 Node 构建链）：

```
app/
├── main.py        # FastAPI 入口：挂载 /static、注册 20 个 routes 路由 + 8 组 /api 路由 + 6 组 cosmic2 路由
├── models.py      # SQLAlchemy 2.0 ORM 模型（20 张表）
├── routes/        # 工作量域路由（auth/node/staff/period/work/word/email/compare/function_list/account/history/system_config/menu_permission/mail_config/rbac/llm_config/case_manage/cosmic_config/token_usage）
├── api/           # COSMIC 域路由（/api 前缀：tasks/cases/rules/prompts/monitor/doc_parse/usage/validator/tools）
├── cosmic2/       # COSMIC 二代工作台（api/ops/sizing/check/gen/quality 六组路由）
├── core/ db/ defaults/ utils/   # 核心/数据库/默认配置/工具
└── static/        # staff/（员工端）、admin/（管理端）、cosmic2/staff/、cosmic2/admin/ 四套页面
```

### 核心数据模型（app/models.py，20 张表）

| 表 | 用途 |
|----|------|
| `administrator` | 账号（admin 字段区分员工 0 / 管理员 1 / 超管 2） |
| `role` | 角色与接口权限点 |
| `auth_token` | 登录令牌 |
| `audit_log` | 审计日志 |
| `node` | 组织节点树（邮件提醒开关、日报模板、AI 生成开关） |
| `staffinfo` | 员工信息 |
| `dayworkdetail` | 工作日报明细 |
| `workdetail` / `workcontent` | 周报工时与内容 |
| `periodconfig` | 填报周期（按周，自动算应出勤天数） |
| `holiday` | 节假日 |
| `functionlist` / `functionlisthis` / `functionperiod` | 功能点清单及历史归档、周期 |
| `wordconfig` | 敏感词配置 |
| `system_config` / `admin_menu_config` | 系统配置 / 菜单权限 |
| `mail_account` / `mail_account_node` | 邮件账号及节点关联 |
| `user_llm_config` | 员工个人模型配置（Key 掩码） |

### 关键设计决策

1. **单仓双端 + 静态前端无构建**：员工端与管理端两套页面纯静态托管在 `/static`（jQuery/layui + Vue3 混用），后端一个 FastAPI 进程承载，部署只需 `docker compose up -d --build`，无前端构建产物发布环节。
2. **数据库可切换**：`DATABASE_URL` 默认 `sqlite:///./workcheck.db`，可切 MySQL；检索增强用独立的 `search_index.db`（查重索引，路径由 `SEARCH_INDEX_PATH` 指定）。
3. **LLM 配置分层回落**：员工个人模型（`user_llm_config`）→ 服务端 `.env` 兜底 Key（`DEEPSEEK_API_KEY`/`DEEPSEEK_BASE_URL`/`DEEPSEEK_MODEL`），共享组件 `llm-config-panel.js` 提供模型来源/提示词/用量监控入口。
4. **规则引擎与 LLM 分工**：日报查重（jieba 分词 + 相似度阈值 + 去重索引）、COSMIC 自动审核（规则引擎）、二代工作台质量门禁走确定性规则；LLM 只负责内容生成与功能点抽取，两阶段并行。

### 对外接口概览

- 工作量域（`app/routes/*`，路由无统一前缀，页面 `/work`、`/compare`、`/case_manage`、`/history` 及各配置页配套接口）：work（日报/周报）、compare（查重）、history（历史导入）、各管理配置。
- COSMIC 域（`app/api/*`，前缀 `/api`）：`/api/tasks`（生成任务）、`/api/cases`（案例库）、`/api/rules`（审核规则）、`/api/prompts`、`/api/monitor`、`/api/doc-parse`、`/api/usage`、`/api/validator`、`/api/tools`（均需登录态）。
- COSMIC 二代（`app/cosmic2/*` 六组路由）：api / ops / sizing / check / gen / quality。

## 部署与发布

### 编排与位置

- compose：`/root/workcheck_python/docker-compose.yml`（SSH 实采原文）：

```yaml
services:
  workcheck-python:
    build: .
    container_name: workcheck-python
    restart: on-failure:5
    ports:
      - "8010:8000"
    environment:
      - TZ=Asia/Shanghai
      - SEARCH_INDEX_PATH=/app/search_index.db
    volumes:
      - /mnt/shared/workcheck-python/data:/app/data
    extra_hosts:
      - "host.docker.internal:host-gateway"
    networks:
      - platform-net
    mem_limit: 256m

networks:
  platform-net:
    external: true
    name: platform-net
```

### 配置清单

| 项 | 值 |
|----|----|
| 端口映射 | 宿主机 8010 → 容器 8000 |
| 卷挂载 | `/mnt/shared/workcheck-python/data → /app/data`（含 search_index.db、exports/rules/templates） |
| 网络 | platform-net（external，连全局基础设施） |
| 资源限制 | mem_limit 256m |
| 环境变量（只列名） | `DATABASE_URL`（SQLite/MySQL 切换）、`SEARCH_INDEX_PATH`、`DEEPSEEK_API_KEY`/`DEEPSEEK_BASE_URL`/`DEEPSEEK_MODEL`（服务端兜底 LLM Key）；compose 自动读取同目录 `.env`，真实值见 Vaultwarden / infrastructure-map 技能 |

### 发布/升级

不走 woodScript，手动发布：

```bash
# mykng 机上
cd /root/workcheck_python
docker compose up -d --build    # 必须 --build，restart 不更新镜像内代码
```

### 回滚

- 应用无状态（数据在挂载卷 + DB），回退 = git checkout 旧版本后重新 `docker compose up -d --build`
- 查重索引损坏可删除 `search_index.db` 后经 `/compare/rebuildDedupIndex` 重建

## 核心功能与使用

### 员工端（`static/staff/`，账号 `admin=0`）
- **工作日报**（`/work` 路由：`getWorkTime`、`saveDayWorkDetail`、`getDayWorkContent`、`dayReport`、`deleteDay*`）：按日填报工作内容/工时/请假调休/加班；提交后自动级联生成并更新对应周报；支持 AI 智能生成工作/加班内容（基于员工最近 80 条日报，节点开启 `openAiWorkContentSwitch` 后生效）。
- **COSMIC 生成**（`app/api/tasks.py` 等）：需求文本 → NLU 解析 → COSMIC 功能点拆分表（E/R/W/X 数据移动、CFP 统计，两阶段并行生成）→ 在线编辑 → 自动审核（规则引擎）→ 历史/文档内查重 → 导出 Excel/Word 需求规格说明书；案例库支持 zip/单独 xlsx 批量导入与进度展示。
- **标准一览**：工作日报填写规范查阅。
- **COSMIC 度量工作台（二代，`static/cosmic2/staff/`）**：需求文档上传（docx/pdf，质量预检）→ 异步生成（LLM 抽取 + 规则判定 E/X/R/W，进度可视）→ 三栏复核（原文证据高亮联动/规则建议/一键修复）→ 近似估算/变更对比/成本区间 → 导出 Excel(5 sheet)/Word 正式报告/审计追溯包；支持第三方 Excel 度量表导入检查。
- 全局入口（共享组件 `llm-config-panel.js`）：⚙ 模型来源 / 📝 提示词配置 / 📊 模型监控；个人模型未配置时回落服务端 `.env` 兜底 Key。

### 管理端（`static/admin/`，账号 `admin=1` 限本节点，`admin=2` 超管全节点）
- **工作量管理**（`/work`、`/compare`、`/case_manage`、`/history`）：
  - 日报管理：按节点/日期查询明细、删除、导出 Excel。
  - 周报管理：按周期/节点查询；员工只有日报无周报时系统自动按日报聚合回填；删除/导出（含内容或仅工时两种）。
  - 工时一览：周期内员工工时统计。
  - 功能点清单：Excel 上传解析、季度归档、历史下载。
  - 工作量自检（`/compare`：`check`、`checkRepeat`、`batchCheckRepeat`、`calculateSimilarity`、`dedupIndexStatus`、`rebuildDedupIndex`）：上传工作量 Excel 逐项校验（账号有效性、长度、敏感词、工时合理性、历史及文件内查重），含去重索引状态与重建。
  - 案例库管理（`/case_manage`）：分页查询/预览/编辑/删除恢复/批量下载；按相似度阈值聚合重复案例组；全部删除（软删/物理删两级）。
  - 导入历史工作量（`/history`：`importHistoryWork`、`getHistoryImportTasks`、`getHistoryPeriods`、`getHistoryWorkDetail`）：导入外部历史 Excel（两种模板自动识别），按条拆分到每天归档，加班落加班当天，员工缺失自动建档。
- **系统配置**（`/period`、`/node`、`/staff`、`/cosmic_config`、`/mail_config`、`/account`、`/rbac`、`/menu_permission`、`/system_config`、`/word`、`/llm_config`、`/token_usage`）：
  - 周期管理：填报周期配置（按周，自动算应出勤天数）；每周一凌晨 1 点 APScheduler 自动新增本周周期。
  - 节点维护：组织节点树、邮件提醒开关、日报模板、AI 生成开关。
  - 员工信息维护：CRUD、Excel 批量导入导出、模板下载。
  - 敏感词配置、日报重复度阈值（仅超管）、邮件发送配置（仅超管 SMTP）、账号分配（仅超管，关联节点+角色+重置密码）、员工模型配置（仅超管，Key 掩码）、角色管理（仅超管，接口权限点+可见菜单）、审计日志（仅超管）、菜单权限管控（仅超管）。
  - COSMIC 配置中心（二代，`static/cosmic2/admin/`）：12 个配置页——概览/检查规则(60+条 L1-L4)/动词词典/Prompt 模板/造价基准 CSBMK/估算参数/示例库/金标准回归门禁/治理看板/LLM 网关(路由/降级链/配额/成本)/质量词表/规则包版本。

### 典型操作路径

- **员工报日报**：登录 `https://workcheck.marschat.online/` → 员工端 → 工作日报 → 填写当日工作/工时（或点 AI 生成）→ 保存（周报自动级联更新）。
- **管理员查重**：管理端 → 工作量自检 → 上传工作量 Excel → 逐项校验结果 → 需要时重建去重索引。
- **超管配账号**：管理端 → 账号分配 → 新建账号并关联节点+角色 → 重置密码。
- **COSMIC 二代出表**：员工端 → COSMIC 工作台 → 上传需求文档 → 异步生成 → 三栏复核修复 → 导出 Excel/Word 报告。

### 账号与角色（`administrator` 表，`admin` 字段区分）
- 员工 `0` / 管理员 `1`（限名下节点 `node.userId`）/ 超级管理员 `2`（仅内置 `admin` 账号，全节点，独占账号分配）。
- 账号必须关联节点；超管账号不可页面增删改，启动时若缺失自动建默认超级管理员账号（**上线后改密，账密见 Vaultwarden 或 infrastructure-map**）。

## 依赖与关联

- 存储/中间件：SQLite（WAL，默认）或 MySQL（由 `DATABASE_URL` 切换）；Elasticsearch（`.env` 配 `ELASTICSEARCH_URL`，检索增强，**是否启用待确认**）；LLM（DeepSeek 等 OpenAI 兼容接口，`.env` 配 `DEEPSEEK_*`）。
- 技术组件：SQLAlchemy 2.0、APScheduler（周期/定时任务）、httpx、openpyxl、python-docx、jieba（查重分词）、Vue3 + Element Plus（登录/框架/COSMIC 页）、jQuery + layui + bootstrap-table（管理页，纯静态无构建）。
- 关联系统：与 cosmic-studio 同属 COSMIC 度量领域——本系统内置 COSMIC 生成/二代工作台（面向研发团队工作量场景），cosmic-studio 为独立的生产系统（编写库/归档库/推导引擎/质量门禁，P0）。两者能力互补、数据模型不同。

## 运维要点

- 启停：mykng 上 `docker compose` 管理 `workcheck-python`（端口 8010）；发布须 `docker compose up -d --build`（restart 不更新镜像内代码）。
- 健康检查：`curl -s -o /dev/null -w '%{http_code}' http://192.168.31.105:8010/` → `200`（2026-09-05 实采）。
- 日志：`docker logs workcheck-python`；`obs-dozzle`（mykng :15500）实时；Grafana/Loki 聚合。
- 数据与备份：业务数据在 `/app/data`（挂载 `/mnt/shared/workcheck-python/data`，含 `search_index.db`、exports/rules/templates）；SQLite/MySQL 需纳入备份体系（待确认是否已在定期备份）。
- 常见问题：
  - 周报缺失：员工只填日报未生成周报时，管理端查询会自动按日报聚合回填。
  - AI 生成不出现：检查节点 `openAiWorkContentSwitch` 是否开启、员工个人模型来源或服务端 `.env` 兜底 Key 是否配置。
  - 查重不准：去重索引可经 `/compare/rebuildDedupIndex` 重建；阈值由超管在「日报重复度阈值」配置。

## 变更记录

- 2026-09-05 v2 补全设计/部署/使用三维度（新增系统设计：目录架构/20 张表模型/4 条设计决策/三组路由概览；新增部署与发布：compose 原文/配置清单/手动发布与回滚；使用节补典型操作路径；路由清单经 SSH 读源码实采）
- 2026-09-05 v1 首次生成（portal 文档补全任务，AI 基于 `/root/workcheck_python` 源码 `main.py`/`routes/*`/`api/*` + README + 容器实采生成）
