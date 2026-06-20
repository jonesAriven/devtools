#!/usr/bin/env python3
"""
mykng 知识库 - HTTP 级 UI 页面测试
测试前端页面加载、静态资源、SPA路由、API集成
"""

import requests
import json
import sys
import time
from datetime import datetime

BASE_URL = "https://tools.marschat.online"
CTX = "/kb"

class UIPageTest:
    def __init__(self):
        self.base_url = BASE_URL
        self.ctx = CTX
        self.session = requests.Session()
        self.results = []
        self.total = 0
        self.passed = 0
        self.failed = 0
        self.skipped = 0

    def log(self, status, name, detail="", elapsed=0):
        self.total += 1
        if status == "PASS":
            self.passed += 1
            icon = "✅"
        elif status == "FAIL":
            self.failed += 1
            icon = "❌"
        else:
            self.skipped += 1
            icon = "⏭️"
        self.results.append({"status": status, "name": name, "detail": detail, "elapsed": elapsed})
        print(f"  {icon} {name} ({elapsed:.1f}s) {detail[:60] if detail else ''}")

    def test_page(self, path, expected_status=200, expect_html=True):
        """测试页面是否正常加载"""
        url = f"{self.base_url}{self.ctx}{path}"
        try:
            resp = self.session.get(url, timeout=10, allow_redirects=False)
            elapsed = 0.001
            
            if resp.status_code == expected_status:
                if expect_html and 'text/html' in resp.headers.get('Content-Type', ''):
                    self.log("PASS", f"页面 {path}", f"HTTP {resp.status_code}")
                elif not expect_html:
                    self.log("PASS", f"页面 {path}", f"HTTP {resp.status_code}")
                else:
                    self.log("FAIL", f"页面 {path}", f"Content-Type: {resp.headers.get('Content-Type')}")
            else:
                self.log("FAIL", f"页面 {path}", f"HTTP {resp.status_code} (期望 {expected_status})")
        except Exception as e:
            self.log("FAIL", f"页面 {path}", str(e))

    def test_redirect(self, from_path, to_path, expected_status=302):
        """测试重定向"""
        url = f"{self.base_url}{from_path}"
        try:
            resp = self.session.get(url, timeout=10, allow_redirects=False)
            if resp.status_code == expected_status:
                location = resp.headers.get('Location', '')
                if to_path in location:
                    self.log("PASS", f"重定向 {from_path} → {to_path}", f"HTTP {resp.status_code}")
                else:
                    self.log("FAIL", f"重定向 {from_path}", f"Location: {location}")
            else:
                self.log("FAIL", f"重定向 {from_path}", f"HTTP {resp.status_code} (期望 {expected_status})")
        except Exception as e:
            self.log("FAIL", f"重定向 {from_path}", str(e))

    def test_static_asset(self, asset_path, expected_type=None):
        """测试静态资源"""
        url = f"{self.base_url}{self.ctx}{asset_path}"
        try:
            resp = self.session.get(url, timeout=10)
            if resp.status_code == 200:
                content_type = resp.headers.get('Content-Type', '')
                if expected_type and expected_type not in content_type:
                    self.log("FAIL", f"资源 {asset_path}", f"Content-Type: {content_type}")
                else:
                    size = len(resp.content)
                    self.log("PASS", f"资源 {asset_path}", f"{size//1024}KB {content_type}")
            else:
                self.log("FAIL", f"资源 {asset_path}", f"HTTP {resp.status_code}")
        except Exception as e:
            self.log("FAIL", f"资源 {asset_path}", str(e))

    def test_spa_fallback(self, path):
        """测试 SPA 路由回退（非真实文件路径应返回 index.html）"""
        url = f"{self.base_url}{self.ctx}{path}"
        try:
            resp = self.session.get(url, timeout=10)
            if resp.status_code == 200 and '<div id="app">' in resp.text:
                self.log("PASS", f"SPA回退 {path}", "返回 index.html")
            else:
                has_app = '<div id="app">' in resp.text
                self.log("FAIL", f"SPA回退 {path}", f"HTTP {resp.status_code}, app div: {has_app}")
        except Exception as e:
            self.log("FAIL", f"SPA回退 {path}", str(e))

    def test_api_from_frontend(self, api_path, method="GET", data=None, expected_code=200):
        """测试前端视角的 API 调用"""
        url = f"{self.base_url}{self.ctx}/api{api_path}"
        try:
            if method == "GET":
                resp = self.session.get(url, timeout=10)
            elif method == "POST":
                resp = self.session.post(url, json=data, timeout=10)
            
            if resp.status_code == 200:
                body = resp.json()
                if body.get("code") == 200 or body.get("code") == 0:
                    self.log("PASS", f"API {method} {api_path}", f"code={body['code']}")
                else:
                    self.log("FAIL", f"API {method} {api_path}", f"code={body.get('code')} msg={body.get('message','')[:40]}")
            else:
                self.log("FAIL", f"API {method} {api_path}", f"HTTP {resp.status_code}")
        except Exception as e:
            self.log("FAIL", f"API {method} {api_path}", str(e))

    def test_html_structure(self):
        """测试 HTML 结构完整性"""
        url = f"{self.base_url}{self.ctx}/"
        try:
            resp = self.session.get(url, timeout=10)
            html = resp.text
            
            checks = [
                ("DOCTYPE", "<!DOCTYPE html>" in html),
                ("lang=zh-CN", 'lang="zh-CN"' in html),
                ("meta charset", 'charset="UTF-8"' in html),
                ("viewport", 'viewport' in html),
                ("title", '<title>' in html),
                ("app div", '<div id="app">' in html),
                ("JS module", 'type="module"' in html),
                ("context path /kb", '/kb/s/' in html),
                ("无硬编码 /favicon", '/favicon.svg' not in html),
                ("无硬编码 /src/", '/src/main.ts' not in html),
            ]
            
            all_pass = True
            for name, ok in checks:
                if not ok:
                    all_pass = False
                    self.log("FAIL", f"HTML结构: {name}", "检查未通过")
            
            if all_pass:
                self.log("PASS", "HTML结构完整性", f"{len(checks)} 项全部通过")
        except Exception as e:
            self.log("FAIL", "HTML结构完整性", str(e))

    def test_cache_headers(self):
        """测试缓存头"""
        try:
            # 静态资源应有长缓存
            resp = self.session.get(f"{self.base_url}{self.ctx}/", timeout=10)
            html = resp.text
            
            # 提取 JS 文件路径
            import re
            js_match = re.search(r'src="(/kb/s/assets/[^"]+\.js)"', html)
            if js_match:
                js_url = f"{self.base_url}{js_match.group(1)}"
                resp = self.session.get(js_url, timeout=10)
                cache_control = resp.headers.get('Cache-Control', '')
                if 'immutable' in cache_control or 'max-age' in cache_control:
                    self.log("PASS", "静态资源缓存头", f"Cache-Control: {cache_control}")
                else:
                    self.log("FAIL", "静态资源缓存头", f"Cache-Control: {cache_control}")
            else:
                self.log("SKIP", "静态资源缓存头", "未找到 JS 文件路径")
        except Exception as e:
            self.log("FAIL", "静态资源缓存头", str(e))

    def test_cors_headers(self):
        """测试 CORS 头"""
        try:
            resp = self.session.options(
                f"{self.base_url}{self.ctx}/api/auth/login",
                headers={"Origin": "https://example.com"},
                timeout=10
            )
            cors_origin = resp.headers.get('Access-Control-Allow-Origin', '')
            if cors_origin:
                self.log("PASS", "CORS 配置", f"Allow-Origin: {cors_origin}")
            else:
                # 可能是 Nginx 层处理了 CORS，检查实际请求
                resp = self.session.get(
                    f"{self.base_url}{self.ctx}/api/auth/login",
                    headers={"Origin": "https://example.com"},
                    timeout=10
                )
                cors_origin = resp.headers.get('Access-Control-Allow-Origin', '')
                if cors_origin:
                    self.log("PASS", "CORS 配置", f"Allow-Origin: {cors_origin}")
                else:
                    self.log("FAIL", "CORS 配置", "未返回 CORS 头")
        except Exception as e:
            self.log("FAIL", "CORS 配置", str(e))

    def test_login_flow(self):
        """测试前端登录流程（API 级模拟）"""
        try:
            # 1. 登录
            resp = self.session.post(
                f"{self.base_url}{self.ctx}/api/auth/login",
                json={"username": "admin", "password": "admin123"},
                timeout=10
            )
            if resp.status_code == 200:
                body = resp.json()
                if body.get("code") == 200 and body.get("data", {}).get("accessToken"):
                    self.log("PASS", "登录流程", "获取到 accessToken")
                    return body["data"]["accessToken"]
                else:
                    self.log("FAIL", "登录流程", f"code={body.get('code')} msg={body.get('message','')}")
            else:
                self.log("FAIL", "登录流程", f"HTTP {resp.status_code}")
        except Exception as e:
            self.log("FAIL", "登录流程", str(e))
        return None

    def run_all(self):
        print(f"\n{'='*60}")
        print(f"  mykng 知识库 - UI 页面测试")
        print(f"  目标: {self.base_url}{self.ctx}/")
        print(f"  时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
        print(f"{'='*60}\n")

        print("📋 1. 页面加载测试")
        self.test_page("/")
        self.test_page("/login")
        self.test_page("/dashboard")
        self.test_page("/space/1")
        self.test_page("/settings")
        self.test_page("/share/test")

        print("\n📋 2. 重定向测试")
        self.test_redirect("/", "/kb/")
        self.test_redirect("/kb", "/kb/")

        print("\n📋 3. 静态资源测试")
        # 从 index.html 提取实际资源路径
        resp = self.session.get(f"{self.base_url}{self.ctx}/", timeout=10)
        import re
        assets = re.findall(r'(?:src|href)="(/kb/s/assets/[^"]+)"', resp.text)
        for asset in assets[:5]:
            self.test_static_asset(asset.replace("/kb/s", "/s"))
        # CSS
        css_assets = re.findall(r'href="(/kb/s/assets/[^"]+\.css)"', resp.text)
        for asset in css_assets:
            self.test_static_asset(asset.replace("/kb/s", "/s"), "text/css")

        print("\n📋 4. SPA 路由回退测试")
        self.test_spa_fallback("/nonexistent")
        self.test_spa_fallback("/dashboard/sub/page")
        self.test_spa_fallback("/doc/123/edit")

        print("\n📋 5. HTML 结构验证")
        self.test_html_structure()

        print("\n📋 6. 缓存头测试")
        self.test_cache_headers()

        print("\n📋 7. CORS 配置测试")
        self.test_cors_headers()

        print("\n📋 8. 登录流程测试")
        token = self.test_login_flow()

        print("\n📋 9. 认证后 API 测试")
        if token:
            self.session.headers["Authorization"] = f"Bearer {token}"
            self.test_api_from_frontend("/user/profile")
            self.test_api_from_frontend("/space/list")
            self.test_api_from_frontend("/folder/tree/1")
            self.test_api_from_frontend("/tag/list")
            self.test_api_from_frontend("/bucket/list")
            self.test_api_from_frontend("/search?q=测试&type=&page=1&size=20")
            self.test_api_from_frontend("/trash/list?page=1&size=20")
            self.test_api_from_frontend("/ops/dashboard")
        else:
            self.log("SKIP", "认证后 API 测试", "无 token")

        print(f"\n{'='*60}")
        print(f"  UI 测试总结: {self.total} 个用例 | ✅ {self.passed} 通过 | ❌ {self.failed} 失败 | ⏭️ {self.skipped} 跳过")
        print(f"{'='*60}\n")

        return self.failed == 0


if __name__ == "__main__":
    tester = UIPageTest()
    success = tester.run_all()
    sys.exit(0 if success else 1)
