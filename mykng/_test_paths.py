import paramiko
import time

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('120.26.66.182', port=3385, username='root', password='root', timeout=15)

# 再等 60 秒
print("Waiting 60 more seconds...")
time.sleep(60)

# 检查网关完整启动日志
print("=== Gateway startup logs (last 30) ===")
stdin, stdout, stderr = ssh.exec_command("docker logs kb-gateway --tail 30 2>&1")
print(stdout.read().decode())

# 测试各种路径
print("\n=== Path tests ===")
paths = [
    "/swagger-ui.html",
    "/kb/swagger-ui.html",
    "/webjars/swagger-ui/index.html",
    "/kb/webjars/swagger-ui/index.html",
    "/v3/api-docs",
    "/kb/v3/api-docs",
    "/v3/api-docs/swagger-config",
    "/kb/v3/api-docs/swagger-config",
]
for p in paths:
    stdin, stdout, stderr = ssh.exec_command(f"curl -s --max-time 3 -o /dev/null -w '%{{http_code}}' http://localhost:8090{p}")
    code = stdout.read().decode().strip()
    print(f"  {p}: HTTP {code}")

ssh.close()
