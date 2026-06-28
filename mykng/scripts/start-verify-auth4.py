#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""在 mykng 上后台启动 kb-auth mvn verify（含 StepDefs）"""
import sys
sys.path.insert(0, r'd:\huliang\java\ideaworkspace\devtools\.trae')
from ssh_exec import exec_command

HOST = "100.93.36.113"
USER = "root"
PASSWORD = "root"

launcher = """#!/bin/bash
cd /root/devtools/mykng/kb-auth
nohup mvn -Pfast verify -B > /tmp/mvn-verify-auth4.log 2>&1 &
echo $! > /tmp/mvn-verify-auth4.pid
sleep 2
echo "PID=$(cat /tmp/mvn-verify-auth4.pid)"
"""

cmd = f"""cat > /tmp/start-verify-auth4.sh <<'LAUNCHER'
{launcher}
LAUNCHER
bash /tmp/start-verify-auth4.sh"""

code = exec_command(HOST, 22, USER, PASSWORD, cmd, timeout=30)
sys.exit(code)
