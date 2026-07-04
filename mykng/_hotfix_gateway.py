import paramiko
import time

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('120.26.66.182', port=3385, username='root', password='root', timeout=15)

# 上传新 jar
print("Uploading kb-gateway.jar...")
sftp = ssh.open_sftp()
sftp.put(
    r'd:\huliang\java\ideaworkspace\devtools\mykng\kb-gateway\target\kb-gateway.jar',
    '/root/kb-deploy/kb-gateway/target/kb-gateway.jar'
)
sftp.close()
print("  Uploaded!")

# 拷贝进容器并重启
print("Updating kb-gateway container...")
stdin, stdout, stderr = ssh.exec_command("docker cp /root/kb-deploy/kb-gateway/target/kb-gateway.jar kb-gateway:/app/kb-gateway.jar")
exit_code = stdout.channel.recv_exit_status()
if exit_code != 0:
    print(f"  cp failed: {stderr.read().decode()}")
else:
    print("  cp ok")
    stdin, stdout, stderr = ssh.exec_command("docker restart kb-gateway")
    exit_code = stdout.channel.recv_exit_status()
    if exit_code != 0:
        print(f"  restart failed: {stderr.read().decode()}")
    else:
        print("  restarted!")

# 等待启动
print("\nWaiting 60s for gateway to start...")
time.sleep(60)

# 验证
print("\n=== Gateway status ===")
stdin, stdout, stderr = ssh.exec_command("docker ps --format '{{.Names}} {{.Status}}' | grep kb-gateway")
print(f"  {stdout.read().decode().strip()}")

print("\n=== Swagger UI (with /kb prefix) ===")
stdin, stdout, stderr = ssh.exec_command("curl -s --max-time 5 -o /dev/null -w '%{http_code}' http://localhost:8090/kb/swagger-ui.html")
print(f"  /kb/swagger-ui.html: HTTP {stdout.read().decode().strip()}")

print("\n=== Swagger UI (without prefix) ===")
stdin, stdout, stderr = ssh.exec_command("curl -s --max-time 5 -o /dev/null -w '%{http_code}' http://localhost:8090/swagger-ui.html")
print(f"  /swagger-ui.html: HTTP {stdout.read().decode().strip()}")

print("\n=== Auth API Docs ===")
stdin, stdout, stderr = ssh.exec_command("curl -s --max-time 5 -o /dev/null -w '%{http_code}' http://localhost:8090/kb/api/auth/v3/api-docs")
print(f"  /kb/api/auth/v3/api-docs: HTTP {stdout.read().decode().strip()}")

print("\n=== Knowledge API Docs ===")
stdin, stdout, stderr = ssh.exec_command("curl -s --max-time 5 -o /dev/null -w '%{http_code}' http://localhost:8090/kb/api/knowledge/v3/api-docs")
print(f"  /kb/api/knowledge/v3/api-docs: HTTP {stdout.read().decode().strip()}")

print("\n=== Gateway swagger config ===")
stdin, stdout, stderr = ssh.exec_command("curl -s --max-time 5 http://localhost:8090/kb/v3/api-docs/swagger-config")
print(f"  {stdout.read().decode().strip()[:500]}")

ssh.close()
