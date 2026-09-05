# 记忆提炼面板

> 对话记忆自动提炼体系的「只读展示端」：把 Hermes 会话经 LLM 提炼出的结构化知识（知识条目 + 来源会话）以 Web 面板呈现，支持检索、筛选与统计。

## 基本信息

| 项 | 值 |
|----|----|
| 分类 | 自研 Web 系统 / 工具（知识提炼） |
| 版本 | 源码未打版本号（Flask 应用，镜像 `memory-extract-memory-panel`）(待确认) |
| 部署位置 | mykng 容器 `memory-panel`（镜像 `memory-extract-memory-panel`），端口 8720，`restart: always`；docker compose 部署 |
| 源码位置 | 本地 `D:\huliang\java\ideaworkspace\devtools\memory-extract\`（核心：`memory-panel.py` 面板 + `memory-extract.py` 提炼管线 + `templates/` 前端 + `config.yaml`）；mykng 路径 `/root/devtools/memory-extract/` |
| CI/CD | 无（自部署 docker compose，见 `docker-compose.yml`） |

## 访问入口

- 公网：`https://memory.marschat.online`（腾讯云2号 nginx 终止 TLS 443）
- 内网：`http://192.168.31.105:8720`（mykng 宿主）
- Tailscale：`http://100.93.36.113:8720`

## 全链路

```
浏览器 → 腾讯云2号 nginx (:443, 域名 memory.marschat.online)
       → http://100.93.36.113:8720  (mykng memory-panel 容器)
```
（mykng 本机 nginx 也有 `/memory/ → 127.0.0.1:8720` 的 path 反代，但公网域名走的是直连容器端口 8720。）

## 核心功能与使用

面板为 Flask 单体（API + 前端一体），数据全部来自 SQLite `memory-extracts.db`。源码路由（memory-panel.py）确定的能力：

- 会话列表 `/api/sessions`
  - 已处理的 Hermes 会话，分页 + 关键词搜索（按 `title` / `session_id`）。
  - 每条会话显示「已提炼条目数」(`extract_count`)、起止时间、处理时间。
  - 默认按 `processed_at` 倒序。
- 会话详情 `/api/sessions/<session_id>`
  - 会话元信息 + 该会话下全部知识条目（`extracts`）列表，便于「按来源会话追溯知识」。
- 知识条目列表 `/api/extracts`
  - 提炼出的结构化知识条目，支持：按 `type` 类型过滤、按 `content`/`tags` 关键词搜索、分页。
  - 默认按 `created_at` 倒序。
- 条目详情 `/api/extracts/<extract_id>`
  - 单条知识：`content`、`tags`、`context`（JSON，已解析为 `context_parsed`）、来源会话标题与摘要、起止时间。
- 统计 `/api/stats`
  - 总会话数、总知识条目数、`type` 类型分布（`type_distribution`）、最近处理时间。
- 首页 `/`（SSR）
  - `index.html` 直接内嵌初始数据（stats + 最近 20 会话），「零额外 API 请求」首屏渲染。

**数据从哪来（提炼管线，memory-extract.py）**
- 每 15 分钟（`schedule_minutes: 15`）扫描 Hermes `state.db`（`/root/.hermes/state.db`）中「新结束的会话」。
- 调 LLM（配置见 `config.yaml`，base_url/model/timeout）将对话提炼为结构化知识，写入 `memory-extracts.db`。
- 面板**只读**展示该库；提炼与展示解耦，可独立部署。
- 支持 `--force-session <ID>` 强制重处理、`--dry-run` 仅预览。

## 依赖与关联

- 依赖
  - SQLite 存储：`memory-extracts.db`（compose 挂载 `./memory-extracts.db:/app/memory-extracts.db`）。
  - 提炼管线依赖：Hermes `state.db`（数据源）、LLM API（凭证在 `config.yaml`，应纳入 Vaultwarden，禁止明文落盘）。
  - mykng 容器运行时（8720 映射）。
- 被依赖/关联系统
  - 上游数据源：Hermes agent（`/root/.hermes/state.db`）。
  - 关联面板入口：经腾讯云2号 nginx `memory.marschat.online` 对外。
  - 与 Vaultwarden / infrastructure-map 关联：LLM key、数据库路径等敏感信息统一在 Vaultwarden 管理。

## 运维要点

- 启停方式
  - 容器：`docker compose up -d memory-panel`（镜像由 `Dockerfile` 构建，`restart: always`）。
  - 面板进程默认 `--host 0.0.0.0 --port 8720`。
- 日志查看
  - `docker logs memory-panel`；提炼管线日志在 mykng 宿主 `memory-extract.py` 运行处（cron/常驻，未实采具体启动方式）(待确认)。
- 数据与备份
  - 核心数据：`memory-extracts.db`（SQLite，含全部会话与知识条目）。当前备份现状未实采 (待确认建议纳入 mykng 数据备份)。
  - 提炼配置 `config.yaml`（含 LLM 凭证）不应明文入库，应入 Vaultwarden。
- 常见问题
  - 面板空/数据不更新：提炼管线（memory-extract.py）是否在跑、Hermes state.db 路径是否正确、LLM 调用是否超时。
  - 升级：改代码后需重建 `memory-extract-memory-panel` 镜像（compose `build: .`）。

## 变更记录

- 2026-09-05 首次生成（portal 文档补全任务，AI 基于本地源码 memory-panel.py / memory-extract.py / config.yaml / docker-compose.yml 精读 + 材料包生成）
