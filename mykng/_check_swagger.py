import paramiko

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('120.26.66.182', port=3385, username='root', password='root', timeout=15)

print("=== Swagger config ===")
stdin, stdout, stderr = ssh.exec_command("curl -s http://localhost:8090/kb/v3/api-docs/swagger-config")
print(stdout.read().decode())

print("\n=== Auth API Docs (via gateway) ===")
stdin, stdout, stderr = ssh.exec_command("curl -s --max-time 5 http://localhost:8090/kb/api/auth/v3/api-docs | head -c 300")
print(stdout.read().decode())

print("\n=== Knowledge API Docs (via gateway) ===")
stdin, stdout, stderr = ssh.exec_command("curl -s --max-time 5 http://localhost:8090/kb/api/knowledge/v3/api-docs | head -c 300")
print(stdout.read().decode())

print("\n=== File API Docs (via gateway) ===")
stdin, stdout, stderr = ssh.exec_command("curl -s --max-time 5 http://localhost:8090/kb/api/file/v3/api-docs | head -c 300")
print(stdout.read().decode())

print("\n=== Intelligence API Docs (via gateway) ===")
stdin, stdout, stderr = ssh.exec_command("curl -s --max-time 5 http://localhost:8090/kb/api/intelligence/v3/api-docs | head -c 300")
print(stdout.read().decode())

ssh.close()
