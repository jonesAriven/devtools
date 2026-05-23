import pymysql

conn = pymysql.connect(
    host='192.168.31.182',
    port=3306,
    user='tools',
    password='toolsmarschat',
    database='tools',
    charset='utf8mb4'
)
cursor = conn.cursor()

cursor.execute('DESCRIBE activation_record')
desc = cursor.fetchall()
columns = [row[0] for row in desc]
print('Current columns:', columns)

if 'device_id' not in columns:
    alter_sql = "ALTER TABLE activation_record ADD COLUMN device_id VARCHAR(128) DEFAULT '' COMMENT '绑定的设备ID' AFTER serial_number"
    cursor.execute(alter_sql)
    conn.commit()
    print('Added device_id column')
else:
    print('device_id column already exists')

cursor.execute('DESCRIBE activation_record')
desc = cursor.fetchall()
for row in desc:
    print(row)

cursor.close()
conn.close()
print('Done!')
