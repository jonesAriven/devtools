# cosmic-studio

COSMIC 度量表生产系统（P0）：编写/归档两库 + 导入导出矩阵 + 推导引擎 + 质量门禁。
前身是 Hermes 的 cosmic 技能族（7 技能 + cosmic_cli.py），确定性规则全部下沉为系统门禁。

## 两库模型

| 库 | 说明 | 写入路径 |
|---|---|---|
| cosmic_active | 编写库（工作库） | API 全量读写 + LLM 编写（P1） |
| cosmic_archive | 归档库 | **仅人工导入**（增量 upsert / 全量覆盖），无自动归档 |
| cosmic_studio | 词库/规则/字段池/版本/导入任务 | 系统自动 + API |

## 快速开始

```bash
# 1. 建库建表+种子规则（幂等，需 root 密码）
DB_ADMIN_PASSWORD=xxx python scripts/init_db.py

# 2. 存量迁移（Hermes SQLite → MySQL）
python scripts/migrate_from_hermes.py --hermes-dir /root/hermes-workspace/cosmic/db

# 3. 起服务（mykng 上 docker compose）
docker compose up -d --build
# 健康检查: curl http://192.168.31.105:8310/api/health
```

## API 速查（/docs 有全量）

```
GET  /api/health                                    健康检查（含三库连通）
GET  /api/{active|archive}/projects                 项目列表（含统计）
POST /api/active/projects                           建项目
GET  /api/{dim}/projects/{pid}/tree                 模块/FP/子过程树
POST /api/active/projects/{pid}/modules             建模块
POST /api/active/projects/{pid}/fps                 建 FP（自动推导 F/E + 标准 EW/ERX 子过程）
POST /api/active/fps/{fid}/subs                     建子过程（自动推导 H/J，校验 EWX/分隔符/字段数）
POST /api/{dim}/projects/{pid}/derive?fix=          推导检查/修复（F/E/EWX/H/J）
GET  /api/{dim}/projects/{pid}/lint                 全量门禁（12 类检查 + 跨库相似度）
POST /api/{dim}/import/xlsx?mode=&project_id=&confirm=   xlsx 导入（增量 upsert / 覆盖重灌）
POST /api/{dim}/import/json                         JSON 导入（同上语义）
GET  /api/{dim}/projects/{pid}/export/xlsx          标准 COSMIC xlsx 导出（固定格式）
GET  /api/{dim}/projects/{pid}/export/json          JSON 导出
POST /api/active/projects/{pid}/versions            版本快照（xlsx+sha256，禁覆盖）
GET  /api/studio/rules                              禁词/伪字段/字段池统计
GET  /api/studio/vocab                              词库查询
```

## 导入导出矩阵

| | 增量导入 | 全量覆盖 | 导出 |
|---|---|---|---|
| active | `mode=incremental&project_id=N`（按业务主键 upsert） | `mode=overwrite&project_id=N` 或 `mode=overwrite&confirm=active`（整库） | xlsx/json |
| archive | 同上（写入归档库） | 同上（confirm=archive） | xlsx/json |

- 覆盖导入自动先备份整维度 JSON 到 `/data/backups/`，报告回传备份路径。
- 增量导入按业务主键 upsert：模块=(project,一级+二级+三级)，FP=(module,FP名)，命中即整体重写子过程。
- 整库覆盖必须 `confirm=<dimension>` 二次确认。

## 质量门禁（lint 报告）

12 类检查：F列格式 / E列格式 / EWX规范 / 子过程描述 / 数据组后缀 / 数据属性分隔符与字段数 /
禁词 / 伪字段PII / 属性池化差异化（重复集合+Jaccard≥0.85） / 同需求相似度65%（E类豁免） /
跨库相似度85%（active↔archive，Jaccard 预过滤） / 字段池覆盖。

规则即数据：禁词表 `rule_forbidden_words`、伪字段黑名单 `rule_pseudo_fields`、
字段池 `attr_pools` 均在 cosmic_studio 库，评审反哺 = 插规则行立即全局生效。

## xlsx 格式

固定格式（与 Hermes generate_xlsx.py 逐字节同口径）：表头 4 行 + 数据从第 5 行起、
A-C 跨需求合并 / D 按模块 / E-G+L-M+按 FP / H/I/J/K 每行独立（J 不合并）、
每行写全 A-P 列再 merge、行高 60、Noto Sans CJK SC。

## 部署

- **前后端分离容器**：`cosmic-web`（nginx 托管前端 + /api 反代，对外 8310）+ `cosmic-api`（FastAPI，仅内网）
- **数据库**：platform-mysql **GR 集群**（105:3306 入口，多主同步 182:3307/3308），库 cosmic_active/cosmic_archive/cosmic_studio
- 构建与发布步骤见 **[docs/DEPLOY.md](docs/DEPLOY.md)**；使用说明见 **[docs/USER_GUIDE.md](docs/USER_GUIDE.md)**；架构决策见 **[docs/adr/](docs/adr/)**
- 流水线就绪后接入 Woodpecker；当前手动 `docker compose up -d --build`（必须 --build，restart 不更新镜像内代码）
- 仓库：Gitee `git@gitee.com:jonesAriven/cosmic-studio.git`（remote 已配，仓库建立后 `git push -u gitee main`）

## 路线

- P1：LLM 编写引擎（需求文档→自动写 cosmic 入编写库）+ 词库自动晋升 + 代码骨架/截图嵌入
- P2：评审修订 LLM 自动化（三分类/连锁传播矩阵/P列/反哺规则库）
- P3：看板 + MCP 封装
