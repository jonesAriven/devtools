# CI/CD 工具对比分析 — Gitea Actions vs Woodpecker CI

## 📋 目录
- [1. 核心差异对比](#1-核心差异对比)
- [2. 资源占用对比](#2-资源占用对比)
- [3. 配置语法对比](#3-配置语法对比)
- [4. 部署复杂度对比](#4-部署复杂度对比)
- [5. 适用场景分析](#5-适用场景分析)
- [6. 最终推荐](#6-最终推荐)

---

## 1. 核心差异对比

| 维度 | **Gitea Actions** | **Woodpecker CI** |
|------|------------------|-------------------|
| **类型** | 代码托管平台内置的 CI/CD | 独立 CI/CD 平台 |
| **语言** | Go | Go |
| **协议** | Apache 2.0 | Apache 2.0 |
| **首次发布** | 2023年（Gitea 1.19） | 2021年（从 Drone 分支） |
| **最新版本** | Gitea 1.26.4 (2026-07) | Woodpecker v3.x (2026-07) |
| **中文支持** | ✅ 官方全中文 | ❌ 英文（社区翻译） |
| **GitHub 兼容性** | ✅ 100% 兼容 GitHub Actions | ❌ 不兼容（但兼容 Drone） |

---

## 2. 资源占用对比

### 🖥️ mykng 虚拟机 (3核 / 11GB 内存)

#### Gitea + Gitea Actions 架构
```
┌─────────────────────────────────────┐
│  mykng 虚拟机 (3C / 11GB)           │
├─────────────────────────────────────┤
│  ┌───────────┐  ┌─────────────────┐ │
│  │  Gitea    │  │  act runner     │ │
│  │  Server   │  │  (CI/CD Runner) │ │
│  │  ~200MB   │  │  ~300-500MB     │ │
│  └───────────┘  └─────────────────┘ │
│  ┌───────────┐                      │
│  │  PostgreSQL│                     │
│  │  ~100MB   │                      │
│  └───────────┘                      │
│                                     │
│  总计: ~600MB - 800MB               │
│  剩余: ~10GB 可用                   │
└─────────────────────────────────────┘
```

#### Woodpecker CI 架构
```
┌─────────────────────────────────────┐
│  mykng 虚拟机 (3C / 11GB)           │
├─────────────────────────────────────┤
│  ┌───────────┐  ┌─────────────────┐ │
│  │ Woodpecker│  │ Woodpecker      │ │
│  │ Server    │  │ Agent           │ │
│  │ ~150MB    │  │ ~200-300MB      │ │
│  └───────────┘  └─────────────────┘ │
│  ┌───────────┐                      │
│  │ PostgreSQL│                     │
│  │ ~50MB     │                      │
│  └───────────┘                      │
│                                     │
│  总计: ~400MB - 500MB               │
│  剩余: ~10.5GB 可用                 │
└─────────────────────────────────────┘
```

### 📊 详细资源对比

| 组件 | Gitea Actions | Woodpecker CI |
|------|--------------|---------------|
| 主服务内存 | 150-200 MB | 100-150 MB |
| Runner/Agent | 300-500 MB | 200-300 MB |
| 数据库 | 100 MB (PostgreSQL) | 50 MB (PostgreSQL) |
| **总计** | **550-800 MB** | **350-500 MB** |
| 磁盘占用 | ~500 MB | ~300 MB |
| CPU 占用 | 低-中 | 低 |

---

## 3. 配置语法对比

### 📝 示例：构建并部署 Java 项目

#### Gitea Actions 语法 (`.gitea/workflows/build.yml`)
```yaml
name: 构建 and 部署

on:
  push:
    branches: [dev, master]
  workflow_dispatch:  # 手动触发

env:
  MAVEN_OPTS: -Dmaven.repo.local=.m2/repository

jobs:
  build-and-deploy:
    runs-on: ubuntu-latest
    
    steps:
      - name: 检出代码
        uses: actions/checkout@v4
      
      - name: 设置 JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
          cache: maven
      
      - name: Maven 编译打包
        run: mvn clean package -DskipTests
      
      - name: SSH 部署到服务器
        uses: appleboy/scp-action@v0.1.7
        with:
          host: ${{ secrets.DEPLOY_HOST }}
          username: root
          key: ${{ secrets.SSH_PRIVATE_KEY }}
          source: "target/*.jar"
          target: "/opt/app/"
          
      - name: 重启服务
        uses: appleboy/ssh-action@v1.0.3
        with:
          host: ${{ secrets.DEPLOY_HOST }}
          username: root
          key: ${{ secrets.SSH_PRIVATE_KEY }}
          script: |
            cd /opt/app
            ./deploy.sh restart
```

#### Woodpecker CI 语法 (`.woodpecker.yml` 或 `.drone.yml`)
```yaml
# Woodpecker 完全兼容 .drone.yml 语法！

kind: pipeline
type: docker
name: 构建 and 部署

trigger:
  branch:
    - dev
    - master
  event:
    - push
    - custom  # 手动触发

steps:
  # ---- Step 1: Maven 编译 ----
  - name: Maven 编译打包
    image: maven:3.9-eclipse-temurin-17
    commands:
      - mvn clean package -DskipTests
    volumes:
      - /root/.m2:/root/.m2  # 缓存 Maven 依赖
  
  # ---- Step 2: SCP 上传 JAR 包 ----
  - name: 上传到部署服务器
    image: appleboy/drone-scp
    settings:
      host:
        from_secret: deploy_host
      username: root
      key:
        from_secret: ssh_private_key
      source: "target/*.jar"
      target: /opt/app/
  
  # ---- Step 3: SSH 执行部署脚本 ----
  - name: 执行部署脚本
    image: appleboy/drone-ssh
    settings:
      host:
        from_secret: deploy_host
      username: root
      key:
        from_secret: ssh_private_key
      script:
        - cd /opt/app && ./deploy.sh restart
```

### 🔍 语法差异总结

| 特性 | Gitea Actions | Woodpecker CI |
|------|--------------|---------------|
| **配置文件位置** | `.gitea/workflows/*.yaml` | 仓库根目录 `.woodpecker.yml` 或 `.drone.yml` |
| **语法风格** | GitHub Actions YAML | Drone YAML |
| **步骤定义** | `steps:` → `- name: xxx` | `steps:` → `- name: xxx` |
| **镜像使用** | `uses: actions/xxx@vX` | `image: xxx` |
| **密钥引用** | `${{ secrets.XXX }}` | `from_secret: xxx` |
| **条件执行** | `if:` | `when:` |
| **缓存机制** | `actions/cache` | `volumes:` 或插件缓存 |
| **学习曲线** | 中等（需了解 GitHub Actions） | 低（Drone 语法更简单） |

---

## 4. 部署复杂度对比

### 🚀 Gitea + Actions 部署步骤（共 8 步）

```bash
# Step 1: 创建目录
mkdir -p /opt/gitea && cd /opt/gitea

# Step 2: 创建 docker-compose.yml
cat > docker-compose.yml << 'EOF'
services:
  gitea:
    image: gitea/gitea:latest
    ports:
      - "3000:3000"   # Web UI
      - "2222:22"     # SSH
    volumes:
      - gitea-data:/data
      - /etc/timezone:/etc/timezone:ro
      - /etc/localtime:/etc/localtime:ro
    environment:
      - USER_UID=1000
      - USER_GID=1000
      - GITEA__database__DB_TYPE=postgres
      - GITEA__database__HOST=db:5432
      - GITEA__database__NAME=gitea
      - GITEA__database__USER=gitea
      - GITEA__database__PASSWD=gitea_password
    depends_on:
      db:
        condition: service_healthy

  db:
    image: postgres:14
    environment:
      - POSTGRES_USER=gitea
      - POSTGRES_PASSWORD=gitea_password
      - POSTGRES_DB=gitea
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U gitea"]
      interval: 10s
      timeout: 5s
      retries: 10

volumes:
  gitea-data:
EOF

# Step 3: 启动 Gitea
docker compose up -d

# Step 4: 初始化 Gitea（访问 http://localhost:3000 完成初始化）

# Step 5: 下载 act runner
wget -O act-runner https://dl.gitea.com/act_runner/latest/act-runner-linux-amd64
chmod +x act-runner

# Step 6: 注册 runner（需要在 Gitea UI 中获取 token）
./act-runner register \
  --instance http://localhost:3000 \
  --token <从Gitea获取的token> \
  --no-interactive

# Step 7: 启动 runner
nohup ./act-runner daemon > /var/log/act-runner.log 2>&1 &

# Step 8: 创建测试流水线
mkdir -p .gitea/workflows
cat > .gitea/workflows/test.yml << 'EOF'
name: Test
on: [push]
jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - run: echo "Hello from Gitea Actions!"
EOF
```

### 🚀 Woodpecker CI 部署步骤（共 5 步）

```bash
# Step 1: 创建目录
mkdir -p /opt/woodpecker && cd /opt/woodpecker

# Step 2: 创建 docker-compose.yml（见上方完整配置）
cat > docker-compose.yml << 'EOF'
# ... （见 woodpecker/docker-compose.yml）
EOF

# Step 3: 生成随机密钥
export WOODPECKER_SECRET=$(openssl rand -hex 32)
export WOODPECKER_AGENT_SECRET=$(openssl rand -hex 32)
export WOODPECKER_DB_PASSWORD=$(openssl rand -hex 16)

# Step 4: 在 Gitee/GitHub 创建 OAuth 应用
# 访问 https://gitee.com/oauth/applications 创建应用
# 设置回调地址为：https://ci.marschat.online/callback

# Step 5: 启动服务
docker compose up -d

# 完成！访问 https://ci.marschat.online
```

### ⏱️ 部署时间对比

| 步骤 | Gitea + Actions | Woodpecker CI |
|------|----------------|---------------|
| 准备工作 | 5 分钟 | 2 分钟 |
| 下载镜像 | 3-5 分钟 | 2-3 分钟 |
| 初始化配置 | 10-15 分钟 | 5 分钟 |
| OAuth 配置 | 5-10 分钟 | 5-10 分钟 |
| **总计** | **25-35 分钟** | **15-20 分钟** |

---

## 5. 适用场景分析

### ✅ 选择 **Gitea Actions** 的场景：

1. **需要自托管 Git 仓库**
   - 不想依赖 Gitee/GitHub 等第三方平台
   - 数据安全要求高，必须代码在内网
   
2. **团队熟悉 GitHub Actions**
   - 开发者已经习惯 GitHub Actions 语法
   - 有大量现成的 GitHub Actions 工作流可以复用
   
3. **追求中文体验**
   - 希望界面、文档、错误提示都是中文
   - 团队成员英语水平有限
   
4. **需要一体化解决方案**
   - 想要代码托管 + CI/CD + 代码审查一个平台搞定
   - 不想维护多个系统

---

### ✅ 选择 **Woodpecker CI** 的场景：

1. **已有 .drone.yml 配置**
   - 从 Drone 迁移过来，不想重写配置
   - 已经有成熟的 Drone 流水线模板
   
2. **追求极致轻量**
   - 服务器资源紧张（< 4核/8GB）
   - 只需要 CI/CD 功能，不需要代码托管
   
3. **快速上线**
   - 想在最短时间内搭建好 CI/CD 系统
   - 不想花时间学习和配置新工具
   
4. **使用 Gitee/GitHub 托管代码**
   - 已经在使用 Gitee 或 GitHub
   - 不想再维护一个 Git 服务器

---

## 6. 最终推荐

### 🏆 针对 your 场景的推荐

根据你的实际情况：
- ✅ **mykng**: 3核/11GB（资源有限）
- ✅ **已有 .drone.yml 配置**（可直接复用）
- ✅ **使用 Gitee 托管代码**（不需要自建 Git）
- ✅ **想要快速部署**（不想花太多时间）

#### 🥇 **首选方案：Woodpecker CI**

**理由：**
1. ✅ **零迁移成本** - 你的 `.drone.yml` 可以直接用
2. ✅ **最轻量** - 只占 400-500MB 内存（节省 200-300MB）
3. ✅ **最快部署** - 15-20 分钟即可完成
4. ✅ **完美适配** - 支持 Gitee OAuth，无需自建 Git

**部署后效果：**
```
当前状态：
  mykng (3C/11GB)
  ├── 已有服务（Jenkins, Portal, ...）~ 6GB
  └── 剩余可用 ~ 5GB

部署 Woodpecker 后：
  mykng (3C/11GB)
  ├── 已有服务 ~ 6GB
  ├── Woodpecker CI ~ 500MB
  └── 剩余可用 ~ 4.5GB ✅ 充足
```

---

#### 🥈 **备选方案：Gitea + Gitea Actions**

如果你更看重**中文体验**和**一体化管理**，可以选择这个方案：

**额外好处：**
1. ✅ 未来可以将代码从 Gitee 迁移到自己的 Gitea
2. ✅ 全中文界面，降低团队学习成本
3. ✅ GitHub Actions 生态，社区资源丰富

**代价：**
1. ❌ 需要重写所有流水线配置（约 2-3 天工作量）
2. ❌ 多占用 200-300MB 内存
3. ❌ 部署时间多 10-15 分钟

---

## 📌 下一步行动

### 如果选择 Woodpecker（推荐）：

我已经为你准备好了完整的配置文件：
- ✅ `woodpecker/docker-compose.yml` - Docker Compose 配置
- ✅ 接下来我会创建：
  - `woodpecker/deploy.sh` - 一键部署脚本
  - `woodpecker/woodpecker-nginx.conf` - Nginx 反向代理配置
  - `docs/woodpecker-setup.md` - 详细使用指南

### 如果选择 Gitea Actions：

我可以帮你：
1. 创建 Gitea 的 Docker Compose 配置
2. 编写 act runner 安装脚本
3. 将现有的 `.drone.yml` 迁移到 `.gitea/workflows/*.yaml`
4. 配置 Nginx 反向代理

---

## 🔗 参考链接

### Gitea Actions
- 官方文档: https://docs.gitea.com/usage/actions/overview
- 快速开始: https://docs.gitea.com/usage/actions/quickstart
- act runner: https://gitea.com/gitea/act_runner
- Gitea 下载: https://about.gitea.com/download

### Woodpecker CI
- 官方网站: https://woodpecker-ci.org/
- 文档: https://woodpecker-ci.org/docs/
- GitHub: https://github.com/woodpeckerci/woodpecker
- Docker Hub: https://hub.docker.com/r/woodpeckerci/woodpecker-server

---

**文档更新时间**: 2026-07-07
**作者**: CatPaw AI Assistant
**适用版本**: 
- Gitea 1.26.4+
- Woodpecker v3.x+
