# Vaultwarden 密码管理

> 全基础设施账密「唯一真相源」：
> Bitwarden 兼容的自托管密码库（Rust 实现），
> 集中保管所有主机、容器、服务与第三方 API 的账号密码/密钥。

## 基本信息

| 项 | 值 |
|----|----|
| 分类 | 基础设施 / 安全（密码管理） |
| 版本 | Vaultwarden **1.36.0**（镜像 label 实采 `org.opencontainers.image.version: 1.36.0`，2026-05-03 构建） |
| 部署位置 | mykng（192.168.31.105）单容器 `vaultwarden`（镜像 `vaultwarden/server:latest`） |
| 部署位置 | 端口 8222 → 80，`restart: always` |
| 源码位置 | 开源 dani-garcia/vaultwarden（Rust 实现 Bitwarden 服务端） |
| 源码位置 | 直接拉官方镜像，本地无构建仓库 |
| CI/CD | 无（`docker run` 单容器自部署，未发现 compose 编排） |

## 访问入口

- 公网：`https://vault.marschat.online`（腾讯云2号 nginx 终止 TLS 443）
- 内网：`http://192.168.31.105:8222`（mykng 宿主）
- Tailscale：`http://100.93.36.113:8222`

## 全链路

```
浏览器 → 腾讯云2号 nginx (:443, 域名 vault.marschat.online)
       → http://100.93.36.113:8222  (mykng vaultwarden 容器)
```

（mykng 本机 nginx 另有 `/vault/ → 127.0.0.1:8222` 的 path 反代。）

## 系统设计

### 组件架构（官方能力要点）

Vaultwarden 是 Bitwarden 服务端兼容实现（Rust，AGPL-3.0）：

- 提供密码库端到端加密存储：登录/密码、安全笔记、API Key、卡片、身份。
- 支持 Bitwarden 全家桶客户端（浏览器扩展、桌面/移动 App、CLI）通过自托管服务器同步。
- 轻量到可跑在树莓派，数据落 SQLite 单文件。

### 我们的集成设计

- **实例角色**
  - mykng 上单容器常驻。
  - 是**全基础设施的唯一账密真相源**——主机 SSH、数据库、各服务后台、云厂商、LLM API Key 等凭据统一存放。
- **与哪些系统连接**
  - 上游：腾讯云2号 nginx（vault.marschat.online 唯一公网入口）+ mykng 本机 nginx `/vault/` 路径反代。
  - 下游（消费方）：几乎所有系统——mykng/Deb/腾讯云/阿里云主机、MySQL/Redis/Mongo/MinIO/Nacos/Woodpecker、FRP dashboard、各类 API Key。
  - 与 `infrastructure-map` 技能互为「运行时凭证」与「架构事实」两大知识源。
- **为什么选它**
  - Bitwarden 生态客户端齐全（多端同步 + 浏览器扩展自动填充）。
  - Vaultwarden 资源占用极低、单文件存储易备份。
- **关键配置思路**（`docker inspect` 环境变量名实采，值不落盘）：

| 环境变量 | 用途 |
|----------|------|
| `DOMAIN` | 服务对外地址（vault.marschat.online） |
| `SIGNUPS_ALLOWED` | 公开注册开关（生产应关闭） |
| `INVITATIONS_ALLOWED` | 邀请注册开关 |
| `ADMIN_TOKEN` | 管理后台令牌 |
| `WEBSOCKET_ENABLED` | 客户端实时同步 |
| `ROCKET_ADDRESS` / `ROCKET_PORT` / `ROCKET_PROFILE` | 内置 HTTP 服务监听 |
| `IP_HEADER` | 反代下真实 IP 取头 |
| `SHOW_PASSWORD_HINT` | 密码提示行为 |

  - 凭证值统一见 Vaultwarden 本身或 infrastructure-map 技能。

### 数据模型概览

- 主库 `db.sqlite3`（用户/加密条目/组织/收藏等 Bitwarden 全量表）。
- 数据卷：`/root/vaultwarden-data → /data`。
- 可选 `attachments/` 附件目录（当前近乎空，未实际使用附件功能）。
- 可选 SMTP（邮件/邀请），未启用。

## 部署与发布

- 编排与位置
  - `docker run` 单容器（inspect 无 compose labels）。
  - 数据卷宿主路径：`/root/vaultwarden-data` → 容器 `/data`。
- 配置清单
  - 端口映射：宿主 `8222` → 容器 `80`。
  - 卷挂载：`/root/vaultwarden-data → /data`（含 db.sqlite3、attachments/）。
  - 环境变量：见「系统设计」节变量名清单（值不落盘）。
  - 重启策略：`always`。
- 发布/升级（单容器镜像升级，实际步骤）
  1. 备份数据：`cp -a /root/vaultwarden-data /root/vaultwarden-data.bak.<date>`。
  2. `docker pull vaultwarden/server:latest`。
  3. `docker stop vaultwarden && docker rm vaultwarden`。
  4. 按原参数 `docker run -d --name vaultwarden --restart always -p 8222:80 -v /root/vaultwarden-data:/data <env...> vaultwarden/server:latest`（env 清单见上，值从 Vaultwarden/记录取）。
  5. 验证：`https://vault.marschat.online` 登录 + 客户端同步。
  - 升级未实采执行过，步骤按官方单容器惯例整理 (待确认)。
- 回滚
  - 数据卷备份目录换回 + 用旧镜像 tag 重建容器。
  - SQLite 单文件整体恢复，无增量问题。

## 核心功能与使用

### 功能清单

- 密码库（Bitwarden 兼容）：登录/密码、安全笔记、API Key、卡片等条目的加密存储；文件夹/收藏组织。
- 多端客户端：浏览器扩展、桌面/移动 App、CLI 均可通过 `vault.marschat.online` 同步。
- 作为「真相源」的用途：
  - 全基础设施账密统一存放于此。
  - 全组文档约定：**任何文档不得出现明文密码/token/密钥**，一律写「账密见 Vaultwarden（vault.marschat.online）或 infrastructure-map 技能」。
- 典型场景：新成员/新服务开通时取对应凭据；轮换密码后回写此处。

### 典型操作路径

1. **查某服务凭据**
   - 浏览器打开 `https://vault.marschat.online`（或 Bitwarden 客户端登录自托管服务器）。
   - 搜索服务名/主机名 → 复制密码/查看安全笔记。
2. **新增一批凭据**
   - 登录 Web 界面 → 新建条目（类型选登录/安全笔记/API Key）。
   - 归入对应文件夹 → 保存，客户端自动同步。
3. **轮换密码**
   - 改目标系统密码 → 回 Vaultwarden 更新该条目 → 备注轮换日期。

## 依赖与关联

- 依赖
  - SQLite 主库 `db.sqlite3`（数据卷）。
  - mykng 容器运行时；可选 SMTP 未启用。
- 被依赖/关联系统
  - **几乎所有**其他系统的凭证都存放在此
    （mykng/Deb/腾讯云/阿里云主机、MySQL/Redis/Mongo/MinIO/Nacos/Woodpecker、FRP dashboard、各类 API Key 等）。

## 运维要点

- 启停方式
  - `docker start|stop|restart vaultwarden`（单容器，无 compose）。
- 日志查看
  - `docker logs vaultwarden`。
  - Grafana/Loki（Deb 侧）可纳入日志归集。
- 数据与备份
  - 备份仓库：`D:\huliang\java\ideaworkspace\vaultwarden-backup\`。
  - 备份内容（实采 2026-09-05），每日 **02:00** 左右生成一组：
    - `db_<YYYYMMDD>_*.sqlite3`（约 278KB，主数据库）
    - `config_<YYYYMMDD>_*.json`（约 1.9KB，服务配置）
    - `attachments_<YYYYMMDD>_*.tar.gz`（附件归档，当前约 45B，近乎空）
  - 连续性：2026-08-29 至 2026-09-05 每日一份稳定运行。
  - 备份任务位置未实采（可能在 mykng cron 或独立脚本拉取/导出）(待确认)。
  - 建议确认异地/长寿保留策略。
- 安全要点
  - **最高敏感系统**：禁止任何明文凭据外泄（含提交仓库、写入文档）。
  - 建议开启 admin 令牌、失败锁定、定期改密。
  - 备份文件同样含敏感数据，需受控访问。
- 常见问题
  - 容器非默认 80 而是宿主 8222，nginx 反代注意 `/vault/` 路径与根域名两种入口的转发一致性。
  - 备份 attachments 长期 45B：未使用附件功能，属正常。
  - 升级后客户端连不上：
    - 先核对 `DOMAIN` 环境变量与 nginx 反代是否一致。
    - 再看 websocket 是否需要开启。

## 变更记录

- 2026-09-05 v2 补全设计/部署/使用三维度
  - 实采版本 1.36.0（镜像 label）、数据卷 /root/vaultwarden-data。
  - docker run 部署形态与全部环境变量名，补单容器升级/回滚步骤与典型操作路径。
- 2026-09-05 v1 首次生成（明确全基础设施账密真相源定位与每日备份现状）
