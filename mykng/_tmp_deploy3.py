import paramiko
import os
import sys

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
print("\n[1/3] Uploading jars...")
for svc in SERVICES:
    local_jar = os.path.join(LOCAL_BASE, svc, "target", f"{svc}.jar")
    remote_jar = f"{REMOTE_BASE}/{svc}/{svc}.jar"
    print(f"  Uploading {svc}.jar ...")
    sftp.put(local_jar, remote_jar)
    print(f"  ✅ {svc}.jar uploaded")

sftp.close()

# 2. Build images (one by one to see output)
print("\n[2/3] Building images...")
for svc in SERVICES:
    print(f"\n--- Building {svc} ---")
    cmd = f"cd {REMOTE_BASE} && docker compose build {svc} 2>&1 | tail -5"
    stdin, stdout, stderr = ssh.exec_command(cmd)
    print(stdout.read().decode())
    exit_code = stdout.channel.recv_exit_status()
    if exit_code != 0:
        print(f"  ❌ Build failed for {svc}")
        ssh.close()
        sys.exit(1)
    print(f"  ✅ {svc} built")

# 3. Restart
print("\n[3/3] Restarting containers...")
cmd = f"cd {REMOTE_BASE} && docker compose up -d {' '.join(SERVICES)} 2>&1"
stdin, stdout, stderr = ssh.exec_command(cmd)
print(stdout.read().decode())
print(stderr.read())

# Wait
print("\nWaiting 30s for services to start...")
import time
time.sleep(30)

# Status
print("\n=== Status ===")
stdin, stdout, stderr = ssh.exec_command("docker ps --format 'table {{.Names}}\\t{{.Status}}' | grep -E 'NAMES|kb-'")
print(stdout.read().decode())

ssh.close()
print("\nDone!")
