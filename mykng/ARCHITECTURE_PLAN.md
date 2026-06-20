# Private-Knowledge-Base 微服务重构与实现计划

## 架构设计

### 服务拆分（轻量微服务，适配单机部署）

```
┌─────────────────────────────────────────────────────────┐
│                    Nginx (443)                            │
│              tools.marschat.online/kb/*                   │
└─────────────────────┬───────────────────────────────────┘
                      │
┌─────────────────────▼───────────────────────────────────┐
│              kb-gateway (端口 8080)                       │
│  Spring Cloud Gateway + JWT Auth Filter + 静态资源       │
│  路由：/api/auth/* → kb-auth:8081                        │
│        /api/file/* → kb-file:8082                        │
│        /api/*      → kb-knowledge:8083                   │
│        /api/ops/*  → kb-ops:8084                         │
│        /s/*        → 静态资源                             │
└─────────────────────┬───────────────────────────────────┘
     │           │           │           │
     ▼           ▼           ▼           ▼
┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐
│ kb-auth │ │ kb-file │ │kb-know- │ │ kb-ops  │
│ :8081   │ │ :8082   │ │ledge    │ │ :8084   │
│         │ │         │ │ :8083   │ │         │
│ 用户    │ │ 文件    │ │         │ │ 运维    │
│ JWT     │ │ 解析    │ │ 目录    │ │ 主机    │
│ Token   │ │ MinIO   │ │ 笔记    │ │ 端口    │
│         │ │         │ │ 网页    │ │ 账密    │
│         │ │         │ │ 搜索    │ │ 看板    │
│         │ │         │ │ 分享    │ │ 矛盾    │
└─────────┘ └─────────┘ └─────────┘ └─────────┘
     │           │           │           │
     └───────────┼───────────┼───────────┘
                 ▼           ▼
          ┌──────────┐ ┌──────────┐
          │ MySQL    │ │ MongoDB  │
          │ 8.0      │ │ 7.0      │
          └──────────┘ └──────────┘
          ┌──────────┐ ┌──────────┐
          │ Redis    │ │ MinIO    │
          │ 7        │ │          │
          └──────────┘ └──────────┘
          ┌──────────┐
          │ Meili-   │
          │ Search   │
          └──────────┘
```

### 数据库拆分（各服务独立 schema）

| 服务 | 数据库 | 表 |
|------|--------|-----|
| kb-auth | kb_auth | user, refresh_token, jwt_blacklist, ops_api_token |
| kb-file | kb_file | file, file_chunk + MongoDB file_content |
| kb-knowledge | kb_knowledge | folder, doc, web_page, tag, resource_tag, share, share_access_log, version, operation_log, bucket + MongoDB doc_content, web_content |
| kb-ops | kb_ops | ops_host, ops_port, ops_credential, ops_domain, ops_dependency, ops_change_log, ops_conflict, ops_snapshot |

### 服务间通信

- **同步**：OpenFeign（服务间 API 调用）
- **异步**：RabbitMQ（文件解析完成通知等，后续）
- **服务发现**：无（单机部署，直接 HTTP URL）

### 技术栈

| 组件 | 选型 |
|------|------|
| 基础框架 | Spring Boot 3.2 + JDK 21 |
| 网关 | Spring Cloud Gateway 4.1 |
| 服务间调用 | OpenFeign |
| ORM | MyBatis-Plus 3.5.6 |
| 认证 | JWT (jjwt 0.12.5) |
| 搜索引擎 | MeiliSearch |
| 对象存储 | MinIO |
| 缓存 | Redis |
| 内容存储 | MongoDB |
| 密码加密 | AES-256-GCM |
| 前端 | Vue3 + ElementPlus + Vite（不变）|

## 实施步骤

### Step 1: 创建父工程 + 通用模块
- kb-parent (pom.xml)
- kb-common (公共工具类、DTO、异常、统一返回)

### Step 2: 实现 kb-auth 服务
- 用户注册/登录
- JWT 双 Token
- API Token 管理（含 scope）
- CryptoUtil（AES-256-GCM）

### Step 3: 实现 kb-file 服务
- 分片上传（修复前后端不匹配）
- 文件解析（txt/md/pdf/docx + MeiliSearch 索引写入）
- MinIO 集成

### Step 4: 实现 kb-knowledge 服务
- 目录 CRUD
- 笔记 CRUD
- 网页收藏
- 全文搜索（修复索引名 + 总数计算）
- 分享功能
- 操作日志

### Step 5: 实现 kb-ops 服务
- 运维知识 CRUD（8 张 ops_* 表）
- 外部导入 API（含 JSON Schema 校验）
- 矛盾检测（6 种规则）
- 知识看板数据接口

### Step 6: 实现 kb-gateway 服务
- 路由配置
- JWT 鉴权过滤器
- 静态资源代理

### Step 7: 前端适配
- 更新 API 路径
- 新增知识看板页面（10 个 /ops/* 页面）
- 修复分片上传调用

### Step 8: Docker Compose + Nginx
- 多容器编排
- Nginx 配置更新
