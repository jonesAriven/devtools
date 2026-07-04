import paramiko

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('120.26.66.182', port=3385, username='root', password='root', timeout=15)

stdin, stdout, stderr = ssh.exec_command("docker ps --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'")
print("=== Docker PS ===")
print(stdout.read().decode())

stdin, stdout, stderr = ssh.exec_command("curl -s http://localhost:8090/kb/actuator/health")
print("=== Health ===")
print(stdout.read().decode())

stdin, stdout, stderr = ssh.exec_command("curl -s http://localhost:8090/kb/api/system/modules")
print("=== Modules ===")
print(stdout.read().decode()[:1000])

stdin, stdout, stderr = ssh.exec_command("curl -s -o /dev/null -w '%{http_code}' http://localhost:8090/kb/swagger-ui.html")
print("=== Swagger UI ===")
print("HTTP:", stdout.read().decode())

stdin, stdout, stderr = ssh.exec_command("curl -s -o /dev/null -w '%{http_code}' http://localhost:8090/kb/api/auth/v3/api-docs")
print("=== Auth API Docs ===")
print("HTTP:", stdout.read().decode())

stdin, stdout, stderr = ssh.exec_command("curl -s -o /dev/null -w '%{http_code}' http://localhost:8090/kb/api/file/v3/api-docs")
print("=== File API Docs ===")
print("HTTP:", stdout.read().decode())

stdin, stdout, stderr = ssh.exec_command("curl -s -o /dev/null -w '%{http_code}' http://localhost:8090/kb/api/knowledge/v3/api-docs")
print("=== Knowledge API Docs ===")
print("HTTP:", stdout.read().decode())

ssh.close()
