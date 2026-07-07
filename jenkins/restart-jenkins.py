#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
重启 Jenkins 并检查插件状态
"""
import paramiko
import time, sys

sys.stdout.reconfigure(encoding='utf-8', errors='ignore')

HOST = "100.93.36.113"
USER = "root"
PASS = "root"

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect(HOST, username=USER, password=PASS, timeout=15)

print("Restarting Jenkins...")
stdin, stdout, stderr = ssh.exec_command("docker restart jenkins-ci", timeout=30)
print(stdout.read().decode().strip())

print("\nWaiting for Jenkins to come up...")
for i in range(20):
    time.sleep(2)
    stdin, stdout, stderr = ssh.exec_command(
        "curl -sf http://localhost:8097/login 2>/dev/null && echo 'OK' || echo 'WAITING'",
        timeout=10
    )
    result = stdout.read().decode(errors='replace').strip()
    if "OK" in result:
        print(f"  [OK] Jenkins is up! ({i*2}s)")
        break
    print(f"  ... ({i*2}s)")

# 检查最终插件列表
print("\nInstalled plugins after restart:")
stdin, stdout, stderr = ssh.exec_command("""
    JENKINS_HOME="/var/lib/docker/volumes/jenkins_jenkins_home/_data"
    echo "Total: $(ls $JENKINS_HOME/plugins/*.jpi 2>/dev/null | wc -l)"
    echo ""
    ls -la $JENKINS_HOME/plugins/*.jpi 2>/dev/null | \
        awk '{print $NF, $5}' | \
        while read f size; do 
            name=$(basename "$f" .jpi)
            echo "  $name ($((size/1024))KB)"
        done | sort
""", timeout=15)
print(stdout.read().decode(errors='replace'))

ssh.close()
print("\n[DONE]")
