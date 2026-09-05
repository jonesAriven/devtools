# Dozzle 日志 (内网 Deb)

> 内网 Debian（192.168.31.182）的 Docker 容器实时日志直查工具（Dozzle v8.11.7），挂载 docker.sock 直读全部容器 stdout，免登录即看；适合快速盯 Grafana/Loki 覆盖之外的临时性问题。

## 基本信息

| 项 | 值 |
|----|----|
| 分类 | 基础设施（可观测性） |
| 版本 | amir20/dozzle:v8.11.7（容器实采 2026-09-05） |
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

## 系统设计

### 组件架构

Dozzle 是轻量级 Docker 日志查看器，官方能力要点：

- 工作原理：挂载 docker.sock 后直接调用 Docker API 拉取各容器 stdout/stderr 流，浏览器实时呈现——**不存储、不索引、零落盘**
- 能力：单容器/多容器实时流、多容器合并视图（同屏按时间混排）、日志内搜索、时间范围回看（依赖 Docker 自身保留的日志文件）、日志文件下载
- 无数据库、无采集端：单容器 + 一个 sock 挂载即完整可用
- 认证可选：默认无认证；官方支持用户/代理级认证，我们实例未启用（靠网络边界保护）

### 我们的集成设计

- **实例角色**：内网 Debian 本机 11 个容器的实时日志窗口；观测栈分工里的"即时视角"
- **数据连接**：只读 docker.sock → Docker daemon → 本机全部容器日志（activecode、frp-manager、rag-qdrant、obs-grafana、obs-loki、obs-uptime-kuma、obs-promtail、rag-embedding、hive-metastore、platform-mysql-2/3 等）
- **为什么选它**：单容器挂个 sock 就能用，无需采集端与索引；对比"SSH + docker logs"多了搜索、多容器同屏、下载导出
- **关键配置思路**：无认证设计的前提是网络边界——仅内网+Tailscale 可达，绝不加公网反代；纳入 observability compose 项目，与观测栈同生共死

### 与 Loki 的分工（日志两套通道）

| 通道 | 看什么 | 特点 |
|------|--------|------|
| Dozzle（本篇） | "现在"：实时流 | 直连快、无查询语法、不留痕 |
| Grafana + Loki | "历史"：可跨机检索 | 有标签模型、可回看任意时段、可导出 |
| 取舍 | 临时排查用 Dozzle；跨机/历史/留证归 Loki | 同机 obs-promtail 经同一 docker.sock 把日志送 Loki |

### 与"SSH + docker logs"的对比

| 维度 | SSH + docker logs | Dozzle（本篇） |
|------|-------------------|----------------|
| 入口成本 | 每次开终端、记容器名 | 浏览器收藏夹直达 |
| 多容器对比 | 多窗口手工切 | 合并视图同屏混排 |
| 搜索 | 管道 grep | 输入框即时过滤 |
| 导出 | 手工 redirect | 内置下载 |
| 权限面 | root SSH 全权 | 仅日志读取（sock 只读能力） |

### 观测栈容器互查关系（本机自举场景）

观测栈自身出问题时，本机的排查链路：obs-dozzle 看 obs-grafana/obs-loki/obs-uptime-kuma/obs-promtail 四个容器的实时输出 → 定位不了的再去 Grafana 查历史。Dozzle 挂了则退化为 SSH + docker logs，观测栈不依赖 Dozzle 存活。

## 部署与发布

### 编排与位置

- compose 文件：`/opt/observability/docker-compose.yml`（内网 Debian 192.168.31.182）
- compose project：`observability`（同项目共 5 容器：obs-promtail / obs-loki / obs-grafana / obs-uptime-kuma / obs-dozzle，`docker compose ls` 实采确认）
- 容器名：`obs-dozzle`，镜像 `amir20/dozzle:v8.11.7`

### 配置清单（实采）

| 项 | 值 |
|----|----|
| 端口映射 | 宿主 15888 → 容器 8080 |
| 卷挂载 | /var/run/docker.sock → /var/run/docker.sock（只读日志能力） |

无环境变量、无数据卷（无状态）。

### 发布/升级

自部署，无流水线。实际操作步骤（SSH 到 192.168.31.182）：

1. 改 compose 中 dozzle 镜像 tag
2. `docker compose pull dozzle && docker compose up -d dozzle`

### 回滚

- 镜像回退：compose 改回旧 tag 后 `docker compose up -d dozzle`
- 无状态无数据卷，不存在数据回滚；重建容器即全新实例

## 核心功能与使用

### 功能清单

| 能力 | 什么场景用 |
|------|------------|
| 实时日志流 | 本机 11 个容器选即看（activecode / frp-manager / rag-qdrant / obs-* / platform-mysql-2/3 等） |
| 多容器合并视图 | 接口链路联排：同屏按时间混排多个容器输出 |
| 搜索 / 时间回看 | 现场关键词定位、回看刚滚过的输出 |
| 日志导出下载 | 把现场日志留档到排查记录 |

### 典型场景（实际用法）

- 激活码服务（activecode）联调时实时盯日志
- MySQL GR Node2/3 异常时快速看容器输出
- Qdrant / rag-embedding 调试
- 观测栈自身（obs-* 容器）的启动与报错查看

### 典型操作路径

1. **盯单个容器**：打开 :15888 → 左侧容器列表点选（如 activecode）→ 实时滚动 → 搜索框过滤关键词
2. **联排链路**：多选多个容器（如 obs-loki + obs-promtail）→ 合并视图按时间混排
3. **导出现场**：容器页 → 下载按钮导出日志文件 → 附到排查记录

## 依赖与关联

- 依赖：Docker daemon（docker.sock 只读日志能力）
- 关联：
  - Dozzle (mykng)（同款，192.168.31.105:15500）——每台 Docker 主机一个 Dozzle 的布局
  - Grafana+Loki（历史日志检索）
  - 同机 obs-promtail 也经 docker.sock 采集同一批容器日志送 Loki（一个实时看、一个落库）

## 运维要点

### 启停 / 备份

- 启停：内网 Debian /opt/observability compose 项目，`docker compose up -d dozzle` / `stop dozzle`
- 数据与备份：无状态（不落盘日志），可随时重建

### 常见问题

| 症状 | 原因 | 处理 |
|------|------|------|
| 某容器日志为空 | 服务把日志写到文件而非 stdout | 确认日志驱动配置；文件日志走宿主路径或 Loki 查 |
| 页面打不开 | 容器挂了或不在内网/Tailscale | `docker compose up -d dozzle`；确认访问网络 |
| 历史回看不到很早 | Docker 日志轮转策略限制 | 属 Docker 侧行为，早期历史去 Loki 查 |

### 安全红线

- 未开认证 + 挂载 docker.sock：**务必保持仅内网/Tailscale 可达，禁止加公网反代**
- 若需暴露更广，先启用 Dozzle 认证或套反向代理认证层

### 操作速查（SSH root@192.168.31.182）

| 动作 | 命令 |
|------|------|
| 看状态 | `docker compose -f /opt/observability/docker-compose.yml ps dozzle` |
| 重启 | `docker compose -f /opt/observability/docker-compose.yml restart dozzle` |
| Dozzle 自身日志 | `docker logs -f obs-dozzle`（自己看不了自己） |
| 确认 sock 挂载 | `docker inspect obs-dozzle --format '{{range .Mounts}}{{.Source}} {{end}}'` |

### 常用排查 runbook（本机）

1. **激活码服务异常**：Dozzle 选 activecode → 看实时输出 → 公网不可用则先查 Tailscale（tools 域名依赖隧道）
2. **MySQL GR Node2/3 异常**：Dozzle 选 platform-mysql-2/3 → 看 GR 报错输出 → 需要 GR 集群级操作走 deploy-mysql-cluster.sh
3. **观测栈自身异常**：Dozzle 选对应 obs-* 容器 → 启动失败多为配置/挂载问题 → 历史对照去 Grafana
4. **Qdrant/embedding 调试**：Dozzle 选 rag-qdrant / rag-embedding → 看请求处理输出

## 变更记录

- 2026-09-05 v2 补全设计/部署/使用三维度（compose 编排归属由 docker inspect labels 实采确认：observability 项目；与 Loki 分工表、FAQ 表整理）
- 2026-09-05 v1 首次生成（portal 文档补全任务，AI 基于实采+源码生成）
