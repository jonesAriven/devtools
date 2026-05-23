import pymysql

conn = pymysql.connect(
    host='192.168.31.182', port=3306,
    user='tools', password='toolsmarschat',
    database='tools', charset='utf8mb4'
)
cursor = conn.cursor()

alter_sqls = [
    "ALTER TABLE activation_record ADD COLUMN activated_time DATETIME DEFAULT NULL COMMENT 'first activate time' AFTER expire_time",
    "ALTER TABLE activation_record ADD COLUMN expire_minutes INT DEFAULT NULL COMMENT 'expire minutes' AFTER activated_time",
    "ALTER TABLE activation_record ADD COLUMN initial_serial VARCHAR(256) DEFAULT NULL COMMENT 'initial serial' AFTER expire_minutes",
    "ALTER TABLE activation_record ADD COLUMN machine_code VARCHAR(256) DEFAULT NULL COMMENT 'machine code' AFTER initial_serial",
]

for sql in alter_sqls:
    try:
        cursor.execute(sql)
        print("OK: " + sql[:70])
    except Exception as e:
        if "Duplicate column name" in str(e):
            print("SKIP (exists): " + sql[:70])
        else:
            print("ERR: " + str(e)[:80])

create_log_sql = """
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
"""
cursor.execute(create_log_sql)
print("OK: activation_log table created")

conn.commit()
cursor.close()
conn.close()
print("Done!")
