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

# 1. 创建 nginx 配置文件
Color.step("创建 nginx 配置")
nginx_conf = """
server {
    listen 80;
    server_name _;

    # gzip 压缩
    gzip on;
    gzip_types text/plain text/css application/json application/javascript text/xml application/xml application/xml+rss text/javascript;
    gzip_min_length 1k;

    # 健康检查
    location /health {
        access_log off;
        return 200 '{"status":"ok"}';
        add_header Content-Type application/json;
    }

    # 静态资源（/kb/s/ 是 base 路径）
    location /kb/s/ {
        alias /usr/share/nginx/html/kb/s/;
        expires 30d;
        add_header Cache-Control "public, immutable";
        try_files $uri =404;
    }

    # SPA 路由回退 - /kb/ 开头的都回退到 index.html
    location /kb/ {
        alias /usr/share/nginx/html/kb/s/;
        index index.html;
        try_files $uri $uri/ /kb/s/index.html;
    }

    # 根路径重定向到 /kb/
    location = / {
        return 302 /kb/;
    }
}
"""

# 写入临时文件并上传
tmp_conf = os.path.join(tempfile.gettempdir(), 'kb-web-nginx.conf')
with open(tmp_conf, 'w', encoding='utf-8') as f:
    f.write(nginx_conf)
Color.info(f"Nginx 配置已写入 {tmp_conf}")

# 2. 打包 dist 目录
Color.step("打包前端静态资源")
dist_path = Path(__file__).parent / 'kb-web' / 'dist'
tar_path = os.path.join(tempfile.gettempdir(), 'kb-web-dist.tar.gz')
with tarfile.open(tar_path, 'w:gz') as tar:
    tar.add(str(dist_path), arcname='kb/s')
Color.info(f"打包完成: {tar_path} ({os.path.getsize(tar_path)/1024/1024:.1f} MB)")

# 3. 上传到服务器
Color.step("上传到服务器")
remote_dir = '/root/kb-deploy/kb-web'
ssh.exec_cmd(f'mkdir -p {remote_dir}/conf.d', timeout=10)
ssh.upload_file(tmp_conf, f'{remote_dir}/conf.d/default.conf')
ssh.upload_file(tar_path, f'{remote_dir}/dist.tar.gz')

# 4. 解压
Color.step("解压静态资源")
ssh.exec_cmd(f'mkdir -p {remote_dir}/html/kb && cd {remote_dir}/html/kb && tar xzf {remote_dir}/dist.tar.gz', timeout=30)
code, out, _ = ssh.exec_cmd(f'ls -la {remote_dir}/html/kb/s/')
Color.ok("解压完成，文件列表:")
print(out.strip()[:500])

# 5. 启动 nginx 容器
Color.step("启动 kb-web 容器")
# 先停止旧的
ssh.exec_cmd('docker rm -f kb-web 2>/dev/null || true', timeout=10)

cmd = f"""docker run -d \\
  --name kb-web \\
  -p 8091:80 \\
  -v {remote_dir}/conf.d:/etc/nginx/conf.d:ro \\
  -v {remote_dir}/html:/usr/share/nginx/html:ro \\
  --restart unless-stopped \\
  nginx:alpine
"""

code, out, err = ssh.exec_cmd(cmd, timeout=30)
if code != 0:
    Color.fail(f"启动失败: {err}")
    # 试试 network 名对不对
    code2, out2, _ = ssh.exec_cmd('docker network ls')
    print("可用网络:")
    print(out2)
    sys.exit(1)

Color.ok("容器已启动")

# 6. 等一下检查状态
import time
time.sleep(3)
code, out, _ = ssh.exec_cmd('docker ps --format "{{.Names}} {{.Status}}" | grep kb-web')
print(out.strip())

# 7. 测试容器内访问
code, out, _ = ssh.exec_cmd('docker exec kb-web curl -s -o /dev/null -w "%{http_code}" http://localhost/health')
print(f"健康检查 HTTP: {out.strip()}")

code, out, _ = ssh.exec_cmd('docker exec kb-web curl -s -o /dev/null -w "%{http_code}" http://localhost/kb/')
print(f"/kb/ HTTP: {out.strip()}")

code, out, _ = ssh.exec_cmd('docker exec kb-web curl -s http://localhost/kb/ | head -5')
print(out.strip()[:300])

ssh.close()
Color.ok("前端部署完成！")
