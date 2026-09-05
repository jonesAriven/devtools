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
       /kb/api/auth/*      → kb-auth
       /kb/api/file/*      → kb-file
       /kb/api/knowledge/* → kb-knowledge（注：各 Controller 的 @RequestMapping 前缀为 /doc、/folder、/space、/tag、/search、/share、/trash、/version、/web，统一挂在 /kb/api 下）
       /kb/api/intelligence/* → kb-intelligence
```

> 说明：外部统一只暴露 kb-gateway（8090）。各微服务内部端口（README 标注：kb-auth 8081 / kb-file 8082 / kb-knowledge 8083 / kb-intelligence 8086）经 Nacos 服务发现互访，宿主机另映射了 8085/8089/8092/8086 供调试，前端不直接访问。

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

## 依赖与关联

- 存储/中间件（均位于 mykng 宿主的 platform 层容器，MySQL 为 GR 集群）：
  - MySQL（`platform-mysql-1` 3306，GR 多主，库名 `tools` 之外的知识库业务库）—— 主业务库
  - Redis 7（`platform-redis` 6379）—— 缓存 / 会话
  - MongoDB 7（`platform-mongo` 27017）—— 文档/非结构化内容
  - MinIO（`platform-minio` 9000/19001）—— 文件对象存储
  - MeiliSearch（`platform-meilisearch` 7700）—— 全文搜索索引
  - Nacos（`platform-nacos` 8848）—— 服务注册发现（实际版本 v2.4.3）
  - Kafka（`platform-kafka` 9092）—— 异步任务（导入/解析）消息
- 关联系统：kb-ops 运维管理（同源微服务，共用 Nacos/MySQL）；portal 工具看板将本系统登记为 id=1；infra-monitor / Dozzle 可观测其容器日志。

## 运维要点

- 启停：`bash scripts/deploy.sh up` / `down`（仓库内 docker-compose）；或走 Woodpecker `mykng` 流水线（build → 产物推 `/mnt/shared/woodScript/publish/` → drone-ssh 到 mykng 执行 cd/deploy-*.sh 重建容器 + 健康检查 24×10s）。
- 健康检查：`curl http://192.168.31.105:8090/kb/api/system/modules` 应返回 200 与各模块状态。
- 日志：`docker logs kb-gateway` 等，`obs-dozzle`（mykng 侧 :15500）看实时日志，Grafana/Loki 聚合。
- 数据与备份：业务数据在 MySQL GR（105:3306 主，182:3307/3308 从），落地有 GR 多副本；MinIO 文件、MongoDB 需纳入备份体系（待确认是否已在定期备份任务中）。
- 常见问题：
  - 前端访问 302 跳 `/portal/`：知识库路径是 `/kb/`，不要走 `main.marschat.online` 根路径。
  - 文件解析/搜索为空：先 `POST /file/rebuild-index` 重建索引，再确认 MeiliSearch 容器健康。
  - 令牌调用失败：用 `/token/verify` 校验 API Token 状态（是否被禁用）。

## 变更记录

- 2026-09-05 首次生成（portal 文档补全任务，AI 基于实采 docker ps + 本地源码 Controller 路由 + README 生成）
