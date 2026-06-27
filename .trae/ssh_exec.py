#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
SSH 远程执行工具（用于 Trae AI 操作内网 Debian RAG 服务）

用法:
  python ssh_exec.py "远程命令"
  python ssh_exec.py --host 100.105.196.63 --user root01 --cmd "远程命令"
  python ssh_exec.py --upload local_file remote_path  # 上传文件

默认目标: 内网 Debian Tailscale 100.105.196.63 (root01/root01)
"""
import sys
import argparse
import paramiko


# 默认凭据（内网 Debian Tailscale）
DEFAULT_HOST = "100.105.196.63"
DEFAULT_PORT = 22
DEFAULT_USER = "root01"
DEFAULT_PASSWORD = "root01"


def exec_command(host, port, user, password, cmd, timeout=30):
    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    try:
        client.connect(host, port=port, username=user, password=password, timeout=10)
        stdin, stdout, stderr = client.exec_command(cmd, timeout=timeout)
        out = stdout.read().decode("utf-8", errors="replace")
        err = stderr.read().decode("utf-8", errors="replace")
        exit_code = stdout.channel.recv_exit_status()
        if out:
            print(out, end="")
        if err:
            print(err, end="", file=sys.stderr)
        return exit_code
    finally:
        client.close()


def upload_file(host, port, user, password, local_path, remote_path):
    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    try:
        client.connect(host, port=port, username=user, password=password, timeout=10)
        sftp = client.open_sftp()
        sftp.put(local_path, remote_path)
        sftp.close()
        print(f"UPLOADED: {local_path} -> {remote_path}")
        return 0
    finally:
        client.close()


def main():
    parser = argparse.ArgumentParser(description="SSH 远程执行工具")
    parser.add_argument("cmd", nargs="?", help="要执行的远程命令")
    parser.add_argument("--host", default=DEFAULT_HOST, help=f"主机 (默认 {DEFAULT_HOST})")
    parser.add_argument("--port", type=int, default=DEFAULT_PORT, help=f"端口 (默认 {DEFAULT_PORT})")
    parser.add_argument("--user", default=DEFAULT_USER, help=f"用户名 (默认 {DEFAULT_USER})")
    parser.add_argument("--password", default=DEFAULT_PASSWORD, help="密码 (默认内置)")
    parser.add_argument("--timeout", type=int, default=30, help="超时秒数 (默认 30)")
    parser.add_argument("--upload", nargs=2, metavar=("LOCAL", "REMOTE"), help="上传文件")
    args = parser.parse_args()

    try:
        if args.upload:
            return upload_file(args.host, args.port, args.user, args.password,
                             args.upload[0], args.upload[1])
        elif args.cmd:
            return exec_command(args.host, args.port, args.user, args.password,
                              args.cmd, args.timeout)
        else:
            parser.error("必须提供 cmd 或 --upload")
    except Exception as e:
        print(f"ERROR: {type(e).__name__} - {e}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
