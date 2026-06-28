-- ============================================================
-- mykng 知识库微服务 - V1 初始化 Schema (Flyway 版本化脚本)
-- ============================================================
-- 版本: V1
-- 说明: 合并 init-sql/01~06 的完整建库建表脚本（按依赖顺序）
-- 数据库: MySQL 8.0+
-- 字符集: utf8mb4 / utf8mb4_unicode_ci
-- 来源: init-sql/01-create-databases.sql ~ 06-kb-intelligence.sql
-- ============================================================

-- ============================================================
-- 1. 创建 5 个独立数据库
-- ============================================================
CREATE DATABASE IF NOT EXISTS `kb_auth`         DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS `kb_file`         DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS `kb_knowledge`    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS `kb_ops`          DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS `kb_intelligence` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;


-- ============================================================
-- 2. kb_auth 数据库（认证服务）
-- 表: user / refresh_token / jwt_blacklist / ops_api_token
-- ============================================================
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

-- 初始管理员账号（密码: admin123, BCrypt加密）
INSERT INTO `user` (`username`, `password`, `nickname`, `status`)
SELECT 'admin', '$2a$10$YfuxV6cAdrN0l1JENSprI.ykW1KD7Ggnul8Ex0V6EbriF92wc/mRK', '管理员', 1
WHERE NOT EXISTS (SELECT 1 FROM `user` WHERE `username` = 'admin');


-- ============================================================
-- 3. kb_file 数据库（文件服务）
-- 表: file / file_chunk / bucket
-- ============================================================
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


-- ============================================================
-- 4. kb_ops 数据库（运维服务）
-- 表: ops_host / ops_service / ops_change_log / ops_knowledge
--      ops_conflict / ops_snapshot / operation_log / ops_port
--      ops_credential / ops_domain / ops_dependency
-- ============================================================
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
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `user_id`       BIGINT       DEFAULT NULL,
    `username`      VARCHAR(100) DEFAULT NULL,
    `action`        VARCHAR(50)  NOT NULL COMMENT 'LOGIN/UPLOAD/DELETE/UPDATE等',
    `resource_type` VARCHAR(50)  DEFAULT NULL,
    `resource_id`   BIGINT       DEFAULT NULL,
    `detail`        VARCHAR(2000) DEFAULT NULL,
    `ip`            VARCHAR(50)  DEFAULT NULL,
    `user_agent`    VARCHAR(500) DEFAULT NULL,
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_action` (`action`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作日志表';

-- 端口管理表
CREATE TABLE IF NOT EXISTS `ops_port` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `host_id`       BIGINT       NOT NULL COMMENT '关联主机ID',
    `port`          INT          NOT NULL COMMENT '端口号',
    `protocol`      VARCHAR(16)  DEFAULT 'TCP' COMMENT '协议: TCP/UDP',
    `service_id`    BIGINT       DEFAULT NULL COMMENT '关联服务ID(可空)',
    `purpose`       VARCHAR(256) DEFAULT NULL COMMENT '用途说明',
    `status`        INT          DEFAULT 1 COMMENT '1=开放 0=关闭',
    `exposed`       INT          DEFAULT 0 COMMENT '是否对外暴露 0=否 1=是',
    `remark`        VARCHAR(512) DEFAULT NULL COMMENT '备注',
    `deleted`       INT          DEFAULT 0 COMMENT '逻辑删除',
    `created_at`    DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `updated_at`    DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_host_id` (`host_id`),
    KEY `idx_service_id` (`service_id`),
    KEY `idx_port` (`port`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='端口管理表';

-- 凭据管理表
CREATE TABLE IF NOT EXISTS `ops_credential` (
    `id`                 BIGINT       NOT NULL AUTO_INCREMENT,
    `name`               VARCHAR(128) NOT NULL COMMENT '凭据名称',
    `type`               VARCHAR(64)  NOT NULL COMMENT '类型: SSH/DB/API_TOKEN/OTHER',
    `username`           VARCHAR(128) DEFAULT NULL COMMENT '用户名',
    `password_encrypted` TEXT         DEFAULT NULL COMMENT '加密后的密码(AES-256-GCM)',
    `secret_key`         TEXT         DEFAULT NULL COMMENT 'API key 类密钥(加密存储)',
    `host_id`            BIGINT       DEFAULT NULL COMMENT '关联主机ID(可空)',
    `service_id`         BIGINT       DEFAULT NULL COMMENT '关联服务ID(可空)',
    `remark`             VARCHAR(512) DEFAULT NULL COMMENT '备注',
    `deleted`            INT          DEFAULT 0 COMMENT '逻辑删除',
    `created_at`         DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `updated_at`         DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_type` (`type`),
    KEY `idx_host_id` (`host_id`),
    KEY `idx_service_id` (`service_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='凭据管理表';

-- 域名管理表
CREATE TABLE IF NOT EXISTS `ops_domain` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT,
    `domain`          VARCHAR(256) NOT NULL COMMENT '域名',
    `type`            VARCHAR(32)  DEFAULT NULL COMMENT '类型: 顶级域/子域',
    `purpose`         VARCHAR(256) DEFAULT NULL COMMENT '用途',
    `registrar`       VARCHAR(128) DEFAULT NULL COMMENT '注册商',
    `expires_at`      DATETIME     DEFAULT NULL COMMENT '域名到期时间',
    `ssl_expires_at`  DATETIME     DEFAULT NULL COMMENT 'SSL证书到期时间',
    `status`          INT          DEFAULT 1 COMMENT '1=正常 0=过期 2=即将过期',
    `remark`          VARCHAR(512) DEFAULT NULL COMMENT '备注',
    `deleted`         INT          DEFAULT 0 COMMENT '逻辑删除',
    `created_at`      DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `updated_at`      DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_domain` (`domain`),
    KEY `idx_status` (`status`),
    KEY `idx_expires_at` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='域名管理表';

-- 服务依赖关系表
CREATE TABLE IF NOT EXISTS `ops_dependency` (
    `id`                       BIGINT       NOT NULL AUTO_INCREMENT,
    `service_id`               BIGINT       NOT NULL COMMENT '依赖方服务ID',
    `service_name`             VARCHAR(128) DEFAULT NULL COMMENT '依赖方服务名(冗余)',
    `depends_on_service_id`    BIGINT       NOT NULL COMMENT '被依赖服务ID',
    `depends_on_service_name`  VARCHAR(128) DEFAULT NULL COMMENT '被依赖服务名(冗余)',
    `dependency_type`          VARCHAR(32)  DEFAULT 'REQUIRED' COMMENT '依赖类型: REQUIRED/OPTIONAL/WEAK',
    `description`              VARCHAR(512) DEFAULT NULL COMMENT '依赖描述',
    `deleted`                  INT          DEFAULT 0 COMMENT '逻辑删除',
    `created_at`               DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `updated_at`               DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_service_id` (`service_id`),
    KEY `idx_depends_on_service_id` (`depends_on_service_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='服务依赖关系表';


-- ============================================================
-- 5. kb_knowledge 数据库（知识库服务）
-- 表: space / folder / doc / web_page / tag / resource_tag
--      share / share_access_log / version
-- ============================================================
USE `kb_knowledge`;

-- 空间表
CREATE TABLE IF NOT EXISTS `space` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `user_id`     BIGINT       NOT NULL,
    `name`        VARCHAR(100) NOT NULL,
    `type`        VARCHAR(20)  NOT NULL DEFAULT 'private' COMMENT 'private/team/public',
    `description` VARCHAR(500) DEFAULT NULL,
    `status`      TINYINT      NOT NULL DEFAULT 1,
    `deleted`     TINYINT      NOT NULL DEFAULT 0,
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 目录表
CREATE TABLE IF NOT EXISTS `folder` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `space_id`    BIGINT       NOT NULL,
    `parent_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '0表示根目录',
    `name`        VARCHAR(200) NOT NULL,
    `sort_order`  INT          NOT NULL DEFAULT 0,
    `deleted`     TINYINT      NOT NULL DEFAULT 0,
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_space_id` (`space_id`),
    KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 笔记表
CREATE TABLE IF NOT EXISTS `doc` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `folder_id`   BIGINT       NOT NULL,
    `user_id`     BIGINT       NOT NULL,
    `title`       VARCHAR(500) NOT NULL,
    `starred`     TINYINT      NOT NULL DEFAULT 0,
    `deleted`     TINYINT      NOT NULL DEFAULT 0,
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_folder_id` (`folder_id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 网页收藏表
CREATE TABLE IF NOT EXISTS `web_page` (
    `id`            BIGINT        NOT NULL AUTO_INCREMENT,
    `folder_id`     BIGINT        NOT NULL,
    `user_id`       BIGINT        NOT NULL,
    `url`           VARCHAR(2000) NOT NULL,
    `title`         VARCHAR(500)  NOT NULL,
    `snapshot_path` VARCHAR(1000) DEFAULT NULL,
    `starred`       TINYINT       NOT NULL DEFAULT 0,
    `deleted`       TINYINT       NOT NULL DEFAULT 0,
    `created_at`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_folder_id` (`folder_id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 标签表
CREATE TABLE IF NOT EXISTS `tag` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT,
    `user_id`    BIGINT       NOT NULL,
    `name`       VARCHAR(100) NOT NULL,
    `color`      VARCHAR(20)  DEFAULT NULL,
    `deleted`    TINYINT      NOT NULL DEFAULT 0,
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_user_name` (`user_id`, `name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 资源标签关联表
CREATE TABLE IF NOT EXISTS `resource_tag` (
    `id`            BIGINT      NOT NULL AUTO_INCREMENT,
    `tag_id`        BIGINT      NOT NULL,
    `resource_type` VARCHAR(20) NOT NULL COMMENT 'file/doc/web',
    `resource_id`   BIGINT      NOT NULL,
    `created_at`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_tag_id` (`tag_id`),
    KEY `idx_resource` (`resource_type`, `resource_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 分享表
CREATE TABLE IF NOT EXISTS `share` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `user_id`       BIGINT       NOT NULL,
    `resource_type` VARCHAR(20)  NOT NULL COMMENT 'file/doc/web/folder',
    `resource_id`   BIGINT       NOT NULL,
    `code`          VARCHAR(50)  NOT NULL COMMENT '分享码(UUID)',
    `extract_code`  VARCHAR(10)  DEFAULT NULL COMMENT '提取码(4位数字)',
    `expire_at`     DATETIME     DEFAULT NULL COMMENT '过期时间(NULL=永久)',
    `view_count`    INT          NOT NULL DEFAULT 0,
    `deleted`       TINYINT      NOT NULL DEFAULT 0,
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_code` (`code`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 分享访问日志表
CREATE TABLE IF NOT EXISTS `share_access_log` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `share_id`    BIGINT       NOT NULL,
    `ip`          VARCHAR(50)  DEFAULT NULL,
    `user_agent`  VARCHAR(500) DEFAULT NULL,
    `accessed_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_share_id` (`share_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 版本表
CREATE TABLE IF NOT EXISTS `version` (
    `id`            BIGINT      NOT NULL AUTO_INCREMENT,
    `resource_type` VARCHAR(20) NOT NULL COMMENT 'file/doc/web',
    `resource_id`   BIGINT      NOT NULL,
    `version_num`   INT         NOT NULL DEFAULT 1,
    `created_at`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_resource` (`resource_type`, `resource_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- ============================================================
-- 6. kb_intelligence 数据库（知识导入与双维度渲染引擎）
-- 表: kn_doc / kn_host / kn_service / kn_port / kn_credential
--      kn_domain / kn_dependency / kn_command / kn_timeline
--      kn_doc_entity_ref
-- ============================================================
USE `kb_intelligence`;

-- 知识文档主表
CREATE TABLE IF NOT EXISTS `kn_doc` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `source_id`     VARCHAR(255) DEFAULT NULL COMMENT '来源标识',
    `title`         VARCHAR(500) NOT NULL COMMENT '文档标题',
    `file_path`     VARCHAR(1000) NOT NULL COMMENT '文件路径',
    `doc_type`      VARCHAR(32)  DEFAULT NULL COMMENT '文档类型(TABLE/PLAN/TIMELINE/GRAPH/RULE)',
    `category`      VARCHAR(100) DEFAULT NULL COMMENT '分类',
    `tags`          VARCHAR(1000) DEFAULT NULL COMMENT '标签(逗号分隔)',
    `summary`       VARCHAR(1000) DEFAULT NULL COMMENT '摘要',
    `content_hash`  VARCHAR(64)  DEFAULT NULL COMMENT '内容SHA256哈希(增量更新)',
    `entity_count`  INT          DEFAULT 0 COMMENT '实体数量',
    `command_count` INT          DEFAULT 0 COMMENT '命令数量',
    `section_count` INT          DEFAULT 0 COMMENT '章节数量',
    `word_count`    INT          DEFAULT 0 COMMENT '字数',
    `status`        INT          DEFAULT 1 COMMENT '状态',
    `created_at`    DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `updated_at`    DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`       INT          DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_file_path` (`file_path`(255)),
    KEY `idx_doc_type` (`doc_type`),
    KEY `idx_category` (`category`),
    KEY `idx_content_hash` (`content_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识文档主表';

-- 主机表
CREATE TABLE IF NOT EXISTS `kn_host` (
    `id`                BIGINT       NOT NULL AUTO_INCREMENT,
    `name`              VARCHAR(200) DEFAULT NULL COMMENT '主机名称',
    `ip`                VARCHAR(50)  DEFAULT NULL COMMENT 'IP地址',
    `tailscale_ip`      VARCHAR(50)  DEFAULT NULL COMMENT 'Tailscale IP',
    `public_ip`         VARCHAR(50)  DEFAULT NULL COMMENT '公网IP',
    `ssh_port`          INT          DEFAULT 22 COMMENT 'SSH端口',
    `username`          VARCHAR(100) DEFAULT NULL COMMENT '用户名',
    `password_encrypted` VARCHAR(500) DEFAULT NULL COMMENT '加密密码',
    `os_type`           VARCHAR(50)  DEFAULT NULL COMMENT '操作系统类型',
    `os_version`        VARCHAR(100) DEFAULT NULL COMMENT '系统版本',
    `cpu_arch`          VARCHAR(30)  DEFAULT NULL COMMENT 'CPU架构',
    `cpu_cores`         INT          DEFAULT NULL COMMENT 'CPU核数',
    `memory_gb`         BIGINT       DEFAULT NULL COMMENT '内存GB',
    `role`              VARCHAR(100) DEFAULT NULL COMMENT '角色',
    `environment`       VARCHAR(50)  DEFAULT NULL COMMENT '环境',
    `location`          VARCHAR(200) DEFAULT NULL COMMENT '位置',
    `status`            VARCHAR(30)  DEFAULT 'running' COMMENT '状态',
    `tags`              VARCHAR(500) DEFAULT NULL COMMENT '标签',
    `remark`            VARCHAR(1000) DEFAULT NULL COMMENT '备注',
    `created_at`        DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `updated_at`        DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`           INT          DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ip` (`ip`),
    KEY `idx_tailscale_ip` (`tailscale_ip`),
    KEY `idx_status` (`status`),
    KEY `idx_role` (`role`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='主机表';

-- 服务表
CREATE TABLE IF NOT EXISTS `kn_service` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT,
    `host_id`      BIGINT       DEFAULT NULL COMMENT '所属主机ID',
    `name`         VARCHAR(200) NOT NULL COMMENT '服务名称',
    `service_type` VARCHAR(100) DEFAULT NULL COMMENT '服务类型',
    `version`      VARCHAR(100) DEFAULT NULL COMMENT '版本',
    `install_path` VARCHAR(500) DEFAULT NULL COMMENT '安装路径',
    `config_path`  VARCHAR(500) DEFAULT NULL COMMENT '配置路径',
    `log_path`     VARCHAR(500) DEFAULT NULL COMMENT '日志路径',
    `status`       VARCHAR(30)  DEFAULT 'running' COMMENT '状态',
    `tags`         VARCHAR(500) DEFAULT NULL COMMENT '标签',
    `remark`       VARCHAR(1000) DEFAULT NULL COMMENT '备注',
    `created_at`   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `updated_at`   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`      INT          DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_host_id` (`host_id`),
    KEY `idx_name` (`name`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='服务表';

-- 端口表
CREATE TABLE IF NOT EXISTS `kn_port` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT,
    `host_id`    BIGINT       DEFAULT NULL COMMENT '所属主机ID',
    `service_id` BIGINT       DEFAULT NULL COMMENT '关联服务ID',
    `port`       INT          NOT NULL COMMENT '端口号',
    `protocol`   VARCHAR(20)  DEFAULT 'tcp' COMMENT '协议',
    `mapping`    VARCHAR(200) DEFAULT NULL COMMENT '端口映射',
    `access_url` VARCHAR(500) DEFAULT NULL COMMENT '访问URL',
    `exposed`    INT          DEFAULT 0 COMMENT '是否对外暴露',
    `remark`     VARCHAR(500) DEFAULT NULL COMMENT '备注',
    `created_at` DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`    INT          DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_host_id` (`host_id`),
    KEY `idx_service_id` (`service_id`),
    KEY `idx_port` (`port`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='端口表';

-- 凭据表
CREATE TABLE IF NOT EXISTS `kn_credential` (
    `id`                  BIGINT       NOT NULL AUTO_INCREMENT,
    `host_id`             BIGINT       DEFAULT NULL COMMENT '关联主机ID',
    `service_id`          BIGINT       DEFAULT NULL COMMENT '关联服务ID',
    `cred_type`           VARCHAR(50)  DEFAULT NULL COMMENT '凭据类型',
    `username`            VARCHAR(200) DEFAULT NULL COMMENT '用户名',
    `password_encrypted`  VARCHAR(500) DEFAULT NULL COMMENT '加密密码',
    `access_key`          VARCHAR(500) DEFAULT NULL COMMENT 'AccessKey',
    `secret_key_encrypted` VARCHAR(500) DEFAULT NULL COMMENT '加密SecretKey',
    `token`               VARCHAR(1000) DEFAULT NULL COMMENT 'Token',
    `remark`              VARCHAR(500) DEFAULT NULL COMMENT '备注',
    `created_at`          DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `updated_at`          DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`             INT          DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_host_id` (`host_id`),
    KEY `idx_service_id` (`service_id`),
    KEY `idx_cred_type` (`cred_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='凭据表';

-- 域名表
CREATE TABLE IF NOT EXISTS `kn_domain` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT,
    `domain`          VARCHAR(255) NOT NULL COMMENT '域名',
    `sub_domain`      VARCHAR(255) DEFAULT NULL COMMENT '子域名',
    `target_host_id`  BIGINT       DEFAULT NULL COMMENT '目标主机ID',
    `target_port`     INT          DEFAULT NULL COMMENT '目标端口',
    `target_service`  VARCHAR(200) DEFAULT NULL COMMENT '目标服务',
    `dns_type`        VARCHAR(20)  DEFAULT NULL COMMENT 'DNS记录类型',
    `status`          VARCHAR(30)  DEFAULT 'active' COMMENT '状态',
    `remark`          VARCHAR(500) DEFAULT NULL COMMENT '备注',
    `created_at`      DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `updated_at`      DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`         INT          DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_domain` (`domain`),
    KEY `idx_target_host_id` (`target_host_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='域名表';

-- 依赖关系表
CREATE TABLE IF NOT EXISTS `kn_dependency` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT,
    `from_type`  VARCHAR(30)  DEFAULT NULL COMMENT '源实体类型',
    `from_id`    BIGINT       DEFAULT NULL COMMENT '源实体ID',
    `to_type`    VARCHAR(30)  DEFAULT NULL COMMENT '目标实体类型',
    `to_id`      BIGINT       DEFAULT NULL COMMENT '目标实体ID',
    `dep_type`   VARCHAR(50)  DEFAULT NULL COMMENT '依赖类型',
    `protocol`   VARCHAR(30)  DEFAULT NULL COMMENT '协议',
    `port`       INT          DEFAULT NULL COMMENT '端口',
    `remark`     VARCHAR(500) DEFAULT NULL COMMENT '备注',
    `created_at` DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`    INT          DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_from` (`from_type`, `from_id`),
    KEY `idx_to` (`to_type`, `to_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='依赖关系表';

-- 命令表
CREATE TABLE IF NOT EXISTS `kn_command` (
    `id`          BIGINT        NOT NULL AUTO_INCREMENT,
    `doc_id`      BIGINT        DEFAULT NULL COMMENT '来源文档ID',
    `command`     TEXT          NOT NULL COMMENT '命令文本',
    `description` VARCHAR(1000) DEFAULT NULL COMMENT '命令描述',
    `category`    VARCHAR(50)   DEFAULT NULL COMMENT '分类',
    `risk_level`  VARCHAR(20)   DEFAULT 'low' COMMENT '风险等级',
    `os_type`     VARCHAR(20)   DEFAULT 'linux' COMMENT '操作系统',
    `created_at`  DATETIME      DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`     INT           DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_doc_id` (`doc_id`),
    KEY `idx_category` (`category`),
    KEY `idx_risk_level` (`risk_level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='命令表';

-- 时间线事件表
CREATE TABLE IF NOT EXISTS `kn_timeline` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `doc_id`      BIGINT       DEFAULT NULL COMMENT '来源文档ID',
    `event_time`  VARCHAR(50)  DEFAULT NULL COMMENT '事件时间',
    `event_type`  VARCHAR(50)  DEFAULT NULL COMMENT '事件类型',
    `title`       VARCHAR(500) NOT NULL COMMENT '事件标题',
    `description` TEXT         DEFAULT NULL COMMENT '事件描述',
    `severity`    VARCHAR(20)  DEFAULT 'low' COMMENT '严重程度',
    `status`      VARCHAR(30)  DEFAULT NULL COMMENT '状态',
    `solution`    TEXT         DEFAULT NULL COMMENT '解决方案',
    `created_at`  DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`     INT          DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_doc_id` (`doc_id`),
    KEY `idx_event_time` (`event_time`),
    KEY `idx_severity` (`severity`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='时间线事件表';

-- 文档-实体关联表
CREATE TABLE IF NOT EXISTS `kn_doc_entity_ref` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `doc_id`        BIGINT       NOT NULL COMMENT '文档ID',
    `entity_type`   VARCHAR(30)  NOT NULL COMMENT '实体类型',
    `entity_id`     BIGINT       NOT NULL COMMENT '实体ID',
    `source_section` VARCHAR(500) DEFAULT NULL COMMENT '来源章节',
    `confidence`    INT          DEFAULT 100 COMMENT '置信度',
    `created_at`    DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `deleted`       INT          DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_doc_id` (`doc_id`),
    KEY `idx_entity` (`entity_type`, `entity_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文档-实体关联表';

-- ============================================================
-- V1 初始化完成
-- 共创建 5 个数据库，38 张表
-- 后续结构变更请使用 V2__*.sql 等版本化脚本（ALTER TABLE）
-- ============================================================
