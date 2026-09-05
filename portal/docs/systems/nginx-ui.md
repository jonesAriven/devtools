# Nginx UI

> 腾讯云2号主机上公网 nginx 入口的可视化管理面板
> （开源 Nginx UI v2.4.1，Go 实现），
> 提供站点/反向代理/证书的可视化配置与热重载，
> 降低手工改 nginx 配置出错风险。

## 基本信息

| 项 | 值 |
|----|----|
| 分类 | 工具软件 / 运维（nginx 管理） |
| 版本 | `nginx-ui 2.4.1 2(526) 0540f3ec (go1.26.4 linux/amd64)`（`--version` 实采，"Yet another Nginx Web UI"） |
| 部署位置 | 腾讯云2号主机（1.117.70.30 / Tailscale 100.110.114.16）**宿主 systemd 服务** `nginx-ui.service`，非 Docker |
| 部署位置 | 二进制 `/usr/local/bin/nginx-ui --config /etc/nginx-ui/app.ini`；监听 19900 |
| 源码位置 | 开源 Nginx UI（Go 实现的 nginx Web 管理面板）；宿主以官方二进制部署 |
| CI/CD | 无（宿主自部署 systemd 服务，二进制替换升级） |

## 访问入口

- 公网：`https://nginxui.marschat.online`（腾讯云2号 nginx 终止 TLS 443）
- 内网：`http://1.117.70.30:19900`（腾讯云2号宿主）
- Tailscale：`http://100.110.114.16:19900`

## 全链路

```
浏览器 → 腾讯云2号 nginx (:443, 域名 nginxui.marschat.online)
       → 127.0.0.1:19900  (本机 nginx-ui 服务)
```

## 系统设计

### 组件架构（官方能力要点）

Nginx UI 是 Go 编写的单二进制 nginx 管理面板：

- 可视化管理 server block / location / upstream。
- TLS 证书申请与自动续期（Let's Encrypt / 自有证书）。
- 配置语法校验、在线编辑与一键 reload/restart。
- 终端/日志查看等运维辅助能力。
- 自带 SQLite 数据库（app.ini `[database]`）保存面板自身的用户与设置。

### 我们的集成设计

- **实例角色**
  - 腾讯云2号宿主 systemd 常驻服务。
  - 是全站 marschat.online 公网 HTTPS 入口（该机 `nginx.service`）的「控制台」。
- **与哪些系统连接**
  - 被管理对象：本机 nginx（配置目录 `/etc/nginx/`）。
  - 面板数据库：`/etc/nginx-ui/database`（SQLite）。
  - 影响面：所有经腾讯云2号公网入口的服务
    （main/kb/woodci/nexus/monitor/note/vault/memory/s3/tokenhub/tools*/workcheck 等）。
- **为什么选它**
  - 公网入口站点数量多（十余个子域），手工改配置易出语法错误且无历史。
  - Nginx UI 提供校验 + 备份 + 可视化，把高危操作收敛到面板。
- **关键配置思路**（`/etc/nginx-ui/app.ini` 实采，敏感项略）：
  - `[server] Port = 19900`，`RunMode = debug`。
  - 未启用面板自身 HTTPS（TLS 由外层 nginx 终止）。
  - `[auth] MaxAttempts = 10`、`BanThresholdMinutes = 10`（登录失败 10 次封禁 10 分钟）。
  - IP 白名单未启用。
  - `[log] EnableRotate = true`（日志轮转开启）。

### systemd 单元关键字段（实采 /etc/systemd/system/nginx-ui.service）

| 字段 | 值 | 说明 |
|------|----|------|
| Description | Nginx UI | — |
| After | nginx.service | 被管理 nginx 先起 |
| ExecStart | /usr/local/bin/nginx-ui --config /etc/nginx-ui/app.ini | 单二进制 |
| Restart / RestartSec | always / 5 | 崩溃自愈 |
| WantedBy | multi-user.target | 开机自启 |

### 对外接口概览

- Web 面板（登录后站点/证书/配置管理）+ 自身 API。
- 按开源项目通用路由，本文不逐个列。

## 部署与发布

- 编排与位置
  - **非 Docker，systemd 服务**：`/etc/systemd/system/nginx-ui.service`。
  - 二进制：`/usr/local/bin/nginx-ui`。
  - 配置：`/etc/nginx-ui/app.ini`。
- 配置清单
  - 端口：`*:19900`（实采 ss 确认；本机 `curl 127.0.0.1:19900` 返回 200）。
  - 目录：`/etc/nginx-ui/`（app.ini + 面板数据库 database）。
  - 目录：`/etc/nginx/`（被管理对象：sites-available/sites-enabled/ssl）。
  - 环境变量：无特殊注入，全部走 app.ini。
- 发布/升级：二进制替换 + 重启，实际操作步骤：
  1. 下载新版 nginx-ui 二进制（对应 amd64）。
  2. `systemctl stop nginx-ui` → 替换 `/usr/local/bin/nginx-ui` → `systemctl start nginx-ui`。
  3. `nginx-ui --version` 验证版本。
  4. 面板登录确认站点列表完整。
  - 升级动作未在本机执行过，步骤按单二进制服务惯例整理 (待确认)。
- 回滚
  - 保留旧版二进制副本，替换回去后 `systemctl restart nginx-ui`。
  - 面板数据库与 nginx 配置不受升级影响。

## 核心功能与使用

### 功能清单

- 可视化管理腾讯云2号上的 nginx（即全站 marschat.online 公网 HTTPS 入口）：
  - 站点/反向代理配置（server block、location、upstream）的可视化增改。
  - TLS 证书管理（Let's Encrypt / 自有证书）与自动续期配置。
  - 配置校验与一键 reload/restart，减少手工改 `/etc/nginx/` 的语法错误。
- 典型场景：
  - 新增/调整某个 `*.marschat.online` 子域反代。
  - 证书轮换。
  - 排查 502/404 时通过 UI 直接查看与回滚配置。

### 典型操作路径

1. **新增子域反代**
   - 登录面板 `https://nginxui.marschat.online`（账密见 Vaultwarden）。
   - 站点列表 → 新建站点（域名 + 反代目标）。
   - 保存并校验 → reload nginx → 公网验证。
2. **证书管理**
   - 证书页查看各域名证书有效期。
   - 到期前通过面板申请/续期 Let's Encrypt 或替换自有证书。
   - 部署后 reload。
3. **故障排查**
   - 公网某子域 502 时 → 面板打开对应站点配置核对 upstream。
   - 查看面板内 nginx 错误日志 → 修正后 reload。

> UI 具体按钮细节按 v2.4.1 通用能力描述；
> 本面板直接操控公网入口 nginx，**改错会影响全站**，操作需谨慎。

## 依赖与关联

- 依赖
  - 腾讯云2号宿主的 `nginx.service`（被管理对象）。
  - `/etc/nginx-ui/`（面板自身）与 `/etc/nginx/`（被管理配置）。
- 被依赖/关联系统
  - **所有**经腾讯云2号公网入口的服务
    （main/kb/woodci/nexus/monitor/note/vault/memory/s3/tokenhub/tools*/workcheck 等）。
  - Nginx UI 是该入口的「控制台」。

## 运维要点

- 启停方式
  - `systemctl start|stop|restart|status nginx-ui`。
  - 单元 Restart=always，崩溃 5 秒后自动拉起。
- 日志查看
  - `journalctl -u nginx-ui -f`。
  - nginx 入口日志 `/var/log/nginx/`。
- 数据与备份
  - `/etc/nginx-ui/`（app.ini + SQLite 数据库）与 `/etc/nginx/` 纳入主机配置备份。
  - `/etc/nginx/sites-backup/` 存有历史配置备份目录（含 `cache-*`），误改后可回溯。
- 常见问题
  - 该服务是宿主进程（非 Docker），`docker ps` 看不到。
    - 定位用 `systemctl` 与 `ss -tlnp | grep 19900`。
  - 误改公网 nginx 配置可能导致全站 502。
    - 通过 UI 校验/回滚能力操作，勿跳过校验直接 reload。
  - `RunMode = debug` 为当前实采值，生产建议切换 release 模式 (待确认是否影响行为)。
  - 与 mykng 侧的 nginx（路径透传反代）是**不同主机**的两套 nginx，本面板只管腾讯云2号这一侧。

## 安全要点

- 面板可直接改公网入口 nginx，账号必须强口令并仅限运维人员；账密入 Vaultwarden。
- app.ini 登录防爆破已启用（10 次/10 分钟封禁）。
- IP 白名单未启用，可考虑仅放行 Tailscale 网段 (待确认需求)。
- 面板端口 19900 经公网域名反代暴露，若需收敛可改为仅本机/内网访问。

## 变更记录

- 2026-09-05 v2 补全设计/部署/使用三维度
  - 实采版本 2.4.1、systemd 单元全文、app.ini 关键配置（端口/防爆破/日志轮转）。
  - 补二进制升级与回滚方式、典型操作路径。
- 2026-09-05 v1 首次生成（基于进程/systemd 实采）
