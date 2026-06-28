#!/bin/bash
# 重新启动 kb-auth PITest 变异测试（surefire 已确认稳定通过，本次执行 PITest）
cd /root/devtools/mykng/kb-auth
nohup mvn -P!fast clean verify -B > /tmp/mvn-pitest-auth-final.log 2>&1 &
echo $! > /tmp/mvn-pitest-auth-final.pid
sleep 3
PID=$(cat /tmp/mvn-pitest-auth-final.pid)
echo "PID=$PID"
ps -p "$PID" -o pid,etime,cmd
