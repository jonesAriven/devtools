import paramiko

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('120.26.66.182', port=3385, username='root', password='root', timeout=15)

# 查看所有 kb-* 容器（包括停止的）
print("=== All kb containers ===")
stdin, stdout, stderr = ssh.exec_command("docker ps -a --format 'table {{.Names}}\\t{{.Status}}\\t{{.Image}}' | grep -E 'NAMES|kb-'")
print(stdout.read().decode())

# 看看 kb-gateway 和 kb-knowledge 的容器状态
print("\n=== kb-gateway details ===")
stdin, stdout, stderr = ssh.exec_command("docker inspect kb-gateway --format '{{.State.Status}} {{.State.Error}}' 2>&1")
print(stdout.read().decode())

print("\n=== kb-knowledge details ===")
stdin, stdout, stderr = ssh.exec_command("docker inspect kb-knowledge --format '{{.State.Status}} {{.State.Error}}' 2>&1")
print(stdout.read().decode())

# 直接手动 start 试试
print("\n=== Starting kb-gateway & kb-knowledge ===")
stdin, stdout, stderr = ssh.exec_command("docker start kb-gateway kb-knowledge 2>&1")
print(stdout.read().decode())
print(stderr.read())

ssh.close()
