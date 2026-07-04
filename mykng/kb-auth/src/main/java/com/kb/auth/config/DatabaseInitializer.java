package com.kb.auth.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Slf4j
@Configuration
public class DatabaseInitializer implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    public DatabaseInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        createTableIfNotExists("operation_log", """
            CREATE TABLE IF NOT EXISTS operation_log (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                user_id BIGINT COMMENT '用户ID',
                username VARCHAR(100) COMMENT '用户名',
                action VARCHAR(100) COMMENT '操作类型',
                resource_type VARCHAR(50) COMMENT '资源类型',
                resource_id BIGINT COMMENT '资源ID',
                detail TEXT COMMENT '操作详情',
                ip VARCHAR(50) COMMENT 'IP地址',
                user_agent VARCHAR(500) COMMENT '用户代理',
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                INDEX idx_user_id (user_id),
                INDEX idx_action (action),
                INDEX idx_resource (resource_type, resource_id),
                INDEX idx_created_at (created_at)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志表'
            """);

        createTableIfNotExists("api_token", """
            CREATE TABLE IF NOT EXISTS api_token (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                user_id BIGINT NOT NULL COMMENT '用户ID',
                name VARCHAR(100) COMMENT '令牌名称',
                token VARCHAR(255) NOT NULL COMMENT '令牌值',
                expires_at DATETIME COMMENT '过期时间',
                last_used_at DATETIME COMMENT '最后使用时间',
                status INT DEFAULT 1 COMMENT '状态 1-启用 0-禁用',
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                deleted INT DEFAULT 0,
                UNIQUE INDEX uk_token (token),
                INDEX idx_user_id (user_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='API令牌表'
            """);

        createTableIfNotExists("jwt_blacklist", """
            CREATE TABLE IF NOT EXISTS jwt_blacklist (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                token VARCHAR(500) NOT NULL COMMENT 'JWT令牌',
                expires_at DATETIME NOT NULL COMMENT '过期时间',
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                INDEX idx_token (token(255)),
                INDEX idx_expires_at (expires_at)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='JWT黑名单表'
            """);

        createTableIfNotExists("refresh_token", """
            CREATE TABLE IF NOT EXISTS refresh_token (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                user_id BIGINT NOT NULL COMMENT '用户ID',
                token VARCHAR(500) NOT NULL COMMENT '刷新令牌',
                expires_at DATETIME NOT NULL COMMENT '过期时间',
                revoked INT DEFAULT 0 COMMENT '是否撤销 0-否 1-是',
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                INDEX idx_user_id (user_id),
                INDEX idx_token (token(255))
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='刷新令牌表'
            """);

        createTableIfNotExists("user", """
            CREATE TABLE IF NOT EXISTS user (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                username VARCHAR(50) NOT NULL COMMENT '用户名',
                password VARCHAR(255) NOT NULL COMMENT '密码',
                nickname VARCHAR(100) COMMENT '昵称',
                email VARCHAR(100) COMMENT '邮箱',
                avatar VARCHAR(500) COMMENT '头像',
                status INT DEFAULT 1 COMMENT '状态 1-启用 0-禁用',
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                deleted INT DEFAULT 0,
                UNIQUE INDEX uk_username (username),
                INDEX idx_email (email)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表'
            """);

        createTableIfNotExists("sys_error_log", """
            CREATE TABLE IF NOT EXISTS sys_error_log (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                user_id BIGINT COMMENT '用户ID',
                username VARCHAR(100) COMMENT '用户名',
                level VARCHAR(20) COMMENT '日志级别 error/warn/info',
                source VARCHAR(50) COMMENT '来源 frontend/backend',
                message TEXT COMMENT '错误信息',
                stack_trace TEXT COMMENT '堆栈信息',
                url VARCHAR(500) COMMENT '页面URL',
                ip VARCHAR(50) COMMENT 'IP地址',
                user_agent VARCHAR(500) COMMENT '用户代理',
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                INDEX idx_user_id (user_id),
                INDEX idx_level (level),
                INDEX idx_source (source),
                INDEX idx_created_at (created_at)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统错误日志表'
            """);

        createTableIfNotExists("sys_request_log", """
            CREATE TABLE IF NOT EXISTS sys_request_log (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                trace_id VARCHAR(64) COMMENT '链路追踪ID',
                user_id BIGINT COMMENT '用户ID',
                username VARCHAR(100) COMMENT '用户名',
                http_method VARCHAR(10) COMMENT 'HTTP方法',
                request_uri VARCHAR(500) COMMENT '请求URI',
                controller_method VARCHAR(200) COMMENT '控制器方法',
                request_args TEXT COMMENT '请求参数',
                response_result TEXT COMMENT '响应结果',
                cost_ms BIGINT COMMENT '耗时(毫秒)',
                status VARCHAR(20) COMMENT '状态 success/error/slow',
                exception TEXT COMMENT '异常信息',
                ip VARCHAR(50) COMMENT 'IP地址',
                user_agent VARCHAR(500) COMMENT '用户代理',
                service_name VARCHAR(50) COMMENT '服务名称',
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                INDEX idx_trace_id (trace_id),
                INDEX idx_user_id (user_id),
                INDEX idx_status (status),
                INDEX idx_created_at (created_at),
                INDEX idx_service (service_name, created_at)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='请求日志表'
            """);

        log.info("认证服务数据库表初始化完成");
    }

    private void createTableIfNotExists(String tableName, String ddl) {
        try {
            jdbcTemplate.execute(ddl);
            log.debug("表 {} 已就绪", tableName);
        } catch (Exception e) {
            log.warn("创建表 {} 失败: {}", tableName, e.getMessage());
        }
    }
}
