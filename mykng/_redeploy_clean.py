import paramiko

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('120.26.66.182', port=3385, username='root', password='root', timeout=15)

# 1. 先停掉并删除 kb-deploy 的所有应用服务容器（保留基础设施）
APPS = "kb-gateway kb-auth kb-file kb-knowledge kb-intelligence"
print("=== Stop & remove app containers ===")
stdin, stdout, stderr = ssh.exec_command(f"cd /root/kb-deploy && docker compose rm -sf {APPS} 2>&1")
print(stdout.read().decode())
print(stderr.read()[:500])

# 2. 清理残留的孤儿容器
print("\n=== Cleanup orphan containers ===")
stdin, stdout, stderr = ssh.exec_command("docker ps -a --format '{{.Names}}' | grep -E '^[a-f0-9]+_kb-' | xargs -r docker rm -f 2>&1")
print(stdout.read().decode())

# 3. 重新启动应用服务（用新镜像）
print("\n=== Up app services with new images ===")
stdin, stdout, stderr = ssh.exec_command(f"cd /root/kb-deploy && docker compose up -d {APPS} 2>&1")
print(stdout.read().decode())
print(stderr.read()[:1000])

# 4. 等待
print("\nWaiting 60s for services to start...")
import time
time.sleep(60)

# 5. 状态
print("\n=== Status ===")
stdin, stdout, stderr = ssh.exec_command("docker ps --format 'table {{.Names}}\\t{{.Status}}\\t{{.Image}}' | grep -E 'NAMES|kb-'")
print(stdout.read().decode())

ssh.close()
print("\nDone!")
