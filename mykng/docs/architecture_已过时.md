# 系统架构文档

| 属性 | 值 |
|------|-----|
| 版本 | v1.4.0 |
| 更新日期 | 2026-06-28 |

## 1. 架构概览

mykng 个人知识库采用 **Spring Cloud 微服务架构**，通过 Docker Compose 编排部署。

```
                    ┌─────────────┐
                    │   前端/Web   │
                    └──────┬──────┘
                           │ HTTP
                    ┌──────▼──────┐
                    │  kb-gateway  │  :8090 (宿主机映射)
                    │  (路由/鉴权)  │  JWT验证 + 限流 + TraceId
                    └──────┬──────┘
                           │ Docker 内网 (kb-net)
          ┌────────┬───────┼───────┬────────┐
          │        │       │       │        │
   ┌──────▼──┐ ┌───▼───┐ ┌▼─────┐ ┌▼─────┐ ┌▼──────────────┐
   │kb-auth  │ │kb-file│ │kb-   │ │kb-   │ │kb-intelligence│
   │:8081    │ │:8082  │ │know  │ │ops   │ │:8086          │
   │JWT/用户 │ │MinIO │ │:8083 │ │:8084 │ │知识导入/渲染   │
   └────┬────┘ └───┬───┘ └──┬───┘ └──┬───┘ └───────┬───────┘
        │          │        │        │             │
        └──────────┴────────┴────────┘             │
                   │                                │
          ┌────────┴────────────────────────────────┘
          │
   ┌──────▼──────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐
   │   MySQL     │  │  Redis   │  │ MongoDB  │  │  MinIO   │  │MeiliSearch│
   │   :3306     │  │  :6379   │  │  :27017  │  │  :9000   │  │  :7700   │
   │ 元数据/关系  │  │ 缓存/会话 │  │ 文档内容  │  │ 文件存储  │  │ 全文搜索  │
   └─────────────┘  └──────────┘  └──────────┘  └──────────┘  └──────────┘
```

## 2. 微服务清单

| 服务 | 端口 | 职责 | 数据库 |
|------|------|------|--------|
| kb-gateway | 8090→8080 | API网关：路由转发、JWT鉴权、限流、CORS、TraceId | - |
| kb-auth | 8081 | 认证服务：JWT签发/验证、用户CRUD、API Token、刷新令牌 | kb_auth |
| kb-file | 8082 | 文件服务：MinIO对象存储、分块上传、文件解析 | kb_file |
| kb-knowledge | 8083 | 知识服务：文档/文件夹/搜索/分享/标签/空间/版本 | kb_knowledge |
| kb-ops | 8084 | 运维服务：主机/服务/部署记录/知识看板/矛盾检测 | kb_ops |
| kb-intelligence | 8086 | 智能服务：知识导入、文档解析、双维度渲染 | kb_intelligence |
| kb-common | - | 公共模块：统一响应、全局异常、TraceId、分页 | - |

## 3. 技术栈

| 层面 | 技术 |
|------|------|
| 语言 | Java 21 |
| 框架 | Spring Boot 3.2.5 + Spring Cloud 2023.0 |
| ORM | MyBatis-Plus 3.5.6 |
| 数据库 | MySQL 8.0 (utf8mb4_unicode_ci) |
| 缓存 | Redis 7 |
| 文档存储 | MongoDB 7 |
| 对象存储 | MinIO |
| 搜索引擎 | MeiliSearch v1.12 |
| 网关 | Spring Cloud Gateway (Reactive) |
| 认证 | JWT (HMAC-SHA256) + Refresh Token |
| 加密 | AES-256-GCM (凭据加密) |
| 容器 | Docker + Docker Compose |
| JDK | Eclipse Temurin 21 JRE Alpine |

## 4. 统一上下文路径

所有 API 统一前缀：`/kb/api/{module}/{operation}`

- `KB_CONTEXT` 环境变量控制前缀（默认 `/kb`）
- Gateway 通过 `StripPrefix=2` 去掉 `/kb/api`，后端收到 `/{module}/{operation}`
- 前端 Vite `base` 配置与之保持一致

## 5. 鉴权链路

```
客户端请求 → kb-gateway
  │
  ├─ 白名单路径？ → 直接放行（/kb/api/auth/login 等）
  │
  ├─ 提取 Authorization: Bearer <token>
  │   ├─ 无token → 401
  │   ├─ token无效 → 401
  │   └─ token有效 → 解析 userId/userName
  │
  └─ 注入请求头 → 转发到下游服务
      ├─ X-User-Id: <userId>
      ├─ X-Username: <username>
      └─ X-Trace-Id: <uuid32>
```

下游服务通过 `GatewayAuthFilter` 从 `X-User-Id` 头提取用户身份，构建 SecurityContext。

## 6. 数据库分离

每个微服务独立数据库，禁止跨库JOIN：

| 数据库 | 服务 | 核心表 |
|--------|------|--------|
| kb_auth | kb-auth | user, refresh_token, jwt_blacklist, api_token |
| kb_file | kb-file | bucket, kb_file, file_chunk |
| kb_knowledge | kb-knowledge | space, folder, doc, tag, share, version, web_page |
| kb_ops | kb-ops | host, ops_service, port, credential, domain, dependency, deployment_record, ops_knowledge, operation_log, ops_conflict, ops_snapshot |
| kb_intelligence | kb-intelligence | kn_doc, kn_host, kn_service, kn_port, kn_credential, kn_domain, kn_dependency, kn_command, kn_timeline, kn_doc_entity_ref |

## 7. 服务间通信

- **同步**：OpenFeign（kb-knowledge → kb-auth 验证用户、kb-knowledge → kb-file 获取文件）
- **异步**：Spring Event（kb-file 解析完成 → kb-knowledge 索引更新）
- **所有跨服务调用自动传递 X-Trace-Id**（通过 FeignTraceIdInterceptor）

## 8. 部署拓扑

- **开发环境**：本地 MySQL/Redis，各服务独立启动（`mvn spring-boot:run`）
- **生产环境**：Docker Compose 编排，全部在 `mykng-debain` 服务器
  - 服务器：VirtualBox Debian 13.5 VM
  - Tailscale IP：100.93.36.113
  - LAN IP：192.168.31.105
  - 代码同步：SMB 共享（`/mnt/shared/devtools/mykng/`）
