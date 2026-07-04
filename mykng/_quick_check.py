import paramiko

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('120.26.66.182', port=3385, username='root', password='root', timeout=15)

print("=== All Services ===")
stdin, stdout, stderr = ssh.exec_command("docker ps --format 'table {{.Names}}\t{{.Status}}' | grep -E 'NAMES|kb-(gateway|auth|file|knowledge|intelligence)'")
print(stdout.read().decode())

print("=== Modules ===")
stdin, stdout, stderr = ssh.exec_command("curl -s --max-time 5 http://localhost:8090/kb/api/system/modules")
print(stdout.read().decode())

print("\n=== Swagger UI ===")
stdin, stdout, stderr = ssh.exec_command("curl -s --max-time 5 -o /dev/null -w '%{http_code}' http://localhost:8090/swagger-ui.html")
print(f"  HTTP {stdout.read().decode().strip()}")

print("\n=== All API Docs ===")
for svc in ['auth', 'file', 'knowledge', 'intelligence']:
    stdin, stdout, stderr = ssh.exec_command(f"curl -s --max-time 5 -o /dev/null -w '%{{http_code}}' http://localhost:8090/kb/api/{svc}/v3/api-docs")
    print(f"  kb-{svc}: HTTP {stdout.read().decode().strip()}")

ssh.close()
