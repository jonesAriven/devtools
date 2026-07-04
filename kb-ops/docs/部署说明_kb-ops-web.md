# kb-ops-web 前端部署说明

> 版本：v1.0
> 日期：2026-07-04
> 状态：已上线

## 1. 项目概述

kb-ops-web 是 kb-ops 运维管控平台的前端项目，基于 Vue 3 + Vite + Element Plus + TypeScript 构建，提供 14 个完整 CRUD 页面，覆盖运维资源全生命周期管理。

### 1.1 技术栈

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
| HTTP 请求 | Axios |
| 包管理 | pnpm（走 Nexus 私服） |

### 1.2 页面清单（14 个页面）

| 页面 | 路由 | 说明 |
|------|------|------|
| 登录页 | /login | 用户登录认证 |
| 仪表盘 | /dashboard | 运维概览看板 |
| 主机管理 | /hosts | 主机资产 CRUD |
| 服务管理 | /services | 服务信息 CRUD |
| 端口管理 | /ports | 端口扫描结果 CRUD |
| 凭据管理 | /credentials | 账号密码加密存储 CRUD |
| 域名管理 | /domains | 域名资产 CRUD |
| 依赖管理 | /dependencies | 服务依赖关系 CRUD |
| 部署记录 | /deployments | 部署历史记录 CRUD |
| 矛盾检测 | /conflicts | 配置冲突检测结果 |
| 运维知识 | /knowledge | 运维知识库 CRUD |
| 知识导入 | /import | 从 kb-intelligence 同步知识 |
| 操作日志 | /logs | 操作审计日志 |
| 404 页面 | /:pathMatch(.*)* | 页面不存在提示 |

---

## 2. 项目结构

```
kb-ops/
├── kb-ops-web/              # 前端项目
│   ├── src/
│   │   ├── api/             # API 接口（13 个模块）
│   │   ├── layouts/         # 布局组件
│   │   ├── router/          # 路由配置
│   │   ├── stores/          # Pinia 状态管理
│   │   ├── styles/          # 全局样式
│   │   ├── types/           # TypeScript 类型定义
│   │   ├── utils/           # 工具函数
│   │   ├── views/           # 页面组件（14 个页面）
│   │   ├── App.vue
│   │   ├── main.ts
│   │   └── config.ts        # 配置文件
│   ├── .env.development     # 开发环境变量
│   ├── .env.production      # 生产环境变量
│   ├── vite.config.ts       # Vite 配置
│   └── package.json
├── src/                     # 后端 Java 项目
├── deploy_web.py            # 前端部署脚本
└── docs/
    └── 部署说明_kb-ops-web.md  # 本文档
```

---

## 3. 本地开发

### 3.1 环境要求

- Node.js >= 18
- pnpm >= 8
- 后端 kb-ops 服务运行中（默认端口 8084）

### 3.2 安装依赖

```bash
cd kb-ops/kb-ops-web
pnpm install
```

### 3.3 启动开发服务器

```bash
pnpm dev
```

默认访问地址：http://localhost:5173/kb-ops/

### 3.4 环境变量配置

**.env.development**：
```
VITE_API_BASE_URL=/kb-ops-api
VITE_CONTEXT_PATH=/kb-ops
```

**.env.production**：
```
VITE_API_BASE_URL=/kb-ops/api
VITE_CONTEXT_PATH=/kb-ops
```

---

## 4. 生产构建

### 4.1 构建命令

```bash
cd kb-ops/kb-ops-web
pnpm build
```

或使用 npx 绕过 pnpm 限制：
```bash
npx vite build
```

### 4.2 构建产物

构建产物输出到 `dist/` 目录：
- `index.html` - 入口 HTML
- `assets/index-*.js` - JS 包
- `assets/index-*.css` - CSS 包
- `favicon.svg` - 图标

### 4.3 Vite 配置要点

- `base: '/kb-ops/'` - 子路径部署
- 自动导入 Element Plus 组件和图标
- 生产环境去除 console 和 debugger

---

## 5. 部署方案

### 5.1 部署架构（双层 Nginx）

```
用户浏览器
    ↓ HTTPS
腾讯云2号 Nginx (main.marschat.online:443)
    ↓ proxy_pass http://100.93.36.113:80/kb-ops/
mykng-debain Nginx (100.93.36.113:80)
    ├─ 静态资源 → alias /var/www/kb-ops-web/
    └─ API 请求 → proxy_pass http://127.0.0.1:8084/kb-ops/
                    ↓
              kb-ops 后端 (Spring Boot :8084)
```

### 5.2 mykng-debain Nginx 配置

配置文件：`/etc/nginx/conf.d/kb.conf`

```nginx
# kb-ops 前端静态资源
location /kb-ops/ {
    alias /var/www/kb-ops-web/;
    index index.html;
    try_files $uri $uri/ /kb-ops/index.html;
    access_log off;
}

# kb-ops 后端 API
location /kb-ops/api/ {
    proxy_pass http://127.0.0.1:8084/kb-ops/;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_connect_timeout 5s;
    proxy_read_timeout 300s;
}
```

### 5.3 腾讯云2号 Nginx 配置

配置文件：`/etc/nginx/sites-enabled/main.marschat.online`

```nginx
server {
    listen 443 ssl;
    server_name main.marschat.online;

    location /kb-ops/ {
        proxy_pass http://100.93.36.113:80/kb-ops/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_connect_timeout 5s;
        proxy_read_timeout 300s;
    }
}
```

---

## 6. 部署步骤

### 方式一：使用部署脚本（推荐）

项目根目录提供 `deploy_web.py` 自动化部署脚本：

```bash
cd kb-ops
python deploy_web.py
```

脚本自动完成：
1. 本地构建前端
2. SFTP 上传到 mykng-debain
3. 备份旧版本
4. 部署新版本
5. 更新 Nginx 配置（可选）
6. 验证部署结果

### 方式二：手动部署

#### 步骤 1：本机构建

```bash
cd kb-ops/kb-ops-web
npx vite build
```

#### 步骤 2：上传产物

使用 SFTP 上传 `dist/` 目录到 mykng-debain：

```bash
# 示例：使用 scp
scp -r dist/* root@192.168.31.105:/var/www/kb-ops-web/
```

#### 步骤 3：配置 Nginx

在 mykng-debain 上更新 Nginx 配置（参见 5.2 节），然后重载：

```bash
nginx -t && nginx -s reload
```

#### 步骤 4：配置腾讯云2号 Nginx

在腾讯云2号上更新 Nginx 配置（参见 5.3 节），然后重载：

```bash
nginx -t && nginx -s reload
```

#### 步骤 5：验证部署

```bash
# 验证前端页面
curl -s -o /dev/null -w "%{http_code}" https://main.marschat.online/kb-ops/

# 验证后端 API
curl -s -o /dev/null -w "%{http_code}" https://main.marschat.online/kb-ops/api/actuator/health
```

均应返回 200。

---

## 7. 访问地址

### 7.1 前端访问地址

| 访问方式 | 地址 | 说明 |
|---------|------|------|
| 生产地址 | https://main.marschat.online/kb-ops/ | 公网访问 |
| 内网直连 | http://192.168.31.105/kb-ops/ | 局域网直接访问 |
| Tailscale | http://100.93.36.113/kb-ops/ | Tailscale 网络内访问 |

### 7.2 后端 API 地址

| 访问方式 | 地址 | 说明 |
|---------|------|------|
| 生产地址 | https://main.marschat.online/kb-ops/api/ | 公网 API 入口 |
| 内网直连 | http://192.168.31.105:8084/kb-ops/ | 直接访问后端服务 |
| 健康检查 | https://main.marschat.online/kb-ops/api/actuator/health | 后端健康状态 |

### 7.3 默认账号

使用 kb-auth 统一认证体系，与知识库共用账号。

---

## 8. 运维维护

### 8.1 版本回滚

部署脚本会自动备份上一版本到 `/var/www/kb-ops-web.bak/`，如需回滚：

```bash
# SSH 到 mykng-debain
rm -rf /var/www/kb-ops-web
mv /var/www/kb-ops-web.bak /var/www/kb-ops-web
```

### 8.2 缓存清理

Nginx 静态资源缓存清理：
```bash
# 强制刷新浏览器缓存（前端构建已做 hash 处理，一般无需手动清理）
# 如需清理 Nginx 缓存：
rm -rf /var/cache/nginx/*
nginx -s reload
```

### 8.3 日志查看

```bash
# Nginx 访问日志
tail -f /var/log/nginx/access.log

# Nginx 错误日志
tail -f /var/log/nginx/error.log

# 后端日志
tail -f /data/kb-ops/logs/ops.log
```

---

## 9. 常见问题

### Q1: 页面 404 刷新后白屏

**原因**：SPA 单页应用路由模式下，Nginx 未配置 fallback。

**解决**：确保 Nginx 配置中有 `try_files $uri $uri/ /kb-ops/index.html;`。

### Q2: API 请求 401 未授权

**原因**：未登录或 Token 过期。

**解决**：重新登录获取新 Token。

### Q3: 跨域问题

**原因**：前后端域名/端口不一致。

**解决**：生产环境通过 Nginx 反向代理解决，开发环境在 `vite.config.ts` 中配置 proxy。

### Q4: 静态资源 404

**原因**：Vite base 路径配置错误。

**解决**：确认 `vite.config.ts` 中 `base` 设置为 `/kb-ops/`。
