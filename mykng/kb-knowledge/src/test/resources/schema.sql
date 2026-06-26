-- H2 兼容的建表脚本（MySQL MODE，已去除 MySQL 特有的 ENGINE/CHARSET/COLLATE/COMMENT/ON UPDATE 语法）
-- 对应 kb_knowledge 数据库的 space / folder / doc / web_page / tag / resource_tag / share / share_access_log / version 表

-- 空间表
CREATE TABLE IF NOT EXISTS `space` (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    user_id      BIGINT       NOT NULL,
    name         VARCHAR(100) NOT NULL,
    type         VARCHAR(20)  DEFAULT 'private',
    description  VARCHAR(500) DEFAULT NULL,
    status       INT          DEFAULT 1,
    deleted      INT          DEFAULT 0,
    created_at   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_space_user_id ON `space` (user_id);

-- 目录表
CREATE TABLE IF NOT EXISTS `folder` (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    space_id     BIGINT       NOT NULL,
    parent_id    BIGINT       DEFAULT 0,
    name         VARCHAR(200) NOT NULL,
    sort_order   INT          DEFAULT 0,
    deleted      INT          DEFAULT 0,
    created_at   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_folder_space_id ON `folder` (space_id);
CREATE INDEX IF NOT EXISTS idx_folder_parent_id ON `folder` (parent_id);

-- 笔记表
CREATE TABLE IF NOT EXISTS `doc` (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    folder_id    BIGINT       NOT NULL,
    user_id      BIGINT       NOT NULL,
    title        VARCHAR(500) NOT NULL,
    starred      INT          DEFAULT 0,
    deleted      INT          DEFAULT 0,
    created_at   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_doc_folder_id ON `doc` (folder_id);
CREATE INDEX IF NOT EXISTS idx_doc_user_id ON `doc` (user_id);

-- 网页收藏表
CREATE TABLE IF NOT EXISTS `web_page` (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    folder_id      BIGINT        NOT NULL,
    user_id        BIGINT        NOT NULL,
    url            VARCHAR(2000) NOT NULL,
    title          VARCHAR(500)  NOT NULL,
    snapshot_path  VARCHAR(1000) DEFAULT NULL,
    starred        INT           DEFAULT 0,
    deleted        INT           DEFAULT 0,
    created_at     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_web_page_folder_id ON `web_page` (folder_id);
CREATE INDEX IF NOT EXISTS idx_web_page_user_id ON `web_page` (user_id);

-- 标签表
CREATE TABLE IF NOT EXISTS `tag` (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    user_id      BIGINT       NOT NULL,
    name         VARCHAR(100) NOT NULL,
    color        VARCHAR(20)  DEFAULT NULL,
    deleted      INT          DEFAULT 0,
    created_at   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_tag_user_name ON `tag` (user_id, name);

-- 资源标签关联表
CREATE TABLE IF NOT EXISTS `resource_tag` (
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    tag_id         BIGINT      NOT NULL,
    resource_type  VARCHAR(20) NOT NULL,
    resource_id    BIGINT      NOT NULL,
    created_at     TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_resource_tag_tag_id ON `resource_tag` (tag_id);
CREATE INDEX IF NOT EXISTS idx_resource_tag_resource ON `resource_tag` (resource_type, resource_id);

-- 分享表
CREATE TABLE IF NOT EXISTS `share` (
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    user_id        BIGINT      NOT NULL,
    resource_type VARCHAR(20) NOT NULL,
    resource_id   BIGINT      NOT NULL,
    code           VARCHAR(50) NOT NULL,
    extract_code   VARCHAR(10) DEFAULT NULL,
    expire_at      TIMESTAMP   DEFAULT NULL,
    view_count     INT         DEFAULT 0,
    deleted        INT         DEFAULT 0,
    created_at     TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_share_code UNIQUE (code)
);
CREATE INDEX IF NOT EXISTS idx_share_user_id ON `share` (user_id);

-- 分享访问日志表
CREATE TABLE IF NOT EXISTS `share_access_log` (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    share_id     BIGINT       NOT NULL,
    ip           VARCHAR(50)  DEFAULT NULL,
    user_agent   VARCHAR(500) DEFAULT NULL,
    accessed_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_share_access_log_share_id ON `share_access_log` (share_id);

-- 版本表
CREATE TABLE IF NOT EXISTS `version` (
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    resource_type  VARCHAR(20) NOT NULL,
    resource_id    BIGINT      NOT NULL,
    version_num    INT         DEFAULT 1,
    created_at     TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_version_resource ON `version` (resource_type, resource_id);
