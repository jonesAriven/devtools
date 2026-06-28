#!/usr/bin/env python3
"""Start mvn test for failed tests only, in background on mykng."""
import paramiko
import time

HOST = "100.93.36.113"
USER = "root"
PASSWORD = "root"

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect(HOST, username=USER, password=PASSWORD, timeout=15)

# 只跑失败的测试类，先 clean 确保编译最新代码
launcher = """#!/bin/bash
cd /root/devtools/mykng/kb-knowledge
nohup mvn -Pfast clean test -B -Dtest='DocServiceImplTest,FolderServiceImplTest' -DfailIfNoTests=false > /tmp/mvn-test-failed.log 2>&1 &
echo $! > /tmp/mvn-test-failed.pid
"""
sftp = ssh.open_sftp()
with sftp.file("/tmp/start-test-failed.sh", "w") as f:
    f.write(launcher)
sftp.chmod("/tmp/start-test-failed.sh", 0o755)
sftp.close()

transport = ssh.get_transport()
channel = transport.open_session()
channel.exec_command("bash /tmp/start-test-failed.sh")
time.sleep(3)
channel.close()
ssh.close()
print("mvn test (failed only) started in background on mykng")
print("Log: /tmp/mvn-test-failed.log")
