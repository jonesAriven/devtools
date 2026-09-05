# 内网 Debian 主机

> 内网 Debian 主机（192.168.31.182 / Tailscale 100.105.196.63 / 主机名 MiWiFi-RD15-srv）的 Cockpit 管理入口 + 主机角色总览。这台机器承担"第二机房"角色：可观测性栈（Kuma/Grafana/Loki/Dozzle/Promtail）、向量与 embedding（Qdrant/BGE）、激活码生产（activecode）、FRP 管理端（frp-manager）、MySQL GR 集群 Node2/Node3。

## 基本信息

| 项 | 值 |
|----|----|
| 分类 | 基础设施（主机） |
| 系统 | Debian（Cockpit + Docker） |
| 管理入口 | Cockpit 宿主服务，端口 15090（实探 http 可访问） |
| SSH | root@192.168.31.182（凭证见 infrastructure-map 技能） |
| 主要容器 | 11 个（docker ps 实采 2026-09-05，见下表） |

## 访问入口

- 公网：—
- Cockpit 内网：`http://192.168.31.182:15090`
- Cockpit Tailscale：`http://100.105.196.63:15090`
- 账密：Linux 系统账号，见 Vaultwarden 或 infrastructure-map 技能

## 全链路

```
浏览器（家庭局域网 / Tailscale）→ http://192.168.31.182:15090 → 宿主 Cockpit
```

## 本机容器清单（实采 2026-09-05）

| 容器 | 镜像 | 宿主端口 | 用途 |
|------|------|----------|------|
| activecode | activecode:latest | 18080 | 激活码服务生产（tools.marschat.online 上游） |
| frp-manager | frp-manager:1.0.0 | 18082 | FRP 内网穿透管理端 |
| rag-qdrant | qdrant/qdrant:latest | 6333/6334 | 向量库（Hermes 记忆/知识检索） |
| rag-embedding | rag-embedding:bge-small-zh-v1.5 | 8081 | BGE 中文向量化服务 |
| obs-uptime-kuma | louislam/uptime-kuma:1 | 15001 | 存活监控+告警（18 monitor） |
| obs-grafana | grafana/grafana:11.3.0 | 15300 | 仪表盘可视化 |
| obs-loki | grafana/loki:3.2.0 | 15100 | 日志聚合存储 |
| obs-dozzle | amir20/dozzle:v8.11.7 | 15888 | 容器实时日志 |
| obs-promtail | grafana/promtail:3.2.0 | 内部 | 日志采集 → Loki |
| hive-metastore | hive:4.2.0 | 内部 9083/10000/10002 | Hive 元数据服务 |
| platform-mysql-2/3 | mysql:8.0 | 3307/3308 | MySQL GR 集群 Node2/Node3 |

## 核心功能与使用

- **Cockpit**：资源水位、systemd 服务、Web 终端、存储/网络——与 mykng Cockpit 用法一致
- **观测中枢**：Grafana（:15300）/ Loki（:15100）/ Kuma（:15001）/ Dozzle（:15888）全在这台，跨机排查从这台进
- **数据面**：MySQL GR Node2/3（deploy-mysql-cluster.sh 统一重启三节点）；Qdrant 向量存储 /var/lib/qdrant-storage 落盘
- **业务面**：激活码生产实例（18080，公网 tools.marschat.online 直达本机 Tailscale IP 100.105.196.63:18080，链路为 腾讯云2号 → 内网Deb，是少数绕过 mykng 的业务链路）

## 依赖与关联

- 关联：腾讯云2号（tools 域名反代到本机）；mykng（MySQL GR 对端、Loki 接收本机及全集群日志）
- 备份：MySQL 数据随 GR 多副本；Qdrant/Kuma/Grafana 数据卷未纳入统一备份体系

## 运维要点

- compose 项目：/opt/observability/docker-compose.yml（观测栈）、activecode/frp-manager 独立 compose
- 日志：本机容器用本机 Dozzle；journal 由本机 promtail 送 Loki
- 常见问题：Tailscale 掉线会导致 tools.marschat.online（激活码）公网不可用，Kuma 会第一时间告警

## 变更记录

- 2026-09-05 首次生成（portal 文档补全任务，AI 基于实采+源码生成）
