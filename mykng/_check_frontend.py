import sys
sys.path.insert(0, '.')
from deploy_engine import SSHManager
import yaml

with open('deploy-config.yml', 'r', encoding='utf-8') as f:
    config = yaml.safe_load(f)

ssh = SSHManager(config['server'])
ssh.connect()

# 1. kb-ops 健康检查
print('=== kb-ops 健康检查 ===')
code, out, _ = ssh.exec_cmd('curl -s http://localhost:8084/actuator/health 2>&1 || echo "no health endpoint"')
print(out.strip()[:500])

# 2. kb-ops 首页
print('\n=== kb-ops 首页 ===')
code, out, _ = ssh.exec_cmd('curl -s -o /dev/null -w "%{http_code}" http://localhost:8084/')
print("HTTP 状态码:", out.strip())

code, out, _ = ssh.exec_cmd('curl -s http://localhost:8084/ | head -20')
print(out.strip()[:500])

# 3. 看看 kb-web 前端有没有部署
print('\n=== 检查 kb-web 前端 ===')
code, out, _ = ssh.exec_cmd("docker ps --format '{{.Names}}' | grep -iE 'web|front|nginx|kb-web'")
print("前端容器:", out.strip() or "无")

# 4. 网关后面有没有前端
print('\n=== 网关 /kb 路径响应 ===')
code, out, _ = ssh.exec_cmd('curl -s -o /dev/null -w "%{http_code}" http://localhost:8090/kb/')
print("/kb/ HTTP:", out.strip())

code, out, _ = ssh.exec_cmd('curl -s http://localhost:8090/kb/ | head -10')
print(out.strip()[:500])

# 5. 检查 Nacos 里注册了哪些服务
print('\n=== Nacos 服务列表 ===')
code, out, _ = ssh.exec_cmd('curl -s "http://localhost:8848/nacos/v1/ns/catalog/services?withInstances=false&pageNo=1&pageSize=50&serviceNameParam=&groupNameParam=" 2>&1 | head -100')
print(out.strip()[:800])

ssh.close()
