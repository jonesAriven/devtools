package com.jones.activation;

import com.jones.activation.controller.AuthController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

@Component
public class DatabaseInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseInitializer.class);

    private final AuthController authController;

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    @Value("${spring.datasource.username}")
    private String username;

    @Value("${spring.datasource.password}")
    private String password;

    public DatabaseInitializer(AuthController authController) {
        this.authController = authController;
    }

    @Override
    public void run(String... args) throws Exception {
        // 从配置的数据源URL中提取不含数据库名的URL，用于创建数据库
        // 例如: jdbc:mysql://192.168.31.77:3306/tools?... -> jdbc:mysql://192.168.31.77:3306?...
        String urlWithoutDb = datasourceUrl.replaceFirst("/tools\\?", "?");

        String[] sqlStatements = {
            "CREATE DATABASE IF NOT EXISTS tools DEFAULT CHARACTER SET utf8mb4 DEFAULT COLLATE utf8mb4_unicode_ci",
            "USE tools",
            "CREATE TABLE IF NOT EXISTS activation_record (" +
            "    id BIGINT AUTO_INCREMENT PRIMARY KEY," +
            "    serial_number VARCHAR(512) NOT NULL COMMENT '唯一序列号'," +
            "    device_id VARCHAR(128) DEFAULT '' COMMENT '设备ID'," +
            "    activation_code TEXT NOT NULL COMMENT '激活码'," +
            "    expire_time BIGINT NOT NULL COMMENT '过期时间戳(毫秒)'," +
            "    activated_time DATETIME DEFAULT NULL COMMENT '激活时间'," +
            "    expire_minutes INT DEFAULT NULL COMMENT '有效期(分钟)'," +
            "    initial_serial VARCHAR(256) DEFAULT NULL COMMENT '初始序列号'," +
            "    machine_code VARCHAR(256) DEFAULT NULL COMMENT '机器码'," +
            "    device_alias VARCHAR(128) DEFAULT NULL COMMENT '设备别名'," +
            "    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'," +
            "    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'," +
            "    UNIQUE KEY uk_serial_number (serial_number)," +
            "    UNIQUE KEY uk_device_alias (device_alias)" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='激活码记录表'",
            "CREATE TABLE IF NOT EXISTS activation_log (" +
            "    id BIGINT AUTO_INCREMENT PRIMARY KEY," +
            "    record_id BIGINT DEFAULT NULL COMMENT '关联记录ID'," +
            "    serial_number VARCHAR(512) DEFAULT NULL COMMENT '唯一序列号'," +
            "    device_id VARCHAR(128) DEFAULT NULL COMMENT '设备ID'," +
            "    event_type VARCHAR(32) NOT NULL COMMENT '事件类型'," +
            "    event_message VARCHAR(512) DEFAULT NULL COMMENT '事件消息'," +
            "    client_ip VARCHAR(64) DEFAULT NULL COMMENT '客户端IP'," +
            "    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'," +
            "    INDEX idx_record_id (record_id)," +
            "    INDEX idx_serial_number (serial_number)," +
            "    INDEX idx_event_type (event_type)," +
            "    INDEX idx_create_time (create_time)" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='稽核日志表'",
            "CREATE TABLE IF NOT EXISTS admin_user (" +
            "    id BIGINT AUTO_INCREMENT PRIMARY KEY," +
            "    username VARCHAR(64) NOT NULL COMMENT '用户名'," +
            "    password VARCHAR(128) NOT NULL COMMENT '密码哈希'," +
            "    salt VARCHAR(64) NOT NULL COMMENT '盐值'," +
            "    last_login_time DATETIME DEFAULT NULL COMMENT '最后登录时间'," +
            "    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'," +
            "    UNIQUE KEY uk_username (username)" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理员用户表'"
        };

        // 兼容已有数据库：如果表已存在但缺少 device_alias 列，则自动添加
        // MySQL 不支持 ADD COLUMN IF NOT EXISTS，需要先查询列是否存在
        String[][] alterChecks = {
            {"activation_record", "device_alias", "ALTER TABLE activation_record ADD COLUMN device_alias VARCHAR(128) DEFAULT NULL COMMENT '设备别名' AFTER machine_code"}
        };

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            Connection conn = DriverManager.getConnection(urlWithoutDb, username, password);
            Statement stmt = conn.createStatement();

            for (String sql : sqlStatements) {
                log.info("执行建表语句: {}", sql.substring(0, Math.min(sql.length(), 80)));
                stmt.execute(sql);
            }

            // 检查并添加缺失的列
            for (String[] alter : alterChecks) {
                String tableName = alter[0];
                String columnName = alter[1];
                String alterSql = alter[2];
                try {
                    var rs = stmt.executeQuery("SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = 'tools' AND TABLE_NAME = '" + tableName + "' AND COLUMN_NAME = '" + columnName + "'");
                    rs.next();
                    if (rs.getInt(1) == 0) {
                        log.info("执行迁移语句: {}", alterSql);
                        stmt.execute(alterSql);
                        log.info("列 {}.{} 添加成功", tableName, columnName);
                    } else {
                        log.info("列 {}.{} 已存在，跳过", tableName, columnName);
                    }
                    rs.close();
                } catch (Exception e) {
                    log.warn("迁移语句执行异常: {}", e.getMessage());
                }
            }

            // 添加唯一索引（如果不存在）
            try {
                var rs = stmt.executeQuery("SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = 'tools' AND TABLE_NAME = 'activation_record' AND INDEX_NAME = 'uk_device_alias'");
                rs.next();
                if (rs.getInt(1) == 0) {
                    stmt.execute("ALTER TABLE activation_record ADD UNIQUE INDEX uk_device_alias (device_alias)");
                    log.info("唯一索引 uk_device_alias 添加成功");
                } else {
                    log.info("唯一索引 uk_device_alias 已存在，跳过");
                }
                rs.close();
            } catch (Exception e) {
                log.warn("添加唯一索引异常: {}", e.getMessage());
            }

            stmt.close();
            conn.close();
            log.info("数据库表初始化完成");
        } catch (Exception e) {
            log.error("数据库初始化失败", e);
        }

        // 初始化默认管理员账号
        authController.initDefaultAdmin();
    }
}
