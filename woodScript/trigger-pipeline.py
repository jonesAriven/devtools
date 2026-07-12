#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
trigger-pipeline.py - 触发 Woodpecker CI 流水线

通用脚本，所有项目共用。触发后返回流水线编号，供 check-pipeline.py 查询。

用法:
    python trigger-pipeline.py <项目名> [分支] [--note 备注]

示例:
    python trigger-pipeline.py active-manager           # 触发激活码部署
    python trigger-pipeline.py active-manager dev       # 指定分支
    python trigger-pipeline.py kb-ops --note "修复bug"  # 带备注

支持项目:
    mykng, kb-ops, kb-ops-web, infra-monitor,
    infra-monitor-web, active-manager, portal-web, portal-server, all

环境变量:
    WOODPECKER_TOKEN - API Token (可选)
    WOODPECKER_URL   - Woodpecker 地址 (可选)
"""

import os
import sys
import json
import urllib.request
from datetime import datetime

DEFAULT_URL = "https://woodci.marschat.online"
REPO_ID = 1

WOODPECKER_TOKEN = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ0eXBlIjoidXNlciIsInVzZXItaWQiOiIxIn0.471qau5gcvZNQnxV4KfpE5VMnZ_9Q16IzNMESLfdmE4"

PROJECT_MAP = {
    "mykng": "mykng",
    "kb-ops": "kb-ops",
    "kb-ops-web": "kb-ops-web",
    "infra-monitor": "infra-monitor",
    "infra-monitor-web": "infra-monitor-web",
    "active-manager": "active-manager",
    "portal-web": "portal-web",
    "portal-server": "portal-server",
    "all": "",
}

PROJECT_DISPLAY = {
    "mykng": "知识库(mykng)", "kb-ops": "运维后台(kb-ops)",
    "kb-ops-web": "运维前端(kb-ops-web)", "infra-monitor": "监控(infra-mon)",
    "infra-monitor-web": "监控前端(im-web)", "active-manager": "激活码(active-mgr)",
    "portal-web": "门户前端(portal-web)", "portal-server": "门户后端(portal-svr)",
    "all": "全量(all)",
}


def get_token():
    return os.environ.get("WOODPECKER_TOKEN", WOODPECKER_TOKEN)


def get_url():
    return os.environ.get("WOODPECKER_URL", DEFAULT_URL).rstrip("/")


def api_post(path, data=None):
    token = get_token()
    body = json.dumps(data).encode("utf-8") if data else None
    req = urllib.request.Request(
        f"{get_url()}{path}",
        data=body,
        method="POST",
        headers={"Authorization": f"Bearer {token}", "Content-Type": "application/json"}
    )
    with urllib.request.urlopen(req, timeout=30) as resp:
        return json.loads(resp.read().decode("utf-8"))


def main():
    args = sys.argv[1:]

    if len(args) < 1 or args[0].startswith("-"):
        print(__doc__)
        print("项目列表:")
        for key, display in PROJECT_DISPLAY.items():
            print(f"  {key:<22s} {display}")
        sys.exit(1)

    project = args[0]
    branch = "dev"
    note = None

    i = 1
    while i < len(args):
        if args[i] in ("--branch", "-b") and i + 1 < len(args):
            branch = args[i + 1]; i += 2
        elif args[i] in ("--note", "-n", "-m") and i + 1 < len(args):
            note = args[i + 1]; i += 2
        elif not args[i].startswith("-"):
            branch = args[i]; i += 1
        else:
            i += 1

    deploy_target = PROJECT_MAP.get(project)
    if deploy_target is None:
        print(f"[错误] 未知项目: {project}")
        print(f"  支持项目: {', '.join(PROJECT_MAP.keys())}")
        sys.exit(1)

    # 触发流水线
    result = api_post(f"/api/repos/{REPO_ID}/pipelines", {
        "branch": branch,
        "variables": {"DEPLOY_TARGET": deploy_target}
    })

    pipeline_num = result.get("number", "?")
    status = result.get("status", "?")
    display_name = PROJECT_DISPLAY.get(project, project)

    print(f"\n{'='*56}")
    print(f"  #{pipeline_num} 已触发  {status.upper()}")
    print(f"{'='*56}")
    print(f"  项目:   {display_name}")
    print(f"  分支:   {branch}")
    print(f"  目标:   {deploy_target or '(全量)'}")
    if note:
        print(f"  备注:   {note}")
    print(f"  时间:   {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print(f"{'='*56}")
    print(f"  查看:   {get_url()}/repos/{REPO_ID}/pipeline/{pipeline_num}")
    print(f"\n  查状态+日志:")
    print(f"    python woodScript/check-pipeline.py {pipeline_num}")
    print()


if __name__ == "__main__":
    main()
