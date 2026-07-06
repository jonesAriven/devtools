# 🚀 kb-cicd — Jenkins CI/CD 部署指南

## 📋 目录结构

```
jenkins/
├── docker-compose.yml      # Docker Compose 编排（一键部署）
├── jenkins.yaml            # JCasC 配置（Configuration as Code）
├── plugins.txt             # 预装插件列表
├── maven-settings.xml      # Maven 配置（指向 Nexus 私服）
├── init.groovy.d/          # 初始化脚本
│   ├── 01-install-plugins.groovy    # 自动安装插件
│   ├── 02-setup-security.groovy     # 安全配置
│   ├── 03-setup-tools.groovy        # Maven + JDK 配置
│   ├── 04-setup-jobs.groovy         # 创建 Pipeline 任务
│   └── 05-setup-credentials.groovy  # 凭据配置
└── README.md               # 本文件

# 根目录
├── Jenkinsfile             # Pipeline 定义（从 Drone 迁移）
```

## ⚡ 快速开始（3步启动）

### 前置条件

- [x] Docker 已安装（mykng 主机）
- [x] Docker Compose v2+
- [x] 端口 `8096` 和 `50000` 可用
- [x] 至少 **3GB** 可用内存给 Jenkins

### 第1步：创建环境变量文件

```bash
cd /root/devtools/jenkins

cat > .env << 'EOF'
# ====== 管理员密码 ======
ADMIN_PASSWORD=admin@2024!

# ====== SSH 部署密码 ======
DEPLOY_PASS_MYKNG=<mykng主机root密码>
DEPLOY_PASS_LAN=<内网Debian root密码>

# ====== Nexus 私服凭据 ======
NEXUS_USER=admin
NEXUS_PASS=<Nexus密码>

# ====== Gitee Token（可选，用于 Webhook 触发）=====
GITEE_TOKEN=
EOF
```

> ⚠️ **安全提醒**: `.env` 文件包含敏感信息，请确保权限正确：
> ```bash
> chmod 600 .env
> ```

### 第2步：启动 Jenkins

```bash
# 启动服务
docker compose up -d

# 查看日志（首次启动需要 3-5 分钟安装插件）
docker compose logs -f

# 看到 "Jenkins is fully running" 表示启动成功
```

### 第3步：访问 Web UI

打开浏览器访问：`http://<mykng-ip>:8096`

| 项目 | 值 |
|------|-----|
| **URL** | `http://100.93.36.113:8096` (Tailscale) 或 `http://<内网IP>:8096` |
| **用户名** | `admin` |
| **密码** | `.env` 中配置的 `ADMIN_PASSWORD` |

---

## 🔧 使用指南

### 手动触发构建

1. 打开 Jenkins 首页
2. 点击任务名称（如 `devtools-mykng`）
3. 左侧菜单 → **Build with Parameters**
4. 选择参数：
   - `DEPLOY_PROJECT`: `all` / `mykng` / `active-manager`
   - `DEPLOY_TARGET`: `production` / `dev`
5. 点击 **Build**

### 查看构建日志

- 构建中：点击左侧 **Console Output**（实时刷新）
- 构建完成：点击历史记录中的 **#编号**

### 新增项目（5分钟搞定）

#### 方法A：修改根目录 Jenkinsfile（推荐）

在 `Jenkinsfile` 的 `stages` 块中添加新 stage：

```groovy
stage('🏷️ kb-ops 运维平台') {
    when {
        anyOf { expression { params.DEPLOY_PROJECT == 'all' || params.DEPLOY_PROJECT == 'kb-ops' } }
    }
    steps {
        sh 'cd kb-ops && mvn clean package -DskipTests -B -V -ntp && cd ..'
        sshPublisher(publishers: [sshPublisherDesc(
            configName: 'lan-deploy',
            transfers: [sshTransfer(execCommand: 'bash /root/devtools/kb-ops/ci/deploy.sh ...')],
            verbose: true
        )])
    }
}
```

然后：
1. 在 `parameters.choice(DEPLOY_PROJECT, ...)` 中添加 `'kb-ops'`
2. 提交代码到 Gitee
3. 下次构建自动生效 ✅

#### 方法B：独立 Jenkinsfile（适合复杂项目）

在项目目录下创建独立的 `Jenkinsfile`：

```bash
# 例如: mykng/Jenkinsfile.mykng
# active-manager/Jenkinsfile.active-manager
```

然后在 Jenkins 任务配置中指定不同的 Jenkinsfile 路径。

### 配置 SSH Server（部署目标）

首次使用前需要在 Jenkins 中配置 SSH Server：

1. **Manage Jenkins** → **System**
2. 找到 **SSH remote hosts** 区域
3. 点击 **Add**：

| 名称 | Hostname | Port | 用途 |
|------|----------|------|------|
| `mykng-deploy` | `100.93.36.113` | 22 | mykng 微服务部署 |
| `lan-deploy` | `192.168.31.182` | 22 | 内网 Debian 部署 |

4. **Credentials** 选择之前创建的 SSH 凭据
5. **Test Configuration** 验证连接成功
6. **Save**

---

## 🌐 配置 Gitee Webhook 自动触发

### 方式1：Gitee 插件（推荐）

1. **Manage Jenkins** → **Configure System**
2. 找到 **Gitee** 配置区域
3. 点击 **Advanced** → **Gitee Server URL**: `https://gitee.com`
4. **Credential**: 添加 Gitee Personal Access Token
   - 获取地址: Gitee → 设置 → 私人令牌 → 生成新令牌
   - 权限: `projects`
5. **Test Connection** 成功后保存

### 方式2：Generic Webhook Trigger（更灵活）

1. 安装插件 `generic-webhook-trigger`（已包含在 plugins.txt）
2. 任务配置 → **Triggers** → **Generic Webhook Trigger**
3. 设置 token: `devtools-cicd-token`
4. 保存

然后在 Gitee 仓库设置 Webhook：

| 字段 | 值 |
|------|-----|
| URL | `http://<jenkins-host>:8096/generic-webhook-trigger/invoke?token=devtools-cicd-token` |
| 密码 | （留空或设置与 token 一致） |
| 事件 | Push events |
| SSL | 取消勾选（内网） |

---

## 📊 性能优化说明

### 为什么比 GitHub Actions 快？

| 对比项 | GitHub Actions | Jenkins (本地) |
|--------|---------------|----------------|
| **网络延迟** | 国外服务器拉取代码/依赖慢 | 内网直连 Gitee + Nexus |
| **Maven 缓存** | 每次部分缓存（有限制） | **永久本地缓存** (`~/.m2/repository`) |
| **Docker 构建** | 需要上传上下文到远程 | **本地 Docker socket** 直连 |
| **SSH 部署** | 通过 FRP 穿透（不稳定） | **Tailscale 内网直连**（稳定） |
| **典型耗时** | 8-15 分钟 | **3-6 分钟**（首次后） |

### Maven 缓存策略

```yaml
# docker-compose.yml 中已挂载 Maven 缓存卷
volumes:
  - maven_cache:/root/.m2/repository
```

首次构建会下载所有依赖（较慢），后续构建直接命中缓存，速度提升 **50-70%**。

如需清理缓存：
```bash
docker compose exec jenkins rm -rf /root/.m2/repository/*
```

---

## 🔐 安全建议

1. **修改默认密码**: 首次登录后立即修改 admin 密码
2. **启用 HTTPS**: 生产环境建议反代 Nginx + SSL
3. **限制访问**: 通过防火墙只允许内网/Tailscale 访问 8096
4. **定期备份**:
   ```bash
   # 备份 Jenkins 配置
   docker compose exec jenkins tar czf /tmp/jenkins-backup.tar.gz /var/jenkins_home
   docker cp kb-jenkins:/tmp/jenkins-backup.tar.gz ./backup/
   ```
5. **凭据加密**: 所有密码存储在 Jenkins 凭据库中（AES256 加密）

---

## 🆘 故障排查

### 问题1: 首次启动很慢

**原因**: 正在从 Jenkins Update Center 下载并安装 40+ 个插件

**解决**: 
```bash
# 查看进度
docker compose logs -f jenkins | grep "插件"
# 看到 "Jenkins is fully running" 即可
```

### 问题2: Maven 编译失败：依赖下载超时

**原因**: Nexus 私服不可达或配置错误

**解决**:
1. 检查 `maven-settings.xml` 中的 Nexus URL 是否正确
2. 测试连通性: `docker compose exec jenkins curl https://nexus.marschat.online`
3. 检查 `.env` 中的 `NEXUS_USER` / `NEXUS_PASS`

### 问题3: SSH 部署失败：Connection refused

**原因**: 目标主机不可达或 SSH 服务未启动

**解决**:
```bash
# 从 Jenkins 容器内测试连通性
docker compose exec jenkins ssh -o StrictHostKeyChecking=no root@100.93.36.113 echo ok
```

### 问题4: 内存不足 OOM Killed

**原因**: Jenkins 默认内存不够

**解决**:
```yaml
# docker-compose.yml 中增加内存限制
environment:
  JAVA_OPTS: "-Xms512m -Xmx2048m ..."  # 最大 2GB
mem_limit: 3g                            # Docker 限制 3GB
```

### 问题5: 如何重置 Jenkins？

⚠️ **警告**: 会丢失所有配置和构建历史！

```bash
# 停止并删除数据卷
docker compose down -v
# 重新启动（全新初始化）
docker compose up -d
```

---

## 📈 后续扩展计划

### 支持更多语言（已设计好架构）

Jenkins 天然支持多语言，只需添加对应 stage：

```groovy
// Node.js 项目示例
stage('🟢 frontend 前端') {
    agent { docker 'node:20-alpine' }
    steps {
        sh 'npm ci && npm run build'
        sshPublisher(...)
    }
}

// Python 项目示例  
stage='🐍 data-pipeline') {
    agent { docker 'python:3.11-slim' }
    steps {
        sh 'pip install -r requirements.py && python main.py'
    }
}

// Go 项目示例
stage('🔵 go-service') {
    agent { docker 'golang:1.21-alpine' }
    steps {
        sh 'go build -o app ./...'
    }
}
```

### 高级功能（按需开启）

| 功能 | 说明 | 开启方式 |
|------|------|---------|
| 🔄 多分支 Pipeline | 不同分支不同流程 | 任务类型改为 Multibranch Pipeline |
| 🎯 构建矩阵 | 多版本并行测试 | `matrix` 指令 |
| ✅ 审批流程 | 生产部署需人工确认 | `input` 指令 |
| 📧 通知 | 邮件/钉钉/企微 | 配置 Post 中的通知步骤 |
| 📊 报告 | 测试覆盖率、静态分析 | 对应插件 + `publish` 步骤 |
| 🔄 回滚 | 一键回滚到上一版本 | 保存 artifact + 重启部署 |

---

## 📝 与 Drone 的对比迁移对照

| Drone 概念 | Jenkins 对应 | 说明 |
|-----------|-------------|------|
| `.drone.yml` | `Jenkinsfile` | Pipeline 定义文件 |
| `kind: pipeline` | `pipeline {}` | 流水线块 |
| `steps:` | `steps {}` | 步骤块 |
| `image: maven:3.9` | `agent any` + `tools { maven 'Maven 3.9' }` | 使用预装工具 |
| `volumes:` | 不需要 | 直接用宿主机的 Maven 缓存 |
| `from_secret:` | `credentials()` | 凭据绑定 |
| `parameters:` | `parameters {}` | 参数定义 |
| `when:` event | `when {}` | 条件执行 |
| `failure: ignore` | `catchError {}` 或 `post { failure {} }` | 错误处理 |
| `trigger: branch` | `triggers {}` | 触发器 |
| Drone UI | Jenkins Dashboard | Web 界面 |

---

## 🙏 致谢

基于现有 Drone CI/CD 配置迁移而来，感谢 Drone 团队的优秀设计。
Jenkins 提供了更强的扩展性和企业级稳定性。
