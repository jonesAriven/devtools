CREATE DATABASE IF NOT EXISTS tools
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE tools;

CREATE TABLE IF NOT EXISTS activation_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    serial_number VARCHAR(512) NOT NULL,
    device_id VARCHAR(128) DEFAULT '',
    activation_code TEXT NOT NULL,
    expire_time BIGINT NOT NULL,
    activated_time DATETIME DEFAULT NULL,
    expire_minutes INT DEFAULT NULL,
    initial_serial VARCHAR(256) DEFAULT NULL,
    machine_code VARCHAR(256) DEFAULT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_serial_number (serial_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS activation_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    record_id BIGINT DEFAULT NULL,
    serial_number VARCHAR(512) DEFAULT NULL,
    device_id VARCHAR(128) DEFAULT NULL,
    event_type VARCHAR(32) NOT NULL,
    event_message VARCHAR(512) DEFAULT NULL,
    client_ip VARCHAR(64) DEFAULT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_record_id (record_id),
    INDEX idx_serial_number (serial_number),
    INDEX idx_event_type (event_type),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sys_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    config_key VARCHAR(128) NOT NULL UNIQUE,
    config_value TEXT DEFAULT NULL,
    config_group VARCHAR(64) DEFAULT 'general',
    remark VARCHAR(256) DEFAULT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
