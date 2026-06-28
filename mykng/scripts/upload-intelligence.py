#!/usr/bin/env python3
"""Recursively upload kb-intelligence/src/main to mykng via SFTP."""
import os
import paramiko
import posixpath

HOST = "100.93.36.113"
USER = "root"
PASSWORD = "root"
LOCAL_BASE = r"d:\huliang\java\ideaworkspace\devtools\mykng\kb-intelligence\src\main"
REMOTE_BASE = "/root/devtools/mykng/kb-intelligence/src/main"


def ensure_remote_dir(sftp, remote_path):
    if remote_path in ("/", "", "."):
        return
    try:
        sftp.stat(remote_path)
    except FileNotFoundError:
        parent = posixpath.dirname(remote_path)
        ensure_remote_dir(sftp, parent)
        sftp.mkdir(remote_path)


def upload_dir(sftp, local_dir, remote_dir):
    count = 0
    for item in os.listdir(local_dir):
        local_path = os.path.join(local_dir, item)
        remote_path = posixpath.join(remote_dir, item)
        if os.path.isdir(local_path):
            ensure_remote_dir(sftp, remote_path)
            count += upload_dir(sftp, local_path, remote_path)
        else:
            ensure_remote_dir(sftp, posixpath.dirname(remote_path))
            sftp.put(local_path, remote_path)
            count += 1
            print(f"[OK] {os.path.relpath(local_path, LOCAL_BASE)}")
    return count


ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect(HOST, username=USER, password=PASSWORD, timeout=15)
sftp = ssh.open_sftp()

ensure_remote_dir(sftp, REMOTE_BASE)
total = upload_dir(sftp, LOCAL_BASE, REMOTE_BASE)

sftp.close()
ssh.close()
print(f"\n=== Uploaded {total} files to kb-intelligence/src/main ===")
