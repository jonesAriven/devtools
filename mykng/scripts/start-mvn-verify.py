#!/usr/bin/env python3
"""Start mvn verify in background on mykng, return immediately."""
import paramiko
import time

HOST = "100.93.36.113"
USER = "root"
PASSWORD = "root"

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect(HOST, username=USER, password=PASSWORD, timeout=15)

# Start mvn in background using a detached screen session
# Write a launcher script first, then execute it
launcher = """#!/bin/bash
cd /root/devtools/mykng/kb-parent
nohup mvn -Pfast verify -B -DskipTests > /tmp/mvn-verify.log 2>&1 &
echo $! > /tmp/mvn-verify.pid
"""
sftp = ssh.open_sftp()
with sftp.file("/tmp/start-mvn.sh", "w") as f:
    f.write(launcher)
sftp.chmod("/tmp/start-mvn.sh", 0o755)
sftp.close()

# Execute launcher and return immediately (don't read stdout fully)
transport = ssh.get_transport()
channel = transport.open_session()
channel.exec_command("bash /tmp/start-mvn.sh")
time.sleep(3)  # Wait for mvn to start
channel.close()
ssh.close()
print("mvn verify started in background on mykng")
print("Log: /tmp/mvn-verify.log")
print("PID: /tmp/mvn-verify.pid")
