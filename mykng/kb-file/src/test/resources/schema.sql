-- H2 兼容的建表脚本（MySQL MODE，已去除 MySQL 特有的 ENGINE/CHARSET/COMMENT/ON UPDATE/KEY 语法）
-- 对应 kb_file 数据库的 file / file_chunk / bucket 表

-- 文件表
CREATE TABLE IF NOT EXISTS `file` (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    folder_id      BIGINT       DEFAULT NULL,
    user_id        BIGINT       NOT NULL,
    name           VARCHAR(255) NOT NULL,
    type           VARCHAR(32)  DEFAULT '',
    size           BIGINT       DEFAULT 0,
    minio_path     VARCHAR(512) DEFAULT NULL,
    parse_status   VARCHAR(32)  DEFAULT 'PENDING',
    parse_error    TEXT         DEFAULT NULL,
    starred        INT          DEFAULT 0,
    deleted        INT          DEFAULT 0,
    created_at     TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

-- 文件分片表
CREATE TABLE IF NOT EXISTS `file_chunk` (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    file_id        VARCHAR(64)  NOT NULL,
    chunk_number   INT          DEFAULT NULL,
    chunk_path     VARCHAR(512) DEFAULT NULL,
    created_at     TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

-- MinIO Bucket 管理表
CREATE TABLE IF NOT EXISTS `bucket` (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    name            VARCHAR(128) NOT NULL,
    type            VARCHAR(32)  DEFAULT NULL,
    lifecycle_days  INT          DEFAULT NULL,
    created_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_name UNIQUE (name)
);
