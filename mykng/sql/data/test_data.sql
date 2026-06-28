-- ============================================================
-- mykng 知识库微服务 - 测试数据脚本（仅用于测试环境）
-- ============================================================
-- 用途: 为测试环境填充测试数据，便于功能验证与集成测试
-- 内容:
--   1. 测试用户（testuser1 / testuser2）
--   2. 测试知识空间、目录、文档
--   3. 测试文件记录
--   4. 测试运维数据
--   5. 测试操作日志
-- 特性: 全部使用 WHERE NOT EXISTS，可重复执行（幂等）
--       测试数据均以 "test_" 前缀命名，便于识别与清理
-- 依赖: 需先执行 V1__init_schema.sql 和 seed_data.sql
-- ============================================================
--
-- 清理测试数据（执行下方语句即可清理本脚本产生的测试数据）:
--
--   USE kb_auth;        DELETE FROM `user`            WHERE username LIKE 'test_%';
--   USE kb_file;        DELETE FROM `file`            WHERE name LIKE 'test_%';
--   USE kb_file;        DELETE FROM `file_chunk`      WHERE file_id LIKE 'test_%';
--   USE kb_knowledge;   DELETE FROM `doc`             WHERE title LIKE 'test_%';
--   USE kb_knowledge;   DELETE FROM `web_page`        WHERE title LIKE 'test_%';
--   USE kb_knowledge;   DELETE FROM `folder`          WHERE name LIKE 'test_%';
--   USE kb_knowledge;   DELETE FROM `space`           WHERE name LIKE 'test_%';
--   USE kb_knowledge;   DELETE FROM `tag`             WHERE name LIKE 'test_%';
--   USE kb_ops;         DELETE FROM `ops_host`        WHERE name LIKE 'test_%';
--   USE kb_ops;         DELETE FROM `ops_service`     WHERE name LIKE 'test_%';
--   USE kb_ops;         DELETE FROM `ops_change_log`  WHERE service_name LIKE 'test_%';
--   USE kb_ops;         DELETE FROM `ops_knowledge`   WHERE title LIKE 'test_%';
--   USE kb_ops;         DELETE FROM `operation_log`   WHERE username LIKE 'test_%';
--   USE kb_intelligence; DELETE FROM `kn_doc`         WHERE title LIKE 'test_%';
--   USE kb_intelligence; DELETE FROM `kn_host`        WHERE name LIKE 'test_%';
--
-- ============================================================


-- ============================================================
-- 1. kb_auth - 测试用户
-- ============================================================
USE `kb_auth`;

-- 1.1 测试用户1（密码: test123456，BCrypt 加密）
INSERT INTO `user` (`username`, `password`, `email`, `nickname`, `status`, `deleted`)
SELECT 'test_user1', '$2a$10$2mE6R8Q4jVqKqYqX5yXqXuO8kZq8vXqKqYqX5yXqXuO8kZq8vXqK', 'test_user1@example.com', '测试用户1', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM `user` WHERE `username` = 'test_user1');

-- 1.2 测试用户2（密码: test123456，BCrypt 加密）
INSERT INTO `user` (`username`, `password`, `email`, `nickname`, `status`, `deleted`)
SELECT 'test_user2', '$2a$10$2mE6R8Q4jVqKqYqX5yXqXuO8kZq8vXqKqYqX5yXqXuO8kZq8vXqK', 'test_user2@example.com', '测试用户2', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM `user` WHERE `username` = 'test_user2');

-- 1.3 已禁用的测试用户（用于测试登录禁用场景）
INSERT INTO `user` (`username`, `password`, `email`, `nickname`, `status`, `deleted`)
SELECT 'test_disabled', '$2a$10$2mE6R8Q4jVqKqYqX5yXqXuO8kZq8vXqKqYqX5yXqXuO8kZq8vXqK', 'test_disabled@example.com', '已禁用测试用户', 0, 0
WHERE NOT EXISTS (SELECT 1 FROM `user` WHERE `username` = 'test_disabled');

-- 1.4 测试 API Token
INSERT INTO `ops_api_token` (`user_id`, `name`, `token_encrypted`, `token_prefix`, `scope`, `status`)
SELECT (SELECT id FROM `user` WHERE username = 'test_user1'),
       'test_api_token', 'TEST_ENCRYPTED_TOKEN_PLACEHOLDER', 'test_abc1', 'read,write', 0
WHERE NOT EXISTS (SELECT 1 FROM `ops_api_token` WHERE `name` = 'test_api_token');


-- ============================================================
-- 2. kb_file - 测试文件记录
-- ============================================================
USE `kb_file`;

-- 2.1 测试文件1（已解析完成）
INSERT INTO `file` (`folder_id`, `user_id`, `name`, `type`, `size`, `minio_path`, `parse_status`, `starred`, `deleted`)
SELECT NULL, (SELECT id FROM `kb_auth`.`user` WHERE username = 'test_user1'),
       'test_document.pdf', 'pdf', 102400, 'kb-file/test/test_document.pdf', 'READY', 0, 0
WHERE NOT EXISTS (SELECT 1 FROM `file` WHERE `name` = 'test_document.pdf');

-- 2.2 测试文件2（解析中）
INSERT INTO `file` (`folder_id`, `user_id`, `name`, `type`, `size`, `minio_path`, `parse_status`, `starred`, `deleted`)
SELECT NULL, (SELECT id FROM `kb_auth`.`user` WHERE username = 'test_user1'),
       'test_report.docx', 'docx', 204800, 'kb-file/test/test_report.docx', 'PARSING', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM `file` WHERE `name` = 'test_report.docx');

-- 2.3 测试文件3（解析失败）
INSERT INTO `file` (`folder_id`, `user_id`, `name`, `type`, `size`, `minio_path`, `parse_status`, `parse_error`, `starred`, `deleted`)
SELECT NULL, (SELECT id FROM `kb_auth`.`user` WHERE username = 'test_user2'),
       'test_corrupt.txt', 'txt', 512, 'kb-file/test/test_corrupt.txt', 'PARSE_FAILED', '文件编码不支持', 0, 0
WHERE NOT EXISTS (SELECT 1 FROM `file` WHERE `name` = 'test_corrupt.txt');

-- 2.4 测试文件分片
INSERT INTO `file_chunk` (`file_id`, `chunk_number`, `chunk_path`)
SELECT 'test_upload_chunk_id_001', 1, 'kb-file/test/chunks/chunk_001'
WHERE NOT EXISTS (SELECT 1 FROM `file_chunk` WHERE `file_id` = 'test_upload_chunk_id_001' AND `chunk_number` = 1);

INSERT INTO `file_chunk` (`file_id`, `chunk_number`, `chunk_path`)
SELECT 'test_upload_chunk_id_001', 2, 'kb-file/test/chunks/chunk_002'
WHERE NOT EXISTS (SELECT 1 FROM `file_chunk` WHERE `file_id` = 'test_upload_chunk_id_001' AND `chunk_number` = 2);


-- ============================================================
-- 3. kb_knowledge - 测试知识库数据
-- ============================================================
USE `kb_knowledge`;

-- 3.1 测试用户1的私人空间
INSERT INTO `space` (`user_id`, `name`, `type`, `description`, `status`, `deleted`)
SELECT (SELECT id FROM `kb_auth`.`user` WHERE username = 'test_user1'),
       'test_我的测试空间', 'private', '测试用私人空间', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM `space` WHERE `name` = 'test_我的测试空间');

-- 3.2 测试用户1的团队空间
INSERT INTO `space` (`user_id`, `name`, `type`, `description`, `status`, `deleted`)
SELECT (SELECT id FROM `kb_auth`.`user` WHERE username = 'test_user1'),
       'test_团队测试空间', 'team', '测试用团队空间', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM `space` WHERE `name` = 'test_团队测试空间');

-- 3.3 测试目录
INSERT INTO `folder` (`space_id`, `parent_id`, `name`, `sort_order`, `deleted`)
SELECT (SELECT id FROM `space` WHERE `name` = 'test_我的测试空间'),
       0, 'test_测试目录1', 0, 0
WHERE NOT EXISTS (SELECT 1 FROM `folder` WHERE `name` = 'test_测试目录1');

INSERT INTO `folder` (`space_id`, `parent_id`, `name`, `sort_order`, `deleted`)
SELECT (SELECT id FROM `space` WHERE `name` = 'test_我的测试空间'),
       0, 'test_测试目录2', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM `folder` WHERE `name` = 'test_测试目录2');

-- 3.4 测试笔记
INSERT INTO `doc` (`folder_id`, `user_id`, `title`, `starred`, `deleted`)
SELECT (SELECT id FROM `folder` WHERE `name` = 'test_测试目录1'),
       (SELECT id FROM `kb_auth`.`user` WHERE username = 'test_user1'),
       'test_测试笔记1', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM `doc` WHERE `title` = 'test_测试笔记1');

INSERT INTO `doc` (`folder_id`, `user_id`, `title`, `starred`, `deleted`)
SELECT (SELECT id FROM `folder` WHERE `name` = 'test_测试目录1'),
       (SELECT id FROM `kb_auth`.`user` WHERE username = 'test_user1'),
       'test_测试笔记2', 0, 0
WHERE NOT EXISTS (SELECT 1 FROM `doc` WHERE `title` = 'test_测试笔记2');

-- 3.5 测试网页收藏
INSERT INTO `web_page` (`folder_id`, `user_id`, `url`, `title`, `snapshot_path`, `starred`, `deleted`)
SELECT (SELECT id FROM `folder` WHERE `name` = 'test_测试目录2'),
       (SELECT id FROM `kb_auth`.`user` WHERE username = 'test_user1'),
       'https://example.com/test', 'test_示例网页', NULL, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM `web_page` WHERE `title` = 'test_示例网页');

-- 3.6 测试标签
INSERT INTO `tag` (`user_id`, `name`, `color`, `deleted`)
SELECT (SELECT id FROM `kb_auth`.`user` WHERE username = 'test_user1'),
       'test_标签1', '#409EFF', 0
WHERE NOT EXISTS (SELECT 1 FROM `tag` WHERE `name` = 'test_标签1' AND `user_id` = (SELECT id FROM `kb_auth`.`user` WHERE username = 'test_user1'));

-- 3.7 测试分享
INSERT INTO `share` (`user_id`, `resource_type`, `resource_id`, `code`, `extract_code`, `expire_at`, `view_count`, `deleted`)
SELECT (SELECT id FROM `kb_auth`.`user` WHERE username = 'test_user1'),
       'doc',
       (SELECT id FROM `doc` WHERE `title` = 'test_测试笔记1'),
       'test_share_code_001', '1234', DATE_ADD(NOW(), INTERVAL 7 DAY), 5, 0
WHERE NOT EXISTS (SELECT 1 FROM `share` WHERE `code` = 'test_share_code_001');

-- 3.8 测试分享访问日志
INSERT INTO `share_access_log` (`share_id`, `ip`, `user_agent`)
SELECT (SELECT id FROM `share` WHERE `code` = 'test_share_code_001'),
       '192.168.1.100', 'Mozilla/5.0 (Test Browser)'
WHERE NOT EXISTS (
    SELECT 1 FROM `share_access_log`
    WHERE `share_id` = (SELECT id FROM `share` WHERE `code` = 'test_share_code_001')
);


-- ============================================================
-- 4. kb_ops - 测试运维数据
-- ============================================================
USE `kb_ops`;

-- 4.1 测试主机
INSERT INTO `ops_host` (`name`, `ip`, `ssh_port`, `username`, `role`, `status`, `tags`, `remark`)
SELECT 'test_host_01', '10.0.0.101', 22, 'root', 'app', 1, 'test,测试', '测试主机'
WHERE NOT EXISTS (SELECT 1 FROM `ops_host` WHERE `name` = 'test_host_01');

INSERT INTO `ops_host` (`name`, `ip`, `ssh_port`, `username`, `role`, `status`, `tags`, `remark`)
SELECT 'test_host_02', '10.0.0.102', 22, 'root', 'db', 0, 'test,测试,已停机', '已停机的测试主机'
WHERE NOT EXISTS (SELECT 1 FROM `ops_host` WHERE `name` = 'test_host_02');

-- 4.2 测试服务
INSERT INTO `ops_service` (`name`, `type`, `version`, `port`, `host_id`, `deploy_path`, `status`, `tags`, `remark`)
SELECT 'test_service_nginx', 'web', '1.25.0', 8080,
       (SELECT id FROM `ops_host` WHERE `name` = 'test_host_01'),
       '/usr/local/nginx', 1, 'test,测试', '测试用 Nginx 服务'
WHERE NOT EXISTS (SELECT 1 FROM `ops_service` WHERE `name` = 'test_service_nginx');

INSERT INTO `ops_service` (`name`, `type`, `version`, `port`, `host_id`, `deploy_path`, `status`, `tags`, `remark`)
SELECT 'test_service_mysql', 'db', '8.0.30', 3307,
       (SELECT id FROM `ops_host` WHERE `name` = 'test_host_01'),
       '/usr/local/mysql', 2, 'test,测试,异常', '异常状态的测试 MySQL'
WHERE NOT EXISTS (SELECT 1 FROM `ops_service` WHERE `name` = 'test_service_mysql');

-- 4.3 测试端口
INSERT INTO `ops_port` (`host_id`, `port`, `protocol`, `service_id`, `purpose`, `status`, `exposed`, `remark`)
SELECT (SELECT id FROM `ops_host` WHERE `name` = 'test_host_01'),
       8080, 'TCP', (SELECT id FROM `ops_service` WHERE `name` = 'test_service_nginx'),
       '测试 Nginx 端口', 1, 0, '测试端口'
WHERE NOT EXISTS (
    SELECT 1 FROM `ops_port`
    WHERE `host_id` = (SELECT id FROM `ops_host` WHERE `name` = 'test_host_01')
      AND `port` = 8080
);

-- 4.4 测试部署记录
INSERT INTO `ops_change_log` (`service_id`, `service_name`, `host_id`, `version`, `previous_version`, `operator`, `deploy_time`, `result`, `rollback`, `remark`)
SELECT (SELECT id FROM `ops_service` WHERE `name` = 'test_service_nginx'),
       'test_service_nginx',
       (SELECT id FROM `ops_host` WHERE `name` = 'test_host_01'),
       '1.25.0', '1.24.0', 'test_user1', NOW(), 1, 0, '测试部署记录'
WHERE NOT EXISTS (
    SELECT 1 FROM `ops_change_log`
    WHERE `service_name` = 'test_service_nginx' AND `version` = '1.25.0'
);

-- 4.5 测试矛盾检测记录
INSERT INTO `ops_conflict` (`rule_code`, `rule_name`, `severity`, `target_type`, `target_id`, `target_name`, `detail`, `status`)
SELECT 'PORT_CONFLICT', '端口冲突', 2, 'SERVICE',
       (SELECT id FROM `ops_service` WHERE `name` = 'test_service_nginx'),
       'test_service_nginx', '端口 8080 与其他服务冲突', 0
WHERE NOT EXISTS (
    SELECT 1 FROM `ops_conflict`
    WHERE `target_name` = 'test_service_nginx' AND `rule_code` = 'PORT_CONFLICT'
);

-- 4.6 测试运维知识
INSERT INTO `ops_knowledge` (`title`, `category`, `content`, `tags`, `author`, `view_count`)
SELECT 'test_测试部署文档', '部署', '# 测试部署文档\n\n用于测试的部署说明', 'test,测试', 'test_user1', 0
WHERE NOT EXISTS (SELECT 1 FROM `ops_knowledge` WHERE `title` = 'test_测试部署文档');

-- 4.7 测试凭据
INSERT INTO `ops_credential` (`name`, `type`, `username`, `password_encrypted`, `host_id`, `remark`)
SELECT 'test_credential_ssh', 'SSH', 'root', 'TEST_ENCRYPTED_PLACEHOLDER',
       (SELECT id FROM `ops_host` WHERE `name` = 'test_host_01'), '测试 SSH 凭据'
WHERE NOT EXISTS (SELECT 1 FROM `ops_credential` WHERE `name` = 'test_credential_ssh');

-- 4.8 测试操作日志
INSERT INTO `operation_log` (`user_id`, `username`, `action`, `resource_type`, `resource_id`, `detail`, `ip`, `user_agent`)
SELECT (SELECT id FROM `kb_auth`.`user` WHERE username = 'test_user1'),
       'test_user1', 'LOGIN', NULL, NULL, '测试登录', '192.168.1.100', 'Mozilla/5.0 (Test)';

INSERT INTO `operation_log` (`user_id`, `username`, `action`, `resource_type`, `resource_id`, `detail`, `ip`, `user_agent`)
SELECT (SELECT id FROM `kb_auth`.`user` WHERE username = 'test_user1'),
       'test_user1', 'UPLOAD', 'file', 1, '测试上传文件', '192.168.1.100', 'Mozilla/5.0 (Test)';

INSERT INTO `operation_log` (`user_id`, `username`, `action`, `resource_type`, `resource_id`, `detail`, `ip`, `user_agent`)
SELECT (SELECT id FROM `kb_auth`.`user` WHERE username = 'test_user2'),
       'test_user2', 'DELETE', 'doc', 1, '测试删除文档', '192.168.1.101', 'Mozilla/5.0 (Test)');


-- ============================================================
-- 5. kb_intelligence - 测试知识引擎数据
-- ============================================================
USE `kb_intelligence`;

-- 5.1 测试主机
INSERT INTO `kn_host` (`name`, `ip`, `ssh_port`, `username`, `os_type`, `os_version`, `cpu_arch`, `cpu_cores`, `memory_gb`, `role`, `environment`, `status`, `tags`, `remark`)
SELECT 'test_kn_host_01', '10.0.0.201', 22, 'root', 'linux', 'CentOS 7', 'x86_64', 2, 4, 'app', 'test', 'running', 'test,测试', '测试主机'
WHERE NOT EXISTS (SELECT 1 FROM `kn_host` WHERE `name` = 'test_kn_host_01');

-- 5.2 测试知识文档
INSERT INTO `kn_doc` (`source_id`, `title`, `file_path`, `doc_type`, `category`, `tags`, `summary`, `content_hash`, `entity_count`, `command_count`, `section_count`, `word_count`, `status`)
SELECT 'test-001', 'test_测试知识文档', '/test/docs/test-doc.md', 'TABLE', '测试', 'test,测试',
       '用于测试的知识文档', SHA2('test-doc-content', 256), 3, 2, 2, 200, 1
WHERE NOT EXISTS (SELECT 1 FROM `kn_doc` WHERE `source_id` = 'test-001');

INSERT INTO `kn_doc` (`source_id`, `title`, `file_path`, `doc_type`, `category`, `tags`, `summary`, `content_hash`, `entity_count`, `command_count`, `section_count`, `word_count`, `status`)
SELECT 'test-002', 'test_测试时间线文档', '/test/docs/test-timeline.md', 'TIMELINE', '测试', 'test,时间线',
       '测试时间线文档', SHA2('test-timeline-content', 256), 0, 0, 1, 100, 1
WHERE NOT EXISTS (SELECT 1 FROM `kn_doc` WHERE `source_id` = 'test-002');

-- 5.3 测试命令记录
INSERT INTO `kn_command` (`doc_id`, `command`, `description`, `category`, `risk_level`, `os_type`)
SELECT (SELECT id FROM `kn_doc` WHERE `source_id` = 'test-001'),
       'test_command_echo', '测试命令', '测试', 'low', 'linux'
WHERE NOT EXISTS (SELECT 1 FROM `kn_command` WHERE `command` = 'test_command_echo');

-- 5.4 测试时间线事件
INSERT INTO `kn_timeline` (`doc_id`, `event_time`, `event_type`, `title`, `description`, `severity`, `status`)
SELECT (SELECT id FROM `kn_doc` WHERE `source_id` = 'test-002'),
       '2026-06-28 10:00:00', 'test', 'test_测试事件', '测试事件描述', 'low', 'resolved'
WHERE NOT EXISTS (SELECT 1 FROM `kn_timeline` WHERE `title` = 'test_测试事件');

-- 5.5 测试文档-实体关联
INSERT INTO `kn_doc_entity_ref` (`doc_id`, `entity_type`, `entity_id`, `source_section`, `confidence`)
SELECT (SELECT id FROM `kn_doc` WHERE `source_id` = 'test-001'),
       'HOST', (SELECT id FROM `kn_host` WHERE `name` = 'test_kn_host_01'),
       '测试章节', 95
WHERE NOT EXISTS (
    SELECT 1 FROM `kn_doc_entity_ref`
    WHERE `doc_id` = (SELECT id FROM `kn_doc` WHERE `source_id` = 'test-001')
      AND `entity_type` = 'HOST'
      AND `entity_id` = (SELECT id FROM `kn_host` WHERE `name` = 'test_kn_host_01')
);


-- ============================================================
-- 测试数据初始化完成
-- 已创建:
--   - kb_auth: 3 个测试用户 + 1 个测试 API Token
--   - kb_file: 3 个测试文件 + 2 个测试分片
--   - kb_knowledge: 2 个测试空间、2 个测试目录、2 篇测试笔记、1 个网页收藏、1 个测试标签、1 个测试分享 + 访问日志
--   - kb_ops: 2 台测试主机、2 个测试服务、1 个测试端口、1 条部署记录、1 条矛盾检测、1 篇测试运维知识、1 个测试凭据、3 条测试操作日志
--   - kb_intelligence: 1 台测试主机、2 篇测试文档、1 条测试命令、1 条测试时间线、1 条文档-实体关联
--
-- 清理方法: 见文件顶部注释
-- ============================================================
