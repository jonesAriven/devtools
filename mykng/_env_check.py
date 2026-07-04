import paramiko

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('120.26.66.182', port=3385, username='root', password='root', timeout=15)

# Check if there's a git repo
print("=== Check git repos ===")
stdin, stdout, stderr = ssh.exec_command("ls -la /root/devtools/mykng/ | head -20")
print(stdout.read().decode())

print("\n=== Check java/mvn ===")
stdin, stdout, stderr = ssh.exec_command("which java && java -version 2>&1 && which mvn && mvn -version 2>&1 | head -3")
print(stdout.read().decode())

print("\n=== Check kb-deploy Dockerfiles ===")
stdin, stdout, stderr = ssh.exec_command("cat /root/kb-deploy/kb-gateway/Dockerfile 2>&1")
print(stdout.read().decode())

ssh.close()
