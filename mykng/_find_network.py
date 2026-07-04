import sys
sys.path.insert(0, '.')
from deploy_engine import SSHManager, Color
import yaml

with open('deploy-config.yml', 'r', encoding='utf-8') as f:
    config = yaml.safe_load(f)

ssh = SSHManager(config['server'])
ssh.connect()

# 1. 找到网关所在的 network
Color.step("查找网关所在网络")
code, out, _ = ssh.exec_cmd("docker inspect kb-gateway --format '{{json .NetworkSettings.Networks}}' | python3 -m json.tool 2>/dev/null || docker inspect kb-gateway | grep -A5 Networks")
print(out.strip()[:500])

# 更直接的方式：列出所有网络，找有 kb-gateway 的
code, out, _ = ssh.exec_cmd("for n in $(docker network ls -q); do echo -n \"$n: \"; docker network inspect $n --format '{{.Name}}: {{range .Containers}}{{.Name}} {{end}}'; done | grep -i kb-gateway")
print("\n包含 kb-gateway 的网络:")
print(out.strip())

ssh.close()
