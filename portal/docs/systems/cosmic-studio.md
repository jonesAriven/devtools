# cosmic-studio 度量表

> COSMIC 功能点度量表生产系统（P0）：把原 Hermes cosmic 技能族的确定性规则下沉为系统门禁，提供「编写库 + 归档库」双库模型、导入导出矩阵、推导引擎与质量门禁，用于标准化产出 COSMIC 度量表（Excel/Word）并做跨库查重与审计。

## 基本信息

| 项 | 值 |
|----|----|
| 分类 | Web 系统（自研） |
| 版本 | 镜像 `cosmic-studio-web:0.2.0` / `cosmic-studio-api:0.2.0`（实采 docker ps）；前端 Vue3 + 后端 FastAPI |
| 部署位置 | 主机 mykng-debain（192.168.31.105）；容器 `cosmic-web` `0.0.0.0:8310->80`（nginx 托管前端 + /api 反代）、`cosmic-api` `8000/tcp`（仅内网，FastAPI） |
| 源码位置 | 本地 `D:\huliang\java\ideaworkspace\devtools\cosmic-studio\`（Gitee `git@gitee.com:jonesAriven/cosmic-studio.git`）；mykng 机 `/root/devtools/cosmic-studio`；含 `app/`（FastAPI）、`frontend/`（Vue3）、`scripts/`、`docs/` |
| CI/CD | 当前手动 `docker compose up -d --build`（**必须 --build，restart 不更新镜像内代码**）；流水线就绪后接入 Woodpecker |
| 编排 | mykng 机 `/root/devtools/cosmic-studio/docker-compose.yml`（材料包镜像 `material/composes/cosmic.yml`） |

## 访问入口

- 公网：`https://main.marschat.online/`（整站路径透传，cosmic-web 挂在 8310，经腾讯云2号 nginx → mykng :80 → `127.0.0.1:8310`）
- 内网：`http://192.168.31.105:8310/`（cosmic-web 容器直连）
- Tailscale：`http://100.93.36.113:8310/`
- API 直连（内网）：`http://192.168.31.105:8310/api/...`（cosmic-web 反代到 cosmic-api）；cosmic-api 容器 8000 仅内网，不对外映射。

## 全链路

```
浏览器
  → https://main.marschat.online (腾讯云2号 nginx :443, 整站透传)
  → http://100.93.36.113:80 (mykng nginx)
  → 127.0.0.1:8310 (cosmic-web 容器, nginx)
       /           托管 Vue3 前端静态资源
       /api/*      反代到 cosmic-api (FastAPI, 容器 8000, 仅内网)
  → cosmic-api 读写 MySQL GR：
       platform-mysql-1 (105:3306 入口) + GR 从节点 (182:3307/3308)
       库：cosmic_active / cosmic_archive / cosmic_studio
```

## 系统设计

### 总体架构

FastAPI（`app/`）+ Vue3（`frontend/`）前后端分离，后端三层：

```
app/
├── routers/    # 路由层：auth（/api/auth）、dimension（/api/{dim}，dim=active|archive）、
│               #          reviews（/api/active）、studio（/api 规则/词库）、chat（/api 对话）
├── engines/    # 确定性引擎：derive（F/E/EWX/H/J 推导）、linter（12 类门禁检查）、
│               #              similarity（Jaccard 相似度/预过滤）、spec、vocab_miner（词库挖掘）
├── services/   # 应用服务：xlsx_import/xlsx_export、json_io、tree、versioning、audit、
│               #             llm、project_copy
└── db.py / auth.py / config.py / paging.py
```

角色权限：`users` 表 + `require_role("viewer")` 等依赖注入，接口按角色（viewer 及以上）管控。

### 核心数据模型（scripts/init_db.py，三库同构）

同一套建表脚本在 `cosmic_active`（编写库）/ `cosmic_archive`（归档库）/ `cosmic_studio`（系统库）三库执行：

| 表 | 用途 |
|----|------|
| `projects` | 度量项目 |
| `modules` | 模块（一/二/三级，业务主键 = project + 层级名） |
| `fps` | 功能点 FP（挂模块，自动推导 F/E） |
| `sub_processes` | 子过程（EWX/H/J 数据移动，挂 FP） |
| `screenshots` | 功能过程代码截图 |
| `rule_forbidden_words` | 禁词表（规则即数据） |
| `rule_pseudo_fields` | 伪字段黑名单（PII 拦截） |
| `attr_pools` | 属性池（池化差异化检查依据） |
| `vocab_categories` / `vocab_terms` | 词库分类与词条 |
| `versions` | 版本快照（xlsx + sha256，禁覆盖） |
| `import_jobs` | 导入任务 |
| `spec_rules` | 规格规则 |
| `users` / `app_kv` / `llm_config` / `chat_logs` / `review_items` | 账号 / 系统键值 / LLM 配置 / 对话日志 / 评审项 |

### 关键设计决策

1. **双库模型**：cosmic_active 全量 API 读写（编写工作库），cosmic_archive 仅人工导入（增量 upsert / 全量覆盖，无自动归档）——编写与归档分离，跨库查重（85%）防止重复需求入库。
2. **规则即数据**：禁词表 `rule_forbidden_words`、伪字段黑名单 `rule_pseudo_fields`、字段池 `attr_pools` 均为库表数据，评审反哺 = 插一行规则立即全局生效，无需发版。
3. **确定性引擎优先**：derive/linter/similarity 全部是确定性规则（12 类检查 + Jaccard 相似度预过滤），不依赖 LLM；LLM 仅用于对话/编写辅助，保证门禁结果可复现。
4. **导入覆盖必须可回滚**：全量覆盖前自动备份整维度 JSON 到 `/data/backups/`，报告回传备份路径；版本快照（xlsx+sha256）禁覆盖。

### 对外接口概览

前缀 `/api`，`/docs` 有全量 Swagger：

- `/api/auth`：登录与会话（auth.py）
- `/api/{active|archive}/projects|modules|fps|subs`：项目树 CRUD（dimension.py，`/api/{dim}` 动态前缀）
- `/api/{dim}/projects/{pid}/derive?fix=`、`/lint`：推导与门禁
- `/api/{dim}/import/xlsx|json`、`/projects/{pid}/export/xlsx|json`：导入导出矩阵
- `/api/active/...`（reviews.py）：评审项
- `/api/studio/rules`、`/api/studio/vocab`：规则与词库（studio.py）
- `/api`（chat.py）：LLM 对话；`/api/health`：健康检查

## 部署与发布

### 编排与位置

- compose：mykng 机 `/root/devtools/cosmic-studio/docker-compose.yml`（SSH 实采原文）：

```yaml
services:
  cosmic-api:
    build: { context: ., dockerfile: Dockerfile }
    image: cosmic-studio-api:0.2.0
    container_name: cosmic-api
    restart: unless-stopped
    environment:
      DB_HOST: 192.168.31.105        # platform-mysql 集群成员（host 网络 3306）
      DB_PORT: "3306"
      DB_USER: （账密见 Vaultwarden / infrastructure-map）
      DB_PASSWORD: （账密见 Vaultwarden / infrastructure-map）
      DATA_DIR: /data
    volumes:
      - /root/devtools/cosmic-studio/data:/data
    healthcheck:
      test: ["CMD-SHELL", "python -c \"import urllib.request;urllib.request.urlopen('http://127.0.0.1:8000/api/health',timeout=3)\""]
      interval: 30s / timeout: 5s / retries: 3 / start_period: 10s

  cosmic-web:
    build: { context: ., dockerfile: frontend/Dockerfile.web }
    image: cosmic-studio-web:0.2.0
    container_name: cosmic-web
    restart: unless-stopped
    ports:
      - "8310:80"
    depends_on:
      cosmic-api: { condition: service_started }
```

### 配置清单

| 项 | 值 |
|----|----|
| 端口映射 | cosmic-web：宿主机 8310 → 容器 80；cosmic-api：容器 8000 仅 compose 内网络，无宿主映射 |
| 卷挂载 | `/root/devtools/cosmic-studio/data → /data`（DATA_DIR：备份 JSON 等） |
| 环境（只列名） | `DB_HOST`/`DB_PORT`（platform-mysql 集群入口 105:3306）、`DB_USER`/`DB_PASSWORD`（见 Vaultwarden / infrastructure-map）、`DATA_DIR` |
| 健康检查 | cosmic-api 容器内每 30s 自检 `http://127.0.0.1:8000/api/health` |

### 发布/升级

当前手动发布（不走 woodScript）：

```bash
# mykng 机上
cd /root/devtools/cosmic-studio
docker compose up -d --build    # 必须 --build，restart 不更新镜像内代码
```

初始化（一次性）：`scripts/init_db.py`（建库建表 + 种子规则，幂等）；存量迁移 `scripts/migrate_from_hermes.py`（Hermes SQLite → 三库）。

### 回滚

- 镜像回退：改 compose `image:` tag 到旧版本（0.2.0 → 旧 tag）后 `docker compose up -d --force-recreate`
- 覆盖导入回滚：使用导入报告回传的 `/data/backups/` 备份 JSON，经导入接口恢复
- 版本快照：`versions` 表 xlsx + sha256 可下载还原

## 核心功能与使用

### 两库模型
- **cosmic_active（编写库/工作库）**：API 全量读写 + LLM 编写（P1 规划）。
- **cosmic_archive（归档库）**：**仅人工导入**（增量 upsert / 全量覆盖），无自动归档。
- **cosmic_studio（系统库）**：词库/规则/字段池/版本/导入任务，系统自动 + API 维护。

### 项目与结构（`/api/{active|archive}/projects`）
- `GET /api/{active|archive}/projects`：项目列表（含统计）。
- `POST /api/active/projects`：建项目。
- `GET /api/{dim}/projects/{pid}/tree`：模块/FP/子过程树。
- `POST /api/active/projects/{pid}/modules`：建模块。
- `POST /api/active/projects/{pid}/fps`：建 FP（自动推导 F/E + 标准 EW/ERX 子过程）。
- `POST /api/active/fps/{fid}/subs`：建子过程（自动推导 H/J，校验 EWX/分隔符/字段数）。

### 推导引擎与质量门禁
- `POST /api/{dim}/projects/{pid}/derive?fix=`：推导检查/修复（F/E/EWX/H/J 五列一致性）。
- `GET /api/{dim}/projects/{pid}/lint`：全量门禁（12 类检查 + 跨库相似度），返回报告。
- 12 类检查：F 列格式 / E 列格式 / EWX 规范 / 子过程描述 / 数据组后缀 / 数据属性分隔符与字段数 / 禁词 / 伪字段 PII / 属性池化差异化（重复集合 + Jaccard≥0.85）/ 同需求相似度 65%（E 类豁免）/ 跨库相似度 85%（active↔archive，Jaccard 预过滤）/ 字段池覆盖。

### 导入导出矩阵
- 增量导入：`POST /api/{dim}/import/xlsx?mode=incremental&project_id=N`（按业务主键 upsert：模块=(project,一/二/三级)，FP=(module,FP名)，命中即整体重写子过程）；JSON 同理 `POST /api/{dim}/import/json`。
- 全量覆盖：`mode=overwrite&project_id=N` 或 `mode=overwrite&confirm=<dimension>`（整库，需二次确认）；覆盖前自动备份整维度 JSON 到 `/data/backups/`，报告回传备份路径。
- 导出：`GET /api/{dim}/projects/{pid}/export/xlsx`（标准 COSMIC xlsx，固定格式：表头 4 行 + 数据第 5 行起，A-C 跨需求合并、D 按模块、E-G+L-M 按 FP、H/I/J/K 每行独立，行高 60，Noto Sans CJK SC）、`/export/json`。

### 版本与系统库
- `POST /api/active/projects/{pid}/versions`：版本快照（xlsx + sha256，禁覆盖）。
- `GET /api/studio/rules`、`/api/studio/vocab`：禁词/伪字段/字段池统计、词库查询。

### 前端（Vue3）
- 编写库/归档库双视图、项目树编辑、推导修复一键执行、lint 报告可视化、导入导出向导、版本管理。

### 典型操作路径

- **新表编写**：登录 `http://192.168.31.105:8310/` → 编写库视图 → 建项目 → 建模块 → 建 FP/子过程（引擎自动推导补列）→ derive?fix=true 一键修复 → lint 通过。
- **存量导入**：前端导入导出向导 → 选维度与 mode（incremental 增量 / overwrite 全量需二次确认）→ 上传 xlsx → 查看导入报告（含备份路径）。
- **评审反哺**：lint 报告定位问题 → 归因到禁词/伪字段/字段池 → `/api/studio/rules` 或前端词库页插规则行 → 立即全局生效。
- **归档与查重**：编写库定稿 → 导出 xlsx → 归档库导入 → 新需求 lint 时自动做 active↔archive 跨库 85% 相似度检查。

## 依赖与关联

- 数据库：platform-mysql **GR 集群**（105:3306 入口，多主同步 182:3307/3308），库 cosmic_active/cosmic_archive/cosmic_studio。
- 初始化：`scripts/init_db.py`（建库建表 + 种子规则，幂等，需 root 密码见 Vaultwarden/infrastructure-map）；`scripts/migrate_from_hermes.py`（存量从 Hermes SQLite 迁移）。
- 关联系统：
  - **workcheck 工作量管理系统**：同样含 COSMIC 生成/二代工作台（面向研发团队工作量场景，内置规则引擎）；cosmic-studio 是独立的生产系统，强调双库 + 门禁 + 跨库查重，数据模型不同、互补。
  - infra-monitor / Dozzle / Grafana：可观测 cosmic-web/cosmic-api 容器。

## 运维要点

- 启停：mykng 上 `docker compose up -d --build`（**必须 --build**）；`deploy-*.sh` 见 `docs/DEPLOY.md`。
- 健康检查：`curl http://192.168.31.105:8310/api/health` → `{"status":"ok","db_active":true,"db_archive":true,"db_studio":true}`（2026-09-05 实采确认三库连通）。
- 日志：`docker logs cosmic-web` / `cosmic-api`；`obs-dozzle`（mykng :15500）实时；Grafana/Loki 聚合。
- 数据与备份：三库在 MySQL GR（多副本）；覆盖导入自动备份到 `/data/backups/`（宿主 `/root/devtools/cosmic-studio/data`）；MySQL 本身建议纳入定期备份体系。
- 常见问题：
  - 改代码不生效：compose 必须 `--build`，仅 `restart` 不会更新镜像内代码。
  - lint 报错难定位：报告按 12 类分组，优先看 F/E 列格式与 EWX 规范；跨库相似度 85% 多为重复需求，需人工去重或反哺规则。
  - 导入失败：先确认 `mode` 与 `confirm` 参数；覆盖导入会先备份，异常可回滚备份 JSON。

## 变更记录

- 2026-09-05 v2 补全设计/部署/使用三维度（新增系统设计：routers/engines/services 分层、18 张表模型、4 条设计决策、接口概览；新增部署与发布：compose 原文（SSH 实采）/配置清单/回滚方式；使用节补 4 条典型操作路径；凭证改为 Vaultwarden 引用）
- 2026-09-05 v1 首次生成（portal 文档补全任务，AI 基于 `cosmic-studio/README.md` + `docs/` + 容器实采 docker ps/health 生成）
