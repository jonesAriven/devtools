-- kb-ops 初始化
-- kb_ops 数据库初始化脚本
-- 运维微服务：主机、服务、部署记录、运维知识、矛盾检测、看板快照

CREATE DATABASE IF NOT EXISTS `kb_ops` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE `kb_ops`;

-- 主机表
CREATE TABLE IF NOT EXISTS `ops_host` (
    `id`                BIGINT       NOT NULL AUTO_INCREMENT,
    `name`              VARCHAR(128) NOT NULL COMMENT '主机名称',
    `ip`                VARCHAR(64)  NOT NULL COMMENT '内网IP',
    `tailscale_ip`      VARCHAR(64)  DEFAULT NULL COMMENT 'Tailscale IP',
    `ssh_port`          INT          DEFAULT 22 COMMENT 'SSH端口',
    `username`          VARCHAR(64)  DEFAULT NULL COMMENT 'SSH用户名',
    `password_encrypted` TEXT        DEFAULT NULL COMMENT '加密后的密码(AES-256-GCM)',
    `role`              VARCHAR(64)  DEFAULT NULL COMMENT '角色: web/db/cache/app等',
    `status`            INT          DEFAULT 1 COMMENT '1=运行中 0=停机 2=维护中',
    `tags`              VARCHAR(512) DEFAULT NULL COMMENT '标签，逗号分隔',
    `remark`            VARCHAR(512) DEFAULT NULL COMMENT '备注',
    `deleted`           INT          DEFAULT 0 COMMENT '逻辑删除',
    `created_at`        DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `updated_at`        DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_ip` (`ip`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='运维主机表';

-- 服务表
CREATE TABLE IF NOT EXISTS `ops_service` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `name`          VARCHAR(128) NOT NULL COMMENT '服务名称',
    `type`          VARCHAR(64)  DEFAULT NULL COMMENT '服务类型: web/db/cache/mq等',
    `version`       VARCHAR(64)  DEFAULT NULL COMMENT '当前版本',
    `port`          INT          DEFAULT NULL COMMENT '服务端口',
    `host_id`       BIGINT       DEFAULT NULL COMMENT '部署主机ID',
    `deploy_path`   VARCHAR(512) DEFAULT NULL COMMENT '部署路径',
    `status`        INT          DEFAULT 1 COMMENT '1=运行中 0=已停止 2=异常',
    `dependencies`  VARCHAR(512) DEFAULT NULL COMMENT '依赖服务，逗号分隔的service name',
    `tags`          VARCHAR(512) DEFAULT NULL COMMENT '标签',
    `remark`        VARCHAR(512) DEFAULT NULL COMMENT '备注',
    `deleted`       INT          DEFAULT 0 COMMENT '逻辑删除',
    `created_at`    DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `updated_at`    DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_host_id` (`host_id`),
    KEY `idx_name` (`name`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='运维服务表';

-- 部署记录表 (变更日志)
CREATE TABLE IF NOT EXISTS `ops_change_log` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT,
    `service_id`      BIGINT       NOT NULL COMMENT '服务ID',
    `service_name`    VARCHAR(128) DEFAULT NULL COMMENT '服务名称(冗余便于查询)',
    `host_id`         BIGINT       DEFAULT NULL COMMENT '部署主机ID',
    `version`         VARCHAR(64)  DEFAULT NULL COMMENT '部署版本',
    `previous_version` VARCHAR(64) DEFAULT NULL COMMENT '回滚前的版本',
    `operator`        VARCHAR(64)  DEFAULT NULL COMMENT '操作人',
    `deploy_time`     DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '部署时间',
    `result`          INT          DEFAULT 1 COMMENT '1=成功 0=失败',
    `rollback`        INT          DEFAULT 0 COMMENT '0=普通部署 1=回滚操作',
    `rollback_info`   VARCHAR(512) DEFAULT NULL COMMENT '回滚信息/说明',
    `remark`          VARCHAR(512) DEFAULT NULL COMMENT '备注',
    `created_at`      DATETIME     DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_service_id` (`service_id`),
    KEY `idx_deploy_time` (`deploy_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='部署/变更记录表';

-- 运维知识表
CREATE TABLE IF NOT EXISTS `ops_knowledge` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `title`         VARCHAR(256) NOT NULL COMMENT '标题',
    `category`      VARCHAR(64)  DEFAULT NULL COMMENT '分类: 部署/排障/巡检/规范等',
    `content`       MEDIUMTEXT   COMMENT '内容(Markdown)',
    `tags`          VARCHAR(512) DEFAULT NULL COMMENT '标签，逗号分隔',
    `host_id`       BIGINT       DEFAULT NULL COMMENT '关联主机ID',
    `service_id`    BIGINT       DEFAULT NULL COMMENT '关联服务ID',
    `author`        VARCHAR(64)  DEFAULT NULL COMMENT '作者',
    `view_count`    INT          DEFAULT 0 COMMENT '查看次数',
    `deleted`       INT          DEFAULT 0 COMMENT '逻辑删除',
    `created_at`    DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `updated_at`    DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_category` (`category`),
    KEY `idx_host_id` (`host_id`),
    KEY `idx_service_id` (`service_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='运维知识表';

-- 矛盾检测结果表
CREATE TABLE IF NOT EXISTS `ops_conflict` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT,
    `rule_code`       VARCHAR(64)  NOT NULL COMMENT '规则编码: VERSION_MISMATCH/PORT_CONFLICT等',
    `rule_name`       VARCHAR(128) DEFAULT NULL COMMENT '规则名称',
    `severity`        INT          DEFAULT 1 COMMENT '严重程度: 1=提示 2=警告 3=严重',
    `target_type`     VARCHAR(32)  DEFAULT NULL COMMENT '对象类型: HOST/SERVICE',
    `target_id`       BIGINT       DEFAULT NULL COMMENT '对象ID',
    `target_name`     VARCHAR(256) DEFAULT NULL COMMENT '对象名称',
    `detail`          VARCHAR(1024) DEFAULT NULL COMMENT '矛盾详情',
    `status`          INT          DEFAULT 0 COMMENT '0=未处理 1=已忽略 2=已解决',
    `detected_at`     DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '检测时间',
    `created_at`      DATETIME     DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_rule_code` (`rule_code`),
    KEY `idx_status` (`status`),
    KEY `idx_target` (`target_type`, `target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='矛盾检测结果表';

-- 运维看板快照表
CREATE TABLE IF NOT EXISTS `ops_snapshot` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT,
    `snapshot_date`  DATE         NOT NULL COMMENT '快照日期',
    `metric_key`     VARCHAR(64)  NOT NULL COMMENT '指标键: host_total/service_total等',
    `metric_value`   BIGINT       DEFAULT 0 COMMENT '指标值',
    `extra`          VARCHAR(512) DEFAULT NULL COMMENT '附加信息(JSON)',
    `created_at`     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_date_key` (`snapshot_date`, `metric_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='运维看板快照表';

-- 操作日志表
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
