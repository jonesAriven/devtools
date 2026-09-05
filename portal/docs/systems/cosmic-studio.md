# cosmic-studio 度量表

> COSMIC 功能点度量表生产系统（P0）：把原 Hermes cosmic 技能族的确定性规则下沉为系统门禁，提供「编写库 + 归档库」双库模型、导入导出矩阵、推导引擎与质量门禁，用于标准化产出 COSMIC 度量表（Excel/Word）并做跨库查重与审计。

## 基本信息

| 项 | 值 |
|----|----|
| 分类 | Web 系统（自研） |
| 版本 | 镜像 `cosmic-studio-web:0.2.0` / `cosmic-studio-api:0.2.0`（实采 docker ps）；前端 Vue3 + 后端 FastAPI |
| 部署位置 | 主机 mykng-debain（192.168.31.105）；容器 `cosmic-web` `0.0.0.0:8310->80`（nginx 托管前端 + /api 反代）、`cosmic-api` `8000/tcp`（仅内网，FastAPI） |
| 源码位置 | 本地 `D:\huliang\java\ideaworkspace\devtools\cosmic-studio\`（Gitee `git@gitee.com:jonesAriven/cosmic-studio.git`）；含 `app/`（FastAPI）、`frontend/`（Vue3）、`scripts/`、`docs/` |
| CI/CD | 当前手动 `docker compose up -d --build`（**必须 --build，restart 不更新镜像内代码**）；流水线就绪后接入 Woodpecker |

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

## 核心功能与使用

前后端分离，能力以 `app/` 下 FastAPI 路由暴露（前缀 `/api`，`/docs` 有全量 Swagger）：

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
- **规则即数据**：禁词表 `rule_forbidden_words`、伪字段黑名单 `rule_pseudo_fields`、字段池 `attr_pools` 均在 cosmic_studio 库，评审反哺 = 插规则行立即全局生效。

### 导入导出矩阵
- 增量导入：`POST /api/{dim}/import/xlsx?mode=incremental&project_id=N`（按业务主键 upsert：模块=(project,一/二/三级)，FP=(module,FP名)，命中即整体重写子过程）；JSON 同理 `POST /api/{dim}/import/json`。
- 全量覆盖：`mode=overwrite&project_id=N` 或 `mode=overwrite&confirm=<dimension>`（整库，需二次确认）；覆盖前自动备份整维度 JSON 到 `/data/backups/`，报告回传备份路径。
- 导出：`GET /api/{dim}/projects/{pid}/export/xlsx`（标准 COSMIC xlsx，固定格式：表头 4 行 + 数据第 5 行起，A-C 跨需求合并、D 按模块、E-G+L-M 按 FP、H/I/J/K 每行独立，行高 60，Noto Sans CJK SC）、`/export/json`。

### 版本与系统库
- `POST /api/active/projects/{pid}/versions`：版本快照（xlsx + sha256，禁覆盖）。
- `GET /api/studio/rules`、`/api/studio/vocab`：禁词/伪字段/字段池统计、词库查询。

### 前端（Vue3）
- 编写库/归档库双视图、项目树编辑、推导修复一键执行、lint 报告可视化、导入导出向导、版本管理。

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
- 数据与备份：三库在 MySQL GR（多副本）；覆盖导入自动备份到 `/data/backups/`；MySQL 本身建议纳入定期备份体系。
- 常见问题：
  - 改代码不生效：compose 必须 `--build`，仅 `restart` 不会更新镜像内代码。
  - lint 报错难定位：报告按 12 类分组，优先看 F/E 列格式与 EWX 规范；跨库相似度 85% 多为重复需求，需人工去重或反哺规则。
  - 导入失败：先确认 `mode` 与 `confirm` 参数；覆盖导入会先备份，异常可回滚备份 JSON。

## 变更记录

- 2026-09-05 首次生成（portal 文档补全任务，AI 基于 `cosmic-studio/README.md` + `docs/` + 容器实采 docker ps/health 生成）
