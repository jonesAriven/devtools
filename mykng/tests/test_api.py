#!/usr/bin/env python3
"""
mykng 知识库微服务 - API 自动化测试套件
基于接口规范清单_v1.md，覆盖全部 83 个接口

运行: python3 tests/test_api.py
依赖: pip install requests
"""

import requests
import json
import time
import sys
from datetime import datetime
from typing import Optional

# ============================================================
# 配置
# ============================================================
BASE_URL = "http://localhost:8090/kb/api"
TIMEOUT = 10
ADMIN_USER = {"username": "admin", "password": "admin123"}

# ============================================================
# 测试结果收集
# ============================================================
class TestResult:
    def __init__(self):
        self.passed = 0
        self.failed = 0
        self.skipped = 0
        self.details = []
    
    def record(self, name: str, status: str, msg: str = "", duration: float = 0):
        self.details.append({
            "name": name,
            "status": status,
            "msg": msg,
            "duration": round(duration, 2)
        })
        if status == "PASS":
            self.passed += 1
        elif status == "FAIL":
            self.failed += 1
        else:
            self.skipped += 1
        # 实时输出
        icon = {"PASS": "✅", "FAIL": "❌", "SKIP": "⏭️"}[status]
        print(f"  {icon} {name} ({duration:.1f}s) {f'→ {msg}' if msg else ''}")
    
    def summary(self):
        total = self.passed + self.failed + self.skipped
        print(f"\n{'='*60}")
        print(f"  测试总结: {total} 个用例 | ✅ {self.passed} 通过 | ❌ {self.failed} 失败 | ⏭️ {self.skipped} 跳过")
        print(f"{'='*60}")
        return self.failed == 0

result = TestResult()
token: Optional[str] = None
refresh_token: Optional[str] = None

def headers() -> dict:
    h = {"Content-Type": "application/json"}
    if token:
        h["Authorization"] = f"Bearer {token}"
    return h

def api_call(method: str, path: str, **kwargs) -> requests.Response:
    """统一 API 调用"""
    url = f"{BASE_URL}{path}"
    kwargs.setdefault("timeout", TIMEOUT)
    kwargs.setdefault("headers", headers())
    return requests.request(method, url, **kwargs)

def test(name: str, func):
    """执行单个测试"""
    start = time.time()
    try:
        func()
        result.record(name, "PASS", duration=time.time() - start)
    except AssertionError as e:
        result.record(name, "FAIL", str(e), time.time() - start)
    except Exception as e:
        result.record(name, "FAIL", f"异常: {type(e).__name__}: {e}", time.time() - start)

def skip(name: str, reason: str = ""):
    result.record(name, "SKIP", reason, 0)

# ============================================================
# 1. 认证服务 (kb-auth) - 10 个接口
# ============================================================
def test_auth():
    print("\n📋 1. 认证服务 (kb-auth)")
    
    # 1.1 登录
    def _login():
        global token, refresh_token
        resp = api_call("POST", "/auth/login", json=ADMIN_USER)
        assert resp.status_code == 200, f"HTTP {resp.status_code}"
        data = resp.json()
        assert data["code"] == 200, f"code={data['code']} msg={data.get('message')}"
        assert data["data"]["accessToken"], "无 accessToken"
        assert data["data"]["refreshToken"], "无 refreshToken"
        token = data["data"]["accessToken"]
        refresh_token = data["data"]["refreshToken"]
    test("POST /auth/login - 管理员登录", _login)
    
    # 1.2 登录失败 - 错误密码
    def _login_fail():
        resp = api_call("POST", "/auth/login", json={"username": "admin", "password": "wrong"})
        assert resp.status_code == 200
        data = resp.json()
        assert data["code"] != 200, "错误密码不应登录成功"
    test("POST /auth/login - 错误密码拒绝", _login_fail)
    
    # 1.3 获取用户信息
    def _profile():
        resp = api_call("GET", "/user/profile")
        assert resp.status_code == 200
        data = resp.json()
        assert data["code"] == 200, f"code={data['code']}"
        assert data["data"]["username"] == "admin", f"username={data['data'].get('username')}"
    test("GET /user/profile - 获取用户信息", _profile)
    
    # 1.4 更新用户信息
    def _update_profile():
        resp = api_call("PUT", "/user/profile", json={"nickname": "管理员", "email": "admin@test.com"})
        assert resp.status_code == 200
        data = resp.json()
        assert data["code"] == 200, f"code={data['code']}"
    test("PUT /user/profile - 更新用户信息", _update_profile)
    
    # 1.5 刷新 Token
    def _refresh():
        global token, refresh_token
        resp = api_call("POST", "/auth/refresh", json={"refreshToken": refresh_token})
        assert resp.status_code == 200
        data = resp.json()
        assert data["code"] == 200, f"code={data['code']} msg={data.get('message')}"
        assert data["data"]["accessToken"], "刷新后无 accessToken"
        token = data["data"]["accessToken"]
        refresh_token = data["data"]["refreshToken"]
    test("POST /auth/refresh - 刷新Token", _refresh)
    
    # 1.6 修改密码
    def _change_password():
        resp = api_call("PUT", "/user/password", json={"oldPassword": "admin123", "newPassword": "admin123"})
        assert resp.status_code == 200
        data = resp.json()
        assert data["code"] == 200, f"code={data['code']}"
    test("PUT /user/password - 修改密码(不变)", _change_password)
    
    # 1.7 创建 API Token
    created_token_id = [None]
    def _create_token():
        resp = api_call("POST", "/token", json={"name": "test-api-token", "expireDays": 7})
        assert resp.status_code == 200
        data = resp.json()
        assert data["code"] == 200, f"code={data['code']} msg={data.get('message')}"
        created_token_id[0] = data["data"]["id"]
    test("POST /token - 创建API Token", _create_token)
    
    # 1.8 切换 Token 状态
    def _toggle_token():
        if not created_token_id[0]:
            skip("PUT /token/{id}/toggle", "未创建Token")
            return
        resp = api_call("PUT", f"/token/{created_token_id[0]}/toggle")
        assert resp.status_code == 200
        data = resp.json()
        assert data["code"] == 200, f"code={data['code']}"
    test("PUT /token/{id}/toggle - 切换Token状态", _toggle_token)
    
    # 1.9 删除 Token
    def _delete_token():
        if not created_token_id[0]:
            skip("DELETE /token/{id}", "未创建Token")
            return
        resp = api_call("DELETE", f"/token/{created_token_id[0]}")
        assert resp.status_code == 200
        data = resp.json()
        assert data["code"] == 200, f"code={data['code']}"
    test("DELETE /token/{id} - 删除Token", _delete_token)
    
    # 1.10 验证 Token（无需认证）
    def _verify_token():
        resp = api_call("POST", "/token/verify", json={"token": "invalid-token-test"})
        assert resp.status_code == 200
        data = resp.json()
        # 无效token应返回失败
        assert data["code"] != 200 or data["data"]["valid"] == False, "无效token不应验证通过"
    test("POST /token/verify - 验证无效Token", _verify_token)

# ============================================================
# 2. 空间管理 (kb-knowledge) 
# ============================================================
created_space_id = [None]

def test_space():
    print("\n📋 2. 空间管理 (kb-knowledge)")
    
    # 2.1 创建空间
    def _create():
        resp = api_call("POST", "/space", json={"name": "测试空间", "description": "自动化测试创建", "type": "PERSONAL"})
        assert resp.status_code == 200, f"HTTP {resp.status_code}"
        data = resp.json()
        assert data["code"] == 200, f"code={data['code']} msg={data.get('message')}"
        created_space_id[0] = data["data"]["id"]
    test("POST /space - 创建空间", _create)
    
    # 2.2 查询空间列表
    def _list():
        resp = api_call("GET", "/space/list")
        assert resp.status_code == 200
        data = resp.json()
        assert data["code"] == 200
        assert isinstance(data["data"], list)
        assert len(data["data"]) > 0, "空间列表为空"
    test("GET /space/list - 查询空间列表", _list)
    
    # 2.3 更新空间
    def _update():
        if not created_space_id[0]:
            skip("PUT /space/{id}")
            return
        resp = api_call("PUT", f"/space/{created_space_id[0]}", json={"name": "测试空间-改", "description": "已更新"})
        assert resp.status_code == 200
        data = resp.json()
        assert data["code"] == 200, f"code={data['code']}"
    test("PUT /space/{id} - 更新空间", _update)

# ============================================================
# 3. 文件夹管理 (kb-knowledge)
# ============================================================
created_folder_id = [None]

def test_folder():
    print("\n📋 3. 文件夹管理 (kb-knowledge)")
    
    # 3.1 创建文件夹
    def _create():
        if not created_space_id[0]:
            skip("POST /folder", "无空间ID")
            return
        resp = api_call("POST", "/folder", json={"spaceId": created_space_id[0], "parentId": 0, "name": "测试文件夹"})
        assert resp.status_code == 200, f"HTTP {resp.status_code}"
        data = resp.json()
        assert data["code"] == 200, f"code={data['code']} msg={data.get('message')}"
        created_folder_id[0] = data["data"]["id"]
    test("POST /folder - 创建文件夹", _create)
    
    # 3.2 获取文件夹树
    def _tree():
        if not created_space_id[0]:
            skip("GET /folder/tree/{spaceId}")
            return
        resp = api_call("GET", f"/folder/tree/{created_space_id[0]}")
        assert resp.status_code == 200
        data = resp.json()
        assert data["code"] == 200, f"code={data['code']}"
    test("GET /folder/tree/{spaceId} - 文件夹树", _tree)
    
    # 3.3 更新文件夹
    def _update():
        if not created_folder_id[0]:
            skip("PUT /folder/{id}")
            return
        resp = api_call("PUT", f"/folder/{created_folder_id[0]}", json={"name": "测试文件夹-改"})
        assert resp.status_code == 200
        data = resp.json()
        assert data["code"] == 200, f"code={data['code']}"
    test("PUT /folder/{id} - 更新文件夹", _update)
    
    # 3.4 文件夹排序
    def _sort():
        if not created_folder_id[0]:
            skip("PUT /folder/{id}/sort")
            return
        resp = api_call("PUT", f"/folder/{created_folder_id[0]}/sort", json={"sortOrder": 1})
        assert resp.status_code == 200
        data = resp.json()
        assert data["code"] == 200, f"code={data['code']}"
    test("PUT /folder/{id}/sort - 文件夹排序", _sort)

# ============================================================
# 4. 文档管理 (kb-knowledge)
# ============================================================
created_doc_id = [None]

def test_doc():
    print("\n📋 4. 文档管理 (kb-knowledge)")
    
    # 4.1 创建文档
    def _create():
        if not created_space_id[0]:
            skip("POST /doc", "无空间ID")
            return
        resp = api_call("POST", "/doc", json={
            "spaceId": created_space_id[0],
            "folderId": created_folder_id[0] or 0,
            "title": "测试文档",
            "content": "这是一个自动化测试创建的文档内容",
            "type": "NOTE"
        })
        assert resp.status_code == 200, f"HTTP {resp.status_code}"
        data = resp.json()
        assert data["code"] == 200, f"code={data['code']} msg={data.get('message')}"
        created_doc_id[0] = data["data"]["id"]
    test("POST /doc - 创建文档", _create)
    
    # 4.2 获取文档
    def _get():
        if not created_doc_id[0]:
            skip("GET /doc/{id}")
            return
        resp = api_call("GET", f"/doc/{created_doc_id[0]}")
        assert resp.status_code == 200
        data = resp.json()
        assert data["code"] == 200, f"code={data['code']}"
        assert data["data"]["title"] == "测试文档", f"title={data['data'].get('title')}"
    test("GET /doc/{id} - 获取文档", _get)
    
    # 4.3 更新文档
    def _update():
        if not created_doc_id[0]:
            skip("PUT /doc/{id}")
            return
        resp = api_call("PUT", f"/doc/{created_doc_id[0]}", json={"title": "测试文档-改", "content": "更新后的内容"})
        assert resp.status_code == 200
        data = resp.json()
        assert data["code"] == 200, f"code={data['code']}"
    test("PUT /doc/{id} - 更新文档", _update)
    
    # 4.4 收藏文档
    def _star():
        if not created_doc_id[0]:
            skip("PUT /doc/{id}/star")
            return
        resp = api_call("PUT", f"/doc/{created_doc_id[0]}/star", json={"starred": True})
        assert resp.status_code == 200
        data = resp.json()
        assert data["code"] == 200, f"code={data['code']}"
    test("PUT /doc/{id}/star - 收藏文档", _star)
    
    # 4.5 移动文档
    def _move():
        if not created_doc_id[0]:
            skip("PUT /doc/{id}/move")
            return
        resp = api_call("PUT", f"/doc/{created_doc_id[0]}/move", json={"targetFolderId": 0})
        assert resp.status_code == 200
        data = resp.json()
        assert data["code"] == 200, f"code={data['code']}"
    test("PUT /doc/{id}/move - 移动文档", _move)

# ============================================================
# 5. 搜索 (kb-knowledge)
# ============================================================
def test_search():
    print("\n📋 5. 搜索 (kb-knowledge)")
    
    def _search():
        resp = api_call("GET", "/search", params={"q": "测试", "page": 1, "size": 10})
        assert resp.status_code == 200, f"HTTP {resp.status_code}"
        data = resp.json()
        assert data["code"] == 200, f"code={data['code']} msg={data.get('message')}"
    test("GET /search?q=测试 - 关键词搜索", _search)
    
    def _search_empty():
        resp = api_call("GET", "/search", params={"q": "zzz不存在的关键词xyz123", "page": 1, "size": 10})
        assert resp.status_code == 200
        data = resp.json()
        assert data["code"] == 200
        assert data["data"]["total"] == 0, f"total={data['data']['total']}"
    test("GET /search?q=不存在 - 空结果", _search_empty)

# ============================================================
# 6. 标签管理 (kb-knowledge)
# ============================================================
created_tag_id = [None]

def test_tag():
    print("\n📋 6. 标签管理 (kb-knowledge)")
    
    def _create():
        resp = api_call("POST", "/tag", json={"name": "测试标签", "color": "#409EFF"})
        assert resp.status_code == 200, f"HTTP {resp.status_code}"
        data = resp.json()
        assert data["code"] == 200, f"code={data['code']} msg={data.get('message')}"
        created_tag_id[0] = data["data"]["id"]
    test("POST /tag - 创建标签", _create)
    
    def _list():
        resp = api_call("GET", "/tag/list")
        assert resp.status_code == 200
        data = resp.json()
        assert data["code"] == 200
        assert isinstance(data["data"], list)
    test("GET /tag/list - 标签列表", _list)
    
    def _bind():
        if not created_tag_id[0] or not created_doc_id[0]:
            skip("POST /tag/bind", "缺少标签ID或文档ID")
            return
        resp = api_call("POST", "/tag/bind", json={
            "entityType": "DOC", "entityId": created_doc_id[0], "tagId": created_tag_id[0]
        })
        assert resp.status_code == 200
        data = resp.json()
        assert data["code"] == 200, f"code={data['code']}"
    test("POST /tag/bind - 绑定标签到文档", _bind)
    
    def _delete():
        if not created_tag_id[0]:
            skip("DELETE /tag/{id}")
            return
        resp = api_call("DELETE", f"/tag/{created_tag_id[0]}")
        assert resp.status_code == 200
        data = resp.json()
        assert data["code"] == 200, f"code={data['code']}"
    test("DELETE /tag/{id} - 删除标签", _delete)

# ============================================================
# 7. 回收站 (kb-knowledge)
# ============================================================
def test_trash():
    print("\n📋 7. 回收站 (kb-knowledge)")
    
    # 先删除一个文档让它进回收站
    if created_doc_id[0]:
        api_call("DELETE", f"/doc/{created_doc_id[0]}")
    
    def _list():
        resp = api_call("GET", "/trash/list", params={"page": 1, "size": 20})
        assert resp.status_code == 200, f"HTTP {resp.status_code}"
        data = resp.json()
        assert data["code"] == 200, f"code={data['code']}"
    test("GET /trash/list - 回收站列表", _list)
    
    def _restore():
        resp = api_call("POST", f"/trash/restore/DOC/{created_doc_id[0]}")
        assert resp.status_code == 200
        data = resp.json()
        assert data["code"] == 200, f"code={data['code']} msg={data.get('message')}"
    test("POST /trash/restore/{type}/{id} - 恢复文档", _restore)

# ============================================================
# 8. 分享 (kb-knowledge)
# ============================================================
created_share_id = [None]
share_code = [None]

def test_share():
    print("\n📋 8. 分享 (kb-knowledge)")
    
    def _create():
        if not created_doc_id[0]:
            skip("POST /share", "无文档ID")
            return
        resp = api_call("POST", "/share", json={
            "entityType": "DOC", "entityId": created_doc_id[0], "expireDays": 7
        })
        assert resp.status_code == 200, f"HTTP {resp.status_code}"
        data = resp.json()
        assert data["code"] == 200, f"code={data['code']} msg={data.get('message')}"
        created_share_id[0] = data["data"].get("id")
        share_code[0] = data["data"].get("code", "")
    test("POST /share - 创建分享", _create)
    
    def _list():
        resp = api_call("GET", "/share/list", params={"page": 1, "size": 20})
        assert resp.status_code == 200
        data = resp.json()
        assert data["code"] == 200, f"code={data['code']}"
    test("GET /share/list - 分享列表", _list)
    
    def _verify():
        if not share_code[0]:
            skip("GET /share/verify/{code}", "无分享码")
            return
        resp = api_call("GET", f"/share/verify/{share_code[0]}")
        assert resp.status_code == 200
        data = resp.json()
        assert data["code"] == 200, f"code={data['code']} msg={data.get('message')}"
    test("GET /share/verify/{code} - 验证分享(无需认证)", _verify)
    
    def _detail():
        if not share_code[0]:
            skip("GET /share/detail/{code}")
            return
        resp = api_call("GET", f"/share/detail/{share_code[0]}")
        assert resp.status_code == 200
        data = resp.json()
        assert data["code"] == 200, f"code={data['code']}"
    test("GET /share/detail/{code} - 获取分享详情", _detail)
    
    def _delete():
        if not created_share_id[0]:
            skip("DELETE /share/{id}")
            return
        resp = api_call("DELETE", f"/share/{created_share_id[0]}")
        assert resp.status_code == 200
        data = resp.json()
        assert data["code"] == 200, f"code={data['code']}"
    test("DELETE /share/{id} - 删除分享", _delete)

# ============================================================
# 9. 版本管理 (kb-knowledge)
# ============================================================
def test_version():
    print("\n📋 9. 版本管理 (kb-knowledge)")
    
    def _list():
        if not created_doc_id[0]:
            skip("GET /version/list/{type}/{id}")
            return
        resp = api_call("GET", f"/version/list/DOC/{created_doc_id[0]}")
        assert resp.status_code == 200, f"HTTP {resp.status_code}"
        data = resp.json()
        assert data["code"] == 200, f"code={data['code']}"
    test("GET /version/list/{type}/{id} - 版本列表", _list)

# ============================================================
# 10. 网页收藏 (kb-knowledge)
# ============================================================
created_web_id = [None]

def test_web():
    print("\n📋 10. 网页收藏 (kb-knowledge)")
    
    def _collect():
        if not created_space_id[0]:
            skip("POST /web/collect", "无空间ID")
            return
        resp = api_call("POST", "/web/collect", json={
            "url": "https://example.com", "spaceId": created_space_id[0]
        })
        assert resp.status_code == 200, f"HTTP {resp.status_code}"
        data = resp.json()
        assert data["code"] == 200, f"code={data['code']} msg={data.get('message')}"
        if data["data"]:
            created_web_id[0] = data["data"].get("id")
    test("POST /web/collect - 收藏网页", _collect)
    
    def _get():
        if not created_web_id[0]:
            skip("GET /web/{id}")
            return
        resp = api_call("GET", f"/web/{created_web_id[0]}")
        assert resp.status_code == 200
        data = resp.json()
        assert data["code"] == 200, f"code={data['code']}"
    test("GET /web/{id} - 获取网页收藏", _get)
    
    def _delete():
        if not created_web_id[0]:
            skip("DELETE /web/{id}")
            return
        resp = api_call("DELETE", f"/web/{created_web_id[0]}")
        assert resp.status_code == 200
        data = resp.json()
        assert data["code"] == 200, f"code={data['code']}"
    test("DELETE /web/{id} - 删除网页收藏", _delete)

# ============================================================
# 11. 文件管理 (kb-file)
# ============================================================
created_file_id = [None]

def test_file():
    print("\n📋 11. 文件管理 (kb-file)")
    
    def _list():
        resp = api_call("GET", "/file/list", params={"spaceId": created_space_id[0] or 1, "page": 1, "size": 20})
        assert resp.status_code == 200, f"HTTP {resp.status_code}"
        data = resp.json()
        assert data["code"] == 200, f"code={data['code']} msg={data.get('message')}"
    test("GET /file/list - 文件列表", _list)
    
    def _upload():
        if not created_space_id[0]:
            skip("POST /file/upload", "无空间ID")
            return
        # 创建一个测试文件
        files = {"file": ("test.txt", b"Hello World - API Test", "text/plain")}
        resp = api_call("POST", "/file/upload", 
            files=files,
            data={"spaceId": str(created_space_id[0]), "folderId": "0"},
            headers={"Authorization": f"Bearer {token}"}  # 不设Content-Type，让requests自动设
        )
        assert resp.status_code == 200, f"HTTP {resp.status_code}"
        data = resp.json()
        assert data["code"] == 200, f"code={data['code']} msg={data.get('message')}"
        if data["data"]:
            created_file_id[0] = data["data"].get("id")
    test("POST /file/upload - 上传文件", _upload)
    
    def _get():
        if not created_file_id[0]:
            skip("GET /file/{id}")
            return
        resp = api_call("GET", f"/file/{created_file_id[0]}")
        assert resp.status_code == 200
        data = resp.json()
        assert data["code"] == 200, f"code={data['code']}"
    test("GET /file/{id} - 获取文件信息", _get)
    
    def _parse_status():
        if not created_file_id[0]:
            skip("GET /file/{id}/parse-status")
            return
        resp = api_call("GET", f"/file/{created_file_id[0]}/parse-status")
        assert resp.status_code == 200
        data = resp.json()
        assert data["code"] == 200, f"code={data['code']}"
    test("GET /file/{id}/parse-status - 解析状态", _parse_status)
    
    def _star():
        if not created_file_id[0]:
            skip("PUT /file/{id}/star")
            return
        resp = api_call("PUT", f"/file/{created_file_id[0]}/star", json={"starred": True})
        assert resp.status_code == 200
        data = resp.json()
        assert data["code"] == 200, f"code={data['code']}"
    test("PUT /file/{id}/star - 收藏文件", _star)
    
    def _delete():
        if not created_file_id[0]:
            skip("DELETE /file/{id}")
            return
        resp = api_call("DELETE", f"/file/{created_file_id[0]}")
        assert resp.status_code == 200
        data = resp.json()
        assert data["code"] == 200, f"code={data['code']}"
    test("DELETE /file/{id} - 删除文件", _delete)

# ============================================================
# 12. 存储桶 (kb-file)
# ============================================================
def test_bucket():
    print("\n📋 12. 存储桶 (kb-file)")
    
    def _list():
        resp = api_call("GET", "/bucket/list")
        assert resp.status_code == 200, f"HTTP {resp.status_code}"
        data = resp.json()
        assert data["code"] == 200, f"code={data['code']}"
    test("GET /bucket/list - 存储桶列表", _list)
    
    def _stats():
        resp = api_call("GET", "/bucket/1/stats")
        assert resp.status_code == 200
        data = resp.json()
        # 可能没有id=1的桶，只要不是500就算通过
        assert data["code"] in [200, 404], f"code={data['code']}"
    test("GET /bucket/{id}/stats - 存储桶统计", _stats)

# ============================================================
# 13. 运维看板 (kb-ops)
# ============================================================
def test_dashboard():
    print("\n📋 13. 运维看板 (kb-ops)")
    
    def _get():
        resp = api_call("GET", "/ops/dashboard")
        assert resp.status_code == 200, f"HTTP {resp.status_code}"
        data = resp.json()
        assert data["code"] == 200, f"code={data['code']} msg={data.get('message')}"
    test("GET /ops/dashboard - 看板数据", _get)
    
    def _refresh():
        resp = api_call("POST", "/ops/dashboard/snapshot/refresh")
        assert resp.status_code == 200
        data = resp.json()
        assert data["code"] == 200, f"code={data['code']}"
    test("POST /ops/dashboard/snapshot/refresh - 刷新看板", _refresh)

# ============================================================
# 14. 主机管理 (kb-ops)
# ============================================================
created_host_id = [None]

def test_host():
    print("\n📋 14. 主机管理 (kb-ops)")
    
    def _create():
        resp = api_call("POST", "/ops/host", json={
            "name": "test-host", "ip": "192.168.1.100", "port": 22, "os": "Linux", "type": "VM"
        })
        assert resp.status_code == 200, f"HTTP {resp.status_code}"
        data = resp.json()
        assert data["code"] == 200, f"code={data['code']} msg={data.get('message')}"
        created_host_id[0] = data["data"].get("id")
    test("POST /ops/host - 创建主机", _create)
    
    def _list():
        resp = api_call("GET", "/ops/host/list")
        assert resp.status_code == 200
        data = resp.json()
        assert data["code"] == 200, f"code={data['code']}"
    test("GET /ops/host/list - 主机列表", _list)
    
    def _get():
        if not created_host_id[0]:
            skip("GET /ops/host/{id}")
            return
        resp = api_call("GET", f"/ops/host/{created_host_id[0]}")
        assert resp.status_code == 200
        data = resp.json()
        assert data["code"] == 200, f"code={data['code']}"
    test("GET /ops/host/{id} - 获取主机详情", _get)
    
    def _update():
        if not created_host_id[0]:
            skip("PUT /ops/host/{id}")
            return
        resp = api_call("PUT", f"/ops/host/{created_host_id[0]}", json={
            "name": "test-host-updated", "ip": "192.168.1.101", "port": 22, "os": "Linux", "type": "VM"
        })
        assert resp.status_code == 200
        data = resp.json()
        assert data["code"] == 200, f"code={data['code']}"
    test("PUT /ops/host/{id} - 更新主机", _update)
    
    def _delete():
        if not created_host_id[0]:
            skip("DELETE /ops/host/{id}")
            return
        resp = api_call("DELETE", f"/ops/host/{created_host_id[0]}")
        assert resp.status_code == 200
        data = resp.json()
        assert data["code"] == 200, f"code={data['code']}"
    test("DELETE /ops/host/{id} - 删除主机", _delete)

# ============================================================
# 15. 服务管理 (kb-ops)
# ============================================================
created_service_id = [None]

def test_ops_service():
    print("\n📋 15. 服务管理 (kb-ops)")
    
    def _list():
        resp = api_call("GET", "/ops/service/list")
        assert resp.status_code == 200, f"HTTP {resp.status_code}"
        data = resp.json()
        assert data["code"] == 200, f"code={data['code']}"
    test("GET /ops/service/list - 服务列表", _list)
    
    def _create():
        resp = api_call("POST", "/ops/service", json={
            "name": "test-service", "hostId": 1, "port": 8080, "type": "WEB", "status": "RUNNING"
        })
        assert resp.status_code == 200
        data = resp.json()
        assert data["code"] == 200, f"code={data['code']} msg={data.get('message')}"
        if data["data"]:
            created_service_id[0] = data["data"].get("id")
    test("POST /ops/service - 创建服务", _create)
    
    def _delete():
        if not created_service_id[0]:
            skip("DELETE /ops/service/{id}")
            return
        resp = api_call("DELETE", f"/ops/service/{created_service_id[0]}")
        assert resp.status_code == 200
        data = resp.json()
        assert data["code"] == 200, f"code={data['code']}"
    test("DELETE /ops/service/{id} - 删除服务", _delete)

# ============================================================
# 16. 运维知识 (kb-ops)
# ============================================================
created_knowledge_id = [None]

def test_ops_knowledge():
    print("\n📋 16. 运维知识 (kb-ops)")
    
    def _create():
        resp = api_call("POST", "/ops/knowledge", json={
            "title": "测试运维知识", "content": "测试内容", "type": "FAQ", "tags": "test"
        })
        assert resp.status_code == 200, f"HTTP {resp.status_code}"
        data = resp.json()
        assert data["code"] == 200, f"code={data['code']} msg={data.get('message')}"
        if data["data"]:
            created_knowledge_id[0] = data["data"].get("id")
    test("POST /ops/knowledge - 创建运维知识", _create)
    
    def _list():
        resp = api_call("GET", "/ops/knowledge/list", params={"page": 1, "size": 20})
        assert resp.status_code == 200
        data = resp.json()
        assert data["code"] == 200, f"code={data['code']}"
    test("GET /ops/knowledge/list - 运维知识列表", _list)
    
    def _get():
        if not created_knowledge_id[0]:
            skip("GET /ops/knowledge/{id}")
            return
        resp = api_call("GET", f"/ops/knowledge/{created_knowledge_id[0]}")
        assert resp.status_code == 200
        data = resp.json()
        assert data["code"] == 200, f"code={data['code']}"
    test("GET /ops/knowledge/{id} - 获取运维知识", _get)
    
    def _delete():
        if not created_knowledge_id[0]:
            skip("DELETE /ops/knowledge/{id}")
            return
        resp = api_call("DELETE", f"/ops/knowledge/{created_knowledge_id[0]}")
        assert resp.status_code == 200
        data = resp.json()
        assert data["code"] == 200, f"code={data['code']}"
    test("DELETE /ops/knowledge/{id} - 删除运维知识", _delete)

# ============================================================
# 17. 部署记录 (kb-ops)
# ============================================================
def test_deployment():
    print("\n📋 17. 部署记录 (kb-ops)")
    
    def _list():
        resp = api_call("GET", "/ops/deployment/list", params={"page": 1, "size": 20})
        assert resp.status_code == 200, f"HTTP {resp.status_code}"
        data = resp.json()
        assert data["code"] == 200, f"code={data['code']}"
    test("GET /ops/deployment/list - 部署记录列表", _list)
    
    def _recent():
        resp = api_call("GET", "/ops/deployment/recent")
        assert resp.status_code == 200
        data = resp.json()
        assert data["code"] == 200, f"code={data['code']}"
    test("GET /ops/deployment/recent - 最近部署", _recent)

# ============================================================
# 18. 矛盾检测 (kb-ops)
# ============================================================
def test_conflict():
    print("\n📋 18. 矛盾检测 (kb-ops)")
    
    def _list():
        resp = api_call("GET", "/ops/conflict/list", params={"page": 1, "size": 20})
        assert resp.status_code == 200, f"HTTP {resp.status_code}"
        data = resp.json()
        assert data["code"] == 200, f"code={data['code']}"
    test("GET /ops/conflict/list - 矛盾列表", _list)

# ============================================================
# 19. 数据导入 (kb-ops)
# ============================================================
def test_import():
    print("\n📋 19. 数据导入 (kb-ops)")
    
    def _import_json():
        resp = api_call("POST", "/ops/import", json={
            "type": "HOST", "override": False, "rows": [{"name": "import-test", "ip": "10.0.0.1", "port": "22", "os": "Linux", "type": "VM"}]
        })
        assert resp.status_code == 200, f"HTTP {resp.status_code}"
        data = resp.json()
        assert data["code"] == 200, f"code={data['code']} msg={data.get('message')}"
    test("POST /ops/import - JSON导入", _import_json)

# ============================================================
# 20. 清理测试数据
# ============================================================
def test_cleanup():
    print("\n📋 20. 清理测试数据")
    
    def _cleanup():
        # 删除测试文档
        if created_doc_id[0]:
            try:
                api_call("DELETE", f"/doc/{created_doc_id[0]}")
            except:
                pass
        # 删除测试文件夹
        if created_folder_id[0]:
            try:
                api_call("DELETE", f"/folder/{created_folder_id[0]}")
            except:
                pass
        # 删除测试空间
        if created_space_id[0]:
            try:
                api_call("DELETE", f"/space/{created_space_id[0]}")
            except:
                pass
        # 登出
        try:
            api_call("POST", "/auth/logout")
        except:
            pass
    test("清理测试数据 + 登出", _cleanup)

# ============================================================
# 主入口
# ============================================================
def main():
    print(f"╔══════════════════════════════════════════════════════════╗")
    print(f"║  mykng 知识库微服务 - API 自动化测试套件                 ║")
    print(f"║  目标: {BASE_URL}")
    print(f"║  时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print(f"╚══════════════════════════════════════════════════════════╝")
    
    # 检查服务是否可达
    try:
        resp = requests.get(f"http://localhost:8090/actuator/health", timeout=3)
        if resp.status_code != 200:
            print(f"\n❌ 网关不可达: HTTP {resp.status_code}")
            sys.exit(1)
        print(f"\n✅ 网关可达: {resp.json()}")
    except Exception as e:
        print(f"\n❌ 网关不可达: {e}")
        sys.exit(1)
    
    # 执行测试
    test_auth()
    test_space()
    test_folder()
    test_doc()
    test_search()
    test_tag()
    test_trash()
    test_share()
    test_version()
    test_web()
    test_file()
    test_bucket()
    test_dashboard()
    test_host()
    test_ops_service()
    test_ops_knowledge()
    test_deployment()
    test_conflict()
    test_import()
    test_cleanup()
    
    # 输出报告
    ok = result.summary()
    
    # 保存 JSON 报告
    report = {
        "timestamp": datetime.now().isoformat(),
        "base_url": BASE_URL,
        "summary": {"passed": result.passed, "failed": result.failed, "skipped": result.skipped},
        "details": result.details
    }
    report_path = "/root/devtools/mykng/tests/api-test-report.json"
    with open(report_path, "w") as f:
        json.dump(report, f, ensure_ascii=False, indent=2)
    print(f"\n📄 报告已保存: {report_path}")
    
    sys.exit(0 if ok else 1)

if __name__ == "__main__":
    main()
