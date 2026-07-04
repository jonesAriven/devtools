import paramiko
import time

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('120.26.66.182', port=3385, username='root', password='root', timeout=15)

# 先看看当前有哪些 kb-* 容器在运行
print("=== Current running kb containers ===")
stdin, stdout, stderr = ssh.exec_command("docker ps --format '{{.Names}} {{.Status}} {{.Image}}' | grep '^kb-'")
print(stdout.read().decode())

# 方法：对于每个运行中的应用服务，直接把新 jar 拷贝进容器，然后 restart
# jar 包位置：/root/kb-deploy/{service}/target/{service}.jar
# 容器内位置：/app/{service}.jar
SERVICES = ["kb-gateway", "kb-auth", "kb-file", "kb-knowledge"]

print("\n=== Copy jar into containers & restart ===")
for svc in SERVICES:
    # 检查容器是否在运行
    stdin, stdout, stderr = ssh.exec_command(f"docker ps --format '{{{{.Names}}}}' | grep -x {svc}")
    running = stdout.read().decode().strip()
    if not running:
        print(f"  ⚠️  {svc} not running, skipping")
        continue
    
    print(f"  Updating {svc} ...", end=" ", flush=True)
    # 拷贝 jar 到容器
    stdin, stdout, stderr = ssh.exec_command(
        f"docker cp /root/kb-deploy/{svc}/target/{svc}.jar {svc}:/app/{svc}.jar"
    )
    exit_code = stdout.channel.recv_exit_status()
    if exit_code != 0:
        print(f"❌ cp failed: {stderr.read().decode()[:200]}")
        continue
    
    # 重启容器
    stdin, stdout, stderr = ssh.exec_command(f"docker restart {svc}")
    exit_code = stdout.channel.recv_exit_status()
    if exit_code != 0:
        print(f"❌ restart failed: {stderr.read().decode()[:200]}")
        continue
    
    print("✅")

# 等待启动
print("\nWaiting 60s for services to start...")
time.sleep(60)

# 验证
print("\n=== Status ===")
stdin, stdout, stderr = ssh.exec_command("docker ps --format 'table {{.Names}}\\t{{.Status}}' | grep -E 'NAMES|kb-'")
print(stdout.read().decode())

print("=== Gateway health ===")
stdin, stdout, stderr = ssh.exec_command("curl -s http://localhost:8090/kb/actuator/health")
print(f"  {stdout.read().decode().strip()}")

print("=== Swagger UI ===")
stdin, stdout, stderr = ssh.exec_command("curl -s -o /dev/null -w '%{http_code}' http://localhost:8090/kb/swagger-ui.html")
print(f"  HTTP {stdout.read().decode().strip()}")

ssh.close()
print("\nDone!")
