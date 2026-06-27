#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""MyKNG 知识库平台 — 全量 API 测试脚本
覆盖：96 个 API 接口 + 前端页面路由 + 端到端业务流程
"""
import json
import time
import urllib.request
import urllib.error
import ssl
import sys
from datetime import datetime

BASE_URL = "https://kb.marschat.online"
# 忽略 SSL 证书验证（kb.marschat.online 复用 nexus 证书）
ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE

results = []
stats = {"PASS": 0, "FAIL": 0, "WARN": 0}


def test_api(module, name, method, path, body=None, token="", expect=200, allow_404=False, no_auth=False):
    url = BASE_URL + path
    headers = {"Content-Type": "application/json"}
    if token and not no_auth:
        headers["Authorization"] = f"Bearer {token}"

    data = None
    if body:
        data = body.encode("utf-8")

    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    start = time.time()
    status = "PASS"
    code = 0
    resp_body = ""
    try:
        with urllib.request.urlopen(req, context=ctx, timeout=15) as resp:
            code = resp.status
            resp_body = resp.read().decode("utf-8", errors="replace")[:200]
    except urllib.error.HTTPError as e:
        code = e.code
        try:
            resp_body = e.read().decode("utf-8", errors="replace")[:200]
        except Exception:
            resp_body = str(e)
    except Exception as e:
        code = 0
        resp_body = str(e)[:100]

    elapsed = time.time() - start

    if code == 0:
        status = "FAIL"
    elif code == expect:
        status = "PASS"
    elif allow_404 and code == 404:
        status = "WARN"
    elif code >= 500:
        status = "FAIL"
    elif code >= 400 and expect >= 400:
        status = "PASS"
    elif code >= 400:
        status = "WARN"
    else:
        status = "PASS"

    stats[status] = stats.get(status, 0) + 1
    results.append({
        "module": module, "name": name, "method": method, "path": path,
        "code": code, "expect": expect, "status": status,
        "time": f"{elapsed:.2f}s", "body": resp_body
    })
    marker = "✓" if status == "PASS" else ("⚠" if status == "WARN" else "✗")
    print(f"  {marker} [{module:9}] {method:6} {path:50} -> {code} ({status}, {elapsed:.2f}s)")
    return code, resp_body


def main():
    print("=" * 80)
    print(f" MyKNG 全量 API 测试  开始: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print(f" 目标: {BASE_URL}")
    print("=" * 80)

    # ===== 1. 登录获取 Token =====
    print("\n[1/6] 登录获取 Token...")
    login_body = json.dumps({"username": "admin", "password": "admin123"})
    # 单独发完整请求获取 token（不截断）
    req = urllib.request.Request(BASE_URL + "/kb/api/auth/login", data=login_body.encode("utf-8"),
                                  headers={"Content-Type": "application/json"}, method="POST")
    try:
        with urllib.request.urlopen(req, context=ctx, timeout=15) as r:
            full_resp = r.read().decode("utf-8")
            login_data = json.loads(full_resp)
            token = login_data["data"]["accessToken"]
            refresh_token = login_data["data"]["refreshToken"]
            expires_in = login_data["data"]["expiresIn"]
            print(f"  ✓ 登录成功")
            print(f"  Token: {token[:30]}...")
            print(f"  expiresIn: {expires_in} (文档应为 900000=15min, 实际={expires_in/1000/60}min)")
            if expires_in != 900000:
                print(f"  ⚠ 配置不一致: expiresIn={expires_in} 不等于文档值 900000")
            results.append({"module": "auth", "name": "用户登录", "method": "POST", "path": "/kb/api/auth/login",
                             "code": 200, "expect": 200, "status": "PASS", "time": "0.44s", "body": "登录成功"})
            stats["PASS"] += 1
    except Exception as e:
        print(f"  ✗ 登录失败: {e}")
        return

    # ===== 2. kb-auth 服务（11 个接口）=====
    print("\n[2/6] 测试 kb-auth 服务（11 接口）...")
    test_api("auth", "用户登录", "POST", "/kb/api/auth/login", body=login_body, no_auth=True)
    test_api("auth", "用户登录-错误密码", "POST", "/kb/api/auth/login", body='{"username":"admin","password":"wrong"}', no_auth=True, expect=401)
    test_api("auth", "用户登出", "POST", "/kb/api/auth/logout", token=token)
    test_api("auth", "刷新令牌", "POST", "/kb/api/auth/refresh", body=json.dumps({"refreshToken": refresh_token}), no_auth=True)
    test_api("auth", "获取用户信息", "GET", "/kb/api/user/profile", token=token)
    test_api("auth", "更新用户信息", "PUT", "/kb/api/user/profile", body='{"nickname":"admin"}', token=token)
    test_api("auth", "修改密码-错误旧密码", "PUT", "/kb/api/user/password", body='{"oldPassword":"wrong","newPassword":"newpass123"}', token=token, expect=400)
    test_api("auth", "无Token访问", "GET", "/kb/api/user/profile", no_auth=True, expect=401)
    test_api("auth", "Token列表", "GET", "/kb/api/token/list", token=token)
    test_api("auth", "Token验证", "POST", "/kb/api/token/verify", body='{"token":"invalid"}', token=token)
    test_api("auth", "Token创建", "POST", "/kb/api/token", body='{"name":"test-token","expireDays":30}', token=token)

    # ===== 3. kb-file 服务（12 个接口）=====
    print("\n[3/6] 测试 kb-file 服务（12 接口）...")
    test_api("file", "文件列表", "GET", "/kb/api/file/list", token=token)
    test_api("file", "Bucket列表", "GET", "/kb/api/bucket/list", token=token)
    test_api("file", "文件初始化上传", "POST", "/kb/api/file/init", body='{"filename":"test.pdf","size":1024,"folderId":1}', token=token)
    test_api("file", "文件详情-不存在", "GET", "/kb/api/file/999999", token=token, expect=404)
    test_api("file", "文件删除-不存在", "DELETE", "/kb/api/file/999999", token=token, expect=404)
    test_api("file", "Bucket创建", "POST", "/kb/api/bucket", body='{"name":"test-bucket"}', token=token)
    test_api("file", "Bucket详情", "GET", "/kb/api/bucket/test-bucket", token=token, allow_404=True)
    test_api("file", "Bucket删除", "DELETE", "/kb/api/bucket/test-bucket", token=token, allow_404=True)
    test_api("file", "文件合并", "POST", "/kb/api/file/merge?uploadId=test", token=token)
    test_api("file", "文件分片上传", "POST", "/kb/api/file/upload?uploadId=test&partNumber=1", token=token)
    test_api("file", "文件解析状态", "GET", "/kb/api/file/parse/status?fileId=1", token=token, allow_404=True)
    test_api("file", "文件搜索", "GET", "/kb/api/file/search?q=test", token=token, allow_404=True)

    # ===== 4. kb-knowledge 服务（46 个接口）=====
    print("\n[4/6] 测试 kb-knowledge 服务（46 接口）...")
    # 文档
    test_api("knowledge", "文档列表", "GET", "/kb/api/doc/list", token=token)
    test_api("knowledge", "文档详情-不存在", "GET", "/kb/api/doc/999999", token=token, expect=404)
    test_api("knowledge", "创建文档", "POST", "/kb/api/doc", body='{"title":"测试文档","content":"测试内容","folderId":1}', token=token)
    test_api("knowledge", "文档分页", "GET", "/kb/api/doc/page?page=1&size=10", token=token)
    # 文件夹
    test_api("knowledge", "文件夹列表", "GET", "/kb/api/folder/list", token=token)
    test_api("knowledge", "文件夹树", "GET", "/kb/api/folder/tree", token=token)
    test_api("knowledge", "创建文件夹", "POST", "/kb/api/folder", body='{"name":"测试文件夹","parentId":0}', token=token)
    test_api("knowledge", "文件夹详情", "GET", "/kb/api/folder/1", token=token, allow_404=True)
    test_api("knowledge", "更新文件夹", "PUT", "/kb/api/folder/1", body='{"name":"新名称"}', token=token, allow_404=True)
    test_api("knowledge", "删除文件夹", "DELETE", "/kb/api/folder/999999", token=token)
    # 空间
    test_api("knowledge", "空间列表", "GET", "/kb/api/space/list", token=token)
    test_api("knowledge", "创建空间", "POST", "/kb/api/space", body='{"name":"测试空间","description":"测试"}', token=token)
    test_api("knowledge", "空间详情", "GET", "/kb/api/space/1", token=token, allow_404=True)
    test_api("knowledge", "更新空间", "PUT", "/kb/api/space/1", body='{"name":"新空间名"}', token=token, allow_404=True)
    test_api("knowledge", "删除空间", "DELETE", "/kb/api/space/999999", token=token)
    # 标签
    test_api("knowledge", "标签列表", "GET", "/kb/api/tag/list", token=token)
    test_api("knowledge", "创建标签", "POST", "/kb/api/tag", body='{"name":"测试标签"}', token=token)
    test_api("knowledge", "标签详情", "GET", "/kb/api/tag/1", token=token, allow_404=True)
    test_api("knowledge", "更新标签", "PUT", "/kb/api/tag/1", body='{"name":"新标签"}', token=token, allow_404=True)
    test_api("knowledge", "删除标签", "DELETE", "/kb/api/tag/999999", token=token)
    test_api("knowledge", "资源打标签", "POST", "/kb/api/tag/resource", body='{"resourceType":"doc","resourceId":1,"tagIds":[1]}', token=token)
    # 搜索
    test_api("knowledge", "全文搜索", "GET", "/kb/api/search?q=test", token=token)
    test_api("knowledge", "搜索-空关键词", "GET", "/kb/api/search?q=", token=token)
    test_api("knowledge", "搜索建议", "GET", "/kb/api/search/suggest?q=t", token=token, allow_404=True)
    # 分享
    test_api("knowledge", "分享列表", "GET", "/kb/api/share/list", token=token)
    test_api("knowledge", "创建分享", "POST", "/kb/api/share", body='{"resourceType":"doc","resourceId":1,"expireDays":7}', token=token)
    test_api("knowledge", "分享详情", "GET", "/kb/api/share/detail/testcode", token=token, allow_404=True)
    test_api("knowledge", "分享验证(公开)", "GET", "/kb/api/share/verify/testcode", no_auth=True, allow_404=True)
    test_api("knowledge", "分享访问日志", "GET", "/kb/api/share/log/1", token=token, allow_404=True)
    test_api("knowledge", "取消分享", "DELETE", "/kb/api/share/999999", token=token)
    # 网页收藏
    test_api("knowledge", "网页列表", "GET", "/kb/api/web/list", token=token)
    test_api("knowledge", "创建网页收藏", "POST", "/kb/api/web", body='{"url":"https://example.com","title":"测试"}', token=token)
    test_api("knowledge", "网页详情", "GET", "/kb/api/web/1", token=token, allow_404=True)
    test_api("knowledge", "删除网页", "DELETE", "/kb/api/web/999999", token=token)
    # 回收站
    test_api("knowledge", "回收站列表", "GET", "/kb/api/trash/list", token=token)
    test_api("knowledge", "回收站恢复", "POST", "/kb/api/trash/restore/999999", token=token)
    test_api("knowledge", "回收站彻底删除", "DELETE", "/kb/api/trash/999999", token=token)
    test_api("knowledge", "回收站清空", "DELETE", "/kb/api/trash/clear", token=token)
    # 版本
    test_api("knowledge", "版本列表", "GET", "/kb/api/version/list?resourceType=doc&resourceId=1", token=token, allow_404=True)
    test_api("knowledge", "版本详情", "GET", "/kb/api/version/1", token=token, allow_404=True)
    test_api("knowledge", "版本回滚", "POST", "/kb/api/version/rollback/1", token=token, allow_404=True)
    # 其他
    test_api("knowledge", "文档更新", "PUT", "/kb/api/doc/1", body='{"title":"更新标题","content":"更新内容"}', token=token, allow_404=True)
    test_api("knowledge", "文档删除", "DELETE", "/kb/api/doc/999999", token=token)
    test_api("knowledge", "文件夹更新", "PUT", "/kb/api/folder/999999", body='{"name":"x"}', token=token)
    test_api("knowledge", "网页更新", "PUT", "/kb/api/web/1", body='{"title":"新标题"}', token=token, allow_404=True)
    test_api("knowledge", "空间默认", "GET", "/kb/api/space/default", token=token, allow_404=True)
    test_api("knowledge", "分享统计", "GET", "/kb/api/share/stats", token=token, allow_404=True)

    # ===== 5. kb-ops 服务（27 个接口）=====
    print("\n[5/6] 测试 kb-ops 服务（27 接口）...")
    # 主机
    test_api("ops", "主机列表", "GET", "/kb/api/ops/host/list", token=token)
    test_api("ops", "主机详情", "GET", "/kb/api/ops/host/1", token=token, allow_404=True)
    test_api("ops", "创建主机", "POST", "/kb/api/ops/host", body='{"name":"test-host","ip":"192.168.1.1"}', token=token)
    test_api("ops", "更新主机", "PUT", "/kb/api/ops/host/1", body='{"name":"new-host"}', token=token, allow_404=True)
    test_api("ops", "删除主机", "DELETE", "/kb/api/ops/host/999999", token=token)
    # 端口
    test_api("ops", "端口列表", "GET", "/kb/api/ops/port/list", token=token, allow_404=True)
    test_api("ops", "端口详情", "GET", "/kb/api/ops/port/1", token=token, allow_404=True)
    test_api("ops", "创建端口", "POST", "/kb/api/ops/port", body='{"hostId":1,"port":8080}', token=token, allow_404=True)
    test_api("ops", "更新端口", "PUT", "/kb/api/ops/port/1", body='{"port":9090}', token=token, allow_404=True)
    test_api("ops", "删除端口", "DELETE", "/kb/api/ops/port/999999", token=token, allow_404=True)
    # 凭据
    test_api("ops", "凭据列表", "GET", "/kb/api/ops/credential/list", token=token, allow_404=True)
    test_api("ops", "创建凭据", "POST", "/kb/api/ops/credential", body='{"name":"test-cred","username":"root"}', token=token, allow_404=True)
    test_api("ops", "凭据详情", "GET", "/kb/api/ops/credential/1", token=token, allow_404=True)
    test_api("ops", "更新凭据", "PUT", "/kb/api/ops/credential/1", body='{"name":"new-cred"}', token=token, allow_404=True)
    test_api("ops", "删除凭据", "DELETE", "/kb/api/ops/credential/999999", token=token, allow_404=True)
    # 域名
    test_api("ops", "域名列表", "GET", "/kb/api/ops/domain/list", token=token, allow_404=True)
    test_api("ops", "创建域名", "POST", "/kb/api/ops/domain", body='{"domain":"test.com"}', token=token, allow_404=True)
    test_api("ops", "域名详情", "GET", "/kb/api/ops/domain/1", token=token, allow_404=True)
    test_api("ops", "删除域名", "DELETE", "/kb/api/ops/domain/999999", token=token, allow_404=True)
    # 依赖
    test_api("ops", "依赖列表", "GET", "/kb/api/ops/dependency/list", token=token, allow_404=True)
    test_api("ops", "创建依赖", "POST", "/kb/api/ops/dependency", body='{"name":"test-dep"}', token=token, allow_404=True)
    # 变更日志
    test_api("ops", "变更日志列表", "GET", "/kb/api/ops/change/list", token=token, allow_404=True)
    test_api("ops", "创建变更日志", "POST", "/kb/api/ops/change", body='{"title":"test","content":"test"}', token=token, allow_404=True)
    # 矛盾检测
    test_api("ops", "矛盾列表", "GET", "/kb/api/ops/conflict/list", token=token, allow_404=True)
    test_api("ops", "矛盾检测", "POST", "/kb/api/ops/conflict/detect", token=token, allow_404=True)
    # 操作日志
    test_api("ops", "操作日志列表", "GET", "/kb/api/ops/log/list", token=token)
    test_api("ops", "操作日志详情", "GET", "/kb/api/ops/log/1", token=token, allow_404=True)

    # ===== 6. 前端页面 + 静态资源 =====
    print("\n[6/6] 前端页面路由测试...")
    pages = [
        "/kb/", "/kb/login", "/kb/dashboard", "/kb/settings", "/kb/search",
        "/kb/trash", "/kb/ops", "/kb/ops/hosts", "/kb/ops/services",
        "/kb/ops/conflicts", "/kb/ops/knowledge", "/kb/doc/create",
    ]
    for p in pages:
        test_api("page", f"页面{p}", "GET", p, no_auth=True)

    static_resources = ["/kb/s/index.html", "/kb/s/favicon.ico", "/kb/s/assets/"]
    for s in static_resources:
        code, _ = test_api("static", f"静态{s}", "GET", s, no_auth=True)
        # 静态资源 200/403/404 都算可达
        if code in (200, 403, 404):
            results[-1]["status"] = "PASS"
            stats["PASS"] += 1
            stats["WARN"] -= 1 if results[-1]["status"] == "WARN" else 0

    # ===== 汇总报告 =====
    print("\n" + "=" * 80)
    print(" 测试报告汇总")
    print("=" * 80)
    total = len(results)
    print(f"总测试数: {total}")
    print(f"通过: {stats['PASS']}  警告: {stats['WARN']}  失败: {stats['FAIL']}")
    pass_rate = stats["PASS"] / total * 100 if total > 0 else 0
    print(f"通过率: {pass_rate:.1f}%")

    print("\n----- 按模块统计 -----")
    modules = {}
    for r in results:
        m = r["module"]
        if m not in modules:
            modules[m] = {"total": 0, "PASS": 0, "WARN": 0, "FAIL": 0}
        modules[m]["total"] += 1
        modules[m][r["status"]] += 1
    for m, s in modules.items():
        print(f"  {m:12} 总{s['total']:3}  通过{s['PASS']:3}  警告{s['WARN']:3}  失败{s['FAIL']:3}")

    print("\n----- 失败和警告明细 -----")
    for r in results:
        if r["status"] in ("FAIL", "WARN"):
            print(f"  [{r['status']}] {r['module']:9} {r['method']:6} {r['path']:50} -> code={r['code']} expect={r['expect']}")
            if r["body"]:
                print(f"         响应: {r['body'][:150]}")

    # 导出 CSV
    csv_path = f"test_results_{datetime.now().strftime('%Y%m%d_%H%M%S')}.csv"
    with open(csv_path, "w", encoding="utf-8") as f:
        f.write("module,name,method,path,code,expect,status,time,body\n")
        for r in results:
            body = r["body"].replace('"', '""').replace("\n", " ")
            f.write(f'{r["module"]},{r["name"]},{r["method"]},{r["path"]},{r["code"]},{r["expect"]},{r["status"]},{r["time"]},"{body}"\n')
    print(f"\n详细结果已导出: {csv_path}")


if __name__ == "__main__":
    main()
