# 变更记录 (CHANGELOG)

本项目遵循 [Semantic Versioning](https://semver.org/lang/zh-CN/)。

## [1.5.0] - 2026-07-03

### 变更
- **剥离 kb-ops 模块**：从代码库彻底删除 kb-ops 微服务（82 个 Java 文件、13 个 Controller、目录、pom 注册、网关路由、docker-compose 服务、CI/CD 配置、脚本、SQL 全部清理），微服务数量由 6 个精简为 5 个
- **OperationLog 迁移至 kb-auth**：操作日志能力从 kb-ops 迁移到 kb-auth，Controller 路径由 `/ops/log` 改为 `/auth/log`，网关路由 `/kb/api/log/**` 现由 kb-auth 处理
- **新建 devtools portal 工具看板**：作为 devtools 所有系统/工具的总入口看板（Vue3 + Vite + Element Plus），部署在 mykng-debain，访问地址 https://tools.marschat.online/portal/

## [1.4.0] - 2026-06-28

### 新增
- **kb-intelligence 微服务**：知识导入与双维度渲染引擎，支持从龙虾记忆库导入知识
  - 四层数据模型：索引层(MySQL) / 参数层(MySQL) / 内容层(MongoDB) / 向量层(MeiliSearch)
  - 五种文档类型解析：结构化表格(A) / 计划文档(B) / 时间线(C) / 关系图(D) / 规则文档(E)
  - 混合解析引擎：规则解析 + LLM提取(可选)
  - L1-L4机器可读API + 人类可读API
  - 内容存储抽象：MongoDB(prod) / 内存(dev) 自动切换
- **kb-intelligence 单元测试**：7个测试类，86个测试方法，覆盖Parser/Service/Query全链路
- **kb-gateway 单元测试**：3个测试类，17个测试方法，覆盖JwtAuthFilter/TraceIdFilter/Properties
- **运维脚本补齐**：rollback.sh / health-check.sh / build.sh
- **.env.example**：环境变量示例文件
- **docs/architecture.md**：系统架构文档
- **docs/database-design.md**：数据库设计文档
- **docs/deployment.md**：部署文档
- **docs/operation-manual.md**：操作手册
- **init-sql/06-kb-intelligence.sql**：kb-intelligence数据库初始化脚本

### 修复
- kb-intelligence application-prod.yml 增加 createDatabaseIfNotExist=true
- kb-intelligence listTimelines 接口改为返回VO，不再直接暴露Entity
- kb-intelligence service层重构为接口/实现分离

### 变更
- gateway 路由表新增 kb-intelligence 路由规则

## [1.3.0] - 2026-06-27

### 新增
- kb-ops 运维微服务：主机/服务/部署记录/运维知识/看板/矛盾检测/导入
- kb-ops 单元测试：6个测试类，覆盖HostService/CredentialService/CryptoUtil/ConflictDetection
- init-sql 补齐 04-kb-ops.sql

### 变更
- docker-compose.yml 新增 kb-ops 服务编排
- gateway 路由表新增 kb-ops 路由规则

## [1.2.0] - 2026-06-26

### 新增
- kb-knowledge 知识库微服务：文档/文件夹/搜索/分享/标签/空间/回收站/版本/Web收藏
- kb-knowledge 单元测试：8个测试类，覆盖所有Service层核心逻辑
- MeiliSearch 全文搜索引擎集成
- MongoDB 文档内容存储

### 变更
- docker-compose.yml 新增 mongodb / meilisearch 服务
- gateway 路由表新增 kb-knowledge 路由规则

## [1.1.0] - 2026-06-25

### 新增
- kb-file 文件微服务：MinIO对象存储 / 分块上传 / 文件解析 / 存储桶管理
- kb-file 单元测试：3个测试类，覆盖KbFileService/FileParse/集成测试
- MinIO 对象存储集成

### 变更
- docker-compose.yml 新增 minio 服务
- gateway 路由表新增 kb-file 路由规则

## [1.0.0] - 2026-06-24

### 新增
- **项目初始化**：mykng 个人知识库微服务架构
- kb-auth 认证微服务：JWT认证 / 用户管理 / API Token / 刷新令牌
- kb-gateway 网关微服务：路由转发 / JWT鉴权 / 限流 / 链路追踪
- kb-common 公共模块：统一响应 / 全局异常 / TraceId / 分页
- docker-compose.yml 编排：MySQL / Redis / 基础设施 + 微服务
- init-sql 数据库初始化脚本：01-create-databases / 02-kb-auth / 03-kb-file
- 运维脚本：deploy.sh / backup.sh / init-db.sh / pull-base-image.sh
