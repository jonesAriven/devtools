import paramiko

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('120.26.66.182', port=3385, username='root', password='root', timeout=15)

# Docker status
stdin, stdout, stderr = ssh.exec_command("docker ps --format '{{.Names}}\t{{.Status}}'")
lines = stdout.read().decode().strip().split('\n')
print("=== Docker 容器状态 ===")
for line in lines:
    if 'kb-' in line or 'NAMES' in line:
        print(f"  {line}")

# Gateway health
print("\n=== 网关健康检查 ===")
stdin, stdout, stderr = ssh.exec_command("curl -s http://localhost:8090/kb/actuator/health")
print(f"  {stdout.read().decode().strip()}")

# Modules endpoint
print("\n=== 模块列表 ===")
stdin, stdout, stderr = ssh.exec_command("curl -s http://localhost:8090/kb/api/system/modules")
out = stdout.read().decode().strip()
print(f"  {out[:500]}")

# Swagger UI
print("\n=== Swagger UI ===")
stdin, stdout, stderr = ssh.exec_command("curl -s -o /dev/null -w '%{http_code}' http://localhost:8090/kb/swagger-ui.html")
print(f"  HTTP status: {stdout.read().decode().strip()}")

# Auth API docs
print("\n=== Auth API Docs ===")
stdin, stdout, stderr = ssh.exec_command("curl -s -o /dev/null -w '%{http_code}' http://localhost:8090/kb/api/auth/v3/api-docs")
print(f"  HTTP status: {stdout.read().decode().strip()}")

ssh.close()
