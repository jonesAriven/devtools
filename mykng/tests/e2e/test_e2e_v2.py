#!/usr/bin/env python3
# -*- coding: utf-8 -*-
r"""L3 E2E 测试 v2 - 15 个前端路由页面可访问性（13 原有 + 4 新增）

新增 4 个页面：
  - 标签管理   /kb/tag
  - 分享中心   /kb/share
  - 文件管理   /kb/file
  - 运维日志   /kb/ops/log

执行：
    $env:PLAYWRIGHT_BROWSERS_PATH="D:\huliang\java\ideaworkspace\devtools\.playwright-browsers"
    $env:PYTHONIOENCODING="utf-8"
    $env:PYTHONUTF8="1"
    python d:\huliang\java\ideaworkspace\devtools\mykng\tests\e2e\test_e2e_v2.py
"""
import os
import sys
import time
import traceback
from datetime import datetime
from playwright.sync_api import sync_playwright, TimeoutError as PlaywrightTimeoutError

BASE_URL = "https://kb.marschat.online"
CTX = "/kb"
LOGIN_URL = f"{BASE_URL}{CTX}/login"
USERNAME = "admin"
PASSWORD = "admin123"
REPORT_DIR = r"d:\huliang\java\ideaworkspace\devtools\mykng\tests\reports"
STATE_PATH = os.path.join(REPORT_DIR, "auth_state_v2.json")

GOTO_TIMEOUT = 30000
LOAD_TIMEOUT = 15000

results = []
console_errors_global = []


# ---------------------------------------------------------------------------
# 通用工具函数（沿用 test_e2e_full.py 模式）
# ---------------------------------------------------------------------------
def record(case_id, desc, status, detail=""):
    results.append({"case_id": case_id, "desc": desc, "status": status, "detail": detail})
    icon = "[PASS]" if status == "PASS" else "[FAIL]"
    print(f"{icon} {case_id} {desc}: {status}")
    if detail:
        print(f"     详情: {detail}")


def screenshot(page, name):
    os.makedirs(REPORT_DIR, exist_ok=True)
    path = os.path.join(REPORT_DIR, f"{name}.png")
    try:
        page.screenshot(path=path, full_page=True)
        return path
    except Exception as e:
        return f"(截图失败: {e})"


def safe_url(page, path):
    """访问页面并捕获状态码与错误"""
    url = f"{BASE_URL}{CTX}{path}"
    response = None
    try:
        response = page.goto(url, timeout=GOTO_TIMEOUT, wait_until="domcontentloaded")
    except PlaywrightTimeoutError:
        return None, "goto timeout", url
    except Exception as e:
        return None, f"goto error: {e}", url
    try:
        page.wait_for_load_state("networkidle", timeout=LOAD_TIMEOUT)
    except Exception:
        # networkidle 经常因为长连接/轮询超时，不致命
        pass
    return response, "", url


def status_label(response):
    if response is None:
        return "no-response"
    return f"HTTP {response.status}"


def fill_login_form(page):
    """使用多种选择器回退填写登录表单"""
    username_selectors = [
        'input[placeholder*="用户名"]',
        'input[placeholder*="username"]',
        '#username',
        'input[name="username"]',
        'input[type="text"]',
        'form input:nth-of-type(1)',
    ]
    filled_user = False
    for sel in username_selectors:
        try:
            loc = page.locator(sel).first
            if loc.count() > 0 and loc.is_visible(timeout=2000):
                loc.fill(USERNAME, timeout=5000)
                filled_user = True
                break
        except Exception:
            continue
    if not filled_user:
        raise RuntimeError("未找到用户名输入框")

    password_selectors = [
        'input[type="password"]',
        'input[placeholder*="密码"]',
        'input[name="password"]',
        '#password',
    ]
    filled_pwd = False
    for sel in password_selectors:
        try:
            loc = page.locator(sel).first
            if loc.count() > 0 and loc.is_visible(timeout=2000):
                loc.fill(PASSWORD, timeout=5000)
                filled_pwd = True
                break
        except Exception:
            continue
    if not filled_pwd:
        raise RuntimeError("未找到密码输入框")

    btn_selectors = [
        'button:has-text("登 录")',
        'button:has-text("登录")',
        'button[type="submit"]',
        '.login-btn',
        'button.el-button--primary',
    ]
    clicked = False
    for sel in btn_selectors:
        try:
            loc = page.locator(sel).first
            if loc.count() > 0 and loc.is_visible(timeout=2000):
                loc.click(timeout=5000)
                clicked = True
                break
        except Exception:
            continue
    if not clicked:
        raise RuntimeError("未找到登录按钮")


def login_and_save_state(browser):
    """登录并保存 storage_state，返回 state_path 或 None"""
    context = browser.new_context(ignore_https_errors=True, locale="zh-CN")
    page = context.new_page()
    page.on("console", lambda msg: console_errors_global.append(msg.text) if msg.type == "error" else None)
    try:
        resp, err, url = safe_url(page, "/login")
        if err:
            raise RuntimeError(f"登录页加载失败: {err}")
        fill_login_form(page)
        try:
            page.wait_for_url("**/dashboard**", timeout=LOAD_TIMEOUT)
        except Exception:
            try:
                page.wait_for_function(
                    "() => !!localStorage.getItem('kb_access_token')",
                    timeout=LOAD_TIMEOUT,
                )
                page.goto(f"{BASE_URL}{CTX}/dashboard", timeout=GOTO_TIMEOUT)
                page.wait_for_load_state("domcontentloaded", timeout=LOAD_TIMEOUT)
            except Exception as e:
                raise RuntimeError(f"登录后未跳转 dashboard: {e}")
        os.makedirs(REPORT_DIR, exist_ok=True)
        context.storage_state(path=STATE_PATH)
        return STATE_PATH
    finally:
        context.close()


def new_auth_context(browser, state_path):
    return browser.new_context(
        storage_state=state_path,
        ignore_https_errors=True,
        locale="zh-CN",
    )


def has_meaningful_content(page):
    try:
        body = page.locator("body")
        if body.count() == 0:
            return False
        text = body.inner_text(timeout=3000).strip()
        return len(text) > 20
    except Exception:
        return False


def body_text(page):
    try:
        return page.locator("body").inner_text(timeout=3000)
    except Exception:
        return ""


# ---------------------------------------------------------------------------
# 原有 13 个用例（与 test_e2e_full.py 等价，仅 case_id 保持一致）
# ---------------------------------------------------------------------------
def test_login_page(browser):
    case_id = "L3-PAGE-001"
    desc = "登录页 /kb/login"
    context = browser.new_context(ignore_https_errors=True, locale="zh-CN")
    page = context.new_page()
    page.on("console", lambda msg: console_errors_global.append(msg.text) if msg.type == "error" else None)
    try:
        resp, err, url = safe_url(page, "/login")
        if err:
            record(case_id, desc, "FAIL", f"加载失败: {err}")
            screenshot(page, case_id)
            return
        has_input = page.locator('input').count() > 0
        has_button = page.locator('button').count() > 0
        title = page.title()
        if has_input and has_button:
            record(case_id, desc, "PASS", f"{status_label(resp)} | title={title} | 含 input+button")
        else:
            record(case_id, desc, "FAIL", f"{status_label(resp)} | input={has_input} button={has_button}")
            screenshot(page, case_id)
    except Exception as e:
        record(case_id, desc, "FAIL", f"异常: {e}")
        screenshot(page, case_id)
    finally:
        context.close()


def test_protected_page(browser, state_path, case_id, desc, path, expect_text_keywords=None):
    if not state_path:
        record(case_id, desc, "FAIL", "未获得登录态 storage_state")
        return
    context = new_auth_context(browser, state_path)
    page = context.new_page()
    page.on("console", lambda msg: console_errors_global.append(msg.text) if msg.type == "error" else None)
    try:
        resp, err, url = safe_url(page, path)
        if err:
            record(case_id, desc, "FAIL", f"加载失败: {err}")
            screenshot(page, case_id)
            return
        status = resp.status if resp else 0
        current_url = page.url
        if "/login" in current_url and "/login" not in path:
            record(case_id, desc, "FAIL", f"重定向到登录页（登录态失效） url={current_url}")
            screenshot(page, case_id)
            return
        if status in (500, 502, 503, 504):
            record(case_id, desc, "FAIL", f"{status_label(resp)} | 后端错误（可能 VM 离线或超时）")
            screenshot(page, case_id)
            return
        if status == 401:
            record(case_id, desc, "FAIL", f"{status_label(resp)} | 401 未授权")
            screenshot(page, case_id)
            return
        if status == 404:
            record(case_id, desc, "FAIL", f"{status_label(resp)} | 404 路由不存在")
            screenshot(page, case_id)
            return
        if not has_meaningful_content(page):
            record(case_id, desc, "FAIL", f"{status_label(resp)} | 页面无可见内容")
            screenshot(page, case_id)
            return
        kw_hit = ""
        if expect_text_keywords:
            try:
                bt = body_text(page)
                hit_kws = [k for k in expect_text_keywords if k in bt]
                kw_hit = f" | 命中关键字: {hit_kws}" if hit_kws else ""
            except Exception:
                pass
        record(case_id, desc, "PASS", f"{status_label(resp)}{kw_hit} | url={current_url}")
    except Exception as e:
        record(case_id, desc, "FAIL", f"异常: {e}")
        screenshot(page, case_id)
    finally:
        context.close()


def test_share_verify_page(browser):
    case_id = "L3-PAGE-013"
    desc = "分享页 /kb/share/non-existent-code"
    context = browser.new_context(ignore_https_errors=True, locale="zh-CN")
    page = context.new_page()
    page.on("console", lambda msg: console_errors_global.append(msg.text) if msg.type == "error" else None)
    try:
        resp, err, url = safe_url(page, "/share/non-existent-code")
        if err:
            record(case_id, desc, "FAIL", f"加载失败: {err}")
            screenshot(page, case_id)
            return
        status = resp.status if resp else 0
        if status in (500, 502, 503, 504):
            record(case_id, desc, "FAIL", f"{status_label(resp)} | 后端错误")
            screenshot(page, case_id)
            return
        current_url = page.url
        if "/login" in current_url:
            record(case_id, desc, "FAIL",
                   f"{status_label(resp)} | 公开分享页被重定向到登录页 url={current_url}（应显示'分享不存在'提示）")
            screenshot(page, case_id)
            return
        if not has_meaningful_content(page):
            record(case_id, desc, "FAIL", f"{status_label(resp)} | 页面无可见内容")
            screenshot(page, case_id)
            return
        record(case_id, desc, "PASS", f"{status_label(resp)} | url={current_url}")
    except Exception as e:
        record(case_id, desc, "FAIL", f"异常: {e}")
        screenshot(page, case_id)
    finally:
        context.close()


# ---------------------------------------------------------------------------
# 新增 4 个用例（标签管理 / 分享中心 / 文件管理 / 运维日志）
# ---------------------------------------------------------------------------
def _wait_for_spinner_gone(page, timeout=8000):
    """等待 Element Plus loading 指示器消失，避免误判空表格"""
    try:
        page.wait_for_selector(
            ".el-loading-mask, .el-loading-spinner",
            state="hidden",
            timeout=timeout,
        )
    except Exception:
        pass


def _common_page_precheck(page, path, case_id, desc):
    """返回 (ok, resp, current_url) 或在失败时已 record 并返回 (False, None, None)"""
    resp, err, url = safe_url(page, path)
    if err:
        record(case_id, desc, "FAIL", f"加载失败: {err}")
        screenshot(page, case_id)
        return False, None, None
    status = resp.status if resp else 0
    current_url = page.url
    if "/login" in current_url and "/login" not in path:
        record(case_id, desc, "FAIL", f"重定向到登录页（登录态失效） url={current_url}")
        screenshot(page, case_id)
        return False, None, None
    if status in (500, 502, 503, 504):
        record(case_id, desc, "FAIL", f"{status_label(resp)} | 后端错误")
        screenshot(page, case_id)
        return False, None, None
    if status == 401:
        record(case_id, desc, "FAIL", f"{status_label(resp)} | 401 未授权")
        screenshot(page, case_id)
        return False, None, None
    if status == 404:
        record(case_id, desc, "FAIL", f"{status_label(resp)} | 404 路由不存在")
        screenshot(page, case_id)
        return False, None, None
    if not has_meaningful_content(page):
        record(case_id, desc, "FAIL", f"{status_label(resp)} | 页面无可见内容")
        screenshot(page, case_id)
        return False, None, None
    return True, resp, current_url


def test_tag_page(browser, state_path):
    """L3-PAGE-TAG: 标签管理 /kb/tag"""
    case_id = "L3-PAGE-TAG"
    desc = "标签管理 /kb/tag"
    if not state_path:
        record(case_id, desc, "FAIL", "未获得登录态 storage_state")
        return
    context = new_auth_context(browser, state_path)
    page = context.new_page()
    page.on("console", lambda msg: console_errors_global.append(msg.text) if msg.type == "error" else None)
    try:
        ok, resp, current_url = _common_page_precheck(page, "/tag", case_id, desc)
        if not ok:
            return
        _wait_for_spinner_gone(page)
        bt = body_text(page)

        # 校验 1：标题"标签管理"
        title_ok = "标签管理" in bt or "标签" in bt

        # 校验 2：表格渲染（el-table 或标签云）
        table_count = page.locator('.el-table, .el-table__body-wrapper, .tag-cloud, .tag-item').count()
        table_ok = table_count > 0

        # 校验 3："新建标签"按钮
        btn_selectors = [
            'button:has-text("新建标签")',
            'button:has-text("新建")',
            'button:has-text("创建标签")',
            'button:has-text("新增标签")',
            '.el-button--primary:has-text("标签")',
        ]
        btn_ok = False
        for sel in btn_selectors:
            try:
                loc = page.locator(sel).first
                if loc.count() > 0 and loc.is_visible(timeout=2000):
                    btn_ok = True
                    break
            except Exception:
                continue

        detail = f"{status_label(resp)} | 标题={'OK' if title_ok else 'MISS'} 表格={table_count} 新建按钮={'OK' if btn_ok else 'MISS'} | url={current_url}"
        if title_ok and table_ok and btn_ok:
            record(case_id, desc, "PASS", detail)
        else:
            record(case_id, desc, "FAIL", detail)
        screenshot(page, case_id)
    except Exception as e:
        record(case_id, desc, "FAIL", f"异常: {e}")
        traceback.print_exc()
        screenshot(page, case_id)
    finally:
        context.close()


def test_share_center_page(browser, state_path):
    """L3-PAGE-SHARE: 分享中心 /kb/share"""
    case_id = "L3-PAGE-SHARE"
    desc = "分享中心 /kb/share"
    if not state_path:
        record(case_id, desc, "FAIL", "未获得登录态 storage_state")
        return
    context = new_auth_context(browser, state_path)
    page = context.new_page()
    page.on("console", lambda msg: console_errors_global.append(msg.text) if msg.type == "error" else None)
    try:
        ok, resp, current_url = _common_page_precheck(page, "/share", case_id, desc)
        if not ok:
            return
        _wait_for_spinner_gone(page)
        bt = body_text(page)

        # 校验 1：标题"分享中心"
        title_ok = "分享中心" in bt or "分享" in bt

        # 校验 2：分享列表表格渲染
        table_count = page.locator(
            '.el-table, .el-table__body-wrapper, .share-list, .share-card'
        ).count()
        table_ok = table_count > 0

        detail = f"{status_label(resp)} | 标题={'OK' if title_ok else 'MISS'} 表格={table_count} | url={current_url}"
        if title_ok and table_ok:
            record(case_id, desc, "PASS", detail)
        else:
            record(case_id, desc, "FAIL", detail)
        screenshot(page, case_id)
    except Exception as e:
        record(case_id, desc, "FAIL", f"异常: {e}")
        traceback.print_exc()
        screenshot(page, case_id)
    finally:
        context.close()


def test_file_management_page(browser, state_path):
    """L3-PAGE-FILE: 文件管理 /kb/file"""
    case_id = "L3-PAGE-FILE"
    desc = "文件管理 /kb/file"
    if not state_path:
        record(case_id, desc, "FAIL", "未获得登录态 storage_state")
        return
    context = new_auth_context(browser, state_path)
    page = context.new_page()
    page.on("console", lambda msg: console_errors_global.append(msg.text) if msg.type == "error" else None)
    try:
        ok, resp, current_url = _common_page_precheck(page, "/file", case_id, desc)
        if not ok:
            return
        _wait_for_spinner_gone(page)
        bt = body_text(page)

        # 校验 1：标题"文件管理"
        title_ok = "文件管理" in bt or "文件" in bt

        # 校验 2：上传按钮存在（多种文案回退）
        upload_selectors = [
            'button:has-text("上传")',
            'button:has-text("上传文件")',
            'button:has-text("选择文件")',
            '.el-upload',
            '.el-upload__input',
            'input[type="file"]',
            '[class*="upload"]',
        ]
        upload_ok = False
        for sel in upload_selectors:
            try:
                loc = page.locator(sel).first
                if loc.count() > 0:
                    upload_ok = True
                    break
            except Exception:
                continue

        detail = f"{status_label(resp)} | 标题={'OK' if title_ok else 'MISS'} 上传按钮={'OK' if upload_ok else 'MISS'} | url={current_url}"
        if title_ok and upload_ok:
            record(case_id, desc, "PASS", detail)
        else:
            record(case_id, desc, "FAIL", detail)
        screenshot(page, case_id)
    except Exception as e:
        record(case_id, desc, "FAIL", f"异常: {e}")
        traceback.print_exc()
        screenshot(page, case_id)
    finally:
        context.close()


def test_ops_log_page(browser, state_path):
    """L3-PAGE-OPS-LOG: 运维日志 /kb/ops/log"""
    case_id = "L3-PAGE-OPS-LOG"
    desc = "运维日志 /kb/ops/log"
    if not state_path:
        record(case_id, desc, "FAIL", "未获得登录态 storage_state")
        return
    context = new_auth_context(browser, state_path)
    page = context.new_page()
    page.on("console", lambda msg: console_errors_global.append(msg.text) if msg.type == "error" else None)
    try:
        ok, resp, current_url = _common_page_precheck(page, "/ops/log", case_id, desc)
        if not ok:
            return
        _wait_for_spinner_gone(page)
        bt = body_text(page)

        # 校验 1：标题"操作日志"
        title_ok = "操作日志" in bt or "日志" in bt

        # 校验 2：日志表格渲染
        table_count = page.locator('.el-table, .el-table__body-wrapper').count()
        table_ok = table_count > 0

        # 校验 3：action 筛选下拉框存在（el-select 或包含 action 关键字的可筛选元素）
        select_count = page.locator(
            '.el-select, select, [class*="filter"] .el-select, .el-select:has(label:has-text("action"))'
        ).count()
        # 回退：检查页面是否出现"操作类型"字样（action 筛选标签）
        action_filter_ok = select_count > 0 or "操作类型" in bt or "action" in bt.lower()

        detail = f"{status_label(resp)} | 标题={'OK' if title_ok else 'MISS'} 表格={table_count} action筛选={'OK' if action_filter_ok else 'MISS'} | url={current_url}"
        if title_ok and table_ok and action_filter_ok:
            record(case_id, desc, "PASS", detail)
        else:
            record(case_id, desc, "FAIL", detail)
        screenshot(page, case_id)
    except Exception as e:
        record(case_id, desc, "FAIL", f"异常: {e}")
        traceback.print_exc()
        screenshot(page, case_id)
    finally:
        context.close()


# ---------------------------------------------------------------------------
# 主流程
# ---------------------------------------------------------------------------
def main():
    os.makedirs(REPORT_DIR, exist_ok=True)
    print("=" * 70)
    print("  L3 E2E 测试 v2 - MyKNG 知识库（15 个页面 = 13 原有 + 4 新增）")
    print(f"  目标: {BASE_URL}{CTX}/")
    print(f"  账号: {USERNAME}")
    print(f"  时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print(f"  浏览器: headless Chromium (PLAYWRIGHT_BROWSERS_PATH={os.environ.get('PLAYWRIGHT_BROWSERS_PATH','未设置')})")
    print("=" * 70)

    with sync_playwright() as p:
        browser = p.chromium.launch(
            headless=True,
            args=["--ignore-certificate-errors", "--no-sandbox", "--disable-dev-shm-usage"],
        )

        # L3-PAGE-001: 登录页（无需登录）
        print("\n--- [1/4] 登录页测试 ---")
        test_login_page(browser)

        # 登录并保存状态
        print("\n--- [2/4] 登录获取 storage_state ---")
        state_path = None
        try:
            state_path = login_and_save_state(browser)
            if state_path:
                print(f"[INFO] 登录成功，state 保存至: {state_path}")
            else:
                print("[WARN] 登录失败，后续受保护页面将无法通过")
        except Exception as e:
            state_path = None
            print(f"[ERROR] 登录异常: {e}")
            traceback.print_exc()

        # L3-PAGE-002~012: 受保护页面（11 个原有页面）
        print("\n--- [3/4] 受保护页面测试（原有 11 个） ---")
        test_protected_page(browser, state_path, "L3-PAGE-002", "仪表盘 /kb/dashboard",
                            "/dashboard", expect_text_keywords=["仪表盘", "统计", "文档", "空间"])
        test_protected_page(browser, state_path, "L3-PAGE-003", "空间内容 /kb/space/1",
                            "/space/1", expect_text_keywords=["空间", "文件夹"])
        test_protected_page(browser, state_path, "L3-PAGE-004", "文件详情 /kb/file/1",
                            "/file/1")
        test_protected_page(browser, state_path, "L3-PAGE-005", "创建文档 /kb/doc/create",
                            "/doc/create")
        test_protected_page(browser, state_path, "L3-PAGE-006", "文档详情 /kb/doc/1",
                            "/doc/1")
        test_protected_page(browser, state_path, "L3-PAGE-007", "网页详情 /kb/web/1",
                            "/web/1")
        test_protected_page(browser, state_path, "L3-PAGE-008", "搜索页 /kb/search",
                            "/search", expect_text_keywords=["搜索"])
        test_protected_page(browser, state_path, "L3-PAGE-009", "回收站 /kb/trash",
                            "/trash", expect_text_keywords=["回收站"])
        test_protected_page(browser, state_path, "L3-PAGE-010", "设置 /kb/settings",
                            "/settings", expect_text_keywords=["设置", "用户"])
        test_protected_page(browser, state_path, "L3-PAGE-011", "运维总览 /kb/ops",
                            "/ops", expect_text_keywords=["运维", "看板", "主机"])
        test_protected_page(browser, state_path, "L3-PAGE-012", "主机管理 /kb/ops/hosts",
                            "/ops/hosts", expect_text_keywords=["主机"])

        # L3-PAGE-013: 分享验证页（公开）
        print("\n--- 公开分享验证页测试 ---")
        test_share_verify_page(browser)

        # 新增 4 个页面
        print("\n--- [4/4] 新增 4 个页面测试 ---")
        test_tag_page(browser, state_path)
        test_share_center_page(browser, state_path)
        test_file_management_page(browser, state_path)
        test_ops_log_page(browser, state_path)

        browser.close()

    # 汇总
    total = len(results)
    passed = sum(1 for r in results if r["status"] == "PASS")
    failed = sum(1 for r in results if r["status"] == "FAIL")
    pass_rate = (passed / total * 100) if total else 0
    print("\n" + "=" * 70)
    print(f"  L3 E2E v2 测试汇总: 总计={total} 通过={passed} 失败={failed} 通过率={pass_rate:.1f}%")
    print("=" * 70)
    if failed > 0:
        print("\n失败用例:")
        for r in results:
            if r["status"] == "FAIL":
                print(f"  [FAIL] {r['case_id']} {r['desc']}")
                print(f"         {r['detail']}")
                shot_path = os.path.join(REPORT_DIR, f"{r['case_id']}.png")
                if os.path.exists(shot_path):
                    print(f"         截图: {shot_path}")

    print(f"\n浏览器控制台错误（共 {len(console_errors_global)} 条，最多显示 10 条）:")
    if console_errors_global:
        for err in console_errors_global[:10]:
            print(f"  - {err[:200]}")
    else:
        print("  无")

    # 写入汇总结果文件（供报告生成读取）
    summary_path = os.path.join(REPORT_DIR, "L3_E2E_v2_summary.txt")
    try:
        with open(summary_path, "w", encoding="utf-8") as f:
            f.write(f"total={total}\npassed={passed}\nfailed={failed}\npass_rate={pass_rate:.1f}\n")
            f.write("\n--- 用例明细 ---\n")
            for r in results:
                f.write(f"{r['case_id']}|{r['desc']}|{r['status']}|{r['detail']}\n")
        print(f"\n[INFO] 汇总结果已写入: {summary_path}")
    except Exception as e:
        print(f"[WARN] 写入汇总失败: {e}")

    return 0 if failed == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
