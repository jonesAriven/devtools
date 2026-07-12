#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
trigger-pipeline.py - 通过 Woodpecker CI API 触发/重跑流水线

用法:
    # ====== 基础触发（产生新编号）======
    python trigger-pipeline.py <项目名> [分支] [--note "备注"]

    # ====== 固定编号模式（推荐！）======
    python trigger-pipeline.py --alias <别名>              # 用固定别名触发
    python trigger-pipeline.py --rerun <编号>               # 重跑指定编号
    python trigger-pipeline.py --rerun <别名>               # 用别名重跑

    # ====== 查询 ======
    python trigger-pipeline.py --list                       # 列出所有固定别名
    python trigger-pipeline.py --recent [N]                 # 最近 N 条记录

支持项目(别名):
    mykng, kb-ops, kb-ops-web, infra-monitor, infra-monitor-web,
    active-manager, portal-web, portal-server, all

示例:
    python trigger-pipeline.py active-manager                # 触发激活码部署
    python trigger-pipeline.py --alias active-manager       # 同上（用别名）
    python trigger-pipeline.py --rerun active-manager       # 重跑上一次（同commit）
    python trigger-pipeline.py active-manager --note "修复bug"
    python trigger-pipeline.py --list                       # 查看所有别名

环境变量:
    WOODPECKER_TOKEN - Woodpecker API Token
    WOODPECKER_URL   - Woodpecker 地址 (默认: https://woodci.marschat.online)

核心设计:
    每个项目首次触发时分配一个"基准编号"，后续可通过 --rerun 基于同一 commit
    重复执行，避免流水线数量膨胀。新代码需正常触发（不用 --rerun）。
"""

import os
import sys
import json
import urllib.request
import urllib.error
from datetime import datetime

# ====== 配置 ======
DEFAULT_URL = "https://woodci.marschat.online"
REPO_ID = 1

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
HISTORY_FILE = os.path.join(SCRIPT_DIR, ".pipeline-history.json")
ALIAS_FILE = os.path.join(SCRIPT_DIR, ".pipeline-aliases.json")   # 别名→基准编号映射

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
    "mykng": "知识库(mykng)",
    "kb-ops": "运维后台(kb-ops)",
    "kb-ops-web": "运维前端(kb-ops-web)",
    "infra-monitor": "监控(infra-mon)",
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


# ========== 别名管理 ==========

def load_aliases():
    """加载别名映射: {别名: 基准编号}"""
    if os.path.exists(ALIAS_FILE):
        try:
            with open(ALIAS_FILE, "r", encoding="utf-8") as f:
                return json.load(f)
        except Exception:
            pass
    return {}


def save_aliases(aliases):
    """保存别名映射"""
    try:
        with open(ALIAS_FILE, "w", encoding="utf-8") as f:
            json.dump(aliases, f, ensure_ascii=False, indent=2)
    except Exception as e:
        print(f"[警告] 无法保存别名文件: {e}")


def get_base_number(alias):
    """获取别名的基准编号，没有则返回 None"""
    aliases = load_aliases()
    num = aliases.get(alias)
    if num:
        return int(num)
    return None


def set_alias(alias, base_number):
    """设置/更新别名指向的基准编号"""
    aliases = load_aliases()
    old_num = aliases.get(alias)
    aliases[alias] = base_number
    save_aliases(aliases)
    if old_num and old_num != base_number:
        print(f"[信息] 别名 '{alias}' 已更新: #{old_num} -> #{base_number}")
    elif not old_num:
        print(f"[信息] 别名 '{alias}' 已注册: #{base_number}")


def list_aliases():
    """列出所有已注册的别名"""
    aliases = load_aliases()
    if not aliases:
        print("[信息] 暂无已注册的别名")
        print("       提示: 使用 --alias <项目名> 首次触发后自动注册")
        return
    
    print(f"\n{'='*60}")
    print(f"  已注册的流水线别名 (共 {len(aliases)} 个)")
    print(f"{'='*60}")
    print(f"  {'别名':<22s} {'基准编号':<10s} {'目标':<16s} {'显示名'}")
    print(f"  {'-'*58}")
    
    for alias, num in sorted(aliases.items()):
        target = PROJECT_MAP.get(alias, alias)
        display = PROJECT_DISPLAY.get(alias, alias)
        print(f"  {alias:<22s} #{str(num):<9s} {target:<16s} {display}")
    
    print(f"{'='*60}")
    print(f"\n  使用方式:")
    print(f"    python trigger-pipeline.py --rerun <别名>     # 重跑（同commit）")
    print(f"    python trigger-pipeline.py --alias <别名>      # 新触发（最新commit）\n")


# ========== 历史记录 ==========

def load_history():
    if os.path.exists(HISTORY_FILE):
        try:
            with open(HISTORY_FILE, "r", encoding="utf-8") as f:
                return json.load(f)
        except Exception:
            pass
    return []


def save_history(history):
    try:
        seen = set()
        unique = []
        for item in reversed(history):
            num = item.get("number")
            if num and num not in seen:
                seen.add(num)
                unique.append(item)
        unique.reverse()
        history = unique[-50:]
        
        with open(HISTORY_FILE, "w", encoding="utf-8") as f:
            json.dump(history, f, ensure_ascii=False, indent=2)
    except Exception as e:
        print(f"[警告] 无法保存历史记录: {e}")


def add_history(pipeline_num, project, branch, note, status, is_rerun=False, rerun_of=None):
    history = load_history()
    record = {
        "number": pipeline_num,
        "project": project,
        "branch": branch,
        "note": note or "",
        "status": status,
        "is_rerun": is_rerun,
        "rerun_of": rerun_of,
        "time": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        "url": f"{get_url()}/repos/{REPO_ID}/pipeline/{pipeline_num}",
    }
    history.append(record)
    save_history(history)


# ========== API 操作 ==========

def api_get(path):
    token = get_token()
    req = urllib.request.Request(
        f"{get_url()}{path}",
        headers={"Authorization": f"Bearer {token}"}
    )
    with urllib.request.urlopen(req, timeout=15) as resp:
        return json.loads(resp.read().decode("utf-8"))


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


def trigger_new(project, branch="dev", note=None):
    """触发全新流水线（基于最新 commit）"""
    deploy_target = PROJECT_MAP.get(project)
    display_name = PROJECT_DISPLAY.get(project, project)

    if deploy_target is None:
        print(f"[错误] 未知项目: {project}")
        print(f"  支持项目: {', '.join(PROJECT_MAP.keys())}")
        sys.exit(1)

    result = api_post(f"/api/repos/{REPO_ID}/pipelines", {
        "branch": branch,
        "variables": {"DEPLOY_TARGET": deploy_target}
    })

    pipeline_num = result.get("number", "?")
    status = result.get("status", "?")

    # 注册/更新别名
    set_alias(project, pipeline_num)

    # 打印结果
    tag = "NEW"
    print(f"\n{'='*60}")
    print(f"  流水线 #{pipeline_num} [{tag}] 已触发")
    print(f"{'='*60}")
    print(f"  项目: {display_name}")
    print(f"  分支: {branch}")
    print(f"  目标: {deploy_target or '(全量)'}")
    print(f"  状态: {status}")
    if note:
        print(f"  备注: {note}")
    print(f"  时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print(f"{'='*60}")
    print(f"  查看: {get_url()}/repos/{REPO_ID}/pipeline/{pipeline_num}")

    add_history(pipeline_num, project, branch, note, status, is_rerun=False)
    
    print(f"\n[提示]")
    print(f"  查状态:  python woodScript/check-pipeline.py {pipeline_num}")
    print(f"  重跑:    python woodScript/trigger-pipeline.py --rerun {project}")
    print(f"  重跑:    python woodScript/trigger-pipeline.py --rerun {pipeline_num}\n")

    return result


def rerun_pipeline(target, note=None):
    """
    重跑已有流水线
    target 可以是: 编号(整数)、别名(字符串)
    基于 target 对应的 commit 重新执行，产生新编号
    """
    base_num = None
    alias_name = None

    # 判断是编号还是别名
    if isinstance(target, int) or (isinstance(target, str) and target.lstrip("-").isdigit()):
        base_num = int(target)
    else:
        # 是别名
        alias_name = target
        base_num = get_base_number(alias_name)
        if not base_num:
            print(f"[错误] 别名 '{alias_name}' 尚未注册")
            print(f"  请先执行: python trigger-pipeline.py --alias {alias_name}")
            sys.exit(1)

    # 获取原始流水线信息
    try:
        original = api_get(f"/api/repos/{REPO_ID}/pipelines/{base_num}")
    except urllib.error.HTTPError as e:
        if e.code == 404:
            print(f"[错误] 流水线 #{base_num} 不存在")
        else:
            print(f"[错误] HTTP {e.code}: {e.reason}")
        sys.exit(1)

    orig_commit = original.get("commit", "?")[:12]
    orig_branch = original.get("branch", "?")
    orig_variables = original.get("variables", {})
    orig_project = orig_variables.get("DEPLOY_TARGET", "")
    display_name = PROJECT_DISPLAY.get(orig_project, orig_project or "unknown")

    # 执行 rerun (POST 到原流水线)
    result = api_post(f"/api/repos/{REPO_ID}/pipelines/{base_num}")

    new_num = result.get("number", "?")
    new_status = result.get("status", "?")

    # 打印结果
    tag = "RERUN"
    source_desc = f"#{base_num}" if not alias_name else f"'{alias_name}' (#{base_num})"
    
    print(f"\n{'='*60}")
    print(f"  流水线 #{new_num} [{tag}] 已触发")
    print(f"{'='*60}")
    print(f"  来源: {source_desc}")
    print(f"  项目: {display_name}")
    print(f"  Commit: {orig_commit} (与原始相同)")
    print(f"  分支: {orig_branch}")
    print(f"  状态: {new_status}")
    if note:
        print(f"  备注: {note}")
    print(f"  时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print(f"{'='*60}")
    print(f"  查看: {get_url()}/repos/{REPO_ID}/pipeline/{new_num}")

    # 记录历史
    project_key = alias_name if alias_name else orig_project
    add_history(new_num, project_key, orig_branch, note, new_status, 
                is_rerun=True, rerun_of=base_num)

    print(f"\n[提示] 基于 commit {orig_commit} 重新执行（代码不变）")
    print(f"  如需部署最新代码，请用: python trigger-pipeline.py {project_key}\n")

    return result


def show_recent(count=10):
    history = load_history()
    if not history:
        print("[信息] 暂无触发历史记录")
        return
    
    recent = history[-count:] if count > 0 else history
    
    print(f"\n{'='*72}")
    print(f"  最近 {len(recent)} 条流水线记录")
    print(f"{'='*72}")
    print(f"  {'编号':<8s} {'类型':<7s} {'目标':<18s} {'状态':<10s} {'时间':<18s} {'备注'}")
    print(f"  {'-'*70}")
    
    for r in recent:
        proj_display = PROJECT_DISPLAY.get(r.get("project", ""), r.get("project", ""))
        note = r.get("note", "") or "-"
        rerun_type = "RERUN" if r.get("is_rerun") else "NEW "
        rerun_of = f"->#{r.get('rerun_of','')}" if r.get("rerun_of") else ""
        num_str = f"#{r.get('number','?')}"
        print(f"  {num_str:<8s} {rerun_type:<7s} {proj_display:<18s} {r.get('status','?'):<10s} {r.get('time',''):<18s} {note}{rerun_of}")
    
    print(f"{'='*72}\n")


# ========== 主入口 ==========

def main():
    args = sys.argv[1:]

    # --list: 列出所有别名
    if "--list" in args or "--aliases" in args:
        list_aliases()
        return

    # --history / --recent: 显示历史
    if "--history" in args or "--recent" in args:
        count = 10
        for i, a in enumerate(args):
            if a in ("--history", "--recent") and i + 1 < len(args) and args[i+1].lstrip("-").isdigit():
                count = int(args[i+1])
        show_recent(count)
        return

    # --rerun: 重跑模式
    if "--rerun" in args:
        idx = args.index("--rerun")
        if idx + 1 >= len(args):
            print("[错误] --rerun 需要指定编号或别名")
            print("  示例: python trigger-pipeline.py --rerun 168")
            print("        python trigger-pipeline.py --rerun active-manager")
            sys.exit(1)
        
        target_str = args[idx + 1]
        note = None
        
        # 提取 note
        for i, a in enumerate(args):
            if a in ("--note", "-n", "-m") and i + 1 < len(args):
                note = args[i + 1]

        # 判断是编号还是别名
        if target_str.lstrip("-").isdigit():
            rerun_pipeline(int(target_str), note)
        else:
            rerun_pipeline(target_str, note)
        return

    # --alias: 别名触发模式（首次触发并注册别名，后续等同于普通触发）
    if "--alias" in args:
        idx = args.index("--alias")
        if idx + 1 >= len(args):
            print("[错误] --alias 需要指定项目名")
            sys.exit(1)
        
        project = args[idx + 1]
        branch = "dev"
        note = None
        
        for i, a in enumerate(args):
            if a in ("--branch", "-b") and i + 1 < len(args):
                branch = args[i + 1]
            elif a in ("--note", "-n", "-m") and i + 1 < len(args):
                note = args[i + 1]

        trigger_new(project, branch, note)
        return

    # 默认: 项目名触发
    if len(args) < 1:
        print(__doc__)
        print("\n子命令:")
        print("  <项目名> [分支] [--note 备注]    触发新流水线")
        print("  --alias <项目名>                 用别名触发（自动注册）")
        print("  --rerun <编号|别名>              重跑已有流水线")
        print("  --list                           列出所有已注册别名")
        print("  --recent [N]                     最近 N 条记录\n")
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

    trigger_new(project, branch, note)


if __name__ == "__main__":
    main()
