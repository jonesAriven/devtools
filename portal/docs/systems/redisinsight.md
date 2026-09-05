# Redis 管理面板（RedisInsight）

> 面向 Redis 实例的 Web 可视化面板，预期提供键浏览、数据结构查看、慢查询与内存分析等运维能力。当前实采**未部署**，公网入口无真实上游。

## 基本信息

| 项 | 值 |
|----|----|
| 分类 | 工具软件（缓存/KV 管理面板） |
| 版本 | RedisInsight（**(待确认)** 当前未实采到运行中容器，版本未知） |
| 部署位置 | **(待确认)** 实采未发现运行/停止态的 redisinsight / redis-commander 容器（mykng 与内网 Debian 均无） |
| 端口 | **(待确认)** portal 表登记公网路径 `tools.marschat.online/redis/`，但实采无对应上游 |
| 后端 | platform-redis（mykng，redis:7-alpine，:6379） |
| 源码位置 | 开源组件，官方仓库 https://github.com/redisinsight/redisinsight（自部署） |
| CI/CD | 无（自部署，实采 platform 部署目录未定义该服务） |

## 访问入口 —— 实采结论：当前不可用，待确认

> ⚠️ **discrepancy（2026-09-05 实采）**：portal 登记公网入口 `https://tools.marschat.online/redis/`，但核实后：
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

## 后端 Redis 实况（已实采确认）

- 实例：`platform-redis`（mykng，镜像 redis:7-alpine），端口 3306→映射 6379（宿主 0.0.0.0:6379）。
- 数据卷：命名卷 `platform_platform-redis-data` → 容器内 `/data`（RDB/AOF 持久化目录）。
- 角色：作为 KB 体系、portal、activecode 等服务的缓存/会话/队列后端。
- 若面板恢复，连接目标为 `platform-redis:6379`（容器内网络）或 `192.168.31.105:6379`（宿主网络），依部署网络而定；需提供 Redis 密码（见 Vaultwarden）。

## 核心功能与使用（能力层面）

- 键空间浏览：按前缀查看 key、类型（string/hash/list/set/zset）与值，便于排查缓存。
- 数据结构可视化：对各类 Redis 数据结构做图形化展示与编辑。
- 慢查询/内存分析：借助 INFO、SLOWLOG 等做容量与性能分析。
- 发布订阅调试：在面板内订阅 channel 观察消息。

> 具体按钮级操作以实际部署后的面板为准，未实装界面不编造步骤。

## 依赖与关联

- 依赖：后端 Redis `platform-redis`（mykng :6379）。
- 被依赖/关联系统：KB 体系、portal、activecode 等服务以 Redis 作缓存/会话/队列，运维排查时用到本面板（若部署）。

## 运维要点

- 启停方式：**(待确认)** 当前未部署。恢复建议在 mykng 部署 RedisInsight 容器连 `platform-redis:6379`，并在 mykng 或腾讯云2号 nginx 增加 `/redis/` 反代。
- 部署参考（非当前生效，供恢复用）：RedisInsight 官方镜像 `redis/redisinsight`，置于 mykng 同网络，环境变量配置 Redis 连接串与密码；反代建议放 mykng nginx，再经腾讯云2号 `tools.marschat.online` 暴露。
- 日志查看：部署后 `docker logs <redisinsight 容器>`；obs-dozzle（mykng :15500）。
- 数据与备份：面板无状态，数据即后端 Redis；Redis 持久化（AOF/RDB）以容器卷 `platform_platform-redis-data` → `/data` 为准。
- 安全：公网暴露面板风险较高，建议加访问保护或仅 Tailscale 暴露；凭证存 Vaultwarden。

## 常见问题

- 公网 `/redis/` 当前无上游，属「未部署」预期表现。
- 连 Redis 建议用只读/监控账号，避免面板误操作 FLUSH 清空缓存。
- 恢复部署时 Redis 7 的 ACL/密码需与 `platform-redis` 实际配置一致。

## 变更记录

- 2026-09-05 首次生成（portal 文档补全任务，AI 基于实采+源码生成）；标注 redisinsight 面板实采未部署、入口待确认，并补充 Redis 后端实况
