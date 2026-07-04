import paramiko

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('120.26.66.182', port=3385, username='root', password='root', timeout=15)

print("=== Nginx / 前端 ===")
stdin, stdout, stderr = ssh.exec_command("curl -s --max-time 5 -I http://120.26.66.182/ 2>&1 | head -5")
print(stdout.read().decode())

print("=== Nacos ===")
stdin, stdout, stderr = ssh.exec_command("curl -s --max-time 5 -o /dev/null -w '%{http_code}' http://120.26.66.182:8848/nacos/")
print(f"  HTTP {stdout.read().decode().strip()}")

print("\n=== MinIO ===")
stdin, stdout, stderr = ssh.exec_command("curl -s --max-time 5 -o /dev/null -w '%{http_code}' http://120.26.66.182:9001/")
print(f"  Console HTTP {stdout.read().decode().strip()}")

print("\n=== MeiliSearch ===")
stdin, stdout, stderr = ssh.exec_command("curl -s --max-time 5 http://120.26.66.182:7700/health")
print(f"  Health: {stdout.read().decode().strip()}")

print("\n=== 模块状态 ===")
stdin, stdout, stderr = ssh.exec_command("curl -s --max-time 5 http://120.26.66.182:8090/kb/api/system/modules")
print(stdout.read().decode())

ssh.close()
