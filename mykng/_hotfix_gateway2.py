import paramiko
import time

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('120.26.66.182', port=3385, username='root', password='root', timeout=15)

print("Uploading kb-gateway.jar...")
sftp = ssh.open_sftp()
sftp.put(
    r'd:\huliang\java\ideaworkspace\devtools\mykng\kb-gateway\target\kb-gateway.jar',
    '/root/kb-deploy/kb-gateway/target/kb-gateway.jar'
)
sftp.close()
print("  Uploaded!")

print("Updating kb-gateway container...")
stdin, stdout, stderr = ssh.exec_command("docker cp /root/kb-deploy/kb-gateway/target/kb-gateway.jar kb-gateway:/app/kb-gateway.jar")
exit_code = stdout.channel.recv_exit_status()
if exit_code != 0:
    print(f"  cp failed: {stderr.read().decode()}")
    ssh.close()
    exit(1)
print("  cp ok")

stdin, stdout, stderr = ssh.exec_command("docker restart kb-gateway")
exit_code = stdout.channel.recv_exit_status()
if exit_code != 0:
    print(f"  restart failed: {stderr.read().decode()}")
    ssh.close()
    exit(1)
print("  restarted!")

print("\nWaiting 60s for gateway to start...")
time.sleep(60)

# 验证
print("\n=== Gateway status ===")
stdin, stdout, stderr = ssh.exec_command("docker ps --format '{{.Names}} {{.Status}}' | grep kb-gateway")
print(f"  {stdout.read().decode().strip()}")

print("\n=== API Docs tests ===")
paths = [
    "/kb/api/auth/v3/api-docs",
    "/kb/api/file/v3/api-docs",
    "/kb/api/knowledge/v3/api-docs",
    "/kb/api/intelligence/v3/api-docs",
]
for p in paths:
    stdin, stdout, stderr = ssh.exec_command(f"curl -s --max-time 5 -o /dev/null -w '%{{http_code}}' http://localhost:8090{p}")
    code = stdout.read().decode().strip()
    print(f"  {p}: HTTP {code}")

print("\n=== Swagger UI ===")
stdin, stdout, stderr = ssh.exec_command("curl -s --max-time 5 -o /dev/null -w '%{http_code}' http://localhost:8090/swagger-ui.html")
print(f"  /swagger-ui.html: HTTP {stdout.read().decode().strip()}")

ssh.close()
print("\nDone!")
