import paramiko
import time

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('120.26.66.182', port=3385, username='root', password='root', timeout=15)

# 找出正在运行的 kb-* 应用容器
print("=== Running app containers ===")
stdin, stdout, stderr = ssh.exec_command("docker ps --format '{{.Names}} {{.Image}}' | grep -E '^kb-(gateway|auth|file|knowledge|intelligence)'")
print(stdout.read().decode())

# 对于运行中的容器，热更新 jar 包
APPS = ["kb-gateway", "kb-auth", "kb-file", "kb-knowledge", "kb-intelligence"]

print("\n=== Hot patch running containers ===")
updated = []
for app in APPS:
    stdin, stdout, stderr = ssh.exec_command(f"docker ps --format '{{{{.Names}}}}' | grep -x {app}")
    name = stdout.read().decode().strip()
    if not name:
        print(f"  ⚠️  {app} not running")
        continue
    
    # 检查 /root/kb-deploy 下是否有新 jar
    stdin, stdout, stderr = ssh.exec_command(f"ls -lh /root/kb-deploy/{app}/target/{app}.jar 2>&1")
    jar_info = stdout.read().decode().strip()
    if "No such" in jar_info:
        print(f"  ⚠️  No jar for {app}: {jar_info}")
        continue
    
    print(f"  Updating {app} ...", end=" ", flush=True)
    stdin, stdout, stderr = ssh.exec_command(f"docker cp /root/kb-deploy/{app}/target/{app}.jar {name}:/app/{app}.jar")
    exit_code = stdout.channel.recv_exit_status()
    if exit_code != 0:
        print(f"❌ cp failed: {stderr.read().decode()[:200]}")
        continue
    
    stdin, stdout, stderr = ssh.exec_command(f"docker restart {name}")
    exit_code = stdout.channel.recv_exit_status()
    if exit_code != 0:
        print(f"❌ restart failed: {stderr.read().decode()[:200]}")
        continue
    
    updated.append(app)
    print("✅")

# 对于没运行的容器，看看能不能 start
print("\n=== Start stopped containers ===")
for app in APPS:
    if app in updated:
        continue
    stdin, stdout, stderr = ssh.exec_command(f"docker ps -a --format '{{{{.Names}}}}' | grep -x {app}")
    name = stdout.read().decode().strip()
    if not name:
        print(f"  ⚠️  {app} container does not exist")
        continue
    stdin, stdout, stderr = ssh.exec_command(f"docker start {name} 2>&1")
    print(f"  Starting {app}: {stdout.read().decode().strip()}")

print("\nWaiting 90s for all services to start...")
time.sleep(90)

# 最终验证
print("\n=== Final Status ===")
stdin, stdout, stderr = ssh.exec_command("docker ps --format 'table {{.Names}}\\t{{.Status}}' | grep -E 'NAMES|kb-'")
print(stdout.read().decode())

print("=== Gateway health ===")
stdin, stdout, stderr = ssh.exec_command("curl -s --max-time 5 http://localhost:8090/kb/actuator/health")
print(f"  {stdout.read().decode().strip()}")

print("=== Modules endpoint ===")
stdin, stdout, stderr = ssh.exec_command("curl -s --max-time 5 http://localhost:8090/kb/api/system/modules")
print(f"  {stdout.read().decode().strip()[:500]}")

print("=== Swagger UI ===")
stdin, stdout, stderr = ssh.exec_command("curl -s --max-time 5 -o /dev/null -w '%{http_code}' http://localhost:8090/kb/swagger-ui.html")
print(f"  HTTP {stdout.read().decode().strip()}")

ssh.close()
print("\nDone!")
