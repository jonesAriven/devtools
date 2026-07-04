import sys
sys.path.insert(0, '.')
from deploy_engine import SSHManager, Color
import yaml

with open('deploy-config.yml', 'r', encoding='utf-8') as f:
    config = yaml.safe_load(f)

ssh = SSHManager(config['server'])
ssh.connect()

print("=== 通过网关访问前端 ===")

# 1. 前端首页
print("\n1. /kb/ (前端首页)")
code, out, _ = ssh.exec_cmd('curl -s -o /dev/null -w "HTTP %{http_code}" http://localhost:8090/kb/')
print(f"   {out.strip()}")

# 2. 静态资源
print("\n2. /kb/s/index.html (静态页面)")
code, out, _ = ssh.exec_cmd('curl -s -o /dev/null -w "HTTP %{http_code}" http://localhost:8090/kb/s/index.html')
print(f"   {out.strip()}")

# 3. SPA 路由回退
print("\n3. /kb/dashboard (SPA路由)")
code, out, _ = ssh.exec_cmd('curl -s -o /dev/null -w "HTTP %{http_code}" http://localhost:8090/kb/dashboard')
print(f"   {out.strip()}")

# 4. API 仍然正常
print("\n4. /kb/api/system/modules (模块API)")
code, out, _ = ssh.exec_cmd('curl -s -o /dev/null -w "HTTP %{http_code}" http://localhost:8090/kb/api/system/modules')
print(f"   {out.strip()}")

# 5. Swagger UI
print("\n5. /swagger-ui.html (Swagger)")
code, out, _ = ssh.exec_cmd('curl -s -o /dev/null -w "HTTP %{http_code}" -L http://localhost:8090/swagger-ui.html')
print(f"   {out.strip()}")

# 6. 直接访问 kb-web:8091
print("\n6. 直接访问 kb-web:8091")
code, out, _ = ssh.exec_cmd('curl -s -o /dev/null -w "HTTP %{http_code}" http://localhost:8091/kb/')
print(f"   {out.strip()}")

# 7. 前端首页内容
print("\n7. 前端首页内容(前10行)")
code, out, _ = ssh.exec_cmd('curl -s http://localhost:8090/kb/ | head -10')
print(out.strip()[:500])

ssh.close()
