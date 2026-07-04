# Portal 工具看板 方案文档

> 版本：v2.0
> 日期：2026-07-04
> 状态：已上线
>
> **v2.0 变更**：新增登录认证（接入 kb-auth）、系统管理 CRUD、全局搜索、分类折叠、常用收藏、portal-server 后端服务，由纯静态导航升级为带后端管理的完整门户系统

## 1. 项目背景

mykng 知识库项目原有 kb-ops 运维模块，承担运维知识导入与分析职责。经评估，kb-ops 偏离了"知识沉淀"的原始愿景，且与 kb-intelligence 知识引擎职责重叠。决定彻底剥离 kb-ops 模块（82 个 Java 文件、13 个 Controller 全部删除），OperationLog 迁移至 kb-auth。

剥离后，devtools 工程下有多个独立系统/工具（mykng 知识库、激活码服务、Nexus 私服、FRP 仪表盘、DolphinScheduler、二维码工具、激活码验证库等），缺乏统一入口。为此新建 **Portal 工具看板**，作为所有系统/工具的总入口，提供导航跳转、状态监控、工具下载、文档链接四大能力。

## 2. 技术栈

### 2.1 前端技术栈

| 维度 | 选型 |
|------|------|
| 框架 | Vue 3.4 |
| 构建工具 | Vite 5.2 |
| UI 库 | Element Plus 2.7 |
| 图标 | @element-plus/icons-vue |
| 语言 | TypeScript 5.4 |
| 样式 | Sass |
| 状态管理 | Pinia |
| 路由 | Vue Router 4 |
| 包管理 | pnpm（走 Nexus 私服） |

### 2.2 后端技术栈（portal-server）

| 维度 | 选型 |
|------|------|
| 框架 | Spring Boot 3.4 |
| JDK | Java 21 |
| ORM | MyBatis-Plus 3.5.6 |
| 数据库 | MySQL 8.0 |
| 工具库 | Hutool 5.8.27 |
| 构建 | Maven |
| 端口 | 8087 |
| 上下文路径 | /portal |

## 3. 功能设计

### 3.1 v1.0 四大核心能力

1. **系统导航跳转**：卡片式展示所有系统，一键跳转到对应系统
2. **状态监控**：实时检测各系统在线状态与响应延迟（HEAD 请求 + 5s 超时）
3. **工具下载入口**：工具类软件提供下载按钮（下载路径配置化）
4. **项目文档链接**：每个系统可关联多个文档链接

### 3.2 v2.0 新增功能

#### 3.2.1 登录认证（接入 kb-auth）

- 接入 mykng 知识库统一认证体系（kb-auth）
- 登录页面：用户名 + 密码登录，支持记住登录状态
- JWT Token 存储在 localStorage，请求时自动携带 Authorization header
- 路由守卫：未登录自动跳转登录页，已登录访问登录页自动跳转首页
- 用户信息：右上角显示当前登录用户名，支持退出登录

**认证流程**：
```
用户输入账号密码 → Portal 前端调用 /portal/api/auth/login → kb-auth 验证 → 返回 JWT Token → 存储 Token → 跳转首页
```

#### 3.2.2 系统管理 CRUD

- **系统列表**：分页展示所有系统，支持按名称搜索、按分类筛选
- **新增系统**：表单录入系统信息（名称、描述、分类、URL、健康检查地址、技术栈、图标、颜色、下载路径等）
- **编辑系统**：修改已有系统配置
- **删除系统**：逻辑删除，支持二次确认
- 管理入口：Header 右上角"管理"按钮，仅登录用户可访问

#### 3.2.3 全局搜索

- 顶部 Header 中央搜索框，实时搜索系统名称和描述
- 搜索结果实时过滤卡片展示
- 支持清空搜索，一键恢复全部系统
- 搜索事件通过 CustomEvent 跨组件通信（`portal-search`）

#### 3.2.4 分类折叠

- 按 4 个分类（Web系统/基础设施/工具软件/项目文档）分组展示
- 每个分类标题可点击展开/折叠
- 折叠状态持久化到 localStorage（`portal_collapsed_categories`）
- 左侧侧边栏同步显示分类列表，点击可快速定位到对应分类

#### 3.2.5 常用收藏

- 每个系统卡片支持收藏/取消收藏（星形图标）
- 收藏的系统在顶部"我的收藏"区域优先展示
- 收藏数据持久化到 localStorage（`portal_favorites`）
- 左侧侧边栏"快捷导航"支持快速跳转到收藏区域

#### 3.2.6 portal-server 后端服务

- 基于 Spring Boot 3.4 + MyBatis-Plus 构建
- 提供系统管理 RESTful API：
  - `GET /system/list` - 分页查询系统列表（支持关键词、分类、状态筛选）
  - `GET /system/category/{category}` - 按分类查询系统列表
  - `GET /system/{id}` - 根据ID查询系统详情
  - `POST /system` - 新增系统
  - `PUT /system/{id}` - 更新系统
  - `DELETE /system/{id}` - 删除系统
- 逻辑删除支持，自动填充创建/更新时间
- Actuator 健康检查端点：`/portal/actuator/health`

### 3.3 页面结构（v2.0）

- **顶部 Header**：渐变标题栏 + 全局搜索框 + 管理按钮 + 用户信息下拉菜单
- **左侧侧边栏**：快捷导航（我的收藏） + 系统分类列表（带数量徽章）
- **统计概览栏**：总系统数 / 在线 / 离线 / 未知 四个统计卡片 + 刷新状态按钮
- **我的收藏区**：收藏的系统卡片优先展示（有收藏时显示）
- **分类卡片区**：按分类分组展示，支持展开/折叠
- **管理页面**：系统列表表格 + 搜索筛选 + 新增/编辑/删除操作

### 3.4 健康检查机制

- 客户端 `fetch` HEAD 请求，`mode: 'no-cors'`（避免 CORS 限制）
- 5 秒超时，测量响应延迟
- `Promise.allSettled` 批量并发检查
- 状态：online（绿色）/ offline（红色）/ checking（黄色脉冲）/ unknown（灰色）
- 在线状态显示延迟毫秒数

## 4. 系统配置清单（11 个系统）

### 4.1 Web 系统（2 个）

| ID | 名称 | URL | 健康检查 | 技术栈 |
|----|------|-----|----------|--------|
| mykng | mykng 知识库 | https://kb.marschat.online/kb/ | /kb/api/auth/actuator/health | Spring Boot 3.2 + Vue3 + MySQL + MongoDB + MinIO + MeiliSearch |
| activation-code | 激活码服务 | https://tools-test.marschat.online | /actuator/health | Spring Boot 3.4 + Java 21 + MyBatis-Plus + MySQL |

### 4.2 基础设施（3 个）

| ID | 名称 | URL | 健康检查 | 技术栈 |
|----|------|-----|----------|--------|
| nexus | Nexus 私服 | https://nexus.marschat.online | /service/rest/v1/status | Nexus Repository Manager 3 |
| frp-dashboard | FRP 仪表盘 | http://120.26.66.182:7500 | http://120.26.66.182:7500 | FRP + Spring Boot + Vue2 |
| dolphin | DolphinScheduler | https://tools.marschat.online/dolphin/ | https://tools.marschat.online/dolphin/ | Apache DolphinScheduler 3.x |

### 4.3 工具软件（4 个）

| ID | 名称 | 下载路径 | 技术栈 |
|----|------|----------|--------|
| qrcode-tool-csharp | QRCodeTool (C#) | /portal/downloads/QRCodeTool.zip | C# .NET 6 WinForms |
| activation-verifier | 激活码验证库 | /portal/downloads/Jones.Activation.dll | C# .NET 6 类库 (Jones.Activation.dll) |
| qr-generator-rust | QR Generator (Rust) | /portal/downloads/qr-rust.zip | Rust + egui |
| git-auto | Git 自动化工具 | /portal/downloads/git-auto.zip | Python + Bat |

### 4.4 项目文档（2 个）

| ID | 名称 | URL | 说明 |
|----|------|-----|------|
| devtools-docs | 项目文档中心 | https://kb.marschat.online/kb/ | devtools 项目文档：架构设计、部署手册、API规范 |
| openclaw-docs | OpenClaw 知识库 | https://kb.marschat.online/kb/ | 龙虾 OpenClaw 体系文档：主机清单、凭据汇总、运维方案 |

## 5. 部署方案

### 5.1 前端构建产物

- 构建命令：`npx vite build`（绕过 pnpm v11 的 `ERR_PNPM_IGNORED_BUILDS` 限制）
- 产物目录：`dist/`
  - `index.html`（486 字节）
  - `assets/index-*.js`（1.16 MB）
  - `assets/index-*.css`（361 KB）
  - `favicon.svg`
- Vite `base: '/portal/'`（子路径部署）

### 5.2 后端部署（portal-server）

**构建命令**：
```bash
cd portal/portal-server
mvn clean package -DskipTests
```

**产物**：`target/portal-server.jar`

**运行方式**：
```bash
java -jar portal-server.jar --spring.profiles.active=prod
```

**数据库初始化**：执行 `portal/portal-server/src/main/resources/sql/portal_init.sql`

**核心数据表**：
- `portal_system` - 系统配置表（存储所有系统/工具的配置信息）

### 5.3 部署架构（双层 Nginx + 后端服务）

```
用户浏览器
    ↓ HTTPS
腾讯云2号 Nginx (main.marschat.online:443)
    ↓ proxy_pass http://100.93.36.113:80/portal/
mykng-debain Nginx (100.93.36.113:80)
    ├─ 静态资源 → alias /data/portal/
    └─ API 请求 → proxy_pass http://127.0.0.1:8087/portal/
                    ↓
              portal-server (Spring Boot :8087)
                    ↓
              MySQL (portal_system 表)
```

### 5.4 Nginx 配置更新（v2.0）

**mykng-debain Nginx 配置**（`/etc/nginx/conf.d/kb.conf`）：
```nginx
# Portal 前端静态资源
location /portal/ {
    alias /data/portal/;
    index index.html;
    try_files $uri $uri/ /portal/index.html;
    access_log off;
}

# Portal 后端 API
location /portal/api/ {
    proxy_pass http://127.0.0.1:8087/portal/;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_connect_timeout 5s;
    proxy_read_timeout 60s;
}
```

**腾讯云2号 Nginx 配置**（`/etc/nginx/sites-enabled/main.marschat.online`）：
```nginx
server {
    listen 443 ssl;
    server_name main.marschat.online;
    location /portal/ {
        proxy_pass http://100.93.36.113:80/portal/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_connect_timeout 5s;
        proxy_read_timeout 60s;
    }
}
```

### 5.5 部署步骤（v2.0）

#### 前端部署
1. **本机构建**：`cd portal && npx vite build`
2. **上传产物**：`python scripts/sftp_upload.py dist /data/portal`（SFTP 到 mykng-debain 192.168.31.105）

#### 后端部署
1. **构建 Jar**：`cd portal/portal-server && mvn clean package -DskipTests`
2. **上传 Jar**：SFTP 上传 `portal-server.jar` 到 `/data/portal-server/`
3. **初始化数据库**：执行 `portal_init.sql`
4. **启动服务**：`nohup java -jar /data/portal-server/portal-server.jar --spring.profiles.active=prod > /data/portal-server/logs/portal.log 2>&1 &`

#### Nginx 配置
5. **更新 mykng-debain Nginx**：添加 `/portal/api/` 反向代理配置
6. **重载 Nginx**：`nginx -t && nginx -s reload`（两台机器分别执行）

#### 验证
7. **前端验证**：`curl -s -o /dev/null -w "%{http_code}" https://main.marschat.online/portal/` 应返回 200
8. **后端验证**：`curl -s -o /dev/null -w "%{http_code}" https://main.marschat.online/portal/api/actuator/health` 应返回 200

### 5.6 辅助脚本

| 脚本 | 用途 |
|------|------|
| `scripts/ssh_exec.py` | SSH 到 mykng-debain 执行远程命令 |
| `scripts/sftp_upload.py` | SFTP 上传目录到 mykng-debain |
| `scripts/configure_nginx.py` | 配置 mykng-debain 的 Nginx /portal/ location |
| `scripts/ssh_tx2.py` | SSH 到腾讯云2号执行远程命令 |
| `scripts/add_portal_to_tx2.py` | 配置腾讯云2号 Nginx /portal/ 转发 |

## 6. 访问地址

### 6.1 前端访问地址

| 访问方式 | 地址 | 说明 |
|---------|------|------|
| 生产地址 | https://main.marschat.online/portal/ | 公网访问，经腾讯云2号 Nginx 转发 |
| 内网直连 | http://192.168.31.105/portal/ | 局域网直接访问 mykng-debain |
| Tailscale | http://100.93.36.113/portal/ | Tailscale 网络内访问 |

### 6.2 后端 API 地址

| 访问方式 | 地址 | 说明 |
|---------|------|------|
| 生产地址 | https://main.marschat.online/portal/api/ | 公网 API 入口 |
| 内网直连 | http://192.168.31.105:8087/portal/ | 直接访问 portal-server |
| 健康检查 | https://main.marschat.online/portal/api/actuator/health | 后端健康状态检查 |

### 6.3 登录账号

与 mykng 知识库共用同一账号体系（kb-auth），使用知识库账号登录即可。

## 7. 维护说明

### 7.1 新增系统/工具（v2.0 推荐方式）

登录后进入"管理"页面，点击"新增系统"按钮，填写系统信息后保存即可。无需重新构建前端。

### 7.2 新增系统/工具（v1.0 静态配置方式，保留兼容）

在 `src/config/systems.ts` 的 `systems` 数组中新增配置项，重新 `npx vite build` 并上传 `dist/` 即可。

### 7.3 更新依赖

```bash
cd portal
pnpm install
npx vite build
```

### 7.4 下载文件管理

工具软件下载文件需放置在 `/data/portal/downloads/` 目录（对应配置中的 `downloadPath`）。

### 7.5 后端服务管理

```bash
# 启动服务
nohup java -jar /data/portal-server/portal-server.jar --spring.profiles.active=prod > /data/portal-server/logs/portal.log 2>&1 &

# 查看日志
tail -f /data/portal-server/logs/portal.log

# 停止服务
ps aux | grep portal-server | grep -v grep | awk '{print $2}' | xargs kill
```

### 7.6 数据备份

定期备份 `portal_system` 表：
```bash
mysqldump -u root -p kb_portal portal_system > portal_system_backup.sql
```
