import paramiko

HOST = "120.26.66.182"
PORT = 3385
USER = "root"
PASSWORD = "root"

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect(HOST, port=PORT, username=USER, password=PASSWORD, timeout=15)

# 检查容器的 labels（看是否由 compose 管理）
print("=== Container labels ===")
for name in ["kb-gateway", "kb-auth", "kb-file", "kb-knowledge", "kb-intelligence", "kb-mysql"]:
    cmd = f'docker inspect {name} --format "{{{{index .Config.Labels \\"com.docker.compose.project\\"}}}} {{{{index .Config.Labels \\"com.docker.compose.service\\"}}}}"'
    stdin, stdout, stderr = ssh.exec_command(cmd)
    out = stdout.read().decode().strip()
    print(f"  {name}: {out}")

# 看看 mykng 上有哪些 docker compose 项目
print("\n=== docker compose ls ===")
stdin, stdout, stderr = ssh.exec_command("cd /root/devtools/mykng && docker compose ls 2>&1")
print(stdout.read().decode())

# 看看实际是怎么启动的
print("\n=== docker compose ps ===")
stdin, stdout, stderr = ssh.exec_command("cd /root/devtools/mykng && docker compose -p kb-deploy ps 2>&1")
print(stdout.read().decode())

ssh.close()
