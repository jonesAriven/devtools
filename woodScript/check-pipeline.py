#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
check-pipeline.py - Woodpecker CI 流水线状态查询 & 日志查看

通用脚本，所有项目共用。

用法:
    python check-pipeline.py [编号]              查询状态 + 所有步骤日志
    python check-pipeline.py --log <编号>        只看所有步骤日志（不含头部信息）
    python check-pipeline.py --watch [编号]      持续监控（每15秒刷新）
    python check-pipeline.py --recent [N]        最近 N 条本地记录

示例:
    python check-pipeline.py 179                 # 完整查询：状态+所有步骤日志
    python check-pipeline.py --log 179           # 纯日志模式
    python check-pipeline.py --watch 179         # 持续监控到结束
    python check-pipeline.py                    # 不给编号查最新一条

环境变量:
    WOODPECKER_TOKEN - API Token (可选，脚本内置默认值)
    WOODPECKER_URL   - Woodpecker 地址 (可选)
"""

import os
import sys
import json
import time
import base64
import urllib.request
from datetime import datetime

DEFAULT_URL = "https://woodci.marschat.online"
REPO_ID = 1

WOODPECKER_TOKEN = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ0eXBlIjoidXNlciIsInVzZXItaWQiOiIxIn0.471qau5gcvZNQnxV4KfpE5VMnZ_9Q16IzNMESLfdmE4"

STATUS_ICONS = {
    "pending": "⏳", "running": "🔄", "success": "✅",
    "failure": "❌", "error": "💥", "killed": "🔪",
    "skipped": "⏭️", "declined": "🚫",
}

PROJECT_DISPLAY = {
    "mykng": "知识库(mykng)", "kb-ops": "运维后台(kb-ops)",
    "kb-ops-web": "运维前端(kb-ops-web)", "infra-monitor": "监控(infra-mon)",
    "infra-monitor-web": "监控前端(im-web)", "active-manager": "激活码(active-mgr)",
    "portal-web": "门户前端(portal-web)", "portal-server": "门户后端(portal-svr)",
    "all": "全量(all)",
}


# ==================== API ====================

def get_token():
    return os.environ.get("WOODPECKER_TOKEN", WOODPECKER_TOKEN)


def get_url():
    return os.environ.get("WOODPECKER_URL", DEFAULT_URL).rstrip("/")


def api_get(path):
    """GET 请求"""
    req = urllib.request.Request(
        f"{get_url()}{path}",
        headers={"Authorization": f"Bearer {get_token()}"}
    )
    with urllib.request.urlopen(req, timeout=15) as resp:
        return json.loads(resp.read().decode("utf-8"))


# ==================== 日志 ====================

def get_step_logs(pipeline_num, step_id, tail=30):
    """获取步骤日志，Base64解码，返回行列表"""
    try:
        logs = api_get(f"/api/repos/{REPO_ID}/logs/{pipeline_num}/{step_id}")
        lines = []
        for entry in logs:
            data_b64 = entry.get("data", "")
            try:
                decoded = base64.b64decode(data_b64).decode("utf-8", errors="replace")
            except Exception:
                decoded = data_b64
            lines.append(decoded.rstrip())
        if len(lines) > tail:
            lines = lines[-tail:]
        return lines
    except Exception as e:
        return [f"  [日志获取失败: {e}]"]


def print_all_logs(pipeline_num, tail=30):
    """打印流水线所有步骤的日志"""
    data = api_get(f"/api/repos/{REPO_ID}/pipelines/{pipeline_num}")
    num = data.get("number", "?")
    
    workflows = data.get("workflows", [])
    if not workflows:
        print("[信息] 无步骤信息")
        return
    
    children = workflows[0].get("children", [])
    if not children:
        print("[信息] 无步骤")
        return
    
    for step in children:
        step_id = step.get("id")
        name = step.get("name", "?")
        state = step.get("state", "?")
        icon = STATUS_ICONS.get(state, "❓")
        
        print(f"\n{'='*60}")
        print(f"  #{num} → {name}  {icon} {state}")
        print(f"{'='*60}")
        
        lines = get_step_logs(pipeline_num, step_id, tail)
        if lines and not all(l.startswith("  [日志获取失败:") for l in lines):
            for line in lines:
                print(f"  {line}")
        else:
            print("  (无日志)")
    print()


# ==================== 状态详情 ====================

def print_pipeline_detail(data, show_all_logs=True, log_tail=30):
    """打印完整流水线信息 + 所有步骤日志"""
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

    created_dt = datetime.fromtimestamp(created_ts) if created_ts else None
    started_dt = datetime.fromtimestamp(started_ts) if started_ts else None
    finished_dt = datetime.fromtimestamp(finished_ts) if finished_ts else None

    target = variables.get("DEPLOY_TARGET", "")
    target_display = PROJECT_DISPLAY.get(target, target) if target else "(全量)"

    # ---- 头部信息 ----
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

    # ---- 步骤摘要 ----
    workflows = data.get("workflows", [])
    children = workflows[0].get("children", []) if workflows else []
    
    if children:
        print(f"\n  步骤:")
        print(f"  {'-'*56}")
        for step in children:
            name = step.get("name", "?")
            state = step.get("state", "?")
            s_icon = STATUS_ICONS.get(state, "❓")
            step_started = step.get("started", 0)
            step_finished = step.get("finished", 0)
            dur = ""
            if step_started and step_finished:
                dur = f" ({step_finished - step_started}s)"
            elif step_started and not step_finished:
                dur = " (运行中...)"
            print(f"    {s_icon} {name:<28s} {state:<10s}{dur}")

    print(f"\n  查看: {get_url()}/repos/{REPO_ID}/pipeline/{num}")

    # ---- 所有步骤日志 ----
    if show_all_logs and children:
        print(f"\n  {'╔'*20} 日志 {'╗'*20}")
        for step in children:
            step_id = step.get("id")
            name = step.get("name", "?")
            state = step.get("state", "?")
            s_icon = STATUS_ICONS.get(state, "❓")
            
            print(f"\n  ┌─ {s_icon} {name} {'─'*max(0, 40-len(name)-4)}┐")
            
            lines = get_step_logs(num, step_id, tail=log_tail)
            if lines and not all("[日志获取失败:" in l for l in lines):
                for line in lines:
                    print(f"  │ {line}")
            else:
                print(f"  │ (无日志)")
            print(f"  └{'─'*56}┘")
        print()


# ==================== 监控 ====================

def watch_pipeline(pipeline_num=None, interval=15):
    """持续监控直到终态"""
    num = pipeline_num
    if not num:
        result_list = api_get(f"/api/repos/{REPO_ID}/pipelines?per_page=1&page=1&sort=-number")
        if result_list:
            num = result_list[0].get("number")
            print(f"[监控] 自动选择最新流水线 #{num}\n")

    if not num:
        print("[错误] 无可监控的流水线")
        sys.exit(1)

    print(f"[监控] 每 {interval}s 刷新 #{num} (Ctrl+C 停止)\n")

    while True:
        try:
            data = api_get(f"/api/repos/{REPO_ID}/pipelines/{num}")
            status = data.get("status", "?")

            print("\033[2J\033[H", end="")
            print_pipeline_detail(data, show_all_logs=True)

            if status in ("success", "failure", "error", "killed", "declined"):
                print(f"[监控] ══════════════════════════════")
                print(f"[监控] 最终结果: {status.upper()}")
                print(f"[监控] ══════════════════════════════\n")
                break

            print(f"[监控] {datetime.now().strftime('%H:%M:%S')} 下次: {interval}s后...\n")
            time.sleep(interval)
        except KeyboardInterrupt:
            print("\n[监控] 已停止\n")
            break
        except Exception as e:
            print(f"[警告] 失败: {e}, {interval}s后重试...\n")
            time.sleep(interval)


# ==================== 主入口 ====================

def main():
    args = sys.argv[1:]

    # --watch: 持续监控
    if "--watch" in args:
        num = None
        for i, a in enumerate(args):
            if a == "--watch" and i + 1 < len(args) and args[i+1].isdigit():
                num = int(args[i+1])
        watch_pipeline(num)
        return

    # --log: 纯日志模式
    if "--log" in args:
        idx = args.index("--log")
        if idx + 1 >= len(args) or not args[idx+1].lstrip("-").isdigit():
            print("用法: python check-pipeline.py --log <编号>")
            sys.exit(1)
        print_all_logs(int(args[idx+1]))
        return

    # 默认: 查询状态+日志
    pipeline_num = None
    for a in args:
        if a.lstrip("-").isdigit():
            pipeline_num = int(a.lstrip("-"))
            break

    if pipeline_num:
        data = api_get(f"/api/repos/{REPO_ID}/pipelines/{pipeline_num}")
        print_pipeline_detail(data, show_all_logs=True)
    else:
        # 无编号 → 查最新一条
        result_list = api_get(f"/api/repos/{REPO_ID}/pipelines?per_page=1&page=1&sort=-number")
        if result_list:
            print_pipeline_detail(result_list[0], show_all_logs=True)
        else:
            print("[信息] 未找到任何流水线")


if __name__ == "__main__":
    main()
