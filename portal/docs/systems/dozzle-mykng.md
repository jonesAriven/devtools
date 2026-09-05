# Dozzle 日志 (mykng)

> mykng 主机（192.168.31.105）的 Docker 容器实时日志直查工具（Dozzle latest），挂载 docker.sock 直读全部容器 stdout；mykng 是主部署机，kb-* 五件套、portal、kb-ops、infra-monitor、woodpecker、Nexus、各中间件共 30 个容器的日志都能在这一个页面里实时盯。

## 基本信息

| 项 | 值 |
|----|----|
| 分类 | 基础设施（可观测性） |
| 版本 | amir20/dozzle:latest（容器实采 2026-09-05） |
| 部署位置 | mykng（192.168.31.105）容器 `obs-dozzle` |
| 端口 | 15500（宿主）→ 容器 8080 |
| 挂载 | /var/run/docker.sock → /var/run/docker.sock |
| 源码位置 | 开源组件，官方仓库 https://github.com/amir20/dozzle |
| CI/CD | 无（自部署） |

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

## 系统设计

### 组件架构

Dozzle 是轻量级 Docker 日志查看器，官方能力要点：

- 工作原理：挂载 docker.sock 后直接调用 Docker API 拉取各容器 stdout/stderr 流，浏览器实时呈现——**不存储、不索引、零落盘**
- 能力：单容器/多容器实时流、多容器合并视图（同屏按时间混排）、日志内搜索、时间范围回看（依赖 Docker 自身保留的日志文件）、日志文件下载
- 无数据库、无采集端：单容器 + 一个 sock 挂载即完整可用
- 认证可选：默认无认证；官方支持用户/代理级认证，我们实例未启用（靠网络边界保护）

### 我们的集成设计

- **实例角色**：主部署机 mykng 全部约 30 个容器的实时日志窗口；与内网 Deb 同款组成"每台 Docker 主机一个 Dozzle"的布局；观测分工里的"即时视角"
- **数据连接**：只读 docker.sock → Docker daemon → 本机全部容器日志：
  - 业务应用：kb-gateway / kb-auth / kb-file / kb-knowledge / kb-intelligence、portal-server / portal-web、kb-ops、infra-monitor、workcheck-python、cosmic-api / cosmic-web、memory-panel
  - 平台与 CI：woodpecker-server / agent、nexus
  - 中间件：platform-*（mysql / redis / mongo / minio / meilisearch / kafka / nacos）
  - 其他：vaultwarden 等
- **为什么选它**：mykng 容器密度最高（流水线部署目标机），部署后盯启动日志的频次也最高，一个常驻入口最省事
- **关键配置思路**：与内网 Deb 实例不同，本实例是**独立容器**（docker inspect 无 compose project 标签，实采确认），不随任何 compose 项目启停；无认证，靠网络边界保护

### 三台 Dozzle 布局

| 实例 | 主机 | 入口 | 编排 |
|------|------|------|------|
| Dozzle (mykng)（本篇） | 192.168.31.105 | :15500 | 独立容器（docker run） |
| Dozzle (内网 Deb) | 192.168.31.182 | :15888 | observability compose 项目 |
| Dozzle (腾讯云2号) | 1.117.70.30（Tailscale 侧） | :15500 | 独立部署 |

### 与其他日志通道的分工

| 通道 | 看什么 | 特点 |
|------|--------|------|
| Dozzle（本篇） | "现在"：实时流 | 直连快、无查询语法、不留痕 |
| Grafana + Loki | "历史"：可跨机检索 | 有标签模型、可回看任意时段 |
| Woodpecker CI 日志 | "构建/部署"过程输出 | 部署问题在 CI 日志，运行问题在 Dozzle |

### 与"SSH + docker logs"的对比

| 维度 | SSH + docker logs | Dozzle（本篇） |
|------|-------------------|----------------|
| 入口成本 | 每次开终端、记容器名 | 浏览器收藏夹直达 |
| 多容器对比 | 多窗口手工切 | 合并视图同屏混排 |
| 搜索 | 管道 grep | 输入框即时过滤 |
| 导出 | 手工 redirect | 内置下载 |
| 权限面 | root SSH 全权 | 仅日志读取（sock 只读能力） |

### 典型联动链路（部署验证场景）

```
Woodpecker 流水线完成（构建/部署日志）
  → 打开本机 Dozzle :15500 看新容器启动输出
  → 健康检查不过 → 继续在 Dozzle 盯错误行
  → 需要回看更早/跨机 → 转 Grafana + Loki
```

这套链路覆盖"部署是否成功"的完整确认闭环，是 mykng 日常发布的标准动作。

## 部署与发布

### 编排与位置

- **无 compose 编排**（实采 2026-09-05：容器 labels 无 com.docker.compose.project / config_files，即 `docker run` 手工启动的独立容器）
- 容器名：`obs-dozzle`，镜像 `amir20/dozzle:latest`

### 配置清单（实采）

| 项 | 值 |
|----|----|
| 端口映射 | 宿主 15500 → 容器 8080 |
| 卷挂载 | /var/run/docker.sock → /var/run/docker.sock |

无环境变量、无数据卷（无状态）。

### 发布/升级

自部署，手工管理（独立容器，无 compose）：

1. `docker pull amir20/dozzle:latest`
2. `docker stop obs-dozzle && docker rm obs-dozzle`
3. 按原参数重新启动：`docker run -d --name obs-dozzle -p 15500:8080 -v /var/run/docker.sock:/var/run/docker.sock amir20/dozzle:latest`

> 注意：镜像用 latest，`docker pull` 后必须重建容器才生效。建议后续纳入 compose 编排以便参数留痕（改进项）。

### 回滚

- 无状态无数据卷；回退 = 指定旧镜像 tag 重新 run
- 误删容器直接按上面参数重建即恢复

## 核心功能与使用

### 功能清单

| 能力 | 什么场景用 |
|------|------------|
| 实时日志流 | kb 五件套 / portal / kb-ops / infra-monitor / woodpecker / nexus / platform-* 中间件等全部一页可达 |
| 搜索 / 合并视图 | 多容器同屏合并（如 kb 五件套联排）排接口链路问题 |
| 日志导出下载 | 现场留档 |
| 时间回看 | 回看刚滚过的启动输出 |

### 典型场景（实际用法）

- 流水线部署后马上盯新容器启动日志，确认健康检查为何不过
- 生产接口报错时实时看 gateway 日志（与 Loki 历史检索互补）
- 中间件（mysql/kafka/nacos 等）异常时快速看容器输出

### 典型操作路径

1. **部署后盯启动**：Woodpecker 部署完成 → 打开 :15500 → 选中刚发布的服务容器 → 观察启动输出到健康检查通过
2. **跨服务排链路**：多选 gateway + 对应业务服务 → 合并视图按时间对齐请求日志
3. **导出现场**：容器页 → 下载日志文件 → 留档

## 依赖与关联

- 依赖：Docker daemon
- 关联：
  - Dozzle (内网 Deb)（192.168.31.182:15888）、Dozzle (腾讯云2号)（1.117.70.30:15500，Tailscale 侧）
  - Woodpecker（构建/部署日志——部署问题在 CI 日志，运行问题在这里）
  - Grafana+Loki（历史日志，可跨机检索）

## 运维要点

### 启停 / 备份

- 启停：`docker restart obs-dozzle`（独立容器，无 compose 编排，实采确认）
- 数据与备份：无状态，可随时重建

### 常见问题

| 症状 | 原因 | 处理 |
|------|------|------|
| 某容器日志为空 | 服务把日志写到文件而非 stdout | 确认日志配置；文件日志去 Loki 查 |
| 升级 latest 后行为变化 | tag 未固定 | 固定具体版本 tag 重建 |
| 页面打不开 | 容器挂了或访问网络不对 | `docker restart obs-dozzle`；确认内网/Tailscale |

### 安全红线

- 无认证 + 挂载 docker.sock：**保持仅内网/Tailscale 可达，禁止公网反代**
- 若需暴露更广，先启用 Dozzle 认证或套反向代理认证层

### 操作速查（SSH root@192.168.31.105）

| 动作 | 命令 |
|------|------|
| 看状态 | `docker ps --filter name=obs-dozzle` |
| 重启 | `docker restart obs-dozzle` |
| Dozzle 自身日志 | `docker logs -f obs-dozzle`（自己看不了自己） |
| 确认编排归属 | `docker inspect obs-dozzle --format '{{index .Config.Labels "com.docker.compose.project"}}'`（当前为空） |
| 确认 sock 挂载 | `docker inspect obs-dozzle --format '{{range .Mounts}}{{.Source}} {{end}}'` |

### mykng 高频查看容器速查（实采容器名）

| 容器 | 什么时候看 |
|------|------------|
| kb-gateway | 接口报错第一现场（路由/鉴权/上游转发） |
| kb-auth / kb-file / kb-knowledge / kb-intelligence | 对应业务域问题，常与 gateway 合并视图联排 |
| portal-server / portal-web | portal 本身异常 |
| woodpecker-server / woodpecker-agent | 部署没生效先看 agent 拉取与执行输出 |
| platform-mysql / redis / mongo / kafka / nacos | 中间件异常（连接拒绝/内存/磁盘） |
| infra-monitor / kb-ops | 自研运维面板服务本身的问题 |

## 变更记录

- 2026-09-05 v2 补全设计/部署/使用三维度；实采确认无 compose 编排（v1"待确认"落实为独立容器 docker run 部署）；三台 Dozzle 布局表、FAQ 表整理
- 2026-09-05 v1 首次生成（portal 文档补全任务，AI 基于实采+源码生成）
