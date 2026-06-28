#!/usr/bin/env python3
# Upload *IT.java and .feature files to mykng via SFTP
import os
import paramiko
import posixpath

HOST = "100.93.36.113"
USER = "root"
PASSWORD = "root"
LOCAL_BASE = r"d:\huliang\java\ideaworkspace\devtools\mykng"
REMOTE_BASE = "/root/devtools/mykng"

# Files to upload (relative paths from base)
FILES = [
    # IT files (11)
    "kb-auth/src/test/java/com/kb/auth/AuthIT.java",
    "kb-auth/src/test/java/com/kb/auth/CucumberIT.java",
    "kb-file/src/test/java/com/kb/file/CucumberIT.java",
    "kb-file/src/test/java/com/kb/file/FileIT.java",
    "kb-gateway/src/test/java/com/kb/gateway/KbGatewayIT.java",
    "kb-intelligence/src/test/java/com/kb/intelligence/CucumberIT.java",
    "kb-intelligence/src/test/java/com/kb/intelligence/KbIntelligenceIT.java",
    "kb-knowledge/src/test/java/com/kb/knowledge/CucumberIT.java",
    "kb-knowledge/src/test/java/com/kb/knowledge/KnowledgeIT.java",
    "kb-ops/src/test/java/com/kb/ops/CucumberIT.java",
    "kb-ops/src/test/java/com/kb/ops/OpsIT.java",
    # Feature files (6)
    "kb-auth/src/test/resources/features/auth.feature",
    "kb-file/src/test/resources/features/file_upload_search.feature",
    "kb-knowledge/src/test/resources/features/share_access.feature",
    "kb-knowledge/src/test/resources/features/doc_lifecycle.feature",
    "kb-ops/src/test/resources/features/ops_dashboard.feature",
    "kb-intelligence/src/test/resources/features/knowledge_import.feature",
]


def ensure_remote_dir(sftp, remote_path):
    """Recursively create remote directory if not exists."""
    if remote_path in ("/", "", "."):
        return
    try:
        sftp.stat(remote_path)
    except FileNotFoundError:
        parent = posixpath.dirname(remote_path)
        ensure_remote_dir(sftp, parent)
        sftp.mkdir(remote_path)
        print(f"  [mkdir] {remote_path}")


def main():
    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    ssh.connect(HOST, username=USER, password=PASSWORD, timeout=15)
    sftp = ssh.open_sftp()

    success = 0
    failed = 0
    for rel_path in FILES:
        local_path = os.path.join(LOCAL_BASE, rel_path.replace("/", os.sep))
        remote_path = posixpath.join(REMOTE_BASE, rel_path)

        if not os.path.exists(local_path):
            print(f"[SKIP] {rel_path} (local file not found)")
            failed += 1
            continue

        try:
            remote_dir = posixpath.dirname(remote_path)
            ensure_remote_dir(sftp, remote_dir)
            sftp.put(local_path, remote_path)
            print(f"[OK]   {rel_path}")
            success += 1
        except Exception as e:
            print(f"[FAIL] {rel_path} -> {e}")
            failed += 1

    sftp.close()
    ssh.close()
    print(f"\n=== Upload Summary: {success} ok, {failed} failed ===")


if __name__ == "__main__":
    main()
