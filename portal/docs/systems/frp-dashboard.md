# FRP 仪表盘

> 内网穿透体系的「服务端状态看板 + 集中管理」双组件：
> 阿里云 frps 服务端仪表盘（7500）查看穿透运行态，
> 内网 Debian 上的自研 frp-manager 集中管理服务端/客户端/隧道配置并生成配置文件。

## 基本信息

| 项 | 值 |
|----|----|
| 分类 | 工具软件 / 接入（内网穿透） |
| 版本 | frps 服务端版本未实采，dashboard 需登录未取到版本号 (待确认) |
| 版本 | frp-manager 自研 `frp-manager:1.0.0`（Java 21 / Spring Boot，容器内 JDK 21.0.11 实采） |
| 部署位置 | FRP 服务端 + 仪表盘：阿里云主机 120.26.66.182（Tailscale 100.89.102.74），仪表盘端口 7500 |
| 部署位置 | frp-manager：内网 Debian 192.168.31.182（Tailscale 100.105.196.63）容器 `frp-manager`，端口 18082 |
| 源码位置 | frp-manager 自研：本地 `D:\huliang\java\ideaworkspace\devtools\myfrp\` |
| 源码位置 | 同 mykng `/root/devtools/myfrp/`，经 gitee devtools 仓库同步 |
| 源码位置 | FRP 本体为开源 fatedier/frp |
| CI/CD | 无流水线（仓库内置 `ci/deploy.sh` 目标机脚本 + docker compose 自部署） |

## 访问入口

- 公网（仪表盘）：`http://120.26.66.182:7500`
  - 直连 IP:端口，非 443；需 dashboard 登录凭据（实测 401 鉴权生效）
- 内网 / Tailscale（仪表盘）：`http://100.89.102.74:7500`
- 内网（frp-manager 管理端）：`http://192.168.31.182:18082/frp_manager`
  - context-path 为 `/frp_manager`，根路径 404 属正常
- 公网域名：无（均直连 IP；frp-manager 不暴露公网）

> FRP 仪表盘与 frp-manager 均不走腾讯云2号 nginx 反代，
> 属于「接入/穿透」侧独立组件，与 marschat.online 公网入口是两套并行通道。

## 全链路

```
[FRP 仪表盘]
浏览器 → 阿里云 120.26.66.182:7500 (frps dashboard，401 鉴权)

[frp-manager 管理端]
浏览器 → 内网 Deb 192.168.31.182:18082/frp_manager
       → frp-manager 容器 (Spring Boot + 内嵌 Vue3)
```

## 系统设计

### 组件架构

FRP（Fast Reverse Proxy）由 **frps（服务端）** 与 **frpc（客户端）** 组成：

- **frps**：部署在有公网 IP 的阿里云主机，作为中转/出口。
  - 内置 Dashboard（本例 7500）提供 Web 状态查看，需单独的用户名/密码鉴权。
- **frpc**：部署在需要被暴露的内网机器。
  - 把本地端口「注册」到 frps；外部流量经 frps 公网端口转发回内网服务。
  - 典型用途：在家/出差访问内网设备与自托管服务，无需为每台设备申请公网 IP。

本套部署把「穿透出口 + 看板」放阿里云、「配置管理」放内网 Debian，形成分工：

- **阿里云 frps**：承接所有隧道注册与流量转发；7500 仪表盘看服务端运行态（客户端连接数、代理列表、流量统计）。
- **frp-manager（自研）**：集中登记 frps 服务端、frpc 客户端、隧道三元数据，生成 frps.ini / frpc.toml / frpc.ini 配置文本，替代手工编辑配置文件。

### frp-manager 技术栈与模块划分

- 后端：Spring Boot（Java 21）+ MyBatis-Plus + Spring Security + JWT（登录态 24h 过期）。
  - 包结构 `com.frp.manager` 下：config / controller / dto / entity / mapper / security / service / util。
- 前端：Vue3 + Vite（`frontend/`）。
  - 构建产物拷入后端 `src/main/resources/static`，随 JAR 一起发布（单 JAR 前后端一体）。
- 存储：MySQL GR 集群（3 节点随机负载均衡 + 自动故障转移）。
  - 库名 `frp_manager`。
  - 连接节点：192.168.31.105:3306 / 192.168.31.182:3307 / 192.168.31.182:3308（账密见 Vaultwarden）。

### 核心数据模型（4 张关键表）

| 表 | 用途 |
|----|------|
| `frp_server` | frps 服务端登记：名称、host、bind_port（默认 7000）、token、dashboard 端口/账密、vhost_http_port |
| `frp_client` | frpc 客户端登记：名称、所属 server_id、config_format（toml/ini，决定生成新/旧版配置格式） |
| `frp_tunnel` | 隧道登记：名称、类型（tcp/udp 等）、local_ip/local_port、remote_port、use_encryption/use_compression、status（0=停用，生成配置时跳过） |
| `sys_user` | 管理端登录用户（JWT 认证），含逻辑删除字段 deleted |

### 关键设计决策

1. **配置生成器集中化**
   - `ConfigGenerator` 统一生成三种配置。
   - frps.ini：汇总所有隧道 remote_port 为 `allow_ports` 白名单。
   - frpc.toml：frp ≥ 0.52 新格式；frpc.ini：旧版兼容，按客户端 config_format 自动选择。
   - 隧道 status=0 自动剔除，不进配置。
2. **生成而非直推**
   - DeployController 只返回配置文本（预览/生成接口），**不做 SSH 自动下发**。
   - 管理员复制配置到对应服务器后重启 frp 进程，避免管理端直连生产主机的安全面。
3. **逻辑删除 + 统一审计字段**
   - MyBatis-Plus 全局逻辑删除（deleted 字段）。
   - MyMetaObjectHandler 自动填充创建/更新时间。

### 对外接口概览

| 路由前缀 | Controller | 功能 |
|----------|-----------|------|
| `/api/auth` | AuthController | 登录、JWT 签发 |
| `/api/servers` | ServerController | frps 服务端 CRUD |
| `/api/clients` | ClientController | frpc 客户端 CRUD |
| `/api/tunnels` | TunnelController | 隧道 CRUD |
| `/api/deploy` | DeployController | frps/frpc 配置预览与生成（`/preview/frps/{id}`、`/preview/frpc/{id}`、`/all` 批量生成） |

## 部署与发布

### frps + 仪表盘（阿里云 120.26.66.182）

- 部署方式
  - 阿里云主机上的 frps 进程/服务托管（systemd 或裸进程未实采）(待确认)。
  - 配置文件即 frp-manager 生成的 frps.ini（含 dashboard_port 7500）。
- 端口
  - 7500（仪表盘）。
  - bind 端口 7000 从 mykng 探测不可达，疑似防火墙仅放行 7500 或 bind 端口不同 (待确认)。
- 日志
  - frp-manager 生成的 frps.ini 固定写 `log_file = /var/log/frps/frps.log`、level info。

### frp-manager（内网 Debian 192.168.31.182）

- 编排与位置
  - compose 文件：`/home/root01/frp-manager/docker-compose.yml`（compose project：frp-manager）。
  - 容器：`frp-manager`；镜像：`frp-manager:1.0.0`（`build: .` 本地构建）。
  - 另 `/home/root01/frp-manager-build/` 为构建工作目录（含 Dockerfile 与 target/）。
- 配置清单
  - 端口映射：`18082:18082`（与 Spring Boot `server.port` 一致）。
  - 卷挂载：仅 `/etc/localtime`（时区同步），应用无状态——全部数据在 MySQL GR 集群。
  - 环境变量：`TZ=Asia/Shanghai`。
  - 应用配置（数据源、JWT secret、初始管理员）打包在 JAR 内 `application.yml`，账密类凭证见 Vaultwarden，不在本文落盘。
- 发布/升级（实际方式，两条路径）
  1. **脚本部署**（仓库内置）：
     - 目标机执行 `bash ci/deploy.sh <commit_sha> <branch>`。
     - 克隆/重置 gitee devtools 仓库到 `/root/devtools`。
     - `frontend` 下 `npm ci && npm run build`，拷 dist 到后端 static。
     - `docker compose build --no-cache`。
     - `docker compose up -d --force-recreate`。
     - 8 轮健康检查（`http://localhost:18082`）。
  2. **手工部署**（Deb 当前实际形态）：
     - 上传新 jar 到 `/home/root01/frp-manager/`。
     - `docker compose build --no-cache && docker compose up -d --force-recreate`。
- 回滚
  - 目录内保留 `frp-manager-1.0.0.jar.bak`，换回旧 jar 后 compose 重建即可。
  - 数据层无状态，无数据回滚问题。

## 核心功能与使用

### 功能清单

- FRP 仪表盘（7500，frps 自带）
  - 查看 frps 运行时间、版本、客户端连接数、各代理流量统计。
  - 查看已建立的代理/隧道列表（TCP/UDP/HTTP/HTTPS）、对应客户端、最近流量。
  - 排障入口：确认某条穿透隧道是否在线、流量是否异常、客户端是否掉线。
- frp-manager（18082/frp_manager，自研）
  - **服务端管理**：登记/维护阿里云 frps 的 bind 端口、token、dashboard、vhost 配置。
  - **客户端管理**：登记各内网 frpc 及其配置格式（toml/ini）。
  - **隧道管理**：登记 local→remote 端口映射，可开关加密/压缩、启用/停用。
  - **配置生成**：一键预览/生成 frps.ini 与 frpc 配置文本，复制到目标主机生效；批量生成走 `/api/deploy/all`。

### 典型操作路径

1. **查看穿透状态**
   - 登录 frps 仪表盘 `http://120.26.66.182:7500`（账密见 Vaultwarden）。
   - 首页看流量/连接数 → Proxies 页看各隧道在线状态。
2. **新增一条穿透隧道**
   - 登录 frp-manager `http://192.168.31.182:18082/frp_manager`（管理员账密见 Vaultwarden）。
   - 隧道管理 → 新建（填类型/本机 IP:端口/远程端口）→ 启用。
   - 部署页生成 frpc 配置 → 复制到对应内网机的 frpc 配置并重启 frpc。
   - 仪表盘确认代理上线。
3. **修改 frps 全局配置**
   - frp-manager 服务端管理改参数 → 部署页生成 frps.ini。
   - 复制覆盖阿里云 frps 配置 → 重启 frps → 仪表盘验证。

> UI 按钮/表单细节按源码路由归纳，未逐页实测；字段语义以源码 entity 为准。

## 依赖与关联

- 依赖
  - 阿里云主机公网 IP 与带宽（穿透出口）。
  - 内网 Deb 容器运行时（frp-manager）。
  - MySQL GR 集群（frp_manager 库，3 节点）。
- 被依赖/关联系统
  - 所有经此穿透暴露的内网服务。
  - 与腾讯云2号 nginx 入口（marschat.online）是**两套并行的对外暴露通道**，分工为「公网子域反代」vs「任意内网服务穿透」。

## 运维要点

- 启停方式
  - frp-manager：`cd /home/root01/frp-manager && docker compose up -d`（restart 策略 `unless-stopped`）。
  - 阿里云 frps：进程托管方式未实采 (待确认)。
- 日志查看
  - `docker logs frp-manager`。
  - frps 日志 `/var/log/frps/frps.log`（阿里云侧）。
- 数据与备份
  - frp-manager 数据全在 MySQL `frp_manager` 库（随 MySQL GR 集群备份体系）。
  - 容器本身无状态。
- 常见问题
  - 访问 7500 返回 401：正常，需 dashboard 账密（见 Vaultwarden）。
  - frp-manager 根路径 404：正常，入口是 `/frp_manager` context-path。
  - 生成的配置不生效：DeployController 只生成文本不下发，需人工复制到目标主机并重启 frp 进程。
  - 从内网探测阿里云 7000 不可达：疑似防火墙策略或 bind 端口非默认 (待确认)。

## 安全要点

- frps Dashboard 必须设强口令（已生效 401）；口令入 Vaultwarden，勿明文。
- frps.ini 中的 token 与 dashboard 账密经 frp-manager 数据库管理，泄露可被任意 frpc 注册隧道，需最小化知悉。
- 穿透直接把内网服务暴露到公网，隧道（谁可建、暴露哪个端口）需最小化管理；frp-manager 仅内网可达、不暴露公网。
- application.yml 内含明文数据源/JWT 凭证，建议后续改为环境变量注入并轮换 (待确认)。

## 变更记录

- 2026-09-05 v2 补全设计/部署/使用三维度
  - 确认 frp-manager 自研源码（myfrp 工程）、4 表数据模型、配置生成机制。
  - Deb 侧 compose 实采路径与 ci/deploy.sh 发布方式；修正入口 context-path。
- 2026-09-05 v1 首次生成（portal 文档补全任务）
