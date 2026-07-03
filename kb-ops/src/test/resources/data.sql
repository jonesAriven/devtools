-- 测试数据：1台主机、1个服务、1条部署记录
-- 使用 MERGE INTO（H2 兼容语法）避免重复插入

MERGE INTO `ops_host` (`id`, `name`, `ip`, `ssh_port`, `status`, `deleted`, `created_at`, `updated_at`)
    KEY(`id`) VALUES (1, 'test-host', '192.168.1.100', 22, 1, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

MERGE INTO `ops_service` (`id`, `name`, `type`, `version`, `port`, `host_id`, `status`, `deleted`, `created_at`, `updated_at`)
    KEY(`id`) VALUES (1, 'kb-auth', 'web', '1.0.0', 8081, 1, 1, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

MERGE INTO `ops_change_log` (`id`, `service_id`, `service_name`, `host_id`, `version`, `operator`, `result`, `rollback`, `created_at`)
    KEY(`id`) VALUES (1, 1, 'kb-auth', 1, '1.0.0', 'admin', 1, 0, CURRENT_TIMESTAMP);
