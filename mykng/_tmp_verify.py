import paramiko

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('120.26.66.182', port=3385, username='root', password='root', timeout=15)

print("=== Container Status ===")
stdin, stdout, stderr = ssh.exec_command("docker ps --format 'table {{.Names}}\t{{.Status}}' | grep -E 'NAMES|kb-'")
print(stdout.read().decode())

print("\n=== Gateway health ===")
stdin, stdout, stderr = ssh.exec_command("curl -s http://localhost:8090/kb/actuator/health")
print(stdout.read().decode())

print("\n=== Modules endpoint ===")
stdin, stdout, stderr = ssh.exec_command("curl -s http://localhost:8090/kb/api/system/modules")
print(stdout.read().decode()[:2000])

print("\n=== Swagger UI check ===")
stdin, stdout, stderr = ssh.exec_command("curl -s -o /dev/null -w '%{http_code}' http://localhost:8090/kb/swagger-ui.html")
print(f"HTTP status: {stdout.read().decode()}")

ssh.close()
