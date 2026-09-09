#!/usr/bin/env python3
"""Fetch and decode Woodpecker pipeline logs - all steps"""
import sys
import json
import base64
import urllib.request

URL = "https://woodci.marschat.online"
REPO = 1
TOKEN = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ0eXBlIjoidXNlciIsInVzZXItaWQiOiIxIn0.XvzTfuP27o9KPyE50Mxjeyw4MSD1qiOcJS8_D2sKQc8"

def api_get(path):
    req = urllib.request.Request(f"{URL}{path}", headers={"Authorization": f"Bearer {TOKEN}"})
    with urllib.request.urlopen(req, timeout=30) as resp:
        return json.loads(resp.read().decode("utf-8"))

pipeline = int(sys.argv[1]) if len(sys.argv) > 1 else 497
step_filter = sys.argv[2] if len(sys.argv) > 2 else None  # e.g. "build"
data = api_get(f"/api/repos/{REPO}/pipelines/{pipeline}")

for wf in data.get("workflows", []):
    for step in wf.get("children", []):
        name = step["name"]
        status = step["state"]
        sid = step["id"]
        
        if step_filter and step_filter not in name:
            continue
        
        print(f"\n{'='*60}")
        print(f"  Step: {name}  Status: {status}  ID: {sid}")
        print(f"{'='*60}")
        
        try:
            logs = api_get(f"/api/repos/{REPO}/logs/{pipeline}/{sid}")
            for entry in logs:
                d = entry.get("data")
                if d:
                    try:
                        print(base64.b64decode(d).decode("utf-8", errors="replace"), end="")
                    except:
                        print(d, end="")
        except Exception as e:
            print(f"  (获取日志失败: {e})")
