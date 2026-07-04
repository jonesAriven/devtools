import paramiko

HOST = "120.26.66.182"
PORT = 3385
USER = "root"
PASSWORD = "root"

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect(HOST, port=PORT, username=USER, password=PASSWORD, timeout=15)

print("=== /root/kb-deploy/ structure ===")
stdin, stdout, stderr = ssh.exec_command("ls -la /root/kb-deploy/")
print(stdout.read().decode())

print("\n=== kb-deploy docker-compose services ===")
stdin, stdout, stderr = ssh.exec_command("cd /root/kb-deploy && docker compose config --services 2>&1")
print(stdout.read().decode())

print("\n=== docker compose build context ===")
stdin, stdout, stderr = ssh.exec_command("cd /root/kb-deploy && grep -A2 'build:' docker-compose.yml | head -20")
print(stdout.read().decode())

ssh.close()
