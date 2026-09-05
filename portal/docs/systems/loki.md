# Loki 日志聚合

> 轻量级日志聚合存储（Loki 3.2.0），接收三台主机 Promtail 采集的 journal/文件/容器日志，供 Grafana Explore 与仪表盘用 LogQL 查询；是"Grafana 看、Promtail 采、Loki 存"日志链路的存储中枢。

## 基本信息

| 项 | 值 |
|----|----|
| 分类 | 基础设施（可观测性） |
| 版本 | grafana/loki:3.2.0（容器实采 2026-09-05） |
| 部署位置 | 内网 Debian（192.168.31.182）容器 `obs-loki` |
| 端口 | 15100（宿主）→ 容器 3100 |
| 数据卷 | 命名卷 `observability_loki-data` → /loki；配置 /opt/observability/loki/config.yml → /etc/loki/config.yml |
| 源码位置 | 开源组件，官方仓库 https://github.com/grafana/loki |
| CI/CD | 无（自部署，compose 位于内网 Debian /opt/observability/docker-compose.yml） |

## 访问入口

- 公网：—（无公网反代）
- 内网：`http://192.168.31.182:15100`（API 层面；人工查询统一走 Grafana）
- Tailscale：`http://100.105.196.63:15100`
- 账密：无鉴权（内网监听），Grafana 侧账密见 Vaultwarden

## 全链路

采集与查询两条链路：

```
采集：mykng / 内网Deb / 腾讯云2号 三台主机 obs-promtail
  → 推送 http://192.168.31.182:15100（Loki /loki/api/v1/push）
  → 容器 obs-loki (:3100)

查询：浏览器 → Grafana (192.168.31.182:15300)
  → Explore/看板 → 内部调用 obs-loki (:15100)
```

## 系统设计

### 组件架构

Loki 是 Grafana 家族的日志聚合系统，官方能力要点：

- 核心思路：**只索引标签（label），不索引正文**——日志按标签集（如 container、host、job）分流入库，查询时先用标签缩小范围，再对范围内日志暴力过滤内容
- 存储模型：索引（标签→chunk 映射）与 chunk（压缩日志块）分离，底层可接本地文件系统/对象存储；我们实例用本地数据卷
- 查询语言 LogQL：标签选择器 + 管道过滤（`|=` / `!=` / 正则）+ 结构化解析（`| json` / `| logfmt`）+ 指标函数（rate / count_over_time / bytes_over_time）
- 部署模式：monolithic（单进程全组件）/ simple-scalable / microservices 三档；我们实例为单机 monolithic
- 接收端：`/loki/api/v1/push`，Promtail/Promtail 兼容采集端均可推

### 我们的集成设计

- **实例角色**：全集群日志的集中存储层；单机单实例（monolithic 模式），不拆组件
- **数据连接**：
  - 入：三台主机 obs-promtail 推送——mykng（100.93.36.113）、内网Deb（100.105.196.63）、腾讯云2号（100.110.114.16），覆盖 systemd journal、/var/log、Docker 容器 stdout
  - 出：Grafana（同机 obs-grafana）作为唯一查询前端
- **为什么选它**：与 Grafana 原生集成；Promtail 部署轻（一台一个容器）；标签模型正好匹配我们"按主机/容器查"的排查习惯；存储成本远低于 ELK，符合小规模家庭机房
- **关键配置思路**：/opt/observability/loki/config.yml 集中管理——retention 保留期、存储路径（/loki 数据卷）在此调整；无鉴权仅内网监听，Tailscale 之外不可达

### 三台采集端一览

| 主机 | 采集端 | 版本 | 采集范围 |
|------|--------|------|----------|
| mykng（100.93.36.113） | obs-promtail | 3.0.0 | journal / 容器 stdout（:15200） |
| 内网Deb（100.105.196.63） | obs-promtail | 3.2.0 | journal / /var/log / 容器 stdout（内部） |
| 腾讯云2号（100.110.114.16） | obs-promtail | — | journal + /var/log + docker.sock（127.0.0.1:15200） |

### obs-promtail 采集实现（内网Deb 实采挂载）

内网Deb 的 obs-promtail 容器挂载（docker inspect 实采）揭示了采集实现方式：

| 挂载（宿主 → 容器） | 用途 |
|----------------------|------|
| /run/log/journal → /run/log/journal | 读 systemd journal 二进制日志 |
| /var/lib/docker/containers → /var/lib/docker/containers | 读容器 stdout 日志文件（json-file 驱动） |
| /var/log → /var/log | 读宿主文件日志（含 nginx 等） |
| /var/run/docker.sock → /var/run/docker.sock | Docker API 发现容器与元数据（打标签） |
| /etc/machine-id → /etc/machine-id | 标识主机身份（host 标签来源） |
| /opt/observability/promtail/config.yml → /etc/promtail/config.yml | 采集配置（relabel/管道/推送目标） |

> 其余两台主机 promtail 同理（tx2 版本为 127.0.0.1:15200 仅本机监听）。新主机接入 = 照此挂载 + 推送目标指向 :15100。

## 部署与发布

### 编排与位置

- compose 文件：`/opt/observability/docker-compose.yml`（内网 Debian 192.168.31.182）
- compose project：`observability`（同项目共 5 容器，`docker compose ls` 实采确认）
- 容器名：`obs-loki`，镜像 `grafana/loki:3.2.0`

### 配置清单（实采）

| 项 | 值 |
|----|----|
| 端口映射 | 宿主 15100 → 容器 3100 |
| 卷挂载 1 | /opt/observability/loki/config.yml → /etc/loki/config.yml（主配置） |
| 卷挂载 2 | 命名卷 observability_loki-data → /loki（索引 + chunk 数据） |

### 发布/升级

自部署，无流水线。实际操作步骤（SSH 到 192.168.31.182）：

1. 改 compose 中 loki 镜像 tag
2. `docker compose pull loki && docker compose up -d loki`
3. 确认三台 promtail 重新连上（Grafana Explore 能查到新日志即通）

### 回滚

- 镜像回退：compose 改回旧 tag 后 `docker compose up -d loki`
- 配置回退：config.yml 恢复备份后 `docker compose restart loki`
- loki-data 卷损坏即日志丢失（属可丢失数据）：重建卷 + 重启采集端即可恢复链路，历史不可回补

## 核心功能与使用

### 功能清单

| 能力 | 什么场景用 |
|------|------------|
| 三机日志集中存储 | 跨机排查不再逐台 SSH grep，一个入口看全部 |
| LogQL 标签检索 | 按容器/主机/关键词快速定位历史日志 |
| LogQL 指标化 | 看日志量/错误率趋势（rate、count_over_time） |
| Grafana 看板沉淀 | 把常用查询固化为面板，进入日常巡检 |

### 典型操作路径

1. **查历史日志**：登录 Grafana（:15300）→ Explore → Loki → 标签选择器选 host/container → 加时间范围 → 过滤关键词
2. **加新主机采集**：新主机跑 promtail 指向 :15100 → 确认 /loki 入库 → Grafana 侧按新标签查询
3. **调保留期**：SSH 编辑 /opt/observability/loki/config.yml 的 retention 段 → `docker compose restart loki`

### 常用 LogQL 速查（能力示例）

| 查询 | 用途 |
|------|------|
| `{container="kb-gateway"}` | 按容器查全部日志 |
| `{host="tx2"} |= "ERROR"` | 按主机 + 关键词过滤 |
| `{container="portal-server"} | json` | 结构化解析后取字段 |
| `sum by (host) (rate({job="docker"}[5m]))` | 按主机聚合日志速率 |
| `bytes_over_time({container="obs-loki"}[1h])` | 单容器日志量趋势 |

### 容量与 retention 机制（官方能力 + 实例边界）

- Loki chunk 按时间/大小滚动压缩落盘到 /loki 数据卷；retention（保留期）到期的数据由 compactor 清理
- 我们实例日志量级为家用规模，磁盘压力主要来自容器 stdout 大户（构建/网关类）
- retention 调整入口唯一：/opt/observability/loki/config.yml；改完必须重启容器生效
- 判断磁盘压力：`docker system df -v` 看卷占用 + Grafana 里 `bytes_over_time` 看增速

### 标签约定（从实例查询反推的常用标签集）

| 标签 | 含义 | 示例 |
|------|------|------|
| container | Docker 容器名 | `{container="kb-gateway"}` |
| host | 来源主机 | `{host="tx2"}` |
| job | 采集任务类别 | `{job="docker"}` |

- 查询不到先怀疑标签拼写：用 Grafana 的 Label browser 确认实际标签集
- 新采集端接入时沿用此约定（host 用主机名/编号，job 区分 journal/docker/文件），保证跨机查询习惯一致

### 操作速查（SSH root@192.168.31.182）

| 动作 | 命令 |
|------|------|
| 看状态 | `docker compose -f /opt/observability/docker-compose.yml ps loki` |
| 重启 | `docker compose -f /opt/observability/docker-compose.yml restart loki` |
| 看容器日志 | 同机 Dozzle :15888 选 obs-loki |
| 探活 API | `curl -s http://192.168.31.182:15100/ready` |
| 查磁盘占用 | `docker system df -v` |

### 排查场景（实际用过的链路）

- portal 状态"离线"定位：查对应容器最后输出，对照 Kuma 心跳时间点
- 流水线部署失败：回看 deploy 脚本输出与容器启动日志
- 生产日志滚动异常跨机比对（日志文件名与内容日期不匹配问题的排查即用此链路）

## 依赖与关联

- 被依赖：Grafana（唯一查询入口）
- 采集侧：各主机 obs-promtail（mykng 3.0.0 :15200、deb 3.2.0 内部、tx2 127.0.0.1:15200）
- 关联：Dozzle（临时直看单容器日志，无需 LogQL）；两者互补——Dozzle 快、Loki 存历史可跨机

## 运维要点

### 启停 / 备份

- 启停：内网 Debian /opt/observability compose 项目，`docker compose up -d loki` / `stop loki`
- 配置：/opt/observability/loki/config.yml（retention/存储路径在此调整）
- 数据与备份：loki-data 卷未纳入统一备份，日志属可丢失数据；容量告急优先调 retention 而非扩盘

### 常见问题

| 症状 | 原因 | 处理 |
|------|------|------|
| 某段时间日志缺失 | promtail 断连，Loki 不回补 | 重连后只收新日志；历史空洞属已知边界 |
| 429/500 拒收 | 磁盘满 | 清 retention / 清旧 chunk → 重启采集端 |
| 时间线错位 | 采集端时钟漂移 | 校时后自然恢复 |
| 标签查不到 | 标签名记错 | Explore 里用标签浏览器确认实际标签集 |

## 变更记录

- 2026-09-05 v2 补全设计/部署/使用三维度（容器端口/挂载/配置路径实采核验；采集端一览表、LogQL 速查、FAQ 表整理）
- 2026-09-05 v1 首次生成（portal 文档补全任务，AI 基于实采+源码生成）
