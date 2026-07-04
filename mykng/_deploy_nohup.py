import paramiko
import os

HOST = "120.26.66.182"
PORT = 3385
USER = "root"
PASSWORD = "root"
LOCAL_BASE = r"d:\huliang\java\ideaworkspace\devtools\mykng"
REMOTE_BASE = "/root/kb-deploy"
SERVICES = ["kb-gateway", "kb-auth", "kb-file", "kb-knowledge"]

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
print("Connecting...")
ssh.connect(HOST, port=PORT, username=USER, password=PASSWORD, timeout=15)
sftp = ssh.open_sftp()
print("Connected!")

# 1. Upload jars
print("\n[1/2] Uploading jars via SFTP...")
for svc in SERVICES:
    local_jar = os.path.join(LOCAL_BASE, svc, "target", f"{svc}.jar")
    remote_jar = f"{REMOTE_BASE}/{svc}/{svc}.jar"
    print(f"  Uploading {svc}.jar ...", end=" ", flush=True)
    sftp.put(local_jar, remote_jar)
    print("✅")

sftp.close()

# 2. Build + restart with nohup (in background)
print("\n[2/2] Starting build + restart in background (nohup)...")
build_script = """
#!/bin/bash
cd /root/kb-deploy
echo "Building images..." > /tmp/deploy.log
for svc in kb-gateway kb-auth kb-file kb-knowledge; do
    echo "--- Building $svc ---" >> /tmp/deploy.log
    docker compose build $svc >> /tmp/deploy.log 2>&1
    echo "Build $svc done, exit=$?" >> /tmp/deploy.log
done
echo "Restarting containers..." >> /tmp/deploy.log
docker compose up -d kb-gateway kb-auth kb-file kb-knowledge >> /tmp/deploy.log 2>&1
echo "Done at $(date)" >> /tmp/deploy.log
"""
stdin, stdout, stderr = ssh.exec_command(f"cat > /tmp/deploy.sh << 'ENDSCRIPT'\n{build_script}\nENDSCRIPT\nchmod +x /tmp/deploy.sh && nohup /tmp/deploy.sh > /tmp/deploy_nohup.log 2>&1 &\necho 'PID: '$!")
print(stdout.read().decode())
print(stderr.read())

ssh.close()
print("\nDeploy script started in background. Check with: python _check_deploy.py")
