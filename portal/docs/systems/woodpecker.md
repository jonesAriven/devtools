# Woodpecker CI

> devtools 全栈的核心 CI/CD 引擎。所有应用层服务（kb-ops / infra-monitor / portal / mykng 等）的代码变更都通过 Woodpecker 流水线完成"编译 → 产物推送 → 目标机部署 → 健康检查"，禁止手动 `docker compose up`。访问 https://woodci.marschat.online。

## 基本信息

| 项 | 值 |
|----|----|
| 分类 | 基础设施 / 核心 CI（自托管 CI 平台） |
| 版本 | Woodpecker **v3**（实测运行 `woodpeckerci/woodpecker-server:v3` / `woodpecker-agent:v3`，v1 实采版本号 3.16.0），元数据库 PostgreSQL 16（`postgres:16-alpine`） |
| 部署位置 | 主机 mykng（192.168.31.105），compose project `woodpecker`，容器 `woodpecker-server`（8000 + gRPC 9002→9000）、`woodpecker-agent`、`woodpecker-db`（5433→5432） |
| 源码位置 | 开源组件，官方仓库 github.com/woodpecker-ci/woodpecker；本实例流水线脚本集 `D:\huliang\java\ideaworkspace\devtools\woodScript\`（mykng 上 `/root/devtools/woodScript`），compose 定义 `devtools/woodpecker/docker-compose.yml` |
| CI/CD | 自身即 CI；由 `trigger-pipeline.py` 经 Woodpecker REST API 触发，仓库 `/root/devtools`（Gitee + GitHub 双推，push Webhook 触发） |
| 技术栈 | Woodpecker Server/Agent (Go) + PostgreSQL 16 + appleboy/drone-ssh 部署 + Maven/pnpm 构建 + Docker Compose |

## 访问入口

- 公网：`https://woodci.marschat.online`（经腾讯云2号 nginx 443 反代，回源走 Tailscale）
- 内网：`http://192.168.31.105:8000/`
- Tailscale：`http://100.93.36.113:8000/`
- 登录方式：GitHub OAuth（server 环境变量 `WOODPECKER_GITHUB` + `WOODPECKER_GITHUB_CLIENT/SECRET`）；API Token 见 Vaultwarden / infrastructure-map 技能（脚本 `trigger-pipeline.py` 内置默认值，可用 `WOODPECKER_TOKEN` 覆盖）。

## 全链路

```
浏览器/脚本 → 腾讯云2号 nginx(443) → http://100.93.36.113:8000 (woodpecker-server)
  ├─ Webhook: Gitee/GitHub push → 触发 pipeline
  └─ REST API: trigger-pipeline.py / check-pipeline.py 调 /api/...

流水线执行（Agent 经 gRPC 9000 领任务，起构建容器跑步骤）:
  ci/build-*.sh  (maven/node 镜像内编译)
    → 产物推 /mnt/shared/woodScript/publish/<项目>-latest.tar.gz
  appleboy/drone-ssh (携 ssh_key_mykng Secret SSH 到 mykng)
    → cd/deploy-*.sh → 解压 → docker compose 重建 → 健康检查 24×10s
```

## 系统设计

### 组件架构

Woodpecker 是 Drone 社区分支的轻量自托管 CI（Go 单二进制），核心三角色：

- **Server**（`woodpecker-server`）：Web UI + REST API + Webhook 接收，持久化到 PostgreSQL，经 gRPC（容器内 9000，宿主机映射 9002 以避开 MinIO 9000-9001）向 Agent 派发任务。
- **Agent**（`woodpecker-agent`）：构建代理，领到任务后用本机 Docker daemon（挂载 `/var/run/docker.sock`）按 `.woodpecker.yml` 逐步骤起容器执行。
- **DB**（`woodpecker-db`）：PostgreSQL 16 存储 pipeline/build/secret/user 元数据。

本实例资源画像（compose 明确限制）：Server 0.5 核/256M、Agent 2 核/1.5G（`WOODPECKER_MAX_PROCS=1`，同时只跑 1 个构建）、DB 0.3 核/128M，构建容器内存上限 1G（+512M swap）。

### 我们的集成设计

- **流水线定义**：单文件统一编排——仓库根 `.woodpecker.yml` 定义 6 条 `build→deploy` 链（mykng / kb-ops / kb-ops-web / infra-web / infra-mon / active-mgr 等），用 `depends_on` 控制串并行（如 kb-ops 部署排在 mykng 之后，保证 kb-app project 内启动顺序）；`when.evaluate: DEPLOY_TARGET == "..."` 实现一个文件按参数选择性执行，改串并行不用改脚本。
- **步骤结构**：每条链两步——`*-build`（maven:3.9-eclipse-temurin-21 或 node:20-slim 镜像内跑 `woodScript/ci/build-*.sh`，挂载 `/mnt/shared` 与宿主缓存目录）+ `*-deploy`（appleboy/drone-ssh）；首步 `sync-ci-scripts`（bash:5.2）负责清空并同步 woodScript 脚本、各项目 compose 文件与 `platform/nacos/init-nacos.sh` 到共享目录，并做 `bash -n` 语法检查。
- **触发方式**：① Gitee/GitHub push Webhook 自动触发（`event: push`）；② `trigger-pipeline.py` 经 REST API 手动触发（`event: manual`，带 `DEPLOY_TARGET` 环境变量选择目标，默认分支 dev）；`check-pipeline.py` 查询状态与日志。
- **Secrets 机制**：敏感信息全部走 Woodpecker 内置 Secrets（仓库级），部署步骤 `settings.key: { from_secret: ssh_key_mykng }` 注入 mykng SSH 私钥给 drone-ssh——流水线文件与日志中均不出现明文；Server 侧的 `WOODPECKER_AGENT_SECRET`（Server↔Agent gRPC 鉴权）、`WOODPECKER_GRPC_SECRET`、OAuth Client Secret 均由 mykng `.env` 注入 compose。
- **drone-ssh 部署模式**：部署步骤用 `appleboy/drone-ssh` 插件镜像 SSH 到目标机执行 `cd/deploy-*.sh`（`host: 192.168.31.105`，`command_timeout: 30m`，`script_stop: false`）。配套两个关键设计：`lib-deploy.sh` 里 `trap '' TERM` 防止 drone-ssh 超时发出的 SIGTERM 中断部署半途状态；产物与脚本经共享目录 `/mnt/shared/woodScript/`（sync-ci-scripts 步骤先同步过去）流转，CI 容器与目标机不直传。
- **Webhook 与仓库布局**：代码仓库 mykng `/root/devtools`（Gitee + GitHub 双推），push 任一远端即触发；另有独立仓库 workcheck-python（repo_id=2）自管流水线。日志与构建记录保留策略（7 天 / 30 天）控制小主机磁盘占用。
- **为什么选它**：比 Jenkins/GitLab CI 轻得多（三容器即可跑）、YAML 配置简单、原生支持 Docker 后端与插件生态（drone-ssh 直接复用）、对 Gitee/GitHub 双仓库友好；mykng 只有 3 核，资源分层限制后 CI 与业务服务可共存。

## 部署与发布

### 编排与位置

- compose 文件：`D:\huliang\java\ideaworkspace\devtools\woodpecker\docker-compose.yml`（mykng 上对应路径同目录），compose project `woodpecker`，专用网络 `woodpecker_woodpecker-network`（bridge，禁用 IPv6 避免 DNS 解析问题）。
- 首次部署：`bash deploy.sh --init && bash deploy.sh --start`（同目录 `deploy.sh`）；后续手动 `docker compose -p woodpecker up -d`。
- 反代配置：腾讯云2号 nginx（`woodpecker-nginx.conf`）443 TLS 终止，upstream 指向 mykng 8000（经 Tailscale 回源）。
- 实采（docker inspect）：

| 容器 | 镜像 | 端口 | 卷挂载 | 重启策略 |
|------|------|------|--------|----------|
| woodpecker-server | woodpeckerci/woodpecker-server:v3 | 8000→8000（UI/API）、9002→9000（gRPC） | named volume `woodpecker_woodpecker-data` → `/var/lib/woodpecker`；`/var/run/docker.sock` | unless-stopped |
| woodpecker-agent | woodpeckerci/woodpecker-agent:v3 | 无对外端口 | `/var/run/docker.sock`（构建起容器用） | unless-stopped |
| woodpecker-db | postgres:16-alpine | 5433→5432 | named volume `woodpecker_woodpecker-db-data` → `/var/lib/postgresql/data` | unless-stopped |

- 三容器均在 `woodpecker_woodpecker-network` 网络内互联（不接 platform-net，独立于 platform 基础设施层）。
- 关键环境变量（只列名与用途，值在 mykng `.env` / Vaultwarden）：`WOODPECKER_HOST`（对外地址）、`WOODPECKER_SERVER_ADDR`/`WOODPECKER_GRPC_ADDR`、`WOODPECKER_DATABASE_DRIVER/DATASOURCE`（连 woodpecker-db）、`WOODPECKER_GITHUB` + `WOODPECKER_GITHUB_CLIENT/SECRET`（OAuth）、`WOODPECKER_AGENT_SECRET`/`WOODPECKER_GRPC_SECRET`（内部鉴权）、`WOODPECKER_ADMIN`、`WOODPECKER_LOGS_RETENTION=7` / `WOODPECKER_PIPELINE_RETENTION=30`（日志/构建记录保留期）、Agent 侧 `WOODPECKER_MAX_PROCS=1`、`WOODPECKER_BACKEND=docker`。

### 配置清单（数据流）

- 产物共享目录：`/mnt/shared/woodScript/publish/`（CI 构建产物）+ `/mnt/shared/woodScript/cd/`（部署脚本），从 mykng 挂载进 CI 容器。
- 部署根目录：目标机 `/root/kb-deploy/`（`DEPLOY_BASE`，前端 dist 与 nginx.conf 落地处）。
- 代码仓库：mykng `/root/devtools`（Gitee + GitHub 双推）。

### 发布/升级（Woodpecker 自身）

基础设施层，不在应用流水线管理范围。升级改 `docker-compose.yml` 中镜像 tag 后 `docker compose -p woodpecker up -d` 重建；升级前对 `woodpecker-db` 做 `pg_dump`。

### 回滚

- 镜像回退：compose 中改回旧 tag 重建即可（Server 数据在 named volume，不受影响）。
- 元数据回退：`pg_dump` 备份文件回导 `woodpecker-db`。

## 核心功能与使用

### 功能清单

- **push 自动构建**：Gitee/GitHub push 即触发对应项目 build→deploy 链——日常开发的默认发布方式。
- **手动定点发布**：`trigger-pipeline.py <目标> --wait` 按参数只部署指定项目——紧急修复/重发场景。
- **构建观测**：Web UI 与 `check-pipeline.py`（状态/日志/`--watch` 持续监控）——排查部署失败第一步。
- **脚本语法门禁**：`sync-ci-scripts` 步骤对全部 `.sh` 做 `bash -n` 检查，语法错误流水线直接失败——防止坏脚本进入部署阶段。
- **Secrets 托管**：部署 SSH 私钥等敏感值仓库级托管，流水线文件零明文。
- **构建资源治理**：单并发 + 内存上限 + 日志 7 天/构建记录 30 天保留——适配 3 核小主机。
- **产物留存**：构建产物统一落 `/mnt/shared/woodScript/publish/`——回滚重发的基础。

### 典型操作路径

1. **日常发布**：改代码 → push 到 Gitee/GitHub dev 分支 → Woodpecker 自动跑对应链 → `python woodScript/check-pipeline.py --watch` 观察到成功。
2. **手动重发**：`python woodScript/trigger-pipeline.py kb-ops --wait`（全量用 `all`）→ 结束后到 kb-ops 部署记录核对。
3. **失败排查**：`check-pipeline.py --log <编号>` 看步骤日志 → 常见卡点是健康检查 24×10s 不过（容器起慢/端口未就绪）。
4. **新增流水线目标**：`.woodpecker.yml` 加一组 build/deploy 步骤 + `woodScript/ci|cd` 各加一个脚本 → push 生效。

### 统一 CI/CD 流程细节

**构建（ci/build-*.sh，CI 容器内 maven/node 镜像执行）**

| 脚本 | 工具 | 产物 |
|------|------|------|
| build-mykng.sh | Maven（5 微服务 `-T 2C` 并行，MAVEN_OPTS -Xmx2048m） | mykng-latest.tar.gz |
| build-kb-ops.sh / build-portal-server.sh / build-active-manager.sh / build-infra-monitor.sh | Maven | `<项目>-latest.tar.gz` |
| build-kb-web.sh / build-kb-ops-web.sh / build-portal-web.sh / build-infra-monitor-web.sh | pnpm（node:20-slim，pnpm-store 挂宿主机 /var/cache/pnpm-store 复用缓存） | `<项目>-latest.tar.gz` |

统一流程：`编译 → collect_artifacts → publish_artifact`（`lib-build.sh` 提供，产物写失败即 exit 1，杜绝半截产物）。`setup_pnpm()` 读项目 `package.json` 的 `packageManager` 字段锁定 pnpm 版本，registry 指向本机 Nexus `192.168.31.105:8081/repository/npm-public/`。

**部署（cd/deploy-*.sh，drone-ssh 在目标机执行）**

| 脚本 | Compose Project | Compose 文件 | 健康检查 |
|------|----------------|--------------|----------|
| deploy-mykng.sh | kb-app | docker-compose.app.yml | localhost:8090/actuator/health |
| deploy-kb-ops.sh | kb-app | docker-compose.app.yml | localhost:8084/kb-ops/actuator/health |
| deploy-portal-server.sh | kb-app | docker-compose.app.yml | localhost:8087/portal/actuator/health |
| deploy-kb-web.sh | kb-web | docker-compose.web.yml | localhost:8091/health |
| deploy-kb-ops-web.sh | kb-web | docker-compose.web.yml | localhost:8093/health |
| deploy-portal-web.sh | kb-web | docker-compose.web.yml | localhost:8095/health |
| deploy-infra-monitor.sh | infra-monitor | infra-monitor-server/docker-compose.yml | localhost:8088/infra/actuator/health |
| deploy-infra-monitor-web.sh | kb-web | docker-compose.web.yml | localhost:8094/health |
| deploy-active-manager.sh | activecode | activation-code-server/docker-compose.yml | localhost:18080/activecode/login.html |

健康检查参数：最多 24 次 × 10 秒（`HEALTH_MAX_RETRIES`/`HEALTH_INTERVAL`，见 `env.sh`）。公共库分工：`env.sh`（SHARED_DIR/DEPLOY_BASE/GIT_REPO/NEXUS_NPM_REGISTRY 等变量）、`lib-build.sh`（collect_artifacts/publish_artifact/setup_pnpm）、`lib-deploy.sh`（442 行，含 `trap '' TERM` 与产物校验/compose 重建/健康检查原语）。

**compose 归属**：应用层按 `kb-app`（docker-compose.app.yml）/ `kb-web`（docker-compose.web.yml）/ `infra-monitor`（infra-monitor-server/docker-compose.yml）/ `activecode` 分 project 部署，project 内串行（depends_on 链）、project 间并行。

### 触发与监控脚本

```bash
cd /root/devtools
python woodScript/trigger-pipeline.py mykng            # 触发单项目（默认分支 dev）
python woodScript/trigger-pipeline.py mykng --wait     # 触发并监控到结束（推荐）
python woodScript/trigger-pipeline.py kb-ops --note "修复登录bug"
python woodScript/trigger-pipeline.py mykng --branch main
python woodScript/trigger-pipeline.py all              # 全量（部署所有项目）

python woodScript/check-pipeline.py 342            # 某次流水线（状态+全部步骤日志）
python woodScript/check-pipeline.py --log 342     # 只看日志
python woodScript/check-pipeline.py --watch 342   # 持续监控到结束
python woodScript/check-pipeline.py               # 不编号=最新一条
python woodScript/check-pipeline.py --recent 5    # 最近 N 条
```

脚本环境变量：`WOODPECKER_URL`（默认 https://woodci.marschat.online）、`WOODPECKER_TOKEN`（API Token，脚本内置默认值，可覆盖）——均为密级见 Vaultwarden。

支持的项目名（DEPLOY_TARGET）：`mykng`、`kb-ops`、`kb-ops-web`、`kb-web`、`infra-monitor`、`infra-monitor-web`、`active-manager`、`portal-web`、`portal-server`、`platform`（起 Kafka + 重启 MySQL GR 集群）、`all`。
> `workcheck-python` 不在此列：独立仓库（Woodpecker repo_id=2，分支 main），用自带流水线，不走 `trigger-pipeline.py`。

## 依赖与关联

- 依赖：
  - **PostgreSQL 16**（`woodpecker-db`，宿主机 5433）：存储流水线/构建/Secret 元数据。
  - **共享目录** `/mnt/shared/woodScript/`（publish + cd 脚本，经 SMB/NFS 从 mykng 挂载到 CI 节点）——产物与部署脚本的流转中枢。
  - **Nexus 私服**：Maven（`nexus.marschat.online/repository/maven-public/`）、npm（`192.168.31.105:8081/repository/npm-public/`）依赖源。
  - **Docker daemon**（mykng）：Agent 起构建容器、部署阶段 `docker compose` 重建容器。
  - **GitHub / Gitee**：OAuth 登录与代码仓库 Webhook 触发。
- 被依赖/关联系统：**所有应用层服务**（kb-ops、infra-monitor、portal、mykng、激活码等）的发布都依赖本流水线；其"部署记录"在 kb-ops 中可对应查询。

## 运维要点

- 重启策略分层（铁律）：
  - 平台层 `platform-*` → `restart: unless-stopped/on-failure` + daemon 重启后 `@reboot` 拉起。
  - 应用层 `kb-*/portal*/infra-monitor` → `restart: on-failure:5`，**且只能走流水线部署**，禁止手动 `docker compose up`。
- 日志查看：Woodpecker 界面查看构建/部署步骤日志；目标机容器日志 `docker logs <服务>` 或 mykng `obs-dozzle`（15500）。
- 数据与备份：元数据在 `woodpecker-db`（PostgreSQL 16）；重要变更前对 `woodpecker-db` 做 `pg_dump`。共享产物目录 `/mnt/shared/woodScript` 建议纳入备份。
- 凭据安全：Woodpecker Agent Secret、gRPC Secret、GitHub OAuth Secret、API Token、仓库 Secret `ssh_key_mykng` 均为密级，**一律见 Vaultwarden（vault.marschat.online）或 infrastructure-map 技能**，文档不写明文。
- 常见问题：
  - 部署卡在健康检查：`check-pipeline.py --log` 看对应步骤，多为容器启动慢或端口未就绪；24×10s 仍失败则回滚。
  - 产物缺失：确认 CI 阶段 `publish_artifact` 成功推到 `/mnt/shared/woodScript/publish/`；`sync-ci-scripts` 步骤失败（脚本语法检查不过）会导致后续全部跳过。
  - pnpm store 膨胀：流水线含 `cleanup-pnpm-store.sh`（距上次 <7 天跳过），必要时 `--force`（固定 pnpm@8.15.9，兼容 v6/v9 lockfile，@latest v10 会误删活跃包）。
  - 保留策略清理：日志 7 天 / 构建记录 30 天自动过期，查历史构建过期的属正常现象。
  - Agent 离线：`docker logs woodpecker-agent` 看 gRPC 连接，多为 `WOODPECKER_AGENT_SECRET` 与 server 不一致（.env 改动后只重启了单边）。
  - 脚本被中断在半途：确认 `lib-deploy.sh` 的 `trap '' TERM` 未被改动——drone-ssh 超时会发 SIGTERM，屏蔽后部署才能跑完。
  - 手动触发 401/403：`WOODPECKER_TOKEN` 过期或权限不足，见 Vaultwarden 更新脚本内置 Token。

## 变更记录

- 2026-09-05 v2 补全设计/部署/使用三维度（新增系统设计节：Server/Agent/DB 架构、Secrets 机制、drone-ssh 模式、`.woodpecker.yml` 统一编排；部署节按 woodpecker/docker-compose.yml + docker inspect 实采重写）
- 2026-09-05 v1 首次生成（portal 文档补全任务，AI 基于实采 docker ps + woodScript/README.md + 容器环境变量生成）
