# 基础设施监控（infra-monitor）

> 基础设施信息中心：统一登记主机、凭据、配置项、服务的资产台账，提供登录鉴权、总览看板、SSH/HTTP 健康巡检与 JSON/YAML 导入导出。是 devtools 局域网"基础设施一张图"。访问入口 https://monitor.marschat.online/infra/。

## 基本信息

| 项 | 值 |
|----|----|
| 分类 | 自研 Web 系统（运维平台 / 基础设施） |
| 版本 | 1.0.0（后端 Spring Boot 3.2.5 / Java 21；前端 Vue3 + Vite + TS 0.1.0） |
| 部署位置 | 主机 mykng（192.168.31.105），容器 `infra-monitor`（后端 8088，context `/infra/`）+ `infra-monitor-web`（前端 nginx，宿主机 8094） |
| 源码位置 | 后端 `D:\huliang\java\ideaworkspace\devtools\infra-monitor\infra-monitor-server\`，前端 `infra-monitor/infra-monitor-web\`；mykng 上 `/root/devtools/infra-monitor` |
| CI/CD | Woodpecker 项目 `infra-monitor` + `infra-monitor-web`；后端 compose project `infra-monitor`（`infra-monitor-server/docker-compose.yml`），前端 compose project `kb-web`（`docker-compose.web.yml`） |
| 技术栈 | Spring Boot 3.2.5、Java 21、Spring Security、Spring Data MongoDB、JSch（SSH）、JJWT 0.12.5、Jackson YAML、Hutool、Actuator |

## 访问入口

- 公网（前端 + 监控域名）：`https://monitor.marschat.online/infra/`
- 公网（后端 API）：`https://monitor.marschat.online/infra/api/`（反代到 `infra-monitor:8088`）
- 内网（前端）：`http://192.168.31.105:8094/`
- 内网（后端）：`http://192.168.31.105:8088/infra/`
- Tailscale：`http://100.93.36.113:8094/`（前端）、`http://100.93.36.113:8088/infra/`（后端）
- 健康检查：`http://192.168.31.105:8088/infra/actuator/health`
- 登录（自带账号体系，区别于 kb-ops）：`POST /infra/auth/login`

## 全链路

```
前端 SPA:
monitor.marschat.online → 腾讯云2号 nginx(443) → http://100.93.36.113:80 → mykng nginx(:80) /infra/ → 127.0.0.1:8094 (infra-monitor-web 静态)

后端 API:
monitor.marschat.online/infra/api → 腾讯云2号 nginx(443) → mykng nginx(:80) /infra/api/ → 127.0.0.1:8088 (infra-monitor)
（注：腾讯云2号 monitor 子域直接回源 mykng:80 的整站 /infra/ 路径）
```

说明：与 kb-ops 不同，infra-monitor **自带登录**（`AuthController /auth/login` 签发 JWT，`JwtAuthFilter` 校验）。`/auth/login` 与 `/actuator/**` 放行，其余全需鉴权。

## 系统设计

### 总体架构

轻量单体前后端分离（后端仅 6 个 Controller），刻意选用文档数据库而非关系库：

- **后端（infra-monitor-server）**：Spring Boot 单模块（Java 21），包 `com.kb.infra` 下 `controller / service / entity / config / util`。数据层 Spring Data MongoDB；巡检用 `HealthCheckScheduler`（`@Scheduled(fixedDelay=60000, initialDelay=30000)`，即启动 30s 后每 60s 一轮）+ `SshUtil`（JSch，对主机做 SSH 连通性/命令检查）；安全用 Spring Security + 自签 JWT（`JwtAuthFilter`）。
- **前端（infra-monitor-web）**：Vue3 + Vite + TypeScript，nginx 静态部署，按资产类型组织页面（主机/凭据/配置/服务/看板/导入导出）。
- **网络模式**：后端容器 `network_mode: host`——巡检需要主动连各主机/服务（SSH、HTTP），host 网络免去端口映射并让"127.0.0.1 即宿主机"的连接串直接生效。
- **数据初始化**：`DataInitializer`（CommandLineRunner）在 `infra_items` 为空时 seed 主机/凭据/配置/服务初始数据，幂等（已存在则跳过）。

### 核心数据模型

MongoDB 库 `infra_monitor`（`platform-mongo`，27017），仅两个集合——用"统一资产 + extra 动态字段"代替多张表：

| 集合 | 用途 |
|------|------|
| `infra_items`（`InfraItem`） | 统一资产集合：字段仅 `type`（host/credential/config/service）、`name`、`category`、`description`、`extra`（Map，承载各类型差异化字段：IP、系统、URL、健康检查地址、Tailscale IP、挂载点、出口代理等）、`sortOrder`、`deleted`（软删）、`createdAt/updatedAt` |
| `infra_health_logs`（`InfraHealthLog`） | 巡检历史：每次探活的服务、结果、耗时、时间 |

当前实例已登记的资产类别（extra 台账）：主机涵盖旧 Windows 宿主机、内网 Debian（192.168.31.182）、mykng-debian（192.168.31.105）、腾讯云2号（1.117.70.30）、阿里云 FRP（120.26.66.182）等；config 类含 FRP 隧道、SMB 共享、代理链路、mykng nginx 反代、Nexus 缓存策略/预热、SSL 证书、RAG 记忆增强、知识时光机等（TABLE / KEY_VALUE 两形态）。

### 关键设计决策

1. **四类资产一个集合**：host/credential/config/service 共用 `InfraItem`（type+category+extra），新增资产类型零迁移——台账型应用的字段天生多变，MongoDB 的动态文档比固定表结构更贴合。
2. **巡检内嵌而非外挂**：60s 定时对登记服务的 `healthCheckUrl` 发 HTTP 请求、对主机走 JSch SSH 检查，结果写 `infra_health_logs`——不引入 Prometheus/Zabbix 等重型监控，只做"在不在"级别的探活；`/check-all`、`/check/{serviceId}` 可手动触发即时巡检。
3. **自带账号体系**：`INFRA_ADMIN_USER/PASS` 环境变量注入管理员，`/auth/login` 签发 JWT——独立于 kb-auth，监控平台自身可用性不依赖业务系统。
4. **JSON/YAML 全量导入导出**：`/io/export/*` 输出全部资产，既是备份也是跨环境迁移通道；`/io/import` 反向回灌。
5. **凭据加密分级**：密码字段经 `CryptoUtil` AES 加密落库（密钥 `CRYPTO_AES_KEY`），接口按权限返回、前端展示脱敏。

### 对外接口概览

Controller 位于 `com.kb.infra.controller`（容器 context `/infra/`）：

| 分组 | 前缀 | 功能 |
|------|------|------|
| AuthController | `/auth` | `/login` 登录签发 JWT |
| InfraItemController | `/items` | 统一资产 CRUD：`/list`、`/all`、`/category/{type}/{category}` 按类目筛选、`/stats/{type}` 统计 |
| CredentialController | `/credentials` | 凭据专项（AES 加密存取、脱敏返回） |
| HealthCheckController | `/health` | `/check-all` 全量探活、`/check/{serviceId}` 单服务探活、`/logs/{serviceId}`（含 `/recent`）探活历史 |
| DashboardController | `/dashboard` | `/summary` 聚合主机数/凭据数/配置数/健康概览 |
| ImportExportController | `/io` | `/import` JSON 导入、`/export/json`、`/export/yaml` 全量导出 |

## 部署与发布

### 编排与位置

- 后端：容器 `infra-monitor`，镜像 `infra-monitor:latest`（`build: .`），归属 compose project `infra-monitor`，compose 文件 `/root/devtools/infra-monitor/infra-monitor-server/docker-compose.yml`。
  - 特殊点：**`network_mode: host`**（直接用宿主机网络，MongoDB 走 `127.0.0.1:27017`），不走 kb-app-net。
  - 无 depends_on：后端启动时 MongoDB 不在线会连库失败重启（on-failure:5 自动重试），平台层就绪后自然恢复。
- 前端：容器 `infra-monitor-web`（nginx:alpine），归属 compose project `kb-web`，compose 文件 `/root/devtools/docker/docker-compose.web.yml`——与 kb-web/kb-ops-web/portal-web 同 project，可独立重建。

### 配置清单

**infra-monitor（后端容器）**

- 端口：host 网络模式，直接监听宿主机 `8088`（无端口映射）
- 卷挂载：`/data/infra-monitor/logs` → `/app/logs`（应用日志落盘）
- 关键环境变量（值见 Vaultwarden / compose 文件，不在文档落盘）：
  - `SPRING_PROFILES_ACTIVE=prod`
  - `SPRING_DATA_MONGODB_URI`（MongoDB 连接串，库名 `infra_monitor`，authSource=admin）
  - `JWT_SECRET`（登录 JWT 签名密钥）
  - `CRYPTO_AES_KEY`（凭据 AES 加密密钥）
  - `INFRA_ADMIN_USER` / `INFRA_ADMIN_PASS`（初始管理员，首次登录后建议修改）
- 内存限制：`256m`，`restart: on-failure:5`

**infra-monitor-web（前端容器）**

- 端口映射：`8094:80`
- 卷挂载（只读）：
  - `/root/kb-deploy/infra-monitor-web/dist` → `/usr/share/nginx/html`
  - `/root/kb-deploy/infra-monitor-web/nginx.conf` → `/etc/nginx/conf.d/default.conf`
- 内存限制：`64m`

### 发布/升级（只允许走流水线）

```bash
python woodScript/trigger-pipeline.py infra-monitor       # 后端
python woodScript/trigger-pipeline.py infra-monitor-web   # 前端
```

链路：push/手动触发 → `.woodpecker.yml` 的 `infra-mon-build` / `infra-mon-deploy`（前端为 `infra-web` / `infra-web-deploy`）步骤 → 产物 `/mnt/shared/woodScript/publish/infra-monitor-latest.tar.gz` → `appleboy/drone-ssh`（`ssh_key_mykng` Secret）登录 mykng → `cd/deploy-infra-monitor.sh`（project `infra-monitor` 重建 + 健康检查 `localhost:8088/infra/actuator/health`，24×10s）；前端走 `deploy-infra-monitor-web.sh`（kb-web project，健康检查 `localhost:8094/health`）。

注意：后端 compose `build: .`——流水线产物解压到 `/root/devtools/infra-monitor/infra-monitor-server/` 后由 compose 重新 build 镜像（区别于 kb-* 的预构建镜像直拉模式），首次构建耗时略长。

### 回滚

- 后端：`/mnt/shared/woodScript/publish/` 旧产物重新解压 → `docker compose -p infra-monitor up -d --build`。
- 数据回退：用 `/io/export/json` 的最近导出文件经 `/io/import` 回灌（导入前先 `mongodump` 当前库）。

## 核心功能与使用

### 功能清单

- **资产总览**：主机/凭据/配置/服务四类资产一张图——查"内网有哪些机器、跑着什么"的第一入口。
- **健康巡检**：60s 周期对登记服务探活（HTTP + SSH），异常历史可查——快速定位"哪个服务挂了、什么时候挂的"。
- **凭据档案**：各系统账号/Token 加密登记（WEB/DB/API_TOKEN/OTHER 分类）——查账密入口（配合 Vaultwarden）。
- **配置台账**：FRP 隧道、SMB 共享、代理链路、nginx 反代、Nexus 缓存策略、SSL 证书等结构化配置记录（TABLE/KEY_VALUE 两形态）。
- **总览看板**：资产数量与健康状态聚合展示（`/dashboard/summary`）。
- **导入导出**：JSON/YAML 全量导出——轻量备份与环境迁移。

### 典型操作路径

1. **首次登录**：打开 `https://monitor.marschat.online/infra/` → 用管理员账号登录（账密见 Vaultwarden）→ 进入总览看板。
2. **登记新服务并纳入巡检**：服务页新增（填 URL + healthCheckUrl）→ 等待 60s 调度或点"立即巡检" → 健康日志页查看结果。
3. **查一台主机的信息**：主机页按类目筛选 → 点开条目看 `extra` 明细（IP/Tailscale/挂载点/出口代理）。
4. **登记一批配置**：配置页新增 TABLE 或 KEY_VALUE 形态条目 → 按 category 归类（如"代理链路"/"SSL 证书"）。
5. **备份资产**：导入导出页 → `/io/export/json` 下载全量文件 → 存档（建议每次大变更后执行）。
6. **迁移到新环境**：新环境起服务（DataInitializer 跳过 seed 或清库）→ `/io/import` 导入导出文件 → 核对 `/stats/{type}` 数量一致。

## 依赖与关联

- 依赖：
  - **MongoDB**：`platform-mongo`（mykng 27017）存储 `infra_items` 与 `infra_health_logs`；容器经 host 网络直连 `127.0.0.1:27017`。
  - **SSH 凭据**：探活内网主机需主机 SSH 账号（凭据加密存放，见 Vaultwarden / infrastructure-map）。
- 被依赖/关联系统：
  - 与 **kb-ops** 互补：infra-monitor 偏"资产总览 + 凭据 + 配置 + 健康巡检"，kb-ops 偏"运维台账 + 知识 + 部署记录 + 矛盾检测"。
  - 巡检结果数据经日志链路汇入 **Loki/Grafana**（mykng 侧 promtail 3.0.0 采集）。
  - 资产数据可作为 **kb-ops** 的对照来源（kb-ops 的 SyncController 从 kb-intelligence 拉取，二者台账可互查）。

## 运维要点

- 启停/发布：应用层铁律——只走流水线（见"部署与发布"节）；应急 `docker restart infra-monitor infra-monitor-web`。
- 数据初始化：`DataInitializer` 仅在资产为空时 seed；seed 中的明文账密属于密级数据，禁止出现在文档，统一以 Vaultwarden 或 infrastructure-map 技能为准。
- 日志查看：
  - 容器日志：`docker logs -f infra-monitor` / `docker logs -f infra-monitor-web`
  - 落盘日志：mykng `/data/infra-monitor/logs/`
  - 容器日志面板：mykng `obs-dozzle`（15500）
- 数据与备份：资产在 `platform-mongo` 的 `infra_monitor` 库；日常用 `/io/export/json` 导出作轻量备份，或 `mongodump --uri <连接串> --db infra_monitor`（连接串见 Vaultwarden / compose）。MongoDB 本身由 platform 层统一维护。
- 巡检日志清理：`infra_health_logs` 每 60s 增长，长期运行后按时间清理旧文档（保留最近 7~30 天足够排障）。
- 凭据安全：所有密码经 `CryptoUtil` AES 加密，前端展示脱敏；**任何系统明文账密一律不写入文档**。
- 常见问题：
  - 巡检一直 UNKNOWN：检查对应 service 的 `healthCheckUrl` 是否可达、目标服务是否在线；SSH 型巡检确认主机 SSH 凭据有效。
  - 登录态失效：JWT 由本系统签发，过期重新 `/auth/login` 即可。
  - 导入失败：确认 JSON 结构与 `/items` 字段一致，建议先用 `/export/json` 取模板。
  - 后端连不上 MongoDB：`network_mode: host` 下 URI 走 `127.0.0.1`，确认 `platform-mongo` 的 27017 已映射到宿主机。
  - 巡检日志暴涨：每 60s 一轮写 `infra_health_logs`，长期运行后可按时间清理旧记录（mongosh 删除过期文档）。
  - 8088 端口被占：host 网络模式下无端口隔离，启动前确认宿主机 8088 空闲。

## 变更记录

- 2026-09-05 v2 补全设计/部署/使用三维度（数据模型/Controller/巡检调度按源码核对，部署节按 infra-monitor-server/docker-compose.yml 实采重写，明确 host 网络模式与 MongoDB 连接方式）
- 2026-09-05 v1 首次生成（portal 文档补全任务，AI 基于实采 docker ps + 源码 Controller/DataInitializer 生成）
