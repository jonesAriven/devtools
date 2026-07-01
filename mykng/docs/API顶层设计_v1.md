# MyKNG 知识库平台 — API 设计文档

| 属性 | 值 |
|------|-----|
| 版本 | v1.0 |
| 创建日期 | 2026-06-28 |
| 适用版本 | mykng v7（微服务架构） |
| SOP 合规 | 满足《软件工程开发流程规范_SOP_V1.1》阶段0要求 |
| 详细接口清单 | 见 [接口规范清单_v1.md](接口规范清单_v1.md)（123 个接口完整规范） |

> 本文档为 API 顶层设计，定义统一规范、错误码、认证机制、路由策略。详细每个接口的入参/出参/示例见接口规范清单。

---

## 1. API 设计原则

1. **RESTful 风格**：资源用名词、操作用 HTTP 方法（GET/POST/PUT/DELETE）
2. **统一前缀**：所有 API 路径带上下文路径前缀 `/kb/api/`
3. **统一返回格式**：所有接口返回 `Result<T>` 结构
4. **统一错误码**：业务错误用 4xx，系统错误用 5xx，错误码全局唯一
5. **幂等性**：所有写操作支持幂等（重复调用结果一致）
6. **分页规范**：列表接口统一用 `PageResult<T>`，参数 `page`+`size`
7. **认证**：除登录/注册/健康检查外，所有接口需 Bearer Token
8. **越权防护**：接口内校验当前用户是否有权操作目标资源
9. **敏感数据脱敏**：密码/密钥/Token 不返回明文
10. **链路追踪**：所有响应包含 `traceId`，由网关注入并透传

---

## 2. 统一返回格式

### 2.1 成功响应

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "username": "admin"
  },
  "traceId": "a1b2c3d4e5f6"
}
```

### 2.2 分页响应

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "records": [
      { "id": 1, "name": "doc1" },
      { "id": 2, "name": "doc2" }
    ],
    "total": 100,
    "page": 1,
    "size": 10,
    "pages": 10
  },
  "traceId": "a1b2c3d4e5f6"
}
```

### 2.3 错误响应

```json
{
  "code": 401,
  "message": "用户名或密码错误",
  "data": null,
  "traceId": "a1b2c3d4e5f6"
}
```

---

## 3. 错误码对照表

| 错误码 | HTTP 状态 | 含义 | 触发场景 |
|--------|----------|------|---------|
| 200 | 200 | 成功 | 正常请求 |
| 400 | 400 | 参数校验失败 | 必填项缺失、格式错误、长度超限 |
| 401 | 401 | 未认证 | 未登录、Token 过期、Token 无效 |
| 403 | 403 | 无权限 | 越权访问、账号被禁用 |
| 404 | 404 | 资源不存在 | 查询的记录不存在 |
| 409 | 409 | 资源冲突 | 重复创建、版本冲突 |
| 429 | 429 | 请求过多 | 触发限流 |
| 500 | 500 | 服务器内部错误 | 未捕获异常、数据库异常 |
| 503 | 503 | 服务不可用 | 依赖服务挂了（降级） |

**业务错误码细分**（在 message 中体现具体业务原因）：
- 40001: 用户名或密码错误
- 40002: RefreshToken 无效
- 40003: 账号已被禁用
- 40004: 文件分片不完整
- 40005: 文档已被锁定
- 40006: 分享链接已过期
- 40007: 分享密码错误
- 40008: 端口冲突
- 40009: 凭据重复
- 40010: 依赖循环

---

## 4. 认证机制

### 4.1 JWT Token

- **accessToken**：有效期 15 分钟，放在 `Authorization: Bearer <token>` 请求头
- **refreshToken**：有效期 7 天，用于刷新 accessToken，一次性使用
- **签名算法**：HS256
- **密钥**：从环境变量 `JWT_SECRET` 读取（≥256 bits）
- **载荷**：`userId`、`username`、`tokenType`（access/refresh）、`iat`、`exp`

### 4.2 API Token（脚本调用）

- 用户可在"个人设置"创建 API Token
- API Token 加密存储（AES-256-GCM）
- 使用方式：`X-API-Token: <token>` 请求头
- 支持吊销，吊销后立即失效

### 4.3 鉴权流程

```
客户端请求
  │
  ▼
kb-gateway
  ├─ 提取 Authorization / X-API-Token
  ├─ JWT 校验（过期/黑名单/格式）
  ├─ 注入 X-User-Id / X-Username 请求头
  ├─ 注入 X-Trace-Id 请求头（如客户端未传）
  └─ 转发到后端服务
       │
       ▼
  后端 Controller
       ├─ 从 X-User-Id 获取当前用户
       ├─ 业务逻辑中校验资源归属（越权防护）
       └─ 返回 Result<T>
```

### 4.4 白名单路径（无需鉴权）

- `POST /kb/api/auth/login` — 登录
- `POST /kb/api/auth/refresh` — 刷新 Token
- `GET /kb/api/share/{token}` — 访问公开分享
- `POST /kb/api/share/{token}/verify` — 校验分享密码
- `GET /actuator/health` — 健康检查

---

## 5. 路由策略

### 5.1 网关路由表

| 路由 ID | 匹配路径（`/kb/api` 前缀下） | 转发目标 | StripPrefix |
|---------|----------------------------|---------|------------|
| kb-auth | `/auth/**`, `/user/**`, `/token/**` | kb-auth:8081 | 2 |
| kb-file | `/file/**`, `/bucket/**` | kb-file:8082 | 2 |
| kb-knowledge | `/doc/**`, `/folder/**`, `/web/**`, `/search/**`, `/share/**`, `/tag/**`, `/space/**`, `/trash/**`, `/version/**` | kb-knowledge:8083 | 2 |
| kb-ops | `/ops/**`, `/log/**` | kb-ops:8084 | 2 |
| kb-intelligence | `/intelligence/**` | kb-intelligence:8086 | 2 |

### 5.2 示例

客户端请求：`GET http://kb.marschat.online/kb/api/doc/list?page=1&size=10`

1. 腾讯云2号 Nginx（:443）SSL 终结 → 反代到 mykng 本地 Nginx
2. mykng 本地 Nginx（:80）匹配 `/kb/api/` → 反代到 kb-gateway:8090
3. kb-gateway 匹配 `/kb/api/doc/**` → JWT 鉴权 → 注入 traceId → StripPrefix=2 → 转发到 kb-knowledge:8083/doc/list
4. kb-knowledge 处理业务 → 返回 `Result<PageResult<DocVO>>`

---

## 6. 接口清单（按服务分组）

### 6.1 kb-auth（12 个接口）

| 方法 | 路径 | 描述 | 鉴权 |
|------|------|------|------|
| POST | /auth/login | 登录 | 否 |
| POST | /auth/logout | 登出 | 是 |
| POST | /auth/refresh | 刷新 Token | 否 |
| GET | /user/profile | 获取当前用户信息 | 是 |
| PUT | /user/profile | 更新当前用户信息 | 是 |
| PUT | /user/password | 修改密码 | 是 |
| GET | /user/list | 用户列表（管理员） | 是（管理员） |
| POST | /user | 创建用户（管理员） | 是（管理员） |
| PUT | /user/{id}/status | 启用/禁用用户（管理员） | 是（管理员） |
| DELETE | /user/{id} | 删除用户（管理员） | 是（管理员） |
| POST | /token | 创建 API Token | 是 |
| GET | /token/list | API Token 列表 | 是 |

### 6.2 kb-file（13 个接口）

| 方法 | 路径 | 描述 | 鉴权 |
|------|------|------|------|
| POST | /file/upload | 上传分片 | 是 |
| POST | /file/merge | 合并分片 | 是 |
| GET | /file/{id} | 获取文件信息 | 是 |
| GET | /file/{id}/download | 下载文件 | 是 |
| DELETE | /file/{id} | 删除文件 | 是 |
| GET | /file/list | 文件列表 | 是 |
| POST | /file/{id}/move | 移动文件 | 是 |
| POST | /bucket | 创建桶（管理员） | 是（管理员） |
| DELETE | /bucket/{name} | 删除桶（管理员） | 是（管理员） |
| GET | /bucket/list | 桶列表 | 是 |
| GET | /bucket/{name}/objects | 桶内对象列表 | 是 |
| POST | /file/parse/{id} | 手动触发解析 | 是 |
| GET | /file/{id}/parse-status | 查询解析状态 | 是 |

### 6.3 kb-knowledge（51 个接口）

详细接口清单见 [接口规范清单_v1.md](接口规范清单_v1.md) 第 4 章，按模块组织：
- 空间管理（5 个）：CRUD + 切换
- 文件夹管理（6 个）：CRUD + 移动 + 排序
- 文档管理（8 个）：CRUD + 移动 + 发布/撤回
- 版本管理（4 个）：列表 + 回滚 + 对比
- 标签管理（5 个）：CRUD + 绑定
- 搜索（4 个）：全文搜索 + 高级搜索 + 建议 + 重建索引
- 分享（6 个）：创建 + 访问 + 校验 + 列表 + 吊销 + 访问日志
- 回收站（4 个）：列表 + 恢复 + 彻底删除 + 清空
- 网页收藏（5 个）：CRUD + 移动
- 资源标签推荐（4 个）：基于内容/历史/标签/混合推荐

#### 6.3.1 搜索接口详情

搜索模块基于 **MeiliSearch** 全文搜索引擎，支持跨资源类型统一搜索。当前已实现 2 个接口：

| 方法 | 路径 | 描述 | 鉴权 | 搜索引擎 |
|------|------|------|------|---------|
| GET | `/search` | 全文搜索（文件+笔记+网页） | 是 | MeiliSearch（主） / MySQL LIKE（降级） |
| GET | `/search/suggest` | 搜索建议 | 是 | 空实现（返回空列表） |

**GET /search 请求参数**：
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| q | string | 是 | 搜索关键词 |
| type | string | 否 | 资源类型过滤：file/doc/web，不传或为 all 时搜全部 |
| folderId | long | 否 | 文件夹过滤 |
| tagId | long | 否 | 标签过滤（走标签关联查询，非全文搜索） |
| page | int | 否 | 页码，默认 1 |
| size | int | 否 | 每页条数，默认 20 |

**GET /search 响应字段**（PageResult 内）：
| 字段 | 类型 | 说明 |
|------|------|------|
| id | string | 资源 ID |
| type | string | 资源类型：file/doc/web |
| title | string | 资源标题/名称 |
| name | string | 资源名称（同 title，兼容用） |
| content | string | 资源内容（MeiliSearch 原始返回） |
| highlight | string | 搜索高亮片段（含 `<em>` 标签） |
| starred | boolean | 是否收藏 |
| createdAt | string | 创建时间 |

### 6.4 kb-intelligence（12 个接口）

知识引擎模块提供运维文档智能解析与实体抽取能力，搜索接口如下：

| 方法 | 路径 | 描述 | 鉴权 | 搜索引擎 |
|------|------|------|------|---------|
| POST | `/intelligence/machine/search` | 知识引擎文档搜索 | 是（管理员） | MySQL LIKE |

**POST /intelligence/machine/search 请求体**：
| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| query | string | 否 | 搜索关键词（匹配标题/摘要/标签/路径） |
| docTypes | string[] | 否 | 文档类型过滤：TABLE/PLAN/TIMELINE/GRAPH/RULE/GENERAL |
| tags | string[] | 否 | 标签过滤 |
| page | int | 否 | 页码，默认 1 |
| size | int | 否 | 每页条数，默认 20 |

### 6.5 kb-ops（47 个接口）

详细接口清单见 [接口规范清单_v1.md](接口规范清单_v1.md) 第 5 章，按模块组织：
- 运维看板（4 个）：汇总 + 趋势 + 矛盾数 + 资源分布
- 主机管理（5 个）：CRUD + 导入
- 服务管理（5 个）：CRUD + 导入
- 端口管理（5 个）：CRUD + 冲突检测
- 凭据管理（5 个）：CRUD + 解密
- 依赖管理（5 个）：CRUD + 循环检测
- 域名管理（5 个）：CRUD + 解析
- 矛盾检测（4 个）：列表 + 详情 + 解决 + 忽略
- 部署记录（4 个）：列表 + 详情 + 创建 + 删除
- 运维知识（4 个）：CRUD + 搜索
- 操作日志（3 个）：列表 + 详情 + 导出
- 数据导入（1 个）：批量导入

---

## 7. 接口规范示例

### 7.1 登录接口

```
POST /kb/api/auth/login
Content-Type: application/json

请求体：
{
  "username": "admin",
  "password": "admin123"
}

成功响应（200）：
{
  "code": 200,
  "message": "success",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
    "expiresIn": 900000,
    "user": {
      "id": 1,
      "username": "admin",
      "nickname": "管理员"
    }
  },
  "traceId": "a1b2c3d4e5f6"
}

失败响应（401）：
{
  "code": 401,
  "message": "用户名或密码错误",
  "data": null,
  "traceId": "a1b2c3d4e5f6"
}
```

### 7.2 创建文档接口

```
POST /kb/api/doc
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
Content-Type: application/json

请求体：
{
  "spaceId": 1,
  "folderId": 2,
  "title": "新建文档",
  "content": "# Hello World\n\n这是文档内容",
  "type": "MARKDOWN"
}

成功响应（200）：
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 100,
    "spaceId": 1,
    "folderId": 2,
    "title": "新建文档",
    "type": "MARKDOWN",
    "status": "DRAFT",
    "createdAt": "2026-06-28T10:00:00",
    "updatedAt": "2026-06-28T10:00:00"
  },
  "traceId": "a1b2c3d4e5f6"
}
```

### 7.3 分页查询接口

```
GET /kb/api/doc/list?spaceId=1&folderId=2&page=1&size=10&keyword=知识库
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...

成功响应（200）：
{
  "code": 200,
  "message": "success",
  "data": {
    "records": [
      { "id": 1, "title": "知识库设计文档", ... },
      { "id": 2, "title": "知识库部署方案", ... }
    ],
    "total": 100,
    "page": 1,
    "size": 10,
    "pages": 10
  },
  "traceId": "a1b2c3d4e5f6"
}
```

### 7.4 全文搜索接口

```
GET /kb/api/search?q=知识库&type=doc&page=1&size=20
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...

成功响应（200）：
{
  "code": 200,
  "message": "success",
  "data": {
    "records": [
      {
        "id": "1",
        "type": "doc",
        "title": "知识库设计文档",
        "name": "知识库设计文档",
        "content": "# 知识库设计文档...",
        "highlight": "...<em>知识库</em>设计文档...",
        "starred": false,
        "createdAt": "2026-06-28T10:00:00"
      }
    ],
    "total": 42,
    "page": 1,
    "size": 20,
    "pages": 3
  },
  "traceId": "a1b2c3d4e5f6"
}
```

**说明**：
- 主搜索引擎为 MeiliSearch，支持中文分词、模糊匹配、相关性排序
- 当 MeiliSearch 不可用时自动降级到 MySQL LIKE 查询（doc + web 类型，file 暂不支持降级）
- 搜索结果按 userId 隔离，用户只能搜索到自己的资源
- 搜索建议接口（`/search/suggest`）当前为空实现，返回空列表

---

## 8. 限流策略

| 接口类型 | 限流策略 | 说明 |
|---------|---------|------|
| 登录接口 | 10 次/分钟/IP | 防止暴力破解 |
| 文件上传 | 100 次/分钟/用户 | 防止滥用 |
| 搜索接口 | 60 次/分钟/用户 | 防止爬取 |
| 其他接口 | 1000 次/分钟/用户 | 默认限流 |
| API Token 调用 | 600 次/分钟/Token | 脚本调用限流 |

超限返回：
```json
{
  "code": 429,
  "message": "请求过于频繁，请稍后再试",
  "data": null,
  "traceId": "a1b2c3d4e5f6"
}
```

---

## 9. CORS 配置

生产环境仅允许指定域名访问：

```
Access-Control-Allow-Origin: https://kb.marschat.online
Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS
Access-Control-Allow-Headers: Authorization, Content-Type, X-API-Token, X-Trace-Id
Access-Control-Allow-Credentials: true
Access-Control-Max-Age: 3600
```

开发环境允许 `http://localhost:*`。

---

## 10. 接口版本管理

- 当前版本：v1（无版本前缀，路径为 `/kb/api/...`）
- 未来版本：v2 路径为 `/kb/api/v2/...`，v1 保持兼容
- 破坏性变更（删除字段/修改语义）必须升版本号
- 非破坏性变更（新增字段/新增接口）直接在 v1 上加

---

## 11. 接口安全要求（SOP V1.1 附录D）

- [x] 所有接口有权限校验（白名单除外）
- [x] 越权防护：接口内校验资源归属
- [x] 参数校验：后端必做（前端校验可绕过）
- [x] SQL 参数化查询：MyBatis 用 `#{}` 不用 `${}`
- [x] 敏感数据加密存储（密码 bcrypt）、脱敏返回
- [x] HTTPS 强制（生产环境）
- [x] 接口限流（见第 8 节）
- [x] 文件上传校验（类型白名单、大小限制、内容检测）
- [x] 错误信息不暴露系统细节
- [x] CORS 配置正确（不允许 *）
- [x] 依赖包定期扫描（OWASP Dependency-Check）
- [x] 日志中无敏感信息

---

## 变更记录

| 版本 | 日期 | 变更内容 | 变更人 |
|------|------|---------|--------|
| v1.0 | 2026-06-28 | 初始版本，按 SOP V1.1 阶段0要求创建 | AI（Trae） |
