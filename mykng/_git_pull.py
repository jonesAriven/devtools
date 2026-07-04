import paramiko

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('120.26.66.182', port=3385, username='root', password='root', timeout=15)

# 检查 /root/devtools/mykng 是否是 git 仓库
print("=== Git status ===")
stdin, stdout, stderr = ssh.exec_command("cd /root/devtools/mykng && git status --short 2>&1 | head -20")
print(stdout.read().decode())
print(stderr.read())

print("\n=== Git pull ===")
stdin, stdout, stderr = ssh.exec_command("cd /root/devtools/mykng && git pull origin dev 2>&1 | tail -10")
print(stdout.read().decode())
print(stderr.read()[:500])

ssh.close()
