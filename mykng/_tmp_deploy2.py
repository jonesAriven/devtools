import paramiko
import sys

SERVICES = ["kb-gateway", "kb-auth", "kb-file", "kb-knowledge", "kb-intelligence"]
HOST = "120.26.66.182"
PORT = 3385
USER = "root"
PASSWORD = "root"
REMOTE_BASE = "/root/devtools/mykng"

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
print("Connecting...")
ssh.connect(HOST, port=PORT, username=USER, password=PASSWORD, timeout=15)
print("Connected!")

# 先检查 jar 包是否上传了
print("\n--- Checking jar files ---")
for svc in SERVICES:
    cmd = f"ls -lh {REMOTE_BASE}/{svc}/target/{svc}.jar 2>&1 | head -2"
    stdin, stdout, stderr = ssh.exec_command(cmd)
    print(f"  {svc}: {stdout.read().decode().strip()}")

# Build
print("\n--- Building images ---")
cmd = f"cd {REMOTE_BASE} && docker compose -p kb-deploy build {' '.join(SERVICES)} 2>&1"
print(f"  Exec: {cmd}")
stdin, stdout, stderr = ssh.exec_command(cmd)
output = stdout.read().decode()
err = stderr.read().decode()
print(output[-2000:] if len(output) > 2000 else output)
if err:
    print("STDERR:", err[-1000:])
exit_code = stdout.channel.recv_exit_status()
print(f"  Exit code: {exit_code}")
if exit_code != 0:
    print("BUILD FAILED!")
    ssh.close()
    sys.exit(1)

# Restart
print("\n--- Restarting containers ---")
cmd = f"cd {REMOTE_BASE} && docker compose -p kb-deploy up -d {' '.join(SERVICES)} 2>&1"
stdin, stdout, stderr = ssh.exec_command(cmd)
print(stdout.read().decode())
print(stderr.read())

# Status
print("\n--- Status ---")
stdin, stdout, stderr = ssh.exec_command("docker ps --format 'table {{.Names}}\t{{.Status}}'")
print(stdout.read().decode())

ssh.close()
print("\nDone!")
