# Dozzle 日志 (mykng)

> mykng 主机（192.168.31.105）的 Docker 容器实时日志直查工具（Dozzle latest），挂载 docker.sock 直读全部容器 stdout；mykng 是主部署机，kb-* 五件套、portal、kb-ops、infra-monitor、woodpecker、Nexus、各中间件共 30 个容器的日志都能在这一个页面里实时盯。

## 基本信息

| 项 | 值 |
|----|----|
| 分类 | 基础设施（可观测性） |
| 版本 | amir20/dozzle:latest（容器实采） |
| 部署位置 | mykng（192.168.31.105）容器 `obs-dozzle` |
| 端口 | 15500（宿主）→ 容器 8080 |
| 挂载 | /var/run/docker.sock → /var/run/docker.sock |
| 源码位置 | 开源组件，官方仓库 https://github.com/amir20/dozzle |
| CI/CD | 无（自部署，独立容器运行） |

## 访问入口

- 公网：—（无公网反代）
- 内网：`http://192.168.31.105:15500`
- Tailscale：`http://100.93.36.113:15500`
- 账密：实例未启用认证（内网监听），portal 描述注明"需 Tailscale/家庭局域网"

## 全链路

```
浏览器（家庭局域网 / Tailscale）
  → http://192.168.31.105:15500（docker-proxy 直连）
  → 容器 obs-dozzle (:8080) → 读 docker.sock 容器日志流
```

## 核心功能与使用

- **实时日志流**：kb-gateway/kb-auth/kb-file/kb-knowledge/kb-intelligence、portal-server/portal-web、kb-ops、infra-monitor、workcheck-python、cosmic-api/cosmic-web、memory-panel、woodpecker-server/agent、nexus、platform-*（mysql/redis/mongo/minio/meilisearch/kafka/nacos）、vaultwarden 等，全部一页可达
- **搜索/合并视图**：多容器同屏合并（如 kb 五件套联排）排接口链路问题特别顺
- **典型场景**：流水线部署后马上盯新容器启动日志确认健康检查为何不过；生产接口报错时实时看 gateway 日志（与 Loki 历史检索互补）
- 与 Loki 分工：Dozzle 看实时，Loki（Grafana）查历史跨机

## 依赖与关联

- 依赖：Docker daemon
- 关联：Dozzle (内网 Deb)（192.168.31.182:15888）、Dozzle (腾讯云2号)（1.117.70.30:15500，Tailscale 侧）；Woodpecker CI 流水线日志看构建/部署，Dozzle 看应用运行时——部署问题在 CI 日志，运行问题在这里

## 运维要点

- 启停：`docker restart obs-dozzle`（单容器，无 compose 编排）——待确认是否已纳入某 compose 项目
- 安全：无认证 + docker.sock，保持仅内网/Tailscale 可达，禁止公网反代
- 数据与备份：无状态，可随时重建

## 变更记录

- 2026-09-05 首次生成（portal 文档补全任务，AI 基于实采+源码生成）
