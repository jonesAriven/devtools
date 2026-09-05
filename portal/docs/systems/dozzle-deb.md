# Dozzle 日志 (内网 Deb)

> 内网 Debian（192.168.31.182）的 Docker 容器实时日志直查工具（Dozzle v8.11.7），挂载 docker.sock 直读全部容器 stdout，免登录即看；适合快速盯 Grafana/Loki 覆盖之外的临时性问题。

## 基本信息

| 项 | 值 |
|----|----|
| 分类 | 基础设施（可观测性） |
| 版本 | amir20/dozzle:v8.11.7（容器实采） |
| 部署位置 | 内网 Debian（192.168.31.182）容器 `obs-dozzle` |
| 端口 | 15888（宿主）→ 容器 8080 |
| 挂载 | /var/run/docker.sock → /var/run/docker.sock（直读容器日志流） |
| 源码位置 | 开源组件，官方仓库 https://github.com/amir20/dozzle |
| CI/CD | 无（自部署，compose 位于内网 Debian /opt/observability/docker-compose.yml） |

## 访问入口

- 公网：—（无公网反代）
- 内网：`http://192.168.31.182:15888`
- Tailscale：`http://100.105.196.63:15888`
- 账密：实例未启用认证（内网监听），故 portal 描述注明"需 Tailscale/家庭局域网"

## 全链路

```
浏览器（家庭局域网 / Tailscale）
  → http://192.168.31.182:15888（docker-proxy 直连）
  → 容器 obs-dozzle (:8080) → 读 docker.sock 容器日志流
```

## 核心功能与使用

- **实时日志流**：本机全部容器（activecode、frp-manager、rag-qdrant、obs-grafana、obs-loki、obs-uptime-kuma、obs-promtail、rag-embedding、hive-metastore、platform-mysql-2/3 等）选即看、多容器合并视图
- **搜索/下载**：日志内关键词搜索、时间范围回看、日志文件导出
- **典型场景**：激活码服务（activecode）联调时实时盯日志；MySQL GR Node2/3 异常时快速看容器输出；Qdrant/embedding 调试
- 与 Loki 的分工：Dozzle 看"现在"（无索引、直连快），Loki 存"历史"（可跨机检索）；临时排查用 Dozzle，跨机/历史归 Loki

## 依赖与关联

- 依赖：Docker daemon（docker.sock 只读日志能力）
- 关联：Dozzle (mykng)（同款，192.168.31.105:15500）；Grafana+Loki（历史日志）

## 运维要点

- 启停：内网 Debian /opt/observability compose 项目
- 安全：因未开认证且挂载 docker.sock，务必保持仅内网/Tailscale 可达，不要加公网反代
- 数据与备份：无状态（不落盘日志），可随时重建

## 变更记录

- 2026-09-05 首次生成（portal 文档补全任务，AI 基于实采+源码生成）
