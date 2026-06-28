-- ============================================================
-- mykng 知识库微服务 - V1 初始化 Schema 回滚脚本
-- ============================================================
-- 版本: V1 (rollback)
-- 说明: 回滚 V1__init_schema.sql 创建的所有表和数据库
-- 警告: 此脚本会删除所有数据，执行前请务必备份！
-- 顺序: 按建表的逆序删除表，最后删除数据库
-- ============================================================
-- 注意: 仅在需要完全回滚 V1 时使用
--       生产环境请使用 V2__*.sql 进行增量变更(ALTER TABLE)
-- ============================================================


-- ============================================================
-- 1. 回滚 kb_intelligence 数据库（知识引擎服务）
-- ============================================================
USE `kb_intelligence`;

DROP TABLE IF EXISTS `kn_doc_entity_ref`;
DROP TABLE IF EXISTS `kn_timeline`;
DROP TABLE IF EXISTS `kn_command`;
DROP TABLE IF EXISTS `kn_dependency`;
DROP TABLE IF EXISTS `kn_domain`;
DROP TABLE IF EXISTS `kn_credential`;
DROP TABLE IF EXISTS `kn_port`;
DROP TABLE IF EXISTS `kn_service`;
DROP TABLE IF EXISTS `kn_host`;
DROP TABLE IF EXISTS `kn_doc`;


-- ============================================================
-- 2. 回滚 kb_knowledge 数据库（知识库服务）
-- ============================================================
USE `kb_knowledge`;

DROP TABLE IF EXISTS `version`;
DROP TABLE IF EXISTS `share_access_log`;
DROP TABLE IF EXISTS `share`;
DROP TABLE IF EXISTS `resource_tag`;
DROP TABLE IF EXISTS `tag`;
DROP TABLE IF EXISTS `web_page`;
DROP TABLE IF EXISTS `doc`;
DROP TABLE IF EXISTS `folder`;
DROP TABLE IF EXISTS `space`;


-- ============================================================
-- 3. 回滚 kb_ops 数据库（运维服务）
-- ============================================================
USE `kb_ops`;

DROP TABLE IF EXISTS `ops_dependency`;
DROP TABLE IF EXISTS `ops_domain`;
DROP TABLE IF EXISTS `ops_credential`;
DROP TABLE IF EXISTS `ops_port`;
DROP TABLE IF EXISTS `operation_log`;
DROP TABLE IF EXISTS `ops_snapshot`;
DROP TABLE IF EXISTS `ops_conflict`;
DROP TABLE IF EXISTS `ops_knowledge`;
DROP TABLE IF EXISTS `ops_change_log`;
DROP TABLE IF EXISTS `ops_service`;
DROP TABLE IF EXISTS `ops_host`;


-- ============================================================
-- 4. 回滚 kb_file 数据库（文件服务）
-- ============================================================
USE `kb_file`;

DROP TABLE IF EXISTS `file_chunk`;
DROP TABLE IF EXISTS `file`;
DROP TABLE IF EXISTS `bucket`;


-- ============================================================
-- 5. 回滚 kb_auth 数据库（认证服务）
-- ============================================================
USE `kb_auth`;

DROP TABLE IF EXISTS `ops_api_token`;
DROP TABLE IF EXISTS `jwt_blacklist`;
DROP TABLE IF EXISTS `refresh_token`;
DROP TABLE IF EXISTS `user`;


-- ============================================================
-- 6. 删除所有数据库
-- ============================================================
DROP DATABASE IF EXISTS `kb_intelligence`;
DROP DATABASE IF EXISTS `kb_knowledge`;
DROP DATABASE IF EXISTS `kb_ops`;
DROP DATABASE IF EXISTS `kb_file`;
DROP DATABASE IF EXISTS `kb_auth`;

-- ============================================================
-- V1 回滚完成
-- 已删除全部 5 个数据库及其下的 38 张表
-- ============================================================
