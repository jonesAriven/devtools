import paramiko
import time

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('120.26.66.182', port=3385, username='root', password='root', timeout=15)

# 先停止 kb-file 的重启循环
print("Stopping kb-file...")
stdin, stdout, stderr = ssh.exec_command("docker stop kb-file 2>&1")
print(f"  {stdout.read().decode().strip()}")

# 上传新 jar
print("Uploading kb-file.jar...")
sftp = ssh.open_sftp()
sftp.put(
    r'd:\huliang\java\ideaworkspace\devtools\mykng\kb-file\target\kb-file.jar',
    '/root/kb-deploy/kb-file/target/kb-file.jar'
)
sftp.close()
print("  Uploaded!")

# 拷贝进容器并启动
print("Updating kb-file container...")
stdin, stdout, stderr = ssh.exec_command("docker cp /root/kb-deploy/kb-file/target/kb-file.jar kb-file:/app/kb-file.jar")
exit_code = stdout.channel.recv_exit_status()
if exit_code != 0:
    print(f"  cp failed: {stderr.read().decode()}")
    ssh.close()
    exit(1)
print("  cp ok")

stdin, stdout, stderr = ssh.exec_command("docker start kb-file")
exit_code = stdout.channel.recv_exit_status()
if exit_code != 0:
    print(f"  start failed: {stderr.read().decode()}")
    ssh.close()
    exit(1)
print("  started!")

# 等待启动
print("\nWaiting 90s for kb-file to start...")
time.sleep(90)

# 验证
print("\n=== kb-file status ===")
stdin, stdout, stderr = ssh.exec_command("docker ps --format '{{.Names}} {{.Status}}' | grep kb-file")
print(f"  {stdout.read().decode().strip()}")

print("\n=== kb-file health ===")
stdin, stdout, stderr = ssh.exec_command("docker exec kb-file wget --spider -q http://localhost:8082/actuator/health 2>&1; echo \"exit=$?\"")
print(f"  {stdout.read().decode().strip()}")

print("\n=== File API Docs ===")
stdin, stdout, stderr = ssh.exec_command("curl -s --max-time 5 -o /dev/null -w '%{http_code}' http://localhost:8090/kb/api/file/v3/api-docs")
print(f"  HTTP {stdout.read().decode().strip()}")

print("\n=== Modules ===")
stdin, stdout, stderr = ssh.exec_command("curl -s --max-time 5 http://localhost:8090/kb/api/system/modules")
print(f"  {stdout.read().decode().strip()}")

ssh.close()
print("\nDone!")
