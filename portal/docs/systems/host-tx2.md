# 腾讯云2号主机

> 公网唯一入口主机（1.117.70.30 / Tailscale 100.110.114.16），nginx 终止全部 marschat.online 子域的 HTTPS 并反代到内网（Tailscale 隧道）；同时跑 Clash 出海代理与 Nginx UI。主机管理走本机 Cockpit（Tailscale 入口 15090），Nginx 管理走 Nginx UI。

## 基本信息

| 项 | 值 |
|----|----|
| 分类 | 基础设施（主机） |
| 系统 | Ubuntu/Debian 系（Nginx sites-available 结构） |
| 公网 IP | 1.117.70.30 |
| Tailscale IP | 100.110.114.16 |
| SSH | root@1.117.70.30（凭证见 infrastructure-map 技能） |
| 本机容器 | obs-dozzle（:15500）、obs-promtail（127.0.0.1:15200）、Nginx UI（宿主/进程 :19900） |

## 访问入口

- 公网 SSH：`root@1.117.70.30`（22 端口开放）
- Nginx UI（本机 Nginx 的 Web 管理台）：`https://nginxui.marschat.online` → 127.0.0.1:19900
- Cockpit：本机 cockpit.socket drop-in（listen.conf）已配置监听 **15090**（2026-09-05 实采确认），但仅经 Tailscale 可达（curl 100.110.114.16:15090 = 200）；9090 端口被 Clash（127.0.0.1:9090）占用，与本机 Cockpit 无关

## 全链路（本机承担的公网反代，2026-09-05 实采 /etc/nginx/sites-enabled/）

| 子域 | 上游 |
|------|------|
| main.marschat.online | http://100.93.36.113（mykng nginx :80，portal/kb/ops/nacos/minio/meili 等全在此展开） |
| kb.marschat.online | http://100.93.36.113 |
| woodci.marschat.online | http://100.93.36.113:8000（Woodpecker） |
| nexus.marschat.online | upstream nexus_backend（mykng Nexus；Docker 组走 :8083） |
| monitor.marschat.online | http://100.93.36.113:80 → /infra/ |
| nginxui.marschat.online | 127.0.0.1:19900（本机 Nginx UI） |
| note.marschat.online | http://100.93.36.113:6806（思源） |
| vault.marschat.online | http://100.93.36.113:8222（Vaultwarden） |
| memory.marschat.online | http://100.93.36.113:8720（memory-panel） |
| s3.marschat.online | http://100.93.36.113:9000（MinIO API） |
| tokenhub.marschat.online | http://100.93.36.113:13000 |
| tools.marschat.online | 激活码 → http://100.105.196.63:18080（直通内网Deb）；/akhq/ → http://100.93.36.113:8080 |
| tools-test.marschat.online | upstream tools_test_backend（激活码测试） |
| workcheck.marschat.online | mykng :8010 |

## 核心功能与使用

- **HTTPS 终止 + 反代**：全家族域名的 443 TLS 证书（含 acme-challenge 自动续期）都在本机；加新子域 = sites-available 新增 conf + ln -s 到 sites-enabled + nginx -t reload（建议直接用 Nginx UI 操作）
- **Nginx UI**（:19900）：可视化管 server/location、证书、日志——公网入口的首选操作面
- **Clash 出海代理**：127.0.0.1:9090（clash-meta API）+ 7890 系端口，供内网机器出海（拉 DockerHub/依赖包等场景的前置通道之一）
- **安全边界**：本机是唯一直接暴露公网的应用面，nginx 之外端口尽量不开放

## 依赖与关联

- 下游：mykng（nginx :80 及各直连端口）、内网 Debian（激活码 18080）——全部走 Tailscale 隧道，公网请求 → 本机 → Tailscale → 内网
- 关联：Uptime Kuma 持续探活本机全部子域；证书到期告警同在 Kuma

## 运维要点

- Nginx 配置备份：sites-backup/ 目录与多个 .bak 时间戳文件（历史变更留痕）
- 日志：nginx access/error 在本机，promtail（127.0.0.1:15200）送 Loki 可在 Grafana 查
- 常见问题：新子域 502 多为 Tailscale 隧道断或下游容器挂；证书续期失败查 acme-challenge location
- 2026-09-05 修正：portal 卡片"腾讯云2号主机"原 URL 错指 mykng Cockpit，已改为本机 Cockpit Tailscale 入口 http://100.110.114.16:15090/

## 变更记录

- 2026-09-05 首次生成（portal 文档补全任务，AI 基于实采+源码生成）
