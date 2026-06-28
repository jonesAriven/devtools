# mykng SQL 版本化脚本

> mykng 知识库微服务 - Flyway 版本化数据库脚本体系（SOP 附录 G 要求）

## 目录结构

```
sql/
├── V1__init_schema.sql            # V1 初始化 Schema（合并 init-sql/01~06）
├── V1__init_schema_rollback.sql   # V1 回滚脚本（DROP 所有表和数据库）
├── V1__init_schema_verify.sql     # V1 校验脚本（校验表/列/索引/字符集）
├── data/
│   ├── seed_data.sql              # 种子数据（admin 用户、默认空间、示例运维数据）
│   └── test_data.sql              # 测试数据（测试用户、测试文档等，测试完可清理）
└── README.md                      # 本说明文档
```

## 数据库概览

| 数据库 | 用途 | 表数量 | 主要表 |
|--------|------|--------|--------|
| `kb_auth` | 认证服务 | 4 | user, refresh_token, jwt_blacklist, ops_api_token |
| `kb_file` | 文件服务 | 3 | file, file_chunk, bucket |
| `kb_ops` | 运维服务 | 11 | ops_host, ops_service, ops_change_log, ops_knowledge, ops_conflict, ops_snapshot, operation_log, ops_port, ops_credential, ops_domain, ops_dependency |
| `kb_knowledge` | 知识库服务 | 9 | space, folder, doc, web_page, tag, resource_tag, share, share_access_log, version |
| `kb_intelligence` | 知识引擎服务 | 11 | kn_doc, kn_host, kn_service, kn_port, kn_credential, kn_domain, kn_dependency, kn_command, kn_timeline, kn_doc_entity_ref |

**合计：5 个数据库，38 张表**

## 文件说明

### V1__init_schema.sql
- **用途**：V1 版本初始化脚本，合并自 `init-sql/01-create-databases.sql` ~ `06-kb-intelligence.sql`
- **内容**：创建 5 个数据库及 38 张表的完整 DDL，并内置 admin 用户和默认 bucket 的种子数据
- **特性**：
  - 全部使用 `CREATE DATABASE/TABLE IF NOT EXISTS`，可重复执行
  - 所有表使用 `ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci`
  - 按依赖顺序执行：建库 → kb_auth → kb_file → kb_ops → kb_knowledge → kb_intelligence
- **来源**：保留原 `init-sql/` 目录不动，本文件为版本化副本

### V1__init_schema_rollback.sql
- **用途**：回滚 V1 创建的所有表和数据库
- **警告**：⚠️ **此脚本会删除所有数据，执行前请务必备份！**
- **顺序**：按建表的逆序删除表，最后删除数据库
- **使用场景**：仅在需要完全回滚 V1 时使用；生产环境请使用 V2__*.sql 进行增量变更

### V1__init_schema_verify.sql
- **用途**：校验 V1 执行后的表结构完整性（SOP V1.1 要求）
- **校验内容**：
  1. 数据库存在性（5 个库）
  2. 表存在性（38 张表，按库分组校验）
  3. 关键列存在性（每库抽样核心表）
  4. 关键索引存在性（主键、唯一索引、普通索引）
  5. 字符集校验（全部应为 `utf8mb4_unicode_ci`）
  6. 种子数据校验（admin 用户、默认 bucket）
  7. 表行数统计（用于数据迁移后核对数据量）
  8. 校验报告汇总
- **使用方法**：直接在 MySQL 中执行，查看输出结果（`PASS` / `FAIL`）

### data/seed_data.sql
- **用途**：初始化基础业务数据，便于系统首次启动后即可使用
- **内容**：
  - admin 用户（BCrypt 加密密码 `admin123`）
  - 运维用户 ops_user（BCrypt 加密密码 `ops123456`）
  - 默认 Bucket（kb-file、kb-backup）
  - 默认知识空间（我的知识库、运维知识库）与目录
  - 默认标签（重要、待办、已完成）
  - 示例运维主机（app-server-01、db-server-01、cache-server-01）
  - 示例运维服务（mysql、redis、nginx）与端口
  - 示例凭据、域名、运维知识、看板快照
  - 示例知识引擎数据（主机、文档、命令）
- **特性**：全部使用 `WHERE NOT EXISTS`，可重复执行（幂等）

### data/test_data.sql
- **用途**：为测试环境填充测试数据，便于功能验证与集成测试
- **内容**：
  - 测试用户（test_user1、test_user2、test_disabled）
  - 测试 API Token
  - 测试文件记录（含不同解析状态：READY/PARSING/PARSE_FAILED）
  - 测试文件分片
  - 测试知识空间、目录、笔记、网页收藏、标签、分享
  - 测试运维主机、服务、端口、部署记录、矛盾检测、运维知识、凭据
  - 测试操作日志
  - 测试知识引擎数据（主机、文档、命令、时间线、实体关联）
- **特性**：
  - 全部使用 `WHERE NOT EXISTS`，可重复执行（幂等）
  - 测试数据均以 `test_` 前缀命名，便于识别与清理
  - 文件顶部包含清理 SQL 语句，可直接复制执行清理

## 执行顺序

### 全新部署（生产环境）

```bash
# 1. 执行 V1 初始化 Schema
mysql -u root -p < V1__init_schema.sql

# 2. 执行种子数据
mysql -u root -p < data/seed_data.sql

# 3. 执行校验脚本（确认部署成功）
mysql -u root -p < V1__init_schema_verify.sql
```

### 测试环境部署

```bash
# 1. 执行 V1 初始化 Schema
mysql -u root -p < V1__init_schema.sql

# 2. 执行种子数据
mysql -u root -p < data/seed_data.sql

# 3. 执行测试数据
mysql -u root -p < data/test_data.sql

# 4. 执行校验脚本
mysql -u root -p < V1__init_schema_verify.sql
```

### 完全回滚

```bash
# ⚠️ 危险操作：会删除所有数据，请先备份！
mysql -u root -p < V1__init_schema_rollback.sql
```

### 清理测试数据

参考 `data/test_data.sql` 文件顶部的清理 SQL 语句，可直接复制执行。

## 默认账号

| 用户名 | 密码 | 用途 |
|--------|------|------|
| `admin` | `admin123` | 系统管理员 |
| `ops_user` | `ops123456` | 运维操作员 |
| `test_user1` | `test123456` | 测试用户1（仅测试环境） |
| `test_user2` | `test123456` | 测试用户2（仅测试环境） |

> ⚠️ 生产环境部署后请立即修改默认密码！

## 规范约定（SOP 要求）

1. **字符集**：所有表使用 `COLLATE=utf8mb4_unicode_ci`
2. **存储引擎**：所有表使用 `ENGINE=InnoDB`
3. **禁止 DROP**：非回滚脚本禁止使用 `DROP TABLE` / `DROP DATABASE`
4. **增量变更**：已有表结构变更一律使用 `ALTER TABLE`，并通过新的版本化脚本（如 `V2__add_xxx_column.sql`）执行
5. **幂等性**：所有 INSERT 使用 `WHERE NOT EXISTS` 保证可重复执行
6. **逻辑删除**：所有业务表包含 `deleted` 字段（0=未删除，1=已删除），不物理删除
7. **审计字段**：所有业务表包含 `created_at` 和 `updated_at` 字段

## Flyway 集成

本目录可作为 Flyway 的 `locations` 配置目录，版本化脚本命名遵循 Flyway 约定：

```
V{版本号}__{描述}.sql
```

例如：
- `V1__init_schema.sql` → V1 版本
- `V2__add_user_avatar_field.sql` → V2 版本（示例）
- `V3__create_notification_table.sql` → V3 版本（示例）

后续结构变更请新建 `V2__*.sql`、`V3__*.sql` 等版本化脚本，使用 `ALTER TABLE` 进行增量变更。

## 相关文档

- 原始初始化脚本：`../init-sql/`
- 数据库设计文档：`../docs/database-design.md`
- 部署文档：`../docs/deployment.md`
- 操作手册：`../docs/operation-manual.md`
