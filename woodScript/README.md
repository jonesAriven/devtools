# WoodScript — 流水线脚本集

devtools monorepo 的 CI/CD 脚本集合，配合 Woodpecker CI 使用。

**铁律：所有应用层部署必须走 Woodpecker 流水线，禁止手动 `docker compose up`。**

## 目录结构

```
woodScript/
├── trigger-pipeline.py   # 触发流水线（入口）
├── check-pipeline.py     # 查询流水线状态/日志
├── env.sh                # 公共变量（路径/Nexus/健康检查/主机）
├── lib-build.sh          # CI 构建侧公共函数库
├── lib-deploy.sh         # 部署侧公共函数库（442行核心）
├── ci/                   # CI 构建脚本（在 CI 容器内运行）
│   ├── build-mykng.sh
│   ├── build-kb-ops.sh
│   ├── build-kb-ops-web.sh
│   ├── build-kb-web.sh
│   ├── build-infra-monitor.sh
│   ├── build-infra-monitor-web.sh
│   ├── build-active-manager.sh
│   ├── build-portal-server.sh
│   └── build-portal-web.sh
└── cd/                   # 部署脚本（在目标服务器运行）
    ├── deploy-mykng.sh
    ├── deploy-kb-ops.sh
    ├── deploy-kb-ops-web.sh
    ├── deploy-kb-web.sh
    ├── deploy-infra-monitor.sh
    ├── deploy-infra-monitor-web.sh
    ├── deploy-active-manager.sh
    ├── deploy-portal-server.sh
    ├── deploy-portal-web.sh
    ├── deploy-mysql-cluster.sh   # MySQL GR 集群重启（Node1 本机 + SSH 到 Debian Node2/3）
    └── cleanup-pnpm-store.sh
```

## 一、日常使用（触发 + 监控）

### 1. 触发流水线 — trigger-pipeline.py

```bash
cd /root/devtools

# 触发单个项目
python woodScript/trigger-pipeline.py mykng

# 触发并自动监控到结束（推荐）
python woodScript/trigger-pipeline.py mykng --wait

# 带备注
python woodScript/trigger-pipeline.py kb-ops --note "修复登录bug"

# 指定分支（默认 dev）
python woodScript/trigger-pipeline.py mykng --branch main

# 全量触发（DEPLOY_TARGET 为空，部署所有项目）
python woodScript/trigger-pipeline.py all
```

**支持的项目名（DEPLOY_TARGET）：**

| 项目名 | 说明 | 部署服务 |
|--------|------|----------|
| `mykng` | 知识库后端 | kb-gateway, kb-auth, kb-file, kb-knowledge, kb-intelligence |
| `kb-ops` | 运维后台 | kb-ops |
| `kb-ops-web` | 运维前端 | kb-ops-web |
| `kb-web` | 知识库前端 | kb-web |
| `infra-monitor` | 监控后端 | infra-monitor |
| `infra-monitor-web` | 监控前端 | infra-monitor-web |
| `active-manager` | 激活码管理 | activation-code-server |
| `portal-web` | 门户前端 | portal-web |
| `portal-server` | 门户后端 | portal-server |
| `workcheck-python` | 工作量管理 | **独立仓库，不走本脚本**（见下文） |
| `platform` | 平台中间件 | platform-deploy 步骤（起 Kafka + 重启 MySQL GR 集群） |
| `all` | 全量 | 以上全部 |

### 2. 查询状态 — check-pipeline.py

```bash
# 查某次流水线（状态 + 所有步骤日志）
python woodScript/check-pipeline.py 342

# 只看日志
python woodScript/check-pipeline.py --log 342

# 持续监控到结束
python woodScript/check-pipeline.py --watch 342

# 不给编号 = 查最新一条
python woodScript/check-pipeline.py

# 最近 N 条记录
python woodScript/check-pipeline.py --recent 5
```

### 3. 环境变量（可选覆盖）

```bash
export WOODPECKER_TOKEN="xxx"                  # API Token（脚本有内置默认值）
export WOODPECKER_URL="https://woodci.marschat.online"  # CI 地址
```

## 二、CI 构建脚本（ci/build-*.sh）

在 CI 容器内运行（maven/node 镜像），编译产物并推送到共享目录 `/mnt/shared/woodScript/publish/`。

统一流程：`编译 → collect_artifacts → publish_artifact`，产物统一命名为 `<项目>-latest.tar.gz`。

| 脚本 | 构建工具 | 产物 |
|------|----------|------|
| build-mykng.sh | Maven (5 微服务, `-T 2C` 并行) | mykng-latest.tar.gz |
| build-kb-ops.sh | Maven | kb-ops-latest.tar.gz |
| build-portal-server.sh | Maven | portal-server-latest.tar.gz |
| build-active-manager.sh | Maven | active-manager-latest.tar.gz |
| build-infra-monitor.sh | Maven | infra-monitor-latest.tar.gz |
| build-kb-web.sh | pnpm | kb-web-latest.tar.gz |
| build-kb-ops-web.sh | pnpm | kb-ops-web-latest.tar.gz |
| build-portal-web.sh | pnpm | portal-web-latest.tar.gz |
| build-infra-monitor-web.sh | pnpm | infra-monitor-web-latest.tar.gz |

**前端构建要点**：`setup_pnpm()` 读取项目 `package.json` 的 `packageManager` 字段锁定 pnpm 版本，registry 指向本机 Nexus `192.168.31.105:8081/repository/npm-public/`。

## 三、部署脚本（cd/deploy-*.sh）

通过 drone-ssh 在目标服务器执行，从共享目录取产物、解压、docker compose 重建、健康检查。

```bash
# 手动调用形式（一般不需要，流水线自动调）
bash deploy-mykng.sh mykng-latest.tar.gz
```

**每个脚本的部署目标：**

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

**健康检查**：最多重试 24 次 × 10 秒（`HEALTH_MAX_RETRIES`/`HEALTH_INTERVAL`，见 env.sh）。

### cleanup-pnpm-store.sh

pnpm store 清理（防 `/var/cache/pnpm-store` 无限膨胀）：

```bash
bash cleanup-pnpm-store.sh          # 距上次清理 <7 天则跳过
bash cleanup-pnpm-store.sh --force  # 强制清理
```

固定用 pnpm@8.15.9 清理（兼容 v6/v9 lockfile，@latest v10 会误删活跃包）。

## 四、公共库

### env.sh — 公共变量

| 变量 | 值 | 说明 |
|------|-----|------|
| `SHARED_DIR` | /mnt/shared/woodScript/publish | 产物共享目录 |
| `CI_DIR` | /mnt/shared/woodScript/cd | 部署脚本目录 |
| `DEPLOY_BASE` | /root/kb-deploy | 目标服务器部署根目录 |
| `GIT_REPO` | /root/devtools | 代码仓库 |
| `NEXUS_NPM_REGISTRY` | http://192.168.31.105:8081/repository/npm-public/ | npm 私服 |
| `HOST_MYKNG` | 192.168.31.105 | 主部署机 |
| `HOST_DEBIAN` | 192.168.31.182 | 内网 Debian |

### lib-build.sh — CI 构建函数

- `collect_artifacts <名> <文件...>` — 收集产物到 publish/<名>/
- `publish_artifact <名>` — 打 tar.gz 推到共享目录（写失败即 exit 1）
- `setup_pnpm <目录>` — 锁定 pnpm 版本 + 配置 Nexus registry + 重试参数

### lib-deploy.sh — 部署函数（442行）

被所有 deploy-*.sh source。关键设计：
- `trap '' TERM` — 防止 drone-ssh 的 SIGTERM 中断部署
- 提供 `verify_artifact` / `extract_artifact` / `compose_stop_services` / `compose_up_services` / 健康检查 / 心跳等原语

## 五、基础组件（platform 层）脚本

全局基础设施中间件，代码在 `/root/devtools/platform/`，所有应用（mykng/kb-ops/portal/infra-monitor 等）共享这一套。

| 组件 | 容器名 | 镜像 | 端口 |
|------|--------|------|------|
| MySQL GR Node1 | platform-mysql-1 | mysql:8.0 | 3306, 33061 (GR) |
| MySQL GR Node2 | platform-mysql-2 | mysql:8.0 | 3307, 33061 (GR) |
| MySQL GR Node3 | platform-mysql-3 | mysql:8.0 | 3308, 33062 (GR) |
| Redis | platform-redis | redis:7-alpine | 6379 |
| MongoDB | platform-mongo | mongo:7.0 | 27017 |
| MinIO | platform-minio | minio/minio:latest | 9000 (API), 19001 (控制台) |
| MeiliSearch | platform-meilisearch | getmeili/meilisearch:v1.12 | 7700 |
| Nacos | platform-nacos | nacos/nacos-server:v2.4.3 | 8848 (HTTP), 9848 (gRPC) |
| Kafka | platform-kafka | apache/kafka:3.7.1 | 9092 |
| Kafka UI | platform-kafka-ui | provectuslabs/kafka-ui:latest | 19092 |

compose 文件：
- `platform/docker-compose.platform.yml` — Redis/Mongo/MinIO/Meili/Nacos/Kafka，统一网络 `platform-net`（external）
- `platform/mysql/docker-compose.mysql-cluster.yml` — MySQL GR Node1 (mykng)
- `platform/mysql/docker-compose.mysql-cluster.debian.yml` — MySQL GR Node2+Node3 (Debian)

MySQL GR 集群配置文件：
- `platform/mysql/cluster.cnf` — Node1 (server-id=1, port=3306)
- `platform/mysql/node2.cnf` — Node2 (server-id=2, port=3307)
- `platform/mysql/node3.cnf` — Node3 (server-id=3, port=3308)

### start-platform.sh — 基础设施启动（手动）

```bash
bash platform/start-platform.sh
```

**唯一允许手动执行的 compose 启动**（基础设施层例外，不归流水线管）。流程：

1. 清理占用 platform-* 容器名的残留容器（解决 Conflict 错误）
2. 检查旧 `kb-*` 数据卷兼容性，提示迁移命令
3. `docker compose -p platform up -d` 启动全部中间件
4. 等待所有容器健康检查通过（最长 2 分钟）

⚠️ 不要手动 `docker network create platform-net`——compose 声明了 `external: true`，手动创建的裸网络缺 compose label 会导致 up 报错。

### migrate-to-platform.sh — 旧 kb-* 迁移（一次性）

```bash
bash platform/migrate-to-platform.sh --dry-run   # 预览，不执行
bash platform/migrate-to-platform.sh             # 实际迁移
```

历史遗留迁移工具：旧基础设施容器名是 `kb-mysql`/`kb-redis` 等，数据卷是 `kb-*-data`，本脚本停旧容器 → 迁移数据卷到 `platform-*-data` → 删旧容器 → 清旧网络。

**执行顺序（不能反）**：
1. `git pull` 拉最新代码
2. `bash platform/migrate-to-platform.sh`（先迁移）
3. `bash platform/start-platform.sh`（再启动）
4. 触发流水线重新部署应用层

前置：磁盘可用 ≥ 旧卷总大小 ×2（迁移期间新旧卷共存）；建议先 mysqldump 备份。已完成迁移的环境此脚本不再需要。

### 流水线中的 platform-deploy 步骤

`DEPLOY_TARGET=platform`（或 all）时，流水线执行两件事：

1. 重启 Kafka：
```bash
cd /mnt/shared/platform && docker compose -f docker-compose.platform.yml up -d --no-deps platform-kafka
```

2. 重启 MySQL GR 集群（通过 `deploy-mysql-cluster.sh`）：
```bash
bash /mnt/shared/woodScript/cd/deploy-mysql-cluster.sh
```

`deploy-mysql-cluster.sh` 的工作流程（参考 MySQL 官方文档 20.5.2 "Restarting a Group"）：
1. 在 mykng (105) 重启 Node1 (platform-mysql-1)，等待 MySQL 就绪后引导集群（bootstrap_group=ON → START GROUP_REPLICATION → OFF）
2. SSH 到 Debian (182) 重启 Node2+Node3 (platform-mysql-2/3)，等待 MySQL 就绪
3. 对 Node2+Node3 显式执行 `START GROUP_REPLICATION` 加入集群（因为 `group_replication_start_on_boot=OFF`，不会自动加入）
4. 等待 GR 集群恢复（检查 3/3 节点 ONLINE）

**Redis/Mongo/MinIO/Meili/Nacos 的首次安装和重启都走 start-platform.sh 手动执行**，流水线不管，避免 CI 误重启。

### 其他文件

| 文件 | 作用 |
|------|------|
| `platform/mysql/my.cnf` | MySQL Node1 自定义配置，挂载到 `/etc/mysql/conf.d/custom.cnf` |
| `platform/mysql/cluster.cnf` | MySQL GR Node1 集群配置 (server-id=1) |
| `platform/mysql/node2.cnf` | MySQL GR Node2 集群配置 (server-id=2) |
| `platform/mysql/node3.cnf` | MySQL GR Node3 集群配置 (server-id=3) |
| `platform/nacos/init-nacos.sh` | Nacos 初始化脚本（nacos-init 一次性容器执行，`restart: "no"`） |

### 重启策略分层（铁律）

- **平台层**（platform-* 全部）→ `restart: on-failure:5` 运行中；docker daemon 重启后靠 `@reboot` 拉起脚本恢复（排除 wp_*/infra-monitor-web）
- **应用层**（kb-*/portal-*/infra-monitor 等）→ `restart: on-failure:5`，且**只能走流水线部署**

## 六、注意事项

1. **workcheck-python 是独立仓库**（`/root/workcheck_python`，Woodpecker repo_id=2，分支 main），不走 trigger-pipeline.py，必须用它自己的流水线。
2. **部署分层**：
   - 平台层（MySQL GR 集群/Redis/Mongo/MinIO/Kafka/Nacos/Meili）→ `restart: unless-stopped`
   - 应用层（kb-*/portal/infra-monitor 等）→ `restart: on-failure:5`，防代码 bug 循环 crash
3. **产物流转**：CI 容器构建 → `/mnt/shared/woodScript/publish/*.tar.gz` → drone-ssh 到目标机 → deploy-*.sh 解压重建。
4. **首次/定位问题**时可手动操作，日常变更一律走流水线。
