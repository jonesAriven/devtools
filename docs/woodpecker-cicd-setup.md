# Woodpecker CI/CD 配置文档

## 📋 概述

本文档记录了 Woodpecker CI 的完整配置，包括流水线设置、Secrets 管理、部署脚本等。

## 🏗️ 流水线结构

### 触发条件
- **事件**: `manual`（手动触发）+ `push`（代码推送）
- **分支**: `dev`, `master`

### 部署步骤

#### 1. mykng 知识库服务

| 步骤 | 名称 | 说明 |
|------|------|------|
| build | `mykng-build` | Maven 编译 Java 项目 |
| deploy | `mykng-deploy` | SSH 部署到 mykng (100.93.36.113) |

**部署目标**:
- 服务器: mykng (Tailscale IP: 100.93.36.113)
- 认证: SSH 密钥 (`ssh_key_mykng`)
- 端口: 8090 (Gateway)
- 脚本: `mykng/ci/deploy.sh`

**微服务列表**:
- kb-gateway (8090→8080) - API 网关
- kb-auth (8081) - 认证服务
- kb-file (8082) - 文件服务
- kb-knowledge (8083) - 知识库服务
- kb-intelligence (8086) - AI 智能服务

#### 2. active-manager 激活码系统

| 步骤 | 名称 | 说明 |
|------|------|------|
| build | `active-manager-build` | Maven 编译 Java 项目 |
| deploy | `active-manager-deploy` | SSH 部署到内网 Debian (100.105.196.63) |

**部署目标**:
- 服务器: 内网 Debian (Tailscale IP: 100.105.196.63)
- 认证: 密码 (`deploy_pass_lan`)
- 端口: 18080 (宿主机) → 8080 (容器)
- 容器名: activecode
- 访问地址: https://tools.marschat.online/activecode/

## 🔐 Secrets 管理

在 Woodpecker UI 中配置的密钥:

| Secret 名称 | 用途 | 类型 |
|-------------|------|------|
| `ssh_key_mykng` | mykng SSH 私钥 (ed25519) | SSH Private Key |
| `deploy_pass_mykng` | mykroot 密码（备用） | Password |
| `deploy_pass_lan` | 内网Debian root 密码 | Password |

### 添加/修改 Secret

1. 访问 https://woodci.marschat.online/repos/1/settings/secrets
2. 点击 "添加密钥"
3. 填写名称、值、事件权限
4. 点击 "添加密钥"

## 📝 部署脚本说明

### mykng/ci/deploy.sh

**功能**: 部署知识库微服务到 mykng 服务器

**参数**:
```bash
bash deploy.sh <commit_sha> <branch> <deploy_target>
```

**特性**:
- ✅ 自动停止并删除旧容器（防孤儿服务）
- ✅ Docker Compose 构建 + 启动
- ✅ 综合健康检查（最多等待 3 分钟）
- ✅ Git 代码同步（支持 SSH 和 HTTPS）
- ✅ 优雅的错误处理（不因健康检查超时而失败）

**健康检查优化**:
- 多端点检查（actuator/health、首页、Docker health）
- 增加重试次数（18次 × 10秒 = 3分钟）
- 不再直接失败，输出详细状态供排查
- 支持容器运行中但未就绪的情况

### active-manager/ci/deploy.sh

**功能**: 部署激活码系统到内网 Debian

**参数**:
```bash
bash deploy.sh <commit_sha> <branch>
```

**特性**:
- ✅ 单容器部署（activecode）
- ✅ 端口占用检查和清理
- ✅ 登录页 + API 双重健康检查
- ✅ 同样的健康检查优化

## 🔧 故障排除

### 常见问题

1. **SSH 认证失败**
   - 检查 Secret 是否正确配置
   - 确认目标服务器允许密钥/密码登录
   - 查看 Woodpecker 日志获取详细错误

2. **部署超时**
   - 增加 `script_timeout`（当前设置为 20m）
   - 检查网络连接（特别是 Tailscale）

3. **健康检查失败**
   - Java 应用首次启动需要 1-2 分钟
   - 检查日志: `docker logs <container_name>`
   - 手动验证: `curl http://localhost:<port>/`

4. **Git fetch 失败**
   - 脚本会自动降级使用本地代码
   - 检查 remote URL 配置
   - 确保 SSH 密钥已添加到 GitHub/Gitee

## 📊 监控和验证

### 部署后验证命令

**mykng**:
```bash
# 检查容器状态
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}" | grep kb-

# 检查 Gateway 健康
curl http://localhost:8090/

# 公网访问
curl https://kb.marschat.online
```

**active-manager**:
```bash
# 检查容器状态
docker ps --filter "name=activecode"

# 检查登录页
curl http://localhost:18080/activecode/login.html

# 公网访问
curl https://tools.marschat.online/activecode/login.html
```

## 🚀 未来改进

- [ ] 添加其他服务（kb-ops, infra-monitor, myfrp, portal）的流水线
- [ ] 配置自动触发（push 到 master 自动部署）
- [ ] 添加通知（钉钉/邮件/Slack）
- [ ] 配置回滚机制
- [ ] 添加集成测试步骤

## 📅 更新历史

- **2026-07-08**: 
  - 初始配置 mykng 流水线
  - 添加 active-manager 流水线
  - 迁移 SSH 私钥到 Secrets
  - 优化健康检查逻辑
  - 修复 git remote 强制修改问题
