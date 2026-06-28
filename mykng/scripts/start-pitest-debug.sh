#!/bin/bash
# 重跑 PITest with -e 标志，获取完整错误信息
cd /root/devtools/mykng/kb-auth
nohup mvn -P!fast clean test -e -B > /tmp/mvn-pitest-auth-debug.log 2>&1 &
echo $! > /tmp/mvn-pitest-auth-debug.pid
sleep 3
PID=$(cat /tmp/mvn-pitest-auth-debug.pid)
echo "PID=$PID"
ps -p "$PID" -o pid,etime,cmd
