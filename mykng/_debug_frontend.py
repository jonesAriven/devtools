import sys
sys.path.insert(0, '.')
from deploy_engine import SSHManager, Color
import yaml

with open('deploy-config.yml', 'r', encoding='utf-8') as f:
    config = yaml.safe_load(f)

ssh = SSHManager(config['server'])
ssh.connect()

print("=== 容器内目录结构 ===")
code, out, _ = ssh.exec_cmd('docker exec kb-web find /usr/share/nginx/html -type d | head -20')
print(out)

print("\n=== /usr/share/nginx/html/kb/s/ 下的文件 ===")
code, out, _ = ssh.exec_cmd('docker exec kb-web ls -la /usr/share/nginx/html/kb/s/ 2>&1')
print(out)

print("\n=== nginx 配置 ===")
code, out, _ = ssh.exec_cmd('docker exec kb-web cat /etc/nginx/conf.d/default.conf')
print(out)

print("\n=== 直接访问 index.html ===")
code, out, _ = ssh.exec_cmd('docker exec kb-web curl -s -o /dev/null -w "%{http_code}" http://localhost/kb/s/index.html')
print(f"/kb/s/index.html HTTP: {out.strip()}")

ssh.close()
