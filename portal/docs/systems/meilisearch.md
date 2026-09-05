# MeiliSearch 搜索引擎

> 高性能开源全文搜索引擎，作为知识库（KB）体系的全文检索后端，为文章/文档标题与正文提供近实时、带相关度排序的搜索能力。对外经 `kb.marschat.online/meilisearch/` 暴露其自带调试面板。

## 基本信息

| 项 | 值 |
|----|----|
| 分类 | 基础设施（搜索中间件） |
| 版本 | getmeili/meilisearch:v1.12（实采确认；容器内 MEILI 版本以镜像为准） |
| 部署位置 | mykng（192.168.31.105）容器 `platform-meilisearch` |
| 端口 | 7700（HTTP API + 内置调试面板） |
| 数据卷 | 命名卷 `platform_platform-meili-data` → 容器内 `/meili_data` |
| 健康状态 | healthy（docker healthcheck 通过） |
| 源码位置 | 开源组件，官方仓库 https://github.com/meilisearch/MeiliSearch（自部署，无自研源码） |
| CI/CD | 无（自部署，随 platform 层一并拉起） |

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

## 实采配置快照（docker inspect，2026-09-05）

- 启动：容器内执行 `/bin/meilisearch`，HTTP 监听 `0.0.0.0:7700`（`MEILI_HTTP_ADDR`）。
- 运行模式：`MEILI_ENV=production`（生产模式，强制要求主密钥，未带密钥的写操作会被拒）。
- 主密钥：`MEILI_MASTER_KEY` 已设置（值见 Vaultwarden，**禁止明文落盘**）；生产模式下创建索引、写入文档等需携带该密钥。
- 健康检查：容器 healthcheck 状态 healthy；数据卷 `platform_platform-meili-data` → `/meili_data`。

## 核心功能与使用

- 全文检索：对索引（Index）中的文档做分词检索，支持中文（内置分词器对中文按字符切分，长词效果一般，复杂中文场景常配合额外分词策略）。
- 索引与文档管理：在调试面板或通过 API 创建索引、增删改文档、配置可搜索字段（searchable attributes）与排序规则。
- 近实时写入：文档 upsert 后秒级可被检索，适合知识库文章频繁更新场景。
- 相关度与高亮：返回结果带匹配高亮与相关度评分，前端可直接展示。
- 关联 KB：知识库前端的搜索框查询由 `kb-gateway`/`kb-knowledge` 转发到本实例的 :7700 API，返回带高亮与相关度的结果。具体检索字段映射以 KB 服务实现为准。

## 客户端/API 调用示例（组件通用用法）

- 健康检查：`curl https://kb.marschat.online/meilisearch/health`（或内网 `:7700/health`）返回 `{"status":"available"}`。
- 搜索（带主密钥）：`curl -X Post 'https://kb.marschat.online/meilisearch/indexes/<idx>/search' -H "Authorization: Bearer <MASTER_KEY>" -H 'Content-Type: application/json' -d '{"q":"关键词"}'`。
- 调试面板：浏览器打开 `https://kb.marschat.online/meilisearch/`，输入主密钥后可可视化搜索/管理索引。

## 依赖与关联

- 依赖：宿主 mykng 的 docker 引擎与数据卷；无独立外部依赖（索引自管于 `/meili_data`）。
- 被依赖/关联系统：
  - `kb-knowledge`（知识库，8092）、`kb-gateway`（8090）经内网 :7700 调用搜索；
  - `kb-web` / `kb.marschat.online` 的站内搜索体验依赖本实例；
  - 与 MinIO（原始文件）、Qdrant（向量）并列构成 KB 检索/存储底座，但分工不同（见 MinIO 文档的关系表）。

## 运维要点

- 启停方式：随 platform 中间件栈管理。`python woodScript/trigger-pipeline.py platform`；或 mykng 上 `docker compose -f /root/devtools/platform/docker-compose.platform.yml up -d platform-meilisearch`。
- 日志查看：`docker logs platform-meilisearch`（持续 `docker logs -f platform-meilisearch`）；obs-dozzle（mykng :15500）按容器名检索。
- 数据与备份：数据卷 `platform_platform-meili-data` → 容器内 `/meili_data`。备份对该卷做快照/同步（是否纳入统一备份以 infra-monitor 策略为准，待确认）。重建索引可由 KB 服务重新全量 upsert，故索引本身通常可从源头重建。
- 可观测性：日志入 Loki（mykng promtail），可在 Grafana（内网 Deb :15300）检索；检索延迟/错误率可结合 nginx 与容器日志估计。
- 版本升级：实采版本 v1.12（镜像 tag 固定为 `v1.12`，非 latest），升级前需确认 KB 服务 SDK 兼容矩阵，避免 API 破坏性变更。升级建议走 platform 层 compose 改 tag 后 `trigger-pipeline.py platform` 或手工 `docker compose up -d`。

## 常见问题

- 7700 同时承载 API 与调试面板；公网 `kb.marschat.online/meilisearch/` 直接暴露了调试面板，注意主密钥保护，避免未授权索引操作（索引可被匿名增删）。
- 中文检索质量受默认分词影响，长句/专业词召回可能不理想，调优需在写入侧做分词或字段配置（如拆字段、加拼音/同义词）。
- 索引重建：若数据损坏或需调整 schema，可由 KB 服务端触发全量重建，无需手工逐条操作。
- 生产模式必须带主密钥，忘记密钥会导致写 API 返回 401；密钥统一存 Vaultwarden。

## 变更记录

- 2026-09-05 首次生成（portal 文档补全任务，AI 基于实采+源码生成）
