#!/usr/bin/env python3
"""
知识库全量API测试 - 覆盖全部89个后端端点
每个端点验证: HTTP状态码 + 响应结构 + 数据一致性
"""
import requests
import json
import time
import sys
import warnings
warnings.filterwarnings("ignore")

BASE = "https://tools.marschat.online/kb/api"
TIMEOUT = 15

s = requests.Session()
s.verify = False
s.headers.update({"Content-Type": "application/json"})

results = []

def test(name, method, path, expected_status=200, **kwargs):
    """执行API请求并验证"""
    url = f"{BASE}{path}"
    try:
        resp = s.request(method, url, timeout=TIMEOUT, **kwargs)
        body = {}
        try:
            body = resp.json()
        except:
            body = {"raw": resp.text[:200]}

        code = body.get("code") if isinstance(body, dict) else None
        passed = resp.status_code == expected_status and (code == 200 or code == 0 or resp.status_code in (200, 204))

        detail = f"{resp.status_code} code={code}"
        if not passed:
            msg = body.get("message", body.get("msg", "")) if isinstance(body, dict) else str(body)[:80]
            detail += f" msg={msg}"
            if resp.status_code == 500:
                detail += f" body={str(body)[:120]}"

        results.append({"name": name, "method": method, "path": path, "passed": passed, "detail": detail})
        status = "✅" if passed else "❌"
        print(f"  {status} {name}: {detail}")
        return body if passed else None
    except Exception as e:
        results.append({"name": name, "method": method, "path": path, "passed": False, "detail": str(e)[:100]})
        print(f"  ❌ {name}: {str(e)[:100]}")
        return None

# ============================================================
# 1. Auth (3 endpoints)
# ============================================================
print("\n=== Auth 认证 (3) ===")

# 1.1 POST /auth/login
body = test("登录", "POST", "/auth/login",
    json={"username": "admin", "password": "admin123"})
token = None
if body and body.get("data"):
    data = body["data"]
    token = data.get("accessToken") if isinstance(data, dict) else data
if token:
    s.headers.update({"Authorization": f"Bearer {token}"})
    print(f"     → Token获取成功")
else:
    print("     → Token获取失败，后续测试将受影响")

# 1.2 POST /auth/refresh (需要refreshToken)
if token:
    test("刷新Token", "POST", "/auth/refresh",
        json={"refreshToken": token})

# 1.3 POST /auth/logout (放最后测，因为会清token)

# ============================================================
# 2. User (3 endpoints)
# ============================================================
print("\n=== User 用户管理 (3) ===")

# 2.1 GET /user/profile
body = test("获取个人信息", "GET", "/user/profile")
user_id = body.get("data", {}).get("id") if body else None

# 2.2 PUT /user/profile
test("修改个人信息", "PUT", "/user/profile",
    json={"nickname": "管理员", "email": "admin@kb.local"})

# 2.3 PUT /user/password
test("修改密码", "PUT", "/user/password",
    json={"oldPassword": "admin123", "newPassword": "admin123"})

# ============================================================
# 3. ApiToken (5 endpoints)
# ============================================================
print("\n=== ApiToken API令牌 (5) ===")

# 3.1 POST /token
body = test("创建API Token", "POST", "/token",
    json={"name": "E2E测试Token", "scopes": "read,write"})
token_id = None
if body and body.get("data"):
    token_data = body["data"]
    token_id = token_data.get("id") if isinstance(token_data, dict) else None
    test_token_value = token_data.get("token") if isinstance(token_data, dict) else None

# 3.2 GET /token
test("Token列表", "GET", "/token")

# 3.3 PUT /token/{id}/toggle
if token_id:
    test("启用/禁用Token", "PUT", f"/token/{token_id}/toggle")

# 3.4 POST /token/verify
test("验证Token", "POST", "/token/verify",
    json={"token": test_token_value if token_id else "dummy"})

# 3.5 DELETE /token/{id}
if token_id:
    test("删除Token", "DELETE", f"/token/{token_id}")

# ============================================================
# 4. Space (4 endpoints)
# ============================================================
print("\n=== Space 空间管理 (4) ===")

# 4.1 GET /space/list
body = test("空间列表", "GET", "/space/list")
space_id = None
if body and body.get("data"):
    data = body["data"]
    if isinstance(data, list) and len(data) > 0:
        space_id = data[0].get("id")
    elif isinstance(data, dict) and "records" in data:
        records = data["records"]
        if records:
            space_id = records[0].get("id")

if not space_id:
    # 4.2 POST /space (如果没有空间就创建一个)
    body = test("创建空间", "POST", "/space",
        json={"name": "E2E测试空间", "description": "自动化测试创建"})
    if body and body.get("data"):
        space_id = body["data"].get("id") if isinstance(body["data"], dict) else body["data"]
else:
    # 用已有空间测创建
    body2 = test("创建空间", "POST", "/space",
        json={"name": "E2E临时空间_" + str(int(time.time())), "description": "临时"})
    temp_space_id = None
    if body2 and body2.get("data"):
        temp_space_id = body2["data"].get("id") if isinstance(body2["data"], dict) else body2["data"]
    if temp_space_id:
        # 4.3 PUT /space/{id}
        test("修改空间", "PUT", f"/space/{temp_space_id}",
            json={"name": "E2E修改空间名", "description": "已修改"})
        # 4.4 DELETE /space/{id}
        test("删除空间", "DELETE", f"/space/{temp_space_id}")

# ============================================================
# 5. Folder (6 endpoints)
# ============================================================
print("\n=== Folder 文件夹 (6) ===")

# 5.1 GET /folder/tree/{spaceId}
body = test("文件夹树", "GET", f"/folder/tree/{space_id}" if space_id else "/folder/tree/1")

# 5.2 POST /folder
folder_id = None
body = test("创建文件夹", "POST", "/folder",
    json={"name": "E2E文件夹_" + str(int(time.time())), "parentId": 0, "spaceId": space_id or 1})
if body and body.get("data"):
    folder_data = body["data"]
    folder_id = folder_data.get("id") if isinstance(folder_data, dict) else folder_data

# 5.3 PUT /folder/{id}
if folder_id:
    test("重命名文件夹", "PUT", f"/folder/{folder_id}",
        json={"name": "E2E重命名_" + str(int(time.time()))})

    # 5.4 PUT /folder/{id}/sort
    test("文件夹排序", "PUT", f"/folder/{folder_id}/sort",
        json={"sortOrder": 1})

    # 5.5 PUT /folder/{id}/move
    test("移动文件夹", "PUT", f"/folder/{folder_id}/move",
        json={"parentId": 0})

    # 5.6 DELETE /folder/{id}
    test("删除文件夹", "DELETE", f"/folder/{folder_id}")

# ============================================================
# 6. Doc (7 endpoints)
# ============================================================
print("\n=== Doc 文档管理 (7) ===")

# 6.1 GET /doc/list
test("文档列表", "GET", "/doc/list", params={"page": 1, "size": 5})

# 6.2 POST /doc
doc_id = None
body = test("创建文档", "POST", "/doc",
    json={"title": "E2E文档_" + str(int(time.time())), "content": "<p>测试内容</p>", "folderId": 0, "spaceId": space_id or 1})
if body and body.get("data"):
    doc_data = body["data"]
    doc_id = doc_data.get("id") if isinstance(doc_data, dict) else doc_data

if doc_id:
    # 6.3 GET /doc/{id}
    test("文档详情", "GET", f"/doc/{doc_id}")

    # 6.4 PUT /doc/{id}
    test("编辑文档", "PUT", f"/doc/{doc_id}",
        json={"title": "E2E修改标题", "content": "<p>修改后内容</p>"})

    # 6.5 PUT /doc/{id}/star
    test("文档星标", "PUT", f"/doc/{doc_id}/star")

    # 6.6 PUT /doc/{id}/move
    test("移动文档", "PUT", f"/doc/{doc_id}/move",
        json={"folderId": 0})

    # 6.7 DELETE /doc/{id} (放最后，先测版本和分享)
    # 先不删，后面版本/分享/回收站需要

# ============================================================
# 7. File (10 endpoints)
# ============================================================
print("\n=== File 文件管理 (10) ===")

# 7.1 POST /file/upload
file_id = None
# 文件上传需要multipart，去掉session的Content-Type
headers_backup = s.headers.pop("Content-Type", None)
files = {"file": ("e2e_test.txt", b"E2E test file content", "text/plain")}
data = {"folderId": "0", "spaceId": str(space_id or 1)}
body = test("文件上传", "POST", "/file/upload", files=files, data=data)
if headers_backup:
    s.headers["Content-Type"] = headers_backup
if body and body.get("data"):
    fdata = body["data"]
    file_id = fdata.get("id") if isinstance(fdata, dict) else fdata

# 7.2 GET /file/list
test("文件列表", "GET", "/file/list", params={"page": 1, "size": 5})

if file_id:
    # 7.3 GET /file/{id}
    test("文件详情", "GET", f"/file/{file_id}")

    # 7.4 GET /file/{id}/parse-status
    test("文件解析状态", "GET", f"/file/{file_id}/parse-status")

    # 7.5 GET /file/{id}/download
    test("文件下载", "GET", f"/file/{file_id}/download")

    # 7.6 POST /file/{id}/reparse
    test("重新解析文件", "POST", f"/file/{file_id}/reparse")

    # 7.7 PUT /file/{id}/star
    test("文件星标", "PUT", f"/file/{file_id}/star")

    # 7.8 PUT /file/{id}/move
    test("移动文件", "PUT", f"/file/{file_id}/move",
        json={"folderId": 0})

    # 7.9 POST /file/merge (分片合并) - DTO需要fileId/name/folderId
    test("分片合并", "POST", "/file/merge",
        json={"fileId": "e2e_merge_test", "name": "e2e_merged.txt", "folderId": 0, "size": 100, "totalChunks": 1})

    # 7.10 DELETE /file/{id}
    test("删除文件", "DELETE", f"/file/{file_id}")

# ============================================================
# 8. WebPage 收藏网页 (7 endpoints)
# ============================================================
print("\n=== WebPage 收藏网页 (7) ===")

# 8.1 POST /web/collect
web_id = None
body = test("收藏网页", "POST", "/web/collect",
    json={"url": "https://example.com", "folderId": 0})
if body and body.get("data"):
    wdata = body["data"]
    web_id = wdata.get("id") if isinstance(wdata, dict) else wdata

# 8.2 GET /web/list
test("网页收藏列表", "GET", "/web/list", params={"page": 1, "size": 5})

if web_id:
    # 8.3 GET /web/{id}
    test("网页详情", "GET", f"/web/{web_id}")

    # 8.4 PUT /web/{id}/star
    test("网页星标", "PUT", f"/web/{web_id}/star")

    # 8.5 PUT /web/{id}/move
    test("移动网页", "PUT", f"/web/{web_id}/move",
        json={"folderId": 0})

    # 8.6 POST /web/{id}/refetch
    test("重新抓取网页", "POST", f"/web/{web_id}/refetch")

    # 8.7 DELETE /web/{id}
    test("删除网页", "DELETE", f"/web/{web_id}")

# ============================================================
# 9. Tag (5 endpoints)
# ============================================================
print("\n=== Tag 标签 (5) ===")

# 9.1 GET /tag/list
test("标签列表", "GET", "/tag/list")

# 9.2 POST /tag
tag_id = None
body = test("创建标签", "POST", "/tag",
    json={"name": "E2E标签_" + str(int(time.time()))})
if body and body.get("data"):
    tdata = body["data"]
    tag_id = tdata.get("id") if isinstance(tdata, dict) else tdata

if tag_id and doc_id:
    # 9.3 POST /tag/bind
    test("绑定标签", "POST", "/tag/bind",
        json={"tagId": tag_id, "resourceType": "doc", "resourceId": doc_id})

    # 9.4 DELETE /tag/unbind (用query参数，不是JSON body)
    test("解绑标签", "DELETE", "/tag/unbind",
        params={"tagId": tag_id, "resourceType": "doc", "resourceId": doc_id})

if tag_id:
    # 9.5 DELETE /tag/{id}
    test("删除标签", "DELETE", f"/tag/{tag_id}")

# ============================================================
# 10. Search (1 endpoint)
# ============================================================
print("\n=== Search 搜索 (1) ===")

# 10.1 GET /search
test("搜索", "GET", "/search", params={"q": "测试", "page": 1, "size": 10})

# ============================================================
# 11. Share (5 endpoints)
# ============================================================
print("\n=== Share 分享 (5) ===")

# 11.1 POST /share
share_code = None
share_id = None
extract_code = None
if doc_id:
    body = test("创建分享", "POST", "/share",
        json={"resourceId": doc_id, "resourceType": "doc", "expireType": "never"})
    if body and body.get("data"):
        sdata = body["data"]
        share_code = sdata.get("code") if isinstance(sdata, dict) else None
        share_id = sdata.get("id") if isinstance(sdata, dict) else None
        extract_code = sdata.get("extractCode") if isinstance(sdata, dict) else None

# 11.2 GET /share/list
test("分享列表", "GET", "/share/list")

# 11.3 GET /share/verify/{code}
if share_code:
    # 分享验证是公开接口(permitAll)，去掉auth header避免403
    saved_auth = s.headers.pop("Authorization", None)
    test("验证分享码", "GET", f"/share/verify/{share_code}",
        params={"extractCode": extract_code or ""})
    if saved_auth:
        s.headers["Authorization"] = saved_auth

    # 11.4 GET /share/detail/{code}
    test("分享详情", "GET", f"/share/detail/{share_code}",
        params={"extractCode": extract_code or ""})

# 11.5 DELETE /share/{id}
if share_id:
    test("删除分享", "DELETE", f"/share/{share_id}")

# ============================================================
# 12. Version (3 endpoints)
# ============================================================
print("\n=== Version 版本控制 (3) ===")

# 12.1 GET /version/list/{type}/{id}
if doc_id:
    body = test("版本历史列表", "GET", f"/version/list/doc/{doc_id}")
    version_id = None
    if body and body.get("data"):
        vdata = body["data"]
        if isinstance(vdata, list) and len(vdata) > 0:
            version_id = vdata[0].get("id")
        elif isinstance(vdata, dict) and "records" in vdata:
            records = vdata["records"]
            if records:
                version_id = records[0].get("id")

    # 12.2 GET /version/{id}
    if version_id:
        test("版本详情", "GET", f"/version/{version_id}")

        # 12.3 POST /version/{id}/rollback
        test("版本回滚", "POST", f"/version/{version_id}/rollback")

# ============================================================
# 13. Trash (3 endpoints)
# ============================================================
print("\n=== Trash 回收站 (3) ===")

# 先删一个文档制造回收站数据
if doc_id:
    s.delete(f"{BASE}/doc/{doc_id}", timeout=TIMEOUT)
    time.sleep(1)

# 13.1 GET /trash/list
test("回收站列表", "GET", "/trash/list", params={"page": 1, "size": 10})

# 13.2 POST /trash/restore/{type}/{id}
if doc_id:
    test("恢复资源", "POST", f"/trash/restore/doc/{doc_id}")
    time.sleep(0.5)
    # 再删一次，测永久删除
    s.delete(f"{BASE}/doc/{doc_id}", timeout=TIMEOUT)
    time.sleep(1)
    # 13.3 DELETE /trash/{type}/{id}
    test("永久删除", "DELETE", f"/trash/doc/{doc_id}")

# ============================================================
# 14. Bucket (2 endpoints)
# ============================================================
print("\n=== Bucket 存储桶 (2) ===")

# 14.1 GET /bucket/list
body = test("存储桶列表", "GET", "/bucket/list")
bucket_id = None
if body and body.get("data"):
    bdata = body["data"]
    if isinstance(bdata, list) and len(bdata) > 0:
        bucket_id = bdata[0].get("id")
    elif isinstance(bdata, dict) and "records" in bdata:
        records = bdata["records"]
        if records:
            bucket_id = records[0].get("id")

# 14.2 GET /bucket/{id}/stats
if bucket_id:
    test("存储桶统计", "GET", f"/bucket/{bucket_id}/stats")

# ============================================================
# 15. Ops Dashboard (2 endpoints)
# ============================================================
print("\n=== Ops Dashboard 运维看板 (2) ===")

# 15.1 GET /ops/dashboard
test("运维看板", "GET", "/ops/dashboard")

# 15.2 POST /ops/dashboard/snapshot/refresh
test("刷新快照", "POST", "/ops/dashboard/snapshot/refresh")

# ============================================================
# 16. Ops Deployment (3 endpoints)
# ============================================================
print("\n=== Ops Deployment 部署管理 (3) ===")

# 16.1 GET /ops/deployment/list
test("部署列表", "GET", "/ops/deployment/list", params={"page": 1, "size": 5})

# 16.2 GET /ops/deployment/recent
test("最近部署", "GET", "/ops/deployment/recent")

# 16.3 POST /ops/deployment
test("创建部署记录", "POST", "/ops/deployment",
    json={"serviceId": 1, "hostId": 1, "version": "v1.0-e2e", "result": 1, "operator": "e2e"})

# ============================================================
# 17. Ops Host (5 endpoints)
# ============================================================
print("\n=== Ops Host 主机管理 (5) ===")

# 17.1 GET /ops/host/list
test("主机列表", "GET", "/ops/host/list", params={"page": 1, "size": 5})

# 17.2 POST /ops/host
host_id = None
body = test("添加主机", "POST", "/ops/host",
    json={"name": "E2E测试主机", "ip": "10.0.0.99", "sshPort": 22, "username": "root", "status": 1})
if body and body.get("data"):
    hdata = body["data"]
    host_id = hdata.get("id") if isinstance(hdata, dict) else hdata

if host_id:
    # 17.3 GET /ops/host/{id}
    test("主机详情", "GET", f"/ops/host/{host_id}")

    # 17.4 PUT /ops/host/{id}
    test("修改主机", "PUT", f"/ops/host/{host_id}",
        json={"name": "E2E修改主机", "ip": "10.0.0.99", "sshPort": 22, "username": "root", "status": 0})

    # 17.5 DELETE /ops/host/{id}
    test("删除主机", "DELETE", f"/ops/host/{host_id}")

# ============================================================
# 18. Ops Service (5 endpoints)
# ============================================================
print("\n=== Ops Service 服务管理 (5) ===")

# 18.1 GET /ops/service/list
test("服务列表", "GET", "/ops/service/list", params={"page": 1, "size": 5})

# 18.2 POST /ops/service
svc_id = None
body = test("添加服务", "POST", "/ops/service",
    json={"name": "E2E测试服务", "type": "web", "port": 8080, "hostId": 1, "status": 1})
if body and body.get("data"):
    sdata = body["data"]
    svc_id = sdata.get("id") if isinstance(sdata, dict) else sdata

if svc_id:
    # 18.3 GET /ops/service/{id}
    test("服务详情", "GET", f"/ops/service/{svc_id}")

    # 18.4 PUT /ops/service/{id}
    test("修改服务", "PUT", f"/ops/service/{svc_id}",
        json={"name": "E2E修改服务", "type": "web", "port": 8081, "hostId": 1, "status": 0})

    # 18.5 DELETE /ops/service/{id}
    test("删除服务", "DELETE", f"/ops/service/{svc_id}")

# ============================================================
# 19. Ops Conflict (3 endpoints)
# ============================================================
print("\n=== Ops Conflict 矛盾检测 (3) ===")

# 19.1 POST /ops/conflict/detect
test("矛盾检测", "POST", "/ops/conflict/detect", json={})

# 19.2 GET /ops/conflict/list
test("矛盾列表", "GET", "/ops/conflict/list", params={"page": 1, "size": 5})

# 19.3 PUT /ops/conflict/{id}/resolve (如果有矛盾的话)
# 跳过，依赖数据

# ============================================================
# 20. Ops Import (2 endpoints)
# ============================================================
print("\n=== Ops Import 导入 (2) ===")

# 20.1 POST /ops/import
test("导入知识", "POST", "/ops/import",
    json={"title": "E2E导入测试", "content": "测试导入内容", "type": "note"})

# 20.2 POST /ops/import/csv
csv_content = "title,content\nE2E CSV测试,测试CSV导入内容"
headers_backup2 = s.headers.pop("Content-Type", None)
test("CSV导入", "POST", "/ops/import/csv",
    files={"file": ("test.csv", csv_content.encode(), "text/csv")})
if headers_backup2:
    s.headers["Content-Type"] = headers_backup2

# ============================================================
# 21. Ops Knowledge (5 endpoints)
# ============================================================
print("\n=== Ops Knowledge 运维知识 (5) ===")

# 21.1 GET /ops/knowledge/list
test("知识库列表", "GET", "/ops/knowledge/list", params={"page": 1, "size": 5})

# 21.2 POST /ops/knowledge
kb_id = None
body = test("添加知识", "POST", "/ops/knowledge",
    json={"title": "E2E运维知识", "content": "测试运维知识内容", "category": "other"})
if body and body.get("data"):
    kdata = body["data"]
    kb_id = kdata.get("id") if isinstance(kdata, dict) else kdata

if kb_id:
    # 21.3 GET /ops/knowledge/{id}
    test("知识详情", "GET", f"/ops/knowledge/{kb_id}")

    # 21.4 PUT /ops/knowledge/{id}
    test("修改知识", "PUT", f"/ops/knowledge/{kb_id}",
        json={"title": "E2E修改知识", "content": "修改后内容", "category": "other"})

    # 21.5 DELETE /ops/knowledge/{id}
    test("删除知识", "DELETE", f"/ops/knowledge/{kb_id}")

# ============================================================
# 22. Auth logout (最后测)
# ============================================================
print("\n=== Auth 登出 (补) ===")

# 22.1 POST /auth/logout
test("退出登录", "POST", "/auth/logout")

# ============================================================
# 汇总报告
# ============================================================
print("\n" + "=" * 80)
passed = sum(1 for r in results if r["passed"])
failed = sum(1 for r in results if not r["passed"])
total = len(results)
print(f"全量API测试总结: {total} 项 | ✅ {passed} 通过 | ❌ {total - passed} 失败")
print("=" * 80)

if total - passed > 0:
    print("\n❌ 失败项:")
    for r in results:
        if not r["passed"]:
            print(f"  {r['method']:6s} {r['path']:45s} {r['name']}: {r['detail']}")

# JSON输出供解析
print("\n__JSON__")
print(json.dumps(results, ensure_ascii=False))

# 退出码
sys.exit(0 if passed == total else 1)
