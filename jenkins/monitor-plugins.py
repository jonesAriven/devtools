#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
持续监控 Jenkins 插件安装进度
"""
import paramiko
import time, sys

sys.stdout.reconfigure(encoding='utf-8', errors='ignore')

HOST = "100.93.36.113"
USER = "root"
PASS = "root"

def main():
    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    ssh.connect(HOST, username=USER, password=PASS, timeout=15)
    
    for round_num in range(10):  # 最多监控 10 轮（约 5 分钟）
        print(f"\n{'='*60}")
        print(f"  Monitor Round {round_num + 1} - {time.strftime('%H:%M:%S')}")
        print(f"{'='*60}")
        
        # 查看最新日志
        stdin, stdout, stderr = ssh.exec_command("""
            docker logs jenkins-ci --tail 5 2>&1 | grep -iE "(downloading|success|fail|installing|pending|error)" | tail -5
        """, timeout=15)
        out = stdout.read().decode(errors='replace').strip()
        if out:
            print(f"  Recent logs:\n{out}")
        
        # 统计已安装插件数量
        stdin, stdout, stderr = ssh.exec_command("""
            JENKINS_HOME="/var/lib/docker/volumes/jenkins_jenkins_home/_data"
            echo "Total .jpi files: $(ls $JENKINS_HOME/plugins/*.jpi 2>/dev/null | wc -l)"
            echo ""
            echo "=== Installed Plugins ==="
            ls -la $JENKINS_HOME/plugins/*.jpi 2>/dev/null | \
                awk '{print $NF, $5}' | \
                while read f size; do 
                    name=$(basename "$f" .jpi)
                    echo "  $name ($((size/1024))KB)"
                done | sort
        """, timeout=15)
        out = stdout.read().decode(errors='replace').strip()
        print(f"\n{out}")
        
        if round_num < 9:
            print("\n  Waiting 30s...")
            time.sleep(30)
    
    ssh.close()
    print("\n[DONE]")


if __name__ == "__main__":
    main()
