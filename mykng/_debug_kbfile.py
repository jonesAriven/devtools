import paramiko

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('120.26.66.182', port=3385, username='root', password='root', timeout=15)

print("=== kb-file logs (last 80 lines) ===")
stdin, stdout, stderr = ssh.exec_command("docker logs kb-file --tail 80 2>&1")
print(stdout.read().decode())

print("\n=== kb-file restart count ===")
stdin, stdout, stderr = ssh.exec_command("docker inspect kb-file --format '{{.RestartCount}}'")
print(f"  Restart count: {stdout.read().decode().strip()}")

ssh.close()
