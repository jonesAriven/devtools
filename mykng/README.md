# mykng 个人知识库微服务

> 基于 Spring Cloud 的私有化全端个人知识库系统，支持文档管理、文件存储、全文搜索、运维知识管理、智能知识导入。

## 快速开始

### 环境要求
- Docker 24+ / Docker Compose v2+
- JDK 21（开发环境）
- Maven 3.9+（开发环境）

### 一键部署
```bash
# 1. 配置环境变量
cp .env.example .env
# 编辑 .env 修改密码/密钥

# 2. 构建并启动
bash scripts/deploy.sh build
bash scripts/deploy.sh up

# 3. 健康检查
bash scripts/health-check.sh
```

### 访问地址
- API网关：http://localhost:8090/kb/api/
- 登录接口：POST http://localhost:8090/kb/api/auth/login

## 微服务清单

| 服务 | 端口 | 职责 |
|------|------|------|
| kb-gateway | 8090 | API网关（路由/JWT鉴权/限流） |
| kb-auth | 8081 | 认证服务（JWT/用户/API Token） |
| kb-file | 8082 | 文件服务（MinIO/分块上传/解析） |
| kb-knowledge | 8083 | 知识服务（文档/搜索/分享/版本） |
| kb-ops | 8084 | 运维服务（主机/服务/部署/看板） |
| kb-intelligence | 8086 | 智能服务（知识导入/解析/渲染） |

## 项目结构
```
mykng/
├── kb-auth/            # 认证微服务
├── kb-file/            # 文件微服务
├── kb-knowledge/       # 知识微服务
├── kb-ops/             # 运维微服务
├── kb-intelligence/    # 智能微服务
├── kb-gateway/         # API网关
├── kb-common/          # 公共模块
├── docs/               # 项目文档
├── init-sql/           # 数据库初始化脚本
├── sql/                # 版本化迁移脚本（Flyway）
├── scripts/            # 运维脚本
├── docker-compose.yml   # 容器编排
├── .env.example        # 环境变量示例
├── CHANGELOG.md        # 变更记录
└── README.md           # 本文件
```

## 文档索引
- [系统架构](docs/architecture.md)
- [数据库设计](docs/database-design.md)
- [部署文档](docs/deployment.md)
- [操作手册](docs/operation-manual.md)
- [变更记录](CHANGELOG.md)

## 技术栈
Java 21 / Spring Boot 3.2.5 / MyBatis-Plus / MySQL 8 / Redis 7 / MongoDB 7 / MinIO / MeiliSearch / Docker
