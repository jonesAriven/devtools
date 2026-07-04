import paramiko

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('120.26.66.182', port=3385, username='root', password='root', timeout=15)

# 1. 看看 kb-deploy compose 管理的容器有哪些
print("=== kb-deploy compose ps ===")
stdin, stdout, stderr = ssh.exec_command("cd /root/kb-deploy && docker compose ps 2>&1")
print(stdout.read().decode())

# 2. 看看 kb-auth 的日志（最后 30 行）
print("\n=== kb-auth logs (last 30) ===")
stdin, stdout, stderr = ssh.exec_command("docker logs kb-auth --tail 30 2>&1")
print(stdout.read().decode())

# 3. 看看 kb-gateway 的日志
print("\n=== kb-gateway logs (last 30) ===")
stdin, stdout, stderr = ssh.exec_command("docker logs kb-gateway --tail 30 2>&1")
print(stdout.read().decode())

ssh.close()
