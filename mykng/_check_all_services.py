import sys
sys.path.insert(0, '.')
from deploy_engine import SSHManager
import yaml

with open('deploy-config.yml', 'r', encoding='utf-8') as f:
    config = yaml.safe_load(f)

ssh = SSHManager(config['server'])
ssh.connect()

print('=== 所有运行中的容器 ===')
code, out, _ = ssh.exec_cmd('docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"')
print(out)

print('\n=== /root 下的目录 ===')
code, out, _ = ssh.exec_cmd('ls -d /root/*/')
print(out)

print('\n=== 80 端口的 nginx 配置（看看有哪些域名/反代） ===')
code, out, _ = ssh.exec_cmd("docker ps --format '{{.Names}}' | grep -i nginx")
print("Nginx 容器:", out.strip() or "无")

# 检查是否有 frp 或看板相关的容器
print('\n=== 查找看板/frp 相关容器 ===')
code, out, _ = ssh.exec_cmd("docker ps -a --format '{{.Names}}' | grep -iE 'board|kanban|frp|task'")
print(out.strip() or "无")

ssh.close()
