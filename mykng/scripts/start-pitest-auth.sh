#!/bin/bash
# 启动 kb-auth PITest 变异测试（不激活 fast profile，启用 PITest）
cd /root/devtools/mykng/kb-auth
nohup mvn -P!fast clean verify -B > /tmp/mvn-pitest-auth.log 2>&1 &
echo $! > /tmp/mvn-pitest-auth.pid
sleep 3
PID=$(cat /tmp/mvn-pitest-auth.pid)
echo "PID=$PID"
ps -p "$PID" -o pid,etime,cmd
