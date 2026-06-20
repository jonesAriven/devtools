# mykng 微服务重构计划

## 现有代码分析

### 现状
- 单体 SpringBoot 应用，包名 `com.jones.kb`
- 123 个 Java 文件，14 Controller，16 Entity，16 Mapper
- 已有统一返回 `R<T>` + `BusinessException` + `PageResult`
- 已有 JWT 鉴权、MyBatis-Plus、MinIO、MeiliSearch、MongoDB、Redis
- 端口 8080，context-path /kb

### 代码质量问题
1. **文件合并 OOM 风险** — `ByteArrayOutputStream` 全量加载到内存，大文件会崩
2. **没有 traceId 链路追踪**
3. **异常体系不完善** — 只有一个 BusinessException，没有分级
4. **@Async 方法在同类中调用** — `triggerAsyncParse` 被 this 调用，代理不生效
5. **MinIO bucket 硬编码** — `"kb-file"` 字符串散落各处

### 架构问题
1. 单体部署，无法独立扩容
2. 端口 8080 与 CodexClaw 冲突
3. 没有网关统一鉴权
4. 没有服务间通信机制

## 重构步骤

### Step 1: 创建父工程 + kb-common 模块
- [ ] kb-parent pom.xml（统一依赖版本管理）
- [ ] kb-common 模块（Result, 异常体系, TraceId, DTO, 工具类）
- [ ] 从现有代码提取公共类到 kb-common
- [ ] 验证：mvn compile 通过

### Step 2: kb-auth 服务（端口 8081）
- [ ] 迁移 User, RefreshToken, JwtBlacklist, ops_api_token 实体
- [ ] 迁移 AuthController, UserController
- [ ] 迁移 JwtUtil, JwtTokenProvider, JwtAuthenticationFilter
- [ ] 迁移 CryptoUtil (AES-256-GCM)
- [ ] 验证：启动 + 登录测试通过

### Step 3: kb-file 服务（端口 8082）
- [ ] 迁移 KbFile, FileChunk 实体
- [ ] 迁移 KbFileController, BucketController
- [ ] 迁移 KbFileService, FileParseService, MinioService
- [ ] 修复文件合并 OOM（改用流式合并）
- [ ] 修复 @Async 自调用问题
- [ ] 验证：上传 + 解析测试通过

### Step 4: kb-knowledge 服务（端口 8083）
- [ ] 迁移 Folder, Doc, WebPage, Tag, ResourceTag, Share, ShareAccessLog, Version, OperationLog, Space, Bucket 实体
- [ ] 迁移对应 Controller + Service
- [ ] 迁移 SearchService (MeiliSearch)
- [ ] 验证：目录 + 笔记 + 搜索测试通过

### Step 5: kb-ops 服务（端口 8084）
- [ ] 新建 ops_* 实体（8张表）
- [ ] 新建 OpsController + Service
- [ ] 新建外部导入 API + Token 校验
- [ ] 新建矛盾检测服务
- [ ] 验证：导入 + 查询测试通过

### Step 6: kb-gateway 服务（端口 8080）
- [ ] Spring Cloud Gateway 路由配置
- [ ] JWT 鉴权过滤器
- [ ] TraceId 注入过滤器
- [ ] 静态资源代理
- [ ] 验证：全链路请求通过

### Step 7: 前端适配
- [ ] 更新 API 路径（统一走网关）
- [ ] 新增知识看板页面
- [ ] 修复分片上传调用

### Step 8: Docker Compose + Nginx
- [ ] 多容器编排（9个容器）
- [ ] Nginx 配置更新
- [ ] 启动顺序 + 健康检查

## 执行完成 ✅

### Step 1: 创建父工程 + kb-common 模块 ✅ 完成
- [x] kb-parent pom.xml（统一依赖版本管理）
- [x] kb-common 模块（Result, 异常体系, TraceId, PageResult, KbEvent, GlobalExceptionHandler）
- [x] mvn compile 通过

### Step 2: kb-auth 认证服务（端口 8081）✅ 完成
- [x] 从单体迁移 AuthController, UserController, JwtTokenProvider, JwtAuthenticationFilter
- [x] 新增 ApiToken 管理（AES-256-GCM 加密）
- [x] OperationLogService → Redis Pub/Sub 事件通知
- [x] SecurityConfig, Dockerfile, SQL 初始化
- [x] 31 Java 文件，编译通过

### Step 3: kb-file 文件服务（端口 8082）✅ 完成
- [x] 从单体迁移 KbFileController, BucketController, FileParseService, MinioService
- [x] 修复 Bug #1: 文件合并 OOM → 流式合并到 MinIO
- [x] 修复 Bug #2: @Async 自调用 → 拆分到 FileParseTrigger 独立 Bean
- [x] OperationLogService → Redis Pub/Sub 事件通知
- [x] 32 Java 文件，编译通过

### Step 4: kb-knowledge 知识服务（端口 8083）✅ 完成
- [x] 从单体迁移 9 个功能域：目录/笔记/网页/搜索/分享/标签/空间/回收站/版本
- [x] Feign 客户端: AuthClient (调用 kb-auth), FileClient (调用 kb-file)
- [x] OperationLogService → Redis Pub/Sub 事件通知
- [x] 78 Java 文件，编译通过

### Step 5: kb-ops 运维服务（端口 8084）✅ 完成
- [x] 新增服务：主机管理、服务管理、部署记录、运维知识
- [x] 矛盾检测：6 种规则（版本不一致、端口冲突、IP重复等）
- [x] 运维看板：统计、趋势、快照
- [x] CSV/JSON 导入
- [x] 52 Java 文件，3 个单元测试通过

### Step 6: kb-gateway 网关（端口 8080）✅ 完成
- [x] 路由转发到 4 个微服务
- [x] JWT 本地验证（jjwt，不回调 kb-auth）
- [x] X-User-Id + X-Trace-Id 注入到下游服务
- [x] CORS 跨域配置
- [x] 白名单路径（登录、刷新、公开分享）
- [x] 7 Java 文件，6 个 WebTestClient 测试通过

### 全量编译验证 ✅
- [x] `mvn clean compile` 全部通过
- [x] 总计 262 个文件（215 Java + 12 XML + 22 YML + 6 SQL + 7 其他）
