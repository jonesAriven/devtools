import paramiko

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('120.26.66.182', port=3385, username='root', password='root', timeout=15)

print("=== Docker volumes ===")
stdin, stdout, stderr = ssh.exec_command("docker volume ls | grep kb-")
print(stdout.read().decode())

print("\n=== Networks ===")
stdin, stdout, stderr = ssh.exec_command("docker network ls | grep kb-")
print(stdout.read().decode())

print("\n=== All kb containers ===")
stdin, stdout, stderr = ssh.exec_command("docker ps -a --format 'table {{.Names}}\\t{{.Status}}\\t{{.Image}}' | grep -E 'NAMES|kb-'")
print(stdout.read().decode())

print("\n=== kb-deploy compose services ===")
stdin, stdout, stderr = ssh.exec_command("cd /root/kb-deploy && docker compose config --volumes 2>&1 | head -20")
print(stdout.read().decode())

ssh.close()
