#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
通过 SSH 连接到 mykng 服务器，配置 Jenkins 插件国内镜像源
并重启 Jenkins 让插件安装继续
"""
import paramiko
import sys, time

sys.stdout.reconfigure(encoding='utf-8', errors='ignore')

# mykng Tailscale IP
HOST = "100.93.36.113"
USER = "root"
PASS = "root"

def ssh_cmd(ssh, cmd):
    """执行 SSH 命令并返回输出"""
    stdin, stdout, stderr = ssh.exec_command(cmd, timeout=30)
    out = stdout.read().decode(errors='replace').strip()
    err = stderr.read().decode(errors='replace').strip()
    return out, err

def main():
    print("=" * 60)
    print("  Jenkins Plugin Mirror Fix")
    print(f"  Target: {HOST}")
    print("=" * 60)
    
    # 1. SSH 连接
    print("\n[1] Connecting to mykng...")
    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    ssh.connect(HOST, username=USER, password=PASS, timeout=15)
    print("  [OK] Connected!")
    
    # 2. 检查当前 Jenkins 插件下载状态
    print("\n[2] Checking current plugin download status...")
    
    # 查看是否有正在运行的下载任务
    out, err = ssh_cmd(ssh, """
        # 查看 Jenkins 容器日志中最近的下载信息
        docker logs jenkins-ci --tail 20 2>&1 | grep -iE "(downloading|success|fail|installing|pending)" | tail -10
    """)
    if out:
        print(f"  Jenkins logs:\n{out}")
    
    # 3. 检查默认的 update-center 配置
    print("\n[3] Checking current update site config...")
    out, err = ssh_cmd(ssh, """
        # Jenkins 默认配置位置
        cat /var/jenkins_home/hudson.model.UpdateCenter.xml 2>/dev/null || \
        docker exec jenkins-ci cat /var/jenkins_home/hudson.model.UpdateCenter.xml 2>/dev/null || \
        echo "FILE_NOT_FOUND"
    """)
    print(f"  Current config:\n{out[:500]}")
    
    # 4. 使用华为云/阿里云镜像（更稳定）
    # 华为云镜像: https://mirrors.huaweicloud.com/jenkins/updates/update-center.json
    # 或者直接用腾讯云内网加速
    
    print("\n[4] Configuring mirror...")
    
    # 方案1: 通过 Jenkins CLI 设置（如果可用）
    # 方案2: 直接修改配置文件
    # 方案3: 在 Docker 容器启动时设置环境变量 JAVA_OPTS=-Dhudson.model.DownloadService.noSignatureCheck=true
    
    # 最可靠的方式: 修改 hudson.model.UpdateCenter.xml
    setup_script = '''
#!/bin/bash
set -e

JENKINS_HOME=$(docker inspect jenkins-ci --format '{{range .Mounts}}{{.Source}} -> {{.Destination}}{{println}}{{end}}' 2>/dev/null | grep jenkins_home | awk '{print $1}' | head -1)

if [ -z "$JENKINS_HOME" ]; then
    # 尝试从容器内获取
    JENKINS_HOME=$(docker exec jenkins-ci printenv JENKINS_HOME 2>/dev/null)
fi

echo "JENKINS_HOME=$JENKINS_HOME"

# 备份原文件
if [ -f "$JENKINS_HOME/hudson.model.UpdateCenter.xml" ]; then
    cp "$JENKINS_HOME/hudson.model.UpdateCenter.xml" "${JENKINS_HOME}/hudson.model.UpdateCenter.xml.bak"
    echo "Backup created"
else
    echo "Config file not found at $JENKINS_HOME"
    # 列出目录内容
    ls -la "$JENKINS_HOME/"*.xml 2>/dev/null || ls -la "$JENKINS_HOME/" 2>/dev/null | head -20
fi

# 写入新的 update site 配置（使用华为云镜像）
cat > "$JENKINS_HOME/hudson.model.UpdateCenter.xml" << 'XMLEOF'
<?xml version='1.1' encoding='UTF-8'?>
<sites>
  <site>
    <id>default</id>
    <url>https://mirrors.huaweicloud.com/jenkins/updates/update-center.json</url>
  </site>
</sites>
XMLEOF

echo "New config written:"
cat "$JENKINS_HOME/hudson.model.UpdateCenter.xml"

# 重载配置不需要重启，但需要触发重新检查
# 先尝试安全重启
echo ""
echo "Restarting Jenkins..."
docker restart jenkins-ci
echo "Jenkins restarted!"
'''
    
    stdin, stdout, stderr = ssh.exec_command(setup_script, timeout=60)
    print(stdout.read().decode(errors='replace'))
    err_read = stderr.read().decode(errors='replace')
    if err_read:
        print(f"  STDERR: {err_read[:300]}")
    
    # 5. 等待 Jenkins 重启完成
    print("\n[5] Waiting for Jenkins to come back up...")
    for i in range(30):  # 最多等 60 秒
        time.sleep(2)
        out, _ = ssh_cmd(ssh, "curl -sf http://localhost:8097/login 2>/dev/null && echo 'OK' || echo 'WAITING'")
        if "OK" in out:
            print(f"  [OK] Jenkins is back up! (took {i*2}s)")
            break
        print(f"  ... waiting ({i*2}s)")
    else:
        print("  [WARN] Jenkins may still be starting...")
    
    # 6. 检查已安装的插件
    print("\n[6] Checking installed plugins...")
    out, _ = ssh_cmd(ssh, """
        docker exec jenkins-cli ls /var/jenkins_home/plugins/*.jpi 2>/dev/null | wc -l
        echo "---"
        docker exec jenkins-cli ls /var/jenkins_home/plugins/*.jpi 2>/dev/null | xargs -I{} basename {} .jpi 2>/dev/null | sort
    """)
    print(f"  Installed plugins:\n{out}")
    
    ssh.close()
    print("\n[DONE]")


if __name__ == "__main__":
    main()
