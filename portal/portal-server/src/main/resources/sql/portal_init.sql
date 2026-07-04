-- portal 门户系统数据库初始化脚本
-- 系统导航、健康检查、分类管理

USE `tools`;

-- 门户系统表
CREATE TABLE IF NOT EXISTS `portal_system` (
    `id`                BIGINT       NOT NULL AUTO_INCREMENT,
    `name`              VARCHAR(128) NOT NULL COMMENT '系统名称',
    `description`       VARCHAR(512) DEFAULT NULL COMMENT '系统描述',
    `url`               VARCHAR(512) NOT NULL COMMENT '系统URL',
    `icon`              VARCHAR(512) DEFAULT NULL COMMENT '图标URL',
    `category`          VARCHAR(64)  DEFAULT NULL COMMENT '分类',
    `status`            INT          DEFAULT 1 COMMENT '1=启用 0=禁用',
    `health_check_url`  VARCHAR(512) DEFAULT NULL COMMENT '健康检查URL',
    `docs`              JSON         DEFAULT NULL COMMENT '文档信息(JSON)',
    `sort_order`        INT          DEFAULT 0 COMMENT '排序号，越小越靠前',
    `deleted`           INT          DEFAULT 0 COMMENT '逻辑删除',
    `created_at`        DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `updated_at`        DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_category` (`category`),
    KEY `idx_status` (`status`),
    KEY `idx_sort_order` (`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='门户系统表';
