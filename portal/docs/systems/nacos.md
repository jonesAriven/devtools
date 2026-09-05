# Nacos 服务中心

> 服务注册与发现中心（开源 Alibaba Nacos v2.4.3），承载 mykng 上 kb-* 五个 Spring Cloud 微服务的注册寻址，是 devtools 微服务体系的服务治理中枢。platform 基础设施层组件，随 platform compose 持久运行。

## 基本信息

| 项 | 值 |
|----|----|
| 分类 | 基础设施（微服务治理） |
| 版本 | nacos/nacos-server:**v2.4.3**（docker inspect 实采；portal 卡片写 2.3.x 已过期） |
| 部署位置 | mykng（192.168.31.105）容器 `platform-nacos`，compose project `platform`，standalone 模式 |
| 端口 | 8848（控制台/OpenAPI）、9848（gRPC 客户端，v2 必需，= 8848+1000 偏移） |
| 源码位置 | 开源组件，官方仓库 https://github.com/alibaba/nacos |
| CI/CD | 无（platform 层基础设施，不归应用流水线管；首次安装/重启走 `platform/start-platform.sh` 手动执行） |

## 访问入口

- 公网：`https://kb.marschat.online/nacos/`
- 内网：`http://192.168.31.105:8848/nacos/`
- Tailscale：`http://100.93.36.113:8848/nacos/`
- 控制台账密：见 Vaultwarden（vault.marschat.online）或 infrastructure-map 技能
- 就绪探针：`/nacos/v1/console/health/readiness`（compose healthcheck 用）

## 全链路

```
浏览器（控制台）:
https://kb.marschat.online/nacos/ → 腾讯云2号 nginx(443, TLS 终止)
  → http://100.93.36.113:80 → mykng nginx /nacos/ → proxy_pass http://127.0.0.1:8848/nacos/
  → 容器 platform-nacos (:8848)

微服务客户端（注册/发现）:
kb-* 容器 → platform-net 网络 → platform-nacos:8848 (HTTP) + :9848 (gRPC)
（客户端直连内网，不经 nginx、不经公网）
```

> 注意：上述 nginx 链路只覆盖 8848 控制台/OpenAPI；微服务客户端的 gRPC 9848 直连内网，防火墙只开 8848 会导致客户端注册失败。

## 系统设计

### 组件架构

Nacos 是 Alibaba 开源的服务注册发现与配置管理中心。v2 的关键特性：客户端经 **gRPC 长连接**（9848，由 8848+1000 偏移规则得出）注册/订阅，比 v1 的 HTTP 轮询更实时、推送更快；支持 standalone/集群两种部署，元数据可存内嵌 Derby 或外部 MySQL；认证体系支持 Token + identity 双校验。

本实例三件套的角色分工：

| 组件 | 角色 |
|------|------|
| `platform-nacos` | 注册中心主服务（standalone，持久运行） |
| `nacos-init` | 一次性用户初始化容器（幂等插入默认用户，跑完即退） |
| `platform-net` 网络 | 注册中心与全部 kb-* 服务容器的互通层（各应用 compose 以 external 引入） |

### 我们的集成设计

- **实例角色**：standalone 单实例注册中心（**当前仅用服务发现，未启用配置中心**——各服务无 bootstrap.yml、无 spring.config.import，配置全部走本地 application*.yml；配置中心能力保留，如需启用见"使用"节）。
- **存储**：**内嵌 Derby**（非 MySQL）——compose 未注入任何 `SPRING_DATASOURCE_*` 变量，数据落卷 `platform-nacos-data`（容器 `/home/nacos/data/derby-data`）。Derby 内核心表：`users`（控制台账号）、`roles`（角色）、`config_info`（配置中心数据）、`tenant_info`（namespace）、`instance` 相关表（服务实例元数据）。v1 文档"依赖 platform-mysql"的说法有误，已修正；若未来升级为 MySQL 存储，只需注入 `SPRING_DATASOURCE_PLATFORM=mysql` + 连接串变量并重建。
- **认证**：`NACOS_AUTH_ENABLE=true`（客户端与服务端经 `NACOS_AUTH_TOKEN` / identity key-value 校验，值为密级见 Vaultwarden）。
- **用户初始化**：v2.4.3 standalone 不会自动建默认用户（Derby schema 只建表不插数据）——compose 里 `nacos-init` 一次性容器在 Nacos 启动前跑 `platform/nacos/init-nacos.sh`，向 Derby `users` 表幂等插入默认用户（标记文件 `.nacos-user-initialized` + SELECT 双重检查），`platform-nacos` 依赖其 `service_completed_successfully` 后才启动。首次启动 Derby 尚未建 schema 时脚本会跳过，Nacos 建库后需再跑一次该容器。
- **namespace/group 组织**：全部服务统一使用默认 namespace `public` + 默认 group `DEFAULT_GROUP`（各服务 application.yml 中 `spring.cloud.nacos.discovery.namespace/group` 的环境变量默认值，实例上未做环境分 namespace——环境差异由 `SPRING_PROFILES_ACTIVE=prod` + 各自 application-prod.yml 承担，而非 Nacos 侧隔离）。
- **注册发现链路**（源码实采）：
  - 五个微服务（`kb-gateway`、`kb-auth`、`kb-file`、`kb-knowledge`、`kb-intelligence`）经 `spring-cloud-starter-alibaba-nacos-discovery` 启动注册，服务名 = `spring.application.name`（即 kb-gateway 等）。
  - 服务地址经 compose 环境变量下发：`NACOS_HOST=platform-nacos`、`NACOS_PORT=8848`、`NACOS_USERNAME/PASSWORD`（值见 mykng `.env` / Vaultwarden）。
  - **kb-gateway 路由全部用 `lb://服务名`**（如 `uri: lb://kb-auth`、`lb://kb-file`、`lb://kb-knowledge`、`lb://kb-intelligence`），经 Spring Cloud LoadBalancer 从 Nacos 按服务名寻址——模块注册即上线、下线即自动摘除，网关零配置拔插；路由统一上下文 `${KB_CONTEXT:/kb}` + `StripPrefix` 去前缀，Swagger 文档路由（`/v3/api-docs`）也走同一机制。
  - 服务端接入样例（各 kb-* 服务 application.yml，同一写法）：
    ```yaml
    spring:
      application:
        name: kb-gateway          # 即 Nacos 服务名
      cloud:
        nacos:
          discovery:
            server-addr: ${NACOS_HOST:platform-nacos}:${NACOS_PORT:8848}
            namespace: ${NACOS_NAMESPACE:public}
            group: ${NACOS_GROUP:DEFAULT_GROUP}
            username: ${NACOS_USERNAME:nacos}
            password: ${NACOS_PASSWORD:nacos123}
    ```
  - 不接入的服务：`kb-ops`、`portal-server`、`infra-monitor`（compose 无 NACOS_* 环境变量、源码无 nacos 依赖，v1 文档"kb-ops/infra-monitor/portal-server 同样接入"的说法不成立，已修正）。
- **为什么选它**：与 Spring Cloud Alibaba 生态原生集成（lb:// 零代码路由）、standalone+Derby 零外部依赖、资源占用小（mem_limit 384m）。

## 部署与发布

### 编排与位置

- compose 文件：`/root/devtools/platform/docker-compose.platform.yml`（本地源码 `D:\huliang\java\ideaworkspace\devtools\platform\docker-compose.platform.yml`），compose project `platform`，网络 `platform-net`（bridge；mykng/应用层各 compose 以 external 引入）。
- 启动：`bash platform/start-platform.sh` 或 `docker compose -p platform -f platform/docker-compose.platform.yml up -d`（platform 层整体，不归流水线，持久运行）。

### 配置清单（docker inspect 实采）

- 容器 `platform-nacos`：镜像 `nacos/nacos-server:v2.4.3`，`restart: unless-stopped`，`mem_limit: 384m`。
- 端口映射：`8848:8848`、`9848:9848`。
- 卷挂载：
  - named volume `platform_platform-nacos-data` → `/home/nacos/data`（Derby 数据）
  - named volume `platform_platform-nacos-logs` → `/home/nacos/logs`
- 关键环境变量（只列名与用途）：
  - `MODE=standalone`、`PREFER_HOST_MODE=hostname`
  - `JVM_XMS=128m / JVM_XMX=256m / JVM_XMN=64m`（JVM 内存限制）
  - `NACOS_AUTH_ENABLE=true`、`NACOS_AUTH_TOKEN`、`NACOS_AUTH_IDENTITY_KEY/VALUE`（鉴权三件套，值见 Vaultwarden）
- 一次性容器 `nacos-init`：同镜像，`entrypoint: bash /init-nacos.sh`，挂载同一数据卷 + `./nacos/init-nacos.sh`（只读），`restart: no`。
- 健康检查：`curl -f http://localhost:8848/nacos/v1/console/health/readiness`（10s 间隔，重试 10 次）。

### 发布/升级

- platform 层无流水线。升级：改 compose 中镜像 tag → `docker compose -p platform -f platform/docker-compose.platform.yml up -d platform-nacos` 重建（Derby 数据在卷中保留；跨大版本升级前先备份 `platform-nacos-data` 卷，并确认目标版本对 v2.4.x 数据的兼容性说明）。
- nacos-init 容器每次 platform 启动都会重跑（幂等，已有用户自动跳过）。
- 若只重启 Nacos 不动其他服务：`docker restart platform-nacos`，客户端自动重连，无需全量重启。
- 变更 `NACOS_AUTH_TOKEN` 等鉴权三件套时：先改 mykng `.env` → 重建 `platform-nacos` → **必须同时重建全部 kb-* 容器**（客户端缓存旧 Token 会 403）。

### 回滚

- 镜像回退：compose 改回旧 tag 重建即可。
- 数据回退：恢复 `platform-nacos-data` 卷备份（停容器 → 回放卷 → 重启）。

## 核心功能与使用

### 功能清单

- **服务注册与发现**（当前实际使用）：kb-* 五微服务启动自动注册，网关 `lb://` 按名寻址。
- **实例健康管理**：gRPC 长连接心跳，实例掉线自动摘除，控制台可见健康状态。
- **配置中心**（能力保留，当前未接入）：可经 `spring.config.import: nacos:` 接入，改配置配合 `@RefreshScope` 下发刷新。
- **namespace/group 分层**（能力保留，当前统一 public/DEFAULT_GROUP）：多环境隔离时可按 namespace 划分。
- **控制台**：服务列表（实例数/健康状态）、配置编辑、监听查询。
- **OpenAPI**：8848 端口 REST 接口（服务列表/实例查询等），脚本化巡检可用。

### 典型操作路径

1. **看注册了哪些服务**：控制台登录（账密见 Vaultwarden）→ 服务管理 → 服务列表 → 点开服务看实例与健康状态。正常应有 kb-gateway/kb-auth/kb-file/kb-knowledge/kb-intelligence 五个服务。
2. **排查某服务没注册**：先看容器日志 `docker logs -f <服务>` 是否有 nacos 连接报错 → 确认 compose 注入了 `NACOS_HOST=platform-nacos` + 账密 → 确认容器在 `platform-net` 网络 → 确认 9848 端口可达（v2 gRPC）。
3. **手动摘除/上线实例**：控制台服务详情 → 下线/上线按钮（临时流量摘除用，正式变更走流水线重启）。
4. **临时重启**：`docker restart platform-nacos`（客户端本地缓存服务列表，重启期间已建立的调用不断，恢复后自动重连）。
5. **启用配置中心**（当前未接入，如需启用）：服务加 `spring-cloud-starter-alibaba-nacos-config` 依赖 → `spring.config.import: nacos:<dataId>` 指向配置 → 控制台"配置管理"发布 → 配合 `@RefreshScope` 热刷新（dataId/group 按 `public`/`DEFAULT_GROUP` 约定，与现有发现配置保持一致）。

## 依赖与关联

- 依赖：
  - **无外部数据库**（内嵌 Derby，数据在 `platform-nacos-data` 卷）——v1 所述 MySQL 依赖不成立。
  - **platform-net 网络**：被注册服务容器经此网络直连。
- 被依赖/关联系统：
  - **mykng 五微服务**（kb-gateway/kb-auth/kb-file/kb-knowledge/kb-intelligence）：注册 + 被网关 lb:// 寻址。
  - **kb-gateway**：全部路由依赖服务名解析，Nacos 不可用则网关路由失效（下游服务直连不受影响）。
  - 不接入：kb-ops、portal-server、infra-monitor（各自独立端口直连，不经 Nacos）。

## 运维要点

- 启停：platform 层 compose 管理；应急 `docker restart platform-nacos`（standalone 单点，重启期间网关新路由解析受影响，已缓存的路由不受影响）。
- nacos-init 容器排障：`docker logs nacos-init`——首次启动会提示 Derby 不存在，platform 整体起来后再 `docker start nacos-init` 补一次即可。
- 日志：`docker logs -f platform-nacos`；落盘日志在宿主机 `platform_platform-nacos-logs` 卷（容器 `/home/nacos/logs`，含 `nacos.log`/`naming-*.log`）；容器级日志也可在 Dozzle（mykng 15500）查看。
- 数据备份：Derby 数据在 `platform-nacos-data` 卷——重要变更前：
  ```bash
  docker run --rm -v platform_platform-nacos-data:/data -v $(pwd):/bak alpine \
    tar czf /bak/nacos-data-$(date +%F).tgz -C /data .
  ```
  （服务注册表可自动恢复，备份重点保用户/权限/配置数据。）
- 凭据安全：控制台账号、`NACOS_AUTH_TOKEN` 等均为密级，**一律见 Vaultwarden（vault.marschat.online）或 infrastructure-map 技能**，文档不写明文。
- 常见问题：
  - 客户端连不上/注册失败：v2 客户端需要 gRPC 9848（8848+1000），防火墙/安全组只放行 8848 时注册必失败；Tailscale 节点访问控制台 8848 即可，无需 9848。
  - 控制台无法登录：v2.4.3 standalone 不自带默认用户，确认 nacos-init 容器日志（首次启动 Derby 未建 schema 时会提示 Nacos 起完后再跑一次）。
  - 网关 503/找不到服务：控制台确认目标服务已注册且健康；确认网关与目标服务同在 platform-net 可达。
  - 服务下线后仍被调用：客户端本地缓存有刷新周期，等几秒或重启网关。
  - 鉴权报错 403：`NACOS_AUTH_TOKEN`/identity key-value 三件套在 server 与 client 两侧必须一致——.env 改动后需同时重建 `platform-nacos` 与全部 kb-* 容器。

## 变更记录

- 2026-09-05 v2 补全设计/部署/使用三维度（新增系统设计节：standalone+Derby 存储、nacos-init 用户初始化、namespace/group=public/DEFAULT_GROUP、五微服务 lb:// 注册链路按源码实采；修正 v1 两处不实——存储非 MySQL、kb-ops/infra-monitor/portal-server 未接入；部署节按 platform compose + docker inspect 实采重写）
- 2026-09-05 v1 首次生成（portal 文档补全任务，AI 基于实采+源码生成；版本按容器实采修正为 v2.4.3）
