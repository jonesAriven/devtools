#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
kb-ops-web 前端部署脚本
参考 Portal 部署脚本风格
"""
import os
import sys
import stat
import paramiko

HOST = "192.168.31.105"
PORT = 22
USER = "root"
PASS = "root"

LOCAL_DIST = os.path.join(os.path.dirname(os.path.abspath(__file__)), "kb-ops-web", "dist")
REMOTE_DIR = "/var/www/kb-ops-web"
NGINX_CONF = "/etc/nginx/conf.d/kb.conf"

# kb-ops 后端配置
OPS_BACKEND = "http://127.0.0.1:8084"
OPS_CONTEXT = "/kb-ops"

# 腾讯云2号（main.marschat.online）
TX2_HOST = "100.110.114.16"
TX2_PASS = "Hwx@1120930"
TX2_KB_CONF = "/etc/nginx/sites-enabled/kb.marschat.online"
TX2_MAIN_CONF = "/etc/nginx/sites-enabled/main.marschat.online"


def ssh_connect():
    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    client.connect(HOST, port=PORT, username=USER, password=PASS, timeout=15)
    return client


def sftp_connect():
    transport = paramiko.Transport((HOST, PORT))
    transport.connect(username=USER, password=PASS)
    return paramiko.SFTPClient.from_transport(transport), transport


def run_remote(client, cmd, timeout=120):
    print(f"\n{'='*60}")
    print(f"[EXEC] {cmd}")
    print(f"{'='*60}")
    stdin, stdout, stderr = client.exec_command(cmd, timeout=timeout)
    out = stdout.read().decode("utf-8", errors="replace")
    err = stderr.read().decode("utf-8", errors="replace")
    exit_code = stdout.channel.recv_exit_status()
    if out:
        print(out, end="")
    if err:
        print(f"[STDERR] {err}", end="")
    print(f"[EXIT] {exit_code}")
    return exit_code, out, err


def mkdir_p(sftp, remote_dir):
    if remote_dir in ("/", "", "."):
        return
    try:
        sftp.stat(remote_dir)
    except FileNotFoundError:
        parent = os.path.dirname(remote_dir.rstrip("/"))
        if parent:
            mkdir_p(sftp, parent)
        try:
            sftp.mkdir(remote_dir)
            print(f"  [MKDIR] {remote_dir}")
        except OSError:
            pass


def upload_dir(sftp, local_dir, remote_dir):
    mkdir_p(sftp, remote_dir)
    for item in os.listdir(local_dir):
        local_path = os.path.join(local_dir, item)
        remote_path = remote_dir.rstrip("/") + "/" + item
        if os.path.isdir(local_path):
            upload_dir(sftp, local_path, remote_path)
        else:
            print(f"  [UPLOAD] {item} -> {remote_path}")
            sftp.put(local_path, remote_path)


def update_mykng_nginx(client):
    """更新 mykng-debain 上的 Nginx 配置"""
    print("\n" + "=" * 60)
    print("阶段：更新 mykng-debain Nginx 配置")
    print("=" * 60)

    # 读取现有配置
    sftp = client.open_sftp()
    with sftp.file(NGINX_CONF, "r") as f:
        content = f.read().decode("utf-8")

    # 检查是否已存在 /ops/ 配置
    if "location /ops/" in content:
        print("[INFO] /ops/ 配置已存在，跳过添加")
        sftp.close()
        return

    # 要插入的配置块
    ops_block = f"""
    # kb-ops 后端 API 代理
    location /ops-api/ {{
        proxy_pass {OPS_BACKEND}{OPS_CONTEXT}/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_connect_timeout 60s;
        proxy_send_timeout    300s;
        proxy_read_timeout    300s;
    }}

    # kb-ops 前端（运维管理平台）
    location /ops/ {{
        alias {REMOTE_DIR}/;
        index index.html;
        try_files $uri $uri/ /ops/index.html;
        access_log off;
    }}

"""

    # 在 "location /health" 之前插入
    marker = "    # 健康检查"
    if marker in content:
        new_content = content.replace(marker, ops_block + marker, 1)
    else:
        # 在最后一个 } 之前插入
        last_brace = content.rfind("}")
        new_content = content[:last_brace] + ops_block + content[last_brace:]

    # 写入配置
    with sftp.file(NGINX_CONF, "w") as f:
        f.write(new_content)
    sftp.close()

    print("[OK] 已添加 /ops/ 和 /ops-api/ 配置")

    # 验证配置
    print("\n验证 Nginx 配置...")
    run_remote(client, "nginx -t")

    # 重载 Nginx
    print("\n重载 Nginx...")
    run_remote(client, "nginx -s reload")


def update_tx2_nginx(client):
    """通过 mykng-debain 跳板，更新腾讯云2号 Nginx 配置"""
    print("\n" + "=" * 60)
    print("阶段：更新腾讯云2号 Nginx 配置（main.marschat.online）")
    print("=" * 60)

    # 检查 main.marschat.online 配置是否存在
    check_cmd = f"sshpass -p '{TX2_PASS}' ssh -o StrictHostKeyChecking=no root@{TX2_HOST} 'ls -la {TX2_MAIN_CONF} 2>/dev/null || echo NOT_FOUND'"
    exit_code, out, err = run_remote(client, check_cmd, timeout=30)

    if "NOT_FOUND" in out:
        print("[INFO] main.marschat.online 配置不存在，尝试更新 kb.marschat.online")
        conf_file = TX2_KB_CONF
    else:
        conf_file = TX2_MAIN_CONF

    print(f"[INFO] 使用配置文件: {conf_file}")

    # 检查是否已存在 /ops/ 配置
    check_ops_cmd = f"sshpass -p '{TX2_PASS}' ssh -o StrictHostKeyChecking=no root@{TX2_HOST} 'grep -q \"location /ops/\" {conf_file} && echo EXISTS || echo NOT_EXISTS'"
    exit_code, out, err = run_remote(client, check_ops_cmd, timeout=30)

    if "EXISTS" in out:
        print("[INFO] 腾讯云2号 /ops/ 配置已存在，跳过")
        return

    # 准备要插入的配置块
    ops_proxy_block = """
    # kb-ops 运维管理平台（反代到 mykng-debain）
    location /ops/ {
        proxy_pass http://100.93.36.113:80/ops/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_connect_timeout 5s;
        proxy_read_timeout 60s;
    }

    location /ops-api/ {
        proxy_pass http://100.93.36.113:80/ops-api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_connect_timeout 5s;
        proxy_read_timeout 60s;
    }
"""

    # 写配置块到跳板机临时文件
    sftp = client.open_sftp()
    with sftp.file("/tmp/ops_proxy_block.txt", "w") as f:
        f.write(ops_proxy_block)
    sftp.close()

    # 上传到腾讯云2号
    scp_cmd = f"sshpass -p '{TX2_PASS}' scp -o StrictHostKeyChecking=no /tmp/ops_proxy_block.txt root@{TX2_HOST}:/tmp/ops_proxy_block.txt"
    run_remote(client, scp_cmd, timeout=30)

    # 写 Python 插入脚本
    insert_py = f'''
import re
with open("{conf_file}") as f:
    content = f.read()
if "location /ops/" in content:
    print("ALREADY_EXISTS")
else:
    marker = "    location = / {{"
    if marker not in content:
        marker = "    # 根路径"
    if marker in content:
        with open("/tmp/ops_proxy_block.txt") as f:
            block = f.read()
        new_content = content.replace(marker, block + marker, 1)
        with open("{conf_file}", "w") as f:
            f.write(new_content)
        print("INSERT_OK")
    else:
        print("MARKER_NOT_FOUND")
        # 打印文件前30行帮助调试
        lines = content.split("\\n")[:30]
        for i, line in enumerate(lines):
            print(f"  {{i}}: {{line}}")
'''

    sftp = client.open_sftp()
    with sftp.file("/tmp/insert_ops_tx2.py", "w") as f:
        f.write(insert_py)
    sftp.close()

    # 上传并执行
    scp2_cmd = f"sshpass -p '{TX2_PASS}' scp -o StrictHostKeyChecking=no /tmp/insert_ops_tx2.py root@{TX2_HOST}:/tmp/insert_ops_tx2.py"
    run_remote(client, scp2_cmd, timeout=30)

    exec_cmd = f"sshpass -p '{TX2_PASS}' ssh -o StrictHostKeyChecking=no root@{TX2_HOST} 'python3 /tmp/insert_ops_tx2.py'"
    exit_code, out, err = run_remote(client, exec_cmd, timeout=30)

    if "INSERT_OK" in out or "ALREADY_EXISTS" in out:
        # 验证并 reload
        print("\n验证腾讯云2号 Nginx 配置...")
        nginx_t_cmd = f"sshpass -p '{TX2_PASS}' ssh -o StrictHostKeyChecking=no root@{TX2_HOST} 'nginx -t'"
        run_remote(client, nginx_t_cmd, timeout=30)

        print("\n重载腾讯云2号 Nginx...")
        reload_cmd = f"sshpass -p '{TX2_PASS}' ssh -o StrictHostKeyChecking=no root@{TX2_HOST} 'nginx -s reload'"
        run_remote(client, reload_cmd, timeout=30)
    else:
        print("[WARN] 插入配置可能失败，请检查")


def verify(client):
    """验证部署"""
    print("\n" + "=" * 60)
    print("阶段：验证部署")
    print("=" * 60)

    # 验证静态文件
    print("\n--- 验证静态文件 ---")
    run_remote(client, f"curl -s -o /dev/null -w '%{{http_code}}' http://localhost/ops/ && echo ' /ops/'", timeout=10)
    run_remote(client, f"curl -s -o /dev/null -w '%{{http_code}}' http://192.168.31.105/ops/ && echo ' /ops/ (内网IP)'", timeout=10)

    # 验证 API 代理
    print("\n--- 验证 API 代理 ---")
    run_remote(client, f"curl -s http://localhost/ops-api/actuator/health 2>/dev/null | head -c 200 || echo 'API_CHECK_FAILED'", timeout=10)

    # 验证远程访问
    print("\n--- 验证公网访问 ---")
    run_remote(client, f"curl -s -o /dev/null -w '%{{http_code}}' https://kb.marschat.online/ops/ && echo ' /ops/ (公网)'", timeout=15)


def main():
    print("=" * 60)
    print("kb-ops-web 前端部署")
    print("=" * 60)

    # 1. 检查本地构建产物
    print("\n[1/5] 检查本地构建产物...")
    index_html = os.path.join(LOCAL_DIST, "index.html")
    if not os.path.exists(index_html):
        print(f"[ERROR] 构建产物不存在: {index_html}")
        print("请先执行: cd kb-ops-web && pnpm build")
        sys.exit(1)
    print(f"[OK] 构建产物存在: {LOCAL_DIST}")

    # 2. 上传文件
    print("\n[2/5] 上传文件到 mykng-debain...")
    sftp, transport = sftp_connect()
    try:
        upload_dir(sftp, LOCAL_DIST, REMOTE_DIR)
        print("\n[OK] 上传完成")
        # 列出远程目录
        print(f"\n远程目录 {REMOTE_DIR} 内容:")
        for item in sftp.listdir(REMOTE_DIR):
            st = sftp.stat(f"{REMOTE_DIR}/{item}")
            kind = "DIR " if stat.S_ISDIR(st.st_mode) else "FILE"
            print(f"  {kind} {item} ({st.st_size} bytes)")
    finally:
        transport.close()

    # 3. SSH 连接
    print("\n[3/5] 连接服务器...")
    client = ssh_connect()
    try:
        # 4. 更新 mykng-debain Nginx
        print("\n[4/5] 更新 mykng-debain Nginx 配置...")
        update_mykng_nginx(client)

        # 5. 更新腾讯云2号 Nginx
        print("\n[5/5] 更新腾讯云2号 Nginx 配置...")
        update_tx2_nginx(client)

        # 验证
        verify(client)

    finally:
        client.close()

    print("\n" + "=" * 60)
    print("部署完成！")
    print(f"  内网地址: http://192.168.31.105/ops/")
    print(f"  Tailscale: http://100.93.36.113/ops/")
    print(f"  公网地址: https://kb.marschat.online/ops/")
    print("=" * 60)


if __name__ == "__main__":
    main()
