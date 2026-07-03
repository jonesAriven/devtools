# Portal 工具看板 方案文档

> 版本：v1.0
> 日期：2026-07-03
> 状态：已上线

## 1. 项目背景

mykng 知识库项目原有 kb-ops 运维模块，承担运维知识导入与分析职责。经评估，kb-ops 偏离了"知识沉淀"的原始愿景，且与 kb-intelligence 知识引擎职责重叠。决定彻底剥离 kb-ops 模块（82 个 Java 文件、13 个 Controller 全部删除），OperationLog 迁移至 kb-auth。

剥离后，devtools 工程下有多个独立系统/工具（mykng 知识库、激活码服务、Nexus 私服、FRP 仪表盘、DolphinScheduler、二维码工具、激活码验证库等），缺乏统一入口。为此新建 **Portal 工具看板**，作为所有系统/工具的总入口，提供导航跳转、状态监控、工具下载、文档链接四大能力。

## 2. 技术栈

| 维度 | 选型 |
|------|------|
| 框架 | Vue 3.4 |
| 构建工具 | Vite 5.2 |
| UI 库 | Element Plus 2.7 |
| 图标 | @element-plus/icons-vue |
| 语言 | TypeScript 5.4 |
| 样式 | Sass |
| 包管理 | pnpm（走 Nexus 私服） |

## 3. 功能设计

### 3.1 四大核心能力

1. **系统导航跳转**：卡片式展示所有系统，一键跳转到对应系统
2. **状态监控**：实时检测各系统在线状态与响应延迟（HEAD 请求 + 5s 超时）
3. **工具下载入口**：工具类软件提供下载按钮（下载路径配置化）
4. **项目文档链接**：每个系统可关联多个文档链接

### 3.2 页面结构

- **顶部 Header**：渐变标题栏 + 副标题
- **统计概览栏**：总系统数 / 在线 / 离线 / 未知 四个统计卡片
- **分类卡片区**：按 4 个分类（Web系统/工具软件/基础设施/项目文档）分组展示
- **卡片内容**：图标 + 标题 + 状态徽章 + 描述 + 技术栈标签 + 操作按钮（访问/下载/文档）
- **Footer**：版权信息

### 3.3 健康检查机制

- 客户端 `fetch` HEAD 请求，`mode: 'no-cors'`（避免 CORS 限制）
- 5 秒超时，测量响应延迟
- `Promise.allSettled` 批量并发检查
- 状态：online（绿色）/ offline（红色）/ checking（黄色脉冲）/ unknown（灰色）
- 在线状态显示延迟毫秒数

## 4. 系统配置清单（11 个系统）

### 4.1 Web 系统（2 个）

| ID | 名称 | URL | 健康检查 | 技术栈 |
|----|------|-----|----------|--------|
| mykng | mykng 知识库 | https://tools.marschat.online/kb/ | /kb/api/auth/actuator/health | Spring Boot 3.2 + Vue3 + MySQL + MongoDB + MinIO + MeiliSearch |
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
| devtools-docs | 项目文档中心 | https://tools.marschat.online/kb/ | devtools 项目文档：架构设计、部署手册、API规范 |
| openclaw-docs | OpenClaw 知识库 | https://tools.marschat.online/kb/ | 龙虾 OpenClaw 体系文档：主机清单、凭据汇总、运维方案 |

## 5. 部署方案

### 5.1 构建产物

- 构建命令：`npx vite build`（绕过 pnpm v11 的 `ERR_PNPM_IGNORED_BUILDS` 限制）
- 产物目录：`dist/`
  - `index.html`（486 字节）
  - `assets/index-*.js`（1.16 MB）
  - `assets/index-*.css`（361 KB）
  - `favicon.svg`
- Vite `base: '/portal/'`（子路径部署）

### 5.2 部署架构（双层 Nginx）

```
用户浏览器
    ↓ HTTPS
腾讯云2号 Nginx (tools.marschat.online:443)
    ↓ proxy_pass http://100.93.36.113:80/portal/
mykng-debain Nginx (100.93.36.113:80)
    ↓ alias /data/portal/
静态文件 /data/portal/index.html + assets/
```

### 5.3 部署步骤

1. **本机构建**：`cd portal && npx vite build`
2. **上传产物**：`python scripts/sftp_upload.py dist /data/portal`（SFTP 到 mykng-debain 192.168.31.105）
3. **mykng-debain Nginx 配置**（`/etc/nginx/conf.d/kb.conf`）：
   ```nginx
   location /portal/ {
       alias /data/portal/;
       index index.html;
       try_files $uri $uri/ /portal/index.html;
       access_log off;
   }
   ```
4. **腾讯云2号 Nginx 配置**（`/etc/nginx/sites-enabled/tools`）：
   ```nginx
   location /portal/ {
       proxy_pass http://100.93.36.113:80/portal/;
       proxy_set_header Host $host;
       proxy_set_header X-Real-IP $remote_addr;
       proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
       proxy_set_header X-Forwarded-Proto $scheme;
       proxy_connect_timeout 5s;
       proxy_read_timeout 60s;
   }
   ```
5. **重载 Nginx**：`nginx -t && nginx -s reload`（两台机器分别执行）
6. **验证**：`curl -s -o /dev/null -w "%{http_code}" https://tools.marschat.online/portal/` 应返回 200

### 5.4 辅助脚本

| 脚本 | 用途 |
|------|------|
| `scripts/ssh_exec.py` | SSH 到 mykng-debain 执行远程命令 |
| `scripts/sftp_upload.py` | SFTP 上传目录到 mykng-debain |
| `scripts/configure_nginx.py` | 配置 mykng-debain 的 Nginx /portal/ location |
| `scripts/ssh_tx2.py` | SSH 到腾讯云2号执行远程命令 |
| `scripts/add_portal_to_tx2.py` | 配置腾讯云2号 Nginx /portal/ 转发 |

## 6. 访问地址

- **生产地址**：https://tools.marschat.online/portal/
- **内网直连**：http://192.168.31.105/portal/
- **Tailscale**：http://100.93.36.113/portal/

## 7. 维护说明

### 新增系统/工具
在 `src/config/systems.ts` 的 `systems` 数组中新增配置项，重新 `npx vite build` 并上传 `dist/` 即可。

### 更新依赖
```bash
cd portal
pnpm install
npx vite build
```

### 下载文件管理
工具软件下载文件需放置在 `/data/portal/downloads/` 目录（对应配置中的 `downloadPath`）。
