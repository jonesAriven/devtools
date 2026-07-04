import paramiko

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('120.26.66.182', port=3385, username='root', password='root', timeout=15)

# 1. 先停掉 mykng 项目的 kb-intelligence（容器名冲突）
print("=== Stop mykng kb-intelligence ===")
stdin, stdout, stderr = ssh.exec_command("cd /root/devtools/mykng && docker compose stop kb-intelligence 2>&1 && docker compose rm -f kb-intelligence 2>&1")
print(stdout.read().decode())
print(stderr.read()[:500])

# 2. 逐个重启 kb-deploy 的 5 个应用服务（不动基础设施）
SERVICES = ["kb-auth", "kb-file", "kb-knowledge", "kb-intelligence", "kb-gateway"]
print("\n=== Restart services ===")
for svc in SERVICES:
    print(f"  Restarting {svc} ...", end=" ", flush=True)
    stdin, stdout, stderr = ssh.exec_command(f"cd /root/kb-deploy && docker compose restart {svc} 2>&1")
    out = stdout.read().decode().strip()
    err = stderr.read().decode().strip()
    if "Error" in err or "error" in err.lower():
        print(f"❌ {err[:200]}")
    else:
        print("✅")

# 3. 等待启动
print("\nWaiting 40s for services to start...")
import time
time.sleep(40)

# 4. 状态
print("\n=== Status ===")
stdin, stdout, stderr = ssh.exec_command("docker ps --format 'table {{.Names}}\\t{{.Status}}' | grep -E 'NAMES|kb-'")
print(stdout.read().decode())

print("\n=== Gateway health ===")
stdin, stdout, stderr = ssh.exec_command("curl -s http://localhost:8090/kb/actuator/health")
print(f"  {stdout.read().decode().strip()}")

print("\n=== Modules endpoint ===")
stdin, stdout, stderr = ssh.exec_command("curl -s http://localhost:8090/kb/api/system/modules")
out = stdout.read().decode().strip()
print(f"  {out[:800]}")

print("\n=== Swagger UI ===")
stdin, stdout, stderr = ssh.exec_command("curl -s -o /dev/null -w '%{http_code}' http://localhost:8090/kb/swagger-ui.html")
print(f"  HTTP {stdout.read().decode().strip()}")

ssh.close()
print("\nDone!")
