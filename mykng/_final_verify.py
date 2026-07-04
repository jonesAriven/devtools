import sys
sys.path.insert(0, '.')
from deploy_engine import SSHManager
import yaml

with open('deploy-config.yml', 'r', encoding='utf-8') as f:
    config = yaml.safe_load(f)

ssh = SSHManager(config['server'])
ssh.connect()

# kb-ops dashboard
print('=== kb-ops Dashboard API ===')
code, out, _ = ssh.exec_cmd('curl -s http://localhost:8084/kb-ops/ops/dashboard')
print(out.strip()[:600])

print('\n=== kb-ops 健康检查 ===')
code, out, _ = ssh.exec_cmd('curl -s http://localhost:8084/kb-ops/actuator/health')
print(out.strip()[:300])

# vaultwarden
print('\n=== Vaultwarden (密码管理) ===')
code, out, _ = ssh.exec_cmd('curl -s -o /dev/null -w "%{http_code}" http://localhost:8222/')
print("HTTP:", out.strip())

ssh.close()
