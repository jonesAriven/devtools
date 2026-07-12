#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
deploy.py - 激活码系统 一键部署

用法:
    python deploy.py                 # 触发 + 自动监控到完成
    python deploy.py "修复bug"        # 带备注
    python deploy.py --trigger-only   # 只触发不监控
"""

import sys
import os
import subprocess

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
WOOD_SCRIPT = os.path.join(SCRIPT_DIR, "..", "woodScript")

PROJECT = "active-manager"
BRANCH = "dev"


def main():
    args = sys.argv[1:]
    note = args[0] if args and not args[0].startswith("--") else None
    trigger_only = "--trigger-only" in args

    cmd = [sys.executable, os.path.join(WOOD_SCRIPT, "trigger-pipeline.py"),
           PROJECT, BRANCH]

    if note:
        cmd.extend(["--note", note])

    if not trigger_only:
        cmd.append("--wait")

    subprocess.call(cmd)


if __name__ == "__main__":
    main()
