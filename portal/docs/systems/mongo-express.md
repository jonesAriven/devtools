# MongoDB 管理面板（mongo-express）

> 面向 MongoDB 实例的轻量 Web 管理面板，预期提供库/集合浏览、文档查看与简单编辑等能力。当前实采**未部署**，公网入口无真实上游。

## 基本信息

| 项 | 值 |
|----|----|
| 分类 | 工具软件（文档数据库管理面板） |
| 版本 | mongo-express（**(待确认)** 当前未实采到运行中容器，版本未知） |
| 部署位置 | **(待确认)** 实采未发现运行/停止态的 mongo-express 容器（2026-09-05 复核 mykng `docker ps -a` 仍无） |
| 端口 | **(待确认)** portal 表登记公网路径 `tools.marschat.online/mongo/`，但实采无对应上游 |
| 后端 | platform-mongo（mykng，mongo:7.0，:27017） |
| 源码位置 | 开源组件，官方仓库 https://github.com/mongo-express/mongo-express（自部署） |
| CI/CD | 无（自部署，platform 部署目录未定义该服务） |

## 访问入口 —— 实采结论：当前不可用，待确认

> ⚠️ **discrepancy（2026-09-05 实采，v2 复核仍成立）**：portal 登记公网入口 `https://tools.marschat.online/mongo/`，但核实后：
> - mykng 与内网 Debian `docker ps -a` 均无 mongo-express 容器；
> - 腾讯云2号与 mykng nginx 均无 `/mongo/` location；
> - `/root/devtools/platform/` 部署目录未定义该服务。
>
> 结论：该面板**当前疑似未部署或已移除**，公网 `/mongo/` 无真实上游。入口状态标「待确认」，待运维确认是否需重新部署。

- 公网：`https://tools.marschat.online/mongo/`（**(待确认)** 实采无对应上游）
- 内网：**(待确认)** 后端 Mongo 在 `http://192.168.31.105:27017`，但面板本身未部署
- Tailscale：**(待确认)**
- 账密：MongoDB 账密见 Vaultwarden（vault.marschat.online）或 infrastructure-map 技能（禁止明文落盘）

## 全链路 —— 实采结果

```
登记链路：tools.marschat.online/mongo/ → 腾讯云2号 nginx → ? (未知上游)
实采：腾讯云2号 tools.marschat.online 仅配置
        /activecode/ → http://100.105.196.63:18080
        /codexclaw/  → http://100.93.36.113:8080/
      无 /mongo/ 段落 → 链路断点
后端 Mongo 实况：mykng 容器 platform-mongo :27017 正常运行（docker ps 确认，mongo:7.0）
```

预期（若重新部署）后端链路：`mongo-express → platform-mongo:27017`。**(待确认)**

## 系统设计

### 组件架构

mongo-express 是基于 Node.js/Express 的 MongoDB 轻量 Web 管理面板：无状态接入目标 Mongo 实例，提供 database/collection 浏览、文档 JSON 查看与简单增删改、索引查看等能力；经 `ME_CONFIG_MONGODB_URL` 连接串（或分项环境变量）指向后端，官方 Docker 镜像分发，可配 Basic Auth 保护面板本身。

### 我们的集成设计

- **实例角色（规划）**：作为 `platform-mongo` 的可视化运维面板，供运维浏览库/集合、查看文档；当前**未部署**，仅存在于 portal 登记表。
- **后端实况（已实采确认）**：
  - 实例：`platform-mongo`（mykng，镜像 mongo:7.0），宿主映射 0.0.0.0:27017->27017；
  - 启动参数（platform.yml）：`--wiredTigerCacheSizeGB=0.25`（限内存缓存）、`MONGO_INITDB_ROOT_USERNAME/PASSWORD` 初始化 root 账号（值见 Vaultwarden）；
  - 数据卷：命名卷 `platform_platform-mongo-data` → `/data/db`（数据），另匿名卷 → `/data/configdb`（配置）；
  - 角色：使用 MongoDB 存储的业务服务（如部分 KB/工具数据）的文档数据库后端。
- **谁读写它**：使用 Mongo 的业务服务经内网 :27017 读写；mongo-express 若恢复部署，仅作为人工管理面接入（连接目标 `platform-mongo:27017` 容器网络或 `192.168.31.105:27017` 宿主网络，需 Mongo 账号密码）。
- **关键设计约束**：Mongo 7 默认启用认证，mongo-express 连接串需带账密且与实例鉴权方式对齐；面板支持文档编辑，公网暴露需 Basic Auth 或仅 Tailscale。

## 部署与发布

### 当前状态

**(待确认/未部署)** platform 部署目录与两台宿主机均无该服务定义与容器。

### 恢复部署参考（非当前生效，供运维实施用）

- 镜像：mongo-express 官方镜像。
- 编排建议：在 mykng 以容器/compose 部署，加入与 `platform-mongo` 相同的 docker 网络（如 platform-net）。
- 关键环境变量：`ME_CONFIG_MONGODB_URL=mongodb://<user>:<pass>@platform-mongo:27017`（连接串含凭证，实际以环境注入，禁止落盘）。
- 反代链路：mykng nginx 增加 `/mongo/` location，再经腾讯云2号 `tools.marschat.online` 暴露；或仅 Tailscale 暴露。
- 发布：手工 `docker compose up -d`（无流水线）；回滚 = 移除容器，面板无状态无数据回退需求。

## 核心功能与使用（能力层面）

### 功能清单

- 库/集合浏览：查看 database 与 collection 列表、文档数量。
- 文档查看与编辑：Web 端查看 JSON 文档、简单增删改（复杂写操作建议在客户端完成）。
- 索引查看：查看集合索引定义。

### 典型操作路径（部署后的预期用法）

1. 打开面板（公网 `/mongo/` 或内网端口，若配 Basic Auth 先过面板认证）→ 顶部选 database。
2. 左侧选 collection → 浏览/搜索文档，点开查看 JSON。
3. 需要编辑时在面板内做小改动；批量/复杂操作走 mongosh 或业务代码。

> 具体按钮级操作以实际部署后的面板为准，未实装界面不编造步骤。

## 依赖与关联

- 依赖：后端 MongoDB `platform-mongo`（mykng :27017）。
- 被依赖/关联系统：使用 MongoDB 存储的业务服务（如部分 KB/工具数据）运维排查时用到本面板（若部署）。

## 运维要点

- 启停：**(待确认)** 当前未部署；若恢复，启停即容器 `docker start/stop <mongo-express 容器>`。
- 日志：部署后 `docker logs <mongo-express 容器>`；obs-dozzle（mykng :15500）。
- 数据与备份：面板无状态，数据即后端 Mongo；Mongo 数据卷 `platform_platform-mongo-data` → `/data/db`（另含 configdb 卷），备份以 infra-monitor 策略为准。
- 安全：公网暴露面板风险较高，建议加访问保护（Basic Auth）或仅 Tailscale 暴露；凭证存 Vaultwarden。

## 常见问题

- 公网 `/mongo/` 当前无上游，属「未部署」预期表现。
- mongo-express 对认证/SSL 的连接串配置较挑剔，部署时需与 `platform-mongo` 的鉴权方式对齐（Mongo 7 默认启用认证）。

## 变更记录

- 2026-09-05 v2 补全设计/部署/使用三维度；复核面板仍未部署（docker ps -a 复核），维持 v1 实采结论；补充 platform-mongo 启动参数（wiredTiger 限额/初始化账号）实况
- 2026-09-05 v1 首次生成（portal 文档补全任务，AI 基于实采+源码生成）；标注 mongo-express 面板实采未部署、入口待确认，并补充 Mongo 后端实况
