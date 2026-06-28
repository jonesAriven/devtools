#!/usr/bin/env python3
"""Upload kb-parent/pom.xml + kb-knowledge test files + DocServiceImpl to mykng."""
import os
import paramiko

HOST = "100.93.36.113"
USER = "root"
PASSWORD = "root"

# 本地 -> 远程 映射
LOCAL_BASE = r"d:\huliang\java\ideaworkspace\devtools\mykng"
REMOTE_BASE = "/root/devtools/mykng"

FILES = [
    # 1. kb-parent/pom.xml（含 failsafe 全局声明）
    (r"kb-parent\pom.xml", f"{REMOTE_BASE}/kb-parent/pom.xml"),
    # 2. kb-knowledge 单元测试代码（确保最新）
    (r"kb-knowledge\src\test\java\com\kb\knowledge\DocServiceImplTest.java",
     f"{REMOTE_BASE}/kb-knowledge/src/test/java/com/kb/knowledge/DocServiceImplTest.java"),
    (r"kb-knowledge\src\test\java\com\kb\knowledge\FolderServiceImplTest.java",
     f"{REMOTE_BASE}/kb-knowledge/src/test/java/com/kb/knowledge/FolderServiceImplTest.java"),
    # 3. kb-auth 集成测试代码
    (r"kb-auth\src\test\java\com\kb\auth\AuthIT.java",
     f"{REMOTE_BASE}/kb-auth/src/test/java/com/kb/auth/AuthIT.java"),
    (r"kb-auth\src\test\java\com\kb\auth\CucumberIT.java",
     f"{REMOTE_BASE}/kb-auth/src/test/java/com/kb/auth/CucumberIT.java"),
    # 4. DocServiceImpl 主源码（确认同步）
    (r"kb-knowledge\src\main\java\com\kb\knowledge\service\impl\DocServiceImpl.java",
     f"{REMOTE_BASE}/kb-knowledge/src/main/java/com/kb/knowledge/service/impl/DocServiceImpl.java"),
    (r"kb-knowledge\src\main\java\com\kb\knowledge\service\impl\FolderServiceImpl.java",
     f"{REMOTE_BASE}/kb-knowledge/src/main/java/com/kb/knowledge/service/impl/FolderServiceImpl.java"),
]

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect(HOST, username=USER, password=PASSWORD, timeout=15)
sftp = ssh.open_sftp()

ok = 0
fail = 0
for local_rel, remote in FILES:
    local = os.path.join(LOCAL_BASE, local_rel)
    try:
        # 确保远程目录存在
        remote_dir = os.path.dirname(remote).replace("\\", "/")
        # 用 mkdir -p 通过 exec_command 创建目录
        ssh.exec_command(f"mkdir -p '{remote_dir}'")[1].channel.recv_exit_status()
        sftp.put(local, remote)
        ok += 1
        print(f"OK  {local_rel} -> {remote}")
    except Exception as e:
        fail += 1
        print(f"FAIL {local_rel}: {e}")

sftp.close()
ssh.close()
print(f"\nResult: {ok} ok, {fail} failed")
