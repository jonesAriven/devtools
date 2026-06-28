#!/usr/bin/env python3
"""Upload all pom.xml files to mykng via SFTP."""
import os
import paramiko
import posixpath

HOST = "100.93.36.113"
USER = "root"
PASSWORD = "root"
LOCAL_BASE = r"d:\huliang\java\ideaworkspace\devtools\mykng"
REMOTE_BASE = "/root/devtools/mykng"

POM_FILES = [
    "kb-parent/pom.xml",
    "kb-common/pom.xml",
    "kb-file/pom.xml",
    "kb-intelligence/pom.xml",
    "kb-ops/pom.xml",
    "kb-knowledge/pom.xml",
    "kb-auth/pom.xml",
    "kb-gateway/pom.xml",
]

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect(HOST, username=USER, password=PASSWORD, timeout=15)
sftp = ssh.open_sftp()

success = 0
failed = 0
for rel_path in POM_FILES:
    local_path = os.path.join(LOCAL_BASE, rel_path.replace("/", os.sep))
    remote_path = posixpath.join(REMOTE_BASE, rel_path)
    if not os.path.exists(local_path):
        print(f"[SKIP] {rel_path} (local not found)")
        failed += 1
        continue
    try:
        sftp.put(local_path, remote_path)
        print(f"[OK]   {rel_path}")
        success += 1
    except Exception as e:
        print(f"[FAIL] {rel_path} -> {e}")
        failed += 1

sftp.close()
ssh.close()
print(f"\n=== Upload Summary: {success} ok, {failed} failed ===")
