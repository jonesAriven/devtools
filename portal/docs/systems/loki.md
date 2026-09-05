# Loki 日志聚合

> 轻量级日志聚合存储（Loki 3.2.0），接收三台主机 Promtail 采集的 journal/文件/容器日志，供 Grafana Explore 与仪表盘用 LogQL 查询；是"Grafana 看、Promtail 采、Loki 存"日志链路的存储中枢。

## 基本信息

| 项 | 值 |
|----|----|
| 分类 | 基础设施（可观测性） |
| 版本 | grafana/loki:3.2.0（容器实采） |
| 部署位置 | 内网 Debian（192.168.31.182）容器 `obs-loki` |
| 端口 | 15100（宿主）→ 容器 3100 |
| 数据卷 | 命名卷 `observability_loki-data` → /loki；配置 /opt/observability/loki/config.yml → /etc/loki/config.yml |
| 源码位置 | 开源组件，官方仓库 https://github.com/grafana/loki |
| CI/CD | 无（自部署，compose 位于内网 Debian /opt/observability/docker-compose.yml） |

## 访问入口

- 公网：—（无公网反代）
- 内网：`http://192.168.31.182:15100`（API 层面；人工查询统一走 Grafana）
- Tailscale：`http://100.105.196.63:15100`
- 账密：无鉴权（内网监听），Grafana 侧见 Vaultwarden

## 全链路

采集与查询两条链路：

```
采集：mykng / 内网Deb / 腾讯云2号 三台主机 obs-promtail
  → 推送 http://192.168.31.182:15100（Loki /loki/api/v1/push）
  → 容器 obs-loki (:3100)

查询：浏览器 → Grafana (192.168.31.182:15300)
  → Explore/看板 → 内部调用 obs-loki (:15100)
```

## 核心功能与使用

- **日志集中存储**：三台主机（mykng 100.93.36.113、内网Deb 100.105.196.63、腾讯云2号 100.110.114.16）的 systemd journal、/var/log、Docker 容器 stdout 全部汇聚
- **LogQL 查询**：在 Grafana Explore 按 `{container="kb-gateway"}`、`{host="tx2"}` 等标签检索，支持管道过滤与指标化（rate/bytes_over_time）
- **排查场景**：portal 状态"离线"定位、流水线部署失败回看部署日志、生产日志滚动异常跨机比对（日志文件名与内容日期不匹配问题排查时即用此链路）

## 依赖与关联

- 被依赖：Grafana（唯一查询入口）
- 采集侧：各主机 obs-promtail（mykng 3.0.0 :15200、deb 3.2.0 内部、tx2 127.0.0.1:15200）
- 关联：Dozzle（临时直看单容器日志，无需 LogQL）；两者互补——Dozzle 快、Loki 存历史可跨机

## 运维要点

- 启停：内网 Debian /opt/observability compose 项目
- 配置：/opt/observability/loki/config.yml（retention/存储路径在此调整）
- 数据与备份：loki-data 卷，未纳入统一备份，日志属可丢失数据，容量告急优先调 retention
- 常见问题：promtail 断连后日志有洞；Loki 磁盘满会拒收（报 429/500），先清 retention 再重启采集端

## 变更记录

- 2026-09-05 首次生成（portal 文档补全任务，AI 基于实采+源码生成）
