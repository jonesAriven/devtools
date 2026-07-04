import paramiko

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('120.26.66.182', port=3385, username='root', password='root', timeout=15)

# 检查 kb-deploy compose 的 docker-compose.yml 里有哪些服务，以及 mysql 是否在里面
print("=== kb-deploy services ===")
stdin, stdout, stderr = ssh.exec_command("cd /root/kb-deploy && docker compose config --services 2>&1")
print(stdout.read().decode())

# 检查当前运行的 compose 项目
print("=== compose ls ===")
stdin, stdout, stderr = ssh.exec_command("docker compose ls 2>&1")
print(stdout.read().decode())

# 看看 kb-deploy 的 docker-compose.yml 里 mysql 部分
print("=== kb-deploy mysql config ===")
stdin, stdout, stderr = ssh.exec_command("grep -A20 'mysql:' /root/kb-deploy/docker-compose.yml 2>&1")
print(stdout.read().decode())

ssh.close()
