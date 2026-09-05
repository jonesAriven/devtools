# MySQL 管理面板（phpMyAdmin）

> 面向 MySQL Group Replication（GR）集群的 Web 管理面板，预期提供库表浏览、SQL 执行、数据导入导出等 DBA 操作界面。当前实采**未部署**，公网入口无真实上游。

## 基本信息

| 项 | 值 |
|----|----|
| 分类 | 工具软件（数据库管理面板） |
| 版本 | phpMyAdmin（**(待确认)** 当前未实采到运行中容器，版本未知） |
| 部署位置 | **(待确认)** 实采未发现运行/停止态的 phpMyAdmin 或 pma 容器（2026-09-05 复核 mykng `docker ps -a` 仍无） |
| 端口 | **(待确认)** portal 表登记公网路径 `tools.marschat.online/pma/`，但实采无对应上游 |
| 后端 | platform-mysql-1（mykng，GR 集群 Node1，:3306）；Node2/Node3 在内网 Debian（:3307 / :3308） |
| 源码位置 | 开源组件，官方仓库 https://github.com/phpmyadmin/phpmyadmin（自部署） |
| CI/CD | 无（自部署，platform 部署目录未定义该服务） |

## 访问入口 —— 实采结论：当前不可用，待确认

> ⚠️ **重要 discrepancy（2026-09-05 实采，v2 复核仍成立）**：portal 项目清单登记的公网入口为 `https://tools.marschat.online/pma/`，但逐一核实后：
> - mykng 与内网 Debian 的 `docker ps -a` 均无 phpMyAdmin / pma 容器；
> - 腾讯云2号 nginx（`/etc/nginx/sites-available/tools.marschat.online`）与 mykng nginx（`/etc/nginx/conf.d/locations/*`）均无 `/pma/` location；
> - `/root/devtools/platform/` 部署目录（grep akhq|phpmyadmin|redisinsight|mongo-express）未定义该服务。
>
> 结论：该面板**当前疑似未部署或已被移除**，公网 `/pma/` 路径无真实上游。以下入口状态以「待确认」标注，待运维确认是否需重新部署。

- 公网：`https://tools.marschat.online/pma/`（**(待确认)** 实采无对应上游，可能 404/无响应）
- 内网：**(待确认)** 未找到监听端口
- Tailscale：**(待确认)**
- 账密：MySQL GR 集群账密见 Vaultwarden（vault.marschat.online）或 infrastructure-map 技能（禁止明文落盘）

## 全链路 —— 实采结果

```
登记链路（材料/portal 表）：tools.marschat.online/pma/ → 腾讯云2号 nginx → ? (未知上游)
实采：腾讯云2号 tools.marschat.online 当前仅配置
        /activecode/ → http://100.105.196.63:18080
        /codexclaw/  → http://100.93.36.113:8080/
      以及 / → 302 /activecode/
      无 /pma/ 段落 → 链路断点，无法抵达任何 phpMyAdmin 实例
```

预期（若重新部署）后端链路应为：`phpMyAdmin → platform-mysql-1:3306（GR Node1）`，并建议指向 GR 集群读写/读节点。**(待确认)**

## 系统设计

### 组件架构

phpMyAdmin 是 PHP 实现的 MySQL/MariaDB Web 管理工具：无本地存储（无状态），通过 PHP mysqli 连接目标 MySQL，提供库表结构浏览、SQL 执行、导入导出、用户权限管理等 DBA 界面；官方以 `phpmyadmin/phpmyadmin` 镜像分发，经 `PMA_HOST`/`PMA_PORT` 等环境变量指向后端数据库。

### 我们的集成设计

- **实例角色（规划）**：作为 MySQL GR 集群的 Web 管理入口，供 DBA/运维做库表浏览与应急 SQL 操作；当前**未部署**，仅存在于 portal 登记表。
- **后端实况（已实采确认）**：MySQL Group Replication 3 节点集群——
  - Node1：`platform-mysql-1`（mykng，mysql:8.0，:3306 / 组内通信 :33061）
  - Node2：`platform-mysql-2`（内网 Debian，:3307）
  - Node3：`platform-mysql-3`（内网 Debian，:3308）
  - 部署脚本：`/root/devtools/platform/mysql/`（deploy-mysql-cluster.sh）；
  - 集群承载 `tools` 库（含 portal 的 `portal_system` 表）及 KB 等业务的库。
- **谁读写它**：所有用 MySQL 的服务（KB 体系 kb-*、portal、activecode 等）直连 GR 集群；phpMyAdmin 若恢复部署，仅作为人工管理面接入（连接目标建议单节点如 Node1 :3306；GR 多写需注意写入路由）。
- **关键设计约束**：GR 集群多写节点，面板连接单节点时读写路由需谨慎；MySQL 8.0 默认认证插件 caching_sha2_password 需确认 phpMyAdmin 版本兼容，必要时建专用账号。

## 部署与发布

### 当前状态

**(待确认/未部署)** platform 部署目录与两台宿主机均无该服务定义与容器。

### 恢复部署参考（非当前生效，供运维实施用）

- 镜像：`phpmyadmin/phpmyadmin`（官方）。
- 编排建议：在 mykng 以容器/compose 部署，加入与 `platform-mysql-1` 相同的 docker 网络（如 platform-net）。
- 关键环境变量：`PMA_HOST=platform-mysql-1`、`PMA_PORT=3306`（值按运维方案定，凭证不落盘）。
- 反代链路：mykng nginx 增加 `/pma/` location（与 minio/meilisearch 同级），再经腾讯云2号 `tools.marschat.online` 暴露；或仅 Tailscale 暴露。
- 发布：手工 `docker compose up -d`（无流水线）；回滚 = 移除容器，无状态无数据回退需求。

## 核心功能与使用（能力层面，基于 phpMyAdmin 通用能力）

### 功能清单

- 库表浏览与结构查看：查看 GR 集群中各库、表结构、索引。
- SQL 执行：在 Web 端直接执行查询/管理语句（生产环境慎用，建议走只读节点）。
- 数据导入导出：库表级 dump 与恢复（注意集群导出需排除 GTID/复制相关项以免破坏 GR）。
- 用户与权限：查看/管理 MySQL 账号（集群账号需与 GR 复制一致，谨慎操作）。

### 典型操作路径（部署后的预期用法）

1. 打开 `https://tools.marschat.online/pma/`（或内网/Tailscale 端口）→ 输入 MySQL 账号密码（见 Vaultwarden）登录。
2. 左侧选库/表 → 浏览数据、查看结构；SQL 标签页执行查询（只读操作优先）。
3. 导出：选库 → Export → 导出 dump（排除 GTID 相关项）。

> 具体按钮级操作以实际部署后的面板为准，本文不编造未实装界面的步骤。

## 依赖与关联

- 依赖：后端 MySQL GR 集群 `platform-mysql-1`（mykng :3306）、Node2/Node3（内网 Debian :3307/:3308）。
- 被依赖/关联系统：DBA/运维人工管理 MySQL 时使用；KB 体系（kb-*）、portal、activecode 等所有用 MySQL 的服务均落在该集群。

## 运维要点

- 启停：**(待确认)** 当前未部署；若恢复，启停即容器 `docker start/stop <pma 容器>`。
- 日志：部署后 `docker logs <pma 容器>`；obs-dozzle（mykng :15500）。
- 数据与备份：phpMyAdmin 本身无状态，数据即后端 MySQL；MySQL 备份以 GR 集群备份策略（见 infra-monitor）为准。
- 安全：公网暴露管理面板风险高，建议加访问保护（Basic Auth 或仅 Tailscale 暴露），凭证统一存 Vaultwarden。

## 常见问题

- 公网 `/pma/` 当前无上游，访问会落到 tools 站点的 302 兜底（跳 /activecode/）或直接无响应——属预期「未部署」表现。
- GR 集群多写节点，phpMyAdmin 连接单节点时需注意读写路由，避免跨节点写入冲突。
- 恢复部署时留意 phpMyAdmin 与 MySQL 8.0 的认证插件（caching_sha2_password）兼容性，必要时建专用账号。

## 变更记录

- 2026-09-05 v2 补全设计/部署/使用三维度；复核面板仍未部署（docker ps -a 复核），维持 v1 实采结论
- 2026-09-05 v1 首次生成（portal 文档补全任务，AI 基于实采+源码生成）；标注 pma 面板实采未部署、入口待确认，并补充 GR 集群后端实况
