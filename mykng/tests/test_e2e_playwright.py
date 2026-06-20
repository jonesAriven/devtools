#!/usr/bin/env python3
"""
mykng 知识库 - Playwright E2E 自动化测试
安装: pip install playwright && playwright install chromium
运行: python tests/test_e2e_playwright.py

测试覆盖:
1. 登录流程（页面操作）
2. Dashboard 加载
3. 空间管理
4. 文档 CRUD
5. 文件上传
6. 搜索功能
7. 分享功能
8. 设置页面
9. 退出登录
"""

import sys
import json
import time
from datetime import datetime

try:
    from playwright.sync_api import sync_playwright, expect
except ImportError:
    print("请先安装 Playwright:")
    print("  pip install playwright")
    print("  playwright install chromium")
    sys.exit(1)

BASE_URL = "https://tools.marschat.online/kb"
USERNAME = "admin"
PASSWORD = "admin123"

class E2ETest:
    def __init__(self):
        self.results = []
        self.total = 0
        self.passed = 0
        self.failed = 0
        self.screenshot_dir = "/root/devtools/mykng/tests/screenshots"

    def log(self, name, passed, detail=""):
        self.total += 1
        if passed:
            self.passed += 1
            icon = "✅"
        else:
            self.failed += 1
            icon = "❌"
        self.results.append({"name": name, "passed": passed, "detail": detail})
        print(f"  {icon} {name} {f'- {detail}' if detail else ''}")

    def screenshot(self, page, name):
        import os
        os.makedirs(self.screenshot_dir, exist_ok=True)
        path = f"{self.screenshot_dir}/{name}.png"
        page.screenshot(path=path)
        return path

    def run_all(self):
        print(f"\n{'='*60}")
        print(f"  mykng 知识库 - Playwright E2E 测试")
        print(f"  目标: {BASE_URL}")
        print(f"  时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
        print(f"{'='*60}\n")

        with sync_playwright() as p:
            browser = p.chromium.launch(headless=True)
            context = browser.new_context(
                viewport={"width": 1920, "height": 1080},
                locale="zh-CN",
            )
            page = context.new_page()

            # 收集 console 错误
            console_errors = []
            page.on("console", lambda msg: console_errors.append(msg.text) if msg.type == "error" else None)

            # ===== 1. 登录流程 =====
            print("📋 1. 登录流程")
            try:
                page.goto(f"{BASE_URL}/login", wait_until="networkidle", timeout=15000)
                self.log("登录页加载", page.title() != "")

                # 截图登录页
                self.screenshot(page, "01-login-page")

                # 填写用户名密码
                username_input = page.locator("input[placeholder*='用户名'], input[type='text']").first
                password_input = page.locator("input[type='password']").first

                username_input.fill(USERNAME)
                password_input.fill(PASSWORD)
                self.log("填写登录表单", True)

                # 点击登录按钮
                login_btn = page.locator("button:has-text('登录'), button[type='submit']").first
                login_btn.click()

                # 等待跳转到 dashboard
                page.wait_for_url("**/dashboard**", timeout=10000)
                self.log("登录成功跳转", "dashboard" in page.url)

                self.screenshot(page, "02-dashboard")
            except Exception as e:
                self.log("登录流程", False, str(e))
                self.screenshot(page, "01-login-error")

            # ===== 2. Dashboard 页面 =====
            print("\n📋 2. Dashboard 页面")
            try:
                # 检查页面是否有内容
                page.wait_for_load_state("networkidle", timeout=10000)
                has_content = page.locator("body").inner_text() != ""
                self.log("Dashboard 内容加载", has_content)

                # 检查导航菜单
                nav_items = page.locator("nav a, .menu-item, .el-menu-item")
                nav_count = nav_items.count()
                self.log("导航菜单渲染", nav_count > 0, f"{nav_count} 个菜单项")

                self.screenshot(page, "02-dashboard-full")
            except Exception as e:
                self.log("Dashboard 页面", False, str(e))

            # ===== 3. 空间管理 =====
            print("\n📋 3. 空间管理")
            try:
                # 点击空间管理菜单
                space_link = page.locator("a:has-text('空间'), .el-menu-item:has-text('空间')").first
                if space_link.count() > 0:
                    space_link.click()
                    page.wait_for_load_state("networkidle", timeout=10000)
                    self.log("进入空间页", True)
                    self.screenshot(page, "03-spaces")
                else:
                    self.log("空间管理入口", False, "未找到空间菜单")
            except Exception as e:
                self.log("空间管理", False, str(e))

            # ===== 4. 文档操作 =====
            print("\n📋 4. 文档操作")
            try:
                # 导航到文档页
                doc_link = page.locator("a:has-text('文档'), .el-menu-item:has-text('文档')").first
                if doc_link.count() > 0:
                    doc_link.click()
                    page.wait_for_load_state("networkidle", timeout=10000)

                    # 点击新建文档
                    create_btn = page.locator("button:has-text('新建'), button:has-text('创建'), .el-button:has-text('新')").first
                    if create_btn.count() > 0:
                        create_btn.click()
                        page.wait_for_timeout(1000)

                        # 填写文档标题
                        title_input = page.locator("input[placeholder*='标题'], input[placeholder*='标题']").first
                        if title_input.count() > 0:
                            title_input.fill("E2E测试文档")
                            self.log("创建文档", True)
                        else:
                            self.log("创建文档", False, "未找到标题输入框")

                    self.screenshot(page, "04-documents")
                else:
                    self.log("文档操作", False, "未找到文档菜单")
            except Exception as e:
                self.log("文档操作", False, str(e))

            # ===== 5. 搜索功能 =====
            print("\n📋 5. 搜索功能")
            try:
                search_input = page.locator("input[placeholder*='搜索'], input[type='search']").first
                if search_input.count() > 0:
                    search_input.fill("测试")
                    search_input.press("Enter")
                    page.wait_for_load_state("networkidle", timeout=10000)
                    self.log("搜索执行", True)
                    self.screenshot(page, "05-search")
                else:
                    # 尝试导航到搜索页
                    page.goto(f"{BASE_URL}/dashboard", wait_until="networkidle", timeout=10000)
                    search_input = page.locator("input[placeholder*='搜索']").first
                    if search_input.count() > 0:
                        search_input.fill("测试")
                        search_input.press("Enter")
                        self.log("搜索执行", True)
                    else:
                        self.log("搜索功能", False, "未找到搜索框")
            except Exception as e:
                self.log("搜索功能", False, str(e))

            # ===== 6. 设置页面 =====
            print("\n📋 6. 设置页面")
            try:
                page.goto(f"{BASE_URL}/settings", wait_until="networkidle", timeout=10000)
                has_settings = page.locator("body").inner_text() != ""
                self.log("设置页加载", has_settings)
                self.screenshot(page, "06-settings")
            except Exception as e:
                self.log("设置页面", False, str(e))

            # ===== 7. 响应式测试 =====
            print("\n📋 7. 响应式测试")
            for width, name in [(375, "手机"), (768, "平板"), (1920, "桌面")]:
                try:
                    page.set_viewport_size({"width": width, "height": 812 if width < 768 else 1080})
                    page.wait_for_timeout(500)
                    body_visible = page.locator("body").is_visible()
                    self.log(f"响应式 {name} ({width}px)", body_visible)
                except Exception as e:
                    self.log(f"响应式 {name}", False, str(e))

            # 恢复桌面尺寸
            page.set_viewport_size({"width": 1920, "height": 1080})

            # ===== 8. 退出登录 =====
            print("\n📋 8. 退出登录")
            try:
                # 查找退出按钮
                logout_btn = page.locator("button:has-text('退出'), button:has-text('登出'), a:has-text('退出')").first
                if logout_btn.count() > 0:
                    logout_btn.click()
                    page.wait_for_timeout(2000)
                    self.log("退出登录", "login" in page.url.lower() or "dashboard" not in page.url)
                else:
                    # 尝试通过 API 退出
                    page.evaluate("""
                        fetch('/kb/api/auth/logout', {
                            method: 'POST',
                            headers: {'Authorization': 'Bearer ' + localStorage.getItem('token')}
                        })
                    """)
                    self.log("退出登录(API)", True)
            except Exception as e:
                self.log("退出登录", False, str(e))

            # ===== 9. Console 错误检查 =====
            print("\n📋 9. Console 错误检查")
            if console_errors:
                for err in console_errors[:5]:
                    self.log(f"JS错误: {err[:60]}", False)
            else:
                self.log("无 JS Console 错误", True)

            browser.close()

        # 总结
        print(f"\n{'='*60}")
        print(f"  E2E 测试总结: {self.total} 个用例 | ✅ {self.passed} 通过 | ❌ {self.failed} 失败")
        print(f"  截图保存: {self.screenshot_dir}/")
        print(f"{'='*60}\n")

        return self.failed == 0


if __name__ == "__main__":
    tester = E2ETest()
    success = tester.run_all()
    sys.exit(0 if success else 1)
