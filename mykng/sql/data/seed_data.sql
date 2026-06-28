-- ============================================================
-- mykng 知识库微服务 - 种子数据脚本
-- ============================================================
-- 用途: 初始化基础业务数据，便于系统首次启动后即可使用
-- 内容:
--   1. admin 用户（BCrypt 加密密码）
--   2. 默认知识空间与目录
--   3. 示例运维主机/服务/端口
--   4. 默认 Bucket（如未在 V1 中创建）
-- 特性: 全部使用 WHERE NOT EXISTS，可重复执行（幂等）
-- 依赖: 需先执行 V1__init_schema.sql
-- ============================================================


-- ============================================================
-- 1. kb_auth - 用户种子数据
-- ============================================================
USE `kb_auth`;

-- 1.1 admin 用户（密码: admin123，BCrypt 加密）
--     注: V1__init_schema.sql 已内置此条，这里再次幂等插入以保安全
INSERT INTO `user` (`id`, `username`, `password`, `nickname`, `status`, `deleted`)
SELECT 1, 'admin', '$2a$10$YfuxV6cAdrN0l1JENSprI.ykW1KD7Ggnul8Ex0V6EbriF92wc/mRK', '管理员', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM `user` WHERE `username` = 'admin');

-- 1.2 运维操作员账号（密码: ops123456，BCrypt 加密）
INSERT INTO `user` (`username`, `password`, `nickname`, `status`, `deleted`)
SELECT 'ops_user', '$2a$10$N9qo8uLOickgx2ZMRZoMy.MrqkVq2gX6DdWk7T4VQ3yXaP3kqJ5Ky', '运维用户', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM `user` WHERE `username` = 'ops_user');


-- ============================================================
-- 2. kb_file - 文件服务种子数据
-- ============================================================
USE `kb_file`;

-- 2.1 默认 Bucket（V1 已内置，此处幂等保证）
INSERT INTO `bucket` (`name`, `type`, `lifecycle_days`)
SELECT 'kb-file', 'file', NULL
WHERE NOT EXISTS (SELECT 1 FROM `bucket` WHERE `name` = 'kb-file');

-- 2.2 备份 Bucket
INSERT INTO `bucket` (`name`, `type`, `lifecycle_days`)
SELECT 'kb-backup', 'backup', 90
WHERE NOT EXISTS (SELECT 1 FROM `bucket` WHERE `name` = 'kb-backup');


-- ============================================================
-- 3. kb_knowledge - 知识库种子数据
-- ============================================================
USE `kb_knowledge`;

-- 3.1 admin 的默认私人空间（id=1）
INSERT INTO `space` (`id`, `user_id`, `name`, `type`, `description`, `status`, `deleted`)
SELECT 1, 1, '我的知识库', 'private', '管理员默认私人空间', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM `space` WHERE `id` = 1);

-- 3.2 admin 的运维知识空间（id=2）
INSERT INTO `space` (`id`, `user_id`, `name`, `type`, `description`, `status`, `deleted`)
SELECT 2, 1, '运维知识库', 'team', '团队共享的运维知识空间', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM `space` WHERE `id` = 2);

-- 3.3 默认空间下的根目录（笔记）
INSERT INTO `folder` (`id`, `space_id`, `parent_id`, `name`, `sort_order`, `deleted`)
SELECT 1, 1, 0, '笔记', 0, 0
WHERE NOT EXISTS (SELECT 1 FROM `folder` WHERE `id` = 1);

-- 3.4 默认空间下的根目录（文档）
INSERT INTO `folder` (`id`, `space_id`, `parent_id`, `name`, `sort_order`, `deleted`)
SELECT 2, 1, 0, '文档', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM `folder` WHERE `id` = 2);

-- 3.5 运维知识空间下的根目录
INSERT INTO `folder` (`id`, `space_id`, `parent_id`, `name`, `sort_order`, `deleted`)
SELECT 3, 2, 0, '运维文档', 0, 0
WHERE NOT EXISTS (SELECT 1 FROM `folder` WHERE `id` = 3);

-- 3.6 默认标签
INSERT INTO `tag` (`user_id`, `name`, `color`, `deleted`)
SELECT 1, '重要', '#F56C6C', 0
WHERE NOT EXISTS (SELECT 1 FROM `tag` WHERE `user_id` = 1 AND `name` = '重要');

INSERT INTO `tag` (`user_id`, `name`, `color`, `deleted`)
SELECT 1, '待办', '#E6A23C', 0
WHERE NOT EXISTS (SELECT 1 FROM `tag` WHERE `user_id` = 1 AND `name` = '待办');

INSERT INTO `tag` (`user_id`, `name`, `color`, `deleted`)
SELECT 1, '已完成', '#67C23A', 0
WHERE NOT EXISTS (SELECT 1 FROM `tag` WHERE `user_id` = 1 AND `name` = '已完成');


-- ============================================================
-- 4. kb_ops - 运维服务种子数据
-- ============================================================
USE `kb_ops`;

-- 4.1 示例运维主机：应用服务器（id=1）
INSERT INTO `ops_host` (`id`, `name`, `ip`, `tailscale_ip`, `ssh_port`, `username`, `role`, `status`, `tags`, `remark`)
SELECT 1, 'app-server-01', '192.168.31.100', '100.64.0.1', 22, 'root', 'app', 1, 'prod,核心', '主应用服务器'
WHERE NOT EXISTS (SELECT 1 FROM `ops_host` WHERE `id` = 1);

-- 4.2 示例运维主机：数据库服务器（id=2）
INSERT INTO `ops_host` (`id`, `name`, `ip`, `tailscale_ip`, `ssh_port`, `username`, `role`, `status`, `tags`, `remark`)
SELECT 2, 'db-server-01', '192.168.31.77', '100.64.0.2', 22, 'root', 'db', 1, 'prod,数据库', 'MySQL 数据库服务器'
WHERE NOT EXISTS (SELECT 1 FROM `ops_host` WHERE `id` = 2);

-- 4.3 示例运维主机：缓存服务器（id=3）
INSERT INTO `ops_host` (`id`, `name`, `ip`, `tailscale_ip`, `ssh_port`, `username`, `role`, `status`, `tags`, `remark`)
SELECT 3, 'cache-server-01', '192.168.31.78', '100.64.0.3', 22, 'root', 'cache', 1, 'prod,缓存', 'Redis 缓存服务器'
WHERE NOT EXISTS (SELECT 1 FROM `ops_host` WHERE `id` = 3);

-- 4.4 示例运维服务：MySQL（id=1，部署在 db-server-01）
INSERT INTO `ops_service` (`id`, `name`, `type`, `version`, `port`, `host_id`, `deploy_path`, `status`, `tags`, `remark`)
SELECT 1, 'mysql', 'db', '8.0.36', 3306, 2, '/usr/local/mysql', 1, 'prod,核心', '主数据库'
WHERE NOT EXISTS (SELECT 1 FROM `ops_service` WHERE `id` = 1);

-- 4.5 示例运维服务：Redis（id=2，部署在 cache-server-01）
INSERT INTO `ops_service` (`id`, `name`, `type`, `version`, `port`, `host_id`, `deploy_path`, `status`, `tags`, `remark`)
SELECT 2, 'redis', 'cache', '7.2.4', 6379, 3, '/usr/local/redis', 1, 'prod,缓存', '缓存服务'
WHERE NOT EXISTS (SELECT 1 FROM `ops_service` WHERE `id` = 2);

-- 4.6 示例运维服务：Nginx（id=3，部署在 app-server-01）
INSERT INTO `ops_service` (`id`, `name`, `type`, `version`, `port`, `host_id`, `deploy_path`, `status`, `tags`, `remark`)
SELECT 3, 'nginx', 'web', '1.25.5', 80, 1, '/usr/local/nginx', 1, 'prod,网关', '反向代理'
WHERE NOT EXISTS (SELECT 1 FROM `ops_service` WHERE `id` = 3);

-- 4.7 示例端口：db-server-01 的 MySQL 端口
INSERT INTO `ops_port` (`host_id`, `port`, `protocol`, `service_id`, `purpose`, `status`, `exposed`, `remark`)
SELECT 2, 3306, 'TCP', 1, 'MySQL 数据库端口', 1, 0, '仅内网访问'
WHERE NOT EXISTS (SELECT 1 FROM `ops_port` WHERE `host_id` = 2 AND `port` = 3306);

-- 4.8 示例端口：cache-server-01 的 Redis 端口
INSERT INTO `ops_port` (`host_id`, `port`, `protocol`, `service_id`, `purpose`, `status`, `exposed`, `remark`)
SELECT 3, 6379, 'TCP', 2, 'Redis 缓存端口', 1, 0, '仅内网访问'
WHERE NOT EXISTS (SELECT 1 FROM `ops_port` WHERE `host_id` = 3 AND `port` = 6379);

-- 4.9 示例端口：app-server-01 的 Nginx 端口
INSERT INTO `ops_port` (`host_id`, `port`, `protocol`, `service_id`, `purpose`, `status`, `exposed`, `remark`)
SELECT 1, 80, 'TCP', 3, 'Nginx HTTP 端口', 1, 1, '对外暴露'
WHERE NOT EXISTS (SELECT 1 FROM `ops_port` WHERE `host_id` = 1 AND `port` = 80);

-- 4.10 示例凭据：db-server-01 的 SSH 凭据（密码已加密占位）
INSERT INTO `ops_credential` (`name`, `type`, `username`, `password_encrypted`, `host_id`, `remark`)
SELECT 'db-server-ssh', 'SSH', 'root', 'ENCRYPTED_PLACEHOLDER_REPLACE_WITH_REAL_AES256GCM_CIPHER', 2, '数据库服务器 SSH 凭据'
WHERE NOT EXISTS (SELECT 1 FROM `ops_credential` WHERE `name` = 'db-server-ssh');

-- 4.11 示例域名
INSERT INTO `ops_domain` (`domain`, `type`, `purpose`, `registrar`, `expires_at`, `ssl_expires_at`, `status`, `remark`)
SELECT 'marschat.online', '顶级域', '主域名', '阿里云', '2027-06-28 00:00:00', '2026-12-28 00:00:00', 1, '主业务域名'
WHERE NOT EXISTS (SELECT 1 FROM `ops_domain` WHERE `domain` = 'marschat.online');

-- 4.12 示例运维知识：部署规范
INSERT INTO `ops_knowledge` (`title`, `category`, `content`, `tags`, `author`, `view_count`)
SELECT 'MySQL 部署规范', '规范', '# MySQL 部署规范\n\n## 1. 端口\n- 默认 3306\n- 仅内网访问\n\n## 2. 字符集\n- utf8mb4_unicode_ci\n\n## 3. 备份\n- 每日凌晨全量备份', 'mysql,规范,部署', 'admin', 0
WHERE NOT EXISTS (SELECT 1 FROM `ops_knowledge` WHERE `title` = 'MySQL 部署规范');

-- 4.13 示例运维知识：Redis 巡检
INSERT INTO `ops_knowledge` (`title`, `category`, `content`, `tags`, `author`, `view_count`)
SELECT 'Redis 巡检清单', '巡检', '# Redis 巡检清单\n\n## 1. 内存使用\n- `redis-cli INFO memory`\n- used_memory_ratio < 80%\n\n## 2. 慢查询\n- `redis-cli SLOWLOG GET 10`\n\n## 3. 持久化\n- 检查 RDB/AOF 文件', 'redis,巡检', 'admin', 0
WHERE NOT EXISTS (SELECT 1 FROM `ops_knowledge` WHERE `title` = 'Redis 巡检清单');

-- 4.14 示例看板快照
INSERT INTO `ops_snapshot` (`snapshot_date`, `metric_key`, `metric_value`, `extra`)
SELECT CURDATE(), 'host_total', 3, '{"running":3,"stopped":0,"maintenance":0}'
WHERE NOT EXISTS (SELECT 1 FROM `ops_snapshot` WHERE `snapshot_date` = CURDATE() AND `metric_key` = 'host_total');

INSERT INTO `ops_snapshot` (`snapshot_date`, `metric_key`, `metric_value`, `extra`)
SELECT CURDATE(), 'service_total', 3, '{"running":3,"stopped":0,"abnormal":0}'
WHERE NOT EXISTS (SELECT 1 FROM `ops_snapshot` WHERE `snapshot_date` = CURDATE() AND `metric_key` = 'service_total');


-- ============================================================
-- 5. kb_intelligence - 知识引擎种子数据
-- ============================================================
USE `kb_intelligence`;

-- 5.1 示例主机记录（与 kb_ops.ops_host 对应）
INSERT INTO `kn_host` (`name`, `ip`, `tailscale_ip`, `ssh_port`, `username`, `os_type`, `os_version`, `cpu_arch`, `cpu_cores`, `memory_gb`, `role`, `environment`, `location`, `status`, `tags`, `remark`)
SELECT 'app-server-01', '192.168.31.100', '100.64.0.1', 22, 'root', 'linux', 'Ubuntu 22.04 LTS', 'x86_64', 8, 16, 'app', 'prod', '内网机房', 'running', 'prod,核心', '主应用服务器'
WHERE NOT EXISTS (SELECT 1 FROM `kn_host` WHERE `ip` = '192.168.31.100');

INSERT INTO `kn_host` (`name`, `ip`, `tailscale_ip`, `ssh_port`, `username`, `os_type`, `os_version`, `cpu_arch`, `cpu_cores`, `memory_gb`, `role`, `environment`, `location`, `status`, `tags`, `remark`)
SELECT 'db-server-01', '192.168.31.77', '100.64.0.2', 22, 'root', 'linux', 'Ubuntu 22.04 LTS', 'x86_64', 4, 8, 'db', 'prod', '内网机房', 'running', 'prod,数据库', 'MySQL 数据库服务器'
WHERE NOT EXISTS (SELECT 1 FROM `kn_host` WHERE `ip` = '192.168.31.77');

-- 5.2 示例知识文档
INSERT INTO `kn_doc` (`source_id`, `title`, `file_path`, `doc_type`, `category`, `tags`, `summary`, `content_hash`, `entity_count`, `command_count`, `section_count`, `word_count`, `status`)
SELECT 'seed-001', '系统部署手册', '/docs/deploy-manual.md', 'PLAN', '部署', '部署,规范', '系统全栈部署步骤说明', SHA2('seed-doc-deploy-manual', 256), 0, 0, 3, 100, 1
WHERE NOT EXISTS (SELECT 1 FROM `kn_doc` WHERE `source_id` = 'seed-001');

-- 5.3 示例命令记录
INSERT INTO `kn_command` (`doc_id`, `command`, `description`, `category`, `risk_level`, `os_type`)
SELECT 1, 'systemctl restart nginx', '重启 Nginx 服务', '运维', 'low', 'linux'
WHERE NOT EXISTS (SELECT 1 FROM `kn_command` WHERE `command` = 'systemctl restart nginx' AND `doc_id` = 1);

INSERT INTO `kn_command` (`doc_id`, `command`, `description`, `category`, `risk_level`, `os_type`)
SELECT 1, 'mysql -u root -p', '登录 MySQL', '数据库', 'low', 'linux'
WHERE NOT EXISTS (SELECT 1 FROM `kn_command` WHERE `command` = 'mysql -u root -p' AND `doc_id` = 1);


-- ============================================================
-- 种子数据初始化完成
-- 已创建:
--   - kb_auth: 2 个用户（admin / ops_user）
--   - kb_file: 2 个 Bucket
--   - kb_knowledge: 2 个空间、3 个目录、3 个标签
--   - kb_ops: 3 台主机、3 个服务、3 个端口、1 个凭据、1 个域名、2 篇运维知识、2 条快照
--   - kb_intelligence: 2 台主机、1 篇文档、2 条命令
-- ============================================================
