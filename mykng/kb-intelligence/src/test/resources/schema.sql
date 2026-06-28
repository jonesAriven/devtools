-- H2 兼容的 kb-intelligence 集成测试建表脚本
-- DatabaseInitializer.java 的 DDL 含 MySQL 专属 inline INDEX/ENGINE/CHARSET 语法，
-- H2（即使 MODE=MySQL）不支持 inline INDEX 定义会导致建表失败（被 try/catch 静默处理）。
-- 因此在此用 H2 兼容语法提前创建 kn_doc 表，保证集成测试的查询能正常执行。
-- CREATE TABLE IF NOT EXISTS 与 DatabaseInitializer 不冲突（已存在则跳过）。

CREATE TABLE IF NOT EXISTS kn_doc (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    source_id VARCHAR(255),
    title VARCHAR(500) NOT NULL,
    file_path VARCHAR(1000) NOT NULL,
    doc_type VARCHAR(32),
    category VARCHAR(100),
    tags VARCHAR(1000),
    summary VARCHAR(1000),
    content_hash VARCHAR(64),
    entity_count INT DEFAULT 0,
    command_count INT DEFAULT 0,
    section_count INT DEFAULT 0,
    word_count INT DEFAULT 0,
    status INT DEFAULT 1,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0
);
