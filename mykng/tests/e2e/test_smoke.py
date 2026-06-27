#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Playwright 冒烟测试 - 验证环境可用"""
from playwright.sync_api import sync_playwright
import os

def test_smoke():
    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True)
        context = browser.new_context(ignore_https_errors=True)
        page = context.new_page()
        page.goto("https://kb.marschat.online/kb/", timeout=30000)
        page.wait_for_load_state("networkidle", timeout=15000)
        title = page.title()
        print(f"页面标题: {title}")
        # 截图保存
        os.makedirs("d:/huliang/java/ideaworkspace/devtools/mykng/tests/reports", exist_ok=True)
        page.screenshot(path="d:/huliang/java/ideaworkspace/devtools/mykng/tests/reports/smoke_login.png")
        print("截图已保存: tests/reports/smoke_login.png")
        browser.close()

if __name__ == "__main__":
    test_smoke()
    print("Playwright 冒烟测试通过")
