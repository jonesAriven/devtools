#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
trigger-pipeline.py - 通过 Woodpecker CI API 触发指定项目流水线

用法:
    python trigger-pipeline.py <项目名> [分支名] [--note "备注说明"]

支持项目:
    mykng, kb-ops, kb-ops-web, infra-monitor, infra-monitor-web,
    active-manager, portal-web, portal-server, all

示例:
    python trigger-pipeline.py active-manager
    python trigger-pipeline.py mykng dev --note "修复登录bug"
    python trigger-pipeline.py all master --note "全量部署v2.0"

环境变量:
    WOODPECKER_TOKEN - Woodpecker API Token (从 UI 获取)
    WOODPECKER_URL   - Woodpecker 地址 (默认: https://woodci.marschat.online)

输出:
    1. 触发成功后打印流水线编号和查看链接
    2. 自动将触发记录写入 .pipeline-history.json（含编号、项目、备注、时间）
    3. 可通过 check-pipeline.py 查看历史记录
"""

import os
import sys
import json
import urllib.request
import urllib.error
from datetime import datetime

# ====== 配置 ======
DEFAULT_URL = "https://woodci.marschat.online"
REPO_ID = 1  # devtools 仓库 ID

# 触发记录文件（与脚本同目录）
HISTORY_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), ".pipeline-history.json")

# Woodpecker API Token (从 UI -> 用户设置 -> CLI & API 获取)
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

# 项目显示名称（用于友好输出）
PROJECT_DISPLAY = {
    "mykng": "知识库微服务(mykng)",
    "kb-ops": "运维后台(kb-ops)",
    "kb-ops-web": "运维前端(kb-ops-web)",
    "infra-monitor": "基础设施监控(infra-monitor)",
    "infra-monitor-web": "监控前端(infra-monitor-web)",
    "active-manager": "激活码系统(active-manager)",
    "portal-web": "门户前端(portal-web)",
    "portal-server": "门户后端(portal-server)",
    "all": "全量部署(all)",
}


def get_token():
    token = os.environ.get("WOODPECKER_TOKEN")
    return token if token else WOODPECKER_TOKEN


def get_url():
    return os.environ.get("WOODPECKER_URL", DEFAULT_URL).rstrip("/")


def load_history():
    """加载触发历史"""
    if os.path.exists(HISTORY_FILE):
        try:
            with open(HISTORY_FILE, "r", encoding="utf-8") as f:
                return json.load(f)
        except Exception:
            pass
    return []


def save_history(history):
    """保存触发历史（最多保留50条）"""
    try:
        # 去重：同一编号只保留最新记录
        seen = set()
        unique = []
        for item in reversed(history):
            num = item.get("number")
            if num and num not in seen:
                seen.add(num)
                unique.append(item)
        unique.reverse()
        
        # 截断到50条
        history = unique[-50:]
        
        with open(HISTORY_FILE, "w", encoding="utf-8") as f:
            json.dump(history, f, ensure_ascii=False, indent=2)
    except Exception as e:
        print(f"[警告] 无法保存历史记录: {e}")


def add_history(pipeline_num, project, branch, note, status):
    """添加一条触发记录"""
    history = load_history()
    record = {
        "number": pipeline_num,
        "project": project,
        "branch": branch,
        "note": note or "",
        "status": status,
        "time": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        "url": f"{get_url()}/repos/{REPO_ID}/pipeline/{pipeline_num}",
    }
    history.append(record)
    save_history(history)
    return record


def trigger_pipeline(project, branch="dev", note=None):
    """触发指定项目的流水线"""
    token = get_token()
    base_url = get_url()
    deploy_target = PROJECT_MAP.get(project)
    display_name = PROJECT_DISPLAY.get(project, project)

    if deploy_target is None:
        print(f"[错误] 未知项目: {project}")
        print(f"  支持项目: {', '.join(PROJECT_MAP.keys())}")
        sys.exit(1)

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
            
            # 打印结果
            print(f"\n{'='*60}")
            print(f"  流水线 #{pipeline_num} 已触发")
            print(f"{'='*60}")
            print(f"  项目: {display_name}")
            print(f"  分支: {branch}")
            print(f"  目标: {deploy_target or '(全量)'}")
            print(f"  状态: {status}")
            if note:
                print(f"  备注: {note}")
            print(f"  时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
            print(f"{'='*60}")
            print(f"  查看: {base_url}/repos/{REPO_ID}/pipeline/{pipeline_num}")
            
            # 保存触发记录
            add_history(pipeline_num, project, branch, note, status)
            print(f"\n[提示] 记录已保存，可用以下命令查看状态:")
            print(f"       python woodScript/check-pipeline.py {pipeline_num}")
            print(f"       python woodScript/check-pipeline.py --recent\n")
            
            return result
    except urllib.error.HTTPError as e:
        print(f"[错误] HTTP {e.code}: {e.reason}")
        try:
            body = json.loads(e.read().decode("utf-8"))
            print(f"  详情: {body}")
        except Exception:
            pass
        sys.exit(1)
    except Exception as e:
        print(f"[错误] 请求失败: {e}")
        sys.exit(1)


def show_recent(count=10):
    """显示最近的触发历史"""
    history = load_history()
    if not history:
        print("[信息] 暂无触发历史记录")
        return
    
    # 显示最近 N 条
    recent = history[-count:] if count > 0 else history
    
    print(f"\n{'='*70}")
    print(f"  最近 {len(recent)} 条流水线触发记录")
    print(f"{'='*70}")
    print(f"  {'编号':<8s} {'项目':<22s} {'分支':<6s} {'状态':<10s} {'时间':<18s} {'备注'}")
    print(f"  {'-'*68}")
    
    for r in recent:
        proj_display = PROJECT_DISPLAY.get(r.get("project", ""), r.get("project", ""))
        note = r.get("note", "") or "-"
        print(f"  #{r.get('number','?'):<7d} {proj_display:<22s} {r.get('branch',''):<6s} {r.get('status','?'):<10s} {r.get('time',''):<18s} {note}")
    
    print(f"{'='*70}\n")


def main():
    # 解析参数
    args = sys.argv[1:]
    
    # 特殊命令: --history / --recent
    if "--history" in args or "--recent" in args:
        count = 10
        for i, a in enumerate(args):
            if a in ("--history", "--recent") and i + 1 < len(args) and args[i+1].isdigit():
                count = int(args[i+1])
        show_recent(count)
        return
    
    if len(args) < 1:
        print(__doc__)
        print("\n子命令:")
        print("  --recent [N]     显示最近 N 条触发记录 (默认10)")
        print("  --history [N]    同上")
        print()
        sys.exit(1)

    project = args[0]
    branch = "dev"
    note = None
    
    i = 1
    while i < len(args):
        if args[i] in ("--branch", "-b") and i + 1 < len(args):
            branch = args[i + 1]
            i += 2
        elif args[i] in ("--note", "-n", "-m") and i + 1 < len(args):
            note = args[i + 1]
            i += 2
        elif not args[i].startswith("-"):
            # 位置参数作为分支
            branch = args[i]
            i += 1
        else:
            i += 1

    trigger_pipeline(project, branch, note)


if __name__ == "__main__":
    main()
