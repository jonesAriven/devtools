# MeiliSearch 搜索引擎

> 高性能开源全文搜索引擎，作为知识库（KB）体系的全文检索后端，为文章/文档标题与正文提供近实时、带相关度排序的搜索能力。对外经 `kb.marschat.online/meilisearch/` 暴露其自带调试面板。

## 基本信息

| 项 | 值 |
|----|----|
| 分类 | 基础设施（搜索中间件） |
| 版本 | getmeili/meilisearch:v1.12（实采确认；镜像 tag 固定 v1.12） |
| 部署位置 | mykng（192.168.31.105）容器 `platform-meilisearch`，compose project `platform` |
| 端口 | 7700（HTTP API + 内置调试面板） |
| 数据卷 | 命名卷 `platform_platform-meili-data` → 容器内 `/meili_data` |
| 健康状态 | healthy（docker healthcheck 通过） |
| 源码位置 | 开源组件，官方仓库 https://github.com/meilisearch/MeiliSearch（自部署，无自研源码） |
| CI/CD | 无独立流水线（自部署，随 platform 层一并拉起） |

## 访问入口

- 公网：`https://kb.marschat.online/meilisearch/`（经 mykng nginx 反代至 :7700，含 MeiliSearch 自带调试面板）
- 内网：`http://192.168.31.105:7700`
- Tailscale：`http://100.93.36.113:7700`
- 账密：主密钥（Master Key）见 Vaultwarden（vault.marschat.online）或 infrastructure-map 技能（禁止明文落盘）

## 全链路

```
浏览器/服务 → https://kb.marschat.online/meilisearch/
  → 腾讯云2号 nginx (443, TLS 终止) → http://100.93.36.113:80
  → mykng nginx /meilisearch/ → proxy_pass http://127.0.0.1:7700/
  → 容器 platform-meilisearch (:7700)
```

> 注：mykng nginx 的 `meilisearch.conf` 中 `location /meilisearch/ { proxy_pass http://127.0.0.1:7700/; }`——尾斜杠已对齐，访问路径 `/meilisearch/` 等价于直接访问 MeiliSearch 根。公网入口即 MeiliSearch 自带的 Web 调试面板（Search Preview），生产检索流量由 KB 服务经内网 :7700 直连。

## 系统设计

### 组件架构

MeiliSearch 是 Rust 实现的搜索引擎：数据模型为 Index → Documents（JSON 文档集合），写入近实时可检索（秒级）；通过 searchableAttributes / filterableAttributes / rankingRules 配置检索行为；单二进制运行、索引自管于数据目录，生产模式强制主密钥鉴权。同一端口（7700）同时承载 REST API 与自带 Web 调试面板。

### 我们的集成设计

- **实例角色**：`platform-meilisearch` 是 platform 基础设施层的全文检索底座，承担 KB 体系文章/文档的关键词搜索——存可检索的文本索引（非原始文件）。
- **谁读写它**：
  - 写：`kb-knowledge`（知识库，8092）在文章/文档变更时向 :7700 upsert/删除文档；
  - 读：`kb-gateway`（8090）/ `kb-knowledge` 转发前端搜索框查询到 :7700，返回带高亮与相关度评分的结果；`kb-web` 的站内搜索体验依赖本实例。
  - 具体索引划分与检索字段映射以 KB 服务实现为准。
- **为什么选它**：开箱即用、默认即有中文分词与相关度排序，无需另建 ES 集群；单二进制 + 256m 内存限额足够知识库规模；API 简单，前端可直接消费高亮结果。
- **与向量检索的分工**：与 Qdrant 互补——MeiliSearch 做关键词全文检索（精确词、过滤、排序），Qdrant 做语义向量检索（RAG），RAG 场景常二者融合（hybrid）提升召回。
- **关键配置思路**：
  - `MEILI_ENV=production` 强制主密钥；`MEILI_MASTER_KEY` 经环境变量注入（值见 Vaultwarden，禁止落盘）；
  - 数据用命名卷 `platform-meili-data` 持久化；healthcheck 走 `/health`；mem_limit 256m。

## 部署与发布

### 编排与位置

- compose 文件：`/root/devtools/platform/docker-compose.platform.yml`（compose project：`platform`，服务名 `platform-meilisearch`）
- 网络：`platform-net`（bridge；KB 服务经 external 引入互访）
- 归属：platform 中间件层**不归任何流水线管理**，手动启动、持久运行

### 配置清单

| 项 | 值 |
|----|----|
| 容器名 | `platform-meilisearch` |
| 镜像 | `getmeili/meilisearch:v1.12` |
| 端口映射 | 宿主 7700 → 容器 7700（HTTP API + 调试面板） |
| 卷挂载 | 命名卷 `platform-meili-data` → `/meili_data`（索引持久化） |
| 环境变量 | `MEILI_MASTER_KEY`（主密钥，值见 Vaultwarden）、`MEILI_ENV=production` |
| healthcheck | `curl -f http://localhost:7700/health`，间隔 10s |
| 资源限制 | mem_limit 256m |
| 重启策略 | `unless-stopped` |

### 发布/升级

- 常规拉起：`python woodScript/trigger-pipeline.py platform`，或在 mykng 上手工：
  `docker compose -p platform -f /root/devtools/platform/docker-compose.platform.yml up -d platform-meilisearch`
- 停止：`docker compose -p platform -f /root/devtools/platform/docker-compose.platform.yml down`
- 升级：改 compose 中镜像 tag（当前固定 `v1.12`，非 latest）→ 重建容器；升级前确认 KB 服务 SDK 兼容矩阵，避免 API 破坏性变更。

### 回滚

- 镜像回退：compose 改回旧 tag（如 v1.11.x）后 `up -d platform-meilisearch`。
- 数据回退：还原命名卷 `platform_platform-meili-data` 快照；索引亦可由 KB 服务重新全量 upsert 重建，通常无需依赖卷快照。

## 核心功能与使用

### 功能清单

- **全文检索**：对 Index 中文档分词检索，支持中文（内置分词按字符切分，长词效果一般，复杂场景需写入侧配合）。
- **近实时写入**：文档 upsert 后秒级可检索，适合知识库文章频繁更新。
- **索引与文档管理**：调试面板或 API 创建索引、增删改文档、配置 searchable attributes 与排序规则。
- **相关度与高亮**：结果带匹配高亮与相关度评分，前端可直接展示。
- **过滤与排序**：支持 filterableAttributes 过滤与自定义 rankingRules（具体字段由 KB 服务定义）。

### 典型操作路径

1. **调试面板**：浏览器打开 `https://kb.marschat.online/meilisearch/`（或内网 `:7700`）→ 输入主密钥 → 选 Index → Search Preview 试搜 / 查看 Documents、Settings。
2. **健康探测**：`curl https://kb.marschat.online/meilisearch/health` → `{"status":"available"}`。
3. **程序搜索（curl 为例）**：`POST /indexes/<idx>/search`，Header 带 `Authorization: Bearer <MASTER_KEY>`，Body `{"q":"关键词"}`。
4. **索引重建**：由 KB 服务端触发全量 upsert（数据损坏或调整 schema 时），无需手工逐条操作。

## 依赖与关联

- 依赖：宿主 mykng 的 docker 引擎与数据卷；无独立外部依赖（索引自管于 `/meili_data`）。
- 被依赖/关联系统：
  - `kb-knowledge`（8092）、`kb-gateway`（8090）经内网 :7700 调用搜索；
  - `kb-web` / `kb.marschat.online` 的站内搜索体验依赖本实例；
  - 与 MinIO（原始文件）、Qdrant（向量）并列构成 KB 检索/存储底座，分工见 MinIO 文档关系表。

## 运维要点

- 启停：随 platform 中间件栈管理（见「部署与发布」）；单服务重建命令同上。
- 日志：`docker logs -f platform-meilisearch`；obs-dozzle（mykng :15500）按容器名检索。
- 数据与备份：数据卷 `platform_platform-meili-data` → `/meili_data`（是否纳入统一备份以 infra-monitor 策略为准，待确认）；索引可从源头（KB 服务）重建。
- 可观测性：日志入 Loki（mykng promtail → 内网 Deb obs-loki :15100），Grafana（内网 Deb :15300）可检索；检索延迟/错误率可结合 nginx 与容器日志估计。
- 版本升级：实采版本 v1.12（tag 固定非 latest）；升级注意 KB 服务 SDK 兼容矩阵与索引文件格式兼容性。

## 常见问题

- 7700 同时承载 API 与调试面板；公网 `kb.marschat.online/meilisearch/` 直接暴露了调试面板，注意主密钥保护，避免未授权索引操作。
- 中文检索质量受默认分词影响，长句/专业词召回可能不理想，调优需在写入侧做分词或字段配置（如拆字段、加拼音/同义词）。
- 生产模式必须带主密钥，忘记密钥会导致写 API 返回 401；密钥统一存 Vaultwarden。
- 索引重建走 KB 服务端全量 upsert，不手工逐条补数据。

## 变更记录

- 2026-09-05 v2 补全设计/部署/使用三维度（新增系统设计、部署与发布节，compose/配置细节引自 platform.yml 原文与 docker inspect 实采）
- 2026-09-05 v1 首次生成（portal 文档补全任务，AI 基于实采+源码生成）
