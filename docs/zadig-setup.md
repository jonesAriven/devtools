# Zadig CI/CD 平台 — 完整部署与使用指南

## 📋 目录

- [1. 概述](#1-概述)
- [2. 快速开始（5分钟上手）](#2-快速开始5分钟上手)
- [3. 详细配置](#3-详细配置)
- [4. 流水线配置](#4-流水线配置)
- [5. 日常运维](#5-日常运维)
- [6. 常见问题](#6-常见问题)
- [7. 进阶功能](#7-进阶功能)

---

## 1. 概述

### 🎯 什么是 Zadig？

**Zadig** 是开源的 **云原生 CI/CD 平台**，由国内公司 **KodeRover（上海）** 开发维护。

| 特性 | 说明 |
|------|------|
| 💰 **费用** | 完全免费开源 (Apache 2.0 协议) |
| 🇨🇳 **中文** | 全中文界面 + 文档 + 社区 |
| 🚀 **性能** | 基于 K8s/Docker，高性能并发构建 |
| 🔧 **易用** | 可视化编排 + YAML 配置 |
| 🌐 **内网支持** | 原生支持自建 Runner 访问内网 |

### ✨ 核心优势

对比 Jenkins / Drone / Gitee Go：

| 维度 | Jenkins | Drone | Gitee Go | **Zadig** ⭐ |
|------|---------|-------|----------|-------------|
| 费用 | 免费 | 免费 | 299元/人/年 | **免费开源** |
| 中文 | ❌ 英文 | ❌ 英文 | ✅ 全中文 | **✅ 全中文** |
| 内网访问 | 需配置 | 需配置 | 需私有部署 | **✅ 原生支持** |
| 上手难度 | 高 | 中 | 低 | **⭐ 低** |
| 功能完整度 | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐⭐⭐ | **⭐⭐⭐⭐⭐** |
| 国内社区 | 少 | 少 | 官方支持 | **活跃** |

### 📦 本项目包含的文件

```
devtools/zadig/
├── docker-compose.yml          # Docker Compose 部署配置
├── deploy.sh                   # 一键部署管理脚本
├── pipelines/
│   └── devtools-pipeline.yaml  # CI/CD 流水线配置
└── README.md                   # 本文档

devtools/docs/
└── zadig-setup.md              # 详细使用指南
```

---

## 2. 快速开始（5分钟上手）

### 🎯 适用场景

- ✅ 个人开发者 / 小团队
- ✅ 有内网服务器需要部署
- ✅ 想要中文界面的 CI/CD 工具
- ✅ 不想付费购买企业版工具

### 📋 前置要求

| 要求 | 版本/说明 |
|------|----------|
| 操作系统 | Linux (Ubuntu 20.04+ / CentOS 8+) 或 macOS |
| Docker | 20.10+ |
| Docker Compose | V2 (docker compose 插件) |
| 内存 | ≥ 4GB（推荐 8GB） |
| 磁盘 | ≥ 20GB（用于镜像和数据） |
| 网络 | 能访问 Docker Hub（或配置镜像加速） |

### 🚀 三步部署

#### Step 1: 下载代码到 mykng 服务器

```bash
# SSH 登录到 mykng 服务器
ssh root@100.93.36.113

# 克隆仓库（如果还没有）
cd /opt
git clone https://gitee.com/jonesAriven/devtools.git
cd devtools/zadig
```

#### Step 2: 初始化并启动

```bash
# 一键初始化（创建目录、生成配置）
bash deploy.sh --init

# 启动服务（首次需要下载镜像，约 3-5 分钟）
bash deploy.sh --start
```

#### Step 3: 访问 Zadig

打开浏览器访问：
- **本地**: http://localhost:8080
- **远程**: http://100.93.36.113:8080（通过 Tailscale）

默认账号密码：
- 用户名: `admin`
- 密码: `zadig`

> ⚠️ **重要**: 登录后请立即修改默认密码！

---

## 3. 详细配置

### 3.1 环境变量配置

编辑 `/opt/zadig/.env` 文件：

```bash
# ============================================
# Zadig 环境变量配置
# ============================================

# MySQL 密码（建议修改为强密码）
MYSQL_PASSWORD=你的MySQL强密码

# Redis 密码（可选，留空则无密码）
REDIS_PASSWORD=

# Zadig 访问地址（修改为实际 IP 或域名）
ZADIG_DOMAIN=http://100.93.36.113:8080

# Zadig 加密密钥（必须修改为随机字符串！）
# 生成方法: openssl rand -hex 32
ZADIG_SECRET=生成的随机字符串

# 日志级别 (debug/info/warn/error)
LOG_LEVEL=info
```

### 3.2 端口说明

| 端口 | 服务 | 用途 | 是否可改 |
|------|------|------|---------|
| 8080 | Zadig Server | Web UI 和 API | ✅ 可在 docker-compose.yml 修改 |
| 3307 | MySQL | 数据库（映射到主机） | ✅ 可选关闭外部访问 |
| 6380 | Redis | 缓存（映射到主机） | ✅ 可选关闭外部访问 |

### 3.3 数据持久化

Zadig 的数据存储在 Docker Volume 中：

```bash
# 查看数据卷
docker volume ls | grep zadig

# 数据卷说明:
# - zadig-data:        Zadig 主数据（配置、构建历史等）
# - zadig-mysql-data:   MySQL 数据库文件
# - zadig-redis-data:   Redis 持久化文件

# 备份数据卷（重要！）
docker run --rm -v zadig-mysql-data:/data -v $(pwd):/backup alpine tar czf /backup/mysql-backup.tar.gz -C /data .
docker run --rm -v zadig-data:/data -v $(pwd):/backup alpine tar czf /backup/zadig-backup.tar.gz -C /data .
```

---

## 4. 流水线配置

### 4.1 从 Drone 迁移

本项目已提供从 `.drone.yml` 迁移过来的流水线配置：

**文件位置**: `zadig/pipelines/devtools-pipeline.yaml`

### 4.2 在 Zadig 中导入流水线

1. **登录 Zadig** → 点击 **"流水线"** 菜单
2. **点击 "新建流水线"**
3. **选择 "YAML 模式"**
4. **上传或粘贴 `devtools-pipeline.yaml` 内容**
5. **配置环境变量**（SSH 密码等敏感信息）

### 4.3 配置环境变量

在 Zadig 项目设置 → **变量管理** 中添加：

#### 必需的 SSH 凭据

| 变量名 | 类型 | 值 | 说明 |
|--------|------|-----|------|
| `DEPLOY_HOST_MYKNG` | 明文 | `100.93.36.113` | mykng 服务器 IP |
| `DEPLOY_PASS_MYKNG` | **加密** | `<password>` | mykng SSH 密码 |
| `DEPLOY_HOST_LAN` | 明文 | `192.168.31.182` | 内网 Debian IP |
| `DEPLOY_PASS_LAN` | **加密** | `<password>` | 内网 SSH 密码 |

> ⚠️ **安全提示**: 密码类变量务必使用 Zadig 的加密存储功能！

### 4.4 运行流水线

#### 手动触发

1. 进入流水线详情页
2. 点击 **"运行"** 按钮
3. 填写参数：
   ```
   DEPLOY_PROJECT: mykng          # 只部署知识库
   DEPLOY_TARGET:  production      # 生产环境
   ```
4. 点击确认，等待构建完成

#### 参数说明

| 参数 | 可选值 | 默认值 | 说明 |
|------|--------|--------|------|
| `DEPLOY_PROJECT` | `all`, `mykng`, `active-manager`, `mykng,active-manager` | `all` | 要部署的项目 |
| `DEPLOY_TARGET` | `production`, `dev`, `test` | `production` | 部署目标环境 |

---

## 5. 日常运维

### 5.1 常用命令

```bash
# 所有命令都在 /opt/zadig 目录下执行
cd /opt/zadg

# 查看状态
bash deploy.sh --status

# 查看日志（实时）
bash deploy.sh --logs

# 重启服务
bash deploy.sh --restart

# 停止服务
bash deploy.sh --start

# 停止服务
bash deploy.sh --stop
```

### 5.2 备份与恢复

#### 自动备份脚本（推荐）

创建定时任务，每天自动备份：

```bash
# 编辑 crontab
crontab -e

# 添加以下内容（每天凌晨 3 点备份）
0 3 * * * /opt/zadig/backup.sh >> /var/log/zadig-backup.log 2>&1
```

创建备份脚本 `/opt/zadig/backup.sh`:

```bash
#!/bin/bash
# Zadig 自动备份脚本

BACKUP_DIR="/opt/zadig/backups"
DATE=$(date +%Y%m%d_%H%M%S)

mkdir -p ${BACKUP_DIR}

echo "[$(date)] 开始备份..."

# 备份 MySQL
docker exec zadig-mysql mysqldump \
  -uroot -p${MYSQL_PASSWORD:-zadig2024} \
  --single-transaction \
  --routines \
  --triggers \
  zadig | gzip > ${BACKUP_DIR}/mysql_${DATE}.sql.gz

# 备份 Zadig 数据卷
docker run --rm \
  -v zadig-data:/data \
  -v ${BACKUP_DIR}:/backup \
  alpine tar czf /backup/zadig_data_${DATE}.tar.gz -C /data .

# 清理 7 天前的备份
find ${BACKUP_DIR} -mtime +7 -delete

echo "[$(date)] 备份完成: ${BACKUP_DIR}"
```

#### 手动恢复

```bash
# 恢复 MySQL
gunzip < backups/mysql_20260707_030000.sql.gz | \
  docker exec -i zadig-mysql mysql -uroot -p<password> zadig

# 恢复数据卷
docker run --rm \
  -v zadig-data:/data \
  -v $(pwd)/backups:/backup \
  aline tar xzf /backup/zadig_data_<date>.tar.gz -C /data
```

### 5.3 升级 Zadig

```bash
cd /opt/zadig

# 拉取新版本镜像
docker compose pull

# 重启服务（自动使用新镜像）
bash deploy.sh --restart
```

---

## 6. 常见问题

### Q1: 端口 8080 被占用怎么办？

**A**: 修改 `docker-compose.yml` 中的端口映射：

```yaml
services:
  zadig-server:
    ports:
      - "9090:80"   # 改为其他端口，如 9090
```

然后重启：`bash deploy.sh --restart`

### Q2: 内存不足怎么办？

**A**: Zadig 推荐最低 4GB 内存。如果内存紧张：

1. **限制容器内存**（在 docker-compose.yml 中添加）：
```yaml
services:
  zadig-server:
    deploy:
      resources:
        limits:
          memory: 2G
  zadig-mysql:
    deploy:
      resources:
        limits:
          memory: 1G
```

2. **增加交换空间**：
```bash
# 创建 2GB swap
sudo fallocate -l 2G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile

# 持久化
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
```

### Q3: Docker 镜像拉取慢怎么办？

**A**: 配置国内镜像加速器：

编辑 `/etc/docker/daemon.json`:
```json
{
  "registry-mirrors": [
    "https://mirror.ccs.tencentyun.com",
    "https://docker.mirrors.ustc.edu.cn"
  ]
}
```

然后重启 Docker：
```bash
sudo systemctl daemon-reload
sudo systemctl restart docker
```

### Q4: 如何重置管理员密码？

**A**:

```bash
# 进入 MySQL 容器
docker exec -it zadig-mysql bash

# 连接数据库
mysql -uroot -p<password> zadig

# 更新密码（假设用户表为 users）
UPDATE users SET password='新密码的MD5哈希' WHERE username='admin';

# 或者删除 admin 用户重新注册（更简单）
DELETE FROM users WHERE username='admin';
```

然后重新访问 http://localhost:8080 注册。

### Q5: 如何配置 Nginx 反向代理？

**A**: 如果你想用域名访问（如 `ci.marschat.online`）：

```nginx
server {
    listen 443 ssl;
    server_name ci.marschat.online;
    
    ssl_certificate     /path/to/cert.pem;
    ssl_certificate_key /path/to/key.pem;
    
    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        
        # WebSocket 支持（Zadig 需要）
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        
        # 超时时间（构建可能耗时较长）
        proxy_read_timeout 300s;
        proxy_send_timeout 300s;
    }
}
```

### Q6: 构建失败如何排查？

**A**:

1. **查看实时日志**:
   ```bash
   bash deploy.sh --logs
   ```

2. **在 Zadig Web UI 查看**:
   - 进入流水线 → 点击具体运行记录 → 查看每个步骤日志

3. **常见原因**:
   - Maven 依赖下载失败 → 检查网络和 Maven 镜像源
   - SSH 连接失败 → 检查目标服务器是否可达、密码是否正确
   - Docker 构建失败 → 检查 Dockerfile 和基础镜像
   - 端口冲突 → 检查目标服务器端口是否被占用

---

## 7. 进阶功能

### 7.1 配置自建 Runner（访问内网）

如果你的目标服务器在内网（如 `192.168.31.182`），需要在内网机器上安装 **Zadig Runner**：

#### 安装 Runner

```bash
# 在内网机器上执行（如内网 Debian）

# 1. 下载 Runner 安装包（从 Zadig UI 获取安装命令）
# 登录 Zadig → 系统设置 → Runner 管理 → 添加 Runner → 复制安装命令

# 2. 示例安装命令（实际以 Zadig UI 显示为准）
curl -sfL https://get.zadig.kl7e.com | bash -s -- \
  --server-url=http://100.93.36.113:8080 \
  --token=<your-runner-token> \
  --labels=lan-runner

# 3. 启动 Runner
systemctl start zadig-runner
```

#### 在流水线中使用 Runner

修改 `devtools-pipeline.yaml`，指定 Runner：

```yaml
steps:
  - step: ssh@1
    name: 部署到内网
    runs-on: [lan-runner]  # 指定内网 Runner
    with:
      host: 192.168.31.182  # 内网 IP
      ...
```

### 7.2 集成通知（钉钉/企业微信/飞书）

#### 钉钉机器人通知

1. 创建钉钉群机器人（获取 Webhook URL）
2. 在流水线中添加通知步骤：

```yaml
steps:
  - step: dingtalk@1
    name: 钉钉通知
    if: always()  # 无论成功失败都通知
    with:
      webhook: ${{DINGTALK_WEBHOOK}}
      message: |
        ## 🚀 Zadig 构建通知
        
        **项目**: devtools
        **状态**: {{.Job.Status}}
        **分支**: {{.Git.Branch}}
        **提交**: {{.Git.CommitShort}}
        **触发者**: {{.Trigger.By}}
        
        [查看详情]({{.Job.Link}})
```

### 7.3 定时触发（Cron）

在 Zadig 流水线设置中添加定时任务：

```yaml
trigger:
  schedule:
    # 每天凌晨 2 点自动构建
    cron: "0 2 * * *"
    # 仅在 master 分支触发
    branch: master
```

### 7.4 多环境管理

配置开发/测试/生产多套环境：

```yaml
environments:
  - name: dev
    displayName: "🧪 开发环境"
    variables:
      DEPLOY_TARGET: dev
      DEPLOY_HOST: 10.0.0.1
      
  - name: test
    displayName: "🧫 测试环境"
    variables:
      DEPLOY_TARGET: test
      DEPLOY_HOST: 10.0.0.2
      
  - name: prod
    displayName: "🏭 生产环境"
    variables:
      DEPLOY_TARGET: production
      DEPLOY_HOST: 100.93.36.113
```

---

## 📞 技术支持

### 官方资源

| 资源 | 地址 |
|------|------|
| **官网** | https://zadig.kl7e.com/ |
| **文档** | https://docs.zadig.kl7e.com/ |
| **GitHub** | https://github.com/koderover/zadig |
| **Gitee** | https://gitee.com/koderover/zadig |
| **社区** | 加入钉钉群/微信群（官网扫码） |

### 社区支持

- **钉钉技术交流群**: 扫描官网二维码加入
- **GitHub Issues**: 提交 Bug 或功能请求
- **微信公众号**: KodeRover（关注获取最新动态）

---

## 📝 更新日志

| 日期 | 版本 | 内容 |
|------|------|------|
| 2026-07-07 | v1.0 | 初始版本，从 Drone 迁移 |

---

## 🎯 下一步

1. ✅ **立即部署**: 按照 [第2节](#2-快速开始5分钟上手) 部署 Zadig
2. 📖 **学习更多**: 阅读 [官方文档](https://docs.zadig.kl7e.com/)
3. 🔧 **配置流水线**: 导入 `devtools-pipeline.yaml`
4. 🚀 **首次运行**: 触发 mykng 项目构建测试
5. 🔔 **集成通知**: 配置钉钉/企业微信构建通知

---

**祝使用愉快！如有问题欢迎提 Issue 或联系社区。** 🎉

---

*本文档基于 Zadig 最新版本编写，如遇差异请以官方文档为准。*
