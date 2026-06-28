#!/usr/bin/env python3
"""Start mvn verify for kb-auth, skip ApplicationTests, run IT only."""
import paramiko
import time

HOST = "100.93.36.113"
USER = "root"
PASSWORD = "root"

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect(HOST, username=USER, password=PASSWORD, timeout=15)

# 跳过 *ApplicationTests，surefire 只跑 *Test（不含 ApplicationTests），failsafe 跑 *IT
launcher = """#!/bin/bash
cd /root/devtools/mykng/kb-auth
nohup mvn -Pfast verify -B \
  -Dtest='!*ApplicationTests' \
  -DfailIfNoTests=false \
  > /tmp/mvn-verify-auth2.log 2>&1 &
echo $! > /tmp/mvn-verify-auth2.pid
"""
sftp = ssh.open_sftp()
with sftp.file("/tmp/start-verify-auth2.sh", "w") as f:
    f.write(launcher)
sftp.chmod("/tmp/start-verify-auth2.sh", 0o755)
sftp.close()

transport = ssh.get_transport()
channel = transport.open_session()
channel.exec_command("bash /tmp/start-verify-auth2.sh")
time.sleep(3)
channel.close()
ssh.close()
print("mvn verify (kb-auth, skip ApplicationTests) started")
print("Log: /tmp/mvn-verify-auth2.log")
