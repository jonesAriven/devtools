-- ============================================================
-- 运维知识模块建表脚本（v5）
-- 在已有 knowledge_base 数据库中执行
-- ============================================================

-- 1. 主机清单
CREATE TABLE IF NOT EXISTS ops_host (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(100) NOT NULL COMMENT '主机名',
    ip              VARCHAR(45) COMMENT '公网 IP',
    tailscale_ip    VARCHAR(45) COMMENT 'Tailscale IP',
    internal_ip     VARCHAR(45) COMMENT '内网 IP',
    os              VARCHAR(50) COMMENT '操作系统',
    ssh_user        VARCHAR(50) COMMENT '登录用户',
    location        VARCHAR(50) COMMENT '位置（腾讯云/阿里云/内网）',
    status          TINYINT DEFAULT 1 COMMENT '1=在线 0=离线',
    remark          TEXT COMMENT '备注',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_location (location),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='主机清单';

-- 2. 端口映射
CREATE TABLE IF NOT EXISTS ops_port (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    host_id         BIGINT NOT NULL COMMENT '关联主机 ID',
    port            INT NOT NULL COMMENT '端口号',
    service         VARCHAR(100) COMMENT '服务名',
    protocol        VARCHAR(10) DEFAULT 'TCP' COMMENT 'TCP/UDP',
    access_type     VARCHAR(20) COMMENT '对外方式（FRP/直连/仅内网）',
    remote_port     INT COMMENT 'FRP 远程端口',
    status          TINYINT DEFAULT 1 COMMENT '1=在线 0=离线',
    remark          TEXT COMMENT '备注',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_host_id (host_id),
    INDEX idx_port (port),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='端口映射';

-- 3. 账密信息
CREATE TABLE IF NOT EXISTS ops_credential (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    host_id             BIGINT COMMENT '关联主机 ID',
    service             VARCHAR(100) NOT NULL COMMENT '服务名',
    username            VARCHAR(100) COMMENT '用户名',
    password_encrypted  VARCHAR(512) NOT NULL COMMENT 'AES-256-GCM 加密密码',
    password_nonce      VARCHAR(32) NOT NULL COMMENT 'AES-GCM 初始向量（hex）',
    access_url          VARCHAR(255) COMMENT '访问地址',
    credential_type     VARCHAR(30) COMMENT '类型（ssh/mysql/redis/web/other）',
    remark              TEXT COMMENT '备注',
    created_at          DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_host_id (host_id),
    INDEX idx_type (credential_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='账密信息';

-- 4. 域名映射
CREATE TABLE IF NOT EXISTS ops_domain (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    domain          VARCHAR(255) NOT NULL COMMENT '域名',
    resolve_to      VARCHAR(45) COMMENT '解析到 IP',
    target_host_id  BIGINT COMMENT '目标主机 ID',
    target_port     INT COMMENT '目标端口',
    ssl_status      TINYINT DEFAULT 0 COMMENT '1=有SSL 0=无',
    remark          TEXT COMMENT '备注',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_domain (domain)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='域名映射';

-- 5. 应用依赖关系
CREATE TABLE IF NOT EXISTS ops_dependency (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    source          VARCHAR(100) NOT NULL COMMENT '源服务',
    source_host_id  BIGINT COMMENT '源主机 ID',
    target          VARCHAR(100) NOT NULL COMMENT '目标服务',
    target_host_id  BIGINT COMMENT '目标主机 ID',
    dep_type        VARCHAR(20) COMMENT '依赖类型（FRP/反代/直连）',
    protocol        VARCHAR(20) COMMENT '协议',
    port            INT COMMENT '端口',
    remark          TEXT COMMENT '备注',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_source (source),
    INDEX idx_target (target)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='应用依赖关系';

-- 6. 变更记录
CREATE TABLE IF NOT EXISTS ops_change_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    change_type     VARCHAR(20) NOT NULL COMMENT '类型（host/port/credential/domain/dependency/config）',
    source          VARCHAR(100) COMMENT '变更来源',
    detail          TEXT COMMENT '变更详情（JSON）',
    snapshot_id     BIGINT COMMENT '关联快照 ID',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_type (change_type),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='变更记录';

-- 7. 矛盾提醒
CREATE TABLE IF NOT EXISTS ops_conflict (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    conflict_type   VARCHAR(20) NOT NULL COMMENT '矛盾类型（port_conflict/credential_mismatch/dns_mismatch）',
    severity        TINYINT DEFAULT 3 COMMENT '严重程度 1-5',
    description     TEXT NOT NULL COMMENT '矛盾描述',
    detail_json     TEXT COMMENT '矛盾详情（JSON）',
    status          TINYINT DEFAULT 0 COMMENT '0=未处理 1=已解决 2=忽略',
    resolved_at     DATETIME COMMENT '解决时间',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_status (status),
    INDEX idx_type (conflict_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='矛盾提醒';

-- 8. 历史快照
CREATE TABLE IF NOT EXISTS ops_snapshot (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    snapshot_date   DATE NOT NULL COMMENT '快照日期',
    file_count      INT DEFAULT 0 COMMENT '文件数',
    host_count      INT DEFAULT 0 COMMENT '主机数',
    port_count      INT DEFAULT 0 COMMENT '端口数',
    credential_count INT DEFAULT 0 COMMENT '账密数',
    domain_count    INT DEFAULT 0 COMMENT '域名数',
    change_count    INT DEFAULT 0 COMMENT '变更数',
    conflict_count  INT DEFAULT 0 COMMENT '矛盾数',
    verify_pass     INT DEFAULT 0 COMMENT '验证通过数',
    verify_fail     INT DEFAULT 0 COMMENT '验证失败数',
    topology_image  VARCHAR(255) COMMENT '架构图 MinIO 路径',
    summary         TEXT COMMENT '快照摘要',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_snapshot_date (snapshot_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='历史快照';

-- 9. API 接入令牌（v5 新增）
CREATE TABLE IF NOT EXISTS ops_api_token (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(100) NOT NULL COMMENT '令牌名称',
    token_hash      VARCHAR(64) NOT NULL COMMENT 'SHA-256 令牌哈希',
    token_prefix    VARCHAR(8) NOT NULL COMMENT '令牌前缀（如 kb_abc）',
    scopes          VARCHAR(255) NOT NULL COMMENT '权限范围（逗号分隔）',
    status          TINYINT DEFAULT 1 COMMENT '1=启用 0=禁用',
    last_used_at    DATETIME COMMENT '最后使用时间',
    expire_at       DATETIME COMMENT '过期时间',
    created_by      BIGINT COMMENT '创建者用户 ID',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='API 接入令牌';
