-- ============================================================
-- kb_intelligence 数据库初始化脚本
-- 微服务: kb-intelligence (知识导入与双维度渲染引擎)
-- ============================================================

CREATE DATABASE IF NOT EXISTS kb_intelligence CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE kb_intelligence;

-- 知识文档主表
CREATE TABLE IF NOT EXISTS kn_doc (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    source_id VARCHAR(255) COMMENT '来源标识',
    title VARCHAR(500) NOT NULL COMMENT '文档标题',
    file_path VARCHAR(1000) NOT NULL COMMENT '文件路径',
    doc_type VARCHAR(32) COMMENT '文档类型(TABLE/PLAN/TIMELINE/GRAPH/RULE)',
    category VARCHAR(100) COMMENT '分类',
    tags VARCHAR(1000) COMMENT '标签(逗号分隔)',
    summary VARCHAR(1000) COMMENT '摘要',
    content_hash VARCHAR(64) COMMENT '内容SHA256哈希(增量更新)',
    entity_count INT DEFAULT 0 COMMENT '实体数量',
    command_count INT DEFAULT 0 COMMENT '命令数量',
    section_count INT DEFAULT 0 COMMENT '章节数量',
    word_count INT DEFAULT 0 COMMENT '字数',
    status INT DEFAULT 1 COMMENT '状态',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0,
    INDEX idx_file_path (file_path(255)),
    INDEX idx_doc_type (doc_type),
    INDEX idx_category (category),
    INDEX idx_content_hash (content_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识文档主表';

-- 主机表
CREATE TABLE IF NOT EXISTS kn_host (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(200) COMMENT '主机名称',
    ip VARCHAR(50) COMMENT 'IP地址',
    tailscale_ip VARCHAR(50) COMMENT 'Tailscale IP',
    public_ip VARCHAR(50) COMMENT '公网IP',
    ssh_port INT DEFAULT 22 COMMENT 'SSH端口',
    username VARCHAR(100) COMMENT '用户名',
    password_encrypted VARCHAR(500) COMMENT '加密密码',
    os_type VARCHAR(50) COMMENT '操作系统类型',
    os_version VARCHAR(100) COMMENT '系统版本',
    cpu_arch VARCHAR(30) COMMENT 'CPU架构',
    cpu_cores INT COMMENT 'CPU核数',
    memory_gb BIGINT COMMENT '内存GB',
    role VARCHAR(100) COMMENT '角色',
    environment VARCHAR(50) COMMENT '环境',
    location VARCHAR(200) COMMENT '位置',
    status VARCHAR(30) DEFAULT 'running' COMMENT '状态',
    tags VARCHAR(500) COMMENT '标签',
    remark VARCHAR(1000) COMMENT '备注',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0,
    UNIQUE INDEX uk_ip (ip),
    INDEX idx_tailscale_ip (tailscale_ip),
    INDEX idx_status (status),
    INDEX idx_role (role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='主机表';

-- 服务表
CREATE TABLE IF NOT EXISTS kn_service (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    host_id BIGINT COMMENT '所属主机ID',
    name VARCHAR(200) NOT NULL COMMENT '服务名称',
    service_type VARCHAR(100) COMMENT '服务类型',
    version VARCHAR(100) COMMENT '版本',
    install_path VARCHAR(500) COMMENT '安装路径',
    config_path VARCHAR(500) COMMENT '配置路径',
    log_path VARCHAR(500) COMMENT '日志路径',
    status VARCHAR(30) DEFAULT 'running' COMMENT '状态',
    tags VARCHAR(500) COMMENT '标签',
    remark VARCHAR(1000) COMMENT '备注',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0,
    INDEX idx_host_id (host_id),
    INDEX idx_name (name),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='服务表';

-- 端口表
CREATE TABLE IF NOT EXISTS kn_port (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    host_id BIGINT COMMENT '所属主机ID',
    service_id BIGINT COMMENT '关联服务ID',
    port INT NOT NULL COMMENT '端口号',
    protocol VARCHAR(20) DEFAULT 'tcp' COMMENT '协议',
    mapping VARCHAR(200) COMMENT '端口映射',
    access_url VARCHAR(500) COMMENT '访问URL',
    exposed INT DEFAULT 0 COMMENT '是否对外暴露',
    remark VARCHAR(500) COMMENT '备注',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0,
    INDEX idx_host_id (host_id),
    INDEX idx_service_id (service_id),
    INDEX idx_port (port)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='端口表';

-- 凭据表
CREATE TABLE IF NOT EXISTS kn_credential (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    host_id BIGINT COMMENT '关联主机ID',
    service_id BIGINT COMMENT '关联服务ID',
    cred_type VARCHAR(50) COMMENT '凭据类型',
    username VARCHAR(200) COMMENT '用户名',
    password_encrypted VARCHAR(500) COMMENT '加密密码',
    access_key VARCHAR(500) COMMENT 'AccessKey',
    secret_key_encrypted VARCHAR(500) COMMENT '加密SecretKey',
    token VARCHAR(1000) COMMENT 'Token',
    remark VARCHAR(500) COMMENT '备注',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0,
    INDEX idx_host_id (host_id),
    INDEX idx_service_id (service_id),
    INDEX idx_cred_type (cred_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='凭据表';

-- 域名表
CREATE TABLE IF NOT EXISTS kn_domain (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    domain VARCHAR(255) NOT NULL COMMENT '域名',
    sub_domain VARCHAR(255) COMMENT '子域名',
    target_host_id BIGINT COMMENT '目标主机ID',
    target_port INT COMMENT '目标端口',
    target_service VARCHAR(200) COMMENT '目标服务',
    dns_type VARCHAR(20) COMMENT 'DNS记录类型',
    status VARCHAR(30) DEFAULT 'active' COMMENT '状态',
    remark VARCHAR(500) COMMENT '备注',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0,
    INDEX idx_domain (domain),
    INDEX idx_target_host_id (target_host_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='域名表';

-- 依赖关系表
CREATE TABLE IF NOT EXISTS kn_dependency (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    from_type VARCHAR(30) COMMENT '源实体类型',
    from_id BIGINT COMMENT '源实体ID',
    to_type VARCHAR(30) COMMENT '目标实体类型',
    to_id BIGINT COMMENT '目标实体ID',
    dep_type VARCHAR(50) COMMENT '依赖类型',
    protocol VARCHAR(30) COMMENT '协议',
    port INT COMMENT '端口',
    remark VARCHAR(500) COMMENT '备注',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0,
    INDEX idx_from (from_type, from_id),
    INDEX idx_to (to_type, to_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='依赖关系表';

-- 命令表
CREATE TABLE IF NOT EXISTS kn_command (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    doc_id BIGINT COMMENT '来源文档ID',
    command TEXT NOT NULL COMMENT '命令文本',
    description VARCHAR(1000) COMMENT '命令描述',
    category VARCHAR(50) COMMENT '分类',
    risk_level VARCHAR(20) DEFAULT 'low' COMMENT '风险等级',
    os_type VARCHAR(20) DEFAULT 'linux' COMMENT '操作系统',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0,
    INDEX idx_doc_id (doc_id),
    INDEX idx_category (category),
    INDEX idx_risk_level (risk_level)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='命令表';

-- 时间线事件表
CREATE TABLE IF NOT EXISTS kn_timeline (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    doc_id BIGINT COMMENT '来源文档ID',
    event_time VARCHAR(50) COMMENT '事件时间',
    event_type VARCHAR(50) COMMENT '事件类型',
    title VARCHAR(500) NOT NULL COMMENT '事件标题',
    description TEXT COMMENT '事件描述',
    severity VARCHAR(20) DEFAULT 'low' COMMENT '严重程度',
    status VARCHAR(30) COMMENT '状态',
    solution TEXT COMMENT '解决方案',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0,
    INDEX idx_doc_id (doc_id),
    INDEX idx_event_time (event_time),
    INDEX idx_severity (severity),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='时间线事件表';

-- 文档-实体关联表
CREATE TABLE IF NOT EXISTS kn_doc_entity_ref (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    doc_id BIGINT NOT NULL COMMENT '文档ID',
    entity_type VARCHAR(30) NOT NULL COMMENT '实体类型',
    entity_id BIGINT NOT NULL COMMENT '实体ID',
    source_section VARCHAR(500) COMMENT '来源章节',
    confidence INT DEFAULT 100 COMMENT '置信度',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0,
    INDEX idx_doc_id (doc_id),
    INDEX idx_entity (entity_type, entity_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文档-实体关联表';
