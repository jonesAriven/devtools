import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).parent))
from deploy_engine import SSHManager, Color
import yaml

with open('deploy-config.yml', 'r', encoding='utf-8') as f:
    config = yaml.safe_load(f)

ssh = SSHManager(config['server'])
ssh.connect()

print("=== kb-intelligence 状态 ===")
code, out, _ = ssh.exec_cmd("docker ps --format '{{.Names}} {{.Status}}' | grep kb-intelligence")
print(out.strip())

print("\n=== 启动时间 ===")
code, out, _ = ssh.exec_cmd("docker logs kb-intelligence 2>&1 | grep -E 'Started Kb|JVM running' | tail -3")
print(out.strip())

print("\n=== 最后 10 行日志 ===")
code, out, _ = ssh.exec_cmd("docker logs kb-intelligence --tail 10 2>&1")
print(out.strip())

print("\n=== 健康检查 ===")
code, out, _ = ssh.exec_cmd("docker inspect kb-intelligence --format '{{json .State.Health.Status}}'")
print(out.strip())

ssh.close()
