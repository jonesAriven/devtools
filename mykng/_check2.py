import paramiko

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('120.26.66.182', port=3385, username='root', password='root', timeout=15)

stdin, stdout, stderr = ssh.exec_command("docker ps -a --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'")
lines = [l for l in stdout.read().decode().strip().split('\n') if 'kb-' in l or 'NAMES' in l]
print("=== All kb containers ===")
for l in lines:
    print(f"  {l}")

print("\n=== Gateway health ===")
stdin, stdout, stderr = ssh.exec_command("curl -s --max-time 5 http://localhost:8090/kb/actuator/health")
print(f"  {stdout.read().decode().strip()}")

print("\n=== Swagger UI ===")
stdin, stdout, stderr = ssh.exec_command("curl -s --max-time 5 -o /dev/null -w '%{http_code}' http://localhost:8090/kb/swagger-ui.html")
print(f"  HTTP {stdout.read().decode().strip()}")

print("\n=== kb-gateway logs (last 10) ===")
stdin, stdout, stderr = ssh.exec_command("docker logs kb-gateway --tail 10 2>&1")
print(f"  {stdout.read().decode().strip()[:500]}")

print("\n=== kb-auth health ===")
stdin, stdout, stderr = ssh.exec_command("docker exec kb-auth wget --spider -q http://localhost:8081/actuator/health 2>&1; echo \"exit=$?\"")
print(f"  {stdout.read().decode().strip()}")

ssh.close()
