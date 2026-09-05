# Kafka 管理面板（AKHQ）

> 面向 Apache Kafka 的 Web 管理面板，预期提供 Topic 浏览、消费组（Consumer Group）监控、消息查看与分区管理等运维能力。当前实采**未部署**，公网入口无真实上游。

## 基本信息

| 项 | 值 |
|----|----|
| 分类 | 工具软件（消息中间件管理面板） |
| 版本 | AKHQ（**(待确认)** 当前未实采到运行中容器，版本未知） |
| 部署位置 | **(待确认)** 实采未发现运行/停止态的 akhq 容器（2026-09-05 复核 mykng `docker ps -a` 仍无） |
| 端口 | **(待确认)** portal 表登记公网路径 `tools.marschat.online/akhq/`，但实采无对应上游 |
| 后端 | platform-kafka（mykng，apache/kafka:3.7.1，:9092） |
| 源码位置 | 开源组件，官方仓库 https://github.com/tchiotludo/akhq（自部署） |
| CI/CD | 无（自部署，platform 部署目录未定义该服务） |

## 访问入口 —— 实采结论：当前不可用，待确认

> ⚠️ **discrepancy（2026-09-05 实采，v2 复核仍成立）**：portal 登记公网入口 `https://tools.marschat.online/akhq/`，且早期材料（infra-materials 第 5 节）记录「`/akhq/ → http://100.93.36.113:8080/`」。但逐一核实后：
> - mykng 与内网 Debian `docker ps -a` 均无 akhq 容器；
> - 腾讯云2号 `tools.marschat.online` 当前配置**已无 `/akhq/` 段落**，现行配置为：
>   - `/activecode/` → http://100.105.196.63:18080
>   - `/codexclaw/`  → http://100.93.36.113:8080/
>   - `/` → 302 /activecode/
> - 进一步在 mykng 上 `ss -tlnp` 查 :8080，监听者为 **一个 python 进程（pid 2000），并非 AKHQ 容器**。即材料记录的「:8080 = AKHQ」已失效，:8080 现被 `/codexclaw/` 占用（用途待确认）。
> - `/root/devtools/platform/` 部署目录未定义该服务。
>
> 结论：AKHQ 面板**当前疑似未部署或已更名移除**，公网 `/akhq/` 无真实上游。入口状态标「待确认」，待运维确认。

- 公网：`https://tools.marschat.online/akhq/`（**(待确认)** 实采无对应上游，可能跳 /activecode/ 或无响应）
- 内网：**(待确认)** 后端 Kafka 在 `http://192.168.31.105:9092`，但面板本身未部署
- Tailscale：**(待确认)**
- 账密：Kafka/AKHQ 账密见 Vaultwarden（vault.marschat.online）或 infrastructure-map 技能（禁止明文落盘）

## 全链路 —— 实采结果

```
登记链路（材料）：tools.marschat.online/akhq/ → 腾讯云2号 nginx → mykng :8080 (AKHQ)
实采：腾讯云2号 tools.marschat.online 现行仅配置 /activecode/ 与 /codexclaw/(→:8080)
      /akhq/ 段落已不存在 → 链路断点
      mykng :8080 现由 python 进程(pid 2000) 监听，非 AKHQ
后端 Kafka 实况：mykng 容器 platform-kafka apache/kafka:3.7.1 :9092 正常运行（Up 13 days, healthy）
```

预期（若重新部署）后端链路：`AKHQ → platform-kafka:9092`（bootstrap-server）。**(待确认)**

## 系统设计

### 组件架构

AKHQ（原 KafkaHQ）是开源 Kafka 集群管理面板：通过 bootstrap-servers 接入 Kafka 集群（支持 SASL/SSL），提供 Topic 管理、Consumer Group 位移与 lag 监控、消息浏览（支持按格式反序列化）、Schema Registry/ACL 管理等能力；单容器部署无状态，配置以 application 配置（`akhq.kafka.<id>.bootstrap.servers` 等）或环境变量注入。

### 我们的集成设计

- **实例角色（规划）**：作为 `platform-kafka` 的可视化运维面板，供运维查 Topic、盯消费组 lag、排查消息堆积；当前**未部署**，仅存在于 portal 登记表。
- **后端实况（已实采确认）**：
  - 实例：`platform-kafka`（mykng，镜像 apache/kafka:3.7.1），宿主 0.0.0.0:9092（healthy，KRaft 模式单节点 controller+broker）；
  - 启动参数（platform.yml）：`KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://192.168.31.105:9092`（对外广播宿主 IP）、自动建 topic 开启、日志保留 72h / 1GiB、offsets 副本因子 1；
  - 数据卷：命名卷 `platform_platform-kafka-data` → `/var/lib/kafka/data`（另 config/secrets 匿名卷）；
  - 现有替代面板：compose 中已定义 `platform-kafka-ui`（provectuslabs/kafka-ui，宿主 19092→8080，连 `platform-kafka:9092`，调试用）——**当前已有 Kafka UI 可用，AKHQ 属于 portal 表遗留登记项**。
  - 角色：作为使用 Kafka 做异步消息/事件流的服务后端（供 platform 各业务使用）。
- **谁读写它**：使用 Kafka 的业务服务经 `192.168.31.105:9092`（advertised listener）生产/消费；AKHQ 若恢复部署，仅作为人工管理面接入（bootstrap-server 指向 `platform-kafka:9092` 容器网络或 `192.168.31.105:9092` 宿主网络）。
- **关键设计约束**：Kafka 客户端经 advertised listener `192.168.31.105:9092` 回连，面板部署位置需保证可达该地址；mykng :8080 已被 codexclaw（python 进程）占用，AKHQ 重部署须换端口。

## 部署与发布

### 当前状态

**(待确认/未部署)** platform 部署目录与两台宿主机均无该服务定义与容器。注意 platform.yml 中已有 `platform-kafka-ui`（provectuslabs/kafka-ui，宿主 19092）作为调试面板，Kafka 运维观测当前以它为准。

### 恢复部署参考（非当前生效，供运维实施用）

- 镜像：AKHQ 官方镜像（tchiotludo/akhq）。
- 编排建议：在 mykng 以容器/compose 部署，加入 platform-net。
- 关键配置：`akhq.kafka.<id>.bootstrap.servers=platform-kafka:9092`（或环境变量等价形式）；如启用 SASL/SSL 需对应凭证（见 Vaultwarden，不落盘）。
- 端口：**避开 mykng :8080（已被 codexclaw 占用）**，可另起端口（如 :8085）再反代。
- 反代链路：mykng nginx 增加 `/akhq/` location，再经腾讯云2号 `tools.marschat.online` 暴露；或仅 Tailscale 暴露。
- 发布：手工 `docker compose up -d`（无流水线）；回滚 = 移除容器，面板无状态无数据回退需求。

## 核心功能与使用（能力层面）

### 功能清单

- Topic 管理：查看/创建 Topic、分区数与副本配置、查看 Topic 内消息样例。
- 消费组监控：查看 Consumer Group 位移（offset）与消费滞后（lag），定位堆积。
- 分区与 broker 视图：查看 broker 状态、分区 leader/follower 分布。
- 数据/Schema：消息内容浏览（反序列化）、Schema Registry 与 ACL 管理入口（启用时）。

### 典型操作路径（部署后的预期用法）

1. 打开 `https://tools.marschat.online/akhq/`（或内网端口）→ 进入 cluster 视图。
2. Topics：按名筛选 → 查看 partition/offset → Data 标签浏览消息样例。
3. Groups：查 Consumer Group lag，定位堆积的消费组。
4. 当前替代方案：Kafka 运维观测走 `platform-kafka-ui`（内网 `http://192.168.31.105:19092`）。

> 具体按钮级操作以实际部署后的面板为准，未实装界面不编造步骤。

## 依赖与关联

- 依赖：后端 Kafka `platform-kafka`（mykng :9092）；如启用 SASL/SSL 还需对应凭证。
- 被依赖/关联系统：使用 Kafka 做异步消息/事件流的服务运维排查时用到本面板（若部署）；同类替代 `platform-kafka-ui` 已随 platform 栈部署。

## 运维要点

- 启停：**(待确认)** 当前未部署；若恢复，启停即容器 `docker start/stop <akhq 容器>`。
- 日志：部署后 `docker logs <akhq 容器>`；obs-dozzle（mykng :15500）。
- 数据与备份：面板无状态，数据即后端 Kafka；Kafka 数据卷 `platform_platform-kafka-data` → `/var/lib/kafka/data`（消息保留 72h/1GiB，见 platform.yml），备份以 infra-monitor 策略为准。
- 安全：公网暴露面板风险较高，建议加访问保护或仅 Tailscale 暴露；凭证存 Vaultwarden。

## 常见问题

- 公网 `/akhq/` 当前无上游，属「未部署」预期表现；勿与 `/codexclaw/`（mykng :8080 python 进程）混淆，二者不是同一服务。
- 部署 AKHQ 时 bootstrap-server 指向 `platform-kafka:9092`（容器内网络）或 `192.168.31.105:9092`（宿主网络/advertised listener），依部署网络而定。
- 注意端口冲突：现行 `/codexclaw/` 已占用 mykng :8080，AKHQ 重新部署应避免复用 :8080。
- Kafka 客户端/面板必须可达 advertised listener `192.168.31.105:9092`，否则元数据拉取失败。

## 变更记录

- 2026-09-05 v2 补全设计/部署/使用三维度；复核面板仍未部署（docker ps -a 复核），维持 v1 实采结论；补充 platform-kafka 启动参数（KRaft/advertised listener/保留策略）与 platform-kafka-ui 替代面板实况
- 2026-09-05 v1 首次生成（portal 文档补全任务，AI 基于实采+源码生成）；标注 akhq 面板实采未部署、材料记录的 :8080 上游已变更为 codexclaw（python 进程），入口待确认，并补充 Kafka 后端实况
