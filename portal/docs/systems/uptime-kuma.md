# Uptime Kuma 监控告警

> 站点/服务/端口存活监控与告警通知（Uptime Kuma 1.x，18 个 monitor），覆盖全部 portal 收录系统的健康检查；观测栈里"第一时间知道谁挂了"的哨兵，部署在内网 Debian，需 Tailscale/家庭局域网访问。

## 基本信息

| 项 | 值 |
|----|----|
| 分类 | 基础设施（可观测性） |
| 版本 | louislam/uptime-kuma:1（容器实采，1.x 线） |
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
```

## 核心功能与使用

- **存活监控（18 个 monitor）**：portal 上收录的域名/系统（main / kb / nexus / woodci / vault / note / memory / tokenhub / tools / tools-test / monitor 等）+ 内部服务端口，按 HTTP(s)/TCP/关键字多类型探活
- **告警通知**：探活失败触发通知渠道（配置于系统内 Notification），恢复自动通知
- **状态页**：可发布公开/私有状态页（当前以内部使用为主）
- **证书监控**：HTTPS 域名证书到期提醒（marschat.online 全家桶统一在此盯）
- **使用建议**：portal 卡片"在线/离线"是浏览器端即时探测，仅当前会话视角；Uptime Kuma 才是 7×24 权威存活记录，二者不一致时以 Kuma 历史为准

## 依赖与关联

- 依赖：无外部中间件（SQLite 自包含）
- 关联：infra-monitor（自研，台账+配置漂移+账密登记视角）；Grafana（指标/日志可视化）——三层分工：Kuma 管存活告警、Grafana 管趋势定位、infra-monitor 管资产台账

## 运维要点

- 启停：内网 Debian /opt/observability compose 项目
- 数据与备份：uptime-kuma-data 卷内 SQLite，改 monitor/通知后建议顺手备份该卷；未纳入统一备份体系
- 日志：容器日志可由同机 Dozzle（:15888）直查
- 常见问题：告警发不出先测 Notification 测试按钮；monitor 探测目标写公网域名时注意本机 Clash 出海代理会影响探测路径（内网 Debian 7890 端口有代理监听）

## 变更记录

- 2026-09-05 首次生成（portal 文档补全任务，AI 基于实采+源码生成；monitor 数量取自 portal 卡片描述 18 个）
