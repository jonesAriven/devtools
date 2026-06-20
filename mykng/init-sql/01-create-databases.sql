-- ============================================================
-- mykng 知识库微服务 - 数据库初始化脚本
-- Docker 启动时自动执行（挂载到 /docker-entrypoint-initdb.d）
-- ============================================================

-- 创建 4 个独立数据库
CREATE DATABASE IF NOT EXISTS kb_auth DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS kb_file DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS kb_knowledge DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS kb_ops DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 授权（如果需要单独用户，可选）
-- GRANT ALL PRIVILEGES ON kb_auth.* TO 'kb'@'%';
-- GRANT ALL PRIVILEGES ON kb_file.* TO 'kb'@'%';
-- GRANT ALL PRIVILEGES ON kb_knowledge.* TO 'kb'@'%';
-- GRANT ALL PRIVILEGES ON kb_ops.* TO 'kb'@'%';
-- FLUSH PRIVILEGES;

-- 说明: 各服务的建表 SQL 在各自模块的 src/main/resources/sql/ 目录下
-- 部署时需要手动执行或通过 Flyway/Liquibase 自动迁移
