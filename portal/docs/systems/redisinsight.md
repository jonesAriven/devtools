# Redis 管理面板（RedisInsight）

> 面向 Redis 实例的 Web 可视化面板，预期提供键浏览、数据结构查看、慢查询与内存分析等运维能力。当前实采**未部署**，公网入口无真实上游。

## 基本信息

| 项 | 值 |
|----|----|
| 分类 | 工具软件（缓存/KV 管理面板） |
| 版本 | RedisInsight（**(待确认)** 当前未实采到运行中容器，版本未知） |
| 部署位置 | **(待确认)** 实采未发现运行/停止态的 redisinsight / redis-commander 容器（2026-09-05 复核 mykng `docker ps -a` 仍无） |
| 端口 | **(待确认)** portal 表登记公网路径 `tools.marschat.online/redis/`，但实采无对应上游 |
| 后端 | platform-redis（mykng，redis:7-alpine，:6379） |
| 源码位置 | 开源组件，官方仓库 https://github.com/redis/RedisInsight（自部署） |
| CI/CD | 无（自部署，platform 部署目录未定义该服务） |

## 访问入口 —— 实采结论：当前不可用，待确认

> ⚠️ **discrepancy（2026-09-05 实采，v2 复核仍成立）**：portal 登记公网入口 `https://tools.marschat.online/redis/`，但核实后：
> - mykng 与内网 Debian `docker ps -a` 均无 redisinsight / redis-commander 容器；
> - 腾讯云2号与 mykng nginx 均无 `/redis/` location；
> - `/root/devtools/platform/` 部署目录未定义该服务。
>
> 结论：该面板**当前疑似未部署或已移除**，公网 `/redis/` 无真实上游。入口状态标「待确认」，待运维确认是否需重新部署。

- 公网：`https://tools.marschat.online/redis/`（**(待确认)** 实采无对应上游）
- 内网：**(待确认)** 后端 Redis 在 `http://192.168.31.105:6379`，但面板本身未部署
- Tailscale：**(待确认)**
- 账密：Redis 密码见 Vaultwarden（vault.marschat.online）或 infrastructure-map 技能（禁止明文落盘）

## 全链路 —— 实采结果

```
登记链路：tools.marschat.online/redis/ → 腾讯云2号 nginx → ? (未知上游)
实采：腾讯云2号 tools.marschat.online 仅配置
        /activecode/ → http://100.105.196.63:18080
        /codexclaw/  → http://100.93.36.113:8080/
      无 /redis/ 段落 → 链路断点
后端 Redis 实况：mykng 容器 platform-redis :6379 正常运行（docker ps 确认，redis:7-alpine）
```

预期（若重新部署）后端链路：`RedisInsight → platform-redis:6379`。**(待确认)**

## 系统设计

### 组件架构

RedisInsight 是 Redis 官方（Redis Ltd.）的桌面/容器化 GUI 管理工具：无状态接入任意 Redis 实例，提供键空间浏览、各数据结构（string/hash/list/set/zset/stream 等）可视化编辑、SLOWLOG/内存分析、Pub/Sub 调试与 CLI 终端；官方镜像 `redis/redisinsight`，Web 服务默认监听单端口，连接信息在界面内配置。

### 我们的集成设计

- **实例角色（规划）**：作为 `platform-redis` 的可视化运维面板，供运维排查缓存键、观察内存与慢查询；当前**未部署**，仅存在于 portal 登记表。
- **后端实况（已实采确认）**：
  - 实例：`platform-redis`（mykng，镜像 redis:7-alpine），宿主 0.0.0.0:6379；
  - 启动参数（platform.yml）：`redis-server --appendonly yes --maxmemory 256mb --maxmemory-policy allkeys-lru`（AOF 持久化 + LRU 淘汰）；
  - 数据卷：命名卷 `platform_platform-redis-data` → 容器内 `/data`；
  - 角色：KB 体系、portal、activecode 等服务的缓存/会话/队列后端。
- **谁读写它**：KB、portal、activecode 等服务经内网 :6379 读写 Redis；RedisInsight 若恢复部署，仅作为人工只读/调试入口接入（连接目标 `platform-redis:6379` 容器内网络或 `192.168.31.105:6379` 宿主网络，需 Redis 密码）。
- **关键设计约束**：面板建议使用只读/监控账号，避免误操作 FLUSH 清空共享缓存；Redis 7 的 ACL/密码需与 `platform-redis` 实际配置一致。

## 部署与发布

### 当前状态

**(待确认/未部署)** platform 部署目录与两台宿主机均无该服务定义与容器。

### 恢复部署参考（非当前生效，供运维实施用）

- 镜像：`redis/redisinsight`（官方）。
- 编排建议：在 mykng 以容器/compose 部署，加入与 `platform-redis` 相同的 docker 网络（如 platform-net）。
- 连接：界面内配置 Redis 连接串指向 `platform-redis:6379`（容器网络）或 `192.168.31.105:6379`（宿主网络），密码见 Vaultwarden（不落盘）。
- 反代链路：mykng nginx 增加 `/redis/` location，再经腾讯云2号 `tools.marschat.online` 暴露；或仅 Tailscale 暴露。
- 发布：手工 `docker compose up -d`（无流水线）；回滚 = 移除容器（面板无状态，连接配置可随容器卷保留）。

## 核心功能与使用（能力层面）

### 功能清单

- 键空间浏览：按前缀查看 key、类型与值，便于排查缓存。
- 数据结构可视化：对各类 Redis 数据结构做图形化展示与编辑。
- 慢查询/内存分析：借助 INFO、SLOWLOG 等做容量与性能分析。
- 发布订阅调试：在面板内订阅 channel 观察消息。

### 典型操作路径（部署后的预期用法）

1. 打开面板 → 新增 Redis Connection（host/port/密码）→ 保存连接。
2. 键空间：按 pattern 搜索 key → 查看类型/TTL/值（排查缓存命中与残留）。
3. 分析：Workbench/CLI 执行 `INFO memory`、`SLOWLOG GET` 评估性能。

> 具体按钮级操作以实际部署后的面板为准，未实装界面不编造步骤。

## 依赖与关联

- 依赖：后端 Redis `platform-redis`（mykng :6379）。
- 被依赖/关联系统：KB 体系、portal、activecode 等服务以 Redis 作缓存/会话/队列，运维排查时用到本面板（若部署）。

## 运维要点

- 启停：**(待确认)** 当前未部署；若恢复，启停即容器 `docker start/stop <redisinsight 容器>`。
- 日志：部署后 `docker logs <redisinsight 容器>`；obs-dozzle（mykng :15500）。
- 数据与备份：面板无状态，数据即后端 Redis；Redis 持久化（AOF）以容器卷 `platform_platform-redis-data` → `/data` 为准（platform.yml 已开 appendonly）。
- 安全：公网暴露面板风险较高，建议加访问保护或仅 Tailscale 暴露；凭证存 Vaultwarden。

## 常见问题

- 公网 `/redis/` 当前无上游，属「未部署」预期表现。
- 连 Redis 建议用只读/监控账号，避免面板误操作 FLUSH 清空共享缓存。
- 恢复部署时 Redis 7 的 ACL/密码需与 `platform-redis` 实际配置一致。

## 变更记录

- 2026-09-05 v2 补全设计/部署/使用三维度；复核面板仍未部署（docker ps -a 复核），维持 v1 实采结论；补充 platform-redis 启动参数（AOF/LRU）实况
- 2026-09-05 v1 首次生成（portal 文档补全任务，AI 基于实采+源码生成）；标注 redisinsight 面板实采未部署、入口待确认，并补充 Redis 后端实况
