#!/bin/bash
# 执行所有init-sql脚本
echo "=== 执行数据库初始化脚本 ==="

# 创建4个数据库
docker exec kb-mysql mysql -uroot -pkb123456 -e "
CREATE DATABASE IF NOT EXISTS kb_auth DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS kb_file DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS kb_knowledge DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS kb_ops DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
" 2>&1
echo "数据库创建完成"

# 执行init-sql目录下的所有SQL文件
for sql_file in /root/kb-deploy/init-sql/*.sql; do
    echo "执行: $sql_file"
    docker exec -i kb-mysql mysql -uroot -pkb123456 < "$sql_file" 2>&1
    if [ $? -eq 0 ]; then
        echo "  成功: $sql_file"
    else
        echo "  失败: $sql_file"
    fi
done

# 验证表创建情况
echo "=== 验证表 ==="
for db in kb_auth kb_file kb_knowledge kb_ops; do
    echo "--- $db ---"
    docker exec kb-mysql mysql -uroot -pkb123456 -e "USE $db; SHOW TABLES;" 2>&1
done

# 创建默认管理员用户（如果不存在）
echo "=== 创建默认管理员 ==="
docker exec kb-mysql mysql -uroot -pkb123456 -e "
USE kb_auth;
INSERT IGNORE INTO user (id, username, password, phone, status, created_at, updated_at)
VALUES (1, 'admin', '\$2a\$10\$N.ZMy8s5L3NjQzjvF6YnHeSRJaQgSPzGe5O8C8v1b3a3b3b3b3b3', NULL, 1, NOW(), NOW());
" 2>&1
echo "管理员创建完成"

echo "=== 初始化完成 ==="
