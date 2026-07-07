# Gitee Go CI/CD 配置指南

## 📋 概述

本文档介绍如何将 devtools 项目的 CI/CD 从 Drone 迁移到 **Gitee Go**（Gitee 企业版流水线）。

### 为什么选择 Gitee Go？

| 优势 | 说明 |
|------|------|
| ✅ 全中文界面 | 无需英文，操作直观 |
| ✅ 国内加速 | 构建速度快，插件下载快 |
| ✅ 与 Gitee 深度集成 | 代码托管 + CI/CD 一站式 |
| ✅ 开箱即用 | 无需自建服务器维护 |
| ⚠️ 有额度限制 | 免费版限时试用，付费版按核分计费 |

---

## 🔧 配置步骤

### 第一步：开通 Gitee Go

1. 访问 [Gitee 企业版](https://gitee.com/enterprises)
2. 点击 **"免费试用"** 或 **"立即购买"**
3. 创建企业/团队
4. 邀请成员（可选）

### 第二步：创建流水线

1. 进入你的 Gitee 仓库：`jonesAriven/devtools`
2. 点击顶部菜单 **"流水线"** 或 **"CI/CD"**
3. 点击 **"新建流水线"**
4. 选择 **"YAML 流水线"**
5. 选择模板或使用已有配置文件

### 第三步：配置环境变量

在流水线设置中添加以下**密文变量**：

#### 必需的 SSH 凭据

| 变量名 | 值 | 说明 |
|--------|-----|------|
| `DEPLOY_HOST_MYKNG` | `100.93.36.113` | mykng 服务器 IP (Tailscale) |
| `DEPLOY_PASS_MYKNG` | `<mykng root密码>` | mykng 服务器 SSH 密码 |
| `DEPLOY_HOST_LAN` | `192.168.31.182` | 内网 Debian IP |
| `DEPLOY_PASS_LAN` | `<内网Debian root密码>` | 内网 Debian SSH 密码 |

> ⚠️ **安全提示**：这些是敏感信息，请确保在 Gitee Go 中设置为**加密变量**，不要明文写在 YAML 中

### 第四步：运行流水线

#### 手动触发

1. 进入流水线页面
2. 点击 **"运行"** 按钮
3. 填写参数：

```
DEPLOY_PROJECT: mykng          # 只部署知识库
DEPLOY_TARGET:  production      # 生产环境
```

#### 参数说明

| 参数 | 可选值 | 默认值 | 说明 |
|------|--------|--------|------|
| `DEPLOY_PROJECT` | `all`, `mykng`, `active-manager`, `mykng,active-manager` | `all` | 要部署的项目 |
| `DEPLOY_TARGET` | `production`, `dev`, `test` | `production` | 部署目标环境 |

---

## 📁 文件结构

```
devtools/
├── .gitee/
│   └── pipeline.yml          # Gitee Go 主配置（本文件）
├── mykng/
│   ├── ci/
│   │   └── deploy.sh         # mykng 部署脚本
│   └── docker-compose.yml    # Docker Compose 配置
├── active-manager/
│   ├── ci/
│   │   └── deploy.sh         # active-manager 部署脚本
│   └── ...
└── docs/
    └── gitee-go-setup.md     # 本文档
```

---

## 🚀 快速开始（5分钟上手）

### 1️⃣ 推送配置到 Gitee

```bash
# 确保你在 dev 分支
git checkout dev

# 添加 Gitee Go 配置
git add .gitee/pipeline.yml docs/gitee-go-setup.md
git commit -m "feat: 添加 Gitee Go CI/CD 配置"

# 推送到 Gitee
git push origin dev
```

### 2️⃣ 在 Gitee 中启用流水线

1. 打开 https://gitee.com/jonesAriven/devtools
2. 点击 **"流水线"** 标签
3. 如果提示未开启，点击 **"开启流水线"**
4. 系统会自动检测 `.gitee/pipeline.yml` 文件

### 3️⃣ 配置 SSH 密钥

在流水线设置 → **变量管理** 中添加：

- 变量名：`DEPLOY_PASS_MYKNG`
- 变量值：`<your_password>`
- 类型：**加密**（重要！）

重复以上步骤添加其他 3 个变量。

### 4️⃣ 第一次运行

1. 点击 **"运行流水线"**
2. 参数填写：
   - `DEPLOY_PROJECT`: `mykng`
   - `DEPLOY_TARGET`: `dev`
3. 观察构建日志
4. 等待部署完成（约 5-10 分钟）

---

## 📊 与 Drone 对比

| 特性 | Drone (旧) | Gitee Go (新) |
|------|-----------|---------------|
| **配置文件** | `.drone.yml` | `.gitee/pipeline.yml` |
| **触发方式** | Custom Event | 手动运行 / 定时 / Webhook |
| **SSH 部署** | `appleboy/drone-ssh` 插件 | `ssh-deploy@1` 步骤 |
| **Maven 构建** | 自定义 commands | `build@1` 步骤 |
| **中文支持** | ❌ 英文界面 | ✅ 全中文 |
| **国内速度** | 取决于服务器位置 | ✅ 国内加速 |
| **费用** | 免费（需自建 Runner） | 免费试用 / 299元/人/年 |

---

## 🔍 常见问题

### Q1: 流水线找不到 `.gitee/pipeline.yml`？

**A**: 确保：
1. 文件在仓库根目录的 `.gitee/` 文件夹下
2. 文件已推送到正确的分支（dev/master）
3. 文件名正确：`pipeline.yml`

### Q2: SSH 连接失败？

**A**: 检查：
1. 环境变量是否正确配置（注意变量名大小写）
2. 目标服务器是否可达（Gitee Go Runner 在公网）
3. 对于内网机器（192.168.31.182），需要通过 FRP 或 VPN 暴露端口

### Q3: Maven 编译太慢？

**A**: 
- Gitee Go 会缓存依赖（首次较慢，后续快）
- 可以在 settings.xml 中配置阿里云镜像源
- 考虑使用 `-o` 参数离线模式（如果依赖已缓存）

### Q4: 如何查看部署日志？

**A**:
1. 在流水线运行详情页，点击每个步骤查看日志
2. SSH 部署步骤会显示远程服务器的完整输出
3. 部署完成后，可以 SSH 到目标服务器查看 Docker 日志：
   ```bash
   docker compose -p kb-deploy logs -f
   ```

### Q5: 核分不够用怎么办？

**A**:
- 免费版：联系 Gitee 客服申请延长试用期
- 付费版：升级套餐或购买额外核分包
- 优化建议：减少不必要的构建步骤、使用缓存

---

## 🎯 下一步

### 高级功能（可选）

1. **定时触发**：设置每日自动构建
   ```yaml
   trigger:
     schedule:
       cron: "0 2 * * *"  # 每天凌晨2点
   ```

2. **通知集成**：构建失败时发送钉钉/企业微信通知
   
3. **多环境部署**：配置 dev/test/production 多套环境

4. **代码扫描**：集成 SonarQube 进行代码质量检查

---

## 📞 技术支持

- **Gitee 官方文档**: https://help.gitee.com/categories/gitee-go
- **Gitee 客服**: 400-606-0201
- **企业微信群**: 购买后可加入技术支持群

---

## 📝 更新日志

| 日期 | 版本 | 内容 |
|------|------|------|
| 2026-07-07 | v1.0 | 初始版本，从 Drone 迁移 |

---

**祝使用愉快！🎉**

如有问题，欢迎提 Issue 或联系管理员。
