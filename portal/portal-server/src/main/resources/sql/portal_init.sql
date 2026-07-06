-- portal 门户系统数据库初始化脚本
-- 系统导航、健康检查、分类管理

USE `tools`;

-- 门户系统表
CREATE TABLE IF NOT EXISTS `portal_system` (
    `id`                BIGINT       NOT NULL AUTO_INCREMENT,
    `name`              VARCHAR(128) NOT NULL COMMENT '系统名称',
    `description`       VARCHAR(512) DEFAULT NULL COMMENT '系统描述',
    `url`               VARCHAR(512) DEFAULT NULL COMMENT '系统URL',
    `icon`              VARCHAR(128) DEFAULT NULL COMMENT '图标名称(Element Plus图标名)',
    `color`             VARCHAR(32)  DEFAULT NULL COMMENT '主题颜色(HEX)',
    `category`          VARCHAR(64)  DEFAULT NULL COMMENT '分类:web/infra/tool/doc',
    `status`            INT          DEFAULT 1 COMMENT '1=启用 0=禁用',
    `health_check_url`  VARCHAR(512) DEFAULT NULL COMMENT '健康检查URL',
    `docs`              JSON         DEFAULT NULL COMMENT '文档信息(JSON数组)',
    `download_path`     VARCHAR(512) DEFAULT NULL COMMENT '下载路径(工具软件)',
    `tech_stack`        VARCHAR(512) DEFAULT NULL COMMENT '技术栈描述',
    `login_username`    VARCHAR(128) DEFAULT NULL COMMENT '登录账号',
    `login_password`    VARCHAR(512) DEFAULT NULL COMMENT '登录密码（AES加密）',
    `sort_order`        INT          DEFAULT 0 COMMENT '排序号，越小越靠前',
    `deleted`           INT          DEFAULT 0 COMMENT '逻辑删除',
    `created_at`        DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `updated_at`        DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_category` (`category`),
    KEY `idx_status` (`status`),
    KEY `idx_sort_order` (`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='门户系统表';

-- 新增列（兼容已有表）
SET @dbname = DATABASE();
SET @tablename = 'portal_system';

-- color 列
SELECT COUNT(*) INTO @col_exists FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = 'color';
SET @sql = IF(@col_exists = 0, 'ALTER TABLE portal_system ADD COLUMN color VARCHAR(32) DEFAULT NULL COMMENT ''主题颜色(HEX)'' AFTER icon', 'SELECT ''color exists''');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- download_path 列
SELECT COUNT(*) INTO @col_exists FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = 'download_path';
SET @sql = IF(@col_exists = 0, 'ALTER TABLE portal_system ADD COLUMN download_path VARCHAR(512) DEFAULT NULL COMMENT ''下载路径(工具软件)'' AFTER docs', 'SELECT ''download_path exists''');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- tech_stack 列
SELECT COUNT(*) INTO @col_exists FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = 'tech_stack';
SET @sql = IF(@col_exists = 0, 'ALTER TABLE portal_system ADD COLUMN tech_stack VARCHAR(512) DEFAULT NULL COMMENT ''技术栈描述'' AFTER download_path', 'SELECT ''tech_stack exists''');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- login_username 列
SELECT COUNT(*) INTO @col_exists FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = 'login_username';
SET @sql = IF(@col_exists = 0, 'ALTER TABLE portal_system ADD COLUMN login_username VARCHAR(128) DEFAULT NULL COMMENT ''登录账号'' AFTER tech_stack', 'SELECT ''login_username exists''');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- login_password 列
SELECT COUNT(*) INTO @col_exists FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = 'login_password';
SET @sql = IF(@col_exists = 0, 'ALTER TABLE portal_system ADD COLUMN login_password VARCHAR(512) DEFAULT NULL COMMENT ''登录密码（AES加密）'' AFTER login_username', 'SELECT ''login_password exists''');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
