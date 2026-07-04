#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
M6-doc 部署脚本：上传 jar + docker-compose + 前端，然后重建容器
"""
import os
import sys
import paramiko

HOST = "120.26.66.182"
PORT = 3385
USER = "root"
PASSWORD = "root"

LOCAL_BASE = r"d:\huliang\java\ideaworkspace\devtools\mykng"
REMOTE_BASE = "/root/devtools/mykng"

SERVICES = ["kb-gateway", "kb-auth", "kb-file", "kb-knowledge", "kb-intelligence"]


def upload_file(sftp, local_path, remote_path, ssh):
    """上传单个文件，自动创建远程目录"""
    remote_dir = os.path.dirname(remote_path).replace("\\", "/")
    stdin, stdout, stderr = ssh.exec_command(f"mkdir -p '{remote_dir}'")
    stdout.channel.recv_exit_status()
    sftp.put(local_path, remote_path)
    print(f"  ✅ {os.path.basename(local_path)} -> {remote_path}")


def main():
    print("=" * 60)
    print("  mykng M6-doc 部署脚本")
    print("=" * 60)

    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    print(f"\n📡 连接 {HOST}:{PORT} ...")
    ssh.connect(HOST, port=PORT, username=USER, password=PASSWORD, timeout=15)
    sftp = ssh.open_sftp()
    print("✅ 连接成功\n")

    # 1. 上传 jar 包
    print("📦 [1/4] 上传服务 jar 包...")
    for svc in SERVICES:
        local_jar = os.path.join(LOCAL_BASE, svc, "target", f"{svc}.jar")
        remote_jar = f"{REMOTE_BASE}/{svc}/target/{svc}.jar"
        upload_file(sftp, local_jar, remote_jar, ssh)
    print("")

    # 2. 上传 docker-compose.yml
    print("📦 [2/4] 上传 docker-compose.yml...")
    local_compose = os.path.join(LOCAL_BASE, "docker-compose.yml")
    remote_compose = f"{REMOTE_BASE}/docker-compose.yml"
    upload_file(sftp, local_compose, remote_compose, ssh)
    print("")

    # 3. 上传前端 dist
    print("📦 [3/4] 上传前端 dist（kb-web）...")
    local_dist = os.path.join(LOCAL_BASE, "kb-web", "dist")
    remote_dist = f"{REMOTE_BASE}/kb-web/dist"
    # 先清空远程 dist
    stdin, stdout, stderr = ssh.exec_command(f"rm -rf {remote_dist} && mkdir -p {remote_dist}")
    stdout.channel.recv_exit_status()

    for root, dirs, files in os.walk(local_dist):
        for f in files:
            local_file = os.path.join(root, f)
            rel_path = os.path.relpath(local_file, local_dist).replace("\\", "/")
            remote_file = f"{remote_dist}/{rel_path}"
            upload_file(sftp, local_file, remote_file, ssh)
    print("")

    sftp.close()

    # 4. 远程 docker compose build + restart
    print("🔄 [4/4] 远程重建容器...")
    cmd = f"cd {REMOTE_BASE} && docker compose -p kb-deploy build {' '.join(SERVICES)} && docker compose -p kb-deploy up -d {' '.join(SERVICES)}"
    print(f"  执行: {cmd}")
    stdin, stdout, stderr = ssh.exec_command(cmd, get_pty=True)
    # 实时输出
    import time
    for line in iter(stdout.readline, ""):
        print(line.rstrip())
    exit_code = stdout.channel.recv_exit_status()

    if exit_code != 0:
        print(f"\n❌ 部署失败，exit code: {exit_code}")
        ssh.close()
        sys.exit(1)

    print("\n⏳ 等待服务启动（30s）...")
    import time
    time.sleep(30)

    # 5. 健康检查
    print("\n🏥 健康检查...")
    health_cmd = f"cd {REMOTE_BASE} && docker compose -p kb-deploy ps"
    stdin, stdout, stderr = ssh.exec_command(health_cmd)
    print(stdout.read().decode())

    ssh.close()
    print("\n🎉 部署完成！")


if __name__ == "__main__":
    main()
