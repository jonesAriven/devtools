-- H2 兼容的建表脚本（MySQL MODE，反引号引用保留字）
-- 表名与 kb-ops 实体 @TableName 保持一致

-- 主机表
CREATE TABLE IF NOT EXISTS `ops_host` (
    `id`                BIGINT       NOT NULL AUTO_INCREMENT,
    `name`              VARCHAR(128) NOT NULL,
    `ip`                VARCHAR(64)  NOT NULL,
    `tailscale_ip`      VARCHAR(64)  DEFAULT NULL,
    `ssh_port`          INT          DEFAULT 22,
    `username`          VARCHAR(64)  DEFAULT NULL,
    `password_encrypted` TEXT        DEFAULT NULL,
    `role`              VARCHAR(64)  DEFAULT NULL,
    `status`            INT          DEFAULT 1,
    `tags`              VARCHAR(512) DEFAULT NULL,
    `remark`            VARCHAR(512) DEFAULT NULL,
    `deleted`           INT          DEFAULT 0,
    `created_at`        TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    `updated_at`        TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`)
);

-- 服务表
CREATE TABLE IF NOT EXISTS `ops_service` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `name`          VARCHAR(128) NOT NULL,
    `type`          VARCHAR(64)  DEFAULT NULL,
    `version`       VARCHAR(64)  DEFAULT NULL,
    `port`          INT          DEFAULT NULL,
    `host_id`       BIGINT       DEFAULT NULL,
    `deploy_path`   VARCHAR(512) DEFAULT NULL,
    `status`        INT          DEFAULT 1,
    `dependencies`  VARCHAR(512) DEFAULT NULL,
    `tags`          VARCHAR(512) DEFAULT NULL,
    `remark`        VARCHAR(512) DEFAULT NULL,
    `deleted`       INT          DEFAULT 0,
    `created_at`    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    `updated_at`    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`)
);

-- 部署/变更记录表
CREATE TABLE IF NOT EXISTS `ops_change_log` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT,
    `service_id`      BIGINT       NOT NULL,
    `service_name`    VARCHAR(128) DEFAULT NULL,
    `host_id`         BIGINT       DEFAULT NULL,
    `version`         VARCHAR(64)  DEFAULT NULL,
    `previous_version` VARCHAR(64) DEFAULT NULL,
    `operator`        VARCHAR(64)  DEFAULT NULL,
    `deploy_time`     TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    `result`          INT          DEFAULT 1,
    `rollback`        INT          DEFAULT 0,
    `rollback_info`   VARCHAR(512) DEFAULT NULL,
    `remark`          VARCHAR(512) DEFAULT NULL,
    `created_at`      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`)
);

-- 运维知识表
CREATE TABLE IF NOT EXISTS `ops_knowledge` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `title`         VARCHAR(256) NOT NULL,
    `category`      VARCHAR(64)  DEFAULT NULL,
    `content`       TEXT,
    `tags`          VARCHAR(512) DEFAULT NULL,
    `host_id`       BIGINT       DEFAULT NULL,
    `service_id`    BIGINT       DEFAULT NULL,
    `author`        VARCHAR(64)  DEFAULT NULL,
    `view_count`    INT          DEFAULT 0,
    `deleted`       INT          DEFAULT 0,
    `created_at`    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    `updated_at`    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`)
);

-- 矛盾检测结果表
CREATE TABLE IF NOT EXISTS `ops_conflict` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT,
    `rule_code`       VARCHAR(64)  NOT NULL,
    `rule_name`       VARCHAR(128) DEFAULT NULL,
    `severity`        INT          DEFAULT 1,
    `target_type`     VARCHAR(32)  DEFAULT NULL,
    `target_id`       BIGINT       DEFAULT NULL,
    `target_name`     VARCHAR(256) DEFAULT NULL,
    `detail`          VARCHAR(1024) DEFAULT NULL,
    `status`          INT          DEFAULT 0,
    `detected_at`     TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    `created_at`      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`)
);

-- 运维看板快照表
CREATE TABLE IF NOT EXISTS `ops_snapshot` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT,
    `snapshot_date`  DATE         NOT NULL,
    `metric_key`     VARCHAR(64)  NOT NULL,
    `metric_value`   BIGINT       DEFAULT 0,
    `extra`          VARCHAR(512) DEFAULT NULL,
    `created_at`     TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_date_key` UNIQUE (`snapshot_date`, `metric_key`)
);

-- 操作日志表
CREATE TABLE IF NOT EXISTS `operation_log` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `user_id`       BIGINT       DEFAULT NULL,
    `username`      VARCHAR(100) DEFAULT NULL,
    `action`        VARCHAR(50)  NOT NULL,
    `resource_type` VARCHAR(50)  DEFAULT NULL,
    `resource_id`   BIGINT       DEFAULT NULL,
    `detail`        VARCHAR(2000) DEFAULT NULL,
    `ip`            VARCHAR(50)  DEFAULT NULL,
    `user_agent`    VARCHAR(500) DEFAULT NULL,
    `created_at`    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`)
);
