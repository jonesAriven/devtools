# mykng 知识库

> 基于 Spring Cloud 的私有化个人/团队知识库系统，提供文档管理、文件存储、全文搜索、网页收藏与智能知识抽取（实体图谱）能力，全端可通过浏览器访问。

## 基本信息

| 项 | 值 |
|----|----|
| 分类 | Web 系统（自研） |
| 版本 | Spring Boot 3.2.5 / Java 21 / MyBatis-Plus（微服务矩阵，README 标注） |
| 部署位置 | 主机 mykng-debain（192.168.31.105）；容器 kb-gateway / kb-auth / kb-file / kb-knowledge / kb-intelligence / kb-web（均在 mykng 宿主机 docker） |
| 源码位置 | 本地 `D:\huliang\java\ideaworkspace\devtools\mykng\`；mykng 机 `/root/devtools/mykng`（Gitee + GitHub 双推） |
| CI/CD | Woodpecker 流水线项目 `mykng`（woodScript 触发：`python woodScript/trigger-pipeline.py mykng --wait`） |
| 编排 | 应用层 `/root/kb-deploy/docker-compose.app.yml`（compose project：kb-app），前端层 `/root/kb-deploy/docker-compose.web.yml`（compose project：kb-web） |

## 访问入口

- 公网：`https://kb.marschat.online/kb/`（经腾讯云2号 nginx → mykng nginx :80）
- 内网：`http://192.168.31.105/kb/`（同机 80 端口，整站路径透传）
- Tailscale：`http://100.93.36.113/kb/`
- API 网关调试：`http://192.168.31.105:8090/kb/api/`（kb-gateway 宿主机映射端口）
- 入口重定向：访问 `https://main.marschat.online/` 或 `/kb` 会 302 跳转到 `/portal/`，知识库需显式走 `kb.marschat.online` 或 `/kb/` 路径

## 全链路

```
浏览器
  → https://kb.marschat.online (腾讯云2号 nginx :443, TLS 终止)
  → http://100.93.36.113:80 (mykng nginx)
       /kb/、/kb/s/        → 127.0.0.1:8091 (kb-web 前端容器)
       /kb/api/            → 127.0.0.1:8090 (kb-gateway)
  → kb-gateway 按路由转发到注册在 Nacos 的后端微服务：
       /kb/api/auth/*、/user/*、/token/*、/log/* → kb-auth
       /kb/api/file/*、/bucket/*                 → kb-file
       /kb/api/doc|folder|web|search|share|tag|space|trash|version/* → kb-knowledge
       /kb/api/intelligence/*                    → kb-intelligence
```

> 说明：外部统一只暴露 kb-gateway（8090）。各微服务内部端口（README 标注：kb-auth 8081 / kb-file 8082 / kb-knowledge 8083 / kb-intelligence 8086）经 Nacos 服务发现互访，宿主机另映射了 8085/8089/8092/8086 供调试，前端不直接访问。网关另有 `/v3/api-docs`、`/swagger-ui` 聚合路由（StripPrefix=3），可按服务查看 Swagger 文档。

## 系统设计

### 总体架构

Spring Cloud 微服务矩阵 + 前端 SPA，5 个后端服务 + 1 个前端 + 1 个公共模块：

| 服务 | 端口 | 职责 |
|------|------|------|
| kb-gateway | 8090（容器 8080） | API 网关：路由转发（Spring Cloud Gateway）、JWT 鉴权、限流、模块健康探测 |
| kb-auth | 8081 | 认证：登录/令牌签发刷新、用户管理、API Token、操作/请求/错误日志 |
| kb-file | 8082 | 文件：MinIO 对象存储、分块上传/合并、文本解析、回收站、桶统计 |
| kb-knowledge | 8083 | 知识：空间/文件夹/文档 CRUD、MeiliSearch 全文搜索、分享、版本、标签、网页收藏 |
| kb-intelligence | 8086 | 智能：知识导入/解析、实体抽取（hosts/services/ports/credentials/domains/commands/timelines）、语义检索 |
| kb-web | 8091（容器 80） | `nginx:alpine` 托管静态 SPA，挂载路径 `/kb/`、`/kb/s/` |
| kb-common | — | 公共模块（Maven 依赖，非独立服务）：统一工具/DTO/异常 |

服务注册发现走 Nacos（`platform-nacos:8848`），服务间调用 Feign 经 Nacos 路由（M5-1 起不再硬编码 URL）。

### 核心数据模型（init-sql 建表脚本）

kb-auth 库：
- `user`：用户账号
- `refresh_token`：刷新令牌
- `jwt_blacklist`：JWT 黑名单（登出/吊销后拦截）
- `ops_api_token`：API Token（供脚本/第三方调用）
- `operation_log`：操作日志

kb-file 库：
- `file`：文件元数据（对象存储索引）
- `file_chunk`：分块上传的分片记录
- `bucket`：文件桶（存储分区）

kb-knowledge 库：
- `space`：知识空间（顶层分类，private/team/public）
- `folder`：文件夹树（parent_id=0 为根）
- `doc`：文档
- `web_page`：网页收藏（抓取 URL 存为知识页）
- `tag` / `resource_tag`：标签与资源打标（resource_type: file/doc/web/folder）
- `share` / `share_access_log`：分享（UUID 分享码 + 4 位提取码 + 过期时间）与访问流水
- `version`：文档历史版本

kb-intelligence 库（kn_ 前缀实体图谱）：
- `kn_doc`：导入文档索引
- `kn_host` / `kn_service` / `kn_port` / `kn_credential` / `kn_domain` / `kn_dependency` / `kn_command` / `kn_timeline`：八类基础设施实体
- `kn_doc_entity_ref`：文档—实体关联

### 关键设计决策

1. **多存储分工**：结构化元数据走 MySQL（GR 集群多主），文件二进制走 MinIO（bucket `kb-file`），文档正文/非结构化内容走 MongoDB，全文检索走 MeiliSearch（索引 `kb_docs`/`kb_files`），Redis 承担缓存/会话 + 事件通道 `kb:events`（跨服务变更广播）。各取所长，避免单库既扛元数据又扛大文件与检索。
2. **网关统一入口 + Nacos 服务发现**：外部只暴露 kb-gateway 一个端口，路由按 `/kb/api/<域>/**` 前缀分流（StripPrefix=2），后端扩缩容/重启对前端透明。
3. **MySQL GR 多主 failover 连接串**：生产 `application-prod.yml` 硬编码 `jdbc:mysql://192.168.31.105:3306,192.168.31.182:3307,192.168.31.182:3308/...` 多主机 failover URL（`failOverReadOnly=false`），compose 里的 MYSQL_HOST 仅作降级 fallback，单点故障自动切主。
4. **服务可独立部署**：compose 无 depends_on，每个服务可单独 `up -d --force-recreate`，互不影响；前端容器层与应用层彻底分离，后端不可用时前端仅显示 502。

### 对外接口概览

全部经 kb-gateway 暴露（路由均相对 `/kb/api`，详细接口清单见"核心功能与使用"）：

- kb-auth：`/auth`（登录态）、`/user`（资料/密码）、`/token`（API Token）、`/log`（审计日志）
- kb-file：`/file`（上传/下载/解析/回收站）、`/bucket`（存储分区）
- kb-knowledge：`/doc`、`/folder`、`/space`、`/tag`、`/search`、`/share`、`/trash`、`/version`、`/web`
- kb-intelligence：`/intelligence/machine`（实体抽取/语义检索/统计）、`/intelligence/import`（批量导入）、`/internal`（实体全量接口）

## 部署与发布

### 编排与位置

- 应用层 compose：`/root/kb-deploy/docker-compose.app.yml`（服务 kb-gateway/kb-auth/kb-file/kb-knowledge/kb-intelligence/kb-ops/portal-server，compose project 默认按目录 kb-deploy）；本地材料包镜像 `material/composes/kb-app.yml`
- 前端层 compose：`/root/kb-deploy/docker-compose.web.yml`（kb-web/kb-ops-web/infra-monitor-web/portal-web，均为 nginx:alpine）；本地材料包镜像 `material/composes/kb-web.yml`
- 网络：应用层双网 `kb-app-net`（内部）+ `platform-net`（external，连全局基础设施）；前端层独立 `kb-web-net`
- 镜像：后端均 `build: /root/devtools/mykng/<服务>` 现场构建；前端直接用 `nginx:alpine` + 挂载 dist

### 配置清单（kb-app.yml）

| 容器 | 端口映射 | 关键环境变量（只列名） |
|------|---------|----------------------|
| kb-gateway | 8090→8080 | SPRING_PROFILES_ACTIVE=prod,kafka-log、NACOS_*、JWT_SECRET |
| kb-auth | 8085→8085 | NACOS_*、JWT_SECRET、MYSQL_*（已废弃仅 fallback）、REDIS_* |
| kb-file | 8089→8089 | NACOS_*、MYSQL_*（fallback）、REDIS_*、MINIO_ENDPOINT/ACCESS_KEY/SECRET_KEY |
| kb-knowledge | 8092→8092 | NACOS_*、MYSQL_*（fallback）、REDIS_*、MONGO_*、MEILI_HOST/KEY |
| kb-intelligence | 8086→8086 | 同 kb-knowledge 一组（Xmx512m、mem_limit 640m） |

- 资源限制：后端 mem_limit 384m（kb-intelligence 640m），JVM -Xms128m -Xmx256m（kb-intelligence 512m）+ G1GC
- 密钥来源：compose `${VAR:-默认}` 读取同目录 `.env`；真实账密见 Vaultwarden（vault.marschat.online）或 infrastructure-map 技能
- 前端挂载：`${DEPLOY_BASE:-/root/kb-deploy}/kb-web/dist → /usr/share/nginx/html:ro`、同目录 `nginx.conf → /etc/nginx/conf.d/default.conf:ro`

### 发布/升级

走 Woodpecker 流水线：

```bash
python woodScript/trigger-pipeline.py mykng --wait
```

链路：CI 构建 → 产物推 `/mnt/shared/woodScript/publish/` → drone-ssh 到 mykng 执行 deploy-*.sh 重建容器 + 健康检查（24×10s）。

手动部署（仓库内）：

```bash
bash scripts/deploy.sh build   # 构建
bash scripts/deploy.sh up      # 启动
bash scripts/health-check.sh   # 健康检查
```

单服务重建（compose 隔离性设计）：

```bash
docker compose -f /root/kb-deploy/docker-compose.app.yml up -d --force-recreate kb-gateway kb-auth kb-file kb-knowledge kb-intelligence
```

### 回滚

- `scripts/rollback.sh`（仓库内回滚脚本）
- compose 重建到旧镜像/旧产物：产物目录回退后 `up -d --force-recreate` 对应服务

## 核心功能与使用

mykng 由 5 个微服务 + 1 个前端组成，能力按模块划分（以下路由均相对 `/kb/api`）：

### kb-gateway（API 网关 · 端口 8090）
- 统一入口、JWT 鉴权、限流。
- `GET /kb/api/system/modules`、`/kb/api/system/modules/{name}`：模块健康/状态探测（前端启动自检用）。

### kb-auth（认证服务）
- `POST /auth/login`、`/auth/logout`、`/auth/refresh`、`GET /auth/me`：登录态与令牌管理。
- `GET /user/profile`、`/user/list`、`PUT /user/profile`、`PUT /user/password`：当前用户资料、密码修改。
- `POST /token`、`GET /token`、`DELETE /token/{id}`、`PUT /token/{id}/toggle`、`POST /token/verify`：API Token 的签发/列表/吊销/启用停用/校验（供脚本/第三方调用知识库接口）。
- `GET /auth/log`、`GET /auth/request-log`、`POST /auth/error-log/report`：操作日志、请求日志、前端错误日志上报（运维审计用）。

### kb-file（文件服务 · MinIO 存储）
- `POST /file/upload`（分块上传）、`POST /file/merge`（合并）、`GET /file/list`、`GET /file/{id}`、`GET /file/{id}/download`、`/download-stream`、`/content`（读写）、`PUT /file/{id}/content`。
- `POST /file/{id}/reparse`：重新解析（全文索引构建）；`POST /file/rebuild-index`：重建全量索引。
- `PUT /file/{id}/star`、`PUT /file/{id}/move`、`GET /file/trash`、`PUT /file/{id}/restore`、`DELETE /file/{id}/permanent`、`DELETE /file/trash/empty`：星标、移动、回收站与清空。
- `GET /bucket/list`、`GET /bucket/{id}/stats`：文件桶（存储分区）列表与统计。

### kb-knowledge（知识服务）
- `POST /doc`、`GET /doc/list`、`GET /doc/{id}`、`PUT /doc/{id}`、`DELETE /doc/{id}`、`PUT /doc/{id}/star`、`PUT /doc/{id}/move`、`GET /doc/{id}/versions`：文档 CRUD、星标、移动、历史版本。
- `POST /folder`、`GET /folder/tree/{spaceId}`、`/tree-with-resources/{spaceId}`、`PUT /folder/{id}/move`、`/sort`、`DELETE /folder/{id}`：知识空间内的文件夹树（含资源树）。
- `GET /space/list`、`POST /space`、`PUT /space/{id}`、`DELETE /space/{id}`：知识空间（顶层分类）管理。
- `GET /tag/list`、`/stats`、`POST /tag`、`POST /tag/bind`、`/unbind`：标签与资源打标。
- `GET /search`、`/search/suggest`、`/search/starred`：全文搜索（底层 MeiliSearch）、联想、星标检索。
- `POST /share`、`GET /share/list`、`/my`、`/verify/{code}`、`/detail/{code}`、`DELETE /share/{id}`：文档分享（生成分享码、凭码访问）。
- `GET /trash/list`、`POST /trash/restore/{type}/{id}`、`DELETE /trash/{type}/{id}`、`DELETE /trash/empty`：知识库级回收站。
- `GET /version/list`、`POST /version/{id}/rollback`：版本列表与回滚。
- `POST /web/collect`、`GET /web/list`、`/refetch`、`PUT /web/{id}/move`、`/star`：网页收藏（抓取 URL 存为知识页）。

### kb-intelligence（智能服务）
- `GET /intelligence/machine/docs`（文档索引列表）、`/docs/{docId}/meta`、`/content`、`/entities`（抽取实体：hosts / services / commands / timelines / ports / credentials / domains）。
- `POST /intelligence/machine/search`：语义/全文混合检索；`GET /intelligence/machine/stats`：知识图谱统计。
- `/internal/*`（hosts / services / ports / credentials / domains / dependencies）：内部实体全量接口（供其他系统拉取基础设施台账）。
- `POST /intelligence/import/path`、`GET /intelligence/import/status`：按本地路径批量导入并解析文档进知识库。

### kb-web（前端）
- `nginx:alpine` 托管静态 SPA，挂载路径 `/kb/`、`/kb/s/`，调用 `/kb/api/` 网关。

### 典型操作路径

- 日常使用：浏览器打开 `https://kb.marschat.online/kb/` → 登录 → 空间/文件夹内新建文档或上传文件 → 编辑/星标/搜索。
- 分享协作：选中文档 → 生成分享（分享码 + 可选提取码/过期时间）→ 把 `kb.marschat.online/kb/s/<code>` 链接发给对方。
- 全文索引修复：登录后（或脚本带 API Token）调 `POST /kb/api/file/rebuild-index` → 确认 MeiliSearch 容器健康。
- 第三方接入：kb-auth 登录 → 签发 API Token → 脚本以 `Authorization` 头调用网关接口。

## 依赖与关联

- 存储/中间件（均位于 mykng 宿主的 platform 层容器，MySQL 为 GR 集群）：
  - MySQL（`platform-mysql-1` 3306，GR 多主，105:3306 / 182:3307 / 182:3308）—— 主业务库
  - Redis 7（`platform-redis` 6379）—— 缓存 / 会话 / 事件通道 kb:events
  - MongoDB 7（`platform-mongo` 27017）—— 文档/非结构化内容
  - MinIO（`platform-minio` 9000/19001）—— 文件对象存储（bucket kb-file）
  - MeiliSearch（`platform-meilisearch` 7700）—— 全文搜索索引（kb_docs / kb_files）
  - Nacos（`platform-nacos` 8848）—— 服务注册发现（实际版本 v2.4.3）
  - Kafka（`platform-kafka` 9092）—— 异步任务（导入/解析）消息（profile kafka-log）
- 关联系统：kb-ops 运维管理（同源微服务，共用 Nacos/MySQL）；portal 工具看板将本系统登记为 id=1；infra-monitor / Dozzle 可观测其容器日志。

## 运维要点

- 启停：`bash scripts/deploy.sh up` / `down`（仓库内 docker-compose）；或走 Woodpecker `mykng` 流水线（见"部署与发布"）。
- 健康检查：`curl http://192.168.31.105:8090/kb/api/system/modules` 应返回 200 与各模块状态。
- 日志：`docker logs kb-gateway` 等，`obs-dozzle`（mykng 侧 :15500）看实时日志，Grafana/Loki 聚合。
- 数据与备份：业务数据在 MySQL GR（105:3306 主，182:3307/3308 从），落地有 GR 多副本；MinIO 文件、MongoDB 需纳入备份体系（待确认是否已在定期备份任务中）。
- 常见问题：
  - 前端访问 302 跳 `/portal/`：知识库路径是 `/kb/`，不要走 `main.marschat.online` 根路径。
  - 文件解析/搜索为空：先 `POST /file/rebuild-index` 重建索引，再确认 MeiliSearch 容器健康。
  - 令牌调用失败：用 `/token/verify` 校验 API Token 状态（是否被禁用）。

## 变更记录

- 2026-09-05 v2 补全设计/部署/使用三维度（新增系统设计：架构/数据模型/设计决策/接口概览；新增部署与发布：compose 位置/配置清单/流水线与回滚；新增典型操作路径；表用途与索引名读 init-sql 与源码实采）
- 2026-09-05 v1 首次生成（portal 文档补全任务，AI 基于实采 docker ps + 本地源码 Controller 路由 + README 生成）
