#!/usr/bin/env python3
"""Start mvn verify for kb-auth, no -Dtest param."""
import paramiko
import time

HOST = "100.93.36.113"
USER = "root"
PASSWORD = "root"

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect(HOST, username=USER, password=PASSWORD, timeout=15)

launcher = """#!/bin/bash
cd /root/devtools/mykng/kb-auth
nohup mvn -Pfast verify -B > /tmp/mvn-verify-auth3.log 2>&1 &
echo $! > /tmp/mvn-verify-auth3.pid
"""
sftp = ssh.open_sftp()
with sftp.file("/tmp/start-verify-auth3.sh", "w") as f:
    f.write(launcher)
sftp.chmod("/tmp/start-verify-auth3.sh", 0o755)
sftp.close()

transport = ssh.get_transport()
channel = transport.open_session()
channel.exec_command("bash /tmp/start-verify-auth3.sh")
time.sleep(3)
channel.close()
ssh.close()
print("mvn verify (kb-auth, no -Dtest) started")
print("Log: /tmp/mvn-verify-auth3.log")
