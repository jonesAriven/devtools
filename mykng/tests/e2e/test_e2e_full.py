#!/usr/bin/env python3
# -*- coding: utf-8 -*-
r"""L3 E2E 测试 - 13 个前端页面可访问性（基于测试方案 6.3 节）

执行：
    $env:PLAYWRIGHT_BROWSERS_PATH="D:\huliang\java\ideaworkspace\devtools\.playwright-browsers"
    $env:PYTHONIOENCODING="utf-8"
    $env:PYTHONUTF8="1"
    python d:\huliang\java\ideaworkspace\devtools\mykng\tests\e2e\test_e2e_full.py
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
STATE_PATH = os.path.join(REPORT_DIR, "auth_state.json")

GOTO_TIMEOUT = 30000
LOAD_TIMEOUT = 15000

results = []
console_errors_global = []


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
    # 用户名输入框
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

    # 密码输入框
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

    # 登录按钮
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
        # 等待跳转至 dashboard
        try:
            page.wait_for_url("**/dashboard**", timeout=LOAD_TIMEOUT)
        except Exception:
            # 兜底：等待 token 写入 localStorage
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
    """复用 storage_state 创建已登录 context"""
    return browser.new_context(
        storage_state=state_path,
        ignore_https_errors=True,
        locale="zh-CN",
    )


def has_meaningful_content(page):
    """页面 body 是否有可见内容（避免空白 SPA）"""
    try:
        body = page.locator("body")
        if body.count() == 0:
            return False
        text = body.inner_text(timeout=3000).strip()
        return len(text) > 20
    except Exception:
        return False


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
        # 校验表单元素
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
    """通用受保护页面测试"""
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
        # 检查是否被重定向回登录页（说明 storage_state 未生效）
        current_url = page.url
        if "/login" in current_url and "/login" not in path:
            record(case_id, desc, "FAIL", f"重定向到登录页（登录态失效） url={current_url}")
            screenshot(page, case_id)
            return
        # 504 / 500
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
        # 关键字命中（可选）
        kw_hit = ""
        if expect_text_keywords:
            try:
                body_text = page.locator("body").inner_text(timeout=3000)
                hit_kws = [k for k in expect_text_keywords if k in body_text]
                kw_hit = f" | 命中关键字: {hit_kws}" if hit_kws else ""
            except Exception:
                pass
        record(case_id, desc, "PASS", f"{status_label(resp)}{kw_hit} | url={current_url}")
    except Exception as e:
        record(case_id, desc, "FAIL", f"异常: {e}")
        screenshot(page, case_id)
    finally:
        context.close()


def test_share_page(browser):
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
        # 分享页允许 200（显示验证页）或显示"分享不存在"提示
        if status in (500, 502, 503, 504):
            record(case_id, desc, "FAIL", f"{status_label(resp)} | 后端错误")
            screenshot(page, case_id)
            return
        # 关键校验：分享页是公开页面（requiresAuth:false），不应重定向到登录页
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


def main():
    os.makedirs(REPORT_DIR, exist_ok=True)
    print("=" * 70)
    print(f"  L3 E2E 测试 - MyKNG 知识库")
    print(f"  目标: {BASE_URL}{CTX}/")
    print(f"  账号: {USERNAME}")
    print(f"  时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print(f"  浏览器: headless Chromium (PLAYWRIGHT_BROWSERS_PATH={os.environ.get('PLAYWRIGHT_BROWSERS_PATH','未设置')})")
    print("=" * 70)

    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True)

        # L3-PAGE-001: 登录页（无需登录）
        test_login_page(browser)

        # 登录并保存状态
        print("\n--- 登录获取 storage_state ---")
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

        # L3-PAGE-002~012: 受保护页面
        print("\n--- 受保护页面测试 ---")
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

        # L3-PAGE-013: 分享页（无需登录）
        print("\n--- 公开页面测试 ---")
        test_share_page(browser)

        browser.close()

    # 汇总
    total = len(results)
    passed = sum(1 for r in results if r["status"] == "PASS")
    failed = sum(1 for r in results if r["status"] == "FAIL")
    pass_rate = (passed / total * 100) if total else 0
    print("\n" + "=" * 70)
    print(f"  L3 E2E 测试汇总: 总计={total} 通过={passed} 失败={failed} 通过率={pass_rate:.1f}%")
    print("=" * 70)
    if failed > 0:
        print("\n失败用例:")
        for r in results:
            if r["status"] == "FAIL":
                print(f"  [FAIL] {r['case_id']} {r['desc']}")
                print(f"         {r['detail']}")
                # 失败截图路径
                shot_path = os.path.join(REPORT_DIR, f"{r['case_id']}.png")
                if os.path.exists(shot_path):
                    print(f"         截图: {shot_path}")
    # 控制台错误
    print(f"\n浏览器控制台错误（共 {len(console_errors_global)} 条，最多显示 10 条）:")
    if console_errors_global:
        for err in console_errors_global[:10]:
            print(f"  - {err[:200]}")
    else:
        print("  无")

    return 0 if failed == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
