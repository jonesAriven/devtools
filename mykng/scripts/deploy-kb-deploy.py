import paramiko
import os
import sys

HOST = "120.26.66.182"
PORT = 3385
USER = "root"
PASSWORD = "root"

LOCAL_BASE = r"d:\huliang\java\ideaworkspace\devtools\mykng"
REMOTE_BASE = "/root/kb-deploy"

SERVICES = ["kb-gateway", "kb-auth", "kb-file", "kb-knowledge", "kb-intelligence"]


def upload_file(sftp, local_path, remote_path, ssh):
    remote_dir = os.path.dirname(remote_path).replace("\\", "/")
    stdin, stdout, stderr = ssh.exec_command(f"mkdir -p '{remote_dir}'")
    stdout.channel.recv_exit_status()
    sftp.put(local_path, remote_path)
    print(f"  ✅ {os.path.basename(local_path)} -> {remote_path}")


def main():
    print("=" * 60)
    print("  部署到 /root/kb-deploy/ (kb-deploy compose project)")
    print("=" * 60)

    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    print(f"\n📡 连接 {HOST}:{PORT} ...")
    ssh.connect(HOST, port=PORT, username=USER, password=PASSWORD, timeout=15)
    sftp = ssh.open_sftp()
    print("✅ 连接成功\n")

    # 1. 上传 jar 包
    print("📦 [1/3] 上传服务 jar 包到 kb-deploy...")
    for svc in SERVICES:
        local_jar = os.path.join(LOCAL_BASE, svc, "target", f"{svc}.jar")
        remote_jar = f"{REMOTE_BASE}/{svc}/{svc}.jar"
        upload_file(sftp, local_jar, remote_jar, ssh)
    print("")

    sftp.close()

    # 2. Build 镜像
    print("🏗️ [2/3] Build 镜像...")
    cmd = f"cd {REMOTE_BASE} && docker compose build {' '.join(SERVICES)} 2>&1"
    print(f"  执行: docker compose build")
    stdin, stdout, stderr = ssh.exec_command(cmd)
    output = stdout.read().decode()
    err = stderr.read().decode()
    # 只看最后部分
    lines = output.strip().split('\n')
    for line in lines[-15:]:
        print(f"  {line}")
    exit_code = stdout.channel.recv_exit_status()
    if exit_code != 0:
        print(f"\n❌ BUILD FAILED (exit={exit_code})")
        print(err[-1000:])
        ssh.close()
        sys.exit(1)
    print("  ✅ Build 成功\n")

    # 3. 重启服务
    print("🔄 [3/3] 重启服务...")
    cmd = f"cd {REMOTE_BASE} && docker compose up -d {' '.join(SERVICES)} 2>&1"
    stdin, stdout, stderr = ssh.exec_command(cmd)
    print(stdout.read().decode())
    print(stderr.read())

    # 等一下让服务启动
    print("⏳ 等待服务启动（20s）...")
    import time
    time.sleep(20)

    # 状态
    print("\n🏥 容器状态：")
    stdin, stdout, stderr = ssh.exec_command("docker ps --format 'table {{.Names}}\t{{.Status}}' | grep -E 'NAMES|kb-'")
    print(stdout.read().decode())

    ssh.close()
    print("\n🎉 部署完成！")


if __name__ == "__main__":
    main()
