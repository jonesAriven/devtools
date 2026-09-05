# MinIO 对象存储

> 兼容 S3 协议的自托管对象存储，为知识库（KB）体系提供文件、头像、附件等二进制对象的统一存储后端；对外暴露 S3 兼容 API 供各业务服务读写，对外暴露 Web 控制台供运维管理桶与对象。

## 基本信息

| 项 | 值 |
|----|----|
| 分类 | 基础设施（存储） |
| 版本 | minio/minio:latest（部署时拉取的最新版，非固定版本号） |
| 部署位置 | mykng（192.168.31.105）容器 `platform-minio`，compose project `platform` |
| 端口 | API 9000 / 控制台 19001（宿主机映射 9000、19001；容器内 API 9000、控制台 9001） |
| 数据卷 | 命名卷 `platform_platform-minio-data` → 容器内 `/data` |
| 健康状态 | healthy（docker healthcheck 通过） |
| 源码位置 | 开源组件，官方仓库 https://github.com/minio/minio（自部署，无自研源码） |
| CI/CD | 无独立流水线（自部署，随 platform 层 `python woodScript/trigger-pipeline.py platform` 一并拉起） |

## 访问入口

- 公网控制台：`https://kb.marschat.online/minio/`（经 mykng nginx 反代）
- 公网 S3 API：`https://s3.marschat.online`（S3 兼容端点，供程序调用，不供人浏览）
- 内网：`http://192.168.31.105:9000`（API）、`http://192.168.31.105:19001`（控制台）
- Tailscale：`http://100.93.36.113:9000`、`http://100.93.36.113:19001`
- 账密：AccessKey/SecretKey 见 Vaultwarden（vault.marschat.online）或 infrastructure-map 技能（禁止明文落盘）

## 全链路

控制台链路：
```
浏览器 → https://kb.marschat.online/minio/
  → 腾讯云2号 nginx (443, TLS 终止) → http://100.93.36.113:80
  → mykng nginx /minio/ → proxy_pass http://127.0.0.1:19001/
  → 容器 platform-minio 控制台 (容器内 :9001，宿主映射 19001)
```

S3 API 链路：
```
程序/服务 → https://s3.marschat.online
  → 腾讯云2号 nginx (443, TLS 终止) → proxy_pass http://100.93.36.113:9000
  → 容器 platform-minio API (:9000)
```

> 注：控制台（19001/容器内 9001）与 API（9000）是 MinIO 两个独立端口。控制台用于人工管理桶与对象；API（9000）是 S3 兼容数据面，业务服务用 AccessKey/SecretKey 调用，不是给人点的网页。mykng nginx 的 `minio.conf` 配置为 `location /minio/ { proxy_pass http://127.0.0.1:19001/; }`（尾斜杠已对齐）。

## 系统设计

### 组件架构

MinIO 是高性能、S3 兼容的开源对象存储：数据面提供 AWS S3 兼容 REST API（桶/对象/预签名 URL/生命周期策略），管理面提供独立 Web Console；单节点部署时对象与元数据统一存于数据目录，无需外部数据库。API 与 Console 是两个独立监听端口（本实例 9000 / 9001）。

### 我们的集成设计

- **实例角色**：`platform-minio` 是 platform 全局基础设施层的对象存储底座，承担 KB 体系全部二进制对象（知识库原始文件、用户头像、上传附件）的持久化存储；对象与元数据自管于 `/data`，不依赖外部数据库。
- **谁读写它**：
  - `kb-file`（文件服务）、`kb-knowledge`（知识库，8092）经 S3 兼容 API 写入/读取对象；
  - `kb-web` / `kb.marschat.online` 前端展示的图片附件最终取自 MinIO（通常经预签名 URL）；
  - 外部脚本/客户端可经公网 `s3.marschat.online` 用 AccessKey/SecretKey 访问。
- **为什么选它**：S3 协议是对象存储事实标准，业务侧用任意 S3 SDK 即可接入、未来可平滑迁移到云上 S3；相比直接落盘 NAS，桶级策略、预签名 URL、生命周期管理开箱即用。
- **与其他存储的分工**：与 MeiliSearch、Qdrant 构成 KB 的非结构化数据底座——MinIO 存原始文件、MeiliSearch 存全文检索索引、Qdrant 存语义向量，三者职责不重叠（见「依赖与关联」关系表）。
- **关键配置思路**：
  - 根凭证**通过文件注入**（`MINIO_ROOT_USER_FILE=access_key`、`MINIO_ROOT_PASSWORD_FILE=secret_key`、`MINIO_CONFIG_ENV_FILE=config.env`），凭证值不进 compose ENV 明文（实采 docker inspect 确认，2026-09-05）；
  - 数据目录用命名卷持久化，healthcheck 走 `/minio/health/live`；
  - 内存限额 512m，控制台 `--console-address :9001` 独立端口，便于 nginx 只反代控制台而不暴露数据面。

## 部署与发布

### 编排与位置

- compose 文件：`/root/devtools/platform/docker-compose.platform.yml`（compose project：`platform`，服务名 `platform-minio`）
- 网络：`platform-net`（bridge；其他层项目经 external 引入该网络互访）
- 启动脚本：`bash platform/start-platform.sh`（即 compose up -d）
- 归属：platform 中间件层**不归任何流水线管理**，手动启动、持久运行；mykng/kb-ops/infra-monitor 等项目共享此基础设施

### 配置清单

| 项 | 值 |
|----|----|
| 容器名 | `platform-minio` |
| 镜像 | `minio/minio:latest` |
| 端口映射 | 宿主 9000 → 容器 9000（S3 API）；宿主 19001 → 容器 9001（Console） |
| 卷挂载 | 命名卷 `platform-minio-data` → `/data`（对象持久化） |
| 环境变量 | `MINIO_ROOT_USER` / `MINIO_ROOT_PASSWORD`（根凭证；实际部署经文件注入，值见 Vaultwarden，禁止落盘） |
| 启动命令 | `server /data --console-address ":9001"` |
| healthcheck | `curl -f http://localhost:9000/minio/health/live`，间隔 10s |
| 资源限制 | mem_limit 512m |
| 重启策略 | `unless-stopped` |

### 发布/升级

- 常规拉起：`python woodScript/trigger-pipeline.py platform`（platform 层流水线），或在 mykng 上手工：
  `docker compose -p platform -f /root/devtools/platform/docker-compose.platform.yml up -d platform-minio`
- 停止：`docker compose -p platform -f /root/devtools/platform/docker-compose.platform.yml down`
- 镜像 tag 为 `latest`，升级 = 重新 pull 后重建容器；升级前确认 KB 服务 SDK 对新版本无破坏性依赖。

### 回滚

- 单容器回滚：改用旧镜像 ID `docker compose ... up -d platform-minio`（latest 无固定版本锚点，建议升级前 `docker tag` 留存旧镜像）。
- 数据回退：对象数据在命名卷 `platform_platform-minio-data`，恢复即还原该卷快照。

## 核心功能与使用

### 功能清单

- **对象存储桶管理**：控制台创建/删除桶、设置公开或私有访问策略。KB 体系的用户头像、上传附件、知识库文件存放在此。
- **S3 兼容 API**：任何兼容 AWS S3 SDK 的客户端（kb-file、kb-knowledge 及外部脚本）用同一套 AccessKey/SecretKey 读写对象。
- **预签名 URL**：业务侧对私有对象签发有时效的预签名 URL 供前端临时下载，避免把 SecretKey 下发到客户端。
- **控制台运维**：查看桶内对象、下载、预览、设置生命周期与配额（按钮级操作以控制台实际界面为准）。
- **多租户隔离**：通过不同前缀/不同桶区分各业务系统的对象空间（具体桶命名以 KB 服务配置为准，待确认）。

### 典型操作路径

1. **控制台管理**：浏览器打开 `https://kb.marschat.online/minio/`（或内网 `:19001`）→ 用 AccessKey/SecretKey 登录 → Object Browser 选桶 → 上传/下载/查看对象。
2. **命令行 mc**：`mc alias set mykng https://s3.marschat.online <ACCESS_KEY> <SECRET_KEY>` → `mc ls mykng/<bucket>` / `mc mirror`（备份同步常用）。
3. **程序调用（boto3 为例）**：endpoint_url 指向 `https://s3.marschat.online`，用 AccessKey/SecretKey 初始化 client → `put_object` / `list_objects_v2` / `generate_presigned_url`。
4. **健康探测**：`curl http://192.168.31.105:9000/minio/health/live`（HTTP 200 即健康）。

## 依赖与关联

- 依赖：宿主 mykng 的 docker 引擎与数据卷；无外部数据库依赖（元数据自管于 `/data`）。
- 被依赖/关联系统：
  - `kb-file`（文件服务）、`kb-knowledge`（知识库，8092）等 KB 服务经 S3 API 读写对象；
  - 知识库前端 `kb-web` / `kb.marschat.online` 展示的图片附件等多源自 MinIO；
  - `s3.marschat.online` 公网域名专供外部 S3 客户端访问；
  - 与 MeiliSearch（检索）、Qdrant（向量）共同构成 KB 的非结构化数据底座，三者职责不同。

### 与其他存储/中间件的关系

| 系统 | 存什么 | 访问方 |
|------|--------|--------|
| MinIO | 原始文件/附件/头像 | kb-file、kb-knowledge、外部 S3 客户端 |
| MeiliSearch | 全文检索索引 | kb-gateway、kb-knowledge |
| Qdrant | 文本向量 | RAG 管线、rag-embedding |
| platform-redis | 缓存/会话/队列 | KB、portal、activecode |
| platform-mongo | 文档数据 | 使用 Mongo 的业务服务 |
| platform-kafka | 异步消息流 | 使用 Kafka 的业务服务 |

## 运维要点

- 启停：随 platform 中间件栈管理（见「部署与发布」）；单服务重建 `docker compose -p platform -f /root/devtools/platform/docker-compose.platform.yml up -d platform-minio`。
- 日志：`docker logs -f platform-minio`；亦可在 obs-dozzle（mykng :15500）按容器名检索。
- 数据与备份：数据卷 `platform_platform-minio-data` → 容器内 `/data`。备份需对该卷做快照/同步（当前备份体系是否覆盖以 infra-monitor 备份策略为准，待确认）。建议定期 `mc mirror` 到异地。
- 可观测性：容器日志入 Loki（mykng promtail → 内网 Deb obs-loki :15100），可在 Grafana（内网 Deb :15300）检索；控制台访问量可结合 nginx 日志观察。
- 扩容：单节点 MinIO，容量受 mykng 磁盘限制；如需分布式需在 compose 增加多节点（当前非分布式，待确认是否有扩容计划）。

## 常见问题

- 控制台 19001 与 API 9000 别混淆——配 SDK 用 9000 的 AccessKey，别把控制台地址当 API 端点。
- 公网 S3 域名是 `s3.marschat.online`（指向 :9000），控制台是 `kb.marschat.online/minio/`（指向 :19001），二者不可互换。
- 控制台经公网暴露，务必保证 AccessKey 强度并定期轮换，凭证统一存 Vaultwarden。
- 单节点部署无纠删码，磁盘故障会导致对象丢失，重要数据务必纳入备份。

## 变更记录

- 2026-09-05 v2 补全设计/部署/使用三维度（新增系统设计、部署与发布节，compose/配置细节引自 platform.yml 原文与 docker inspect 实采）
- 2026-09-05 v1 首次生成（portal 文档补全任务，AI 基于实采+源码生成）
