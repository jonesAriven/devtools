import paramiko
import time

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('120.26.66.182', port=3385, username='root', password='root', timeout=15)

# 等 kb-file 启动
print("Waiting 60s for kb-file...")
time.sleep(60)

# 容器状态
print("\n=== All Services ===")
stdin, stdout, stderr = ssh.exec_command("docker ps --format 'table {{.Names}}\t{{.Status}}' | grep -E 'NAMES|kb-(gateway|auth|file|knowledge|intelligence)'")
print(stdout.read().decode())

# 模块端点
print("=== Modules Status ===")
stdin, stdout, stderr = ssh.exec_command("curl -s --max-time 5 http://localhost:8090/kb/api/system/modules")
print(stdout.read().decode())

# 前端
print("\n=== Frontend ===")
stdin, stdout, stderr = ssh.exec_command("curl -s --max-time 5 -o /dev/null -w '%{http_code}' http://localhost:8090/kb/")
print(f"  前端页面: HTTP {stdout.read().decode().strip()}")

# Swagger
print("\n=== Swagger / API Docs ===")
stdin, stdout, stderr = ssh.exec_command("curl -s --max-time 5 -o /dev/null -w '%{http_code}' http://localhost:8090/swagger-ui.html")
print(f"  Swagger UI 聚合页: HTTP {stdout.read().decode().strip()}")

services = [
    ("认证服务 (kb-auth)", "/kb/api/auth/v3/api-docs"),
    ("文件服务 (kb-file)", "/kb/api/file/v3/api-docs"),
    ("知识库服务 (kb-knowledge)", "/kb/api/knowledge/v3/api-docs"),
    ("知识引擎 (kb-intelligence)", "/kb/api/intelligence/v3/api-docs"),
]
for name, path in services:
    stdin, stdout, stderr = ssh.exec_command(f"curl -s --max-time 5 -o /dev/null -w '%{{http_code}}' http://localhost:8090{path}")
    code = stdout.read().decode().strip()
    print(f"  {name}: HTTP {code}")

# Nacos
print("\n=== Nacos ===")
stdin, stdout, stderr = ssh.exec_command("curl -s --max-time 5 -o /dev/null -w '%{http_code}' http://localhost:8848/nacos/")
print(f"  Nacos 控制台: HTTP {stdout.read().decode().strip()}")

ssh.close()
