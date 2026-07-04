import paramiko

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('120.26.66.182', port=3385, username='root', password='root', timeout=15)

print("=== /tmp/deploy_full.log (last 50 lines) ===")
stdin, stdout, stderr = ssh.exec_command("tail -50 /tmp/deploy_full.log 2>&1")
print(stdout.read().decode())

print("\n=== /tmp/deploy_full_nohup.log ===")
stdin, stdout, stderr = ssh.exec_command("cat /tmp/deploy_full_nohup.log 2>&1")
print(stdout.read().decode())

print("\n=== ps aux | grep deploy ===")
stdin, stdout, stderr = ssh.exec_command("ps aux | grep -E 'deploy|mvn|docker' | grep -v grep")
print(stdout.read().decode())

ssh.close()
