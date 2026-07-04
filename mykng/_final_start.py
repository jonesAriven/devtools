import paramiko
import time

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('120.26.66.182', port=3385, username='root', password='root', timeout=15)

# 启动 kb-gateway 和 kb-knowledge
print("Starting kb-gateway and kb-knowledge...")
stdin, stdout, stderr = ssh.exec_command("docker start kb-gateway kb-knowledge 2>&1")
print(stdout.read().decode())
print(stderr.read())

# 等 2 分钟
print("Waiting 120s...")
time.sleep(120)

# 检查状态
stdin, stdout, stderr = ssh.exec_command("docker ps --format 'table {{.Names}}\\t{{.Status}}\\t{{.Ports}}'")
lines = [l for l in stdout.read().decode().strip().split('\n') if 'kb-' in l or 'NAMES' in l]
print("\n=== Status ===")
for l in lines:
    print(f"  {l}")

print("\n=== Gateway health ===")
stdin, stdout, stderr = ssh.exec_command("curl -s --max-time 5 http://localhost:8090/kb/actuator/health")
print(f"  {stdout.read().decode().strip()}")

print("\n=== Modules endpoint ===")
stdin, stdout, stderr = ssh.exec_command("curl -s --max-time 5 http://localhost:8090/kb/api/system/modules")
print(f"  {stdout.read().decode().strip()[:800]}")

print("\n=== Swagger UI ===")
stdin, stdout, stderr = ssh.exec_command("curl -s --max-time 5 -o /dev/null -w '%{http_code}' http://localhost:8090/kb/swagger-ui.html")
print(f"  HTTP {stdout.read().decode().strip()}")

print("\n=== Auth API Docs ===")
stdin, stdout, stderr = ssh.exec_command("curl -s --max-time 5 -o /dev/null -w '%{http_code}' http://localhost:8090/kb/api/auth/v3/api-docs")
print(f"  HTTP {stdout.read().decode().strip()}")

print("\n=== File API Docs ===")
stdin, stdout, stderr = ssh.exec_command("curl -s --max-time 5 -o /dev/null -w '%{http_code}' http://localhost:8090/kb/api/file/v3/api-docs")
print(f"  HTTP {stdout.read().decode().strip()}")

print("\n=== Knowledge API Docs ===")
stdin, stdout, stderr = ssh.exec_command("curl -s --max-time 5 -o /dev/null -w '%{http_code}' http://localhost:8090/kb/api/knowledge/v3/api-docs")
print(f"  HTTP {stdout.read().decode().strip()}")

ssh.close()
