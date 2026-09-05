# Cockpit 系统管理

> mykng 主机（192.168.31.105）的 Web 系统管理台（Cockpit，systemd 宿主服务），浏览器里看主机资源、服务状态、存储、网络、日志，并可直接开 Web 终端；portal 上"基础设施"分类的主机管理类入口即指此。

## 基本信息

| 项 | 值 |
|----|----|
| 分类 | 基础设施（主机管理） |
| 版本 | Debian 12/13 发行版自带 Cockpit（宿主 systemd 服务，非容器） |
| 部署位置 | mykng 宿主机（192.168.31.105），`systemctl` 管理，服务 active（实采） |
| 端口 | 15090（cockpit-tls 进程监听，实探 http:200 可访问，非 443 证书那种 TLS） |
| 源码位置 | 开源组件，官方仓库 https://github.com/cockpit-project/cockpit |
| CI/CD | 无（发行版包管理安装） |

## 访问入口

- 公网：—（无公网反代）
- 内网：`http://192.168.31.105:15090`
- Tailscale：`http://100.93.36.113:15090`
- 账密：Linux 系统账号（root），密码见 Vaultwarden 或 infrastructure-map 技能

## 全链路

```
浏览器（家庭局域网 / Tailscale）
  → http://192.168.31.105:15090（cockpit-tls / systemd 直监听，无 nginx 层）
  → 宿主机 Cockpit Web 服务
```

## 核心功能与使用

- **系统概览**：CPU/内存/磁盘 I/O/网络实时图，33 个服务跑在同一台机上，资源水位一眼可判
- **服务管理**：systemd 服务列表启停（含 docker、nginx、cockpit 自身）；容器则跳转 Dozzle/命令行
- **Web 终端**：浏览器内直接开 root shell，跑 `docker ps`、流水线脚本等，RDP 之外的轻量通道
- **存储/网络**：磁盘占用、挂载点、网络接口查看；日志页可按 unit 过滤 journal（与 Grafana+Loki 互补，本机即时视角）

## 依赖与关联

- 依赖：systemd（宿主原生）
- 关联：内网 Debian 主机 Cockpit（192.168.31.182:15090，同款）；Dozzle (mykng)（容器日志专看）；腾讯云2号主机（其 Cockpit 未暴露端口，经 SSH 管理）

## 运维要点

- 启停：`systemctl status cockpit` / `cockpit.socket`
- 配置：/etc/cockpit/cockpit.conf（端口 15090 为自定义，默认 9090 被占用或安全考虑改口——以宿主实际配置为准）
- 安全：持系统账号权限 + Web 终端，仅限内网/Tailscale，禁止公网反代
- 常见问题：登不上先 `systemctl is-active cockpit`；端口变更后 portal 卡片 URL 需同步更新

## 变更记录

- 2026-09-05 首次生成（portal 文档补全任务，AI 基于实采+源码生成）
