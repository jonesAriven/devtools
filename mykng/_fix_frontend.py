import sys
sys.path.insert(0, '.')
from deploy_engine import SSHManager, Color
import yaml
import os
import tarfile
import tempfile
from pathlib import Path

with open('deploy-config.yml', 'r', encoding='utf-8') as f:
    config = yaml.safe_load(f)

ssh = SSHManager(config['server'])
ssh.connect()

# 1. 重新打包（arcname 用 kb/s，解压到 html 根目录）
Color.step("重新打包前端静态资源")
dist_path = Path(__file__).parent / 'kb-web' / 'dist'
tar_path = os.path.join(tempfile.gettempdir(), 'kb-web-dist.tar.gz')
with tarfile.open(tar_path, 'w:gz') as tar:
    tar.add(str(dist_path), arcname='kb/s')
Color.info(f"打包完成: {tar_path} ({os.path.getsize(tar_path)/1024/1024:.1f} MB)")

# 2. 上传
remote_dir = '/root/kb-deploy/kb-web'
ssh.upload_file(tar_path, f'{remote_dir}/dist.tar.gz')

# 3. 清空旧的 html 目录，重新解压
Color.step("重新解压")
ssh.exec_cmd(f'rm -rf {remote_dir}/html/* && cd {remote_dir}/html && tar xzf {remote_dir}/dist.tar.gz', timeout=30)

# 4. 检查目录结构
code, out, _ = ssh.exec_cmd(f'docker exec kb-web find /usr/share/nginx/html -type f | head -10')
print("文件结构:")
print(out.strip())

# 5. 重载 nginx（其实直接重启容器更简单）
Color.step("重启容器")
ssh.exec_cmd('docker restart kb-web', timeout=15)
import time
time.sleep(3)

# 6. 测试
print("\n=== 测试访问 ===")
code, out, _ = ssh.exec_cmd('curl -s -o /dev/null -w "%{http_code}" http://localhost:8091/health')
print(f"/health: {out.strip()}")

code, out, _ = ssh.exec_cmd('curl -s -o /dev/null -w "%{http_code}" http://localhost:8091/kb/')
print(f"/kb/: {out.strip()}")

code, out, _ = ssh.exec_cmd('curl -s http://localhost:8091/kb/ | head -5')
print(out.strip()[:300])

code, out, _ = ssh.exec_cmd('curl -s -o /dev/null -w "%{http_code}" http://localhost:8091/kb/s/index.html')
print(f"/kb/s/index.html: {out.strip()}")

# 测试 SPA 路由回退
code, out, _ = ssh.exec_cmd('curl -s -o /dev/null -w "%{http_code}" http://localhost:8091/kb/dashboard')
print(f"/kb/dashboard (SPA路由): {out.strip()}")

ssh.close()
Color.ok("前端部署完成！")
