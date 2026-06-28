#!/usr/bin/env python3
"""Start mvn test in background on mykng, return immediately."""
import paramiko
import time

HOST = "100.93.36.113"
USER = "root"
PASSWORD = "root"

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect(HOST, username=USER, password=PASSWORD, timeout=15)

# 后台启动 mvn test，日志写到 /tmp/mvn-test.log
launcher = """#!/bin/bash
cd /root/devtools/mykng/kb-knowledge
nohup mvn -Pfast test -B > /tmp/mvn-test.log 2>&1 &
echo $! > /tmp/mvn-test.pid
"""
sftp = ssh.open_sftp()
with sftp.file("/tmp/start-test.sh", "w") as f:
    f.write(launcher)
sftp.chmod("/tmp/start-test.sh", 0o755)
sftp.close()

transport = ssh.get_transport()
channel = transport.open_session()
channel.exec_command("bash /tmp/start-test.sh")
time.sleep(3)
channel.close()
ssh.close()
print("mvn test started in background on mykng")
print("Log: /tmp/mvn-test.log")
print("PID: /tmp/mvn-test.pid")
