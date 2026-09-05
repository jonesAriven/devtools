# Qdrant 向量库

> 开源向量数据库，作为 RAG（检索增强生成）管线的向量检索后端，存储文本向量的集合（Collection）并提供相似度检索，配合同机 rag-embedding（bge-small-zh-v1.5）embedding 模型服务使用。当前仅内网可达，不对外暴露。

## 基本信息

| 项 | 值 |
|----|----|
| 分类 | 基础设施（向量数据库） |
| 版本 | qdrant/qdrant:latest（实采确认） |
| 部署位置 | 内网 Debian（192.168.31.182，MiWiFi-RD15-srv）容器 `rag-qdrant` |
| 端口 | 6333（HTTP API + Web Dashboard）、6334（gRPC API） |
| 数据卷 | bind 挂载 `/var/lib/qdrant-storage` → 容器内 `/qdrant/storage`（另 bind `/etc/localtime`） |
| 配套 embedding | 同机容器 `rag-embedding`（镜像 rag-embedding:bge-small-zh-v1.5，端口 8081，模型目录 bind `/mnt/shared/temp/rag-data/models`） |
| 运行模式 | RUN_MODE=production；时区 Asia/Shanghai；重启策略 unless-stopped |
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

## 系统设计

### 组件架构

Qdrant 是 Rust 实现的向量数据库：数据模型为 Collection → Point（向量 + payload），支持 HNSW 索引、多种距离度量（cosine/欧氏/点积）与 payload 过滤检索；提供 HTTP（:6333）与 gRPC（:6334）两套 API，并自带 Web Dashboard。单节点部署时全部数据（向量、索引、payload）自管于存储目录，无需外部依赖。

### 我们的集成设计

- **实例角色**：`rag-qdrant` 是 RAG 管线的语义检索底座，存知识库文本块经 embedding 后的向量（及来源 payload），承担「先检索后生成」中的检索环节。
- **谁读写它**：
  - 写：RAG 入库管线调用同机 `rag-embedding`（:8081）把文本块向量化后 upsert 进 Collection；
  - 读：RAG 查询管线把用户问题向量化后做 Top-K 相似度检索，检索结果交给生成环节；相关 RAG 管线运行于 kb-knowledge / kb-intelligence 等服务（具体服务边界待确认）。
- **配套 embedding**：`rag-embedding` 镜像 `rag-embedding:bge-small-zh-v1.5`（gunicorn 单 worker，:8081，超时 600s），模型 `BAAI/bge-small-zh-v1.5`（向量维度 512），模型缓存 bind `/mnt/shared/temp/rag-data/models` → `/app/models`；出网经代理 `172.17.0.1:7890`（仅模型首次拉取用），`no_proxy` 覆盖内网。二者同机部署，形成「向量化—入库—检索」闭环。
- **为什么选它**：单二进制、资源占用小、带 Dashboard 便于调试，REST + gRPC 双协议够用；与 MeiliSearch 分工——MeiliSearch 做关键词全文检索、Qdrant 做语义向量检索，RAG 常二者融合（hybrid）提升召回。
- **关键配置思路**：
  - 数据 bind 挂载 `/var/lib/qdrant-storage` → `/qdrant/storage`，便于直接备份宿主目录；
  - `RUN_MODE=production`、`TZ=Asia/Shanghai`；重启策略 `unless-stopped` 保活；
  - 集合维度必须与 embedding 模型一致（bge-small-zh-v1.5 = 512），换模型需重建集合并全量重向量化。

## 部署与发布

### 编排与位置

- **无 compose 编排**（2026-09-05 实采 docker inspect 确认：容器无 com.docker.compose.* 标签，宿主机未检索到含 rag-qdrant 的 compose/yml/启动脚本）——`rag-qdrant` 与 `rag-embedding` 均为 `docker run` 手工启动，配置靠重启策略 `unless-stopped` 保活。**所属 compose project：无（待确认是否有离线部署脚本未入库）**。

### 配置清单（docker inspect 实采）

| 项 | 值 |
|----|----|
| 容器名 | `rag-qdrant` |
| 镜像 | `qdrant/qdrant:latest` |
| 端口映射 | 宿主 6333 → 6333（HTTP/Dashboard）、6334 → 6334（gRPC） |
| 卷挂载 | bind `/var/lib/qdrant-storage` → `/qdrant/storage`（数据）；bind `/etc/localtime` → `/etc/localtime`（时区） |
| 环境变量 | `RUN_MODE=production`、`TZ=Asia/Shanghai` |
| 网络 | bridge |
| 重启策略 | `unless-stopped` |
| 配套容器 | `rag-embedding`：端口 8081，bind `/mnt/shared/temp/rag-data/models` → `/app/models`，ENV `MODEL_NAME=BAAI/bge-small-zh-v1.5`、`MODEL_CACHE=/app/models`，HTTP 代理 `172.17.0.1:7890`（首次拉模型用） |

### 发布/升级

- 自部署，无流水线。重建/升级步骤：确认数据目录已备份 → `docker stop rag-qdrant && docker rm rag-qdrant` → 按上表参数 `docker run` 重建（**建议先把当前 run 参数固化为 compose 文件入库，避免下次重建参数漂移，待确认**）。
- 升级耦合提醒：镜像为 latest，升级会与 embedding 维度/距离配置耦合，建议锁定大版本并与 `rag-embedding` 模型维度（512）保持一致；升级前确认现有 Collection 的维度与距离函数不受破坏性变更影响。

### 回滚

- 镜像回退：用旧镜像 ID 重新 `docker run`（latest 无版本锚点，建议升级前 `docker tag` 留存）。
- 数据回退：还原宿主目录 `/var/lib/qdrant-storage` 快照；向量索引可由 embedding 服务重新全量重建，一般可从源头恢复。

## 核心功能与使用

### 功能清单

- **向量集合管理**：创建 Collection、配置向量维度与距离度量（如 cosine），Dashboard/API 查看点数量与集合列表。
- **相似度检索**：给定查询向量返回 Top-K 最相近文本块，是 RAG「先检索后生成」的关键环节。
- **payload 过滤**：检索时附加过滤条件（如按知识库 ID、文档来源），具体字段由 RAG 业务定义。
- **批量与点操作**：批量 upsert、按 ID 获取/删除点，适合知识库增量更新。
- **配套向量化**：文本→向量统一经同机 `rag-embedding`（:8081），保证维度与语义一致。

### 典型操作路径

1. **Dashboard 查看**：浏览器打开 `http://192.168.31.182:6333/dashboard`（内网/Tailscale）→ 查看 Collections、Point 数量与基础统计。
2. **健康探测**：`curl http://192.168.31.182:6333/health` → `{"status":"ok"}`。
3. **列集合（若启用密钥）**：`curl http://192.168.31.182:6333/collections -H "Api-Key: <KEY>"`。
4. **检索联调**：先 `POST http://192.168.31.182:8081`（embedding 服务）取查询向量 → 再 `POST /collections/<name>/points/search` 检索 Top-K。

## 依赖与关联

- 依赖：宿主内网 Debian 的 docker 引擎与本地存储目录 `/var/lib/qdrant-storage`；embedding 向量化依赖同机 `rag-embedding`（bge-small-zh-v1.5，:8081）。
- 被依赖/关联系统：
  - RAG 知识库管线（kb-knowledge / kb-intelligence 等相关服务）通过内网 :6333 读写向量；
  - `rag-embedding` 与 `rag-qdrant` 同机部署，形成「向量化—入库—检索」闭环；
  - 与 MeiliSearch 互为补充：MeiliSearch 做关键词全文检索，Qdrant 做语义向量检索，RAG 常二者融合（hybrid）以提升召回。

## 运维要点

- 启停：`docker start/stop rag-qdrant`；容器无编排文件，`unless-stopped` 保活，宿主机重启后 Docker 自动拉起。
- 日志：`docker logs -f rag-qdrant`；obs-dozzle（内网 Deb :15888）按容器名检索。
- 数据与备份：bind 目录 `/var/lib/qdrant-storage`，备份直接对该目录做快照/同步（是否纳入统一备份以 infra-monitor 策略为准，待确认）；向量索引可由 embedding 服务重新全量重建。
- 可观测性：容器日志入 Loki（内网 Deb promtail），Grafana（内网 Deb :15300）可检索；Dashboard 自带基础统计。
- 安全：仅内网/Tailscale 可达，天然不暴露公网；若启用 API Key，凭证存 Vaultwarden；跨网访问必须走 Tailscale。

## 常见问题

- embedding 维度必须与集合创建时声明的一致（bge-small-zh-v1.5 为 512），否则写入/检索报维度不匹配。
- 容器为手工 `docker run` 启动、无编排文件，重建前先 `docker inspect` 留档参数，避免配置丢失。
- 向量库与 embedding 服务务必同模型版本，换模型需重建集合并重新向量化全量语料。
- embedding 服务首次需经代理拉取模型权重（172.17.0.1:7890），离线环境需预置 `/app/models`。

## 变更记录

- 2026-09-05 v2 补全设计/部署/使用三维度；实采确认无 compose 编排（docker run 手工启动），部署节改写为 docker run 参数清单
- 2026-09-05 v1 首次生成（portal 文档补全任务，AI 基于实采+源码生成）
