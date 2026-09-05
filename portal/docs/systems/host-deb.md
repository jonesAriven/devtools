# 内网 Debian 主机

> 内网 Debian 主机（192.168.31.182 / Tailscale 100.105.196.63 / 主机名 MiWiFi-RD15-srv）的主机管理入口 + 角色总览。这台机器承担"第二机房"角色：可观测性栈（Kuma/Grafana/Loki/Dozzle/Promtail）、向量与 embedding（Qdrant/BGE）、激活码生产（activecode）、FRP 管理端（frp-manager）、MySQL GR 集群 Node2/Node3。

## 基本信息

| 项 | 值 |
|----|----|
| 分类 | 基础设施（主机） |
| 系统 | Debian（Docker；Cockpit 已装但当前未运行，见下） |
| 管理入口 | SSH（主）；Cockpit :15090 当前 socket failed、无监听（2026-09-05 实采修正） |
| SSH | root@192.168.31.182（凭证见 infrastructure-map 技能） |
| 主要容器 | 11 个（docker ps 实采 2026-09-05，见下表） |

## 访问入口

- 公网：—
- Cockpit 内网：`http://192.168.31.182:15090`（2026-09-05 已修复：默认 9090 被 clash-meta 占用导致 socket failed，已加 systemd drop-in `/etc/systemd/system/cockpit.socket.d/listen.conf` 改监听 15090，实测 301 跳登录页正常）
- Cockpit Tailscale：`http://100.105.196.63:15090`（同上，随服务状态恢复）
- SSH：`root@192.168.31.182`（当前唯一稳定管理通道）
- 账密：Linux 系统账号，见 Vaultwarden 或 infrastructure-map 技能

## 全链路

```
管理：本地终端 → ssh root@192.168.31.182 → 宿主
观测：浏览器（家庭局域网 / Tailscale）→ Grafana :15300 / Kuma :15001 / Dozzle :15888 → 对应容器
业务：tools.marschat.online（公网）→ 腾讯云2号反代 → Tailscale → 本机 :18080 activecode
```

## 系统设计（主机角色）

### 定位

- **第二机房**：与主部署机 mykng 互为备份面——观测栈整体迁出 mykng（mykng 挂了监控不陪葬）、MySQL GR 副本、向量/激活码等旁路业务
- 机房网络位置：家庭内网 192.168.31.x 段；Tailscale IP 100.105.196.63；公网流量只能经腾讯云2号反代进来

### 容器矩阵（11 个，实采 2026-09-05）

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

### 关键设计决策

- **观测栈独立成 compose 项目放这台**：监控与被监控对象故障域隔离——mykng 整机故障不影响 Kuma 告警与 Grafana 查询能力
- **本机 9090 是 clash-meta 代理端口**（实采确认），与 Cockpit 无关；探测公网域名的出站路径受其影响
- **激活码链路独立**：腾讯云2号 → 本机 Tailscale IP（100.105.196.63:18080），是少数绕过 mykng 的业务链路，mykng 停机不影响 tools.marschat.online
- **MySQL GR 多副本**：Node2/Node3 在本机，与 mykng 对端互备，单机故障不丢数据

### 端口分配总表（实采）

| 端口 | 归属 | 暴露范围 |
|------|------|----------|
| 15001 | obs-uptime-kuma | 内网 + Tailscale |
| 15090 | Cockpit（当前 failed） | 内网 + Tailscale |
| 15100 | obs-loki | 内网 + Tailscale |
| 15300 | obs-grafana | 内网 + Tailscale |
| 15888 | obs-dozzle | 内网 + Tailscale |
| 18080 | activecode | 公网（经腾讯云2号反代 + Tailscale 隧道） |
| 18082 | frp-manager | 内网 + Tailscale |
| 8081 | rag-embedding | 内网 |
| 6333/6334 | rag-qdrant | 内网 |
| 3307/3308 | platform-mysql-2/3 | 内网（GR 集群） |
| 9090 | clash-meta（宿主进程，非容器） | 本机 |
| 7890 | Clash 代理端口 | 内网（影响 Kuma 探测路径） |

### 命令速查（SSH root@192.168.31.182）

| 动作 | 命令 |
|------|------|
| 列 compose 项目 | `docker compose ls` |
| 观测栈状态 | `docker compose -f /opt/observability/docker-compose.yml ps` |
| 全容器列表 | `docker ps --format '{{.Names}}\t{{.Image}}\t{{.Ports}}'` |
| 重建某容器 | `docker compose -f <compose路径> up -d <服务名>` |
| 修 Cockpit | `systemctl restart cockpit.socket`（失败查 `journalctl -u cockpit.socket`） |
| 看 journal | `journalctl -f`（同时可被本机 promtail 送 Loki） |

### 备份矩阵

| 数据 | 位置 | 备份现状 |
|------|------|----------|
| MySQL（GR Node2/3） | platform-mysql-2/3 容器 | GR 多副本天然冗余 |
| Qdrant 向量 | /var/lib/qdrant-storage 落盘 | 未纳入统一备份 |
| Kuma 配置 | observability_uptime-kuma-data 卷 | 未纳入统一备份（改配置后手工备份） |
| Grafana 看板 | observability_grafana-data 卷 + provisioning 目录 | provisioning 有文件副本，手工看板未备份 |
| Loki 日志 | observability_loki-data 卷 | 可丢失数据，不备份 |

### 与 mykng 的分工对照

| 维度 | mykng（主部署机） | 本机（第二机房） |
|------|-------------------|------------------|
| 业务主体 | kb 五件套 / portal / cosmic 等 | 激活码 / 向量 / FRP 管理端 |
| 数据库 | MySQL GR Node1 | MySQL GR Node2/3（副本） |
| 观测栈 | 只跑采集（promtail + dozzle） | 全套（Kuma/Grafana/Loki/Dozzle/Promtail） |
| 公网暴露 | 无直连（经腾讯云2号反代） | 同左，另有 tools 域名直达激活码 |
| 故障影响 | 主业务不可用 | 观测告警仍在（Kuma 在此），激活码不受影响 |

### 新服务上机 runbook

1. 确定归属：观测类进 /opt/observability；业务类按现有惯例建独立 compose 目录
2. 写 compose（端口避开上表已占用端口），`docker compose up -d`
3. 日志出口：stdout 交 promtail 采集；确认 Dozzle 能看到
4. 监控：Kuma 加 monitor（公网域名/内部端口）
5. portal 收录：卡片入口 + 描述同步
6. 凭证登记 Vaultwarden，不落文档

## 部署与发布（compose 清单，实采 docker compose ls）

| compose 项目 | compose 文件绝对路径 | 容器 |
|----|----|----|
| observability（5） | /opt/observability/docker-compose.yml | obs-promtail / obs-loki / obs-grafana / obs-uptime-kuma / obs-dozzle |
| activecode（1） | /root/devtools/active-manager/activation-code-server/docker-compose.yml | activecode |
| hive-compose（1） | /home/root01/hive-compose/docker-compose.yml | hive-metastore |
| rag-embedding（1） | /home/root01/rag-embedding/docker-compose.yml | rag-embedding |

- frp-manager、rag-qdrant、platform-mysql-2/3 未在上述四个项目中（独立容器或项目归属待确认）
- 发布方式：全部自部署无流水线；升级走"改 compose tag → `docker compose pull && docker compose up -d`"标准动作
- 回滚：compose tag 回退重建；MySQL 数据随 GR 多副本兜底

## 核心功能与使用

### 功能清单

| 能力 | 什么场景用 |
|------|------------|
| 观测中枢 | Grafana（:15300）/ Loki（:15100）/ Kuma（:15001）/ Dozzle（:15888）全在这台，跨机排查从这台进 |
| 数据面 | MySQL GR Node2/3（deploy-mysql-cluster.sh 统一重启三节点）；Qdrant 向量存储 /var/lib/qdrant-storage 落盘 |
| 业务面 | 激活码生产实例（18080，公网 tools.marschat.online 直达本机） |
| 宿主管理 | Cockpit 恢复后用法与 mykng Cockpit 一致（资源水位/服务/Web 终端/存储网络）；当前以 SSH 为准 |

### 典型操作路径

1. **跨机查日志**：打开 Grafana :15300 → Explore → Loki → 按标签查任意主机日志
2. **查本机容器实时日志**：打开 Dozzle :15888 → 选容器
3. **重启观测栈**：`ssh root@192.168.31.182 "cd /opt/observability && docker compose up -d"`
4. **MySQL GR 运维**：SSH 进本机 → 跑 deploy-mysql-cluster.sh 统一管理三节点
5. **Cockpit 已修复**（2026-09-05）：9090 被 clash-meta 占用，drop-in 改监听 15090

## 依赖与关联

- 关联：
  - 腾讯云2号（tools 域名反代到本机）
  - mykng（MySQL GR 对端、Loki 接收本机及全集群日志）
- 备份：MySQL 数据随 GR 多副本；Qdrant/Kuma/Grafana/Loki 数据卷未纳入统一备份体系

## 运维要点

### compose 与日志

- compose 项目：/opt/observability（观测栈）、activecode / hive-compose / rag-embedding 各自独立（路径见上表）
- 日志：本机容器用本机 Dozzle；journal 与 /var/log 由本机 obs-promtail 送 Loki

### 常见问题

| 症状 | 原因 | 处理 |
|------|------|------|
| Cockpit 打不开 | 端口被占（9090 被 clash-meta 占用，已 drop-in 改 15090） | restart socket；查 `journalctl -u cockpit.socket` |
| tools.marschat.online 公网不可用 | Tailscale 掉线 | Kuma 会第一时间告警；恢复 Tailscale 即恢复 |
| 观测栈整体失联 | 本机断电/断网 | 已知边界：Kuma 也在本机，整机故障时告警自身失效，依赖人工发现 |
| MySQL 单节点异常 | GR 节点故障 | 用 deploy-mysql-cluster.sh 统一重启，勿单点操作 |

## 变更记录

- 2026-09-05 v2 补全设计/部署/使用三维度；实采修正：Cockpit 当前 socket failed 入口不可用（v1 记录的"可访问"失效）；确认四个 compose 项目绝对路径；确认本机 9090 为 clash-meta
- 2026-09-05 v1 首次生成（portal 文档补全任务，AI 基于实采+源码生成）
