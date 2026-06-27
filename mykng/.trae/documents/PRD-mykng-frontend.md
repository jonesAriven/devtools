# mykng 知识库前端 - 产品需求文档（PRD）

## 1. 产品概述

mykng 是一个私有化部署的个人知识库系统，后端已采用微服务架构（Spring Cloud Gateway + 4 个业务微服务）部署运行。本前端为 Vue3 单页应用，对接已部署的后端 API，提供文档管理、知识检索、分享协作、运维监控等全功能可视化界面。

- 目标用户：个人开发者/技术团队，需要一个集中管理文档、网页剪藏、知识检索的私有化平台
- 核心价值：数据自主可控、全功能知识管理、类 Obsidian 的文档组织能力

## 2. 核心功能

### 2.1 用户角色

| 角色 | 注册方式 | 核心权限 |
|------|----------|----------|
| 管理员 | 预置账号（admin/admin123） | 全部功能 + 运维管理 + API Token 管理 |
| 普通用户 | 管理员创建 | 文档/空间/分享/搜索（无运维模块） |

### 2.2 功能模块

1. **登录页**：用户名密码登录，获取 JWT 双 Token
2. **工作台**：数据统计看板、最近文档、快捷操作
3. **知识空间**：空间 CRUD、空间内目录树管理
4. **文档编辑**：Markdown 编辑器、目录组织、收藏、版本历史
5. **全文搜索**：MeiliSearch 驱动的即时搜索、高亮匹配
6. **标签管理**：标签 CRUD、资源绑定、按标签筛选
7. **分享中心**：分享链接生成、访问密码、有效期、访问日志
8. **回收站**：已删除文档恢复/彻底删除
9. **文件管理**：存储桶管理、文件上传/解析/索引
10. **运维中心**：主机/服务/部署记录/看板/操作日志/矛盾检测
11. **设置**：用户资料、API Token 管理、修改密码

### 2.3 页面详情

| 页面名称 | 模块名称 | 功能描述 |
|----------|----------|----------|
| 登录页 | 登录表单 | 用户名+密码输入，Enter 提交，错误提示 |
| 工作台 | 统计卡片 | 文档数/空间数/标签数/分享数统计 |
| 工作台 | 最近文档 | 按更新时间排序的文档列表 |
| 知识空间 | 空间列表 | 卡片式展示所有空间，支持创建/编辑/删除 |
| 知识空间 | 目录树 | 左侧树形目录，支持拖拽排序、右键菜单 |
| 文档编辑 | Markdown 编辑器 | 所见即所得编辑、实时预览、自动保存 |
| 文档编辑 | 工具栏 | 收藏、分享、版本历史、删除按钮 |
| 全文搜索 | 搜索框 | 即时搜索（防抖 300ms）、结果高亮 |
| 标签管理 | 标签列表 | 标签云/列表视图切换、颜色标识 |
| 分享中心 | 分享列表 | 分享链接、提取码、状态、访问统计 |
| 回收站 | 回收列表 | 按类型筛选、恢复/彻底删除 |
| 文件管理 | 存储桶 | 桶列表、文件列表、上传、解析状态 |
| 运维中心 | 主机管理 | 主机 CRUD、状态监控 |
| 运维中心 | 服务管理 | 服务列表、部署记录 |
| 运维中心 | 看板 | 服务状态、趋势图、告警 |
| 运维中心 | 操作日志 | 操作记录分页查询、按类型筛选 |
| 设置 | 个人资料 | 头像、昵称、邮箱修改 |
| 设置 | API Token | Token 创建/撤销、权限范围 |

## 3. 核心流程

用户打开系统 → 未登录跳转登录页 → 输入凭据获取 Token → 进入工作台 → 浏览统计 → 创建知识空间 → 新建目录 → 新建文档并编辑 → 搜索内容 → 创建分享链接 → 查看访问日志 → 进入运维中心查看系统状态。

```mermaid
flowchart TD
    "A[打开系统]" --> "B{已登录?}"
    "B" -->|否| "C[登录页]"
    "C" --> "D[输入用户名密码]"
    "D" --> "E[API: /kb/api/auth/login]"
    "E --> F[获取 JWT Token]"
    "F --> G[工作台 Dashboard]"
    "G --> H[创建知识空间]"
    "H --> I[创建目录]"
    "I --> J[创建并编辑文档]"
    "J --> K[全文搜索]"
    "K --> L[生成分享链接]"
    "L --> M[查看运维监控]"
    "B" -->|是| "G"
```

## 4. 界面设计

### 4.1 设计风格

- **主色调**：深墨蓝（#1a2332）+ 琥珀金（#d4a574）点缀，营造知识沉淀的沉稳感
- **背景**：暖灰白底（#faf8f5），文档区纯白，降低长时间阅读疲劳
- **按钮**：圆角 8px，主操作深色填充，次操作描边
- **字体**：标题用思源宋体（Noto Serif SC）显文学气质，正文用思源黑体（Noto Sans SC）
- **布局**：左侧固定导航栏（64px 折叠/240px 展开）+ 顶部面包屑 + 内容区
- **图标**：Lucide 图标库，线性风格，2px 描边
- **动效**：页面切换淡入、列表加载骨架屏、按钮 hover 微动效

### 4.2 页面设计概览

| 页面名称 | 模块名称 | UI 元素 |
|----------|----------|----------|
| 登录页 | 登录卡片 | 居中卡片、左插画右表单、渐变背景 |
| 工作台 | 统计区 | 4 列卡片网格、数字大字号、趋势 mini 图 |
| 知识空间 | 空间网格 | 响应式卡片网格、hover 阴影上浮 |
| 文档编辑 | 编辑器 | 左目录树 + 中编辑器 + 右预览，三栏可调宽 |
| 搜索 | 搜索结果 | 列表式结果、关键词高亮、类型标签 |
| 运维看板 | 监控图表 | 折线图 + 饼图 + 状态指示灯 |

### 4.3 响应式

- 桌面优先（1920px 设计基准）
- 平板适配（768-1024px）：侧栏折叠为图标
- 移动端（< 768px）：底部 Tab 导航、编辑器全屏

## 5. 路径规范

### 5.1 前端路由（SPA）

| 路由路径 | 页面 |
|----------|------|
| `/kb/login` | 登录页 |
| `/kb/dashboard` | 工作台 |
| `/kb/space` | 知识空间 |
| `/kb/space/:id` | 空间详情（目录+文档） |
| `/kb/doc/:id` | 文档编辑 |
| `/kb/search` | 全文搜索 |
| `/kb/tag` | 标签管理 |
| `/kb/share` | 分享中心 |
| `/kb/trash` | 回收站 |
| `/kb/file` | 文件管理 |
| `/kb/ops/host` | 运维-主机 |
| `/kb/ops/service` | 运维-服务 |
| `/kb/ops/dashboard` | 运维-看板 |
| `/kb/ops/log` | 运维-日志 |
| `/kb/settings` | 设置 |

### 5.2 API 调用前缀

所有 API 请求统一前缀 `/kb/api/`，由网关路由到各微服务：
- `/kb/api/auth/**` → kb-auth（登录/刷新/登出）
- `/kb/api/user/**` → kb-auth（用户信息）
- `/kb/api/token/**` → kb-auth（API Token）
- `/kb/api/space/**`、`/kb/api/folder/**`、`/kb/api/doc/**`、`/kb/api/tag/**`、`/kb/api/search/**`、`/kb/api/share/**`、`/kb/api/version/**`、`/kb/api/trash/**`、`/kb/api/web/**` → kb-knowledge
- `/kb/api/bucket/**`、`/kb/api/file/**` → kb-file
- `/kb/api/ops/**`、`/kb/api/log/**` → kb-ops
  - `/kb/api/ops/host/**`、`/ops/service/**`、`/ops/deployment/**`、`/ops/conflict/**`、`/ops/dashboard/**`、`/ops/knowledge/**`、`/ops/import/**`、`/ops/log/**`（已有 8 个 Controller）
  - `/kb/api/ops/port/**`、`/ops/credential/**`、`/ops/domain/**`、`/ops/dependency/**`（新增 4 个 Controller，详见第 7 节）

### 5.3 静态资源

- 构建产物部署路径：`/kb/s/`
- 开发环境通过 Vite proxy 代理到后端网关 `http://192.168.31.105:8090`

## 6. 功能模块详细设计

> 本节对第 2.2 节中前 4 个缺乏页面级设计描述的功能模块进行补充，覆盖标签管理、分享中心、文件管理、运维操作日志四个页面，明确各页面的视图布局、交互行为与对应后端接口。

### 6.1 标签管理（路由 `/kb/tag`）

**视图布局**：
- 顶部工具栏：视图切换（标签云 / 列表）、新建标签按钮、关键词搜索框
- 标签云视图：按引用文档数动态字号展示，颜色按 `color` 字段渲染，hover 显示"已绑定 N 篇文档"
- 列表视图：表格列（名称、颜色色块、绑定资源数、创建时间、操作）
- 右侧抽屉：按标签筛选的文档列表（点击标签云节点触发），支持点击跳转文档编辑页

**交互与功能**：
- CRUD：新建/编辑标签（名称、颜色十六进制值，重复名称校验）
- 按标签筛选文档：调用 `GET /kb/api/search?tagId={id}` 拉取关联文档
- 批量绑定/解绑：在文档编辑页或本页勾选多个资源后调用 `POST /kb/api/tag/bind`、`DELETE /kb/api/tag/unbind`
- 删除标签：二次确认，联动清理 `resource_tag` 关联记录

**对应接口**：`GET /tag/list`、`POST /tag`、`DELETE /tag/{id}`、`POST /tag/bind`、`DELETE /tag/unbind`（详见接口规范清单 v2.2 第 4.6 节）

### 6.2 分享中心（路由 `/kb/share`）

**视图布局**：
- 顶部统计卡片：我的分享总数、今日访问量、即将过期数
- 分享列表表格：资源类型、资源标题、分享码（一键复制按钮）、提取码（脱敏显示 + 复制）、有效期、状态徽标（有效/已过期/已撤销）、访问次数、创建时间、操作列
- 详情抽屉：单条分享的访问日志时间线（IP、User-Agent、时间）

**交互与功能**：
- 创建分享入口：在文档/文件/网页详情页触发，弹窗选择提取码（自动生成或自定义）、有效期（1 天/7 天/30 天/永久）
- 分享码复制：点击复制按钮写入剪贴板，提示"已复制 https://kb.marschat.online/kb/share/{code}"
- 有效期管理：列表支持按"即将过期"筛选，过期后状态自动置灰
- 访问统计：调用分享详情接口查看 `viewCount`，详情抽屉展示访问日志
- 取消分享：调用 `DELETE /kb/api/share/{id}` 撤销，列表状态变为已撤销

**对应接口**：`POST /share`、`GET /share/list`、`DELETE /share/{id}`、`GET /share/verify/{code}`、`GET /share/detail/{code}`（详见接口规范清单 v2.2 第 4.4 节）

### 6.3 文件管理（路由 `/kb/file`）

**视图布局**：
- 左侧目录树：空间 → 文件夹层级，支持折叠/展开、右键新建文件夹
- 主区文件列表：图标/列表双视图，列含文件名、类型图标、大小、解析状态徽标（PENDING/PROCESSING/SUCCESS/FAILED）、星标、更新时间、操作
- 顶部上传区：拖拽上传热区 + 选择文件按钮，支持多文件、分片上传进度条
- 右侧详情面板：选中文件时展示元数据、解析状态、星标切换、移动、下载、删除操作

**交互与功能**：
- 上传：拖拽文件到热区 → 前端分片 → `POST /kb/api/file/upload` 逐片上传 → `POST /kb/api/file/merge` 合并触发解析
- 星标：点击星标图标切换 `PUT /kb/api/file/{id}/star`
- 移动：弹窗选择目标文件夹 → `PUT /kb/api/file/{id}/move`
- 下载：`GET /kb/api/file/{id}/download` 获取预签名 URL 直连 MinIO
- 删除：二次确认 → `DELETE /kb/api/file/{id}`（逻辑删除进回收站）
- 解析状态：列表轮询 `GET /kb/api/file/{id}/parse-status`，PROCESSING 显示进度，FAILED 显示错误并提供"重新解析"按钮 `POST /kb/api/file/{id}/reparse`

**对应接口**：文件 12 个接口 + Bucket 2 个接口（详见接口规范清单 v2.2 第 3 章）

### 6.4 运维操作日志（路由 `/kb/ops/log`）

**视图布局**：
- 顶部筛选栏：操作类型下拉（action，如 user.login、doc.create）、用户 ID 输入、时间范围选择器、查询按钮、重置按钮
- 日志表格：操作时间、用户名、操作类型、资源类型、资源 ID、详情摘要、客户端 IP、操作列（查看详情）
- 分页器：底部分页，默认每页 20 条

**交互与功能**：
- 操作记录分页查询：`GET /kb/api/ops/log/list`，支持 `action`、`userId`、`page`、`size` 参数
- 按 action 筛选：下拉选择常用操作类型快捷过滤
- 按 userId 筛选：定位某用户的所有操作轨迹
- 按时间范围筛选：日期选择器限定起止时间
- 详情查看：点击行展开/弹窗显示完整 `detail`、`userAgent`、`ip` 字段

**对应接口**：`GET /ops/log/list`（详见接口规范清单 v2.2 第 5.8 节）

## 7. 后端 Controller 规划

> kb-ops 模块共规划 **12 个 Controller**（8 个已有 + 4 个新增），统一挂在网关 `/kb/api/ops/**` 路由下。每个 Controller 提供 5 个标准接口：list 分页查询、{id} 详情、POST 新增、PUT 更新、DELETE 删除。

### 7.1 已有 Controller（8 个）

| 序号 | Controller | 路由前缀 | 接口数 | 说明 |
|------|-----------|---------|--------|------|
| 1 | HostController | `/ops/host` | 5 | 主机管理（list/{id}/POST/PUT/DELETE） |
| 2 | ServiceController | `/ops/service` | 5 | 服务管理（list/{id}/POST/PUT/DELETE） |
| 3 | DeploymentController（=ChangeLog） | `/ops/deployment` | 3 | 部署记录（对应 `ops_change_log` 表，list/recent/POST） |
| 4 | KnowledgeController | `/ops/knowledge` | 5 | 运维知识（list/{id}/POST/PUT/DELETE） |
| 5 | ImportController | `/ops/import` | 2 | 数据导入（JSON/CSV） |
| 6 | ConflictController | `/ops/conflict` | 3 | 矛盾检测（detect/list/resolve） |
| 7 | DashboardController | `/ops/dashboard` | 2 | 运维看板（数据/快照刷新） |
| 8 | OperationLogController | `/ops/log` | 2 | 操作日志（list/详情） |

### 7.2 新增 Controller（4 个）

| 序号 | Controller | 路由前缀 | 接口数 | 对应数据表 | 说明 |
|------|-----------|---------|--------|-----------|------|
| 9 | PortController | `/ops/port` | 5 | `ops_port` | 端口管理（list/{id}/POST/PUT/DELETE） |
| 10 | CredentialController | `/ops/credential` | 5 | `ops_credential` | 凭据管理（list/{id}/POST/PUT/DELETE，密码默认不返回） |
| 11 | DomainController | `/ops/domain` | 5 | `ops_domain` | 域名管理（list/{id}/POST/PUT/DELETE） |
| 12 | DependencyController | `/ops/dependency` | 5 | `ops_dependency` | 依赖关系（list/{id}/POST/PUT/DELETE） |

### 7.3 新增 Controller 接口清单（5 个 × 4 = 20 个）

#### 7.3.1 PortController（端口管理）

| 方法 | 路径 | 描述 |
|------|------|------|
| GET | `/kb/api/ops/port/list` | 端口分页列表（hostId/serviceId/keyword 筛选） |
| GET | `/kb/api/ops/port/{id}` | 端口详情 |
| POST | `/kb/api/ops/port` | 新增端口记录 |
| PUT | `/kb/api/ops/port/{id}` | 更新端口记录 |
| DELETE | `/kb/api/ops/port/{id}` | 删除端口记录 |

#### 7.3.2 CredentialController（凭据管理）

| 方法 | 路径 | 描述 |
|------|------|------|
| GET | `/kb/api/ops/credential/list` | 凭据分页列表（type/keyword 筛选） |
| GET | `/kb/api/ops/credential/{id}` | 凭据详情（`revealPassword=false` 默认不返回密码） |
| POST | `/kb/api/ops/credential` | 新增凭据（密码 AES-256-GCM 加密存储） |
| PUT | `/kb/api/ops/credential/{id}` | 更新凭据 |
| DELETE | `/kb/api/ops/credential/{id}` | 删除凭据 |

#### 7.3.3 DomainController（域名管理）

| 方法 | 路径 | 描述 |
|------|------|------|
| GET | `/kb/api/ops/domain/list` | 域名分页列表（keyword/status 筛选） |
| GET | `/kb/api/ops/domain/{id}` | 域名详情 |
| POST | `/kb/api/ops/domain` | 新增域名记录 |
| PUT | `/kb/api/ops/domain/{id}` | 更新域名记录 |
| DELETE | `/kb/api/ops/domain/{id}` | 删除域名记录 |

#### 7.3.4 DependencyController（依赖关系）

| 方法 | 路径 | 描述 |
|------|------|------|
| GET | `/kb/api/ops/dependency/list` | 依赖关系分页列表（serviceId 筛选） |
| GET | `/kb/api/ops/dependency/{id}` | 依赖关系详情 |
| POST | `/kb/api/ops/dependency` | 新增依赖关系 |
| PUT | `/kb/api/ops/dependency/{id}` | 更新依赖关系 |
| DELETE | `/kb/api/ops/dependency/{id}` | 删除依赖关系 |

> 接口完整请求/响应字段规范详见 `docs/接口规范清单_v1.md`（v2.2）第 5.9 ~ 5.12 节；数据表结构详见 `docs/私有化全端个人知识库_v6.md` "运维数据模型扩展"章节。
