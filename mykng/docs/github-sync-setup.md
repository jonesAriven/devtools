# 🔄 Gitee → GitHub 双向镜像同步配置指南

## 📋 概述

本文档说明如何配置 **Gitee 主仓库** 到 **GitHub 镜像仓库** 的自动同步，使得 GitHub Actions CI/CD 流水线能够正常触发。

### 架构流程

```
┌─────────────┐     Push      ┌─────────────┐
│   本地开发   │ ────────────→ │  Gitee (主)  │
│  (IDE/CLI)  │               │  devtools    │
└─────────────┘               └──────┬──────┘
                                     │
                              [自动同步]
                                     │ (每5分钟)
                                     ↓
                             ┌─────────────┐
                             │  GitHub (镜)  │←── 触发 CI/CD
                             │  mykng       │
                             └─────────────┘
```

---

## 🔧 配置步骤（共 5 步）

### Step 1: 在 GitHub 创建空仓库

1. 登录 [GitHub](https://github.com)
2. 点击 **New repository**
3. 填写信息：
   - **Repository name**: `mykng` （或你喜欢的名字）
   - **Description**: `mykng 知识库微服务 - Gitee 镜像仓库`
   - **Visibility**: Private（推荐，与 Gitee 保持一致）
4. **⚠️ 重要：不要勾选任何初始化选项！**
   - ❌ 不勾选 "Add a README file"
   - ❌ 不勾选 "Add .gitignore"
   - ❌ 不勾选 "Choose a license"
5. 点击 **Create repository**

### Step 2: 获取 Gitee Personal Access Token

1. 登录 [Gitee](https://gitee.com)
2. 点击右上角头像 → **设置**
3. 左侧菜单选择 **私人令牌**
4. 点击 **生成新令牌**
5. 配置权限：
   - **描述**: `GitHub Sync Bot`
   - **权限**: 勾选 `projects`（项目读写权限）
6. 点击 **提交** 并复制生成的 Token（只显示一次！）

### Step 3: 配置 GitHub Secrets

1. 进入刚创建的 GitHub 仓库
2. 点击 **Settings** → **Secrets and variables** → **Actions**
3. 点击 **New repository secret**
4. 添加以下 Secret：

| Name | Value | Description |
|------|-------|-------------|
| `GITEE_TOKEN` | `<你的Gitee Token>` | Gitee 私人令牌 |

5. （可选）如果需要通知功能，还可以添加：

| Name | Value | Description |
|------|-------|-------------|
| `SLACK_WEBHOOK_URL` | `<Slack Webhook>` | Slack 通知 URL |
| `NEXUS_USER` | `<Nexus用户名>` | Maven 私服用户名 |
| `NEXUS_PASS` | `<Nexus密码>` | Maven 私服密码 |
| `SONAR_TOKEN` | `<SonarQube Token>` | SonarQube 认证 Token |
| `SONAR_HOST_URL` | `<SonarQube URL>` | SonarQube 服务地址 |

### Step 4: 推送代码到 GitHub（首次初始化）

在本地执行以下命令：

```bash
# 1. 进入 mykng 项目目录
cd d:/huliang/java/ideaworkspace/devtools/mykng

# 2. 添加 GitHub 远程仓库（替换 <your-github-username>）
git remote add github https://github.com/<your-github-username>/mykng.git

# 3. 推送所有分支到 GitHub
git push github --all

# 4. 推送所有 Tags 到 GitHub
git push github --tags
```

### Step 5: 触发首次同步测试

1. 进入 GitHub 仓库的 **Actions** 标签页
2. 左侧选择 **Sync from Gitee** 工作流
3. 点击 **Run workflow**
4. 选择分支（默认 main），点击绿色 **Run workflow** 按钮
5. 等待运行完成，检查是否成功

---

## 🎯 同步工作流特性

### 自动触发方式

| 触发方式 | 说明 | 适用场景 |
|----------|------|----------|
| **定时任务** | 每 5 分钟自动检查更新 | 日常开发 |
| **手动触发** | 通过 Actions 页面手动运行 | 首次同步、紧急同步 |
| **Webhook** | 外部 API 调用触发 | 与 Gitee Webhook 集成 |

### 同步内容

- ✅ 所有配置的分支（main, develop, dev）
- ✅ 所有 Git Tags
- ✅ 完整的 Commit 历史
- ⏭️ 跳过已同步的内容（增量同步）

### 强制推送选项

当需要覆盖 GitHub 的历史记录时（如回滚操作），可以：

1. 进入 **Actions** → **Sync from Gitee**
2. 勾选 **Force push** 选项为 `true`
3. 点击 **Run workflow**

⚠️ **警告**：强制推送会丢失 GitHub 上的提交历史，请谨慎使用！

---

## 📊 监控与调试

### 查看同步状态

1. GitHub 仓库 → **Actions** 标签页
2. 点击 **Sync from Gitee** 工作流
3. 查看最近的运行记录和日志

### 常见问题排查

#### 问题 1: 同步失败 - Authentication failed

**原因**: GITEE_TOKEN 无效或过期

**解决**:
1. 重新生成 Gitee Token
2. 更新 GitHub Secrets 中的 `GITEE_TOKEN`

#### 问题 2: 分支冲突

**原因**: GitHub 上有新的 commit，与 Gitee 不同步

**解决**:
- 使用 Force push 选项强制同步
- 或先手动处理冲突

#### 问题 3: 定时任务不触发

**原因**: GitHub Actions 对公开仓库的定时任务有限制

**解决**:
- 确保仓库是 Private（私有仓库无限制）
- 或使用外部 webhook 触发

---

## 🔄 本地开发工作流（配置完成后）

### 正常开发流程

```bash
# 1. 正常开发代码
git add .
git commit -m "feat: 新功能"

# 2. Push 到 Gitee（主仓库）
git push origin develop

# 3. 等待 5 分钟（或手动触发 Actions）→ 自动同步到 GitHub
# 4. GitHub Actions 自动触发 CI/CD 流水线
```

### 快速手动同步（可选）

如果你不想等 5 分钟，可以创建一个本地脚本：

```bash
# 文件: scripts/sync-to-github.sh
#!/bin/bash
echo "🔄 Triggering manual sync..."

# 方式 1: 使用 GitHub CLI（需要安装 gh）
gh workflow run "sync-from-gitee.yml" -r main

# 方式 2: 使用 curl 调用 GitHub API
# curl -X POST \
#   -H "Authorization: token $GITHUB_TOKEN" \
#   -H "Accept: application/vnd.github.v3+json" \
#   https://api.github.com/repos/<owner>/mykng/actions/workflows/sync-from-gitee.yml/dispatches \
#   -d '{"ref":"main"}'

echo "✅ Sync triggered! Check GitHub Actions for progress."
```

---

## 🛡️ 安全注意事项

1. **Token 安全**:
   - Gitee Token 存储在 GitHub Secrets 中，不会暴露在日志中
   - 定期轮换 Token（建议每 90 天）

2. **仓库权限**:
   - 确保 GitHub 仓库设置为 Private
   - 仅授权团队成员访问

3. **审计日志**:
   - GitHub Actions 运行记录可在 Actions 页面查看
   - 可追踪所有同步操作

---

## 📞 技术支持

如有问题，请检查：
1. GitHub Actions 运行日志
2. Gitee Token 是否有效
3. 网络连接是否正常

---

**最后更新**: 2026-07-04
**维护者**: huliang
