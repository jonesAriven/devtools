#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
trigger-pipeline.py - 通过 Woodpecker CI API 触发指定项目流水线

用法:
    python trigger-pipeline.py <项目名> [分支名]

支持项目:
    mykng, kb-ops, kb-ops-web, infra-monitor, infra-monitor-web,
    active-manager, portal-web, portal-server, all

示例:
    python trigger-pipeline.py mykng
    python trigger-pipeline.py all dev

环境变量:
    WOODPECKER_TOKEN - Woodpecker API Token (从 UI 获取)
    WOODPECKER_URL   - Woodpecker 地址 (默认: https://woodci.marschat.online)
"""

import os
import sys
import json
import urllib.request
import urllib.error

# ====== 配置 ======
DEFAULT_URL = "https://woodci.marschat.online"
REPO_ID = 1  # devtools 仓库 ID

# Woodpecker API Token (从 UI -> 用户设置 -> CLI & API 获取)
# 注意: 此 Token 具有触发流水线权限，请勿泄露
WOODPECKER_TOKEN = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ0eXBlIjoidXNlciIsInVzZXItaWQiOiIxIn0.471qau5gcvZNQnxV4KfpE5VMnZ_9Q16IzNMESLfdmE4"

# 项目到 DEPLOY_TARGET 的映射
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


def get_token():
    """获取 Woodpecker API Token"""
    # 优先从环境变量读取（方便临时覆盖）
    token = os.environ.get("WOODPECKER_TOKEN")
    if token:
        return token
    # 使用内置 Token
    return WOODPECKER_TOKEN


def get_url():
    """获取 Woodpecker URL"""
    return os.environ.get("WOODPECKER_URL", DEFAULT_URL).rstrip("/")


def trigger_pipeline(project, branch="dev"):
    """触发指定项目的流水线"""
    token = get_token()
    base_url = get_url()
    deploy_target = PROJECT_MAP.get(project)

    if deploy_target is None:
        print(f"[错误] 未知项目: {project}")
        print(f"  支持项目: {', '.join(PROJECT_MAP.keys())}")
        sys.exit(1)

    # 构建 API 请求
    # Woodpecker API: POST /api/repos/{repo_id}/pipelines
    # 参数: branch, variables (DEPLOY_TARGET)
    url = f"{base_url}/api/repos/{REPO_ID}/pipelines"
    data = json.dumps({
        "branch": branch,
        "variables": {
            "DEPLOY_TARGET": deploy_target
        }
    }).encode("utf-8")

    req = urllib.request.Request(
        url,
        data=data,
        headers={
            "Authorization": f"Bearer {token}",
            "Content-Type": "application/json",
        },
        method="POST"
    )

    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            result = json.loads(resp.read().decode("utf-8"))
            pipeline_num = result.get("number", "?")
            status = result.get("status", "?")
            print(f"[成功] 触发流水线: #{pipeline_num}")
            print(f"  项目: {project}")
            print(f"  分支: {branch}")
            print(f"  DEPLOY_TARGET: {deploy_target or 'all'}")
            print(f"  状态: {status}")
            print(f"  查看: {base_url}/repos/{REPO_ID}/pipeline/{pipeline_num}")
            return result
    except urllib.error.HTTPError as e:
        print(f"[错误] HTTP {e.code}: {e.reason}")
        try:
            body = json.loads(e.read().decode("utf-8"))
            print(f"  详情: {body}")
        except:
            pass
        sys.exit(1)
    except Exception as e:
        print(f"[错误] 请求失败: {e}")
        sys.exit(1)


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(1)

    project = sys.argv[1]
    branch = sys.argv[2] if len(sys.argv) > 2 else "dev"

    trigger_pipeline(project, branch)


if __name__ == "__main__":
    main()
