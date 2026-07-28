#!/usr/bin/env python3
"""
OmniFind 后端重启脚本（Windows 专用，可重复使用）。

行为：
  1. 找到占用 127.0.0.1:8899 的进程并 taskkill 掉
  2. 用最新项目代码后台拉起 uvicorn 服务（DETACHED，常驻）
  3. 轮询端口直到 LISTENING，打印结果

用法：
  python restart_omnifind.py
（直接双击或命令行运行均可；无需参数）
"""
from __future__ import annotations

import os
import re
import subprocess
import sys
import time

PORT = 8899
PROJECT_ROOT = r"D:\huliang\java\ideaworkspace\devtools\omnifind"
PYTHON = r"D:\huliang\software\Python\Python313\python.exe"
LOG_FILE = os.path.join(PROJECT_ROOT, "omnifind-server.log")

# Windows 进程创建标志
DETACHED_PROCESS = 0x00000008
CREATE_NEW_PROCESS_GROUP = 0x00000200


def _netstat() -> str:
    try:
        # 中文 Windows 下 netstat 输出为 GBK，不能用 text=True(默认 utf-8 解码会崩)
        r = subprocess.run(["netstat", "-ano"], capture_output=True, shell=True)
        return r.stdout.decode("gbk", errors="ignore")
    except Exception:
        return ""


def find_pid_on_port(port: int) -> str | None:
    for line in _netstat().splitlines():
        if f":{port}" in line and "LISTENING" in line:
            m = re.search(r"(\d+)\s*$", line.strip())
            if m:
                return m.group(1)
    return None


def is_listening(port: int) -> bool:
    return any(
        f":{port}" in ln and "LISTENING" in ln for ln in _netstat().splitlines()
    )


def kill_pid(pid: str) -> None:
    r = subprocess.run(["taskkill", "/PID", pid, "/F"], capture_output=True)
    msg = (r.stdout or r.stderr).decode("gbk", errors="ignore").strip()
    print(f"[kill] PID {pid}: {msg or 'no output'}")


def main() -> int:
    print(f"[restart] 项目根: {PROJECT_ROOT}")
    pid = find_pid_on_port(PORT)
    if pid:
        print(f"[restart] 发现旧进程 PID={pid}，正在杀死")
        kill_pid(pid)
        for _ in range(10):
            if not is_listening(PORT):
                break
            time.sleep(0.5)
    else:
        print("[restart] 未发现占用 8899 的进程，直接启动新服务")

    env = os.environ.copy()
    env["PYTHONPATH"] = PROJECT_ROOT
    with open(LOG_FILE, "w", encoding="utf-8") as lf:
        proc = subprocess.Popen(
            [PYTHON, "-m", "omnifind.web.server"],
            cwd=PROJECT_ROOT,
            env=env,
            stdout=lf,
            stderr=subprocess.STDOUT,
            creationflags=DETACHED_PROCESS | CREATE_NEW_PROCESS_GROUP,
        )
    print(f"[restart] 已后台拉起新服务 (PID={proc.pid})，等待端口就绪...")

    for i in range(30):
        time.sleep(1)
        if is_listening(PORT):
            print(f"[restart] 服务已就绪 (用时 {i + 1}s)，监听 127.0.0.1:{PORT}")
            print(f"[restart] 日志: {LOG_FILE}")
            return 0
    print(f"[restart] 等待超时，请查看日志: {LOG_FILE}")
    return 1


if __name__ == "__main__":
    sys.exit(main())
