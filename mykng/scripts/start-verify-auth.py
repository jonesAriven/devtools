#!/usr/bin/env python3
"""Start mvn verify for kb-auth (integration tests), in background on mykng."""
import paramiko
import time

HOST = "100.93.36.113"
USER = "root"
PASSWORD = "root"

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect(HOST, username=USER, password=PASSWORD, timeout=15)

# kb-auth mvn verify（含集成测试）
launcher = """#!/bin/bash
cd /root/devtools/mykng/kb-auth
nohup mvn -Pfast verify -B > /tmp/mvn-verify-auth.log 2>&1 &
echo $! > /tmp/mvn-verify-auth.pid
"""
sftp = ssh.open_sftp()
with sftp.file("/tmp/start-verify-auth.sh", "w") as f:
    f.write(launcher)
sftp.chmod("/tmp/start-verify-auth.sh", 0o755)
sftp.close()

transport = ssh.get_transport()
channel = transport.open_session()
channel.exec_command("bash /tmp/start-verify-auth.sh")
time.sleep(3)
channel.close()
ssh.close()
print("mvn verify (kb-auth) started in background on mykng")
print("Log: /tmp/mvn-verify-auth.log")
