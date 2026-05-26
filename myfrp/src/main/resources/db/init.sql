CREATE DATABASE IF NOT EXISTS frp_manager DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE frp_manager;

CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    real_name VARCHAR(50),
    role VARCHAR(20) NOT NULL DEFAULT 'USER' COMMENT 'ADMIN / USER',
    status INT DEFAULT 1 COMMENT '0=disabled 1=enabled',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS frp_server (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    host VARCHAR(255) NOT NULL,
    bind_port INT DEFAULT 7000,
    token VARCHAR(255),
    dashboard_port INT DEFAULT 7500,
    dashboard_user VARCHAR(50),
    dashboard_pwd VARCHAR(255),
    vhost_http_port INT,
    remark VARCHAR(500),
    status INT DEFAULT 1 COMMENT '0=disabled 1=enabled',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS frp_client (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    server_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    host VARCHAR(255) NOT NULL,
    config_path VARCHAR(500),
    config_format VARCHAR(10) DEFAULT 'toml' COMMENT 'ini / toml',
    ssh_host VARCHAR(255),
    ssh_port INT DEFAULT 22,
    ssh_user VARCHAR(50),
    ssh_pwd VARCHAR(500) COMMENT 'encrypted',
    os_type VARCHAR(20) DEFAULT 'linux' COMMENT 'linux / windows',
    frpc_cmd VARCHAR(500),
    status INT DEFAULT 1 COMMENT '0=disabled 1=enabled',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (server_id) REFERENCES frp_server(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS frp_tunnel (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    client_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    type VARCHAR(10) NOT NULL COMMENT 'tcp / udp / http / https',
    local_ip VARCHAR(255) DEFAULT '127.0.0.1',
    local_port INT NOT NULL,
    remote_port INT,
    use_encryption INT DEFAULT 0,
    use_compression INT DEFAULT 0,
    status INT DEFAULT 1 COMMENT '0=disabled 1=enabled',
    remark VARCHAR(500),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (client_id) REFERENCES frp_client(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
