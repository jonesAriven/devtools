# Nginx UI

> 腾讯云2号主机上公网 nginx 入口的可视化管理面板，提供 server block、反向代理、证书的可视化配置与热重载，降低手工改 nginx 配置出错风险。

## 基本信息

| 项 | 值 |
|----|----|
| 分类 | 工具软件 / 运维（nginx 管理） |
| 版本 | Nginx UI（二进制 `/usr/local/bin/nginx-ui`）；具体版本号未实采 (待确认) |
| 部署位置 | 腾讯云2号主机（1.117.70.30 / Tailscale 100.110.114.16）**宿主 systemd 服务** `nginx-ui.service`，非 Docker；二进制 `--config /etc/nginx-ui/app.ini`；监听 19900 |
| 源码位置 | 开源 Nginx UI（linuxserver/nginx-ui 或等价实现）；腾讯云2号宿主 `/usr/local/bin/nginx-ui` |
| CI/CD | 无（宿主自部署 systemd 服务） |

## 访问入口

- 公网：`https://nginxui.marschat.online`（腾讯云2号 nginx 终止 TLS 443）
- 内网：`http://1.117.70.30:19900`（腾讯云2号宿主）
- Tailscale：`http://100.110.114.16:19900`

## 全链路

```
浏览器 → 腾讯云2号 nginx (:443, 域名 nginxui.marschat.online)
       → 127.0.0.1:19900  (本机 nginx-ui 服务)
```

实采（腾讯云2号，2026-09-05）：
- `systemctl` 存在 `nginx-ui.service`（loaded active running）与 `nginx.service`（公网入口 nginx 本身）。
- 进程：`/usr/local/bin/nginx-ui --config /etc/nginx-ui/app.ini`（PID 194216，7月18日启动）。
- 监听：`*:19900`（进程名 `nginx-ui`），本机探测 `http://127.0.0.1:19900/` 返回 200。

## 核心功能与使用

- 可视化管理腾讯云2号上的 nginx（即全站 marschat.online 公网 HTTPS 入口）：
  - 站点/反向代理配置（server block、location、upstream）的可视化增改。
  - TLS 证书管理（Let's Encrypt / 自有证书）与自动续期配置。
  - 配置校验与一键 reload/restart，减少手工改 `/etc/nginx/` 的语法错误。
- 典型场景：新增/调整某个 `*.marschat.online` 子域反代、证书轮换、排查 502/404 时通过 UI 直接查看与回滚配置。

> UI 具体操作步骤未经实测，按能力层面描述；本面板直接操控公网入口 nginx，**改错会影响全站**，操作需谨慎。

## 依赖与关联

- 依赖：腾讯云2号宿主的 `nginx.service`（被管理对象）；配置目录 `/etc/nginx-ui/`（app.ini）与 nginx 配置目录 `/etc/nginx/`。
- 被依赖/关联系统：**所有**经腾讯云2号公网入口的服务（main/kb/woodci/nexus/monitor/note/vault/memory/s3/tokenhub/tools*/workcheck 等）。Nginx UI 是该入口的「控制台」。

## 运维要点

- 启停方式
  - `systemctl start|stop|restart|status nginx-ui`
  - 配置：`/etc/nginx-ui/app.ini`（Nginx UI 自身配置）
- 日志查看
  - `journalctl -u nginx-ui -f`；nginx 入口日志 `/var/log/nginx/`。
- 数据与备份
  - Nginx UI 的站点配置通常落盘在 `/etc/nginx-ui/` 与 `/etc/nginx/` 下（具体路径未实采，建议纳入主机配置备份）(待确认)。
  - 注意：与 mykng 侧的 nginx（路径透传反代）是**不同主机**的两套 nginx，本面板只管腾讯云2号这一侧。
- 常见问题
  - 该服务是宿主进程（非 Docker），`docker ps` 看不到；定位用 `systemctl` 与 `ss -tlnp | grep 19900`。
  - 误改公网 nginx 配置可能导致全站 502；建议通过 UI 的校验/回滚能力操作，并保留 `/etc/nginx/sites-backup/` 历史（材料包显示存在 `cache-*` 备份目录）。

## 变更记录

- 2026-09-05 首次生成（portal 文档补全任务，AI 基于腾讯云2号 SSH 实采进程/systemd + 材料包生成）
