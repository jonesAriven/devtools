# Qdrant 向量库

> 开源向量数据库，作为 RAG（检索增强生成）管线的向量检索后端，存储文本向量的集合（Collection）并提供相似度检索，配合 bge-small-zh-v1.5 embedding 模型服务使用。当前仅内网可达，不对外暴露。

## 基本信息

| 项 | 值 |
|----|----|
| 分类 | 基础设施（向量数据库） |
| 版本 | qdrant/qdrant:latest（实采确认） |
| 部署位置 | 内网 Debian（192.168.31.182，MiWiFi-RD15-srv）容器 `rag-qdrant` |
| 端口 | 6333（HTTP API + Web Dashboard）、6334（gRPC API） |
| 数据卷 | bind 挂载 `/var/lib/qdrant-storage` → 容器内 `/qdrant/storage`（另 bind `/etc/localtime`） |
| 配套 embedding | 同机容器 `rag-embedding`（镜像 rag-embedding:bge-small-zh-v1.5，端口 8081，模型目录 bind `/mnt/shared/temp/rag-data/models`） |
| 运行模式 | RUN_MODE=production；时区 Asia/Shanghai |
| 源码位置 | 开源组件，官方仓库 https://github.com/qdrant/qdrant（自部署，无自研源码） |
| CI/CD | 无（自部署） |

## 访问入口

- 公网：**—**（无公网反代，不对外暴露；实采确认腾讯云2号 nginx 与 mykng nginx 均无 qdrant 相关 location）
- 内网：`http://192.168.31.182:6333`（Dashboard + API）、`http://192.168.31.182:6334`（gRPC）
- Tailscale：`http://100.105.196.63:6333`、`http://100.105.196.63:6334`
- 账密：启用 API Key 时见 Vaultwarden（vault.marschat.online）或 infrastructure-map 技能（禁止明文落盘）

## 全链路

```
RAG 服务/embedding → http://192.168.31.182:6333  (HTTP)
                   → http://192.168.31.182:6334  (gRPC)
  内网直连，无公网反代；不经由腾讯云2号 nginx
```

> 注：Qdrant 仅在内网 Debian 上运行，未做公网暴露。Dashboard（:6333/dashboard）实测返回 200，可访问。调用方（RAG 管线/embedding 服务）均在同一内网或经 Tailscale 访问。embedding 服务 `rag-embedding` 与 `rag-qdrant` 同机部署，降低向量化→入库的网络开销。

## 实采配置快照（docker inspect，2026-09-05）

- 启动：容器入口 `./entrypoint.sh`，运行模式 `RUN_MODE=production`，时区 `TZ=Asia/Shanghai`。
- 数据目录：bind `/var/lib/qdrant-storage` → `/qdrant/storage`（向量与索引持久化）。
- 配套 embedding 服务 `rag-embedding`（同机）：
  - 镜像 `rag-embedding:bge-small-zh-v1.5`，启动 `gunicorn -w 1 -b 0.0.0.0:8081 --timeout 600 server:app`；
  - 模型 `MODEL_NAME=BAAI/bge-small-zh-v1.5`，缓存 `MODEL_CACHE=/app/models`（bind `/mnt/shared/temp/rag-data/models`）；
  - 出网经 HTTP 代理 `172.17.0.1:7890`（模型首次拉取用），`no_proxy` 覆盖内网网段；
  - 运行时 Python 3.11。
- Dashboard 探活：`:6333/dashboard` 返回 200。

## 核心功能与使用

- 向量集合（Collection）管理：创建集合、配置向量维度与距离度量（如 cosine），在 Dashboard 或 API 中查看点（Point）数量与集合列表。
- 相似度检索：给定查询向量，返回 Top-K 最相近的文本块，是 RAG「先检索后生成」的关键环节。
- 配套 embedding：文本→向量由同机 `rag-embedding`（bge-small-zh-v1.5）完成，默认维度 512；写入/查询 Qdrant 前需经该 embedding 服务统一向量化，保证维度与语义一致。
- 过滤与 payload：可在向量检索时附加 payload 过滤（如按知识库 ID、文档来源过滤），具体过滤字段由 RAG 业务定义。
- 批量与点操作：支持批量 upsert、按 ID 获取/删除点，适合知识库增量更新。

## 客户端/API 调用示例（组件通用用法）

- 健康检查：`curl http://192.168.31.182:6333/health` 返回 `{"status":"ok"}`。
- 列出集合：`curl http://192.168.31.182:6333/collections -H "Api-Key: <KEY>"`（若启用密钥）。
- Dashboard：浏览器打开 `http://192.168.31.182:6333/dashboard`（内网/Tailscale）。
- 检索：通过 embedding 服务 `http://192.168.31.182:8081` 取得查询向量后，POST `/collections/<name>/points/search`。

## 依赖与关联

- 依赖：宿主内网 Debian 的 docker 引擎与本地存储目录 `/var/lib/qdrant-storage`；embedding 向量化依赖同机 `rag-embedding` 服务（bge-small-zh-v1.5，:8081）。
- 被依赖/关联系统：
  - RAG 知识库管线（kb-knowledge / kb-intelligence 等相关服务）通过内网 :6333 读写向量；
  - `rag-embedding` 与 `rag-qdrant` 同机部署，形成「向量化—入库—检索」闭环；
  - 与 MeiliSearch 互为补充：MeiliSearch 做关键词全文检索，Qdrant 做语义向量检索，RAG 常二者融合（hybrid）以提升召回。

## 运维要点

- 启停方式：在内网 Debian 上以 docker 运行（容器 `rag-qdrant`）。具体 compose/启动命令以该机部署脚本为准（未逐一实采启动文件，待确认完整命令）。
- 日志查看：`docker logs rag-qdrant`（持续 `docker logs -f rag-qdrant`）；obs-dozzle（内网 Deb :15888）按容器名检索。
- 数据与备份：数据目录 bind 挂载 `/var/lib/qdrant-storage` → 容器内 `/qdrant/storage`。备份对该目录做快照/同步（是否纳入统一备份以 infra-monitor 策略为准，待确认）。向量索引可由 embedding 服务重新全量重建，故一般可从源头恢复。
- 可观测性：容器日志入 Loki（内网 Deb promtail），可在 Grafana（内网 Deb :15300）检索；Dashboard 自带基础统计。
- 安全：仅内网/Tailscale 可达，天然不暴露公网；若启用 API Key，凭证存 Vaultwarden。公网不可直接连，跨网访问必须走 Tailscale。

## 版本与兼容性

- 镜像为 `qdrant/qdrant:latest`（非固定版本），升级会与 embedding 维度/距离配置耦合，建议锁定大版本并与 `rag-embedding` 的模型维度（bge-small-zh-v1.5 = 512）保持一致。
- 升级前确认现有 Collection 的向量维度与距离函数不被破坏性变更影响。

## 常见问题

- embedding 维度必须与集合创建时声明的一致（bge-small-zh-v1.5 为 512），否则写入/检索报维度不匹配。
- 仅内网可达，跨网访问需走 Tailscale；公网不可直接连。
- 向量库与 embedding 服务务必同模型版本，换模型需重建集合并重新向量化全量语料。
- embedding 服务首次需经代理拉取模型权重（172.17.0.1:7890），离线环境需预置 `/app/models`。

## 变更记录

- 2026-09-05 首次生成（portal 文档补全任务，AI 基于实采+源码生成）
