# Grafana 仪表盘

> 日志/指标可视化平台，承载 Hermes-Ops-Overview 等仪表盘，与 Loki（日志存储）、Promtail（采集）、Uptime Kuma（存活告警）、自研 infra-monitor（主机台账）组成可观测性栈的可视化层；部署在内网 Debian 上，需 Tailscale/家庭局域网访问。

## 基本信息

| 项 | 值 |
|----|----|
| 分类 | 基础设施（可观测性） |
| 版本 | grafana/grafana:11.3.0（容器实采 2026-09-05） |
| 部署位置 | 内网 Debian（192.168.31.182）容器 `obs-grafana` |
| 端口 | 15300（宿主）→ 容器 3000 |
| 数据卷 | 命名卷 `observability_grafana-data` → /var/lib/grafana；配置预置目录 /opt/observability/grafana/provisioning → /etc/grafana/provisioning |
| 源码位置 | 开源组件，官方仓库 https://github.com/grafana/grafana |
| CI/CD | 无（自部署，compose 位于内网 Debian /opt/observability/docker-compose.yml） |

## 访问入口

- 公网：—（未配置公网反代，属内网观测资产，刻意不放公网）
- 内网：`http://192.168.31.182:15300`
- Tailscale：`http://100.105.196.63:15300`
- 账密：见 Vaultwarden（vault.marschat.online）或 infrastructure-map 技能

## 全链路

```
浏览器（家庭局域网 / Tailscale 客户端）
  → http://192.168.31.182:15300（docker-proxy 直连容器，无 nginx 层）
  → 容器 obs-grafana (:3000)
```

> 内网 Debian 不在公网入口链路上（公网入口是腾讯云2号 → mykng），Grafana/Loki/Uptime Kuma 刻意只留内网+Tailscale 入口。

## 系统设计

### 组件架构

Grafana 是开源的可视化与观测平台，官方能力要点：

- 核心数据模型：**数据源（DataSource）→ 查询（Query）→ 面板（Panel）→ 仪表盘（Dashboard）**，一个仪表盘由若干面板组成，每个面板绑定一个数据源的一条查询
- 多数据源统一界面：Loki、Prometheus、MySQL、PostgreSQL 等几十种数据源插件化接入，查询与展示体验一致
- Explore：即席查询视图，适合临时排查（区别于固定布局的 Dashboard）
- Alerting：告警规则、通知渠道、静默等能力内建
- Provisioning：声明式配置机制——数据源、看板、告警等以 YAML/JSON 文件描述，服务启动时自动加载
- 插件体系：面板插件/数据源插件可扩展（我们实例未加装额外插件）

### 我们的集成设计

- **实例角色**：可观测性栈的"人机界面"——所有日志检索、指标看板、跨机排查的统一入口；自身不存业务数据，只做查询与展示
- **数据连接**：
  - 读 Loki（同机 obs-loki :15100）——日志数据源，Explore 与看板面板均走 LogQL
  - 读 infra-monitor（自研，monitor.marschat.online/infra/）——主机/服务台账视角互补（台账与漂移在 infra-monitor 看，时序在这里看）
- **为什么选它**：与 Loki 同属 Grafana 家族，数据源零适配；LogQL 查询界面成熟；单容器部署成本低，符合家庭机房运维强度
- **关键配置思路**：
  - **Provisioning 预置为主**：宿主 /opt/observability/grafana/provisioning 挂载到 /etc/grafana/provisioning，数据源（Loki）与看板以文件声明，容器启动即自动加载——compose 重建后配置不丢的关键
  - **UI 手工配置为辅**：手工建的看板/用户存 grafana-data 命名卷（SQLite），与 provisioning 互补
  - 认证账密在 Vaultwarden 登记，不落文档

### 观测栈分工（本实例所处位置）

| 成员 | 角色 | 位置 |
|------|------|------|
| Uptime Kuma | 存活探测 + 告警（知道谁挂了） | 内网Deb :15001 |
| Grafana（本篇） | 可视化与查询（看趋势、查现场） | 内网Deb :15300 |
| Loki | 日志集中存储（可跨机检索历史） | 内网Deb :15100 |
| Promtail | 各主机日志采集推送 | mykng / 内网Deb / 腾讯云2号 |
| Dozzle | 单机容器日志实时直查 | 内网Deb :15888 / mykng :15500 |
| infra-monitor（自研） | 资产台账 + 配置漂移 | monitor.marschat.online/infra/ |

## 部署与发布

### 编排与位置

- compose 文件：`/opt/observability/docker-compose.yml`（内网 Debian 192.168.31.182）
- compose project：`observability`（同项目共 5 容器：obs-promtail / obs-loki / obs-grafana / obs-uptime-kuma / obs-dozzle，`docker compose ls` 实采确认）
- 容器名：`obs-grafana`，镜像 `grafana/grafana:11.3.0`

### 配置清单（实采）

| 项 | 值 |
|----|----|
| 端口映射 | 宿主 15300 → 容器 3000 |
| 卷挂载 1 | /opt/observability/grafana/provisioning → /etc/grafana/provisioning（数据源/看板预置） |
| 卷挂载 2 | 命名卷 observability_grafana-data → /var/lib/grafana（SQLite 库存用户/手工看板/偏好） |
| 环境变量 | 账密/域类变量（值不落盘，见 compose 原文与 Vaultwarden） |

### Provisioning 机制（实例用法）

- 宿主目录 /opt/observability/grafana/provisioning 挂载为容器 /etc/grafana/provisioning，按官方约定分子目录：
  - `datasources/`——数据源声明（我们的 Loki 数据源在此，指向 obs-loki :15100）
  - `dashboards/`——预置看板（JSON 模型文件 + provider 声明，Hermes-Ops-Overview 在此管理）
- 加载时机：容器启动时一次性扫描；文件改动后需重启容器生效
- 与 UI 的边界：provisioning 的数据源/看板在 UI 里只读（不可改），要改就改文件——避免"UI 改了、重建丢失"的漂移

### 操作速查（SSH root@192.168.31.182）

| 动作 | 命令 |
|------|------|
| 看状态 | `docker compose -f /opt/observability/docker-compose.yml ps grafana` |
| 重启 | `docker compose -f /opt/observability/docker-compose.yml restart grafana` |
| 看容器日志 | 同机 Dozzle :15888 选 obs-grafana，或 `docker logs -f obs-grafana` |
| 进容器排查 | `docker exec -it obs-grafana bash` |
| 备份看板数据 | 拷贝 /var/lib/docker/volumes/observability_grafana-data/_data |

### 发布/升级

自部署，无流水线。实际操作步骤（SSH 到 192.168.31.182）：

1. 修改 /opt/observability/docker-compose.yml 中 grafana 镜像 tag
2. `docker compose pull grafana && docker compose up -d grafana`
3. provisioning 目录有改动时随 up -d 自动生效（部分 provisioning 变更需重启容器）
4. 升级后验证：登录 → Dashboards 看 Hermes-Ops-Overview 是否正常渲染 → Explore 查一条 Loki 日志

### 回滚

- 镜像回退：compose 中改回旧版本 tag，`docker compose up -d grafana`
- 配置回退：provisioning 目录文件改回后重启容器
- grafana-data 卷损坏：删卷重建（丢失手工建的面板与用户），provisioning 预置内容自动恢复

## 核心功能与使用

### 功能清单

| 能力 | 什么场景用 |
|------|------------|
| Hermes-Ops-Overview 仪表盘（8 面板） | 日常巡检：主机/服务/容器运行指标总览 |
| Explore + LogQL | 临时排查：按容器/主机/关键词查历史日志 |
| 仪表盘管理 | 沉淀固定视图；provisioning 预置的看板重启自动加载 |
| 告警（Grafana Alerting） | 能力具备但当前主战场在 Uptime Kuma（存活类），Grafana 侧重可视化 |

### 典型操作路径

1. **查某容器历史日志**：登录 → Explore → 数据源选 Loki → 输入 `{container="obs-uptime-kuma"}` → 设时间范围 → 流式/表格查看
2. **看总览仪表盘**：登录 → Dashboards → Hermes-Ops-Overview → 按主机/时间过滤面板
3. **新增预置数据源/看板**：SSH 编辑 /opt/observability/grafana/provisioning 下 YAML/JSON → `docker compose restart grafana`
4. **临时看某个错误趋势**：Explore → LogQL 指标查询（见下表）→ 切图表视图

### 常用 LogQL 速查（能力示例）

| 查询 | 用途 |
|------|------|
| `{container="kb-gateway"}` | 按容器查全部日志 |
| `{host="tx2"} |= "ERROR"` | 按主机 + 关键词过滤 |
| `{container="portal-server"} \| json` | 结构化解析后取字段 |
| `rate({job="docker"}[5m])` | 日志行速率（流量突增感知） |
| `sum by (host) (count_over_time({job="docker"}[15m]))` | 按主机聚合错误量 |

## 依赖与关联

- 依赖：Loki（日志数据源，obs-loki :15100，同机同 compose 项目）
- 采集侧：mykng obs-promtail 3.0.0、内网Deb obs-promtail 3.2.0、腾讯云2号 obs-promtail（journal + /var/log + docker.sock）
- 关联：infra-monitor（自研主机/服务台账，monitor.marschat.online/infra/）——Grafana 看时序，infra-monitor 看台账与配置漂移；Uptime Kuma 管存活告警

## 运维要点

### 启停 / 备份

- 启停：内网 Debian `/opt/observability/docker-compose.yml`（observability 项目），`docker compose up -d grafana` / `docker compose stop grafana`
- 日志：容器自身可被同机 obs-dozzle（:15888）直查
- 数据与备份：grafana-data 卷存看板/用户；Loki 数据独立卷；均未纳入统一备份体系，属可重建数据；重要手工看板建议导出 JSON 归档到 provisioning 目录（升级为预置看板）

### 常见问题

| 症状 | 原因 | 处理 |
|------|------|------|
| 看不到某主机日志 | 对应主机 promtail 挂了 | 查 mykng :15200 / 内网Deb / tx2 的 promtail 存活 |
| 时间线错位 | 采集端时钟漂移 | 校时（NTP）后自然恢复 |
| provisioning 改动不生效 | 文件格式错误或未重启 | 校验 YAML/JSON → `docker compose restart grafana` |
| 手工看板丢失 | grafana-data 卷被重建 | 从 provisioning 预置恢复，或养成导出 JSON 习惯 |

### Explore 使用技巧（官方能力）

- 标签浏览器：查询框左侧点 "Label browser"可看 Loki 实际存在的标签与取值，避免瞎猜标签名
- 查询历史：Explore 保留会话内查询历史，微调条件时直接回退
- 时间范围：排查时先放宽到 24h 看趋势，再收窄到分钟级看现场
- 逐行查看：日志行可展开看完整字段（json 解析后尤其有用），并可加"只显示该值"的过滤器

### 升级/变更前检查清单

- [ ] provisioning 目录改动是否已过 YAML/JSON 格式校验
- [ ] 是否需要导出手工看板 JSON 归档（grafana-data 卷不含于 provisioning）
- [ ] 升级大版本前先查官方 breaking changes（11.x 线内小版本可直接跟）
- [ ] 升级后验证两件事：Hermes-Ops-Overview 渲染正常 + Explore 能查到 Loki 新日志

## 变更记录

- 2026-09-05 v2 补全设计/部署/使用三维度（容器端口/挂载/provisioning 机制实采核验；观测栈分工表、LogQL 速查、FAQ 表整理）
- 2026-09-05 v1 首次生成（portal 文档补全任务，AI 基于实采+源码生成）
