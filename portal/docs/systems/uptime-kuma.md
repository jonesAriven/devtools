# Uptime Kuma 监控告警

> 站点/服务/端口存活监控与告警通知（Uptime Kuma 1.x，18 个 monitor），覆盖全部 portal 收录系统的健康检查；观测栈里"第一时间知道谁挂了"的哨兵，部署在内网 Debian，需 Tailscale/家庭局域网访问。

## 基本信息

| 项 | 值 |
|----|----|
| 分类 | 基础设施（可观测性） |
| 版本 | louislam/uptime-kuma:1（容器实采 2026-09-05，1.x 线） |
| 部署位置 | 内网 Debian（192.168.31.182）容器 `obs-uptime-kuma` |
| 端口 | 15001（宿主）→ 容器 3001 |
| 数据卷 | 命名卷 `observability_uptime-kuma-data` → /app/data（SQLite，含全部 monitor 配置） |
| 源码位置 | 开源组件，官方仓库 https://github.com/louislam/uptime-kuma |
| CI/CD | 无（自部署，compose 位于内网 Debian /opt/observability/docker-compose.yml） |

## 访问入口

- 公网：—（无公网反代）
- 内网：`http://192.168.31.182:15001`
- Tailscale：`http://100.105.196.63:15001`
- 账密：见 Vaultwarden（vault.marschat.online）或 infrastructure-map 技能

## 全链路

```
浏览器（家庭局域网 / Tailscale）
  → http://192.168.31.182:15001（docker-proxy 直连，无 nginx 层）
  → 容器 obs-uptime-kuma (:3001)

探测出站：Kuma 主动发起 HTTP(s)/TCP 探测 → 公网域名经本机网络出口（注意代理路径）
```

## 系统设计

### 组件架构

Uptime Kuma 是开源的自托管拨测监控，官方能力要点：

- 核心模型：**monitor（监控项）→ heartbeat（心跳）→ notification（通知）**——monitor 按配置间隔对目标探测，逐次产生心跳记录，状态翻转（up/down）时触发已配置的通知渠道
- 探测类型：HTTP(s)（状态码/关键字/证书）、TCP 端口、Ping、DNS、Docker 容器状态、Push（被动接收心跳）等十余种
- 通知渠道：Telegram、邮件（SMTP）、Webhook、钉钉/飞书类等近百种集成
- 状态页：可发布公开/私有状态页，聚合展示多个 monitor 的可用率
- 存储：单文件 SQLite，全部配置与心跳历史在 /app/data

### 我们的集成设计

- **实例角色**：全系统 7×24 存活哨兵 + 证书到期提醒；观测三层分工里的"告警层"
- **数据连接**：
  - 探测目标：portal 收录的全部公网域名（main / kb / nexus / woodci / vault / note / memory / tokenhub / tools / tools-test / monitor 等）+ 内部服务端口
  - 无上游存储依赖：SQLite 自包含，数据全在 uptime-kuma-data 卷
- **为什么选它**：单容器零依赖、UI 上手快、通知渠道丰富；对"十几个人用的家庭机房"场景，Kuma 的存活+通知粒度刚好，不需要 Prometheus 级别的复杂度
- **关键配置思路**：
  - monitor 探测间隔与重试次数按"公网域名严、内部端口松"区分
  - 通知渠道配置在系统内 Notification（渠道凭据不入文档）
  - 探测目标写公网域名时注意本机 Clash 出海代理会影响探测路径（内网 Debian 7890 端口有代理监听）

### 观测三层分工（本实例所处位置）

| 层 | 系统 | 职责 |
|----|------|------|
| 告警层 | Uptime Kuma（本篇） | 谁挂了、证书什么时候到期，7×24 主动通知 |
| 定位层 | Grafana + Loki | 挂了之后查日志/趋势，定位原因 |
| 台账层 | infra-monitor（自研） | 资产清单、账密登记、配置漂移 |

### monitor 探测类型（官方能力，实例使用的类型加粗）

| 类型 | 说明 | 实例用途 |
|------|------|----------|
| **HTTP(s)** | 探测 URL 状态码，可配关键字/证书到期 | 公网域名全家桶 + 证书到期提醒 |
| **TCP** | 探测端口连通性 | 内部服务端口（mysql/redis 等） |
| **关键字** | 探测响应体包含/不包含指定文字 | 页面被劫持/报错页误判兜底 |
| HTTP(s) - 关键字 | 状态码+关键字组合 | — |
| Ping / DNS / Push / Docker | 其他类型 | 实例未使用（能力层备选） |

### 通知机制（官方能力）

- 通知渠道（Notification）在系统内配置一次，可复用到多个 monitor；状态翻转（down→恢复 或 up→故障）即触发
- 支持渠道近百种（Telegram/SMTP/Webhook/国内 IM webhook 等）；渠道凭据只存 SQLite，不落文档
- 重试与确认：Heartbeats 重试次数达标才判 down，避免单次抖动误报

## 部署与发布

### 编排与位置

- compose 文件：`/opt/observability/docker-compose.yml`（内网 Debian 192.168.31.182）
- compose project：`observability`（同项目共 5 容器，`docker compose ls` 实采确认）
- 容器名：`obs-uptime-kuma`，镜像 `louislam/uptime-kuma:1`

### 配置清单（实采）

| 项 | 值 |
|----|----|
| 端口映射 | 宿主 15001 → 容器 3001 |
| 卷挂载 | 命名卷 observability_uptime-kuma-data → /app/data（SQLite 库：monitor/心跳/通知渠道全部在此） |

### 发布/升级

自部署，无流水线。实际操作步骤（SSH 到 192.168.31.182）：

1. **升级前备份**（SQLite 含全部配置，必做）：
   - `docker compose stop uptime-kuma`
   - 拷贝 /var/lib/docker/volumes/observability_uptime-kuma-data/_data 到安全位置
2. 改 compose 镜像 tag（1.x 线内跟随，如 1.23.x）
3. `docker compose pull uptime-kuma && docker compose up -d uptime-kuma`
4. 验证：登录后抽查几个 monitor 心跳是否正常入库

### 回滚

- 镜像回退：compose 改回旧 tag 后 `docker compose up -d uptime-kuma`
- 数据回退：用升级前备份的卷覆盖后重建容器（monitor 配置随之恢复）

## 核心功能与使用

### 功能清单

| 能力 | 什么场景用 |
|------|------------|
| 存活监控（18 个 monitor） | portal 收录域名/系统 + 内部服务端口，HTTP(s)/TCP/关键字多类型探活，心跳历史形成可用率曲线 |
| 告警通知 | 探活失败触发通知渠道，恢复自动通知；改配置后先用 Notification 测试按钮验证 |
| 证书监控 | HTTPS 域名证书到期提醒（marschat.online 全家桶统一在此盯） |
| 状态页 | 可发布公开/私有状态页（当前以内部使用为主） |

### 使用原则（重要）

- **portal 卡片"在线/离线"是浏览器端即时探测**，仅当前会话视角
- **Uptime Kuma 才是 7×24 权威存活记录**，二者不一致时以 Kuma 历史为准

### 典型操作路径

1. **新增监控项**：登录（:15001）→ Add New Monitor → 选类型（HTTP(s)/TCP/关键字）→ 填目标与间隔 → 配 Heartbeats 重试次数 → 保存
2. **接通知渠道**：登录 → Settings/Notifications → 添加渠道 → 逐个 monitor 勾选应用 → 点测试按钮验证链路
3. **看可用率**：首页 monitor 列表 → 点开单项 → 心跳条与 uptime 百分比 → 定位抖动时间点 → 转 Grafana 查对应时段日志
4. **证书巡检**：证书类 monitor 列表 → 查看到期天数 → 续期后确认告警解除

## 依赖与关联

- 依赖：无外部中间件（SQLite 自包含）
- 关联：infra-monitor（自研，台账+配置漂移+账密登记视角）；Grafana+Loki（日志定位）；腾讯云2号上全部子域均在其探活范围内

## 运维要点

### 启停 / 备份

- 启停：内网 Debian /opt/observability compose 项目，`docker compose up -d uptime-kuma` / `stop uptime-kuma`
- 数据与备份：uptime-kuma-data 卷内 SQLite，改 monitor/通知后建议顺手备份该卷；未纳入统一备份体系
- 日志：容器日志可由同机 Dozzle（:15888）直查

### 常见问题

| 症状 | 原因 | 处理 |
|------|------|------|
| 告警发不出 | 渠道凭据失效或未应用到 monitor | 先点 Notification 测试按钮，再逐 monitor 检查勾选 |
| 探测结果时好时坏 | 探测路径经过本机代理（7890） | 确认 monitor 目标的期望路径，必要时调整探测出口 |
| Kuma 自身挂了没人报警 | 无外部拨测兜底 | 已知边界：依赖人工发现，整机断电场景告警自身失效 |
| 心跳大量 down 但服务正常 | Kuma 所在网络到目标链路抖动 | 对照 Grafana 时段日志 + Tailscale 状态综合判断 |

### 操作速查（SSH root@192.168.31.182）

| 动作 | 命令 |
|------|------|
| 看状态 | `docker compose -f /opt/observability/docker-compose.yml ps uptime-kuma` |
| 重启 | `docker compose -f /opt/observability/docker-compose.yml restart uptime-kuma` |
| 备份配置 | stop 后拷贝 /var/lib/docker/volumes/observability_uptime-kuma-data/_data |
| 看容器日志 | 同机 Dozzle :15888 选 obs-uptime-kuma |

### 维护节奏建议

- 改 monitor / 通知渠道后：顺手备份 data 卷（SQLite 单文件，拷走即可）
- 每次新子域上线：同步在 Kuma 加 monitor + 证书监控，保持探活范围与 portal 收录一致
- 季度巡检：抽查通知渠道有效性（点测试按钮），防止渠道凭据过期静默失效

### 新系统接入监控 checklist

1. portal 收录新系统时，同步在 Kuma 建 monitor（HTTP(s) 类型，目标写公网域名）
2. 内部有裸端口服务（不挂域名）的，补 TCP 类型 monitor
3. HTTPS 域名一并建证书到期 monitor
4. monitor 应用到统一通知渠道，并点测试按钮验证
5. 回填 portal 卡片描述中的 monitor 数量口径（当前 18 个）

### 与 portal 卡片"在线/离线"的关系

- portal 前端卡片状态是浏览器即时探测：只反映"你此刻的网络视角"，跨网/代理场景会误判
- Kuma 的心跳历史才是权威：间隔探测 + 重试确认 + 7×24 留痕
- 排查顺序建议：Kuma 心跳正常 → 问题在用户侧网络；Kuma 也 down → 按告警链路处理

## 变更记录

- 2026-09-05 v2 补全设计/部署/使用三维度（compose 编排/挂载实采核验；三层分工表、FAQ 表整理；monitor 数量沿用 portal 卡片描述 18 个）
- 2026-09-05 v1 首次生成（portal 文档补全任务，AI 基于实采+源码生成）
