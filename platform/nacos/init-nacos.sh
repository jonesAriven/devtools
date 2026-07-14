#!/bin/bash
# ============================================================
# init-nacos.sh — Nacos 默认用户自动初始化
# ============================================================
# 问题: Nacos v2.4.3 standalone 模式不会自动创建默认用户
#       derby-schema.sql 只建表不插数据，users 表为空导致无法登录
#
# 本脚本在 Nacos 启动前执行:
#   1. 首次启动(Derby 不存在) → 跳过，让 Nacos 自己建 schema
#   2. users 表已有数据 → 跳过
#   3. users 表为空 → 插入 nacos/nacos 默认用户
#
# 幂等: 通过标记文件 + SELECT 双重检查
# ============================================================
set -e

DERBY_PATH="/home/nacos/data/derby-data"
INIT_FLAG="/home/nacos/data/.nacos-user-initialized"

# 如果标记文件已存在，说明之前已初始化过，直接退出
if [ -f "$INIT_FLAG" ]; then
  echo "[INIT] Nacos 用户已初始化过，跳过"
  exit 0
fi

# 如果 Derby 数据库不存在（首次启动），让 Nacos 先自己创建 schema
if [ ! -d "$DERBY_PATH" ]; then
  echo "[INIT] Derby 数据库不存在（首次启动），Nacos 将自动创建 schema"
  echo "[INIT] Nacos 启动后重新执行本容器即可初始化用户"
  exit 0
fi

# 提取 Derby 工具
echo "[INIT] 提取 Derby 工具..."
cd /home/nacos
unzip -o -q target/nacos-server.jar BOOT-INF/lib/derby-10.14.2.0.jar -d /tmp/
wget -q -O /tmp/derbytools.jar https://repo1.maven.org/maven2/org/apache/derby/derbytools/10.14.2.0/derbytools-10.14.2.0.jar

# 检查 users 表是否已有数据
echo "[INIT] 检查 users 表..."
cat > /tmp/check_users.sql << 'SQLEOF'
CONNECT 'jdbc:derby:/home/nacos/data/derby-data;user=nacos;password=nacos';
SELECT COUNT(*) AS cnt FROM users;
SQLEOF

RESULT=$(java -Dij.driver=org.apache.derby.jdbc.EmbeddedDriver \
  -cp /tmp/derbytools.jar:/tmp/BOOT-INF/lib/derby-10.14.2.0.jar \
  org.apache.derby.tools.ij /tmp/check_users.sql 2>&1)

echo "$RESULT"

# users 表已有数据则跳过
if echo "$RESULT" | grep -qE 'cnt' && echo "$RESULT" | grep -qE '[1-9]'; then
  echo "[INIT] users 表已有数据，跳过初始化"
  touch "$INIT_FLAG"
  exit 0
fi

# 插入默认用户 nacos/nacos
echo "[INIT] 插入默认用户 nacos/nacos..."
cat > /tmp/insert_user.sql << 'SQLEOF'
CONNECT 'jdbc:derby:/home/nacos/data/derby-data;user=nacos;password=nacos';
INSERT INTO users (username, password, enabled) VALUES ('nacos', '$2a$10$EuWPZHzz32dJN7jexM34MOeYirDdFAZm2kuWj7VEOJhhZkDrxfvUu', true);
INSERT INTO roles (username, role) VALUES ('nacos', 'ROLE_ADMIN');
SELECT username, enabled FROM users;
SQLEOF

java -Dij.driver=org.apache.derby.jdbc.EmbeddedDriver \
  -cp /tmp/derbytools.jar:/tmp/BOOT-INF/lib/derby-10.14.2.0.jar \
  org.apache.derby.tools.ij /tmp/insert_user.sql

touch "$INIT_FLAG"
echo "[INIT] Nacos 用户初始化完成！"
