-- kb_file 数据库初始化脚本
-- 包含 file, file_chunk, bucket 表

CREATE DATABASE IF NOT EXISTS `kb_file` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE `kb_file`;

-- 文件表
CREATE TABLE IF NOT EXISTS `file` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT,
    `folder_id`    BIGINT       DEFAULT NULL COMMENT '所属文件夹ID',
    `user_id`      BIGINT       NOT NULL COMMENT '所属用户ID',
    `name`         VARCHAR(255) NOT NULL COMMENT '文件名',
    `type`         VARCHAR(32)  DEFAULT '' COMMENT '文件扩展名',
    `size`         BIGINT       DEFAULT 0 COMMENT '文件大小(字节)',
    `minio_path`   VARCHAR(512) DEFAULT NULL COMMENT 'MinIO存储路径',
    `parse_status` VARCHAR(32)  DEFAULT 'PENDING' COMMENT '解析状态: PENDING/PARSING/READY/PARSE_FAILED',
    `parse_error`  TEXT         DEFAULT NULL COMMENT '解析错误信息',
    `starred`      INT          DEFAULT 0 COMMENT '是否星标: 0=否 1=是',
    `deleted`      INT          DEFAULT 0 COMMENT '逻辑删除: 0=未删除 1=已删除',
    `created_at`   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `updated_at`   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_folder_id` (`folder_id`),
    KEY `idx_starred` (`starred`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文件表';

-- 文件分片表
CREATE TABLE IF NOT EXISTS `file_chunk` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `file_id`       VARCHAR(64)  NOT NULL COMMENT '分片上传标识(前端生成)',
    `chunk_number`  INT          DEFAULT NULL COMMENT '分片序号',
    `chunk_path`    VARCHAR(512) DEFAULT NULL COMMENT 'MinIO分片路径',
    `created_at`    DATETIME     DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_file_id` (`file_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文件分片表';

-- MinIO Bucket 管理表
CREATE TABLE IF NOT EXISTS `bucket` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT,
    `name`           VARCHAR(128) NOT NULL COMMENT 'Bucket名称',
    `type`           VARCHAR(32)  DEFAULT NULL COMMENT 'Bucket类型',
    `lifecycle_days` INT          DEFAULT NULL COMMENT '生命周期天数',
    `created_at`     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `updated_at`     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MinIO Bucket管理表';

-- 初始化默认 Bucket
INSERT INTO `bucket` (`name`, `type`, `lifecycle_days`)
SELECT 'kb-file', 'file', NULL
WHERE NOT EXISTS (SELECT 1 FROM `bucket` WHERE `name` = 'kb-file');
