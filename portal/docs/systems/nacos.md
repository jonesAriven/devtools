# Nacos 服务中心

> 服务注册与配置中心，承载 mykng 上所有 Spring Boot 微服务（kb-* 五件套、portal-server、kb-ops、infra-monitor、active-manager 等）的服务发现与配置管理，是 devtools 微服务体系的注册中枢。

## 基本信息

| 项 | 值 |
|----|----|
| 分类 | 基础设施（微服务治理） |
| 版本 | nacos/nacos-server:v2.4.3（portal 卡片写 2.3.x 已过期，以实采为准） |
| 部署位置 | mykng（192.168.31.105）容器 `platform-nacos` |
| 端口 | 8848（控制台/OpenAPI）、9848（gRPC 客户端，v2 必需） |
| 源码位置 | 开源组件，官方仓库 https://github.com/alibaba/nacos |
| CI/CD | 无（platform 层自部署，随 `python woodScript/trigger-pipeline.py platform` 一并拉起） |

## 访问入口

- 公网：`https://kb.marschat.online/nacos/`
- 内网：`http://192.168.31.105:8848/nacos/` 或 `http://192.168.31.105/nacos/`
- Tailscale：`http://100.93.36.113:8848/nacos/`
- 控制台账密：见 Vaultwarden（vault.marschat.online）或 infrastructure-map 技能

## 全链路

```
浏览器 → https://kb.marschat.online/nacos/
  → 腾讯云2号 nginx (443, TLS 终止) → http://100.93.36.113:80
  → mykng nginx /nacos/ → proxy_pass http://127.0.0.1:8848/nacos/
  → 容器 platform-nacos (:8848)
```

> 注意：该链路只覆盖 8848 控制台/OpenAPI；微服务客户端走 gRPC 9848 端口直连内网/Tailscale，不经 nginx。

## 核心功能与使用

- **服务注册与发现**：kb-gateway / kb-auth / kb-file / kb-knowledge / kb-intelligence 等微服务启动后自动注册，Spring Cloud LoadBalancer 按服务名寻址
- **配置中心**：各服务 `bootstrap.yml` 指向 Nacos 拉取配置；改配置后可对订阅服务下发刷新（配合 `@RefreshScope`）
- **命名空间/分组**：dev 与 prod 环境隔离（按各服务实际接入情况确认，控制台可见）
- **控制台**：服务列表（实例健康状态）、配置编辑、监听查询

## 依赖与关联

- 依赖：platform-mysql（持久化，GR 集群内）
- 被依赖：mykng 全部 Spring Boot 微服务（注册+配置）；kb-ops、infra-monitor、portal-server 同样接入
- 关联：Spring Boot 3.x 接入用 Nacos 2.x client；gRPC 双端口（8848+9848 偏移 +1000）是 v2 特性，防火墙只开 8848 会导致客户端注册失败

## 运维要点

- 启停：platform 层 compose 管理；`docker restart platform-nacos` 应急（重启不影响已注册客户端的本地缓存，恢复后自动重连）
- 日志：`docker logs platform-nacos`；容器级日志也可在 Dozzle (mykng) 中查看
- 数据备份：配置数据在 MySQL tools/platform 库中，随 MySQL GR 集群三副本容灾
- 常见问题：客户端连不上多因 9848 端口未放行；控制台登录密码遗忘需重置（见 Vaultwarden）

## 变更记录

- 2026-09-05 首次生成（portal 文档补全任务，AI 基于实采+源码生成；版本按容器实采修正为 v2.4.3）
