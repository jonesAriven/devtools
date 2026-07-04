import paramiko

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('120.26.66.182', port=3385, username='root', password='root', timeout=15)
stdin, stdout, stderr = ssh.exec_command('cd /root/devtools/mykng && docker ps --format "table {{.Names}}\t{{.Status}}"')
print(stdout.read().decode())
print(stderr.read().decode())
ssh.close()
