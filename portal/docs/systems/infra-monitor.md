# 基础设施监控（infra-monitor）

> 基础设施信息中心：统一登记主机、凭据、配置项、服务的资产台账，提供登录鉴权、总览看板、SSH/HTTP 健康巡检与 JSON/YAML 导入导出。是 devtools 局域网"基础设施一张图"。访问入口 https://monitor.marschat.online/infra/。

## 基本信息

| 项 | 值 |
|----|----|
| 分类 | 自研 Web 系统（运维平台 / 基础设施） |
| 版本 | 1.0.0（后端 Spring Boot 3.2.5 / Java 21；前端 Vue3 + Vite + TS 0.1.0） |
| 部署位置 | 主机 mykng（192.168.31.105），容器 `infra-monitor`（后端 8088，context `/infra/`）+ `infra-monitor-web`（前端 nginx，宿主机 8094） |
| 源码位置 | 后端 `D:\huliang\java\ideaworkspace\devtools\infra-monitor\infra-monitor-server\`，前端 `infra-monitor/infra-monitor-web\`；mykng 上 `/root/devtools/infra-monitor` |
| CI/CD | Woodpecker 项目 `infra-monitor` + `infra-monitor-web`；后端 compose `infra-monitor`（`infra-monitor-server/docker-compose.yml`），前端 compose `kb-web`（`docker-compose.web.yml`） |
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

## 核心功能与使用

后端模块（`com.kb.infra.controller`），数据模型统一为 `InfraItem`（按 `type` 区分 host / credential / config / service），能力如下：

- **资产总览（InfraItemController `/items`）**：主机、凭据、配置项、服务的统一 CRUD；支持 `/list`、`/all`、`/category/{type}/{category}` 按类目筛选、`/stats/{type}` 统计。
- **主机台账**：记录每台主机的 IP、系统、角色、虚拟化信息、Tailscale IP、挂载点、出口代理（Clash）等。当前实例已登记的主机涵盖：旧 Windows 宿主机（192.168.31.243）、内网 Debian（192.168.31.182）、mykng-debian（192.168.31.105 / Tailscale 100.93.36.113）、腾讯云2号（1.117.70.30）、阿里云 FRP（120.26.66.182）、龙虾主机等。
- **凭据管理（CredentialController `/credentials`）**：集中登记各系统的账号/Token 类别（WEB/DB/API_TOKEN/OTHER），密码字段经 `CryptoUtil` **AES 加密落库**，接口按权限返回。
- **配置项（config 类型）**：FRP 隧道状态、SMB 共享、代理链路、mykng nginx 反代、Nexus 缓存策略、SSL 证书、RAG 记忆增强、知识时光机、Nexus 缓存预热等结构化配置台账（TABLE / KEY_VALUE 两种形态）。
- **服务监控（service 类型）**：登记各服务的 URL、健康检查 URL、技术栈、启用状态；由巡检器周期探活。
- **健康巡检（HealthCheckController `/health` + `HealthCheckScheduler`）**：`/check-all` 手动全量探活、`/check/{serviceId}` 单服务探活、`/logs/{serviceId}`（含 `/recent`）查看探活历史；调度器周期性对 `healthCheckUrl` 发请求并写 `InfraHealthLog`。
- **总览看板（DashboardController `/dashboard/summary`）**：聚合主机数、凭据数、配置数、服务健康概览等指标。
- **导入导出（ImportExportController `/io`）**：`/import` 从 JSON 导入资产；`/export/json`、`/export/yaml` 全量导出，便于备份与跨环境迁移。
- **SSH 探活**：`SshUtil`（基于 JSch）对主机执行 SSH 连通性/命令检查，配合健康检查。

## 依赖与关联

- 依赖：
  - **MongoDB**：`platform-mongo`（mykng 27017）存储 `InfraItem` 与 `InfraHealthLog`（Spring Data MongoDB Repository）。
  - **SSH 凭据**：探活内网主机需主机 SSH 账号（凭据加密存放，见 Vaultwarden / infrastructure-map）。
- 被依赖/关联系统：
  - 与 **kb-ops** 互补：infra-monitor 偏"资产总览 + 凭据 + 配置 + 健康巡检"，kb-ops 偏"运维台账 + 知识 + 部署记录 + 矛盾检测"。
  - 巡检结果数据经日志链路汇入 **Loki/Grafana**（mykng 侧 promtail 3.0.0 采集）。
  - 资产数据可作为 **kb-ops** 的同步来源之一（kb-ops 的 `SyncController` 从 intelligence 拉取，二者台账可对照）。

## 运维要点

- 启停/发布（应用层只允许走流水线）：
  - 后端：`python woodScript/trigger-pipeline.py infra-monitor`
  - 前端：`python woodScript/trigger-pipeline.py infra-monitor-web`
  - 后端部署目标 compose `infra-monitor`（`infra-monitor-server/docker-compose.yml`），健康检查 `localhost:8088/infra/actuator/health`；前端部署目标 `kb-web`，健康检查 `localhost:8094/health`。
- 数据初始化：`DataInitializer`（CommandLineRunner）在 host 数量为 0 时 seed 初始主机/凭据/配置/服务数据；已存在则跳过（幂等）。**seed 中的明文账密属于密级数据，禁止出现在文档，统一以 Vaultwarden 或 infrastructure-map 技能为准**。
- 日志查看：
  - 容器日志：`docker logs -f infra-monitor` / `docker logs -f infra-monitor-web`
  - 容器日志面板：mykng `obs-dozzle`（15500）
- 数据与备份：资产在 MongoDB（`platform-mongo`），建议用 `/io/export/json` 定期导出作为轻量备份；MongoDB 本身由 platform 层统一维护。
- 凭据安全：所有密码经 `CryptoUtil` AES 加密，前端展示脱敏；**任何系统明文账密一律不写入文档**。
- 常见问题：
  - 巡检一直 UNKNOWN：检查对应 service 的 `healthCheckUrl` 是否可达、目标服务是否在线。
  - 登录态失效：JWT 由本系统签发，过期重新 `/auth/login` 即可。
  - 导入失败：确认 JSON 结构与 `/items` 字段一致，建议先用 `/export/json` 取模板。

## 变更记录

- 2026-09-05 首次生成（portal 文档补全任务，AI 基于实采 docker ps + 源码 Controller/DataInitializer 生成）
