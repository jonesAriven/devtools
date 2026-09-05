# Cockpit 系统管理 (mykng)

> mykng 主机（192.168.31.105）的 Web 系统管理台（Cockpit，systemd 宿主服务，非容器），浏览器里看主机资源、服务状态、存储、网络、日志，并可直接开 Web 终端；portal 上"基础设施"分类的主机管理类入口即指此。

## 基本信息

| 项 | 值 |
|----|----|
| 分类 | 基础设施（主机管理） |
| 版本 | Debian 12/13 发行版自带 Cockpit（宿主 systemd 服务，非容器） |
| 部署位置 | mykng 宿主机（192.168.31.105），`systemctl` 管理，cockpit.service 与 cockpit.socket 均 active（实采 2026-09-05） |
| 端口 | 15090（cockpit-tls 进程 + systemd socket 监听 *:15090，实采） |
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
  → http://192.168.31.105:15090（systemd socket → cockpit-tls 直监听，无 nginx 层）
  → 宿主机 Cockpit Web 服务
```

## 系统设计

### 组件架构

Cockpit 是 Red Hat 系发行版的 Web 管理台，官方能力要点：

- 以 **systemd 为底座**，通过 D-Bus 聚合主机各维度信息；socket 激活模式平时不占资源，连接进来才起会话
- 模块化页面：Overview（资源总览）、Services（systemd 服务）、Storage（磁盘/挂载）、Networking（接口/防火墙）、Logs（journal 过滤）、Terminal（Web 终端）等
- 认证走系统账号（PAM），权限即 Linux 账号权限——登录什么账号就有什么权限
- cockpit-tls 进程可提供 TLS；也支持 AllowUnencrypted 明文 http（靠网络边界保护）
- 非容器部署，随发行版包管理升级

### 我们的集成设计

- **实例角色**：mykng（主部署机，33 个服务同机）的图形化管理面——RDP/SSH 之外的第三通道，最贴近"看一眼资源水位 + 点两下服务"的场景
- **数据连接**：直接读宿主 systemd/D-Bus/journal，不经过 Docker；容器层面则与 Dozzle (mykng)（:15500）分工——systemd 服务与主机资源在 Cockpit，容器日志在 Dozzle
- **为什么选它**：发行版自带、零容器化成本；Web 终端对临时救急（SSH 客户端不在手边）非常实用
- **关键配置思路**（实采 2026-09-05）：
  - 端口定制：**不在 cockpit.conf**，而是 systemd socket drop-in `/etc/systemd/system/cockpit.socket.d/listen.conf`，`[Socket] ListenStream=15090`（默认 9090 让路——本机 9090 场景见各主机说明）
  - `/etc/cockpit/cockpit.conf`：`AllowUnencrypted = true`；Origin 白名单含内网 IP / Tailscale IP 的 15090 入口；`ProtocolHeader = X-Forwarded-Proto` 为反代场景预留
  - cockpit-tls 同时承担 TLS：https 握手可通（实采）；http 因 AllowUnencrypted 同样可用
  - 白名单中 `cockpit-mykng.marschat.online:15090` 域名当前**无 DNS 解析记录**（实采 getent 无结果），属预留项，暂不可用作入口
  - 安全边界：持系统账号权限 + Web 终端，仅限内网/Tailscale，禁止公网反代

### 两台 Cockpit 与一台未暴露的布局

| 主机 | 入口 | 状态（2026-09-05 实采） |
|------|------|--------------------------|
| mykng（本篇） | 192.168.31.105:15090 / 100.93.36.113:15090 | active，正常服务 |
| 内网 Debian | 192.168.31.182:15090 | cockpit.socket failed，入口不可用（待修复） |
| 腾讯云2号 | 100.110.114.16:15090（仅 Tailscale） | socket drop-in 已配 15090，可达 |

### 功能模块与 Dozzle/SSH 的分工（官方模块 × 实例用法）

| Cockpit 模块 | 官方能力 | 实例用法 | 不用它的场合 |
|--------------|----------|----------|--------------|
| Overview | CPU/内存/磁盘 I/O/网络实时图 | 日常资源水位巡检 | 精细时序去 Grafana |
| Services | systemd 服务列表与启停 | docker/nginx 等宿主服务管理 | 容器日志去 Dozzle |
| Terminal | 浏览器内 shell | 救急通道（SSH 客户端不在手边） | 批量操作仍走 SSH 脚本 |
| Storage | 磁盘/挂载点/RAID | 磁盘水位与挂载检查 | — |
| Networking | 接口/地址/防火墙 | 快速看网卡与监听 | — |
| Logs | journal 过滤查看 | 本机即时日志 | 历史/跨机去 Grafana+Loki |

### 权限与安全模型（官方机制）

- 认证走 PAM：登录即 Linux 系统账号，权限与该账号一致（root 登录 = 全权，因此入口必须收敛）
- 会话经 cockpit-tls/WebService 转发到 D-Bus，socket 激活模式下无连接即无进程开销
- Origin 白名单（cockpit.conf）是浏览器侧防线；网络边界（仅内网/Tailscale）才是根本防线，两层都要在

## 部署与发布

### 编排与位置

- 非容器部署：宿主 systemd 服务（cockpit.service + cockpit.socket），发行版包管理安装
- 配置文件（实采）：

| 文件 | 作用 |
|------|------|
| /etc/systemd/system/cockpit.socket.d/listen.conf | 端口 15090 定制（socket drop-in） |
| /etc/cockpit/cockpit.conf | AllowUnencrypted / Origins 白名单 / ProtocolHeader |

### 配置清单

| 项 | 值 |
|----|----|
| 监听 | systemd socket *:15090（drop-in 定制） |
| 认证 | Linux 系统账号（PAM），root 可登录 |
| TLS | cockpit-tls 提供 https 能力，http 因 AllowUnencrypted 同时可用 |

### 发布/升级

- 升级随发行版：`apt update && apt install --only-upgrade cockpit`
- 配置变更流程：编辑上述两个配置文件 → `systemctl daemon-reload && systemctl restart cockpit.socket`
- portal 卡片 URL 与 Cockpit 入口联动：端口变更后同步改 portal 数据

### 回滚

- 配置回退：删除/还原 listen.conf drop-in → daemon-reload → restart cockpit.socket（回到默认 9090）
- 服务异常：`systemctl restart cockpit.socket`（socket 激活模式，重启 socket 即可）

## 核心功能与使用

### 功能清单

| 能力 | 什么场景用 |
|------|------------|
| 系统概览 | CPU/内存/磁盘 I/O/网络实时图，33 个服务同机的资源水位一眼可判 |
| 服务管理 | systemd 服务列表启停（含 docker、nginx、cockpit 自身）；容器则跳转 Dozzle/命令行 |
| Web 终端 | 浏览器内直接开 root shell，跑 `docker ps`、流水线脚本等，RDP 之外的轻量通道 |
| 存储/网络 | 磁盘占用、挂载点、网络接口查看 |
| 日志页 | 按 unit/优先级过滤 journal（与 Grafana+Loki 互补，本机即时视角） |

### 典型操作路径

1. **看资源水位**：登录（:15090）→ Overview 页 → CPU/内存曲线判断是否需要扩容/重启
2. **救急终端**：登录 → Terminal → 直接跑 `docker compose up -d <svc>` 等命令
3. **查本机日志**：登录 → Logs → 按 unit/优先级过滤 journal（历史跨机查询转 Grafana+Loki）
4. **启停系统服务**：Services 页 → 搜索服务 → Start/Stop/Restart

## 依赖与关联

- 依赖：systemd（宿主原生）
- 关联：
  - 内网 Debian 主机 Cockpit（192.168.31.182:15090，同款配置思路——注意该机 cockpit.socket 当前 failed，见其文档）
  - Dozzle (mykng)（容器日志专看）
  - 腾讯云2号主机（Cockpit 仅 Tailscale 15090 可达，公网管理走 SSH）

## 运维要点

### 启停

- 状态检查：`systemctl status cockpit` / `systemctl status cockpit.socket`
- socket 激活模式重启入口是 restart socket，不是 restart service

### 常见问题

| 症状 | 原因 | 处理 |
|------|------|------|
| 登不上 | socket/服务未起 | `systemctl is-active cockpit cockpit.socket` → restart socket |
| 浏览器报 Origin 校验失败 | 新入口不在白名单 | cockpit.conf 的 Origins 列表加 IP/域名 → restart |
| 端口对不上 | 只改了 cockpit.conf 没改 socket drop-in（或反之） | 两处一起改，daemon-reload 后重启 |
| portal 卡片打不开 | 入口变更未同步 | 端口变更后更新 portal 数据 |

### 安全红线

- 持系统账号权限 + Web 终端：**仅限内网/Tailscale，禁止公网反代**
- TLS 白名单里的预留域名（cockpit-mykng.marschat.online）启用前需先加 DNS 解析并复核 Origin 配置

### 操作速查（SSH root@192.168.31.105）

| 动作 | 命令 |
|------|------|
| 看状态 | `systemctl status cockpit cockpit.socket` |
| 重启入口 | `systemctl restart cockpit.socket` |
| 看监听 | `ss -tlnp | grep 15090` |
| 看端口配置 | `cat /etc/systemd/system/cockpit.socket.d/listen.conf` |
| 看 Origin 配置 | `cat /etc/cockpit/cockpit.conf` |
| 验证连通 | `curl -sk -m 5 -o /dev/null -w "%{http_code}" https://127.0.0.1:15090` |

### mykng 管理面对照（什么时候用哪个）

| 管理面 | 入口 | 适用场景 |
|--------|------|----------|
| Cockpit（本篇） | :15090 Web | 资源水位、systemd 服务、救急终端、journal |
| SSH | root@192.168.31.105 | 批量操作、脚本、Cockpit 不可用时的兜底 |
| Dozzle (mykng) | :15500 Web | 容器 stdout 实时日志 |
| RDP | 远程桌面 | 需要图形桌面时 |
| Woodpecker | woodci.marschat.online | 构建/部署日志（非运行时） |

原则：看主机用 Cockpit，看容器日志用 Dozzle，跑批量命令用 SSH，部署问题去 CI——四者互补不重叠。

## 变更记录

- 2026-09-05 v2 补全设计/部署/使用三维度；实采确认端口定制位于 socket drop-in listen.conf、AllowUnencrypted+Origins 配置、TLS 可用、cockpit-mykng.marschat.online 无 DNS 解析（预留项）；三主机 Cockpit 布局表
- 2026-09-05 v1 首次生成（portal 文档补全任务，AI 基于实采+源码生成）
