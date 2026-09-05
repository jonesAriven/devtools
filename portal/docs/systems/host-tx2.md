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

## 系统设计（主机角色）

### 定位

- **安全边界**：全家族唯一暴露公网的应用面——443 TLS 终止、端口收敛都靠这台；nginx 之外端口尽量不开放
- 机房网络位置：公网 1.117.70.30 + Tailscale 100.110.114.16；内网机器不直接暴露，公网 IP 只有一个

### 三层转发架构

```
公网请求（https://*.marschat.online）
  → 本机 nginx :443（TLS 终止 + 路由）
  → Tailscale 隧道（100.x 网段）
  → 内网：mykng nginx :80（多数子域展开）或 内网Deb :18080（激活码）
```

### 关键设计决策

- **反代配置结构**：sites-available + sites-enabled 软链；历史变更留 .bak 时间戳文件与 sites-backup/ 目录，可回溯
- **Nginx UI 叠加管理**：可视化操作面与手工配置操作同一份 nginx 配置，二选一操作避免打架
- **旁路代理能力**：Clash（127.0.0.1:9090 clash-meta API + 7890 系端口）为内网机器提供依赖拉取出海通道
- **观测接入**：本机 Dozzle（:15500）只监听内网/Tailscale 侧；obs-promtail（127.0.0.1:15200）把 journal + /var/log + nginx 日志送 Loki，公网只留 22/80/443

### 端口收敛表（实采归纳）

| 端口 | 归属 | 暴露范围 |
|------|------|----------|
| 22 | SSH | 公网 |
| 80/443 | nginx（含 acme-challenge） | 公网 |
| 19900 | Nginx UI | 本机 127.0.0.1（经 nginxui 子域反代） |
| 15090 | Cockpit | 仅 Tailscale |
| 15500 | obs-dozzle | 仅内网/Tailscale 侧 |
| 15200 | obs-promtail | 仅 127.0.0.1 |
| 9090 | clash-meta API | 仅 127.0.0.1 |
| 7890 系 | Clash 代理 | 内网机器出海用 |

### 命令速查（SSH root@1.117.70.30）

| 动作 | 命令 |
|------|------|
| 列启用站点 | `ls /etc/nginx/sites-enabled/` |
| 校验配置 | `nginx -t` |
| 平滑重载 | `nginx -s reload` |
| 测下游隧道 | `curl -s -m 5 http://100.93.36.113` |
| 看证书链路 | 对应 server 块内 acme-challenge location |
| 容器日志 | 本机 Dozzle :15500（Tailscale 侧）或 `docker logs` |

### 备份矩阵

| 数据 | 位置 | 备份现状 |
|------|------|----------|
| nginx 配置 | /etc/nginx/sites-available/ | .bak 时间戳文件 + sites-backup/ 目录（历史留痕） |
| 证书 | Let's Encrypt 标准路径 | acme-challenge 自动续期 |
| 本机容器数据 | obs-dozzle/obs-promtail | 均无状态，可重建 |

### 新子域上线 runbook

1. 规划上游：确定走 mykng nginx :80 还是直连某容器端口，确认 Tailscale 隧道可达
2. Nginx UI 建 server：域名、上游、证书（Let's Encrypt），保存自动 reload
3. 校验：`nginx -t` + curl 公网域名探活 + 检查证书链
4. 观察：access 日志确认流量进来（Grafana 查本机 nginx 日志）
5. 监控：Uptime Kuma 加 HTTP(s) monitor + 证书 monitor
6. 留痕：sites-backup/ 备份一份当前配置，portal 收录新卡片

### 故障影响面速判

| 现象 | 最可能原因 | 影响面 |
|------|------------|--------|
| 单个子域 502 | 下游容器挂或路径配错 | 仅该服务 |
| 全部子域 502/超时 | Tailscale 隧道断 | 全部内网业务（nginxui 等本机服务不受影响） |
| 全站证书告警 | 续期失败 | 全部 https 域名（Kuma 证书 monitor 会先报） |
| 本机失联 | 公网 IP/SSH 异常 | 全家族公网入口，最高优先级处理 |

## 部署与发布

### 编排与位置

| 组件 | 形态 | 位置/端口 |
|------|------|-----------|
| nginx | 宿主安装（非容器） | /etc/nginx/sites-available/，启用经软链到 sites-enabled/；备份 sites-backup/ |
| Nginx UI | 宿主进程 | :19900，管理同一份 nginx 配置 |
| obs-dozzle | 容器 | :15500（内网/Tailscale 侧） |
| obs-promtail | 容器 | 127.0.0.1:15200 → Loki |
| Cockpit | 宿主 systemd socket | drop-in listen.conf 定制 15090，仅 Tailscale 可达 |
| Clash | 宿主进程 | 127.0.0.1:9090（API）+ 7890 系端口 |

### 发布/升级

- 加新子域：sites-available 新增 conf → `ln -s` 到 sites-enabled → `nginx -t && nginx -s reload`（建议直接用 Nginx UI 操作）
- 证书：acme-challenge 自动续期挂在本机 443 链路
- 本机容器升级：`docker pull` + 重建（自部署，无流水线）

### 回滚

- nginx 配置：回滚到 sites-backup/ 或 .bak 时间戳文件 → `nginx -t && nginx -s reload`
- 502 类故障先看是配置回滚问题还是 Tailscale 隧道/下游容器问题，不要盲目回滚

## 核心功能与使用

### 功能清单

| 能力 | 什么场景用 |
|------|------------|
| HTTPS 终止 + 反代 | 全家族域名的 443 TLS 证书（含 acme-challenge 自动续期）都在本机；加新子域一条链路走通即可上线 |
| Nginx UI（:19900） | 可视化管 server/location、证书、日志——公网入口的首选操作面 |
| Clash 出海代理 | 供内网机器出海（拉 DockerHub/依赖包等场景的前置通道之一） |
| 安全边界 | 唯一直接暴露公网的应用面，nginx 之外端口尽量不开放 |

### 典型操作路径

1. **上线新子域**：登录 Nginx UI → 站点管理 → 新建/编辑 server → 挂证书 → 保存自动 reload → Uptime Kuma 加对应 monitor
2. **排查 502**：SSH 登本机 → `curl -s -m 5 http://100.93.36.113` 测 Tailscale 隧道 → 通则查下游容器（mykng Dozzle :15500），不通查 Tailscale 状态
3. **看公网流量日志**：本机 nginx access/error 日志 → 本机 promtail 已送 Loki → Grafana 查（关键词/状态码过滤）
4. **主机管理**：Tailscale 下访问 http://100.110.114.16:15090（本机 Cockpit）或 SSH

## 依赖与关联

- 下游：mykng（nginx :80 及各直连端口）、内网 Debian（激活码 18080）——全部走 Tailscale 隧道，公网请求 → 本机 → Tailscale → 内网
- 关联：Uptime Kuma 持续探活本机全部子域；证书到期告警同在 Kuma；本机 Dozzle（:15500）+ obs-promtail 接入观测栈

## 运维要点

### 备份与日志

- Nginx 配置备份：sites-backup/ 目录与多个 .bak 时间戳文件（历史变更留痕）
- 日志：nginx access/error 在本机，promtail（127.0.0.1:15200）送 Loki 可在 Grafana 查

### 常见问题

| 症状 | 原因 | 处理 |
|------|------|------|
| 新子域 502 | Tailscale 隧道断或下游容器挂 | 先测隧道再查下游（见操作路径 2） |
| 证书续期失败 | acme-challenge location 被改动 | 检查对应 server 块的 challenge 配置 |
| Cockpit 端口困惑 | 9090 被 Clash 占用 | 本机 Cockpit 用 15090，与 mykng 情形同理 |
| portal 卡片 URL 错误 | 曾错指 mykng Cockpit | 2026-09-05 已改为本机 Cockpit Tailscale 入口 http://100.110.114.16:15090/ |

### 安全红线

- 公网只留 22/80/443；Dozzle/Cockpit/promtail 等管理面一律仅内网/Tailscale 可达
- 修改 nginx 前先备份（.bak / sites-backup/），reload 前必过 `nginx -t`

## 变更记录

- 2026-09-05 v2 补全设计/部署/使用三维度（主机角色/三层转发/组件位置表/发布回滚/FAQ 表整理）
- 2026-09-05 v1 首次生成（portal 文档补全任务，AI 基于实采+源码生成）
