import sys
sys.path.insert(0, '.')
from deploy_engine import SSHManager, Color
import yaml

with open('deploy-config.yml', 'r', encoding='utf-8') as f:
    config = yaml.safe_load(f)

ssh = SSHManager(config['server'])
ssh.connect()

# 1. 把 kb-web 加入网关所在网络
Color.step("将 kb-web 加入 kb-deploy_kb-net 网络")
ssh.exec_cmd('docker network connect kb-deploy_kb-net kb-web', timeout=10)
code, out, _ = ssh.exec_cmd('docker network inspect kb-deploy_kb-net --format "{{range .Containers}}{{.Name}} {{end}}"')
print("网络内容器:", out.strip())

# 2. 测试网关能否访问 kb-web
Color.step("测试网关容器内访问 kb-web")
code, out, _ = ssh.exec_cmd('docker exec kb-gateway curl -s -o /dev/null -w "%{http_code}" http://kb-web/health')
print(f"kb-web/health from gateway: {out.strip()}")

code, out, _ = ssh.exec_cmd('docker exec kb-gateway curl -s -o /dev/null -w "%{http_code}" http://kb-web/kb/')
print(f"kb-web/kb/ from gateway: {out.strip()}")

ssh.close()
Color.ok("网络配置完成！")
