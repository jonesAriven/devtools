# Nexus 私服（公网入口）

> Sonatype Nexus Repository Manager 3 自建制品仓库，统一代理 Maven / npm / PyPI / Docker / apt / conda / NuGet 等源，作为 devtools 全部构建的依赖中枢与缓存加速层。本篇从**公网域名入口**视角描述；同一实例的内网直连用法见 `nexus-lan.md`。

## 基本信息

| 项 | 值 |
|----|----|
| 分类 | 基础设施 / 制品仓库（开源组件自部署） |
| 版本 | Nexus Repository Manager **3.91.1**（`sonatype/nexus3:3.91.1`，docker inspect 实采） |
| 部署位置 | 主机 mykng（192.168.31.105），容器 `nexus`（bridge 网络），端口 `8081-8083`（8081=UI/REST+Maven/npm/PyPI，8082/8083=Docker registry connector） |
| 源码位置 | 开源组件，官方仓库 github.com/sonatype/nexus-public；本实例数据在 mykng `/root/nexus/data`（容器挂载 `/nexus-data`） |
| CI/CD | 无（基础设施层，自部署手工管理；不归应用流水线管） |
| 技术栈 | Java（Nexus OSS 3）、OrientDB 元数据 + blob 存储、REST API v1 |

## 访问入口

- 公网（主入口）：`https://nexus.marschat.online`（Maven/npm/PyPI 仓库与 Web 控制台，经腾讯云2号 nginx 443）
- 公网（Docker registry）：经同一域名、Docker 走 `nexus_backend_docker:8083`（push/pull 用 8083）
- 内网：`http://192.168.31.105:8081/`（Web 控制台与 REST）
- Tailscale：`http://100.93.36.113:8081/`
- 状态探针（匿名可探）：`/service/rest/v1/status`；Web 控制台 `/`（登录后管理仓库/权限）

## 全链路

```
Maven/npm/PyPI + Web 控制台:
nexus.marschat.online → 腾讯云2号 nginx(443) → upstream nexus_backend (mykng nexus :8081)

Docker registry (push/pull):
Docker 客户端 → 腾讯云2号 nginx → upstream nexus_backend_docker (mykng nexus :8083)
（内网机也可直连 192.168.31.105:8082/8083，见 nexus-lan.md）
```

## 系统设计

### 组件架构

Nexus Repository 3 是 Sonatype 开源的通用制品仓库，核心能力：**hosted（自托管发布）/ proxy（代理回源并缓存）/ group（聚合多仓库为单一入口）** 三种仓库类型 × 多格式（maven2/npm/pypi/docker/apt/conda/nuget...），配合 ACL、清理策略与 REST API。

### 我们的集成设计

- **实例角色**：devtools 全栈的"单一依赖源"——所有 Maven/npm/PyPI/Docker 构建只认 Nexus，公网源一律收敛为代理缓存；同时承担内部制品（releases/snapshots/hosted）的发布归档。
- **数据布局**：Nexus 3 的持久化分两层——OrientDB 元数据（仓库/权限/任务配置）+ blob 存储（制品二进制），本实例统一放在 `/nexus-data`（宿主机 `/root/nexus/data`），无外部分数据库依赖，备份即拷目录。
- **访问控制**：匿名账号仅开放只读（拉取/状态探测），发布与管理操作需实名账号；各构建端的发布凭证见 Vaultwarden。
- **代理链设计**（REST `/service/rest/v1/repositories` 实采，23 个仓库，按格式分组）：

| 格式 | group（构建端唯一 URL） | hosted（内部发布） | proxy（回源缓存） |
|------|------------------------|--------------------|-------------------|
| maven2 | `maven-public` | maven-releases、maven-snapshots | maven-central（官方中央源）、maven-aliyun（阿里云） |
| npm | `npm-public` | npm-hosted | npm-proxy、npm-mirror（npmjs 回源，双源互备） |
| pypi | `pypi-public` | pypi-hosted | pypi-proxy、pypi-aliyun、pypi-tuna（三源互备） |
| docker | `docker-public`（8082/8083 connector） | docker-hosted | docker-daocloud、docker-hub-direct（Docker Hub 回源） |
| nuget | `nuget-group` | nuget-hosted | nuget.org-proxy |
| apt / conda | —（单代理直用） | — | apt-proxy、conda-proxy |

  - 业务项目（kb-ops/infra-monitor/portal/mykng）的 `pom.xml` 将 `repositories`/`pluginRepositories` 与 `distributionManagement` 全部指向 `https://nexus.marschat.online/repository/maven-public/`（releases/snapshots），**强制走 Nexus，禁用其他公网源**。
  - 前端 pnpm 构建 registry 统一指 `npm-public`（见 woodpecker `env.sh` 的 `NEXUS_NPM_REGISTRY`）。
- **为什么这样聚合**：group 仓库让构建端只配置一个 URL，内部命中 hosted、未命中逐级落到 proxy 并缓存——多代理源（central+aliyun、npmjs+tuna+aliyun、daocloud+hub-direct）互为容错，任一上游抖动不断供。
- **缓存策略**：`negativeCache` 关闭——某包回源 404 不会被缓存为"不存在"，避免上游临时故障造成长期误判；`contentMaxAge=525600`（制品缓存 365 天，制品不可变，命中即本地返回）；`metadataMaxAge=1440`（版本列表/索引每天刷新一次，保证新版本可见性的同时减少回源）。
- **数据一致性**：两入口（公网/内网）同一容器同一数据卷，任一入口的拉取/发布结果对另一入口立即可见，无双写一致性问题。
- **双入口设计**（为什么内网+公网两个入口，详见 nexus-lan.md）：同一实例同时暴露公网域名（外部/移动场景）与内网直连（高频构建场景），内网流量不绕公网带宽；两入口共享同一份数据与配置。
- **与 infra-monitor 台账联动**：Nexus 缓存策略、Nexus 缓存预热等配置项在 infra-monitor 的 config 类资产中登记（TABLE 形态），巡检覆盖 `8081/service/rest/v1/status` 探活——Nexus 异常可从 infra-monitor 看板第一时间发现。
- **缓存预热**：`/home/liangzi/tools/nexus-warmup.sh`（腾讯云2号）每周日 03:30 cron 执行，覆盖 npm（Top250+80 包）/maven（33）/pypi（47）/docker（22 个基础镜像），日志 `/var/log/nexus-warmup.log`；脚本经各仓库下载 API 逐包请求触发缓存（幂等，已缓存仅元数据刷新）；实测 react 6.8MB 首次 19s → 缓存后 0.08s（约 237× 提速）。

## 部署与发布

### 编排与位置

- 容器 `nexus`（docker inspect 实采）：镜像 `sonatype/nexus3:3.91.1`，**bridge 网络**（独立于 platform-net），`restart: unless-stopped`。
- 单容器自部署（无 compose project 编排，`docker run`/手工管理）。

### 配置清单

- 容器基础（docker inspect 实采）：

| 项 | 值 |
|----|----|
| 镜像 | `sonatype/nexus3:3.91.1` |
| 网络 | bridge（独立于 platform-net） |
| 重启策略 | unless-stopped |
| 内存限制 | 无（默认，Nexus 3 建议 ≥2G 可用内存） |

- 端口映射：`8081:8081`（UI/REST + Maven/npm/PyPI）、`8082:8082`、`8083:8083`（Docker registry connector）。
- 卷挂载（宿主→容器）：`/root/nexus/data` → `/nexus-data`（全部 blob 存储与元数据，唯一持久卷）。
- 关键配置：管理员账密（密级，见 Vaultwarden）；仓库/代理/缓存策略经 Web 控制台或 REST API 维护，持久化在 `/nexus-data`。
- 出口回源：拉取公网源（google/github/docker/pypi）经 mykng Clash Meta `:7890` 代理（见 infra-monitor 代理链路配置）。

### 发布/升级

- 无流水线；升级时替换镜像 tag（如 `sonatype/nexus3:3.91.1` → 新版本）重建容器，数据卷保留。
- **升级注意**：Nexus 3 不能跨大版本直接跳（OrientDB schema 迁移有顺序要求），需按官方升级路径逐版本过渡；升级前必须完整备份 `/root/nexus/data`。
- 仓库配置变更在 Web 控制台操作（Repository → 各仓库的 proxy URL / group 成员 / 缓存策略），持久化在数据卷中，重建容器不丢失。

### 回滚

- 镜像回退：改回旧 tag 重建容器即可，`/root/nexus/data` 不动。
- 数据回退：`/nexus-data` 目录整体备份（建议停写后 tar，即 `docker stop nexus` → tar → start），异常时停容器回放目录再启动。

## 核心功能与使用

### 功能清单

- **依赖统一代理**：Maven/npm/PyPI/Docker 全走 Nexus——构建可复现、不依赖各机器外网质量。
- **内部包托管**：`npm-hosted`/`pypi-hosted`/`maven-releases`/`maven-snapshots`/`docker-hosted`/`nuget-hosted` 发布内部制品。
- **缓存加速**：365 天制品缓存 + 每周预热——CI 构建近乎本地速度（react 实测 19s → 0.08s）。
- **多源容错聚合**：group 内多 proxy 互备（如 pypi 三源、npm 双源），任一上游抖动不断供。
- **Docker 镜像加速**：内网 `docker pull` 基础镜像走 docker-public——不挤公网带宽、不受 Hub 限流。
- **REST API**：`/service/rest/v1/...` 查询仓库/组件/状态——脚本化巡检用（warmup 脚本即基于 API 探测）。

### 典型操作路径

1. **查/改仓库配置**：登录 `https://nexus.marschat.online` 控制台 → 左侧齿轮（Server administration）→ Repositories → 点开仓库编辑 proxy URL/缓存策略/group 成员。
2. **看缓存命中**：控制台 Browse 菜单 → 选仓库 → 按路径浏览已缓存制品；或 Search 按格式/名称检索组件。
3. **验证服务状态**：`curl http://192.168.31.105:8081/service/rest/v1/status`（匿名可探）；列仓库 `curl .../service/rest/v1/repositories`。
4. **构建端接入**：Maven 改 `pom.xml`/`settings.xml` 指向 `maven-public`；npm 设 registry=`npm-public`；pip 用 `-i` 指 `pypi-public`；docker 配 `daemon.json`（各端内网直连细节见 nexus-lan.md）。
5. **发布内部制品**：`mvn deploy` / `npm publish` / `docker push` 指向对应 hosted 仓库（凭证见 Vaultwarden）。
6. **查缓存预热结果**：腾讯云2号 `tail /var/log/nexus-warmup.log`（每周日 03:30 运行后查看各格式预热包数与耗时）。

## 依赖与关联

- 依赖：
  - **出口代理**：回源公网（google/github/docker/pypi）经各机 Clash Meta `:7890`（见 infra-monitor 代理链路配置）。
  - **存储卷**：mykng `/root/nexus/data`（blob + 元数据），唯一持久化位置，务必纳入备份。
  - **腾讯云2号 nginx**：公网 HTTPS 终止与反代（nexus_backend / nexus_backend_docker）。
- 被依赖/关联系统：
  - **全部 Maven/npm 构建**：kb-ops、infra-monitor、portal、mykng 等 `pom.xml`/`package.json` 指向本 Nexus。
  - **Woodpecker CI**：CI 容器内编译直接消费 Nexus 私服。
  - **Docker 镜像拉取**：内网机器经 8082/8083 拉基础镜像（见 nexus-lan.md）。

## 运维要点

- 启停：`docker start/stop/restart nexus`（基础设施层，手工管理，不归应用流水线）。注意 Nexus 3 启动需 1~2 分钟才能就绪，探活以 `/service/rest/v1/status` 返回 200 为准。
- 日志：`docker logs -f nexus`；Nexus 自身日志在宿主机 `/root/nexus/data/log/karaf.log`（滚动）。
- 数据与备份：`/root/nexus/data` 整目录（blob + OrientDB 元数据）定期备份；冷备方式：`docker stop nexus` → `tar czf nexus-data-$(date +%F).tgz -C /root/nexus data` → `docker start nexus`。重要版本升级前必做一次；建议每季度做一次还原演练验证备份可用。缓存预热日志在腾讯云2号 `/var/log/nexus-warmup.log`。
- 存储清理：代理缓存的旧版本组件会持续膨胀——控制台可按仓库建 Cleanup Policies（按组件最后下载时间/版本数），再配 Tasks → "Admin - Cleanup repositories" + "Admin - Repair - Rebuild blob store list" 定期执行（当前实例策略以控制台为准，未见独立配置文件）。
- 常用 REST（匿名或带凭证，凭证见 Vaultwarden）：
  - `GET /service/rest/v1/status` — 健康探针
  - `GET /service/rest/v1/repositories` — 仓库清单（含 format/type）
  - `GET /service/rest/v1/search?repository=maven-public&name=<组件名>` — 查缓存是否命中
  - `GET /service/rest/v1/security/users` — 用户管理（需管理员权限）
- 凭据安全：Nexus 管理员账号与发布账号为密级，**一律见 Vaultwarden（vault.marschat.online）或 infrastructure-map 技能**；Maven/npm 发布用的 `settings.xml`/`_authToken` 亦不得明文入文档。
- 常见问题：
  - 公网拉取慢：先确认缓存预热是否覆盖该包；未覆盖的首次拉取会经代理回源。
  - `negativeCache` 误伤：若曾回源失败被缓存，清对应仓库 negative cache 或等 `metadataMaxAge` 过期。
  - Docker 推送 443/8083 不通：确认客户端 `daemon.json` 已配置 insecure-registry 或走 nginx TLS（见 nexus-lan.md）。
  - 某上游源失效：group 内多个 proxy 互备，控制台停用坏源即可，构建端无需改动。
  - 启动后 503：Nexus 3 冷启动需 1~2 分钟（OrientDB 恢复），勿急着重启；`docker logs -f nexus` 看到 `Started Sonatype Nexus` 即就绪。
  - 磁盘膨胀：代理缓存长期累积，按运维要点建 Cleanup Policy 定期清理；`/root/nexus/data` 所在分区建议预留 2 倍现有体积。
  - 上传大制品失败：默认请求体限制，公网入口检查腾讯云2号 nginx `client_max_body_size` 配置。
  - 匿名拉取 401：仓库匿名访问权限被关闭时构建端需带凭证——排查顺序：控制台 Security → Anonymous 权限 → 各仓库 Read 权限。

## 变更记录

- 2026-09-05 v2 补全设计/部署/使用三维度（新增系统设计节：hosted/proxy/group 代理链按 REST API 实采 23 个仓库重写；部署节按 docker inspect 实采补 `/root/nexus/data` 卷挂载与 bridge 网络）
- 2026-09-05 v1 首次生成（portal 文档补全任务，AI 基于实采 docker ps + 腾讯云2号 nginx 配置 + infra-monitor 缓存策略生成）
