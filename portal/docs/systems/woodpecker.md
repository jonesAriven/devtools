# Woodpecker CI

> devtools 全栈的核心 CI/CD 引擎。所有应用层服务（kb-ops / infra-monitor / portal / mykng 等）的代码变更都通过 Woodpecker 流水线完成"编译 → 产物推送 → 目标机部署 → 健康检查"，禁止手动 `docker compose up`。访问 https://woodci.marschat.online。

## 基本信息

| 项 | 值 |
|----|----|
| 分类 | 基础设施 / 核心 CI（自托管 CI 平台） |
| 版本 | Woodpecker **v3**（实测运行 `3.16.0`），Agent 同 v3，元数据库 PostgreSQL 16 |
| 部署位置 | 主机 mykng（192.168.31.105），容器 `woodpecker-server`（8000 + gRPC 9002→9000）、`woodpecker-agent`（内部）、`woodpecker-db`（postgres:16-alpine，5433→5432） |
| 源码位置 | 流水线脚本集 `D:\huliang\java\ideaworkspace\devtools\woodScript\`（mykng 上 `/root/devtools/woodScript`）；官方仓库 github.com/woodpecker-ci/woodpecker |
| CI/CD | 自身即 CI；由 `trigger-pipeline.py` 经 Woodpecker REST API 触发，仓库 `/root/devtools`（Gitee + GitHub 双推，Webhook 触发） |
| 技术栈 | Woodpecker Server/Agent (Go) + PostgreSQL 16 + drone-ssh 部署 + Maven/pnpm 构建 + Docker Compose |

## 访问入口

- 公网：`https://woodci.marschat.online`（经腾讯云2号 nginx 443 反代）
- 内网：`http://192.168.31.105:8000/`
- Tailscale：`http://100.93.36.113:8000/`
- 登录方式：GitHub OAuth（`WOODPECKER_GITHUB=true`，server 环境变量）；API Token 见 Vaultwarden / infrastructure-map 技能（脚本 `trigger-pipeline.py` 内置默认值，可用 `WOODPECKER_TOKEN` 覆盖）。

## 全链路

```
浏览器/脚本 → 腾讯云2号 nginx(443) → http://100.93.36.113:8000 (woodpecker-server)
  ├─ Webhook: Gitee/GitHub push → 触发 pipeline
  └─ REST API: trigger-pipeline.py / check-pipeline.py 调 /api/...

流水线执行（CI 容器内）:
  ci/build-*.sh  (Maven/pnpm 编译)
    → 产物推 /mnt/shared/woodScript/publish/<项目>-latest.tar.gz
  cd/deploy-*.sh  (drone-ssh 到 mykng)
    → 解压 → docker compose 重建 → 健康检查 24×10s
```

## 核心功能与使用

### 1. 触发流水线 — `trigger-pipeline.py`

```bash
cd /root/devtools
python woodScript/trigger-pipeline.py mykng            # 触发单项目（默认分支 dev）
python woodScript/trigger-pipeline.py mykng --wait     # 触发并监控到结束（推荐）
python woodScript/trigger-pipeline.py kb-ops --note "修复登录bug"
python woodScript/trigger-pipeline.py mykng --branch main
python woodScript/trigger-pipeline.py all              # 全量（部署所有项目）
```

支持的项目名（DEPLOY_TARGET）：

| 项目名 | 部署服务 |
|--------|----------|
| `mykng` | kb-gateway, kb-auth, kb-file, kb-knowledge, kb-intelligence |
| `kb-ops` | kb-ops |
| `kb-ops-web` | kb-ops-web |
| `kb-web` | kb-web |
| `infra-monitor` | infra-monitor |
| `infra-monitor-web` | infra-monitor-web |
| `active-manager` | activation-code-server |
| `portal-web` | portal-web |
| `portal-server` | portal-server |
| `platform` | 起 Kafka + 重启 MySQL GR 集群 |
| `all` | 以上全部 |

> `workcheck-python` **不在本列表**：它是独立仓库（Woodpecker repo_id=2，分支 main），使用自己的流水线，不走 `trigger-pipeline.py`。

### 2. 查询/监控 — `check-pipeline.py`

```bash
python woodScript/check-pipeline.py 342            # 某次流水线（状态+全部步骤日志）
python woodScript/check-pipeline.py --log 342     # 只看日志
python woodScript/check-pipeline.py --watch 342   # 持续监控到结束
python woodScript/check-pipeline.py               # 不编号=最新一条
python woodScript/check-pipeline.py --recent 5    # 最近 N 条
```

### 3. 统一 CI/CD 流程

- **构建（CI 容器内）**：`ci/build-*.sh` 编译，产物命名为 `<项目>-latest.tar.gz` 推到共享目录 `/mnt/shared/woodScript/publish/`。
  - Maven 项目：`build-mykng.sh`(5 微服务 `-T 2C` 并行)、`build-kb-ops.sh`、`build-portal-server.sh`、`build-active-manager.sh`、`build-infra-monitor.sh`
  - 前端（pnpm）：`build-kb-web.sh`、`build-kb-ops-web.sh`、`build-portal-web.sh`、`build-infra-monitor-web.sh`；`setup_pnpm()` 锁定 pnpm 版本，registry 指向本机 Nexus `192.168.31.105:8081/repository/npm-public/`。
- **部署（drone-ssh 到目标机）**：`cd/deploy-*.sh` 取产物、解压、`docker compose` 重建、健康检查（最多 24 次 ×10 秒）。核心函数库 `lib-deploy.sh`（含 `trap '' TERM` 防止 drone-ssh SIGTERM 中断部署）。
- **compose 归属**：应用层按 `kb-app`（`docker-compose.app.yml`）/ `kb-web`（`docker-compose.web.yml`）/ `infra-monitor`（`infra-monitor-server/docker-compose.yml`）分项目部署。

### 4. platform 层（基础设施中间件）

`DEPLOY_TARGET=platform` 时流水线重启 Kafka（`docker compose ... up -d --no-deps platform-kafka`）与 MySQL GR 集群（`deploy-mysql-cluster.sh`，按 MySQL 官方"Restarting a Group"流程：mykng 重启 Node1 并 bootstrap → SSH 到 Debian 重启 Node2/Node3 并显式 `START GROUP_REPLICATION` → 校验 3/3 ONLINE）。Redis/Mongo/MinIO/Meili/Nacos 的首次安装与重启走 `platform/start-platform.sh` 手动执行，流水线不管。

## 依赖与关联

- 依赖：
  - **PostgreSQL 16**（`woodpecker-db`，5433）：存储流水线/构建/Secret 元数据。
  - **共享目录** `/mnt/shared/woodScript/`（publish + cd 脚本，经 SMB/NFS 从 mykng 挂载到 CI 节点）——产物与部署脚本的流转中枢。
  - **Nexus 私服**：Maven（`nexus.marschat.online/repository/maven-public/`）、npm（`192.168.31.105:8081/repository/npm-public/`）依赖源。
  - **Docker daemon**（mykng）：部署阶段 `docker compose` 重建容器。
  - **GitHub / Gitee**：OAuth 登录与代码仓库 Webhook 触发。
- 被依赖/关联系统：**所有应用层服务**（kb-ops、infra-monitor、portal、mykng、激活码等）的发布都依赖本流水线；其"部署记录"在 kb-ops 中可对应查询。

## 运维要点

- 重启策略分层（铁律）：
  - 平台层 `platform-*` → `restart: on-failure:5` + daemon 重启后 `@reboot` 拉起。
  - 应用层 `kb-*/portal*/infra-monitor` → `restart: on-failure:5`，**且只能走流水线部署**，禁止手动 `docker compose up`。
- 日志查看：Woodpecker 界面查看构建/部署步骤日志；目标机容器日志 `docker logs <服务>` 或 mykng `obs-dozzle`（15500）。
- 数据与备份：元数据在 `woodpecker-db`（PostgreSQL 16）；重要变更前建议对 `woodpecker-db` 做 `pg_dump`。共享产物目录 `/mnt/shared/woodScript` 建议纳入备份。
- 凭据安全：Woodpecker Agent Secret、GitHub OAuth Secret、API Token 等均为密级，**一律见 Vaultwarden（vault.marschat.online）或 infrastructure-map 技能**，文档不写明文。
- 常见问题：
  - 部署卡在健康检查：查看 `check-pipeline.py --log` 对应步骤，多为容器启动慢或端口未就绪；健康检查 24×10s 仍失败则回滚。
  - 产物缺失：确认 CI 阶段 `publish_artifact` 成功推到 `/mnt/shared/woodScript/publish/`。
  - pnpm store 膨胀：流水线含 `cleanup-pnpm-store.sh`（距上次 <7 天跳过），必要时 `--force`。

## 变更记录

- 2026-09-05 首次生成（portal 文档补全任务，AI 基于实采 docker ps + woodScript/README.md + 容器环境变量生成）
