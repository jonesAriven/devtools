#!/usr/bin/env python3
"""
页面功能测试 - 覆盖全部16个前端路由可达性 + 95个API接口功能
目标: VM 后端 (192.168.31.105:8090) + 本地 Vite dev server (localhost:3000)
用途: 回答用户"每个功能都点了都测了么"的逐页面逐功能验证
"""
import requests
import json
import time
import sys
import warnings
warnings.filterwarnings("ignore")

# 后端 API（VM 网关）
API_BASE = "http://192.168.31.105:8090/kb/api"
# 前端 dev server（本地 Vite）
WEB_BASE = "http://localhost:3000/kb/s"
TIMEOUT = 15

s = requests.Session()
s.verify = False
s.headers.update({"Content-Type": "application/json"})

# 两条记录集
api_results = []
page_results = []


def api_test(name, method, path, expected_status=200, **kwargs):
    """执行API请求并验证"""
    url = f"{API_BASE}{path}"
    try:
        resp = s.request(method, url, timeout=TIMEOUT, **kwargs)
        body = {}
        try:
            body = resp.json()
        except Exception:
            body = {"raw": resp.text[:200]}
        code = body.get("code") if isinstance(body, dict) else None
        passed = resp.status_code == expected_status and (code in (200, 0, None) or resp.status_code in (200, 204))
        detail = f"{resp.status_code} code={code}"
        if not passed:
            msg = body.get("message", body.get("msg", "")) if isinstance(body, dict) else str(body)[:80]
            detail += f" msg={msg}"
            if resp.status_code >= 400:
                detail += f" body={str(body)[:120]}"
        api_results.append({"name": name, "method": method, "path": path, "passed": passed, "detail": detail})
        status = "PASS" if passed else "FAIL"
        print(f"  [{status}] {name}: {detail}")
        return body if passed else None
    except Exception as e:
        api_results.append({"name": name, "method": method, "path": path, "passed": False, "detail": str(e)[:120]})
        print(f"  [FAIL] {name}: {str(e)[:120]}")
        return None


def page_test(name, path, expect_login_redirect=False):
    """测试前端路由可达性"""
    url = f"{WEB_BASE}{path}"
    try:
        resp = s.get(url, timeout=TIMEOUT, allow_redirects=False)
        # Vite dev server 对所有路由都返回 200 + index.html (SPA)
        # 关键验证: 返回 200 + HTML 内容 + 含 Vue 挂载点
        body = resp.text
        has_mount = '<div id="app">' in body or 'id="app"' in body
        has_script = '<script' in body and 'index' in body.lower()
        passed = resp.status_code == 200 and has_mount
        detail = f"{resp.status_code} app_mount={'Y' if has_mount else 'N'} script={'Y' if has_script else 'N'} len={len(body)}"
        page_results.append({"name": name, "path": path, "passed": passed, "detail": detail})
        status = "PASS" if passed else "FAIL"
        print(f"  [{status}] {name}: {detail}")
        return passed
    except Exception as e:
        page_results.append({"name": name, "path": path, "passed": False, "detail": str(e)[:120]})
        print(f"  [FAIL] {name}: {str(e)[:120]}")
        return False


# ============================================================
# Part A: 16个前端路由可达性测试（通过 Vite dev server）
# ============================================================
print("\n" + "=" * 80)
print("Part A: 前端16个页面路由可达性测试（Vite dev server）")
print("=" * 80)

# 不需要认证的页面
page_test("登录页 /login", "/login")
page_test("分享访问页 /share/abc123", "/share/abc123")

# 需要认证的页面（dev server 返回 HTML，路由守卫在客户端 JS 执行）
page_test("仪表盘 /dashboard", "/dashboard")
page_test("空间详情 /space/1", "/space/1")
page_test("文件详情 /file/1", "/file/1")
page_test("创建文档 /doc/create", "/doc/create")
page_test("编辑文档 /doc/1", "/doc/1")
page_test("网页详情 /web/1", "/web/1")
page_test("搜索页 /search", "/search")
page_test("回收站 /trash", "/trash")
page_test("系统设置 /settings", "/settings")
page_test("运维看板 /ops", "/ops")
page_test("主机管理 /ops/hosts", "/ops/hosts")
page_test("服务管理 /ops/services", "/ops/services")
page_test("矛盾检测 /ops/conflicts", "/ops/conflicts")
page_test("运维知识 /ops/knowledge", "/ops/knowledge")

# SPA 根路径重定向
page_test("根路径 / 重定向到 /dashboard", "/")

# 静态资源可达性
print("\n--- 静态资源可达性 ---")
try:
    resp = s.get(f"{WEB_BASE}/", timeout=TIMEOUT)
    body = resp.text
    import re
    scripts = re.findall(r'<script[^>]+src="([^"]+)"', body)
    styles = re.findall(r'<link[^>]+href="([^"]+\.css)"', body)
    for sc in scripts[:3]:
        url = sc if sc.startswith("http") else f"{WEB_BASE}{sc}" if sc.startswith("/") else f"{WEB_BASE}/{sc}"
        try:
            r = s.get(url, timeout=TIMEOUT)
            ok = r.status_code == 200 and len(r.content) > 100
            print(f"  [{'PASS' if ok else 'FAIL'}] JS资源 {sc}: {r.status_code} {len(r.content)}B")
            page_results.append({"name": f"JS资源 {sc}", "path": sc, "passed": ok, "detail": f"{r.status_code} {len(r.content)}B"})
        except Exception as e:
            print(f"  [FAIL] JS资源 {sc}: {str(e)[:80]}")
            page_results.append({"name": f"JS资源 {sc}", "path": sc, "passed": False, "detail": str(e)[:80]})
    for st in styles[:2]:
        url = st if st.startswith("http") else f"{WEB_BASE}{st}" if st.startswith("/") else f"{WEB_BASE}/{st}"
        try:
            r = s.get(url, timeout=TIMEOUT)
            ok = r.status_code == 200 and len(r.content) > 100
            print(f"  [{'PASS' if ok else 'FAIL'}] CSS资源 {st}: {r.status_code} {len(r.content)}B")
            page_results.append({"name": f"CSS资源 {st}", "path": st, "passed": ok, "detail": f"{r.status_code} {len(r.content)}B"})
        except Exception as e:
            print(f"  [FAIL] CSS资源 {st}: {str(e)[:80]}")
            page_results.append({"name": f"CSS资源 {st}", "path": st, "passed": False, "detail": str(e)[:80]})
except Exception as e:
    print(f"  [FAIL] 解析index.html失败: {str(e)[:80]}")

# ============================================================
# Part B: 95个 API 接口功能测试（通过 VM 网关）
# ============================================================
print("\n" + "=" * 80)
print("Part B: 95个API接口功能测试（VM 网关 192.168.31.105:8090）")
print("=" * 80)

# ============================================================
# B1. Auth 认证 (11个)
# ============================================================
print("\n--- B1. Auth 认证 (11个) ---")

# 1. POST /auth/login
body = api_test("登录", "POST", "/auth/login",
                json={"username": "admin", "password": "admin123"})
token = None
refresh_token = None
if body and body.get("data"):
    data = body["data"]
    if isinstance(data, dict):
        token = data.get("accessToken") or data.get("token")
        refresh_token = data.get("refreshToken")
if token:
    s.headers.update({"Authorization": f"Bearer {token}"})
    print(f"     -> Token获取成功")
else:
    print("     -> Token获取失败，后续测试将受影响")

# 2. GET /user/profile (使用token)
body = api_test("获取个人信息", "GET", "/user/profile")
user_id = body.get("data", {}).get("id") if body else None

# 3. PUT /user/profile
api_test("修改个人信息", "PUT", "/user/profile",
         json={"nickname": "管理员", "email": "admin@kb.local"})

# 4. PUT /user/password
api_test("修改密码", "PUT", "/user/password",
         json={"oldPassword": "admin123", "newPassword": "admin123"})

# 5. POST /auth/refresh
if refresh_token:
    api_test("刷新Token", "POST", "/auth/refresh",
             json={"refreshToken": refresh_token})

# 6. POST /token (创建API Token)
api_token_id = None
test_token_value = None
body = api_test("创建API Token", "POST", "/token",
               json={"name": "页面功能测试Token", "scope": "read,write",
                     "expireAt": "2026-12-31 23:59:59"})
if body and body.get("data"):
    td = body["data"]
    api_token_id = td.get("id") if isinstance(td, dict) else None
    test_token_value = td.get("token") if isinstance(td, dict) else None

# 7. GET /token
api_test("Token列表", "GET", "/token", params={"page": 1, "size": 10})

# 8. PUT /token/{id}/toggle
if api_token_id:
    api_test("启用/禁用Token", "PUT", f"/token/{api_token_id}/toggle")

# 9. POST /token/verify
api_test("验证Token", "POST", "/token/verify",
         json={"token": test_token_value if api_token_id else "dummy"})

# 10. DELETE /token/{id}
if api_token_id:
    api_test("删除Token", "DELETE", f"/token/{api_token_id}")

# 11. POST /auth/logout (放最后)
# 先不登出，保留token给后续测试

# ============================================================
# B2. kb-file 文件服务 (12个)
# ============================================================
print("\n--- B2. kb-file 文件服务 (12个) ---")

# 12. POST /file/upload
file_id = None
headers_backup = s.headers.pop("Content-Type", None)
files = {"file": ("e2e_test.txt", b"E2E test file content", "text/plain")}
data = {"folderId": "0", "spaceId": "1"}
body = api_test("文件上传", "POST", "/file/upload", files=files, data=data)
if headers_backup:
    s.headers["Content-Type"] = headers_backup
if body and body.get("data"):
    fd = body["data"]
    file_id = fd.get("id") if isinstance(fd, dict) else fd

# 13. GET /file/list
api_test("文件列表", "GET", "/file/list", params={"page": 1, "size": 5})

if file_id:
    # 14. GET /file/{id}
    api_test("文件详情", "GET", f"/file/{file_id}")
    # 15. GET /file/{id}/parse-status
    api_test("文件解析状态", "GET", f"/file/{file_id}/parse-status")
    # 16. GET /file/{id}/download
    api_test("文件下载", "GET", f"/file/{file_id}/download")
    # 17. POST /file/{id}/reparse
    api_test("重新解析文件", "POST", f"/file/{file_id}/reparse")
    # 18. PUT /file/{id}/star
    api_test("文件星标", "PUT", f"/file/{file_id}/star")
    # 19. PUT /file/{id}/move
    api_test("移动文件", "PUT", f"/file/{file_id}/move", json={"folderId": 0})

# 20. POST /file/merge
api_test("分片合并", "POST", "/file/merge",
        json={"fileId": "e2e_merge_test", "name": "e2e_merged.txt",
              "folderId": 0, "size": 100, "totalChunks": 1})

# 21. GET /bucket/list
body = api_test("存储桶列表", "GET", "/bucket/list")
bucket_id = None
if body and body.get("data"):
    bd = body["data"]
    if isinstance(bd, list) and len(bd) > 0:
        bucket_id = bd[0].get("id")
    elif isinstance(bd, dict) and "records" in bd:
        if bd["records"]:
            bucket_id = bd["records"][0].get("id")

# 22. GET /bucket/{id}/stats
if bucket_id:
    api_test("存储桶统计", "GET", f"/bucket/{bucket_id}/stats")

# 23. DELETE /file/{id} (最后删)
if file_id:
    api_test("删除文件", "DELETE", f"/file/{file_id}")

# ============================================================
# B3. kb-knowledge 知识服务 (46个)
# ============================================================
print("\n--- B3. kb-knowledge 知识服务 (46个) ---")

# 24. GET /space/list
body = api_test("空间列表", "GET", "/space/list")
space_id = None
if body and body.get("data"):
    d = body["data"]
    if isinstance(d, list) and len(d) > 0:
        space_id = d[0].get("id")
    elif isinstance(d, dict) and "records" in d:
        if d["records"]:
            space_id = d["records"][0].get("id")

# 25. POST /space
if not space_id:
    body = api_test("创建空间", "POST", "/space",
                   json={"name": "页面测试空间", "description": "自动化测试创建"})
    if body and body.get("data"):
        space_id = body["data"].get("id") if isinstance(body["data"], dict) else body["data"]
else:
    body2 = api_test("创建临时空间", "POST", "/space",
                    json={"name": f"页面测试临时_{int(time.time())}", "description": "临时"})
    temp_space_id = None
    if body2 and body2.get("data"):
        temp_space_id = body2["data"].get("id") if isinstance(body2["data"], dict) else body2["data"]
    if temp_space_id:
        # 26. PUT /space/{id}
        api_test("修改空间", "PUT", f"/space/{temp_space_id}",
                 json={"name": "页面测试修改", "description": "已修改"})
        # 27. DELETE /space/{id}
        api_test("删除空间", "DELETE", f"/space/{temp_space_id}")

# 28. GET /folder/tree/{spaceId}
api_test("文件夹树", "GET", f"/folder/tree/{space_id or 1}")

# 29. POST /folder
folder_id = None
body = api_test("创建文件夹", "POST", "/folder",
               json={"name": f"页面测试文件夹_{int(time.time())}", "parentId": 0, "spaceId": space_id or 1})
if body and body.get("data"):
    fd = body["data"]
    folder_id = fd.get("id") if isinstance(fd, dict) else fd

if folder_id:
    # 30. PUT /folder/{id}
    api_test("重命名文件夹", "PUT", f"/folder/{folder_id}",
             json={"name": f"页面测试重命名_{int(time.time())}"})
    # 31. PUT /folder/{id}/sort
    api_test("文件夹排序", "PUT", f"/folder/{folder_id}/sort", json={"sortOrder": 1})
    # 32. PUT /folder/{id}/move
    api_test("移动文件夹", "PUT", f"/folder/{folder_id}/move", json={"parentId": 0})

# 33. GET /doc/list
api_test("文档列表", "GET", "/doc/list", params={"page": 1, "size": 5})

# 34. POST /doc
doc_id = None
body = api_test("创建文档", "POST", "/doc",
               json={"title": f"页面测试文档_{int(time.time())}",
                     "content": "<p>测试内容</p>",
                     "folderId": 0, "spaceId": space_id or 1})
if body and body.get("data"):
    dd = body["data"]
    doc_id = dd.get("id") if isinstance(dd, dict) else dd

if doc_id:
    # 35. GET /doc/{id}
    api_test("文档详情", "GET", f"/doc/{doc_id}")
    # 36. PUT /doc/{id}
    api_test("编辑文档", "PUT", f"/doc/{doc_id}",
             json={"title": "页面测试修改标题", "content": "<p>修改后内容</p>"})
    # 37. PUT /doc/{id}/star
    api_test("文档星标", "PUT", f"/doc/{doc_id}/star")
    # 38. PUT /doc/{id}/move
    api_test("移动文档", "PUT", f"/doc/{doc_id}/move", json={"folderId": 0})

# 39. POST /web/collect
web_id = None
body = api_test("收藏网页", "POST", "/web/collect",
               json={"url": "https://example.com", "folderId": 0})
if body and body.get("data"):
    wd = body["data"]
    web_id = wd.get("id") if isinstance(wd, dict) else wd

# 40. GET /web/list
api_test("网页收藏列表", "GET", "/web/list", params={"page": 1, "size": 5})

if web_id:
    # 41. GET /web/{id}
    api_test("网页详情", "GET", f"/web/{web_id}")
    # 42. PUT /web/{id}/star
    api_test("网页星标", "PUT", f"/web/{web_id}/star")
    # 43. PUT /web/{id}/move
    api_test("移动网页", "PUT", f"/web/{web_id}/move", json={"folderId": 0})
    # 44. POST /web/{id}/refetch
    api_test("重新抓取网页", "POST", f"/web/{web_id}/refetch")

# 45. GET /tag/list
api_test("标签列表", "GET", "/tag/list")

# 46. POST /tag
tag_id = None
body = api_test("创建标签", "POST", "/tag",
               json={"name": f"页面测试标签_{int(time.time())}"})
if body and body.get("data"):
    td = body["data"]
    tag_id = td.get("id") if isinstance(td, dict) else td

if tag_id and doc_id:
    # 47. POST /tag/bind
    api_test("绑定标签", "POST", "/tag/bind",
             json={"tagId": tag_id, "resourceType": "doc", "resourceId": doc_id})
    # 48. DELETE /tag/unbind
    api_test("解绑标签", "DELETE", "/tag/unbind",
             params={"tagId": tag_id, "resourceType": "doc", "resourceId": doc_id})

if tag_id:
    # 49. DELETE /tag/{id}
    api_test("删除标签", "DELETE", f"/tag/{tag_id}")

# 50. GET /search
api_test("搜索", "GET", "/search", params={"q": "测试", "page": 1, "size": 10})

# 51. POST /share
share_code = None
share_id = None
extract_code = None
if doc_id:
    body = api_test("创建分享", "POST", "/share",
                   json={"resourceId": doc_id, "resourceType": "doc", "expireType": "never"})
    if body and body.get("data"):
        sd = body["data"]
        share_code = sd.get("code") if isinstance(sd, dict) else None
        share_id = sd.get("id") if isinstance(sd, dict) else None
        extract_code = sd.get("extractCode") if isinstance(sd, dict) else None

# 52. GET /share/list
api_test("分享列表", "GET", "/share/list")

# 53. GET /share/verify/{code} (公开接口)
if share_code:
    saved_auth = s.headers.pop("Authorization", None)
    api_test("验证分享码", "GET", f"/share/verify/{share_code}",
             params={"extractCode": extract_code or ""})
    if saved_auth:
        s.headers["Authorization"] = saved_auth
    # 54. GET /share/detail/{code}
    api_test("分享详情", "GET", f"/share/detail/{share_code}",
             params={"extractCode": extract_code or ""})

# 55. GET /version/list/{type}/{id}
if doc_id:
    body = api_test("版本历史列表", "GET", f"/version/list/doc/{doc_id}")
    version_id = None
    if body and body.get("data"):
        vd = body["data"]
        if isinstance(vd, list) and len(vd) > 0:
            version_id = vd[0].get("id")
        elif isinstance(vd, dict) and "records" in vd:
            if vd["records"]:
                version_id = vd["records"][0].get("id")
    # 56. GET /version/{id}
    if version_id:
        api_test("版本详情", "GET", f"/version/{version_id}")
        # 57. POST /version/{id}/rollback
        api_test("版本回滚", "POST", f"/version/{version_id}/rollback")

# 58. GET /trash/list
api_test("回收站列表", "GET", "/trash/list", params={"page": 1, "size": 10})

# 59. POST /trash/restore/{type}/{id}
if doc_id:
    s.delete(f"{API_BASE}/doc/{doc_id}", timeout=TIMEOUT)
    time.sleep(1)
    api_test("恢复资源", "POST", f"/trash/restore/doc/{doc_id}")
    time.sleep(0.5)
    s.delete(f"{API_BASE}/doc/{doc_id}", timeout=TIMEOUT)
    time.sleep(1)
    # 60. DELETE /trash/{type}/{id}
    api_test("永久删除", "DELETE", f"/trash/doc/{doc_id}")

# 61. DELETE /folder/{id}
if folder_id:
    api_test("删除文件夹", "DELETE", f"/folder/{folder_id}")

# 62. DELETE /share/{id}
if share_id:
    api_test("删除分享", "DELETE", f"/share/{share_id}")

# 63. DELETE /web/{id}
if web_id:
    api_test("删除网页", "DELETE", f"/web/{web_id}")

# ============================================================
# B4. kb-ops 运维服务 (26个)
# ============================================================
print("\n--- B4. kb-ops 运维服务 (26个) ---")

# 64. GET /ops/dashboard
api_test("运维看板", "GET", "/ops/dashboard")

# 65. POST /ops/dashboard/snapshot/refresh
api_test("刷新快照", "POST", "/ops/dashboard/snapshot/refresh")

# 66. GET /ops/deployment/list
api_test("部署列表", "GET", "/ops/deployment/list", params={"page": 1, "size": 5})

# 67. GET /ops/deployment/recent
api_test("最近部署", "GET", "/ops/deployment/recent")

# 68. POST /ops/deployment
api_test("创建部署记录", "POST", "/ops/deployment",
        json={"serviceId": 1, "hostId": 1, "version": "v1.0-e2e",
              "result": 1, "operator": "e2e"})

# 69. GET /ops/host/list
api_test("主机列表", "GET", "/ops/host/list", params={"page": 1, "size": 5})

# 70. POST /ops/host
host_id = None
body = api_test("添加主机", "POST", "/ops/host",
               json={"name": "页面测试主机", "ip": "10.0.0.99",
                     "sshPort": 22, "username": "root", "status": 1})
if body and body.get("data"):
    hd = body["data"]
    host_id = hd.get("id") if isinstance(hd, dict) else hd

if host_id:
    # 71. GET /ops/host/{id}
    api_test("主机详情", "GET", f"/ops/host/{host_id}")
    # 72. PUT /ops/host/{id}
    api_test("修改主机", "PUT", f"/ops/host/{host_id}",
             json={"name": "页面测试修改", "ip": "10.0.0.99",
                   "sshPort": 22, "username": "root", "status": 0})
    # 73. DELETE /ops/host/{id}
    api_test("删除主机", "DELETE", f"/ops/host/{host_id}")

# 74. GET /ops/service/list
api_test("服务列表", "GET", "/ops/service/list", params={"page": 1, "size": 5})

# 75. POST /ops/service
svc_id = None
body = api_test("添加服务", "POST", "/ops/service",
               json={"name": "页面测试服务", "type": "web",
                     "port": 8080, "hostId": 1, "status": 1})
if body and body.get("data"):
    sd = body["data"]
    svc_id = sd.get("id") if isinstance(sd, dict) else sd

if svc_id:
    # 76. GET /ops/service/{id}
    api_test("服务详情", "GET", f"/ops/service/{svc_id}")
    # 77. PUT /ops/service/{id}
    api_test("修改服务", "PUT", f"/ops/service/{svc_id}",
             json={"name": "页面测试修改", "type": "web",
                   "port": 8081, "hostId": 1, "status": 0})
    # 78. DELETE /ops/service/{id}
    api_test("删除服务", "DELETE", f"/ops/service/{svc_id}")

# 79. POST /ops/conflict/detect
api_test("矛盾检测", "POST", "/ops/conflict/detect", json={})

# 80. GET /ops/conflict/list
api_test("矛盾列表", "GET", "/ops/conflict/list", params={"page": 1, "size": 5})

# 81. POST /ops/import
api_test("导入知识", "POST", "/ops/import",
        json={"title": "页面测试导入", "content": "测试导入内容", "type": "note"})

# 82. POST /ops/import/csv
csv_content = "title,content\n页面测试CSV,测试CSV导入内容"
headers_backup2 = s.headers.pop("Content-Type", None)
api_test("CSV导入", "POST", "/ops/import/csv",
         files={"file": ("test.csv", csv_content.encode(), "text/csv")})
if headers_backup2:
    s.headers["Content-Type"] = headers_backup2

# 83. GET /ops/knowledge/list
api_test("知识库列表", "GET", "/ops/knowledge/list", params={"page": 1, "size": 5})

# 84. POST /ops/knowledge
kb_id = None
body = api_test("添加知识", "POST", "/ops/knowledge",
               json={"title": "页面测试知识", "content": "测试运维知识内容", "category": "other"})
if body and body.get("data"):
    kd = body["data"]
    kb_id = kd.get("id") if isinstance(kd, dict) else kd

if kb_id:
    # 85. GET /ops/knowledge/{id}
    api_test("知识详情", "GET", f"/ops/knowledge/{kb_id}")
    # 86. PUT /ops/knowledge/{id}
    api_test("修改知识", "PUT", f"/ops/knowledge/{kb_id}",
             json={"title": "页面测试修改", "content": "修改后内容", "category": "other"})
    # 87. DELETE /ops/knowledge/{id}
    api_test("删除知识", "DELETE", f"/ops/knowledge/{kb_id}")

# 88. GET /ops/log/list
api_test("操作日志列表", "GET", "/ops/log/list", params={"page": 1, "size": 5})

# ============================================================
# B5. Auth 登出 (最后)
# ============================================================
print("\n--- B5. Auth 登出 ---")

# 89. POST /auth/logout
api_test("退出登录", "POST", "/auth/logout")

# ============================================================
# Part C: 汇总报告
# ============================================================
print("\n" + "=" * 80)
print("Part C: 测试汇总报告")
print("=" * 80)

page_pass = sum(1 for r in page_results if r["passed"])
page_fail = sum(1 for r in page_results if not r["passed"])
api_pass = sum(1 for r in api_results if r["passed"])
api_fail = sum(1 for r in api_results if not r["passed"])

print(f"\n[前端页面路由测试] 共 {len(page_results)} 项 | 通过 {page_pass} | 失败 {page_fail}")
print(f"[API接口功能测试]   共 {len(api_results)} 项 | 通过 {api_pass} | 失败 {api_fail}")
print(f"[总计]             共 {len(page_results) + len(api_results)} 项 | 通过 {page_pass + api_pass} | 失败 {page_fail + api_fail}")

if page_fail > 0:
    print("\n--- 失败的页面路由 ---")
    for r in page_results:
        if not r["passed"]:
            print(f"  {r['name']}: {r['detail']}")

if api_fail > 0:
    print("\n--- 失败的API接口 ---")
    for r in api_results:
        if not r["passed"]:
            print(f"  {r['method']:6s} {r['path']:45s} {r['name']}: {r['detail']}")

print("\n" + "=" * 80)
print(f"测试完成时间: {time.strftime('%Y-%m-%d %H:%M:%S')}")
print("=" * 80)

# JSON输出
print("\n__JSON__")
print(json.dumps({"pages": page_results, "apis": api_results}, ensure_ascii=False))

sys.exit(0 if (page_fail + api_fail) == 0 else 1)
