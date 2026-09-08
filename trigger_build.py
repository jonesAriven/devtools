#!/usr/bin/env python3
import json, urllib.request

TOKEN = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ0eXBlIjoidXNlciIsInVzZXItaWQiOiIxIn0.XvzTfuP27o9KPyE50Mxjeyw4MSD1qiOcJS8_D2sKQc8"
URL = "https://woodci.marschat.online"

# 触发新的流水线
data = json.dumps({
    "branch": "dev",
    "variables": {"DEPLOY_TARGET": "kb-ops-web"}
}).encode("utf-8")

req = urllib.request.Request(
    f"{URL}/api/repos/1/pipelines",
    data=data,
    method="POST",
    headers={
        "Authorization": f"Bearer {TOKEN}",
        "Content-Type": "application/json"
    }
)

try:
    with urllib.request.urlopen(req, timeout=30) as resp:
        result = json.loads(resp.read().decode("utf-8"))
        print(f"Pipeline #{result.get('number')} triggered: {result.get('status')}")
except urllib.error.HTTPError as e:
    print(f"Error {e.code}: {e.read().decode()[:200]}")
