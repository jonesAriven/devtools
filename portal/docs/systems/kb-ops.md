# kb-ops 运维管理

> 面向 devtools 全栈运维人员的 CMDB + 运维知识平台：集中管理主机、服务、域名、端口、凭据、部署记录与运维知识，并具备矛盾检测、看板快照与从 kb-intelligence 同步的能力。前端 https://kb.marschat.online/ops/，后端 API 走 `/ops-api/`。

## 基本信息

| 项 | 值 |
|----|----|
| 分类 | 自研 Web 系统（运维平台） |
| 版本 | 1.0.0（后端 Spring Boot 3.2.5 / Java 21；前端 Vue3 + Vite 0.1.0） |
| 部署位置 | 主机 mykng（192.168.31.105），容器 `kb-ops`（后端 8084）+ `kb-ops-web`（前端 nginx，宿主机 8093） |
| 源码位置 | 后端 `D:\huliang\java\ideaworkspace\devtools\kb-ops\`（groupId com.devtools），前端 `kb-ops/kb-ops-web\`；mykng 上 `/root/devtools/kb-ops` |
| CI/CD | Woodpecker 项目 `kb-ops` + `kb-ops-web`（统一 `.woodpecker.yml`，`kb-app` / `kb-web` compose） |
| 技术栈 | 后端：Spring Boot 3.2.5、Java 21、MyBatis-Plus 3.5.6、Redis、OpenFeign、JJWT 0.12.5、Actuator；前端：Vue 3.4、Element Plus 2.6、Pinia、ECharts 5.5、Vite |

## 访问入口

- 公网（前端 SPA）：`https://kb.marschat.online/ops/`
- 公网（后端 API）：`https://kb.marschat.online/ops-api/`（反代到 `kb-ops:8084/kb-ops/`）
- 公网（认证 API）：`https://kb.marschat.online/ops/auth-api/`（转发到 `kb-gateway:8090/kb/api/auth/`，复用知识库统一登录）
- 内网（前端）：`http://192.168.31.105:8093/`
- 内网（后端）：`http://192.168.31.105:8084/kb-ops/`
- Tailscale：`http://100.93.36.113:8093/`（前端）、`http://100.93.36.113:8084/kb-ops/`（后端）
- 健康检查：`http://192.168.31.105:8084/kb-ops/actuator/health`

## 全链路

```
前端 SPA:
域名 ops → 腾讯云2号 nginx(443) → mykng nginx(:80) /ops/ → alias /var/www/kb-ops-web (nginx 静态)

后端 API:
域名 ops-api → 腾讯云2号 nginx(443) → mykng nginx(:80) /ops-api/ → 127.0.0.1:8084/kb-ops/

登录鉴权（无独立登录页，复用知识库账号体系）:
域名 ops/auth-api → mykng nginx(:80) /ops/auth-api/ → kb-gateway:8090 /kb/api/auth/
  → kb-auth 校验后下发 JWT；kb-ops 通过 JwtAuthenticationFilter + OpenFeign 透传校验
```

说明：kb-ops 自身 `SecurityConfig` 全路由 `authenticated()`，由 `JwtAuthenticationFilter` 校验 JWT；它不维护用户体系，账号/角色由 `kb-auth` 统一托管，登录入口在知识库登录页。

## 系统设计

### 总体架构

单体前后端分离架构（刻意不拆微服务——运维平台自身追求简单可维护）：

- **后端（kb-ops）**：Spring Boot 单模块（Java 21），包结构 `com.kb.ops` 下分 `controller / service / mapper / entity / dto / config / security / util`，公共层复用 `com.kb.common`（统一 `Result` 返回体、分页 `page`、异常 `exception`、`trace` 链路标记、加密工具）。数据访问 MyBatis-Plus（mapper-locations `classpath*:/mapper/**/*.xml`），登录鉴权不自带而是复用 kb-auth。
- **前端（kb-ops-web）**：Vue 3.4 + Element Plus 2.6 + Pinia 2.1 + ECharts 5.5 + Vite，按后端模块组织页面（主机/服务/域名/端口/凭据/部署/知识/矛盾/看板），看板用 ECharts 渲染指标。
- **外部协同**：通过 OpenFeign 调 kb-auth（JWT 校验，`kb.feign.auth-url`）与 kb-intelligence（资产同步，`kb.feign.intelligence-url`，容器内默认 `http://kb-intelligence:8086`）。
- **可观测**：Actuator 暴露 `health,info`（`show-details: always`）；logback 经 logstash-logback-encoder + kafka-appender 推结构化日志（profile `kafka-log`）。

### 核心数据模型

MySQL 库 `kb_ops`，初始化脚本 `src/main/resources/sql/kb_ops_init.sql`（11 张表，实体注解 `@TableName` 与之一一对应）：

| 表名 | 用途 |
|------|------|
| `ops_host` | 运维主机表：IP、系统、角色、Tailscale IP、挂载点等资产台账 |
| `ops_service` | 运维服务表：访问地址、技术栈、健康检查 URL、启用状态 |
| `ops_domain` | 域名管理表：对外域名与反代路径映射 |
| `ops_port` | 端口管理表：主机端口占用登记，防冲突 |
| `ops_dependency` | 服务依赖关系表：服务/组件间依赖，用于影响面分析 |
| `ops_credential` | 凭据管理表：账号/Token，密码字段 AES 加密落库 |
| `ops_change_log` | 部署/变更记录表（实体 `DeploymentRecord`） |
| `ops_knowledge` | 运维知识表：排障手册/经验，支持分类与检索 |
| `ops_conflict` | 矛盾检测结果表：端口冲突、依赖缺失等 |
| `ops_snapshot` | 运维看板快照表：Dashboard 指标的历史快照 |
| `operation_log` | 操作日志表：接口操作审计 |

### 关键设计决策

1. **鉴权外挂而非重建**：不建用户表，JWT 由 kb-auth 签发、kb-ops 只做过滤器校验——运维平台与知识库共用一套账号，避免多套密码。认证流量单独走 `/ops/auth-api/` 反代到网关，与业务 API 分路径。
2. **统一资产模型 + 矛盾检测闭环**：主机/服务/域名/端口/依赖五类台账横向关联，`ConflictController /ops/conflict/detect` 扫描"端口冲突、依赖缺失、配置不一致"，列表展示并 `/{id}/resolve` 标记解决——形成"登记 → 检测 → 解决"闭环。
3. **凭据加密存储**：密码/Token 经 `CRYPTO_AES_KEY`（AES）加密后才落 `ops_credential`，接口按权限返回脱敏值；明文永不入文档。
4. **部署记录对齐流水线**：`ops_change_log` 的记录与 Woodpecker 部署一一对应，`/recent` 看板直接展示最近变更；操作行为另落 `operation_log` 审计。
5. **GR 集群直连容灾**：`application-prod.yml` 连接串为三节点随机负载均衡 failover URL（`loadBalanceStrategy=random&failOverReadOnly=false&retriesAllDown=3`），任一节点宕机自动切换。

### 对外接口概览

所有 Controller 位于 `com.kb.ops.controller`，路由统一前缀 `/ops/*`（容器 context `/kb-ops/`）：

| 分组 | 前缀 | 功能 |
|------|------|------|
| HostController | `/ops/host` | 主机台账 CRUD |
| ServiceController | `/ops/service` | 服务台账 CRUD |
| DomainController | `/ops/domain` | 域名映射 CRUD |
| PortController | `/ops/port` | 端口占用 CRUD |
| DependencyController | `/ops/dependency` | 依赖关系 CRUD |
| CredentialController | `/ops/credential` | 凭据 CRUD（加密） |
| DeploymentController | `/ops/deployment` | 部署记录，`/recent` 最近记录 |
| KnowledgeController | `/ops/knowledge` | 运维知识 CRUD/检索 |
| ConflictController | `/ops/conflict` | `/detect` 矛盾检测、`/{id}/resolve` 标记解决 |
| DashboardController | `/ops/dashboard` | 指标汇总、`/snapshot/refresh` 快照刷新 |
| ImportController | `/ops/import` | `/csv` 批量导入资产 |
| SyncController | `/ops/sync` | `/from-intelligence` 从 kb-intelligence 同步资产 |
| OperationLogController | `/ops/log` | 操作审计日志 |

## 部署与发布

### 编排与位置

- 后端：容器 `kb-ops`，镜像由 `build: /root/devtools/kb-ops`（Dockerfile 在源码根）构建，归属 compose project `kb-app`，compose 文件 `/root/devtools/docker/docker-compose.app.yml`（`service: kb-ops`）。
- 前端：容器 `kb-ops-web`（nginx:alpine 静态服务），归属 compose project `kb-web`，compose 文件 `/root/devtools/docker/docker-compose.web.yml`。
- 两个 compose project 互相隔离：`kb-app.yml` 中 kb-ops 无 depends_on，可独立 `up -d --force-recreate kb-ops`，不影响 mykng 五微服务。
- 前端产物落点：流水线把 pnpm 构建产物解压到目标机 `/root/kb-deploy/kb-ops-web/dist`，nginx 容器只读挂载——前端"发布"实质是换 dist 文件 + 重载 nginx。

### 配置清单

**kb-ops（后端容器）**

- 端口映射：`8084:8084`
- 网络：`kb-app-net`（应用层内部）+ `platform-net`（连全局基础设施）
- 卷挂载：无持久卷（数据全在 MySQL/Redis）
- 关键环境变量（值见 Vaultwarden / mykng `.env`，不在文档落盘）：
  - `SPRING_PROFILES_ACTIVE=prod`（附加 kafka-log 日志 profile）、`JAVA_OPTS`（JVM 内存/GC）
  - `MYSQL_DATABASE=kb_ops`、`MYSQL_USER`、`MYSQL_PASSWORD`（注意：`MYSQL_HOST/PORT` 已废弃，生产连接串为 GR 集群三节点 failover URL，硬编码在 `application-prod.yml`）
  - `REDIS_HOST=platform-redis`、`REDIS_PORT=6379`
  - `CRYPTO_AES_KEY`（凭据 AES 加密密钥）
  - `KB_GATEWAY_URL=http://kb-gateway:8080`
- 内存限制：`mem_limit: 384m`，`restart: on-failure:5`

**kb-ops-web（前端容器）**

- 端口映射：`8093:80`
- 卷挂载（宿主→容器，只读）：
  - `/root/kb-deploy/kb-ops-web/dist` → `/usr/share/nginx/html`
  - `/root/kb-deploy/kb-ops-web/nginx.conf` → `/etc/nginx/conf.d/default.conf`
- 内存限制：`64m`

### 发布/升级（只允许走流水线）

```bash
python woodScript/trigger-pipeline.py kb-ops        # 后端：CI Maven 构建 → drone-ssh → deploy-kb-ops.sh
python woodScript/trigger-pipeline.py kb-ops-web    # 前端：CI pnpm 构建 → drone-ssh → deploy-kb-ops-web.sh
python woodScript/trigger-pipeline.py kb-ops --note "修复登录bug"
```

链路：push/手动触发 → Woodpecker（`.woodpecker.yml` 的 `kb-ops-build` / `kb-ops-deploy` 步骤）→ 产物 `/mnt/shared/woodScript/publish/kb-ops-latest.tar.gz` → `appleboy/drone-ssh` 携 `ssh_key_mykng` Secret 登录 mykng → `cd/deploy-kb-ops.sh`（kb-app project 重建 + 健康检查 `localhost:8084/kb-ops/actuator/health`，最多 24×10s）。前端同理，健康检查 `localhost:8093/health`。

### 回滚

- 产物回退：`/mnt/shared/woodScript/publish/` 保留最近产物，重新解压旧产物后 `docker compose -p kb-app up -d --force-recreate kb-ops`（实际操作见 `lib-deploy.sh` 原语）。
- 数据库回退：变更前对 `kb_ops` 库 `mysqldump`（GR 集群 Node1 执行），异常时回导。

## 核心功能与使用

### 功能清单

- **CMDB 台账**：主机/服务/域名/端口/依赖五类资产集中登记与查询——任何部署前先查台账防冲突。
- **矛盾检测**：一键扫描端口冲突、依赖缺失、配置不一致——新服务上线前跑一遍。
- **凭据保管**：各系统账号/Token 加密存档——查账密入口（配合 Vaultwarden）。
- **部署记录**：每次流水线发布的时间/目标/备注留痕——追溯"谁在什么时候改了什么"。
- **运维知识库**：排障手册结构化沉淀、全文检索——同类故障二次发生时先查知识。
- **看板快照**：主机数/服务数/未解决矛盾/近期部署一屏总览。

### 典型操作路径

1. **登记一台新主机**：登录知识库获取 JWT → 进入 `https://kb.marschat.online/ops/` 主机管理 → 新增（填 IP/系统/角色/Tailscale IP）→ 保存。
2. **上线新服务前查冲突**：先在"服务管理/端口管理"登记拟用端口 → 矛盾检测页点"检测" → 无未解决矛盾再触发流水线部署。
3. **记录一次部署**：流水线发布完成后 → 部署记录页新增（或 `/recent` 核对自动记录）→ 填目标与备注。
4. **沉淀排障经验**：故障解决后 → 运维知识页新增，按分类归档，写明现象/根因/处置步骤。
5. **从知识库同步资产**：`POST /ops/sync/from-intelligence`（可传 override/entityTypes）→ 拉取 kb-intelligence 资产/拓扑补全 CMDB。

## 依赖与关联

- 依赖：
  - **MySQL**：GR 集群三节点（`192.168.31.105:3306, 192.168.31.182:3307, 192.168.31.182:3308`，随机负载均衡 + 自动故障转移）存储 `kb_ops` 库。
  - **Redis**：`platform-redis`（6379）缓存/会话辅助。
  - **kb-auth / kb-gateway**：登录与 JWT 校验（`KB_GATEWAY_URL=http://kb-gateway:8080`）。
  - **kb-intelligence**：资产同步数据源（OpenFeign）。
- 被依赖/关联系统：
  - 与 **infra-monitor** 定位互补：kb-ops 偏"运维台账+知识+部署记录+矛盾检测"，infra-monitor 偏"资产总览+凭据+配置+健康巡检"。
  - 部署记录与 **Woodpecker CI** 变更联动。
  - 资产数据由 **kb-intelligence** 提供、kb-ops 消费（SyncController）。

## 运维要点

- 启停/发布：应用层铁律——只走流水线（见"部署与发布"节）；应急重启 `docker restart kb-ops kb-ops-web`。
- 日志查看：
  - 容器日志：`docker logs -f kb-ops` / `docker logs -f kb-ops-web`
  - 容器日志面板：mykng 的 `obs-dozzle`（15500）
  - 结构化日志：logback-kafka-appender 推 Kafka，可被 Loki/Grafana 检索。
- 数据与备份：业务数据在 MySQL GR 集群 `kb_ops` 库；重要变更前 `mysqldump`。
- 凭据安全：凭据 AES 加密落库、接口脱敏；**所有系统明文账密一律不写入文档，统一见 Vaultwarden（vault.marschat.online）或 infrastructure-map 技能**。
- 常见问题：
  - 鉴权失败：检查 `kb-auth`/`kb-gateway` 是否正常，JWT 由统一认证签发，kb-ops 不单独登录；401 多为 JWT 过期，重新走知识库登录。
  - 端口冲突：用"端口管理 + 矛盾检测"先行排查，再触发部署。
  - 依赖解析失败：确认 `kb-intelligence` 在线，`/ops/sync/from-intelligence` 才能拉到最新资产。
  - 数据库连接：`application-prod.yml` 用 GR 集群 failover URL，compose 里的 `MYSQL_HOST/PORT` 仅为降级 fallback，排查连库问题时先看容器实际 profile。
  - 前端 502：`kb-ops-web` 前端容器独立运行，后端不可用时反代 502——`docker logs kb-ops` 查后端，多为后端重启中。
  - 同步后数据重复：`/ops/sync/from-intelligence` 默认不覆盖已有记录，传 `override=true` 才更新；重复数据先检查 entityTypes 过滤条件。

## 变更记录

- 2026-09-05 v2 补全设计/部署/使用三维度（数据表按 `kb_ops_init.sql` 核对、Controller 前缀按源码核对、部署节按 docker-compose.app/web.yml 实采重写）
- 2026-09-05 v1 首次生成（portal 文档补全任务，AI 基于实采 docker ps + 源码 Controller/README 生成）
