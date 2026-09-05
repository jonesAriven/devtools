# Nexus 私服（内网直连）

> 与 `nexus.md` **同一个 Nexus 实例**（`sonatype/nexus3:3.91.1`，mykng 容器 `nexus`，端口 8081-8083）。本篇从**内网构建依赖**视角描述：局域网/Tailscale 机器如何绕过公网、直连 `192.168.31.105:8081` 拉取 Maven/npm/PyPI/Docker 制品，获得最低延迟、不挤占腾讯云2号公网带宽。公网入口与仓库代理链设计见 `nexus.md`。

## 基本信息

| 项 | 值 |
|----|----|
| 分类 | 基础设施 / 制品仓库（内网视角） |
| 版本 | 同 `nexus.md`：Nexus Repository Manager **3.91.1** |
| 部署位置 | 主机 mykng（192.168.31.105，内网 IP），容器 `nexus`（bridge 网络），`8081-8083` |
| 与 nexus.md 的关系 | **同一容器、同一数据卷（`/root/nexus/data`）**，仅访问入口/用途侧重不同（内网直连 vs 公网域名） |
| CI/CD | 无（基础设施层，自部署手工管理） |

## 访问入口（内网视角）

- 内网直连（推荐构建用）：`http://192.168.31.105:8081/`
- Tailscale：`http://100.93.36.113:8081/`
- Docker registry 内网直连：`http://192.168.31.105:8082/`（拉取）、`http://192.168.31.105:8083/`（推送/拉取，对应公网 `nexus_backend_docker`）
- 公网（对照）：`https://nexus.marschat.online`（见 `nexus.md`）

## 全链路（内网）

```
内网构建机 / Tailscale 节点:
  Maven  → http://192.168.31.105:8081/repository/maven-public/
  npm    → http://192.168.31.105:8081/repository/npm-public/
  PyPI   → http://192.168.31.105:8081/repository/pypi-public/
  Docker → http://192.168.31.105:8082/ 或 :8083/ (docker-public group)

（不经过腾讯云2号，也不经公网带宽；局域网路由直达 mykng）

回源路径（仅缓存未命中时）:
Nexus → mykng 本机 Clash Meta :7890 → 公网上游（maven-central/npmjs/pypi/Docker Hub）
```

说明：Tailscale 节点把 `192.168.31.105` 换成 `100.93.36.113` 即可，端口与仓库路径不变；同一构建配置在"局域网/Tailscale/公网域名"三张网内只需改 host 部分。

## 系统设计

### 为什么内网+公网双入口

同一实例开两个入口是刻意的分流设计：

1. **高频构建走内网**：Maven/npm/pip/docker 依赖拉取是最高频的网络行为，CI（Woodpecker 在 mykng 本机）与各内网开发机直连 8081，延迟最低、零公网带宽消耗，也不依赖腾讯云2号 nginx 的可用性。
2. **公网域名兜底**：外部环境（移动办公、腾讯云2号上的 warmup 脚本）经 `nexus.marschat.online` HTTPS 访问同一份数据，配置与缓存完全共享。
3. **Docker 双通道**：内网 HTTP 直连 8082/8083（配合客户端 `insecure-registries`，免 TLS 证书管理）；公网走 nginx TLS 443。

### 仓库入口对照（REST 实采）

| 用途 | group 仓库（构建端唯一 URL） | 成员（hosted/proxy） |
|------|------------------------------|----------------------|
| Maven | `maven-public` | maven-releases、maven-snapshots（hosted）；maven-central、maven-aliyun（proxy） |
| npm | `npm-public` | npm-hosted（hosted）；npm-proxy、npm-mirror（proxy） |
| PyPI | `pypi-public` | pypi-hosted（hosted）；pypi-proxy、pypi-aliyun、pypi-tuna（proxy） |
| Docker | `docker-public`（8082/8083） | docker-hosted（hosted）；docker-daocloud、docker-hub-direct（proxy） |

缓存策略与预热脚本同 `nexus.md`（contentMaxAge 365 天、每周日 03:30 预热 npm/maven/pypi/docker 常用包）。

### 内网构建端接入配置

- **Maven（`settings.xml`）**：`<mirror>` 指向 `http://192.168.31.105:8081/repository/maven-public/`，或 `pom.xml` 的 `<repository>` 指向同一地址。应用项目（kb-ops/infra-monitor/portal/mykng）已强制走 Nexus，内网直连即生效；发布内部制品用 `distributionManagement` 指 `maven-releases`/`maven-snapshots`。
- **npm / pnpm**：registry 指向 `http://192.168.31.105:8081/repository/npm-public/`（与 woodpecker `env.sh` 的 `NEXUS_NPM_REGISTRY` 一致）；前端流水线 `setup_pnpm()` 即锁定此地址；发布内部包用 `npm publish --registry .../npm-hosted/`（凭证见 Vaultwarden）。
- **PyPI（pip）**：`pip install -i http://192.168.31.105:8081/repository/pypi-public/simple/ ...`；多代理源（pypi-proxy/pypi-aliyun/pypi-tuna）由 group 内部聚合，构建端无需感知。
- **Docker**：内网 `daemon.json` 将 `192.168.31.105:8082`（或 `:8083`）加入 `insecure-registries`（HTTP 内网直连无需 TLS），`docker pull 192.168.31.105:8082/<image>` 走缓存；推送内部镜像 `docker push 192.168.31.105:8083/<image>`（凭证见 Vaultwarden）。

### 入口选择速查

| 场景 | 用哪个入口 | 理由 |
|------|-----------|------|
| mykng 本机 / 内网机器日常构建 | `192.168.31.105:8081` | 延迟最低，不占公网带宽 |
| Woodpecker CI 构建容器 | 同上（env.sh 固化） | 与部署机同主机，直连最快 |
| Tailscale 节点（腾讯云2号 warmup 等） | `100.93.36.113:8081` | VPN 直达 mykng，绕过公网 nginx |
| 外部/移动办公 | `nexus.marschat.online` | HTTPS 公网域名（见 nexus.md） |
| Docker pull/push | 内网 `:8082/:8083`；公网经域名 `nexus_backend_docker` | 见 nexus.md 全链路 |

### 各端接入验证命令

```bash
# Maven
mvn -s settings.xml dependency:resolve          # 观察日志中下载 URL 是否 8081
# npm
npm config get registry                          # 应输出 .../npm-public/
# pip
pip download requests -d /tmp -i http://192.168.31.105:8081/repository/pypi-public/simple/
# Docker
docker pull 192.168.31.105:8082/alpine:3.20     # 成功即 insecure-registry 生效
```

### 首次拉取与缓存命中判定

- **首次拉取**（未命中缓存）：经 mykng Clash `:7890` 代理回源公网，速度取决于上游——此时日志/控制台 Browse 会新增该制品。
- **命中缓存**：直接读 `/root/nexus/data` blob，内网千兆下大包也在秒级。
- **判定方法**：对比两次下载耗时，或控制台 Browse 查看制品是否已存在；REST `GET /service/rest/v1/search?repository=maven-public&name=<名>` 也可查询。

### 与公网入口的一致性

两个入口指向**同一份数据与配置**（同容器同卷）：

- 缓存共享：任一入口拉过的包，另一入口立即可见——内网预热的成果公网访问同样受益，反之亦然。
- 配置同步：控制台在内网或公网改动仓库配置，无同步延迟（同一实例）。
- 凭据通用：同一套账号在两个入口均可用（传输层不同：内网 HTTP 明文、公网 HTTPS TLS 加密）。

## 部署与发布

- 与 `nexus.md` 完全同一容器：`nexus`（`sonatype/nexus3:3.91.1`，bridge 网络，`restart: unless-stopped`，卷 `/root/nexus/data` → `/nexus-data`，端口 8081-8083 直映射）。
- 本篇无独立部署物——内网入口是同一实例的端口直暴露，无需额外配置；运维/升级/回滚/备份全部见 `nexus.md` 部署与发布节。
- 客户端侧唯一需要维护的是各构建工具的源配置（`settings.xml`/`.npmrc`/`pip.conf`/`daemon.json`），属各机器自身配置管理，建议纳入 infra-monitor 的 config 台账登记。

## 核心功能与使用

### 功能清单（内网视角）

- **内网构建提速**：CI 与开发机直连 8081，缓存命中后近乎本地速度。
- **不依赖公网/代理**：缓存命中时构建完全不回源——公网或 Clash 故障不影响存量依赖拉取。
- **内部制品发布**：`maven-releases`/`npm-hosted`/`pypi-hosted`/`docker-hosted` 经内网直接 `mvn deploy`/`npm publish`/`docker push`（发布凭证见 Vaultwarden）。
- **Docker 镜像内网分发**：基础镜像 `docker pull 192.168.31.105:8082/...`，不受 Docker Hub 限流影响。
- **多源容错**：group 聚合多 proxy（maven-central+aliyun、npm 双源、pypi 三源、docker 双源），内网构建端单 URL 即享受容错。

### 典型操作路径

1. **新机器接入**：编辑 Maven `settings.xml` / npm `.npmrc` / pip `pip.conf` / docker `daemon.json`（配置见上文"内网构建端接入配置"）→ 跑一次构建验证依赖来自内网。
2. **验证内网可达**：`curl -s -m 5 http://192.168.31.105:8081/service/rest/v1/status`（匿名可取状态）；`curl -s .../service/rest/v1/repositories` 列仓库确认 group 仓库名。
3. **预热某常用包**：外网可达时手动拉一次（如 `docker pull 192.168.31.105:8082/<镜像>`、`pip download <包>`），即进缓存供全员复用。
4. **内网 CI 构建验证**：在 Woodpecker 触发任一前端流水线 → 构建日志确认 registry 走 `192.168.31.105:8081/repository/npm-public/`。
5. **发布内部制品**：`mvn deploy`（maven-releases/snapshots）或 `npm publish`（npm-hosted）→ 控制台 Browse 确认上架 → 团队经 group 仓库自动可拉。

## 依赖与关联

- 依赖：
  - **mykng Clash 代理**（`:7890`）：Nexus 缓存未命中回源公网时经此出口（google/github/docker/pypi → Proxy）。
  - **存储卷**：同 nexus.md，mykng `/root/nexus/data`。
- 被依赖/关联系统：
  - **所有内网构建机 / Tailscale 节点**：Maven、pnpm、pip、docker 默认源指向此处。
  - **Woodpecker CI 节点**：CI 容器内编译消费 Nexus（mykng 本机直连，延迟最低）。
  - **infra-monitor**：Nexus 缓存策略/预热登记为其 config 台账，8081 状态探活纳入巡检。
  - 与 `nexus.md` 互为入口：公网域名用于外部/移动访问，内网直连用于高频构建。

## 运维要点

- 验证内网可达：`curl -s -m 5 http://192.168.31.105:8081/service/rest/v1/status`（匿名可取状态；个别接口需权限返回空属正常）。
- 启停/日志/备份/升级：与 `nexus.md` 完全一致（同一容器），此处不重复。
- 凭据安全：Nexus 管理员/发布账号与 Maven/npm/PyPI/Docker 凭证均为密级，**一律见 Vaultwarden（vault.marschat.online）或 infrastructure-map 技能**；内网 `settings.xml`/`_authToken`/`daemon.json` 中的明文不得入文档。
- 常见问题：
  - 内网 8081 不通：确认 mykng 防火墙/lan 路由允许 8081-8083，且非经公网回环。
  - Docker HTTP 被拒：内网直连需在客户端 `daemon.json` 声明 `insecure-registries` 并重启 docker daemon（`systemctl restart docker`），或改用公网 HTTPS 域名。
  - 某包首次极慢：首次回源经代理，预热脚本未覆盖；可手动触发一次让其进缓存。
  - 构建端 URL 写成 `pypi`/`npm` 单仓库而非 group：统一改用 `pypi-public`/`npm-public`/`maven-public`/`docker-public`，享受多源聚合。
  - Tailscale 节点 8081 不通：确认目标机 Tailscale 已连（`ping 100.93.36.113`），mykng 侧 `tailscale0` 接口未被防火墙规则排除。
  - 内网拉取到的包版本旧：group 命中 hosted 优先于 proxy，内部包与代理包同名时以 hosted 为准；确认是否误发到 hosted。

## 变更记录

- 2026-09-05 v2 补全设计/部署/使用三维度（新增"为什么双入口"设计与仓库入口对照表，group 仓库名按 REST API 实采修正为 maven-public/npm-public/pypi-public/docker-public；与 nexus.md 同源实例）
- 2026-09-05 v1 首次生成（portal 文档补全任务，AI 基于实采 docker ps + 内网入口与 nexus.md 同源实例生成）
