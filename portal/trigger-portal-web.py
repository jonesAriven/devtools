#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""trigger-portal-web.py - 门户前端(portal-web) 一键部署"""
import sys, os, subprocess
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
WOOD_SCRIPT = os.path.join(SCRIPT_DIR, "..", "woodScript")
PROJECT = "portal-web"
BRANCH = "dev"

def main():
    args = sys.argv[1:]
    note = args[0] if args and not args[0].startswith("--") else None
    trigger_only = "--trigger-only" in args
    cmd = [sys.executable, os.path.join(WOOD_SCRIPT, "trigger-pipeline.py"), PROJECT, BRANCH]
    if note: cmd.extend(["--note", note])
    if not trigger_only: cmd.append("--wait")
    subprocess.call(cmd)

if __name__ == "__main__":
    main()
