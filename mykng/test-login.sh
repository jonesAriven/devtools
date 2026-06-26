#!/bin/bash
# 测试登录
curl -s -X POST http://localhost:8090/kb/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}'
echo ""
