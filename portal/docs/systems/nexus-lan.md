# Nexus 私服（内网直连）

> 与 `nexus.md` **同一个 Nexus 实例**（`sonatype/nexus3:3.91.1`，mykng 容器 `nexus`，端口 8081-8083）。本篇从**内网构建依赖**视角描述：局域网/ Tailscale 机器如何绕过公网、直连 `192.168.31.105:8081` 拉取 Maven/npm/PyPI/Docker 制品，获得最低延迟、不挤占腾讯云2号公网带宽。公网入口与仓库清单见 `nexus.md`。

## 基本信息

| 项 | 值 |
|----|----|
| 分类 | 基础设施 / 制品仓库（内网视角） |
| 版本 | 同 `nexus.md`：Nexus Repository Manager **3.91.1** |
| 部署位置 | 主机 mykng（192.168.31.105，内网 IP），容器 `nexus`，`8081-8083` |
| 与 nexus.md 的关系 | **同一容器、同一数据卷**，仅访问入口/用途侧重不同（内网直连 vs 公网域名） |
| CI/CD | 无（基础设施层，自部署） |

## 访问入口（内网视角）

- 内网直连（推荐构建用）：`http://192.168.31.105:8081/`
- Tailscale：`http://100.93.36.113:8081/`（与腾讯云2号同机，Tailscale 直接到 mykng）
- Docker registry 内网直连：`http://192.168.31.105:8082/`（拉取）、`http://192.168.31.105:8083/`（推送/拉取，对应公网 `nexus_backend_docker`）
- 公网（对照）：`https://nexus.marschat.online`（见 `nexus.md`）

## 全链路（内网）

```
内网构建机 / Tailscale 节点:
  Maven  → http://192.168.31.105:8081/repository/maven-public/
  npm    → http://192.168.31.105:8081/repository/npm-public/
  PyPI   → http://192.168.31.105:8081/repository/pypi/   (仓库名以控制台为准)
  Docker → http://192.168.31.105:8082/ 或 :8083/

（不经过腾讯云2号，也不经公网带宽；仅 mykng 本机 nginx/lan 路由可达）
```

## 核心功能与使用（内网构建配置）

本实例对内网构建的意义是"把公网源收敛到一台机器并缓存"，各语言/工具需把源指向内网 Nexus：

- **Maven（`settings.xml`）**：将 `<mirror>` 指向 `http://192.168.31.105:8081/repository/maven-public/`，或用 `pom.xml` 的 `repository` 指向同一地址。应用项目（kb-ops/infra-monitor/portal/mykng）已强制走 Nexus，内网直连即生效。
- **npm / pnpm**：registry 指向 `http://192.168.31.105:8081/repository/npm-public/`（与 woodpecker `env.sh` 的 `NEXUS_NPM_REGISTRY` 一致）；前端流水线 `setup_pnpm()` 即锁定此地址。
- **PyPI（pip）**：`pip install -i http://192.168.31.105:8081/repository/pypi/simple/ ...`（仓库名以控制台为准）。
- **Docker**：内网 `daemon.json` 将 `192.168.31.105:8082`（或 `:8083`）加入 `insecure-registries`（HTTP 内网直连无需 TLS），`docker pull 192.168.31.105:8082/<image>` 走缓存。
- **缓存收益**：依赖首次回源（经 mykng Clash `:7890` 代理）后缓存 365 天（`contentMaxAge`），内网后续拉取近乎本地速度；每周日 03:30 有 `nexus-warmup.sh` 预热常用包（npm Top250+/maven 33/pypi 47/docker 22）。

## 依赖与关联

- 依赖：
  - **mykng Clash 代理**（`:7890`）：Nexus 回源公网时经此出口（google/github/docker/pypi → Proxy）。
  - **存储卷**：同 nexus.md，mykng 本地数据卷。
- 被依赖/关联系统：
  - **所有内网构建机 / Tailscale 节点**：Maven、pnpm、pip、docker 默认源指向此处。
  - **Woodpecker CI 节点**：CI 容器内编译消费 Nexus（内网可达）。
  - 与 `nexus.md` 互为入口：公网域名用于外部/移动访问，内网直连用于高频构建。

## 运维要点

- 验证内网可达：`curl -s -m 5 http://192.168.31.105:8081/service/rest/v1/status`（匿名可取状态；个别接口需权限返回空属正常）。
- 启停：`docker restart nexus`（基础设施层手工管理）。
- 日志：`docker logs -f nexus`；容器内 `sonatype-work/nexus3/log/`。
- 数据与备份：同 nexus.md（blob + 元数据在 mykng 数据卷）。
- 凭据安全：Nexus 管理员/发布账号与 Maven/npm/PyPI/Docker 凭证均为密级，**一律见 Vaultwarden（vault.marschat.online）或 infrastructure-map 技能**；内网 `settings.xml`/`_authToken`/`daemon.json` 中的明文不得入文档。
- 常见问题：
  - 内网 8081 不通：确认 mykng 防火墙/lan 路由允许 8081-8083，且非经公网回环。
  - Docker HTTP 被拒：内网直连需在客户端 `daemon.json` 声明 `insecure-registries`，或改用公网 HTTPS 域名。
  - 某包首次极慢：首次回源经代理，预热脚本未覆盖；可手动触发一次让其进缓存。

## 变更记录

- 2026-09-05 首次生成（portal 文档补全任务，AI 基于实采 docker ps + 内网入口与 nexus.md 同源实例生成；与 nexus.md 为同一实例的不同视角）
