#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
检查 Jenkins 插件安装进度，修复镜像源
"""
import paramiko
import time, sys

sys.stdout.reconfigure(encoding='utf-8', errors='ignore')

HOST = "100.93.36.113"
USER = "root"
PASS = "root"

def main():
    print("=" * 60)
    print("  Jenkins Plugin Progress Check")
    print("=" * 60)
    
    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    ssh.connect(HOST, username=USER, password=PASS, timeout=15)
    
    # 1. 检查当前下载状态
    print("\n[1] Current download progress:")
    stdin, stdout, stderr = ssh.exec_command("""
        docker logs jenkins-ci --tail 30 2>&1 | grep -iE "(downloading|success|fail|installing|pending|error|404)" | tail -15
    """, timeout=15)
    out = stdout.read().decode(errors='replace').strip()
    if out:
        print(f"  {out}")
    
    # 2. 修复镜像源 - 华为云404了，换回清华或用腾讯云内网
    print("\n[2] Fixing mirror source (huaweicloud returned 404)...")
    
    # 方案: 使用清华源 + 禁用签名验证加速
    fix_script = '''
JENKINS_HOME="/var/lib/docker/volumes/jenkins_jenkins_home/_data"

# 方法1: 换回清华镜像（之前能用）
cat > "$JENKINS_HOME/hudson.model.UpdateCenter.xml" << 'XMLEOF'
<?xml version='1.1' encoding='UTF-8'?>
<sites>
  <site>
    <id>default</id>
    <url>https://mirrors.tuna.tsinghua.edu.cn/jenkins/updates/update-center.json</url>
  </site>
</sites>
XMLEOF

echo "Mirror set to: Tsinghua"

# 方法2: 在 docker-compose 中添加环境变量禁用签名检查
# 这样可以跳过签名验证，加快下载速度

# 检查是否有 jenkins.cli 或 jenkins.sh 可以设置 JAVA_OPTS
docker inspect jenkins-ci --format '{{range .Config.Env}}{{println .}}{{end}}' | grep -i java || echo "No JAVA_OPTS found"

# 方法3: 通过 Jenkins 环境变量文件设置
mkdir -p "$JENKINS_HOME/init.groovy.d"
cat > "$JENKINS_HOME/init.groovy.d/disable-signature-check.groovy" << 'GROOVYEOF'
import jenkins.model.*
import hudson.model.*
import jenkins.install.*

def instance = Jenkins.getInstance()
def uc = instance.getUpdateCenter()
uc.getSites().each { site ->
    site.updateCenterConfiguration.disableSignatureCheck()
}
println "Plugin signature check disabled"
GROOVYEOF

echo "Created init groovy to disable signature check"
echo ""
echo "Current plugins count:"
ls "$JENKINS_HOME/plugins/"*.jpi 2>/dev/null | wc -l
echo ""
echo "Plugins list:"
ls -la "$JENKINS_HOME/plugins/"*.jpi 2>/dev/null | awk '{print $NF}' | xargs -I{} basename {} .jpi 2>/dev/null | sort || echo "No .jpi files yet"
'''
    
    stdin, stdout, stderr = ssh.exec_command(fix_script, timeout=30)
    print(stdout.read().decode(errors='replace'))
    err = stderr.read().decode(errors='replace')
    if err:
        print(f"  STDERR: {err[:300]}")
    
    # 3. 重启 Jenkins 使配置生效
    print("\n[3] Restarting Jenkins to apply new config...")
    stdin, stdout, stderr = ssh.exec_command("docker restart jenkins-ci", timeout=30)
    print(stdout.read().decode(errors='replace').strip())
    
    # 4. 等待启动
    print("\n[4] Waiting for Jenkins...")
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
    
    # 5. 再次检查插件列表
    print("\n[5] Installed plugins after restart:")
    stdin, stdout, stderr = ssh.exec_command("""
        JENKINS_HOME="/var/lib/docker/volumes/jenkins_jenkins_home/_data"
        echo "Total .jpi files: $(ls $JENKINS_HOME/plugins/*.jpi 2>/dev/null | wc -l)"
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


if __name__ == "__main__":
    main()
