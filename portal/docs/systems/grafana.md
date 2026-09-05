# Grafana 仪表盘

> 日志/指标可视化平台，承载 Hermes-Ops-Overview 等仪表盘，与 Loki（日志）、Promtail（采集）、自研 infra-monitor（主机台账）组成可观测性栈的可视化层；部署在内网 Debian 上，需 Tailscale/家庭局域网访问。

## 基本信息

| 项 | 值 |
|----|----|
| 分类 | 基础设施（可观测性） |
| 版本 | grafana/grafana:11.3.0（容器实采） |
| 部署位置 | 内网 Debian（192.168.31.182）容器 `obs-grafana` |
| 端口 | 15300（宿主）→ 容器 3000 |
| 数据卷 | 命名卷 `observability_grafana-data` → /var/lib/grafana；配置预置目录 /opt/observability/grafana/provisioning → /etc/grafana/provisioning |
| 源码位置 | 开源组件，官方仓库 https://github.com/grafana/grafana |
| CI/CD | 无（自部署，compose 位于内网 Debian /opt/observability/docker-compose.yml） |

## 访问入口

- 公网：—（未配置公网反代，属内网观测资产，不放公网）
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

## 核心功能与使用

- **Hermes-Ops-Overview 仪表盘**：8 面板总览（portal 卡片描述），展示主机/服务/容器运行指标
- **Loki 日志查询**：Explore → Loki 数据源，用 LogQL 按容器/主机/关键词查日志（promtail 从 mykng、内网Deb、腾讯云2号 三台采集）
- **仪表盘管理**：provisioning 目录预置数据源/看板，重启自动加载；手工建的看板存 /var/lib/grafana 数据卷
- **告警**：Grafana Alerting 能力具备，当前告警主战场在 Uptime Kuma（存活类），Grafana 侧重可视化

## 依赖与关联

- 依赖：Loki（日志数据源，obs-loki :15100）
- 采集侧：mykng obs-promtail 3.0.0、内网Deb obs-promtail 3.2.0、腾讯云2号 obs-promtail（journal + /var/log + docker.sock）
- 关联：infra-monitor（自研主机/服务台账，monitor.marschat.online/infra/）——Grafana 看时序，infra-monitor 看台账与配置漂移

## 运维要点

- 启停：内网 Debian `/opt/observability/docker-compose.yml`（observability 项目）
- 日志：容器自身可被同机 obs-dozzle（:15888）直查
- 数据与备份：grafana-data 卷存看板/用户；Loki 数据独立卷，未纳入统一备份体系，属可重建数据
- 常见问题：看不到日志先查对应主机 promtail 是否存活（mykng :15200 / deb / tx2）；时钟不同步会导致时间线错位

## 变更记录

- 2026-09-05 首次生成（portal 文档补全任务，AI 基于实采+源码生成）
