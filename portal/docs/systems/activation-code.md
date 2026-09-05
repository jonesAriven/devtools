# 激活码服务

> 自研的软件授权（激活码）管理服务：管理员在后台生成带 RSA 签名的激活码，客户端软件内嵌验证库离线校验激活码有效性/过期/设备绑定，并记录激活流水。分为「生成/管理后台」与「客户端验证库」两部分，本篇为服务端（生成与管理）。

## 基本信息

| 项 | 值 |
|----|----|
| 分类 | Web 系统（自研） |
| 版本 | Spring Boot（Java）；镜像 `activecode:latest`（实采 `docker inspect`）；测试环境为 Spring Boot 3.4 + Java 21（portal 表 tools-test 条目） |
| 部署位置 | 生产：内网 Debian（192.168.31.182）容器 `activecode`，`0.0.0.0:18080->8080`；挂载 `/mnt/shared/www/download/QRCodeTools → /app/downloads` |
| 源码位置 | 本地 `D:\huliang\java\ideaworkspace\devtools\active-manager\activation-code-server\`（仓库 `active-manager`，与前端静态页、验证库同仓） |
| CI/CD | Woodpecker 流水线项目 `active-manager`（woodScript `deploy-active-manager.sh` 部署到内网 Debian activecode compose，健康检查 `localhost:18080/activecode/login.html`） |

## 访问入口

- 公网（生产）：`https://tools.marschat.online/activecode/login.html`（腾讯云2号 nginx `tools.marschat.online` → `http://100.105.196.63:18080`）
- 公网（测试）：`https://tools-test.marschat.online/`（portal 表里 tools-test 条目，对应 mykng 上 portal-server 8087 的激活码测试环境，Spring Boot 3.4 + Java 21）
- 内网：`http://192.168.31.182:18080/activecode/login.html`（内网 Debian 直连）
- Tailscale：`http://100.105.196.63:18080/activecode/login.html`
- 管理后台页面：`/activecode/main.html`（登录后）、`/activecode/index.html`、`/activecode/downloads.html`（二维码工具下载页）；登录页 `/activecode/login.html`

## 全链路（生产）

```
管理员浏览器
  → https://tools.marschat.online/activecode/login.html (腾讯云2号 nginx :443)
  → http://100.105.196.63:18080 (内网 Debian activecode 容器)
       /activecode/login.html 等静态页由后端 PageController 从 classpath 读取返回
       /activecode/api/*        由 Spring Boot 接口处理
```

> 测试环境链路：`tools-test.marschat.online` → mykng nginx :80 → `127.0.0.1:8087`（portal-server 容器内的激活码测试模块）。

## 核心功能与使用

服务端由 4 个 Controller 组成（前缀 `/activecode/api`）：

### AuthController（`/activecode/api/auth`）
- `POST /auth/login`、`/auth/logout`、`GET /auth/session`、`POST /auth/change-password`：管理员会话登录（基于 HttpSession，SHA-256 + 随机盐哈希口令）。
- 首次启动若 `admin` 表为空，自动创建内置默认管理员账号（初始凭据见源码初始化逻辑，**上线后务必改密，账密统一见 Vaultwarden 或 infrastructure-map 技能**）。

### ActivationController（`/activecode/api/activation`）—— 核心
- `POST /generate`：根据唯一序列号（软件初始序列号 + 机器码加密串）生成激活码（序列号 + 过期时间戳的 RSA 签名加密串）。每条生成记录落库。
- `POST /verify`：激活码在线校验接口（客户端可调用，等价于离线验证库的在线版）。
- `GET /list`：激活码记录查询（按 keyword/status 分页）。
- `GET /logs`：激活流水查询（按 recordId/serialNumber/eventType/deviceId/时间区间）。
- `GET /parse-code`、`/parse-serial`：解析激活码 / 序列号中的明文信息（调试/排查用）。
- `DELETE /{id}`、`DELETE /batch`：单条/批量（≤100）删除记录。
- `PUT /{id}/alias`：修改设备别名（便于管理识别）。
- `GET/PUT /config/default-expire`：默认有效期配置（默认 43200 分钟 = 30 天）。
- `GET/PUT /version-check`：版本校验配置（控制客户端激活时是否校验软件版本）。

### DownloadController（`/activecode/api/download`）
- `GET /list`：扫描挂载目录 `/app/downloads`（→ `/mnt/shared/www/download/QRCodeTools`）下的 `.exe`，按修改时间倒序列出可下载版本（含大小）。
- `GET /{filename}.exe`：带路径遍历防护的文件下载（二维码工具等客户端分发）。

### PageController（静态页）
- 提供 `/activecode/login.html`、`main.html`、`index.html`、`downloads.html`；未登录访问 `main.html` 自动 302 跳登录页。

## 依赖与关联

- 数据库：MySQL（业务库，连接信息见 Vaultwarden / infrastructure-map；服务内使用连接池，记录表 `activation_record`、`activation_log`、`admin_user` 等）。
- 关联系统：
  - **激活码使用页面 / 验证库（activation-verifier）**：客户端内嵌的验证 SDK，离线用 RSA 公钥验签，与服务端 `/generate` 生成的激活码一一对应。本服务是「发证机关」，对端是「验票终端」。
  - 客户端软件（如二维码工具 QRCodeTools）：拿到激活码后在本地调用验证库完成激活，相关 exe 由本服务 `/download` 分发。

## 运维要点

- 启停：内网 Debian 上 `docker compose` 管理 `activecode` 容器；升级走 Woodpecker `active-manager` 流水线（`deploy-active-manager.sh`）。
- 健康检查：`curl -s -o /dev/null -w '%{http_code}' http://192.168.31.182:18080/activecode/login.html` → `200`（2026-09-05 实采确认）。
- 日志：`docker logs activecode`；内网 Debian 侧 `obs-dozzle`（:15888）实时日志，Grafana/Loki 聚合。
- 数据与备份：激活码记录与流水在 MySQL；默认管理员账号 `admin` 在 `admin_user` 表，改密后请同步更新 Vaultwarden。
- 常见问题：
  - 默认密码风险：首次部署的默认管理员账号必须改密（账密见 Vaultwarden 或 infrastructure-map 技能），否则任何人可进入后台生成激活码。
  - 客户端激活失败：先 `/parse-code` 看激活码是否过期或设备不匹配；再核对 `/version-check` 配置是否拦截了当前客户端版本。

## 变更记录

- 2026-09-05 首次生成（portal 文档补全任务，AI 基于实采 docker ps/inspect + 本地 `activation-code-server` 源码 Controller + 腾讯云2号 nginx 配置生成）
