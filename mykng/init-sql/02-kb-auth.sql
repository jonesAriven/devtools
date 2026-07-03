-- kb-auth 初始化
-- kb_auth 数据库初始化脚本
-- 包含 user, refresh_token, jwt_blacklist, ops_api_token, operation_log 表

CREATE DATABASE IF NOT EXISTS `kb_auth` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE `kb_auth`;

-- 用户表
CREATE TABLE IF NOT EXISTS `user` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT,
    `username`       VARCHAR(64)  NOT NULL,
    `password`       VARCHAR(128) NOT NULL,
    `email`          VARCHAR(128) DEFAULT NULL,
    `phone`          VARCHAR(20)  DEFAULT NULL,
    `wechat_openid`  VARCHAR(64)  DEFAULT NULL,
    `avatar`         VARCHAR(512) DEFAULT NULL,
    `nickname`       VARCHAR(64)  DEFAULT NULL,
    `status`         INT          DEFAULT 1 COMMENT '1=正常 0=禁用',
    `deleted`        INT          DEFAULT 0 COMMENT '逻辑删除',
    `created_at`     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `updated_at`     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    KEY `idx_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- 刷新令牌表
CREATE TABLE IF NOT EXISTS `refresh_token` (
    `id`         BIGINT      NOT NULL AUTO_INCREMENT,
    `user_id`    BIGINT      NOT NULL,
    `token`      VARCHAR(512) NOT NULL,
    `expire_at`  DATETIME    DEFAULT NULL,
    `created_at` DATETIME    DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_token` (`token`(255))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='刷新令牌表';

-- JWT 黑名单表
CREATE TABLE IF NOT EXISTS `jwt_blacklist` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT,
    `token`      VARCHAR(512) NOT NULL,
    `expire_at`  DATETIME     DEFAULT NULL,
    `created_at` DATETIME     DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_token` (`token`(255)),
    KEY `idx_expire_at` (`expire_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='JWT黑名单表';

-- API Token 表
CREATE TABLE IF NOT EXISTS `ops_api_token` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT,
    `user_id`         BIGINT       NOT NULL,
    `name`            VARCHAR(128) NOT NULL COMMENT 'Token名称',
    `token_encrypted` TEXT         NOT NULL COMMENT '加密后的Token',
    `token_prefix`    VARCHAR(64)  DEFAULT NULL COMMENT 'Token前缀（展示用）',
    `scope`           VARCHAR(256) DEFAULT NULL COMMENT '权限范围，逗号分隔',
    `status`          INT          DEFAULT 0 COMMENT '0=启用 1=禁用',
    `expire_at`       DATETIME     DEFAULT NULL COMMENT '过期时间',
    `last_used_at`    DATETIME     DEFAULT NULL COMMENT '最后使用时间',
    `deleted`         INT          DEFAULT 0 COMMENT '逻辑删除',
    `created_at`      DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `updated_at`      DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_token_prefix` (`token_prefix`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='API Token表';

-- 操作日志表（从 kb_ops 迁移至 kb_auth，统一由认证服务管理用户行为审计）
CREATE TABLE IF NOT EXISTS `operation_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint DEFAULT NULL,
  `username` varchar(100) DEFAULT NULL,
  `action` varchar(50) NOT NULL COMMENT 'LOGIN/UPLOAD/DELETE/UPDATE等',
  `resource_type` varchar(50) DEFAULT NULL,
  `resource_id` bigint DEFAULT NULL,
  `detail` varchar(2000) DEFAULT NULL,
  `ip` varchar(50) DEFAULT NULL,
  `user_agent` varchar(500) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_action` (`action`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作日志表';

-- 初始管理员账号（密码: admin123, BCrypt加密）
INSERT INTO `user` (`username`, `password`, `nickname`, `status`)
SELECT 'admin', '$2a$10$YfuxV6cAdrN0l1JENSprI.ykW1KD7Ggnul8Ex0V6EbriF92wc/mRK', '管理员', 1
WHERE NOT EXISTS (SELECT 1 FROM `user` WHERE `username` = 'admin');

