#!/bin/bash
# 更新管理员密码
docker exec kb-mysql mysql -uroot -pkb123456 -e "
USE kb_auth;
UPDATE user SET password='\$2y\$10\$ilbd2H0v1imqFL5VYPUYBeE3BAppLcROlSF5bF17hrbmAuiwodgDm' WHERE username='admin';
SELECT id, username, LEFT(password,30) as pwd_prefix FROM user;
" 2>&1

echo "=== 测试登录 ==="
curl -s -X POST http://localhost:8090/kb/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}'
echo ""
