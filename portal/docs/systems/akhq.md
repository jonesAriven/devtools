# Kafka 管理面板（AKHQ）

> 面向 Apache Kafka 的 Web 管理面板，预期提供 Topic 浏览、消费组（Consumer Group）监控、消息查看与分区管理等运维能力。当前实采**未部署**，公网入口无真实上游。

## 基本信息

| 项 | 值 |
|----|----|
| 分类 | 工具软件（消息中间件管理面板） |
| 版本 | AKHQ（**(待确认)** 当前未实采到运行中容器，版本未知） |
| 部署位置 | **(待确认)** 实采未发现运行/停止态的 akhq 容器（mykng 与内网 Debian 均无） |
| 端口 | **(待确认)** portal 表登记公网路径 `tools.marschat.online/akhq/`，但实采无对应上游 |
| 后端 | platform-kafka（mykng，apache/kafka:3.7.1，:9092） |
| 源码位置 | 开源组件，官方仓库 https://github.com/tchiotludo/akhq（自部署） |
| CI/CD | 无（自部署，实采 platform 部署目录未定义该服务） |

## 访问入口 —— 实采结论：当前不可用，待确认

> ⚠️ **discrepancy（2026-09-05 实采）**：portal 登记公网入口 `https://tools.marschat.online/akhq/`，且早期材料（infra-materials 第 5 节）记录「`/akhq/ → http://100.93.36.113:8080/`」。但逐一核实后：
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

## 后端 Kafka 实况（已实采确认）

- 实例：`platform-kafka`（mykng，镜像 apache/kafka:3.7.1），宿主映射 0.0.0.0:9092->9092（健康状态 healthy，已运行 13 天）。
- 数据卷：
  - `platform_platform-kafka-data` → 容器内 `/var/lib/kafka/data`（消息数据）
  - 匿名卷 → `/mnt/shared/config`（配置）
  - 匿名卷 → `/etc/kafka/secrets`（密钥）
- 角色：作为使用 Kafka 做异步消息/事件流的服务后端。
- 若面板恢复，bootstrap-server 指向 `platform-kafka:9092`（容器内网络）或 `192.168.31.105:9092`（宿主网络）。

## 核心功能与使用（能力层面）

- Topic 管理：查看/创建 Topic、分区数与副本配置、查看 Topic 内消息样例。
- 消费组监控：查看 Consumer Group 的位移（offset）与消费滞后（lag），定位堆积。
- 分区与 broker 视图：查看 broker 状态、分区 leader/follower 分布。
- ACL/配置：在启用安全时为运维提供配置入口。

> 具体按钮级操作以实际部署后的面板为准，未实装界面不编造步骤。

## 依赖与关联

- 依赖：后端 Kafka `platform-kafka`（mykng :9092）；如启用 SASL/SSL 还需对应凭证。
- 被依赖/关联系统：使用 Kafka 做异步消息/事件流的服务运维排查时用到本面板（若部署）。

## 运维要点

- 启停方式：**(待确认)** 当前未部署。恢复建议在 mykng 部署 AKHQ 容器连 `platform-kafka:9092`（bootstrap-server），并在 mykng 或腾讯云2号 nginx 增加 `/akhq/` 反代（注意与现有 `/codexclaw/ → :8080` 区分端口，避免冲突）。
- 部署参考（非当前生效，供恢复用）：AKHQ 官方镜像，配置 `akhq.kafka.[id].bootstrap.servers=platform-kafka:9092`；反代建议放 mykng nginx（避免占用 :8080，可另起端口如 :8085 再经腾讯云2号 `tools.marschat.online/akhq/` 暴露）。
- 日志查看：部署后 `docker logs <akhq 容器>`；obs-dozzle（mykng :15500）。
- 数据与备份：面板无状态，数据即后端 Kafka；Kafka 数据卷 `platform_platform-kafka-data` → `/var/lib/kafka/data`（另含 config/secrets 卷）。
- 安全：公网暴露面板风险较高，建议加访问保护或仅 Tailscale 暴露；凭证存 Vaultwarden。

## 常见问题

- 公网 `/akhq/` 当前无上游，属「未部署」预期表现；勿与 `/codexclaw/`（mykng :8080 python 进程）混淆，二者不是同一服务。
- 部署 AKHQ 时 bootstrap-server 指向 `platform-kafka:9092`（容器内网络）或 `192.168.31.105:9092`（宿主网络），依部署网络而定。
- 注意端口冲突：现行 `/codexclaw/` 已占用 mykng :8080，AKHQ 重新部署应避免复用 :8080。

## 变更记录

- 2026-09-05 首次生成（portal 文档补全任务，AI 基于实采+源码生成）；标注 akhq 面板实采未部署、材料记录的 :8080 上游已变更为 codexclaw（python 进程），入口待确认，并补充 Kafka 后端实况
