# kb-ops 运维管理

> 面向 devtools 全栈运维人员的 CMDB + 运维知识平台：集中管理主机、服务、域名、端口、凭据、部署记录与运维知识，并具备矛盾检测、看板快照与从 kb-intelligence 同步的能力。前端 https://kb.marschat.online/ops/，后端 API 走 `/ops-api/`。

## 基本信息

| 项 | 值 |
|----|----|
| 分类 | 自研 Web 系统（运维平台） |
| 版本 | 1.0.0（后端 Spring Boot 3.2.5 / Java 21；前端 Vue3 + Vite 0.1.0） |
| 部署位置 | 主机 mykng（192.168.31.105），容器 `kb-ops`（后端 8084）+ `kb-ops-web`（前端 nginx，宿主机 8093） |
| 源码位置 | 后端 `D:\huliang\java\ideaworkspace\devtools\kb-ops\`（groupId com.devtools），前端 `kb-ops/kb-ops-web\`；mykng 上 `/root/devtools/kb-ops` |
| CI/CD | Woodpecker 项目 `kb-ops` + `kb-ops-web`（`docker-compose.app.yml` / `docker-compose.web.yml`） |
| 技术栈 | Spring Boot 3.2.5、Java 21、Spring Cloud 2023.0.3、MyBatis-Plus 3.5.6、Redis、OpenFeign、JJWT 0.12.5、Actuator |

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

## 核心功能与使用

后端按模块拆分 Controller（`com.kb.ops.controller`），所有接口前缀 `/ops/*`，对应能力如下：

- **主机管理（HostController `/ops/host`）**：CMDB 主机台账，增删改查；记录 IP、系统、角色、Tailscale IP、挂载点等资产属性。
- **服务管理（ServiceController `/ops/service`）**：登记各业务/基础设施服务的访问地址、技术栈、健康检查 URL、启用状态。
- **域名管理（DomainController `/ops/domain`）**：维护对外域名与反代路径映射，与 mykng nginx `locations/*.conf` 互为补充台账。
- **端口管理（PortController `/ops/port`）**：登记主机端口占用，避免端口冲突；与"矛盾检测"联动。
- **依赖关系（DependencyController `/ops/dependency`）**：描述服务/组件间的依赖（如应用层依赖 platform-* 中间件），用于影响面分析。
- **凭据管理（CredentialController `/ops/credential`）**：集中存放各类系统账号/Token（加密存储，见运维要点）；提供列表/详情/增删改。
- **部署记录（DeploymentController `/ops/deployment`）**：记录每次部署的时间、目标、操作人、备注，`/recent` 取最近记录——与 Woodpecker 流水线变更对应。
- **运维知识（KnowledgeController `/ops/knowledge`）**：运维经验/排障手册的结构化知识库，支持全文检索与分类。
- **矛盾检测（ConflictController `/ops/conflict`）**：`/detect` 扫描端口冲突、依赖缺失、配置不一致等矛盾，列表展示并支持 `/{id}/resolve` 标记解决。
- **看板（DashboardController `/ops/dashboard`）**：汇总主机数、服务数、近期部署、未解决矛盾等指标；`/snapshot/refresh` 刷新快照。
- **导入（ImportController `/ops/import`）**：支持 `/csv` 批量导入主机/服务/端口等资产数据，降低手工录入成本。
- **从 intelligence 同步（SyncController `/ops/sync/from-intelligence`）**：从 `kb-intelligence` 拉取资产/拓扑，保持 CMDB 与知识库一致。
- **运维日志（OperationLogController `/ops/log`）**：操作审计日志列表与详情。

前端（Vue3 + Element Plus + Pinia + ECharts）按上述模块组织页面，看板用 ECharts 展示指标。

## 依赖与关联

- 依赖：
  - **MySQL**：`platform-mysql-1`（mykng 3306，GR 集群 Node1）存储业务数据（MyBatis-Plus 访问）；库名见部署配置（以 mykng 实例为准）。
  - **Redis**：`platform-redis`（mykng 6379）用于缓存/会话辅助。
  - **kb-auth / kb-gateway**：登录与 JWT 校验依赖知识库统一认证（`kb-gateway:8090`）。
- 被依赖/关联系统：
  - 与 **infra-monitor** 定位互补：kb-ops 偏"运维台账+知识+部署记录+矛盾检测"，infra-monitor 偏"资产总览+凭据+配置+健康巡检"。
  - 部署记录与 **Woodpecker CI**（woodpecker）变更联动。
  - 资产数据可被 **kb-intelligence** 提供、由 kb-ops 消费（SyncController）。

## 运维要点

- 启停/发布（铁律：应用层只允许走流水线）：
  - 触发后端：`python woodScript/trigger-pipeline.py kb-ops`
  - 触发前端：`python woodScript/trigger-pipeline.py kb-ops-web`
  - 全量：`python woodScript/trigger-pipeline.py all`
  - 后端部署目标 compose `kb-app`（`docker-compose.app.yml`），健康检查 `localhost:8084/kb-ops/actuator/health`；前端部署目标 compose `kb-web`（`docker-compose.web.yml`），健康检查 `localhost:8093/health`。
- 日志查看：
  - 容器日志：`docker logs -f kb-ops` / `docker logs -f kb-ops-web`
  - 容器日志面板：mykng 的 `obs-dozzle`（15500）
  - 结构化日志：后端经 logstash-logback-encoder + logback-kafka-appender 推到 Kafka，可被 Loki/Grafana 检索（链路见 infra-monitor 文档）。
- 数据与备份：业务数据在 `platform-mysql-1`；凭据字段经 AES 加密落库（见下）。备份随 MySQL GR 集群整体策略，重要变更前建议 `mysqldump`。
- 凭据安全：凭据（密码/Token）入库前由 `com.kb.common` 的加密工具 AES 加密存储，接口返回时按权限脱敏；**所有系统明文账密一律不在文档出现，统一见 Vaultwarden（vault.marschat.online）或 infrastructure-map 技能**。
- 常见问题：
  - 鉴权失败：检查 `kb-auth`/`kb-gateway` 是否正常，JWT 由统一认证签发，kb-ops 不单独登录。
  - 端口冲突：用"端口管理 + 矛盾检测"先行排查，再触发部署。
  - 依赖解析失败：确认 `kb-intelligence` 在线，`/ops/sync/from-intelligence` 才能拉到最新资产。

## 变更记录

- 2026-09-05 首次生成（portal 文档补全任务，AI 基于实采 docker ps + 源码 Controller/README 生成）
