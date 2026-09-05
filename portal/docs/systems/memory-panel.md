# 记忆提炼面板

> 对话记忆自动提炼体系的「只读展示端」：
> 把 Hermes 会话经 LLM 提炼出的结构化知识（知识条目 + 来源会话）以 Web 面板呈现，
> 支持检索、筛选与统计。

## 基本信息

| 项 | 值 |
|----|----|
| 分类 | 自研 Web 系统 / 工具（知识提炼） |
| 版本 | 源码未打版本号（Flask 应用，镜像 `memory-extract-memory-panel`）(待确认) |
| 部署位置 | mykng 容器 `memory-panel`（镜像 `memory-extract-memory-panel`），端口 8720 |
| 部署位置 | `restart: always`；docker compose 部署 |
| 源码位置 | 本地 `D:\huliang\java\ideaworkspace\devtools\memory-extract\` |
| 源码位置 | `memory-panel.py` 面板 314 行 + `memory-extract.py` 提炼管线 493 行 |
| 源码位置 | `templates/index.html` 前端 + `static/` + `config.yaml` |
| 源码位置 | mykng 同路径 `/root/devtools/memory-extract/` |
| CI/CD | 无（自部署 docker compose，compose 文件 `/root/devtools/memory-extract/docker-compose.yml`） |

## 访问入口

- 公网：`https://memory.marschat.online`（腾讯云2号 nginx 终止 TLS 443）
- 内网：`http://192.168.31.105:8720`（mykng 宿主）
- Tailscale：`http://100.93.36.113:8720`

## 全链路

```
浏览器 → 腾讯云2号 nginx (:443, 域名 memory.marschat.online)
       → http://100.93.36.113:8720  (mykng memory-panel 容器)
```

（mykng 本机 nginx 也有 `/memory/ → 127.0.0.1:8720` 的 path 反代，
但公网域名走的是直连容器端口 8720。）

## 系统设计

### 总体架构

两个独立 Python 程序 + 一个 SQLite 库，提炼与展示解耦，可独立部署：

- **提炼管线 `memory-extract.py`（宿主运行，非容器）**
  - 定时/手动扫描 Hermes `state.db` → 调 LLM 提炼 → 写入 `memory-extracts.db`。
- **展示面板 `memory-panel.py`（容器运行）**
  - Flask 单体（API + SSR 前端一体），只读 `memory-extracts.db`。
- **前端**
  - `templates/index.html` 单页（SSR 首页内嵌初始数据 + 原生 JS 渲染）。
  - `static/` 静态资源。

### 提炼管线核心逻辑（memory-extract.py）

1. **选会话**
   - 读 Hermes `state.db` 的 `sessions` 表。
   - 筛选：已结束（ended_at 非空）+ 来源非 cron/webhook + message_count ≥ 4。
   - 排除已处理（extract 库 sessions 表已有的 session_id）。
   - 单轮最多处理 5 个（`--limit` 可调）。
2. **取消息**
   - 读 `messages` 表中 user/assistant 角色、active=1 的有效内容。
   - 单条截断 200 字。
3. **LLM 提炼**
   - 拼装提炼 prompt，调 OpenAI 兼容 `/chat/completions`。
   - 配置：`config.yaml` 的 llm.base_url / model / timeout。
   - api_key 属凭证，见 Vaultwarden，不落盘。
   - 要求严格输出 JSON 数组。
4. **落库**
   - 解析 JSON（兼容 markdown 代码块/正则兜底）。
   - 写 sessions 摘要与 extracts 条目。
5. **CLI 参数**
   - `--dry-run`（只预览）
   - `--force-session=<ID>`（强制重处理）
   - `--limit=<N>`（单轮处理数）
   - `--reset`（清空 extract 库重跑）

### 核心数据模型（memory-extracts.db，2 张表）

| 表 | 字段 | 用途 |
|----|------|------|
| `sessions` | session_id（主键） | 已处理的 Hermes 会话 |
| | title / source / user_id / model | 会话元信息 |
| | started_at / ended_at / message_count | 会话时间与规模 |
| | input_tokens / output_tokens | token 消耗 |
| | processed_at / extract_count | 处理时间与产出条数 |
| | conversation_summary | 取前 3 条用户消息拼接的摘要 |
| `extracts` | id（uuid）| 提炼出的知识条目 |
| | session_id（外键，索引） | 来源会话 |
| | type | decision/lesson/architecture/preference/fact/detail/general |
| | content / tags / category | 内容、标签、分类（运维/开发/架构/配置） |
| | context（JSON）/ created_at | 上下文与创建时间（索引建在 session_id 与 type 上） |

### 关键设计决策

1. **只读展示端**
   - 面板对 extract 库零写操作，提炼与展示解耦。
   - 管线挂了面板仍可查历史。
2. **零依赖倾向**
   - 管线不引入 pyyaml/openai SDK，自写轻量 YAML 解析与 urllib 调用。
   - 便于在任何 Python3 环境裸跑。
3. **SSR 首屏**
   - `/` 路由把 stats + 最近 20 条会话直接内嵌进 HTML。
   - 首屏零额外 API 请求，列表翻页/搜索再走 API。
4. **质量闸门**
   - message_count ≥ 4、单条消息 <3 字符跳过、LLM 输出解析失败降级为空。
   - 避免噪音入库。

### 对外接口概览（memory-panel.py 路由）

| 路由 | 功能 |
|------|------|
| `GET /api/sessions` | 会话分页列表（page/per_page/search，按 title 或 session_id 模糊），附 extract_count，默认 processed_at 倒序 |
| `GET /api/sessions/<session_id>` | 会话详情 + 该会话全部知识条目 |
| `GET /api/extracts` | 条目分页列表（type 过滤 + content/tags 搜索），默认 created_at 倒序 |
| `GET /api/extracts/<extract_id>` | 单条详情（context 解析为 context_parsed、来源会话标题与摘要） |
| `GET /api/stats` | 总会话数、总条目数、type 分布、最近处理时间 |
| `GET /` | SSR 首页（内嵌初始数据） |
| `GET /static/<file>` | 静态资源 |

## 部署与发布

- 编排与位置
  - compose 文件：`/root/devtools/memory-extract/docker-compose.yml`（compose project：memory-extract）。
  - 容器：`memory-panel`；镜像：`memory-extract-memory-panel`（`build: .` 由同目录 `Dockerfile` 构建）。
- 配置清单
  - 端口映射：`8720:8720`（面板默认 `--host 0.0.0.0 --port 8720`）。
  - 卷挂载：`./memory-extracts.db:/app/memory-extracts.db`（SQLite 单文件直挂，容器无其他持久化）。
  - 环境变量：`TZ=Asia/Shanghai`。
  - LLM 凭证在宿主 `config.yaml`（不入容器、不落文档）。
- 发布/升级
  - 无流水线。
  - 改代码后在 `/root/devtools/memory-extract/` 执行：
    - `docker compose build --no-cache`
    - `docker compose up -d --force-recreate`
- 回滚
  - git 仓库（devtools）回退源码后重新 compose build。
  - `memory-extracts.db` 不随代码回滚。
- 提炼管线部署
  - 宿主 `python3 memory-extract.py` 直接运行（无容器）。
  - 当前 mykng 上未见常驻进程或 cron/timer，自动调度触发方式 (待确认)，必要时手动执行。

## 核心功能与使用

### 功能清单

- **会话追溯**：按会话浏览「哪些 Hermes 对话被提炼过、各提炼出几条知识」，支持标题/ID 搜索。
- **知识检索**：按类型（decision/lesson/architecture 等）过滤 + 关键词搜索全部知识条目。
- **条目详情**：查看单条知识的完整内容、标签、分类、上下文（来源会话标题与摘要）。
- **总量统计**：会话/条目总数、类型分布、最近处理时间——判断提炼管线是否正常运转。

### 典型操作路径

1. **查最近提炼成果**
   - 打开 `https://memory.marschat.online`。
   - 首页直接看统计与最近 20 条会话。
   - 点某条会话进详情看其全部知识条目。
2. **找特定知识**
   - 知识条目页 → 输入关键词（或选 type）搜索。
   - 点条目看详情与来源会话。
3. **补处理某会话**
   - SSH 到 mykng。
   - `cd /root/devtools/memory-extract && python3 memory-extract.py --force-session=<会话ID>`。
   - 可先 `--dry-run` 预览 → 刷新面板确认。
4. **验证管线活性**
   - 看 `/api/stats` 的 last_processed_at 是否在最近推进。
   - 长期不推进则检查管线运行与 Hermes state.db。

## 依赖与关联

- 依赖
  - SQLite 存储：`memory-extracts.db`（compose 单文件挂载）。
  - 提炼管线依赖：Hermes `state.db`（`/root/.hermes/state.db`，数据源）。
  - LLM API（凭证在 `config.yaml`，应纳入 Vaultwarden，禁止明文落盘）。
  - mykng 容器运行时（8720 映射）。
- 被依赖/关联系统
  - 上游数据源：Hermes agent（`/root/.hermes/state.db`）。
  - 关联面板入口：经腾讯云2号 nginx `memory.marschat.online` 对外。
  - 与 Vaultwarden / infrastructure-map 关联：LLM key 等敏感信息统一在 Vaultwarden 管理。
  - 与 SiYuan（note.marschat.online）同属知识类但独立：SiYuan 为人工笔记，本面板为自动提炼。

## 运维要点

- 启停方式
  - `cd /root/devtools/memory-extract && docker compose up -d`（restart: always）。
- 日志查看
  - `docker logs memory-panel`。
  - 提炼管线输出到 stderr（含每会话处理进度），运行方式（手动/调度）确认后补录 (待确认)。
- 数据与备份
  - 核心数据：`memory-extracts.db`（SQLite 单文件，含全部会话与知识条目）。
  - 当前备份现状未实采 (待确认)，建议纳入 mykng 数据备份。
  - `config.yaml` 含 LLM 凭证，不入库不入文档。
  - 目录内另有历史库 `extracts.db`（早期产物）。
- 常见问题
  - 面板空/数据不更新：
    - 先查提炼管线是否在跑（`ps -ef | grep memory-extract`）。
    - 再查 Hermes state.db 路径与 LLM 调用是否超时。
  - 单库单文件挂载：SQLite 并发写弱，提炼管线与面板同时频繁访问时以管线写入优先，面板只读影响小。
  - `--reset` 会清空全部提炼数据，慎用；重跑前先备份 `memory-extracts.db`。

## 变更记录

- 2026-09-05 v2 补全设计/部署/使用三维度
  - 精读 memory-panel.py / memory-extract.py 源码。
  - 补全管线四步逻辑、2 表数据模型、API 路由表、CLI 参数、compose 细节与典型操作路径。
- 2026-09-05 v1 首次生成（基于源码精读 + 材料包）
