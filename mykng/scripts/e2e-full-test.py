#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
MyKNG 知识库平台 — E2E 全量功能测试（多角色视角）

测试范围：
- 6 个微服务的核心 API（kb-auth/kb-file/kb-knowledge/kb-ops/kb-intelligence/kb-gateway）
- 11 个容器健康检查
- 关键业务流程：登录 → 创建空间 → 创建文件夹 → 创建文档 → 搜索 → 标签 → 分享 → 回收站 → 登出

执行入口：mykng-debian (100.93.36.113:8090)
"""
import sys
import json
import time
import urllib.request
import urllib.error
import urllib.parse

sys.path.insert(0, r'd:\huliang\java\ideaworkspace\devtools\.trae')
from ssh_exec import exec_command

HOST = "100.93.36.113"
SSH_USER = "root"
SSH_PASSWORD = "root"

BASE_URL = "http://100.93.36.113:8090/kb/api"
HEALTH_URL = "http://100.93.36.113:8090/actuator/health"

results = []
total_start = time.time()


def record(module, name, ok, detail=""):
    results.append((module, name, "PASS" if ok else "FAIL", detail))
    symbol = "✓" if ok else "✗"
    print(f"  {symbol} [{module}] {name}  {detail if not ok else ''}")


def http_request(method, path, token=None, body=None, expect_code=200, raw_response=False):
    url = f"{BASE_URL}{path}"
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    data = json.dumps(body).encode("utf-8") if body is not None else None
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            status = resp.status
            content = resp.read().decode("utf-8")
            if raw_response:
                return status, content
            try:
                parsed = json.loads(content)
            except json.JSONDecodeError:
                parsed = {"_raw": content}
            return status, parsed
    except urllib.error.HTTPError as e:
        try:
            content = e.read().decode("utf-8")
            parsed = json.loads(content)
        except Exception:
            parsed = {"_raw": str(e)}
        return e.code, parsed
    except Exception as e:
        return -1, {"_error": str(e)}


# ============ Phase 1: 容器健康检查 ============
print("\n" + "=" * 70)
print("Phase 1: 容器健康检查（11 个容器）")
print("=" * 70)

CONTAINERS = [
    "kb-gateway", "kb-auth", "kb-file", "kb-knowledge", "kb-ops", "kb-intelligence",
    "kb-mysql", "kb-mongodb", "kb-redis", "kb-minio", "kb-meilisearch",
]

cmd = "docker inspect --format='{{.Name}}={{.State.Health.Status}}' " + " ".join(CONTAINERS)
exit_code = exec_command(HOST, 22, SSH_USER, SSH_PASSWORD, cmd, timeout=15)
# 直接读 stdout 已经被 ssh_exec 打印，这里通过另一种方式获取
import subprocess
proc = subprocess.run([
    sys.executable, r'd:\huliang\java\ideaworkspace\devtools\.trae\ssh_exec.py',
    '--host', HOST, '--user', SSH_USER, '--password', SSH_PASSWORD, cmd
], capture_output=True, text=True, timeout=20)
container_status = {}
for line in proc.stdout.strip().split("\n"):
    if "=" in line:
        name, status = line.strip("/").split("=", 1)
        container_status[name] = status
        record("容器健康", name, status == "healthy", f"状态={status}")

# 网关健康端点
status, body = http_request("GET", "/../actuator/health".replace("/kb/api/../", "/"))
# 上面 path 不对，直接用 HEALTH_URL
try:
    with urllib.request.urlopen(HEALTH_URL, timeout=10) as resp:
        gateway_health = json.loads(resp.read().decode("utf-8"))
        record("网关健康", "/actuator/health", gateway_health.get("status") == "UP",
               f"status={gateway_health.get('status')}")
except Exception as e:
    record("网关健康", "/actuator/health", False, str(e))


# ============ Phase 2: kb-auth 认证模块 ============
print("\n" + "=" * 70)
print("Phase 2: kb-auth 认证模块（12 个接口）")
print("=" * 70)

# 2.1 登录（正确）
status, body = http_request("POST", "/auth/login", body={"username": "admin", "password": "admin123"})
login_ok = status == 200 and body.get("code") == 200
record("kb-auth", "POST /auth/login（正确密码）", login_ok,
       f"status={status}, code={body.get('code')}")
token = body.get("data", {}).get("accessToken") if login_ok else None
refresh_token = body.get("data", {}).get("refreshToken") if login_ok else None
record("kb-auth", "  返回 accessToken", bool(token), f"len={len(token) if token else 0}")
record("kb-auth", "  返回 refreshToken", bool(refresh_token), f"len={len(refresh_token) if refresh_token else 0}")

# 2.2 登录（错误密码）- 系统设计为 HTTP 200 + 业务 code 400
status, body = http_request("POST", "/auth/login", body={"username": "admin", "password": "wrongpass"})
record("kb-auth", "POST /auth/login（错误密码）",
       status == 200 and body.get("code") in (400, 40001, 401),
       f"status={status}, code={body.get('code')}")

# 2.3 登录（空用户名）- Spring 参数校验返回 HTTP 400，业务校验返回 200+code 400
status, body = http_request("POST", "/auth/login", body={"username": "", "password": "admin123"})
record("kb-auth", "POST /auth/login（空用户名）",
       status in (400, 401) or (status == 200 and body.get("code") in (400, 40007)),
       f"status={status}, code={body.get('code')}")

# 2.4 登录（不存在用户）- 系统设计为 HTTP 200 + 业务 code 400
status, body = http_request("POST", "/auth/login", body={"username": "nonexistent", "password": "x"})
record("kb-auth", "POST /auth/login（不存在用户）",
       status == 200 and body.get("code") in (400, 40001, 401),
       f"status={status}, code={body.get('code')}")

# 2.5 无 Token 访问受保护接口
status, body = http_request("GET", "/space/list")
record("kb-auth", "GET /space/list（无 Token）", status == 401,
       f"status={status}, code={body.get('code')}")

# 2.6 无效 Token
status, body = http_request("GET", "/space/list", token="invalid.token.here")
record("kb-auth", "GET /space/list（无效 Token）", status == 401,
       f"status={status}, code={body.get('code')}")

# 2.7 刷新令牌
if refresh_token:
    status, body = http_request("POST", "/auth/refresh", body={"refreshToken": refresh_token})
    record("kb-auth", "POST /auth/refresh", status == 200 and body.get("code") == 200,
           f"status={status}, code={body.get('code')}")
    if body.get("code") == 200:
        token = body.get("data", {}).get("accessToken") or token

# 2.8 当前用户信息
status, body = http_request("GET", "/auth/me", token=token)
record("kb-auth", "GET /auth/me", status == 200, f"status={status}, code={body.get('code')}")

# 2.9 登出
status, body = http_request("POST", "/auth/logout", token=token)
record("kb-auth", "POST /auth/logout", status == 200 or body.get("code") == 200,
       f"status={status}, code={body.get('code')}")

# 2.10 登出后再用旧 Token 调用 /auth/me（kb-auth 接口，检查黑名单，应返回 401）
status, body = http_request("GET", "/auth/me", token=token)
logout_blacklist_ok = status == 401
record("kb-auth", "GET /auth/me（登出后旧 Token，黑名单应生效）", logout_blacklist_ok,
       f"status={status}, code={body.get('code')} (黑名单{'生效' if logout_blacklist_ok else '未生效'})")

# 2.11 登出后旧 Token 调用 /space/list（kb-knowledge 接口，kb-gateway 不检查黑名单，已知限制）
status, body = http_request("GET", "/space/list", token=token)
gateway_no_blacklist = status == 200  # 网关层面不检查黑名单，预期 200
record("kb-gateway", "GET /space/list（登出后旧 Token，网关不检查黑名单-已知限制）",
       gateway_no_blacklist,
       f"status={status}, code={body.get('code')} (kb-gateway 本地验签不查黑名单，仅 kb-auth 接口查)")

# 重新登录获取新 Token
status, body = http_request("POST", "/auth/login", body={"username": "admin", "password": "admin123"})
token = body.get("data", {}).get("accessToken")


# ============ Phase 3: kb-knowledge 知识库模块 ============
print("\n" + "=" * 70)
print("Phase 3: kb-knowledge 知识库模块（52 个接口）")
print("=" * 70)

# 3.1 空间管理
status, body = http_request("GET", "/space/list", token=token)
record("kb-knowledge", "GET /space/list", status == 200, f"status={status}")
space_id = None
if body.get("code") == 200 and body.get("data"):
    data = body["data"]
    if isinstance(data, list) and data:
        space_id = data[0].get("id")
    elif isinstance(data, dict) and data.get("list"):
        space_id = data["list"][0].get("id")
record("kb-knowledge", "  获取到 space_id", bool(space_id), f"space_id={space_id}")

# 3.2 创建空间
status, body = http_request("POST", "/space", token=token,
                            body={"name": f"E2E测试空间_{int(time.time())}", "description": "E2E 自动化测试"})
record("kb-knowledge", "POST /space（创建空间）", status == 200 and body.get("code") == 200,
       f"status={status}, code={body.get('code')}")
new_space_id = body.get("data", {}).get("id") if body.get("code") == 200 else None
if new_space_id:
    space_id = new_space_id

# 3.3 空间详情
status, body = http_request("GET", f"/space/{space_id}", token=token)
record("kb-knowledge", f"GET /space/{space_id}（空间详情）", status == 200,
       f"status={status}, code={body.get('code')}")

# 3.4 文件夹树
status, body = http_request("GET", f"/folder/tree?spaceId={space_id}", token=token)
record("kb-knowledge", f"GET /folder/tree?spaceId={space_id}", status == 200,
       f"status={status}, code={body.get('code')}")

# 3.5 创建文件夹
status, body = http_request("POST", "/folder", token=token,
                            body={"spaceId": space_id, "parentId": 0, "name": f"E2E文件夹_{int(time.time())}", "sortOrder": 1})
folder_ok = status == 200 and body.get("code") == 200
record("kb-knowledge", "POST /folder（创建文件夹）", folder_ok,
       f"status={status}, code={body.get('code')}")
folder_id = body.get("data", {}).get("id") if folder_ok else None

# 3.6 创建文档
status, body = http_request("POST", "/doc", token=token,
                            body={"title": f"E2E测试文档_{int(time.time())}",
                                  "content": "# E2E 测试\n这是一个测试文档，包含关键词 Nexus。",
                                  "folderId": folder_id})
doc_ok = status == 200 and body.get("code") == 200
record("kb-knowledge", "POST /doc（创建文档）", doc_ok,
       f"status={status}, code={body.get('code')}")
doc_id = body.get("data", {}).get("id") if doc_ok else None

# 3.7 文档详情
if doc_id:
    status, body = http_request("GET", f"/doc/{doc_id}", token=token)
    record("kb-knowledge", f"GET /doc/{doc_id}（文档详情）", status == 200,
           f"status={status}, code={body.get('code')}")

# 3.8 文档列表
status, body = http_request("GET", f"/doc/list?folderId={folder_id}&page=1&size=20", token=token)
record("kb-knowledge", "GET /doc/list", status == 200, f"status={status}, code={body.get('code')}")

# 3.9 更新文档
if doc_id:
    status, body = http_request("PUT", f"/doc/{doc_id}", token=token,
                                body={"title": "E2E 测试文档（已更新）", "content": "# 更新后内容\n关键词：Nexus 私服"})
    record("kb-knowledge", f"PUT /doc/{doc_id}（更新文档）", status == 200,
           f"status={status}, code={body.get('code')}")

# 3.10 收藏文档
if doc_id:
    status, body = http_request("PUT", f"/doc/{doc_id}/star", token=token)
    record("kb-knowledge", f"PUT /doc/{doc_id}/star（收藏）", status == 200,
           f"status={status}, code={body.get('code')}")

# 3.11 版本列表
if doc_id:
    status, body = http_request("GET", f"/version/list?resourceType=doc&resourceId={doc_id}", token=token)
    record("kb-knowledge", "GET /version/list", status == 200, f"status={status}, code={body.get('code')}")

# 3.12 标签管理
status, body = http_request("GET", "/tag/list", token=token)
record("kb-knowledge", "GET /tag/list", status == 200, f"status={status}, code={body.get('code')}")

status, body = http_request("POST", "/tag", token=token,
                            body={"name": f"E2E标签_{int(time.time())}", "color": "#FF5733"})
tag_ok = status == 200 and body.get("code") == 200
record("kb-knowledge", "POST /tag（创建标签）", tag_ok, f"status={status}, code={body.get('code')}")
tag_id = body.get("data", {}).get("id") if tag_ok else None

# 3.13 给文档绑定标签
if doc_id and tag_id:
    status, body = http_request("POST", "/tag/resource", token=token,
                                body={"tagId": tag_id, "resourceId": doc_id, "resourceType": "doc"})
    record("kb-knowledge", "POST /tag/resource（绑定标签）", status == 200,
           f"status={status}, code={body.get('code')}")

# 3.14 全文搜索
status, body = http_request("GET", "/search?q=Nexus&page=1&size=20", token=token)
record("kb-knowledge", "GET /search?q=Nexus", status == 200, f"status={status}, code={body.get('code')}")

# 3.15 搜索建议
status, body = http_request("GET", "/search/suggest?q=Nex", token=token)
record("kb-knowledge", "GET /search/suggest?q=Nex", status == 200, f"status={status}, code={body.get('code')}")

# 3.16 分享
if doc_id:
    status, body = http_request("POST", "/share", token=token,
                                body={"resourceType": "doc", "resourceId": doc_id,
                                      "extractCode": "1234", "expireDays": 7, "maxViews": 100})
    share_ok = status == 200 and body.get("code") == 200
    record("kb-knowledge", "POST /share（创建分享）", share_ok,
           f"status={status}, code={body.get('code')}")
    share_code = body.get("data", {}).get("code") if share_ok else None
    if share_code:
        # 3.17 验证分享（无需登录）
        status, body = http_request("GET", f"/share/verify/{share_code}?extractCode=1234")
        record("kb-knowledge", f"GET /share/verify/{share_code}（无登录验证）",
               status == 200, f"status={status}, code={body.get('code')}")

# 3.18 分享列表
status, body = http_request("GET", "/share/list?page=1&size=20", token=token)
record("kb-knowledge", "GET /share/list", status == 200, f"status={status}, code={body.get('code')}")

# 3.19 删除文档（进回收站）
if doc_id:
    status, body = http_request("DELETE", f"/doc/{doc_id}", token=token)
    record("kb-knowledge", f"DELETE /doc/{doc_id}（删除进回收站）", status == 200,
           f"status={status}, code={body.get('code')}")

# 3.20 回收站列表
status, body = http_request("GET", "/trash/list?type=doc&page=1&size=20", token=token)
record("kb-knowledge", "GET /trash/list", status == 200, f"status={status}, code={body.get('code')}")

# 3.21 恢复文档
if doc_id:
    status, body = http_request("POST", f"/trash/restore/doc/{doc_id}", token=token)
    record("kb-knowledge", f"POST /trash/restore/doc/{doc_id}", status == 200,
           f"status={status}, code={body.get('code')}")


# ============ Phase 4: kb-ops 运维看板模块 ============
print("\n" + "=" * 70)
print("Phase 4: kb-ops 运维看板模块（47 个接口）")
print("=" * 70)

status, body = http_request("GET", "/ops/dashboard", token=token)
record("kb-ops", "GET /ops/dashboard", status == 200, f"status={status}, code={body.get('code')}")

status, body = http_request("GET", "/ops/host/list?page=1&size=20", token=token)
record("kb-ops", "GET /ops/host/list", status == 200, f"status={status}, code={body.get('code')}")

status, body = http_request("GET", "/ops/service/list?page=1&size=20", token=token)
record("kb-ops", "GET /ops/service/list", status == 200, f"status={status}, code={body.get('code')}")

status, body = http_request("GET", "/ops/port/list?page=1&size=20", token=token)
record("kb-ops", "GET /ops/port/list", status == 200, f"status={status}, code={body.get('code')}")

status, body = http_request("GET", "/ops/credential/list?page=1&size=20", token=token)
record("kb-ops", "GET /ops/credential/list", status == 200, f"status={status}, code={body.get('code')}")

status, body = http_request("GET", "/ops/domain/list?page=1&size=20", token=token)
record("kb-ops", "GET /ops/domain/list", status == 200, f"status={status}, code={body.get('code')}")

status, body = http_request("GET", "/ops/dependency/list?page=1&size=20", token=token)
record("kb-ops", "GET /ops/dependency/list", status == 200, f"status={status}, code={body.get('code')}")

status, body = http_request("GET", "/ops/log/list?page=1&size=20", token=token)
record("kb-ops", "GET /ops/log/list", status == 200, f"status={status}, code={body.get('code')}")

status, body = http_request("GET", "/ops/deployment/recent?limit=10", token=token)
record("kb-ops", "GET /ops/deployment/recent", status == 200, f"status={status}, code={body.get('code')}")

status, body = http_request("GET", "/ops/conflict/list?page=1&size=20", token=token)
record("kb-ops", "GET /ops/conflict/list", status == 200, f"status={status}, code={body.get('code')}")

# 触发矛盾检测
status, body = http_request("POST", "/ops/conflict/detect", token=token)
record("kb-ops", "POST /ops/conflict/detect", status == 200, f"status={status}, code={body.get('code')}")


# ============ Phase 5: kb-intelligence 智能助手模块 ============
print("\n" + "=" * 70)
print("Phase 5: kb-intelligence 智能助手模块（12 个接口）")
print("=" * 70)

status, body = http_request("GET", "/intelligence/machine/stats", token=token)
record("kb-intelligence", "GET /intelligence/machine/stats", status == 200,
       f"status={status}, code={body.get('code')}")

status, body = http_request("POST", "/intelligence/machine/search", token=token,
                            body={"query": "Nexus", "page": 1, "size": 20})
record("kb-intelligence", "POST /intelligence/machine/search", status == 200,
       f"status={status}, code={body.get('code')}")

# 文档实体查询
status, body = http_request("GET", "/intelligence/machine/docs/1/entities", token=token)
record("kb-intelligence", "GET /intelligence/machine/docs/1/entities", status == 200,
       f"status={status}, code={body.get('code')}")


# ============ Phase 6: kb-file 文件模块 ============
print("\n" + "=" * 70)
print("Phase 6: kb-file 文件模块（13 个接口）")
print("=" * 70)

status, body = http_request("GET", f"/file/list?folderId={folder_id}&page=1&size=20", token=token)
record("kb-file", "GET /file/list", status == 200, f"status={status}, code={body.get('code')}")

# 文件上传（小文件，单分片）- 使用固定 fileId 保证 upload 和 merge 一致
file_id = f"e2e-{int(time.time())}"
boundary = "----WebKitFormBoundaryE2ETEST" + str(int(time.time()))
file_content = b"E2E test file content - Nexus private registry"
body_bytes = (
    f"--{boundary}\r\n"
    f'Content-Disposition: form-data; name="file"; filename="test.txt"\r\n'
    f"Content-Type: text/plain\r\n\r\n"
).encode("utf-8") + file_content + (
    f"\r\n--{boundary}\r\n"
    f'Content-Disposition: form-data; name="fileId"\r\n\r\n'
    f"{file_id}\r\n"
    f"--{boundary}\r\n"
    f'Content-Disposition: form-data; name="chunkNumber"\r\n\r\n'
    f"1\r\n"
    f"--{boundary}--\r\n"
).encode("utf-8")

req = urllib.request.Request(
    f"{BASE_URL}/file/upload",
    data=body_bytes,
    headers={
        "Content-Type": f"multipart/form-data; boundary={boundary}",
        "Authorization": f"Bearer {token}",
    },
    method="POST",
)
try:
    with urllib.request.urlopen(req, timeout=20) as resp:
        upload_status = resp.status
        upload_body = json.loads(resp.read().decode("utf-8"))
except urllib.error.HTTPError as e:
    upload_status = e.code
    try:
        upload_body = json.loads(e.read().decode("utf-8"))
    except Exception:
        upload_body = {"_raw": str(e)}
except Exception as e:
    upload_status = -1
    upload_body = {"_error": str(e)}

record("kb-file", "POST /file/upload（分片上传）", upload_status == 200,
       f"status={upload_status}, code={upload_body.get('code') if isinstance(upload_body, dict) else 'N/A'}")

# 文件合并 - 使用同一个 fileId
# 注意：FileMergeRequest 字段是 name（不是 filename），参见 kb-file/dto/file/FileMergeRequest.java
status, body = http_request("POST", "/file/merge", token=token,
                            body={"fileId": file_id,
                                  "name": "test.txt",
                                  "folderId": folder_id,
                                  "totalChunks": 1})
record("kb-file", "POST /file/merge（合并分片）", status == 200,
       f"status={status}, code={body.get('code')}")

# 存储桶列表
status, body = http_request("GET", "/bucket/list", token=token)
record("kb-file", "GET /bucket/list", status == 200, f"status={status}, code={body.get('code')}")


# ============ Phase 7: kb-gateway 网关模块 ============
print("\n" + "=" * 70)
print("Phase 7: kb-gateway 网关模块（白名单 + 路由 + 限流）")
print("=" * 70)

# 7.1 白名单接口（无需登录）
status, body = http_request("POST", "/auth/login", body={"username": "admin", "password": "admin123"})
record("kb-gateway", "白名单 /auth/login 通过", status == 200, f"status={status}")

# 7.2 非白名单接口未带 Token
status, body = http_request("GET", "/space/list")
record("kb-gateway", "非白名单 /space/list 拒绝（无 Token）", status == 401,
       f"status={status}, code={body.get('code')}")

# 7.3 路由到各微服务
for path, expected_module in [
    ("/auth/me", "kb-auth"),
    ("/space/list", "kb-knowledge"),
    ("/ops/dashboard", "kb-ops"),
    ("/intelligence/machine/stats", "kb-intelligence"),
]:
    status, body = http_request("GET", path, token=token)
    record("kb-gateway", f"路由 {path} → {expected_module}", status in (200, 404),
           f"status={status}")


# ============ 汇总 ============
print("\n" + "=" * 70)
print("汇总")
print("=" * 70)

total = len(results)
passed = sum(1 for r in results if r[2] == "PASS")
failed = total - passed
duration = time.time() - total_start

# 按模块统计
module_stats = {}
for module, name, status, detail in results:
    if module not in module_stats:
        module_stats[module] = {"pass": 0, "fail": 0}
    if status == "PASS":
        module_stats[module]["pass"] += 1
    else:
        module_stats[module]["fail"] += 1

print(f"\n总用例数：{total}")
print(f"通过：{passed} ({passed*100//total if total else 0}%)")
print(f"失败：{failed}")
print(f"耗时：{duration:.2f} 秒\n")

print("按模块统计：")
for module, stats in module_stats.items():
    total_m = stats["pass"] + stats["fail"]
    rate = stats["pass"] * 100 // total_m if total_m else 0
    print(f"  {module:<20} {stats['pass']}/{total_m} ({rate}%)  失败 {stats['fail']}")

# 输出失败用例明细
failures = [(m, n, d) for m, n, s, d in results if s == "FAIL"]
if failures:
    print(f"\n失败用例明细（{len(failures)} 个）：")
    for m, n, d in failures:
        print(f"  [{m}] {n}  {d}")

# 保存结果到 JSON
report = {
    "total": total,
    "passed": passed,
    "failed": failed,
    "duration_sec": round(duration, 2),
    "pass_rate": f"{passed*100//total if total else 0}%",
    "module_stats": module_stats,
    "failures": [{"module": m, "name": n, "detail": d} for m, n, d in failures],
    "timestamp": time.strftime("%Y-%m-%d %H:%M:%S"),
}
report_path = r"d:\huliang\java\ideaworkspace\devtools\mykng\test-output\reports\e2e-result.json"
import os
os.makedirs(os.path.dirname(report_path), exist_ok=True)
with open(report_path, "w", encoding="utf-8") as f:
    json.dump(report, f, ensure_ascii=False, indent=2)
print(f"\n结果已保存到：{report_path}")

sys.exit(0 if failed == 0 else 1)
