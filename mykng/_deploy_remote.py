import paramiko

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('120.26.66.182', port=3385, username='root', password='root', timeout=15)

# 部署脚本：mvn package + docker compose build + up -d
# 注意：/root/kb-deploy/ 是实际运行的 compose 项目，但代码在 /root/devtools/mykng/
# Dockerfile 里 COPY target/*.jar，所以需要把 jar 复制到 /root/kb-deploy/{service}/target/
deploy_script = r"""#!/bin/bash
set -e
LOG=/tmp/deploy_full.log
echo "=== Deployment started at $(date) ===" > $LOG

SERVICES="kb-gateway kb-auth kb-file kb-knowledge kb-intelligence"
SRC=/root/devtools/mykng
DST=/root/kb-deploy

# 1. Maven build
echo "[1/4] Maven package..." >> $LOG
cd $SRC/kb-parent
mvn clean package -DskipTests -q >> $LOG 2>&1
echo "Maven build done" >> $LOG

# 2. Copy jars to kb-deploy
echo "[2/4] Copy jars to kb-deploy..." >> $LOG
for svc in $SERVICES; do
    mkdir -p $DST/$svc/target
    cp $SRC/$svc/target/$svc.jar $DST/$svc/target/$svc.jar
    echo "  Copied $svc.jar" >> $LOG
done

# 3. Docker build
echo "[3/4] Docker build..." >> $LOG
cd $DST
for svc in $SERVICES; do
    echo "  Building $svc..." >> $LOG
    docker compose build $svc >> $LOG 2>&1
    echo "  $svc built" >> $LOG
done

# 4. Restart containers
echo "[4/4] Restart containers..." >> $LOG
cd $DST
docker compose up -d $SERVICES >> $LOG 2>&1
echo "Restart done" >> $LOG

echo "=== Deployment finished at $(date) ===" >> $LOG
"""

stdin, stdout, stderr = ssh.exec_command(f"cat > /tmp/deploy_full.sh << 'ENDSCRIPT'\n{deploy_script}\nENDSCRIPT\nchmod +x /tmp/deploy_full.sh && nohup /tmp/deploy_full.sh > /tmp/deploy_full_nohup.log 2>&1 &\necho 'Started PID: '$!")
print(stdout.read().decode())
err = stderr.read().decode()
if err:
    print("STDERR:", err)

ssh.close()
print("\n部署脚本已在远程后台启动。")
print("日志文件：/tmp/deploy_full.log")
print("大约 3-5 分钟后检查结果。")
