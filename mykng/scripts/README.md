# mykng 运维脚本体系（SOP附录G）

本目录提供 mykng 知识库微服务的完整运维脚本，覆盖部署、构建、回滚、健康检查、备份、初始化、状态查看、迁移校验、混沌演练等场景。

## 脚本清单

### Linux 部署环境（.sh）

| 脚本                       | 用途                                              | 用法示例                                  |
| -------------------------- | ------------------------------------------------- | ----------------------------------------- |
| `deploy.sh`                | 一键部署（build/up/down/restart/all/logs/status） | `bash scripts/deploy.sh all`              |
| `build.sh`                 | Maven 编译 + Docker 构建                          | `bash scripts/build.sh --no-cache`        |
| `rollback.sh`              | 服务回滚（单个 / 全部）                           | `bash scripts/rollback.sh kb-auth`        |
| `health-check.sh`          | 健康检查（容器 + actuator + 基础设施连通性）      | `bash scripts/health-check.sh`            |
| `backup.sh`                | 数据库备份（5 个 MySQL + MongoDB）                | `bash scripts/backup.sh`                  |
| `init-env.sh`              | 首次环境初始化                                    | `bash scripts/init-env.sh`                |
| `start.sh`                 | 启动服务                                          | `bash scripts/start.sh`                   |
| `stop.sh`                  | 停止服务（保留 / 移除容器）                       | `bash scripts/stop.sh --down`             |
| `restart.sh`               | 重启服务                                          | `bash scripts/restart.sh kb-auth`         |
| `status.sh`                | 状态查看（容器 + 端口 + 磁盘 + 内存）             | `bash scripts/status.sh`                  |
| `db-migrate-verify.sh`     | 数据迁移校验（快照对比 + verify SQL）             | `bash scripts/db-migrate-verify.sh`       |
| `init-db.sh`               | 数据库初始化                                      | `bash scripts/init-db.sh --verify`        |
| `pull-base-image.sh`       | 拉取基础镜像（含加速器配置）                      | `bash scripts/pull-base-image.sh`         |

### Windows 开发环境（.ps1）

| 脚本                | 用途                                  | 用法示例                            |
| ------------------- | ------------------------------------- | ----------------------------------- |
| `deploy.ps1`        | 部署（build/up/down/restart/all）     | `.\scripts\deploy.ps1 all`          |
| `build.ps1`         | Maven 编译 + Docker 构建              | `.\scripts\build.ps1 -NoCache`      |
| `health-check.ps1`  | 健康检查                              | `.\scripts\health-check.ps1`        |
| `backup.ps1`        | 数据库备份                            | `.\scripts\backup.ps1`              |
| `status.ps1`        | 状态查看                              | `.\scripts\status.ps1`              |

### 混沌工程（chaos-engineering/）

| 脚本                          | 用途                                   | 用法示例                                                |
| ----------------------------- | -------------------------------------- | ------------------------------------------------------- |
| `inject-mysql-down.sh`        | 模拟 MySQL 宕机                        | `bash chaos-engineering/inject-mysql-down.sh`           |
| `inject-redis-down.sh`        | 模拟 Redis 宕机                        | `bash chaos-engineering/inject-redis-down.sh`           |
| `inject-network-delay.sh`     | 模拟网络延迟（tc netem）               | `bash chaos-engineering/inject-network-delay.sh`        |
| `inject-oom.sh`               | 模拟 OOM                               | `bash chaos-engineering/inject-oom.sh`                  |
| `verify-ha.sh`                | 高可用综合验证                         | `bash chaos-engineering/verify-ha.sh`                   |

## 首次部署流程

```bash
# 1. 环境初始化（创建目录、生成 .env、初始化数据库）
bash scripts/init-env.sh

# 2. 修改 .env（按需修改密码、密钥）
vim .env

# 3. 拉取基础镜像
bash scripts/pull-base-image.sh

# 4. 全流程部署（build + up + health-check）
bash scripts/deploy.sh all

# 或分步执行
bash scripts/build.sh
bash scripts/deploy.sh up
bash scripts/health-check.sh
```

## 日常运维操作

### 启停服务

```bash
bash scripts/start.sh                 # 启动所有
bash scripts/stop.sh                  # 停止所有（保留容器）
bash scripts/stop.sh --down           # 停止并移除容器
bash scripts/restart.sh               # 重启所有
bash scripts/restart.sh kb-auth       # 重启单个服务
```

### 查看状态

```bash
bash scripts/status.sh                # 完整状态（容器 + 端口 + 资源）
bash scripts/health-check.sh          # 健康检查（actuator + 连通性）
bash scripts/deploy.sh logs           # 查看所有日志
bash scripts/deploy.sh logs kb-auth   # 查看单个服务日志
```

### 数据库备份与恢复

```bash
# 立即备份
bash scripts/backup.sh

# 验证最近一次备份
bash scripts/backup.sh --verify

# 列出所有备份
bash scripts/backup.sh --list

# 清理过期备份
bash scripts/backup.sh --clean

# cron 定时备份（每天凌晨 2 点）
# 0 2 * * * /mnt/shared/devtools/mykng/scripts/backup.sh
```

恢复示例：

```bash
# 从备份恢复 kb_auth 数据库
docker exec -i kb-mysql mysql -uroot -p<password> kb_auth < /data/backup/mysql/<ts>/kb_auth.sql

# 从备份恢复 MongoDB
docker exec -i kb-mongo mongorestore --uri="mongodb://kb:kb123456@localhost:27017" --archive < /data/backup/mongodb/<ts>/mongodb.archive
```

### 服务回滚

```bash
# 回滚单个服务到上一版本镜像
bash scripts/rollback.sh kb-intelligence

# 回滚到指定 tag
bash scripts/rollback.sh kb-auth 20260628_103000

# 回滚所有服务（含数据备份）
bash scripts/rollback.sh all
```

### 数据迁移校验

```bash
# 迁移前：生成数据快照
bash scripts/db-migrate-verify.sh --snapshot

# 执行 ALTER TABLE / 数据迁移 SQL
# ...

# 迁移后：与快照对比表行数
bash scripts/db-migrate-verify.sh --compare /data/backup/migrate-snapshot/<ts>

# 执行表结构校验 SQL
bash scripts/db-migrate-verify.sh
```

## 混沌演练

⚠ **仅在测试环境执行**

```bash
# 单项故障注入
bash scripts/chaos-engineering/inject-mysql-down.sh --duration 30
bash scripts/chaos-engineering/inject-redis-down.sh --duration 60
bash scripts/chaos-engineering/inject-network-delay.sh --target kb-gateway --delay 500ms
bash scripts/chaos-engineering/inject-oom.sh --target kb-file --mem 128m

# 综合高可用验证（依次注入 → 恢复 → 健康检查）
bash scripts/chaos-engineering/verify-ha.sh

# 紧急恢复所有服务
bash scripts/chaos-engineering/verify-ha.sh --recover
```

## 脚本规范

所有脚本遵循以下规范：

- **Shebang**：`#!/bin/bash` + `set -e` 严格模式
- **颜色输出**：绿色成功、红色失败、黄色警告、蓝色信息
- **帮助**：支持 `-help` / `--help` / `-h` 参数
- **路径变量化**：`PROJECT_ROOT`、`COMPOSE_PROJECT`、`COMPOSE_FILE`
- **Docker Compose**：统一使用 `-p kb-deploy`
- **密码读取**：从 `.env` 文件读取，兼容默认值
- **日志输出**：关键操作有日志输出

## 关键路径

| 路径                              | 用途                       |
| --------------------------------- | -------------------------- |
| `/data/kb-web/`                   | 前端静态文件               |
| `/data/logs/`                     | 应用日志                   |
| `/data/logs/backup.log`           | 备份日志                   |
| `/data/logs/chaos-reports/`       | 混沌演练报告               |
| `/data/logs/migrate-reports/`     | 数据迁移校验报告           |
| `/data/backup/mysql/`             | MySQL 备份                 |
| `/data/backup/mongodb/`           | MongoDB 备份               |
| `/data/backup/migrate-snapshot/`  | 迁移快照                   |
| `/data/import/`                   | 知识导入源目录             |

## Docker Compose 项目约定

- **项目名**：`kb-deploy`
- **网络**：`kb-deploy_kb-net`
- **容器命名前缀**：`kb-`（如 `kb-mysql`、`kb-auth`）
- **镜像命名前缀**：`kb-deploy-`（如 `kb-deploy-kb-auth`）

## 服务端口映射

| 服务              | 容器端口 | 宿主机端口 | 说明         |
| ----------------- | -------- | ---------- | ------------ |
| kb-gateway        | 8080     | 8090       | API 网关     |
| kb-auth           | 8081     | -          | 认证服务     |
| kb-file           | 8082     | -          | 文件服务     |
| kb-knowledge      | 8083     | -          | 知识服务     |
| kb-ops            | 8084     | -          | 运维服务     |
| kb-intelligence   | 8086     | -          | 智能引擎     |
| mysql             | 3306     | 3306       | MySQL        |
| redis             | 6379     | 6379       | Redis        |
| mongodb           | 27017    | 27017      | MongoDB      |
| minio             | 9000/9001| 9000/9001  | MinIO        |
| meilisearch       | 7700     | 7700       | MeiliSearch  |

## 相关文档

- 项目部署文档：`docs/deployment.md`
- 运维手册：`docs/operation-manual.md`
- 架构设计：`docs/architecture.md`
- 数据库设计：`docs/database-design.md`
- 项目根 README：`../README.md`
