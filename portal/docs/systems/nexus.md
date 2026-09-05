# Nexus 私服（公网入口）

> Sonatype Nexus Repository Manager 3 自建制品仓库，统一代理 Maven / npm / PyPI / Docker 等源，作为 devtools 全部构建的依赖中枢与缓存加速层。本篇从**公网域名入口**视角描述；同一实例的内网直连用法见 `nexus-lan.md`。

## 基本信息

| 项 | 值 |
|----|----|
| 分类 | 基础设施 / 制品仓库（开源组件自部署） |
| 版本 | Nexus Repository Manager **3.91.1**（`sonatype/nexus3:3.91.1`） |
| 部署位置 | 主机 mykng（192.168.31.105），容器 `nexus`，端口 `8081-8083`（8081=UI/REST+Maven/npm/PyPI，8082/8083=Docker registry） |
| 源码位置 | 开源组件，官方仓库 github.com/sonatype-nexus-community；本实例配置见 mykng 上 `/root` 下 nexus 数据卷（容器挂载） |
| CI/CD | 无（基础设施层，自部署；首次安装/重启走手工 `docker`，不归应用流水线管） |
| 技术栈 | Java（Nexus OSS 3）、OrientDB/D blob 存储、REST API v1 |

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

## 核心功能与使用

Nexus 作为"单一依赖源"，对外暴露若干仓库（repo），本实例实际配置与 infra-monitor 资产台账中的缓存策略一致：

- **Maven（maven-public / maven-releases / maven-snapshots）**：
  - `maven-public` 为 group 仓库（聚合 releases + snapshots + 代理中央仓库），是 Maven 构建的统一 `mirror`/`repository`。
  - 应用项目（如 kb-ops、infra-monitor）的 `pom.xml` 将 `repositories`/`pluginRepositories` 与 `distributionManagement` 全部指向 `https://nexus.marschat.online/repository/maven-public/`（releases/snapshots），**强制走 Nexus，禁用其他公网源**。
- **npm（npm-public）**：group 仓库（代理 npmjs + hosted），前端 pnpm 构建的 registry 指向 `192.168.31.105:8081/repository/npm-public/`（见 woodpecker 文档 `env.sh` 的 `NEXUS_NPM_REGISTRY`）。
- **PyPI（pypi）**：代理仓库，供 Python 构建/依赖安装。
- **Docker（docker group，8082/8083）**：托管/代理基础镜像，供内网 `docker pull` 加速（具体仓库名见控制台）。
- **缓存策略**（与本实例一致）：`negativeCache` 关闭（避免临时失败被缓存为不存在）、`contentMaxAge=525600`（制品缓存 365 天，制品不可变）、`metadataMaxAge=1440`（版本列表每天刷新）。
- **缓存预热**：`/home/liangzi/tools/nexus-warmup.sh`（腾讯云2号）每周日 03:30 执行，覆盖 npm(Top250+80 包)/maven(33)/pypi(47)/docker(22 个基础镜像)，日志 `/var/log/nexus-warmup.log`；实测 react 6.8MB 首次 19s → 缓存后 0.08s（约 237× 提速）。

## 依赖与关联

- 依赖：
  - **出口代理**：拉取公网源（google/github/docker/pypi）经各机 Clash Meta `:7890` 代理（见 infra-monitor 代理链路配置）。
  - **存储卷**：Nexus 数据卷（blob + 元数据）挂在 mykng 本地，建议纳入备份。
  - **腾讯云2号 nginx**：公网 HTTPS 终止与反代（nexus_backend / nexus_backend_docker）。
- 被依赖/关联系统：
  - **全部 Maven/npm 构建**：kb-ops、infra-monitor、portal、mykng 等 `pom.xml`/`package.json` 指向本 Nexus。
  - **Woodpecker CI**：CI 容器内编译直接消费 Nexus 私服。
  - **Docker 镜像拉取**：内网机器经 8082/8083 拉基础镜像（见 nexus-lan.md）。

## 运维要点

- 启停：`docker start/stop/restart nexus`（基础设施层，手工管理，不归应用流水线）。
- 日志：`docker logs -f nexus`；Nexus 自身 `sonatype-work/nexus3/log/` 在容器内数据卷。
- 数据与备份：blob 存储与元数据在 mykng 本地数据卷，建议定期备份；缓存预热日志在腾讯云2号 `/var/log/nexus-warmup.log`。
- 凭据安全：Nexus 管理员账号与发布账号为密级，**一律见 Vaultwarden（vault.marschat.online）或 infrastructure-map 技能**；Maven/npm 发布用的 `settings.xml`/`_authToken` 亦不得明文入文档。
- 常见问题：
  - 公网拉取慢：先确认缓存预热是否覆盖该包；未覆盖的首次拉取会经代理回源。
  - `negativeCache` 误伤：若曾回源失败被缓存，清对应仓库 negative cache 或等 `metadataMaxAge` 过期。
  - Docker 推送 443/8083 不通：确认客户端 `daemon.json` 已配置 insecure-registry 或走 nginx TLS（见 nexus-lan.md）。

## 变更记录

- 2026-09-05 首次生成（portal 文档补全任务，AI 基于实采 docker ps + 腾讯云2号 nginx 配置 + 源码/infra-monitor 缓存策略生成）
