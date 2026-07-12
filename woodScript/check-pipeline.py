#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
check-pipeline.py - 查询 Woodpecker CI 流水线状态

用法:
    python check-pipeline.py [流水线编号]
    python check-pipeline.py --recent [N]    查看最近 N 条触发记录
    python check-pipeline.py --watch [编号]  持续监控（每10秒刷新）
    python check-pipeline.py --alias <别名>  用别名查询

示例:
    python check-pipeline.py 168             查询指定流水线
    python check-pipeline.py                 查询最近一条
    python check-pipeline.py --recent 5      查看最近5条触发记录
    python check-pipeline.py --watch 168     持续监控 #168
    python check-pipeline.py --alias active-manager  用别名查询

环境变量:
    WOODPECKER_TOKEN - Woodpecker API Token
    WOODPECKER_URL   - Woodpecker 地址 (默认: https://woodci.marschat.online)
"""

import os
import sys
import json
import time
import urllib.request
import urllib.error
from datetime import datetime

DEFAULT_URL = "https://woodci.marschat.online"
REPO_ID = 1

WOODPECKER_TOKEN = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ0eXBlIjoidXNlciIsInVzZXItaWQiOiIxIn0.471qau5gcvZNQnxV4KfpE5VMnZ_9Q16IzNMESLfdmE4"

HISTORY_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), ".pipeline-history.json")
ALIAS_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), ".pipeline-aliases.json")

STATUS_ICONS = {
    "pending": "⏳",
    "running": "🔄",
    "success": "✅",
    "failure": "❌",
    "error": "💥",
    "killed": "🔪",
    "skipped": "⏭️",
    "declined": "🚫",
}

STATE_ICONS = {
    "pending": "⏳",
    "running": "🔄",
    "success": "✅",
    "failure": "❌",
    "error": "💥",
    "killed": "🔪",
    "skipped": "⏭️",
    "declined": "🚫",
}

PROJECT_DISPLAY = {
    "mykng": "知识库(mykng)",
    "kb-ops": "运维后台(kb-ops)",
    "kb-ops-web": "运维前端(kb-ops-web)",
    "infra-monitor": "监控(infra-monitor)",
    "infra-monitor-web": "监控前端(im-web)",
    "active-manager": "激活码(active-mgr)",
    "portal-web": "门户前端(portal-web)",
    "portal-server": "门户后端(portal-svr)",
    "all": "全量(all)",
}


def get_token():
    return os.environ.get("WOODPECKER_TOKEN", WOODPECKER_TOKEN)


def get_url():
    return os.environ.get("WOODPECKER_URL", DEFAULT_URL).rstrip("/")


def api_get(path):
    """发送 GET 请求到 Woodpecker API"""
    token = get_token()
    base = get_url()
    req = urllib.request.Request(
        f"{base}{path}",
        headers={"Authorization": f"Bearer {token}"}
    )
    with urllib.request.urlopen(req, timeout=15) as resp:
        return json.loads(resp.read().decode("utf-8"))


def query_pipeline(pipeline_num=None):
    """查询指定或最近的流水线状态"""
    if pipeline_num:
        path = f"/api/repos/{REPO_ID}/pipelines/{pipeline_num}"
        data = api_get(path)
        print_pipeline_detail(data)
        return data
    else:
        # 查询最近一条
        path = f"/api/repos/{REPO_ID}/pipelines?per_page=1&page=1&sort=-number"
        result_list = api_get(path)
        if not result_list:
            print("[信息] 未找到任何流水线")
            return None
        print_pipeline_detail(result_list[0])
        return result_list[0]


def print_pipeline_detail(data):
    """打印流水线详细信息"""
    num = data.get("number", "?")
    status = data.get("status", "?")
    event = data.get("event", "?")
    branch = data.get("branch", "?")
    commit = data.get("commit", "?")[:12]
    author = data.get("author", "?")
    created_ts = data.get("created", 0)
    started_ts = data.get("started", 0)
    finished_ts = data.get("finished", 0)
    variables = data.get("variables", {})

    icon = STATUS_ICONS.get(status, "❓")
    
    # 尝试从本地历史获取备注
    note = ""
    history = load_history()
    for h in history:
        if h.get("number") == num:
            note = h.get("note", "")
            break

    created_dt = datetime.fromtimestamp(created_ts) if created_ts else None
    started_dt = datetime.fromtimestamp(started_ts) if started_ts else None
    finished_dt = datetime.fromtimestamp(finished_ts) if finished_ts else None
    
    # 计算目标项目显示名
    target = variables.get("DEPLOY_TARGET", "")
    target_display = PROJECT_DISPLAY.get(target, target) if target else "(全量)"

    print(f"\n{'='*60}")
    print(f"  流水线 #{num}  {icon} {status.upper()}")
    print(f"{'='*60}")
    print(f"  事件:     {event}")
    print(f"  分支:     {branch}")
    print(f"  目标:     {target_display}")
    print(f"  提交:     {commit}")
    print(f"  作者:     {author}")
    print(f"  创建时间: {created_dt.strftime('%Y-%m-%d %H:%M:%S') if created_dt else '?'}")
    if started_dt:
        print(f"  开始时间: {started_dt.strftime('%Y-%m-%d %H:%M:%S')}")
    if finished_dt:
        print(f"  完成时间: {finished_dt.strftime('%Y-%m-%d %H:%M:%S')}")
        duration = (finished_dt - started_dt).total_seconds() if started_dt else 0
        print(f"  耗时:     {duration:.0f}秒")
    if note:
        print(f"  备注:     {note}")

    # 打印各步骤状态
    workflows = data.get("workflows", [])
    if workflows:
        wf = workflows[0]
        children = wf.get("children", [])
        if children:
            print(f"\n  步骤详情:")
            print(f"  {'-'*56}")
            for step in children:
                name = step.get("name", "?")
                state = step.get("state", "?")
                s_icon = STATE_ICONS.get(state, "❓")
                step_started = step.get("started", 0)
                step_finished = step.get("finished", 0)
                dur = ""
                if step_started and step_finished:
                    dur = f" ({step_finished - step_started}s)"
                elif step_started and not step_finished:
                    dur = " (运行中...)"
                print(f"    {s_icon} {name:<30s} {state:<10s}{dur}")

    base = get_url()
    print(f"\n  查看: {base}/repos/{REPO_ID}/pipeline/{num}")
    print()


def load_history():
    """加载触发历史"""
    if os.path.exists(HISTORY_FILE):
        try:
            with open(HISTORY_FILE, "r", encoding="utf-8") as f:
                return json.load(f)
        except Exception:
            pass
    return []


def show_recent(count=10):
    """显示最近的触发历史"""
    history = load_history()
    if not history:
        print("[信息] 暂无触发历史记录")
        print("       提示: 使用 trigger-pipeline.py 触发时会自动记录")
        return
    
    recent = history[-count:] if count > 0 else history
    
    print(f"\n{'='*72}")
    print(f"  最近 {len(recent)} 条流水线触发记录")
    print(f"{'='*72}")
    print(f"  {'编号':<8s} {'目标':<20s} {'分支':<6s} {'状态':<10s} {'时间':<18s} {'备注'}")
    print(f"  {'-'*70}")
    
    for r in recent:
        proj_display = PROJECT_DISPLAY.get(r.get("project", ""), r.get("project", ""))
        note = r.get("note", "") or "-"
        num_str = f"#{r.get('number','?')}"
        print(f"  {num_str:<8s} {proj_display:<20s} {r.get('branch',''):<6s} {r.get('status','?'):<10s} {r.get('time',''):<18s} {note}")
    
    print(f"{'='*72}\n")


def watch_pipeline(pipeline_num=None, interval=10):
    """持续监控流水线状态"""
    num = pipeline_num
    if not num:
        # 获取最新一条
        path = f"/api/repos/{REPO_ID}/pipelines?per_page=1&page=1&sort=-number"
        result_list = api_get(path)
        if result_list:
            num = result_list[0].get("number")
            print(f"[监控] 自动选择最新流水线 #{num}\n")
    
    if not num:
        print("[错误] 无可监控的流水线")
        sys.exit(1)
    
    print(f"[监控] 每 {interval} 秒刷新流水线 #{num} 状态 (Ctrl+C 停止)\n")
    
    try:
        while True:
            try:
                path = f"/api/repos/{REPO_ID}/pipelines/{num}"
                data = api_get(path)
                status = data.get("status", "?")
                
                # 清屏效果（用空行分隔）
                print(f"\033[2J\033[H", end="")
                print_pipeline_detail(data)
                
                # 终态则停止监控
                if status in ("success", "failure", "error", "killed", "declined"):
                    print(f"\n[监控] 流水线已结束 ({status})，监控停止\n")
                    break
                
                time.sleep(interval)
            except KeyboardInterrupt:
                print("\n\n[监控] 用户中断\n")
                break
            except Exception as e:
                print(f"[警告] 查询失败: {e}，{interval}秒后重试...")
                time.sleep(interval)
    except KeyboardInterrupt:
        print("\n[监控] 已停止\n")


def load_aliases():
    if os.path.exists(ALIAS_FILE):
        try:
            with open(ALIAS_FILE, "r", encoding="utf-8") as f:
                return json.load(f)
        except Exception:
            pass
    return {}


def main():
    args = sys.argv[1:]
    
    # --history / --recent: 显示触发历史
    if "--history" in args or "--recent" in args:
        count = 10
        for i, a in enumerate(args):
            if a in ("--history", "--recent") and i + 1 < len(args) and args[i+1].lstrip("-").isdigit():
                count = int(args[i+1])
        show_recent(count)
        return
    
    # --watch: 持续监控
    if "--watch" in args:
        num = None
        for i, a in enumerate(args):
            if a == "--watch" and i + 1 < len(args) and args[i+1].isdigit():
                num = int(args[i+1])
        watch_pipeline(num)
        return
    
    # --alias: 用别名查询
    if "--alias" in args:
        idx = args.index("--alias")
        if idx + 1 >= len(args):
            print("[错误] --alias 需要指定别名")
            print("  示例: python check-pipeline.py --alias active-manager")
            sys.exit(1)
        
        alias_name = args[idx + 1]
        aliases = load_aliases()
        base_num = aliases.get(alias_name)
        
        if not base_num:
            print(f"[错误] 别名 '{alias_name}' 尚未注册")
            print("  已注册的别名:")
            for a, n in sorted(aliases.items()):
                print(f"    {a} -> #{n}")
            sys.exit(1)
        
        # 查询该别名的基准编号
        query_pipeline(int(base_num))
        return
    
    # 默认：查询流水线
    pipeline_num = None
    for a in args:
        if a.isdigit():
            pipeline_num = int(a)
            break
    
    query_pipeline(pipeline_num)


if __name__ == "__main__":
    main()
