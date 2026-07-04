import paramiko

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('120.26.66.182', port=3385, username='root', password='root', timeout=15)

print("=== docker ps -a ===")
stdin, stdout, stderr = ssh.exec_command("docker ps -a --format 'table {{.Names}}\\t{{.Status}}\\t{{.Image}}' | grep -E 'NAMES|kb-'")
print(stdout.read().decode())

print("\n=== kb-auth logs (last 40) ===")
stdin, stdout, stderr = ssh.exec_command("docker logs kb-auth --tail 40 2>&1")
print(stdout.read().decode())

print("\n=== kb-file logs (last 40) ===")
stdin, stdout, stderr = ssh.exec_command("docker logs kb-file --tail 40 2>&1")
print(stdout.read().decode())

print("\n=== kb-gateway logs (last 20) ===")
stdin, stdout, stderr = ssh.exec_command("docker logs kb-gateway --tail 20 2>&1")
print(stdout.read().decode())

print("\n=== kb-knowledge logs (last 20) ===")
stdin, stdout, stderr = ssh.exec_command("docker logs kb-knowledge --tail 20 2>&1")
print(stdout.read().decode())

ssh.close()
