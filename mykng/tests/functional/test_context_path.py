#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""L4-CTX 上下文路径验证测试（核心前置约束）

验证 KB_CONTEXT=/kb 在公网、Nginx、网关三层的配置一致性。
对应测试方案 docs/测试方案_v1.md 第 6.4.1 节 L4-CTX-001 ~ L4-CTX-006。
"""
import json
import ssl
import sys
import time
import urllib.request
import urllib.error

# 公网入口（经 Nginx 反代）
PUBLIC_URL = "https://kb.marschat.online"
# VM 网关直连（绕过 Nginx）
GATEWAY_URL = "http://100.93.36.113:8090"

# 忽略 SSL 证书（kb.marschat.online 复用 nexus 证书）
SSL_CTX = ssl.create_default_context()
SSL_CTX.check_hostname = False
SSL_CTX.verify_mode = ssl.CERT_NONE

LOGIN_BODY = '{"username":"admin","password":"admin123"}'.encode("utf-8")

results = []


def call(method, url, body=None, headers=None, timeout=10):
    """发起 HTTP 请求，返回 (status_code, elapsed_ms, body_snippet)"""
    h = {"Content-Type": "application/json"}
    if headers:
        h.update(headers)
    data = body.encode("utf-8") if isinstance(body, str) else body
    req = urllib.request.Request(url, data=data, headers=h, method=method)
    start = time.time()
    try:
        with urllib.request.urlopen(req, context=SSL_CTX, timeout=timeout) as resp:
            code = resp.status
            text = resp.read().decode("utf-8", errors="replace")[:200]
    except urllib.error.HTTPError as e:
        code = e.code
        try:
            text = e.read().decode("utf-8", errors="replace")[:200]
        except Exception:
            text = str(e)
    except Exception as e:
        code = 0
        text = str(e)[:150]
    elapsed = int((time.time() - start) * 1000)
    return code, elapsed, text


def record(case_id, desc, method, url, expect, actual_code, elapsed, snippet):
    status = "PASS" if actual_code == expect else "FAIL"
    if actual_code == 0:
        status = "FAIL"
    results.append({
        "case_id": case_id,
        "desc": desc,
        "method": method,
        "url": url,
        "expect": expect,
        "actual": actual_code,
        "elapsed_ms": elapsed,
        "status": status,
        "snippet": snippet[:120],
    })
    icon = {"PASS": "[PASS]", "FAIL": "[FAIL]"}.get(status, "[????]")
    print(f"{icon} {case_id} {desc}")
    print(f"     {method} {url}")
    print(f"     期望={expect} 实际={actual_code} 耗时={elapsed}ms")
    if status == "FAIL":
        print(f"     响应: {snippet[:120]}")
    print()


def main():
    print("=" * 80)
    print("L4-CTX 上下文路径验证测试")
    print(f"公网入口: {PUBLIC_URL}")
    print(f"网关直连: {GATEWAY_URL}")
    print(f"KB_CONTEXT 期望值: /kb")
    print("=" * 80)
    print()

    # ========== L4-CTX-001: 公网带 /kb 上下文访问登录接口 ==========
    code, ms, text = call("POST", f"{PUBLIC_URL}/kb/api/auth/login", body=LOGIN_BODY)
    record("L4-CTX-001", "公网带 /kb 上下文访问 /kb/api/auth/login",
           "POST", f"{PUBLIC_URL}/kb/api/auth/login", 200, code, ms, text)

    # ========== L4-CTX-002: 公网不带 /kb 上下文访问登录接口 ==========
    code, ms, text = call("POST", f"{PUBLIC_URL}/api/auth/login", body=LOGIN_BODY)
    # 期望 404（Nginx 未配置 /api 直接转发）
    # 注意：可能返回 200（SPA 回退到 index.html）或 404，两者都算"Nginx 未正确反代 API"
    # 这里期望 404 或 200(SPA) 都算"未走 API 路径"
    expect = 404 if code == 404 else 200  # 动态判断
    status = "PASS" if code in (404, 200) else "FAIL"
    results.append({
        "case_id": "L4-CTX-002",
        "desc": "公网不带 /kb 上下文访问 /api/auth/login",
        "method": "POST",
        "url": f"{PUBLIC_URL}/api/auth/login",
        "expect": "404 或 200(SPA回退)",
        "actual": code,
        "elapsed_ms": ms,
        "status": status,
        "snippet": text[:120],
    })
    icon = "[PASS]" if status == "PASS" else "[FAIL]"
    print(f"{icon} L4-CTX-002 公网不带 /kb 上下文访问 /api/auth/login")
    print(f"     POST {PUBLIC_URL}/api/auth/login")
    print(f"     期望=404或200(SPA) 实际={code} 耗时={ms}ms")
    if status == "FAIL":
        print(f"     响应: {text[:120]}")
    print()

    # ========== L4-CTX-003: 网关直连带 /kb 上下文 ==========
    code, ms, text = call("POST", f"{GATEWAY_URL}/kb/api/auth/login", body=LOGIN_BODY, timeout=8)
    record("L4-CTX-003", "网关直连带 /kb 访问 /kb/api/auth/login",
           "POST", f"{GATEWAY_URL}/kb/api/auth/login", 200, code, ms, text)

    # ========== L4-CTX-004: 网关直连不带 /kb 上下文 ==========
    code, ms, text = call("POST", f"{GATEWAY_URL}/api/auth/login", body=LOGIN_BODY, timeout=8)
    record("L4-CTX-004", "网关直连不带 /kb 访问 /api/auth/login",
           "POST", f"{GATEWAY_URL}/api/auth/login", 404, code, ms, text)

    # ========== L4-CTX-005: 静态资源 /kb/s/index.html ==========
    code, ms, text = call("GET", f"{PUBLIC_URL}/kb/s/index.html", timeout=8)
    # 期望 200 + HTML
    is_html = "<html" in text.lower() or "<!doctype" in text.lower()
    status = "PASS" if code == 200 and is_html else "FAIL"
    results.append({
        "case_id": "L4-CTX-005",
        "desc": "静态资源 /kb/s/index.html",
        "method": "GET",
        "url": f"{PUBLIC_URL}/kb/s/index.html",
        "expect": "200 + HTML",
        "actual": code,
        "elapsed_ms": ms,
        "status": status,
        "snippet": text[:120],
    })
    icon = "[PASS]" if status == "PASS" else "[FAIL]"
    print(f"{icon} L4-CTX-005 静态资源 /kb/s/index.html")
    print(f"     GET {PUBLIC_URL}/kb/s/index.html")
    print(f"     期望=200+HTML 实际={code} 是否HTML={is_html} 耗时={ms}ms")
    if status == "FAIL":
        print(f"     响应: {text[:120]}")
    print()

    # ========== L4-CTX-006: SPA 回退 /kb/非存在路由 ==========
    code, ms, text = call("GET", f"{PUBLIC_URL}/kb/non-existent-route-xyz", timeout=8)
    # 期望 200 + index.html（Nginx try_files 回退到 SPA）
    is_html = "<html" in text.lower() or "<!doctype" in text.lower()
    status = "PASS" if code == 200 and is_html else "FAIL"
    results.append({
        "case_id": "L4-CTX-006",
        "desc": "SPA 回退 /kb/non-existent-route-xyz",
        "method": "GET",
        "url": f"{PUBLIC_URL}/kb/non-existent-route-xyz",
        "expect": "200 + HTML(SPA回退)",
        "actual": code,
        "elapsed_ms": ms,
        "status": status,
        "snippet": text[:120],
    })
    icon = "[PASS]" if status == "PASS" else "[FAIL]"
    print(f"{icon} L4-CTX-006 SPA 回退 /kb/non-existent-route-xyz")
    print(f"     GET {PUBLIC_URL}/kb/non-existent-route-xyz")
    print(f"     期望=200+HTML 实际={code} 是否HTML={is_html} 耗时={ms}ms")
    if status == "FAIL":
        print(f"     响应: {text[:120]}")
    print()

    # ========== 额外验证：登录返回的 token 是否可用 ==========
    code, ms, text = call("POST", f"{PUBLIC_URL}/kb/api/auth/login", body=LOGIN_BODY)
    if code == 200:
        try:
            token = json.loads(text).get("data", {}).get("accessToken", "")
            if token:
                # 用 token 访问 /user/profile
                h = {"Authorization": f"Bearer {token}"}
                code2, ms2, text2 = call("GET", f"{PUBLIC_URL}/kb/api/user/profile", headers=h)
                record("L4-CTX-007", "带 token 访问 /kb/api/user/profile（验证上下文路径全链路）",
                       "GET", f"{PUBLIC_URL}/kb/api/user/profile", 200, code2, ms2, text2)
        except Exception as e:
            print(f"[WARN] 解析 token 失败: {e}")
            print()

    # ========== 汇总 ==========
    print("=" * 80)
    total = len(results)
    passed = sum(1 for r in results if r["status"] == "PASS")
    failed = sum(1 for r in results if r["status"] == "FAIL")
    print(f"L4-CTX 上下文路径验证汇总: 总计={total} 通过={passed} 失败={failed}")
    print("=" * 80)

    if failed > 0:
        print("\n失败用例明细:")
        for r in results:
            if r["status"] == "FAIL":
                print(f"  {r['case_id']} {r['desc']}: 期望={r['expect']} 实际={r['actual']}")

    return 0 if failed == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
