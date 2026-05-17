import mysql.connector
from mysql.connector import errorcode

try:
    print("Connecting to 192.168.31.182:3306...")
    conn = mysql.connector.connect(
        host='192.168.31.182',
        port=3306,
        user='tools',
        password='toolsmarschat'
    )
    
    print("Connected successfully!")
    
    cursor = conn.cursor()
    
    print("Creating database tools if not exists...")
    cursor.execute("CREATE DATABASE IF NOT EXISTS tools DEFAULT CHARACTER SET utf8mb4 DEFAULT COLLATE utf8mb4_unicode_ci")
    
    print("Using database tools...")
    cursor.execute("USE tools")
    
    print("Creating activation_record table...")
    create_table_sql = """
    CREATE TABLE IF NOT EXISTS activation_record (
        id BIGINT AUTO_INCREMENT PRIMARY KEY,
        serial_number VARCHAR(512) NOT NULL COMMENT '唯一序列号',
        activation_code TEXT NOT NULL COMMENT '激活码',
        expire_time BIGINT NOT NULL COMMENT '过期时间戳(毫秒)',
        create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
        update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
        UNIQUE KEY uk_serial_number (serial_number)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='激活码记录表'
    """
    cursor.execute(create_table_sql)
    
    print("Verifying table...")
    cursor.execute("SHOW TABLES")
    tables = cursor.fetchall()
    print(f"Tables in tools database: {tables}")
    
    cursor.close()
    conn.close()
    
    print("\nDatabase initialization completed successfully!")
    
except mysql.connector.Error as err:
    if err.errno == errorcode.ER_ACCESS_DENIED_ERROR:
        print("Error: Access denied - check username/password")
    elif err.errno == errorcode.ER_BAD_DB_ERROR:
        print("Error: Database does not exist")
    else:
        print(f"Error: {err}")
except Exception as e:
    print(f"Unexpected error: {e}")
