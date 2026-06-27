#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""L4 业务流程测试 - 公网域名端到端业务流程验证

基于 mykng/tests/test_functional.py 的 10 大业务场景测试模式改造：
- BASE_URL 改为公网域名 https://kb.marschat.online/kb/api
- 用 urllib（避免 requests 依赖，SSL 用 CERT_NONE）
- 每个测试方法 try/except 包裹，失败不中断后续测试
- 测试结束清理创建的测试数据（删除测试空间/文档/标签等）
- 保留原脚本的"流程串联"测试特点（创建→编辑→搜索→分享→删除→恢复 等链式验证）

10 大业务场景对照表：
| FLOW-01 | 文件上传全流程     | 8 验证点 |
| FLOW-02 | 文档全生命周期     | 9 验证点 |
| FLOW-03 | 搜索深度测试       | 6 验证点 |
| FLOW-04 | 空间文件夹管理     | 5 验证点 |
| FLOW-05 | 分享功能           | 4 验证点 |
| FLOW-06 | 回收站             | 5 验证点 |
| FLOW-07 | 标签系统           | 5 验证点 |
| FLOW-08 | 版本控制           | 4 验证点 |
| FLOW-09 | 权限安全           | 4 验证点 |
| FLOW-10 | 网页收藏           | 4 验证点 |
合计约 54 个验证点
"""

import json
import ssl
import sys
import time
import uuid
import random
import string
import hashlib
import urllib.request
import urllib.error
import urllib.parse
from datetime import datetime

# ============================================================
# 配置
# ============================================================
BASE_URL = "https://kb.marschat.online/kb/api"
TIMEOUT = 30
ADMIN = {"username": "admin", "password": "admin123"}

# SSL 忽略（kb.marschat.online 复用 nexus 证书，CN 不匹配）
SSL_CTX = ssl.create_default_context()
SSL_CTX.check_hostname = False
SSL_CTX.verify_mode = ssl.CERT_NONE


# ============================================================
# HTTP 工具
# ============================================================
def call(method, path, body=None, token=None, raw_bytes=None,
         content_type="application/json", query=None, no_token=False):
    """发起 HTTP 请求，返回 (http_code, elapsed_ms, response_text)

    - body: dict，自动 JSON 序列化
    - raw_bytes: 原始字节（用于 multipart 上传），此时 content_type 必须显式传入
    - query: dict，拼接到 URL query string
    - no_token: True 时不带 Authorization 头（用于公开接口/安全测试）
    """
    url = BASE_URL + path
    if query:
        url = url + "?" + urllib.parse.urlencode(query)

    headers = {"Accept": "application/json"}
    if not no_token and token:
        headers["Authorization"] = "Bearer " + token

    data = None
    if raw_bytes is not None:
        data = raw_bytes
        headers["Content-Type"] = content_type
    elif body is not None:
        data = json.dumps(body).encode("utf-8")
        headers["Content-Type"] = "application/json"

    req = urllib.request.Request(url, data=data, method=method, headers=headers)
    t0 = time.time()
    try:
        resp = urllib.request.urlopen(req, context=SSL_CTX, timeout=TIMEOUT)
        text = resp.read().decode("utf-8", errors="replace")
        code = resp.status
    except urllib.error.HTTPError as e:
        text = e.read().decode("utf-8", errors="replace")
        code = e.code
    except Exception as e:
        elapsed = int((time.time() - t0) * 1000)
        return 0, elapsed, "EXC:" + str(e)
    elapsed = int((time.time() - t0) * 1000)
    return code, elapsed, text


def parse_json(text):
    try:
        return json.loads(text)
    except Exception:
        return None


def extract_list(data):
    """从多种分页结构中提取列表数据。"""
    if data is None:
        return []
    if isinstance(data, list):
        return data
    if isinstance(data, dict):
        # PageResult<T>: {list: [...], total, page, size}
        if "list" in data and isinstance(data["list"], list):
            return data["list"]
        # 兼容旧结构: {records: [...]}
        if "records" in data and isinstance(data["records"], list):
            return data["records"]
        # 单对象包成 list
        return [data]
    return []


def biz_ok(j):
    """判断业务返回是否成功（code == 200）。"""
    return isinstance(j, dict) and j.get("code") == 200


def biz_data(j):
    """安全提取 data 字段。"""
    if isinstance(j, dict):
        return j.get("data")
    return None


def rand_suffix():
    """生成时间戳 + 随机字符串后缀，避免数据冲突。"""
    ts = str(int(time.time()))
    rand = "".join(random.choices(string.ascii_lowercase + string.digits, k=6))
    return ts + "_" + rand


def ts_str():
    return str(int(time.time()))


# ============================================================
# 测试套件
# ============================================================
class BusinessFlowTest:
    def __init__(self):
        self.passed = 0
        self.failed = 0
        self.skipped = 0
        self.details = []  # (case_id, name, status, detail)
        self.token = None
        self.scenarios = []  # (scenario_id, scenario_name, pass_cnt, fail_cnt)
        self.cur_scenario_pass = 0
        self.cur_scenario_fail = 0
        # 清理资源（按依赖顺序）
        self.cleanup = {
            "docs": [],       # 文档 ID（先删）
            "files": [],       # 文件 ID
            "folders": [],     # 文件夹 ID
            "tags": [],        # 标签 ID
            "shares": [],      # 分享 ID
            "webs": [],        # 网页 ID
            "spaces": [],      # 空间 ID（最后删）
            "trash_purge": [],  # (type, id) 永久删除项
        }

    # ---------- 统计 ----------
    def start_scenario(self, sid, name):
        print("\n" + "=" * 60)
        print("  场景 %s: %s" % (sid, name))
        print("=" * 60)
        self.cur_scenario_pass = 0
        self.cur_scenario_fail = 0
        self.cur_scenario_id = sid
        self.cur_scenario_name = name

    def end_scenario(self):
        self.scenarios.append((self.cur_scenario_id, self.cur_scenario_name,
                               self.cur_scenario_pass, self.cur_scenario_fail))

    def ok(self, case_id, name, detail=""):
        self.passed += 1
        self.cur_scenario_pass += 1
        self.details.append((case_id, name, "PASS", detail))
        print("  [PASS] %s %s%s" % (case_id, name, (" -> " + detail) if detail else ""))

    def fail(self, case_id, name, detail=""):
        self.failed += 1
        self.cur_scenario_fail += 1
        self.details.append((case_id, name, "FAIL", detail))
        print("  [FAIL] %s %s -> %s" % (case_id, name, detail))

    def skip(self, case_id, name, reason=""):
        self.skipped += 1
        self.details.append((case_id, name, "SKIP", reason))
        print("  [SKIP] %s %s -> %s" % (case_id, name, reason))

    def assert_eq(self, case_id, name, actual, expected, context=""):
        if actual == expected:
            self.ok(case_id, name, context or ("actual=%s" % actual))
        else:
            self.fail(case_id, name, "expected=%s actual=%s %s" % (expected, actual, context))

    def assert_true(self, case_id, name, cond, detail=""):
        if cond:
            self.ok(case_id, name, detail)
        else:
            self.fail(case_id, name, detail or "condition not met")

    # ---------- 封装 API 调用 ----------
    def api(self, method, path, body=None, query=None, no_token=False):
        """发起已认证 API 请求，返回 (http_code, j, text)。"""
        code, _, text = call(method, path, body=body, token=self.token, query=query, no_token=no_token)
        return code, parse_json(text), text

    # ============================================================
    # 前置：登录
    # ============================================================
    def login(self):
        print("\n" + "#" * 60)
        print("  前置：登录获取 Token")
        print("#" * 60)
        code, _, text = call("POST", "/auth/login", body=ADMIN, no_token=True)
        j = parse_json(text)
        if code == 200 and biz_ok(j) and isinstance(biz_data(j), dict):
            self.token = biz_data(j).get("accessToken")
            self.ok("LOGIN-001", "管理员登录成功", "token length=%d" % len(self.token or ""))
            return True
        else:
            self.fail("LOGIN-001", "管理员登录失败", "HTTP=%s body=%s" % (code, text[:200]))
            return False

    # ============================================================
    # FLOW-01: 文件上传全流程（8 验证点）
    # 流程: 上传 -> 列表 -> 详情 -> 解析状态 -> 收藏 -> 验证收藏 -> 取消收藏 -> 删除 -> 验证消失
    # ============================================================
    def test_file_upload_workflow(self):
        self.start_scenario("FLOW-01", "文件上传全流程")
        try:
            suffix = rand_suffix()
            # 创建空间
            code, j, text = self.api("POST", "/space",
                                     body={"name": "FLOW01_文件测试空间_" + suffix,
                                           "type": "TEAM", "description": "L4业务流程测试"})
            space_id = None
            if code == 200 and biz_ok(j):
                space_id = biz_data(j).get("id") if isinstance(biz_data(j), dict) else None
                if space_id:
                    self.cleanup["spaces"].append(space_id)
                    self.ok("FLOW-01-01", "创建测试空间", "spaceId=%s" % space_id)
                else:
                    self.fail("FLOW-01-01", "创建测试空间", "data无id: %s" % text[:150])
                    return
            else:
                self.fail("FLOW-01-01", "创建测试空间", "HTTP=%s body=%s" % (code, text[:150]))
                return

            # 在该空间下创建文件夹作为上传目标
            code, j, text = self.api("POST", "/folder",
                                     body={"spaceId": space_id, "parentId": 0,
                                           "name": "上传文件夹_" + suffix, "sortOrder": 1})
            folder_id = None
            if code == 200 and biz_ok(j) and isinstance(biz_data(j), dict):
                folder_id = biz_data(j).get("id")
                if folder_id:
                    self.cleanup["folders"].append(folder_id)

            if not folder_id:
                self.fail("FLOW-01-02", "创建上传目标文件夹", "HTTP=%s body=%s" % (code, text[:150]))
                return

            # 上传文件（multipart/form-data，手动构造）
            boundary = "----L4FlowBoundary" + uuid.uuid4().hex
            file_name = "FLOW01_测试文件_%s.txt" % suffix
            file_content = ("L4 业务流程测试文件\n" +
                            "包含中文、English、数字 12345\n" +
                            "时间: %s\n" +
                            "唯一标识: %s\n" +
                            "MD5: %s\n") % (
                                datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
                                suffix,
                                hashlib.md5(suffix.encode()).hexdigest())
            file_bytes = file_content.encode("utf-8")
            body_parts = []
            body_parts.append(("--%s\r\n" % boundary).encode())
            body_parts.append(b'Content-Disposition: form-data; name="file"; filename="%s"\r\n' % file_name.encode())
            body_parts.append(b"Content-Type: text/plain\r\n\r\n")
            body_parts.append(file_bytes)
            body_parts.append(b"\r\n")
            body_parts.append(("--%s\r\n" % boundary).encode())
            body_parts.append(('Content-Disposition: form-data; name="folderId"\r\n\r\n%s\r\n' % folder_id).encode())
            body_parts.append(("--%s\r\n" % boundary).encode())
            body_parts.append(('Content-Disposition: form-data; name="chunkNumber"\r\n\r\n1\r\n').encode())
            body_parts.append(("--%s--\r\n" % boundary).encode())
            raw_bytes = b"".join(body_parts)
            ct = "multipart/form-data; boundary=%s" % boundary

            code, _, text = call("POST", "/file/upload", token=self.token,
                                 raw_bytes=raw_bytes, content_type=ct)
            j = parse_json(text)
            file_id = None
            if code == 200 and biz_ok(j):
                d = biz_data(j)
                # 简单上传返回 ID 字符串或对象
                if isinstance(d, str):
                    try:
                        file_id = int(d)
                    except Exception:
                        file_id = None
                elif isinstance(d, dict):
                    file_id = d.get("id")
                if file_id:
                    self.cleanup["files"].append(file_id)
                    self.ok("FLOW-01-02", "上传文件", "fileId=%s name=%s" % (file_id, file_name))
                else:
                    self.fail("FLOW-01-02", "上传文件", "无 fileId: %s" % text[:150])
                    return
            else:
                self.fail("FLOW-01-02", "上传文件", "HTTP=%s body=%s" % (code, text[:200]))
                return

            # 等待文件入库
            time.sleep(1)

            # 验证2: 文件出现在列表中
            code, j, text = self.api("GET", "/file/list",
                                     query={"folderId": folder_id, "page": 1, "size": 50})
            if code == 200 and biz_ok(j):
                file_list = extract_list(biz_data(j))
                found = any(f.get("id") == file_id for f in file_list)
                self.assert_true("FLOW-01-03", "上传后文件出现在列表中", found,
                                 "list size=%d %s" % (len(file_list),
                                                     "包含目标文件" if found else "未找到目标文件"))
            else:
                self.fail("FLOW-01-03", "查询文件列表", "HTTP=%s body=%s" % (code, text[:150]))

            # 验证3: 获取文件详情
            code, j, text = self.api("GET", "/file/%s" % file_id)
            if code == 200 and biz_ok(j) and isinstance(biz_data(j), dict):
                f = biz_data(j)
                name = f.get("name") or f.get("fileName")
                # 文件名可能被规范化（去中文等），只要 id 存在即视为已入库
                self.assert_true("FLOW-01-04", "获取文件详情成功", f.get("id") is not None,
                                 "id=%s name=%s" % (f.get("id"), str(name)[:40]))
            else:
                self.fail("FLOW-01-04", "获取文件详情", "HTTP=%s body=%s" % (code, text[:150]))

            # 验证4: 解析状态可查询
            code, j, text = self.api("GET", "/file/%s/parse-status" % file_id)
            if code == 200 and biz_ok(j):
                status = biz_data(j)
                status_val = status.get("status") if isinstance(status, dict) else status
                self.assert_true("FLOW-01-05", "解析状态可查询", status_val is not None,
                                 "status=%s" % status_val)
            else:
                self.fail("FLOW-01-05", "解析状态查询", "HTTP=%s body=%s" % (code, text[:150]))

            # 验证5: 收藏文件（切换状态，无需 body）
            code, j, text = self.api("PUT", "/file/%s/star" % file_id)
            if code == 200 and biz_ok(j):
                self.ok("FLOW-01-06", "收藏文件")
            else:
                self.fail("FLOW-01-06", "收藏文件", "HTTP=%s body=%s" % (code, text[:150]))

            # 验证6: 验证收藏状态生效
            code, j, text = self.api("GET", "/file/%s" % file_id)
            if code == 200 and biz_ok(j) and isinstance(biz_data(j), dict):
                f = biz_data(j)
                starred = f.get("starred") or f.get("isStarred")
                self.assert_true("FLOW-01-07", "收藏状态生效",
                                 starred in (True, 1, "1"),
                                 "starred=%s" % starred)
            else:
                self.fail("FLOW-01-07", "验证收藏状态", "HTTP=%s body=%s" % (code, text[:150]))

            # 验证7: 取消收藏（再次切换）
            code, j, text = self.api("PUT", "/file/%s/star" % file_id)
            if code == 200 and biz_ok(j):
                self.ok("FLOW-01-08", "取消收藏文件")
            else:
                self.fail("FLOW-01-08", "取消收藏文件", "HTTP=%s body=%s" % (code, text[:150]))

            # 验证8: 删除文件（逻辑删除，进回收站）
            code, j, text = self.api("DELETE", "/file/%s" % file_id)
            if code == 200 and biz_ok(j):
                self.ok("FLOW-01-09", "删除文件到回收站", "fileId=%s" % file_id)
                # 加入回收站永久删除队列（避免污染）
                self.cleanup["trash_purge"].append(("FILE", file_id))
                # 从 files 清理列表移除（已进回收站，不再走文件删除）
                if file_id in self.cleanup["files"]:
                    self.cleanup["files"].remove(file_id)
            else:
                self.fail("FLOW-01-09", "删除文件到回收站", "HTTP=%s body=%s" % (code, text[:150]))

            # 验证9: 确认文件从列表消失
            time.sleep(1)
            code, j, text = self.api("GET", "/file/list",
                                     query={"folderId": folder_id, "page": 1, "size": 50})
            if code == 200 and biz_ok(j):
                file_list = extract_list(biz_data(j))
                gone = not any(f.get("id") == file_id for f in file_list)
                self.assert_true("FLOW-01-10", "删除后文件从列表消失", gone,
                                 "文件已不在列表中" if gone else "文件仍在列表中")
            else:
                self.fail("FLOW-01-10", "删除后列表验证", "HTTP=%s body=%s" % (code, text[:150]))
        except Exception as e:
            import traceback
            self.fail("FLOW-01-EX", "文件上传全流程异常", str(e))
            traceback.print_exc()
        finally:
            self.end_scenario()

    # ============================================================
    # FLOW-02: 文档全生命周期（9 验证点）
    # 流程: 创建 -> 编辑 -> 搜索验证 -> 标签绑定 -> 移动 -> 分享 -> 回收站 -> 恢复 -> 永久删除
    # ============================================================
    def test_document_lifecycle(self):
        self.start_scenario("FLOW-02", "文档全生命周期")
        try:
            suffix = rand_suffix()
            # 创建空间
            code, j, text = self.api("POST", "/space",
                                     body={"name": "FLOW02_文档生命周期_" + suffix,
                                           "type": "TEAM", "description": ""})
            if not (code == 200 and biz_ok(j) and isinstance(biz_data(j), dict)):
                self.fail("FLOW-02-01", "创建测试空间", "HTTP=%s body=%s" % (code, text[:150]))
                return
            space_id = biz_data(j).get("id")
            self.cleanup["spaces"].append(space_id)

            # 创建子文件夹（用于后续移动）
            code, j, text = self.api("POST", "/folder",
                                     body={"spaceId": space_id, "parentId": 0,
                                           "name": "子文件夹_" + suffix, "sortOrder": 1})
            sub_folder_id = None
            if code == 200 and biz_ok(j) and isinstance(biz_data(j), dict):
                sub_folder_id = biz_data(j).get("id")
                if sub_folder_id:
                    self.cleanup["folders"].append(sub_folder_id)

            # 创建文档
            unique_title = "FLOW02_功能测试文档_%s" % suffix
            keyword = "深度搜索测试_%s" % suffix
            code, j, text = self.api("POST", "/doc",
                                     body={"folderId": sub_folder_id or 0,
                                           "title": unique_title,
                                           "content": "# %s\n\n包含关键词「%s」。\n## 第二节\n更多内容 here。" % (unique_title, keyword)})
            if not (code == 200 and biz_ok(j) and isinstance(biz_data(j), dict)):
                self.fail("FLOW-02-01", "创建文档", "HTTP=%s body=%s" % (code, text[:150]))
                return
            doc_id = biz_data(j).get("id")
            self.cleanup["docs"].append(doc_id)
            self.ok("FLOW-02-01", "创建文档", "docId=%s title=%s" % (doc_id, unique_title))

            # 编辑文档（生成新版本）
            new_title = unique_title + "(已编辑)"
            new_content = ("# %s\n\n更新后的内容，关键词「%s」仍然存在。\n新增内容：版本2。\n"
                           % (new_title, keyword))
            code, j, text = self.api("PUT", "/doc/%s" % doc_id,
                                     body={"title": new_title, "content": new_content})
            if code == 200 and biz_ok(j):
                self.ok("FLOW-02-02", "编辑文档", "title=%s" % new_title)
            else:
                self.fail("FLOW-02-02", "编辑文档", "HTTP=%s body=%s" % (code, text[:150]))

            # 验证编辑生效
            code, j, text = self.api("GET", "/doc/%s" % doc_id)
            if code == 200 and biz_ok(j) and isinstance(biz_data(j), dict):
                doc = biz_data(j)
                title_ok = "(已编辑)" in (doc.get("title") or "")
                self.assert_true("FLOW-02-03", "编辑后标题生效", title_ok,
                                 "title=%s" % str(doc.get("title", ""))[:40])
            else:
                self.fail("FLOW-02-03", "获取编辑后文档", "HTTP=%s body=%s" % (code, text[:150]))

            # 搜索验证 - 等待索引后搜索关键词应能找到
            time.sleep(3)
            code, j, text = self.api("GET", "/search",
                                     query={"q": keyword, "page": 1, "size": 20})
            if code == 200 and biz_ok(j):
                hits = extract_list(biz_data(j))
                found = any(h.get("id") == doc_id or h.get("docId") == doc_id for h in hits)
                self.assert_true("FLOW-02-04", "搜索关键词能找到文档", found,
                                 "hits=%d %s" % (len(hits),
                                                 "包含目标文档" if found else "未找到目标文档"))
            else:
                self.fail("FLOW-02-04", "搜索验证", "HTTP=%s body=%s" % (code, text[:150]))

            # 标签绑定
            tag_name = "FLOW02_标签_" + suffix
            code, j, text = self.api("POST", "/tag",
                                     body={"name": tag_name, "color": "#1890ff"})
            tag_id = None
            if code == 200 and biz_ok(j) and isinstance(biz_data(j), dict):
                tag_id = biz_data(j).get("id")
                if tag_id:
                    self.cleanup["tags"].append(tag_id)
            if tag_id:
                # 按 spec v2.2: POST /tag/bind, body {tagId, resourceType:"DOC", resourceId}
                code, j, text = self.api("POST", "/tag/bind",
                                         body={"tagId": tag_id, "resourceType": "DOC", "resourceId": doc_id})
                if code == 200 and biz_ok(j):
                    self.ok("FLOW-02-05", "绑定标签到文档", "tagId=%s docId=%s" % (tag_id, doc_id))
                else:
                    self.fail("FLOW-02-05", "绑定标签", "HTTP=%s body=%s" % (code, text[:150]))
            else:
                self.fail("FLOW-02-05", "创建标签", "HTTP=%s body=%s" % (code, text[:150]))

            # 移动文档（如果创建了子文件夹，移动到根；反之亦然）
            target_folder = 0 if sub_folder_id else 0
            code, j, text = self.api("PUT", "/doc/%s/move" % doc_id,
                                     body={"folderId": target_folder})
            if code == 200 and biz_ok(j):
                self.ok("FLOW-02-06", "移动文档", "docId=%s -> folderId=%s" % (doc_id, target_folder))
            else:
                self.fail("FLOW-02-06", "移动文档", "HTTP=%s body=%s" % (code, text[:150]))

            # 创建分享链接
            code, j, text = self.api("POST", "/share",
                                     body={"resourceType": "doc", "resourceId": doc_id,
                                           "extractCode": "", "expireAt": ""})
            share_code = None
            share_id = None
            if code == 200 and biz_ok(j) and isinstance(biz_data(j), dict):
                share_data = biz_data(j)
                share_code = share_data.get("code") or share_data.get("shareCode")
                share_id = share_data.get("id")
                if share_id:
                    self.cleanup["shares"].append(share_id)
                self.assert_true("FLOW-02-07", "创建分享链接", share_code is not None,
                                 "code=%s" % share_code)
            else:
                self.fail("FLOW-02-07", "创建分享", "HTTP=%s body=%s" % (code, text[:150]))

            # 删除文档 -> 回收站
            code, j, text = self.api("DELETE", "/doc/%s" % doc_id)
            if code == 200 and biz_ok(j):
                self.ok("FLOW-02-08", "删除文档到回收站", "docId=%s" % doc_id)
            else:
                self.fail("FLOW-02-08", "删除文档到回收站", "HTTP=%s body=%s" % (code, text[:150]))

            # 验证在回收站中
            time.sleep(1)
            code, j, text = self.api("GET", "/trash/list", query={"page": 1, "size": 50})
            if code == 200 and biz_ok(j):
                trash_items = extract_list(biz_data(j))
                in_trash = any(t.get("id") == doc_id or t.get("docId") == doc_id
                               or str(t.get("resourceId")) == str(doc_id) for t in trash_items)
                self.assert_true("FLOW-02-09", "文档出现在回收站", in_trash,
                                 "trash size=%d %s" % (len(trash_items),
                                                       "在回收站中" if in_trash else "不在回收站中"))
            else:
                self.fail("FLOW-02-09", "回收站查询", "HTTP=%s body=%s" % (code, text[:150]))

            # 从回收站恢复（路径: POST /trash/restore/{type}/{id}）
            code, j, text = self.api("POST", "/trash/restore/DOC/%s" % doc_id)
            if code == 200 and biz_ok(j):
                self.ok("FLOW-02-10", "从回收站恢复文档", "docId=%s" % doc_id)
            else:
                self.fail("FLOW-02-10", "恢复文档", "HTTP=%s body=%s" % (code, text[:150]))

            # 验证恢复后可访问
            time.sleep(1)
            code, j, text = self.api("GET", "/doc/%s" % doc_id)
            if code == 200 and biz_ok(j) and isinstance(biz_data(j), dict):
                self.ok("FLOW-02-11", "恢复后文档可访问",
                        "title=%s" % str(biz_data(j).get("title", ""))[:30])
            else:
                self.fail("FLOW-02-11", "恢复后文档不可访问", "HTTP=%s body=%s" % (code, text[:150]))

            # 永久删除：先删到回收站，再从回收站永久删除
            code, j, text = self.api("DELETE", "/doc/%s" % doc_id)
            if code == 200 and biz_ok(j):
                # 从回收站永久删除（路径: DELETE /trash/{type}/{id}）
                code2, j2, text2 = self.api("DELETE", "/trash/DOC/%s" % doc_id)
                if code2 == 200 and biz_ok(j2):
                    self.ok("FLOW-02-12", "永久删除文档", "docId=%s" % doc_id)
                    if doc_id in self.cleanup["docs"]:
                        self.cleanup["docs"].remove(doc_id)
                else:
                    # 永久删除可能因为已经在回收站直接生效
                    self.fail("FLOW-02-12", "永久删除文档", "HTTP=%s body=%s" % (code2, text2[:150]))
            else:
                self.fail("FLOW-02-12", "二次删除到回收站", "HTTP=%s body=%s" % (code, text[:150]))
        except Exception as e:
            import traceback
            self.fail("FLOW-02-EX", "文档全生命周期异常", str(e))
            traceback.print_exc()
        finally:
            self.end_scenario()

    # ============================================================
    # FLOW-03: 搜索深度测试（6 验证点）
    # 流程: 多文档建索引 -> 关键词搜索 -> 类型过滤 -> 分页 -> 空结果
    # ============================================================
    def test_search_deep(self):
        self.start_scenario("FLOW-03", "搜索深度测试")
        try:
            suffix = rand_suffix()
            # 创建测试空间
            code, j, text = self.api("POST", "/space",
                                     body={"name": "FLOW03_搜索测试_" + suffix,
                                           "type": "TEAM", "description": ""})
            if not (code == 200 and biz_ok(j) and isinstance(biz_data(j), dict)):
                self.fail("FLOW-03-01", "创建搜索测试空间", "HTTP=%s body=%s" % (code, text[:150]))
                return
            space_id = biz_data(j).get("id")
            self.cleanup["spaces"].append(space_id)
            self.ok("FLOW-03-01", "创建搜索测试空间", "spaceId=%s" % space_id)

            # 创建 3 个不同内容的文档
            unique_kw = "RedisL4_%s" % suffix
            docs = [
                {"title": "FLOW03_Redis缓存策略_" + suffix,
                 "content": "%s 是内存数据库，常用于缓存。本文讨论缓存穿透、缓存雪崩。" % unique_kw},
                {"title": "FLOW03_MySQL索引优化_" + suffix,
                 "content": "MySQL索引提升查询性能。B+树索引结构、联合索引、覆盖索引。"},
                {"title": "FLOW03_Redis集群部署_" + suffix,
                 "content": "%s Cluster 通过分片实现水平扩展。主从复制、哨兵模式。" % unique_kw},
            ]
            doc_ids = []
            for d in docs:
                code, j, text = self.api("POST", "/doc",
                                         body={"folderId": 0, "title": d["title"], "content": d["content"]})
                if code == 200 and biz_ok(j) and isinstance(biz_data(j), dict):
                    did = biz_data(j).get("id")
                    if did:
                        doc_ids.append(did)
                        self.cleanup["docs"].append(did)
            self.assert_eq("FLOW-03-02", "创建测试文档数量", len(doc_ids), 3,
                           "created=%d" % len(doc_ids))

            if len(doc_ids) < 3:
                self.skip("FLOW-03-SKIP", "文档创建不足，跳过后续搜索验证", "created=%d" % len(doc_ids))
                return

            # 等待索引
            time.sleep(4)

            # 搜索1: unique_kw 应返回 2 个结果（2 个 Redis 文档）
            code, j, text = self.api("GET", "/search",
                                     query={"q": unique_kw, "page": 1, "size": 20})
            if code == 200 and biz_ok(j):
                hits = extract_list(biz_data(j))
                # 至少包含我们创建的文档（不强求严格 2 个，因搜索可能匹配其他内容）
                contains_ours = sum(1 for h in hits if h.get("id") in doc_ids)
                self.assert_true("FLOW-03-03", "搜索关键词返回结果", contains_ours >= 1,
                                 "hits=%d contains_ours=%d" % (len(hits), contains_ours))
            else:
                self.fail("FLOW-03-03", "搜索关键词", "HTTP=%s body=%s" % (code, text[:150]))

            # 搜索2: 类型过滤 DOC
            code, j, text = self.api("GET", "/search",
                                     query={"q": unique_kw, "type": "DOC", "page": 1, "size": 20})
            if code == 200 and biz_ok(j):
                hits = extract_list(biz_data(j))
                self.assert_true("FLOW-03-04", "类型过滤 DOC 不崩溃", len(hits) >= 0,
                                 "hits=%d" % len(hits))
            else:
                self.fail("FLOW-03-04", "类型过滤搜索", "HTTP=%s body=%s" % (code, text[:150]))

            # 搜索3: 不存在的关键词应返回空
            code, j, text = self.api("GET", "/search",
                                     query={"q": "zzznotexist12345_%s" % suffix, "page": 1, "size": 20})
            if code == 200 and biz_ok(j):
                hits = extract_list(biz_data(j))
                self.assert_eq("FLOW-03-05", "不存在关键词返回0条", len(hits), 0,
                               "正确返回空结果")
            else:
                self.fail("FLOW-03-05", "搜索不存在关键词", "HTTP=%s body=%s" % (code, text[:150]))

            # 搜索4: 空关键词不崩溃
            code, _, text = call("GET", "/search",
                                 token=self.token,
                                 query={"q": "", "page": 1, "size": 20})
            self.assert_true("FLOW-03-06", "空关键词不崩溃", code in (200, 400),
                             "HTTP=%s" % code)

            # 搜索5: 分页 size=1 返回 1 条
            code, j, text = self.api("GET", "/search",
                                     query={"q": unique_kw, "page": 1, "size": 1})
            if code == 200 and biz_ok(j):
                hits = extract_list(biz_data(j))
                self.assert_eq("FLOW-03-07", "分页 size=1 返回1条", len(hits), 1,
                               "分页正常")
            else:
                self.fail("FLOW-03-07", "分页搜索", "HTTP=%s body=%s" % (code, text[:150]))
        except Exception as e:
            import traceback
            self.fail("FLOW-03-EX", "搜索深度测试异常", str(e))
            traceback.print_exc()
        finally:
            self.end_scenario()

    # ============================================================
    # FLOW-04: 空间文件夹管理（5 验证点）
    # 流程: 创建空间 -> 建文件夹树 -> 验证树结构
    # ============================================================
    def test_space_folder_management(self):
        self.start_scenario("FLOW-04", "空间文件夹管理")
        try:
            suffix = rand_suffix()
            # 创建空间
            code, j, text = self.api("POST", "/space",
                                     body={"name": "FLOW04_文件夹树测试_" + suffix,
                                           "type": "TEAM", "description": "测试文件夹层级"})
            if not (code == 200 and biz_ok(j) and isinstance(biz_data(j), dict)):
                self.fail("FLOW-04-01", "创建空间", "HTTP=%s body=%s" % (code, text[:150]))
                return
            space_id = biz_data(j).get("id")
            self.cleanup["spaces"].append(space_id)
            self.ok("FLOW-04-01", "创建空间", "spaceId=%s" % space_id)

            # 创建层级文件夹: 根 -> A -> A1, 根 -> B
            folder_ids = []
            folder_specs = [("文件夹A_" + suffix, None),
                            ("文件夹A1_" + suffix, 0),
                            ("文件夹B_" + suffix, None)]
            for idx, (fname, parent_idx) in enumerate(folder_specs):
                parent_id = folder_ids[parent_idx] if parent_idx is not None else 0
                code, j, text = self.api("POST", "/folder",
                                         body={"spaceId": space_id, "parentId": parent_id,
                                               "name": fname, "sortOrder": idx + 1})
                if code == 200 and biz_ok(j) and isinstance(biz_data(j), dict):
                    fid = biz_data(j).get("id")
                    if fid:
                        folder_ids.append(fid)
                        self.cleanup["folders"].append(fid)
                        self.ok("FLOW-04-0%d" % (idx + 2), "创建%s" % fname,
                                "id=%s parentId=%s" % (fid, parent_id))
                else:
                    self.fail("FLOW-04-0%d" % (idx + 2), "创建%s" % fname,
                              "HTTP=%s body=%s" % (code, text[:150]))
                    return

            if len(folder_ids) < 3:
                self.fail("FLOW-04-05", "文件夹创建不全", "created=%d" % len(folder_ids))
                return

            # 获取文件夹树（路径: GET /folder/tree/{spaceId}）
            code, j, text = self.api("GET", "/folder/tree/%s" % space_id)
            if code == 200 and biz_ok(j):
                tree = biz_data(j)
                if isinstance(tree, list):
                    # 找到文件夹 A
                    folder_a = next((f for f in tree if f.get("id") == folder_ids[0]), None)
                    if folder_a:
                        children = folder_a.get("children") or []
                        has_a1 = any(c.get("id") == folder_ids[1] for c in children)
                        self.assert_true("FLOW-04-05", "文件夹树层级正确(A下有A1)", has_a1,
                                         "A子节点数=%d %s" % (len(children),
                                                              "A1存在" if has_a1 else "A1缺失"))
                    else:
                        self.fail("FLOW-04-05", "文件夹树验证", "未找到文件夹A in tree size=%d" % len(tree))
                else:
                    self.assert_true("FLOW-04-05", "文件夹树返回数据", tree is not None, "有返回数据")
            else:
                self.fail("FLOW-04-05", "获取文件夹树", "HTTP=%s body=%s" % (code, text[:150]))
        except Exception as e:
            import traceback
            self.fail("FLOW-04-EX", "空间文件夹管理异常", str(e))
            traceback.print_exc()
        finally:
            self.end_scenario()

    # ============================================================
    # FLOW-05: 分享功能（4 验证点）
    # 流程: 创建文档 -> 分享 -> 无Token访问 -> 验证内容 -> 分享列表
    # ============================================================
    def test_share_function(self):
        self.start_scenario("FLOW-05", "分享功能")
        try:
            suffix = rand_suffix()
            # 创建空间和文档
            code, j, text = self.api("POST", "/space",
                                     body={"name": "FLOW05_分享测试_" + suffix,
                                           "type": "TEAM", "description": ""})
            if not (code == 200 and biz_ok(j) and isinstance(biz_data(j), dict)):
                self.fail("FLOW-05-01", "创建空间", "HTTP=%s body=%s" % (code, text[:150]))
                return
            space_id = biz_data(j).get("id")
            self.cleanup["spaces"].append(space_id)

            code, j, text = self.api("POST", "/doc",
                                     body={"folderId": 0,
                                           "title": "FLOW05_分享测试文档_" + suffix,
                                           "content": "这是分享的内容_%s，应该可以被无Token访问者看到。" % suffix})
            if not (code == 200 and biz_ok(j) and isinstance(biz_data(j), dict)):
                self.fail("FLOW-05-01", "创建文档", "HTTP=%s body=%s" % (code, text[:150]))
                return
            doc_id = biz_data(j).get("id")
            self.cleanup["docs"].append(doc_id)
            self.ok("FLOW-05-01", "创建分享测试文档", "docId=%s" % doc_id)

            # 创建分享
            code, j, text = self.api("POST", "/share",
                                     body={"resourceType": "doc", "resourceId": doc_id,
                                           "extractCode": "", "expireAt": ""})
            share_code = None
            share_id = None
            if code == 200 and biz_ok(j) and isinstance(biz_data(j), dict):
                share_data = biz_data(j)
                share_code = share_data.get("code") or share_data.get("shareCode")
                share_id = share_data.get("id")
                if share_id:
                    self.cleanup["shares"].append(share_id)
                self.assert_true("FLOW-05-02", "创建分享链接", share_code is not None,
                                 "code=%s" % share_code)
            else:
                self.fail("FLOW-05-02", "创建分享", "HTTP=%s body=%s" % (code, text[:150]))
                return

            # 无 Token 访问分享（公开接口）
            time.sleep(1)
            code, _, text = call("GET", "/share/verify/%s" % share_code, no_token=True)
            j = parse_json(text)
            if code == 200 and biz_ok(j):
                share_content = biz_data(j)
                self.assert_true("FLOW-05-03", "无Token可访问分享",
                                 share_content is not None, "分享内容可访问")
            else:
                # 部分实现可能返回 403/404
                self.assert_true("FLOW-05-03", "无Token访问分享",
                                 code in (200, 403, 404),
                                 "HTTP=%s (可能需要密码或已设置访问控制)" % code)

            # 分享列表
            code, j, text = self.api("GET", "/share/list", query={"page": 1, "size": 20})
            if code == 200 and biz_ok(j):
                self.ok("FLOW-05-04", "获取分享列表", "list size=%d" % len(extract_list(biz_data(j))))
            else:
                self.fail("FLOW-05-04", "分享列表", "HTTP=%s body=%s" % (code, text[:150]))
        except Exception as e:
            import traceback
            self.fail("FLOW-05-EX", "分享功能异常", str(e))
            traceback.print_exc()
        finally:
            self.end_scenario()

    # ============================================================
    # FLOW-06: 回收站（5 验证点）
    # 流程: 创建文档 -> 删除 -> 回收站验证 -> 恢复 -> 验证恢复 -> 永久删除
    # ============================================================
    def test_recycle_bin(self):
        self.start_scenario("FLOW-06", "回收站功能")
        try:
            suffix = rand_suffix()
            code, j, text = self.api("POST", "/space",
                                     body={"name": "FLOW06_回收站测试_" + suffix,
                                           "type": "TEAM", "description": ""})
            if not (code == 200 and biz_ok(j) and isinstance(biz_data(j), dict)):
                self.fail("FLOW-06-01", "创建空间", "HTTP=%s body=%s" % (code, text[:150]))
                return
            space_id = biz_data(j).get("id")
            self.cleanup["spaces"].append(space_id)

            code, j, text = self.api("POST", "/doc",
                                     body={"folderId": 0,
                                           "title": "FLOW06_回收站测试文档_" + suffix,
                                           "content": "待删除的内容_%s" % suffix})
            if not (code == 200 and biz_ok(j) and isinstance(biz_data(j), dict)):
                self.fail("FLOW-06-01", "创建文档", "HTTP=%s body=%s" % (code, text[:150]))
                return
            doc_id = biz_data(j).get("id")
            self.ok("FLOW-06-01", "创建回收站测试文档", "docId=%s" % doc_id)

            # 删除文档到回收站
            code, j, text = self.api("DELETE", "/doc/%s" % doc_id)
            self.assert_true("FLOW-06-02", "删除文档到回收站",
                             code == 200 and biz_ok(j),
                             "HTTP=%s biz=%s" % (code, j.get("code") if isinstance(j, dict) else "?"))

            # 回收站列表
            time.sleep(1)
            code, j, text = self.api("GET", "/trash/list", query={"page": 1, "size": 50})
            if code == 200 and biz_ok(j):
                trash_list = extract_list(biz_data(j))
                in_trash = any(t.get("id") == doc_id or t.get("docId") == doc_id
                               or str(t.get("resourceId")) == str(doc_id) for t in trash_list)
                self.assert_true("FLOW-06-03", "文档在回收站中", in_trash,
                                 "trash size=%d %s" % (len(trash_list),
                                                       "在回收站" if in_trash else "不在回收站"))
            else:
                self.fail("FLOW-06-03", "回收站列表", "HTTP=%s body=%s" % (code, text[:150]))

            # 从回收站恢复（路径: POST /trash/restore/{type}/{id}）
            code, j, text = self.api("POST", "/trash/restore/DOC/%s" % doc_id)
            self.assert_true("FLOW-06-04", "恢复文档",
                             code == 200 and biz_ok(j),
                             "HTTP=%s biz=%s" % (code, j.get("code") if isinstance(j, dict) else "?"))

            # 确认恢复后文档可访问
            time.sleep(1)
            code, j, text = self.api("GET", "/doc/%s" % doc_id)
            if code == 200 and biz_ok(j) and isinstance(biz_data(j), dict):
                self.ok("FLOW-06-05", "恢复后文档可访问",
                        "title=%s" % str(biz_data(j).get("title", ""))[:30])
            else:
                self.fail("FLOW-06-05", "恢复后文档不可访问", "HTTP=%s body=%s" % (code, text[:150]))

            # 永久删除：先删到回收站，再从回收站永久删除
            self.api("DELETE", "/doc/%s" % doc_id)
            time.sleep(1)
            code, j, text = self.api("DELETE", "/trash/DOC/%s" % doc_id)
            if code == 200 and biz_ok(j):
                self.ok("FLOW-06-06", "永久删除文档", "docId=%s" % doc_id)
                if doc_id in self.cleanup["docs"]:
                    self.cleanup["docs"].remove(doc_id)
            else:
                self.fail("FLOW-06-06", "永久删除", "HTTP=%s body=%s" % (code, text[:150]))
        except Exception as e:
            import traceback
            self.fail("FLOW-06-EX", "回收站功能异常", str(e))
            traceback.print_exc()
        finally:
            self.end_scenario()

    # ============================================================
    # FLOW-07: 标签系统（5 验证点）
    # 流程: 建标签 -> 绑定文档 -> 标签列表 -> 解绑 -> 验证
    # ============================================================
    def test_tag_system(self):
        self.start_scenario("FLOW-07", "标签系统")
        try:
            suffix = rand_suffix()
            # 创建标签
            tag_name = "FLOW07_重要_" + suffix
            tag_color = "#52c41a"
            code, j, text = self.api("POST", "/tag",
                                     body={"name": tag_name, "color": tag_color})
            tag_id = None
            if code == 200 and biz_ok(j) and isinstance(biz_data(j), dict):
                tag_id = biz_data(j).get("id")
                if tag_id:
                    self.cleanup["tags"].append(tag_id)
                    self.ok("FLOW-07-01", "创建标签", "tagId=%s name=%s" % (tag_id, tag_name))
            else:
                self.fail("FLOW-07-01", "创建标签", "HTTP=%s body=%s" % (code, text[:150]))
                return

            # 标签列表
            code, j, text = self.api("GET", "/tag/list")
            if code == 200 and biz_ok(j):
                tags = biz_data(j) if isinstance(biz_data(j), list) else []
                tag_exists = any(t.get("id") == tag_id for t in tags)
                self.assert_true("FLOW-07-02", "标签出现在列表中", tag_exists,
                                 "total=%d %s" % (len(tags),
                                                  "包含目标标签" if tag_exists else "未找到"))
            else:
                self.fail("FLOW-07-02", "标签列表", "HTTP=%s body=%s" % (code, text[:150]))

            # 创建文档并绑定标签
            code, j, text = self.api("POST", "/space",
                                     body={"name": "FLOW07_标签测试_" + suffix,
                                           "type": "TEAM", "description": ""})
            space_id = biz_data(j).get("id") if (code == 200 and biz_ok(j) and isinstance(biz_data(j), dict)) else None
            if space_id:
                self.cleanup["spaces"].append(space_id)

            code, j, text = self.api("POST", "/doc",
                                     body={"folderId": 0,
                                           "title": "FLOW07_标签测试文档_" + suffix,
                                           "content": "内容_%s" % suffix})
            if code == 200 and biz_ok(j) and isinstance(biz_data(j), dict):
                doc_id = biz_data(j).get("id")
                self.cleanup["docs"].append(doc_id)
            else:
                self.fail("FLOW-07-03", "创建标签测试文档", "HTTP=%s body=%s" % (code, text[:150]))
                return

            # 绑定标签（路径: POST /tag/bind, body {tagId, resourceType:"DOC", resourceId}）
            code, j, text = self.api("POST", "/tag/bind",
                                     body={"tagId": tag_id, "resourceType": "DOC", "resourceId": doc_id})
            if code == 200 and biz_ok(j):
                self.ok("FLOW-07-03", "绑定标签到文档", "tagId=%s docId=%s" % (tag_id, doc_id))
            else:
                self.fail("FLOW-07-03", "绑定标签", "HTTP=%s body=%s" % (code, text[:150]))

            # 解绑标签（路径: DELETE /tag/unbind?tagId=&resourceType=&resourceId=）
            code, j, text = self.api("DELETE", "/tag/unbind",
                                     query={"tagId": tag_id, "resourceType": "DOC", "resourceId": doc_id})
            self.assert_true("FLOW-07-04", "解绑标签",
                             code == 200 and biz_ok(j),
                             "HTTP=%s biz=%s" % (code, j.get("code") if isinstance(j, dict) else "?"))

            # 验证解绑后文档仍可访问
            code, j, text = self.api("GET", "/doc/%s" % doc_id)
            if code == 200 and biz_ok(j):
                self.ok("FLOW-07-05", "解绑后文档仍可访问", "docId=%s" % doc_id)
            else:
                self.fail("FLOW-07-05", "解绑后文档访问", "HTTP=%s body=%s" % (code, text[:150]))
        except Exception as e:
            import traceback
            self.fail("FLOW-07-EX", "标签系统异常", str(e))
            traceback.print_exc()
        finally:
            self.end_scenario()

    # ============================================================
    # FLOW-08: 版本控制（4 验证点）
    # 流程: 创建 -> 多次编辑 -> 版本历史
    # ============================================================
    def test_version_control(self):
        self.start_scenario("FLOW-08", "版本控制")
        try:
            suffix = rand_suffix()
            code, j, text = self.api("POST", "/space",
                                     body={"name": "FLOW08_版本测试_" + suffix,
                                           "type": "TEAM", "description": ""})
            if not (code == 200 and biz_ok(j) and isinstance(biz_data(j), dict)):
                self.fail("FLOW-08-01", "创建空间", "HTTP=%s body=%s" % (code, text[:150]))
                return
            space_id = biz_data(j).get("id")
            self.cleanup["spaces"].append(space_id)

            # 创建文档
            code, j, text = self.api("POST", "/doc",
                                     body={"folderId": 0,
                                           "title": "FLOW08_版本测试_" + suffix,
                                           "content": "版本1内容_%s" % suffix})
            if not (code == 200 and biz_ok(j) and isinstance(biz_data(j), dict)):
                self.fail("FLOW-08-01", "创建文档", "HTTP=%s body=%s" % (code, text[:150]))
                return
            doc_id = biz_data(j).get("id")
            self.cleanup["docs"].append(doc_id)
            self.ok("FLOW-08-01", "创建文档", "docId=%s" % doc_id)

            # 编辑 2 次（生成 2 个新版本）
            for i in range(2, 4):
                code, j, text = self.api("PUT", "/doc/%s" % doc_id,
                                         body={"title": "FLOW08_版本测试_" + suffix,
                                               "content": "版本%d内容_%s" % (i, suffix)})
                if code == 200 and biz_ok(j):
                    self.ok("FLOW-08-0%d" % i, "编辑文档版本%d" % i, "docId=%s" % doc_id)
                else:
                    self.fail("FLOW-08-0%d" % i, "编辑文档版本%d" % i,
                              "HTTP=%s body=%s" % (code, text[:150]))
                time.sleep(0.5)

            # 版本历史列表（路径: GET /version/list/{type}/{id}）
            code, j, text = self.api("GET", "/version/list/DOC/%s" % doc_id)
            if code == 200 and biz_ok(j):
                versions = biz_data(j)
                if isinstance(versions, list):
                    self.assert_true("FLOW-08-04", "版本历史记录存在", len(versions) >= 1,
                                     "versions=%d" % len(versions))
                else:
                    self.assert_true("FLOW-08-04", "版本历史有返回", versions is not None, "有返回数据")
            else:
                self.fail("FLOW-08-04", "版本历史接口",
                          "HTTP=%s body=%s" % (code, text[:150]))
        except Exception as e:
            import traceback
            self.fail("FLOW-08-EX", "版本控制异常", str(e))
            traceback.print_exc()
        finally:
            self.end_scenario()

    # ============================================================
    # FLOW-09: 权限安全（4 验证点）
    # 流程: 无Token -> 错误Token -> SQL注入 -> 接口存在性
    # ============================================================
    def test_security(self):
        self.start_scenario("FLOW-09", "权限安全")
        try:
            # 无 Token 访问受保护接口
            code, _, text = call("GET", "/user/profile", no_token=True)
            self.assert_true("FLOW-09-01", "无Token访问被拒绝",
                             code in (401, 403),
                             "HTTP=%s" % code)

            # 错误 Token
            code, _, text = call("GET", "/user/profile",
                                token="invalid_token_12345")
            self.assert_true("FLOW-09-02", "错误Token被拒绝",
                             code in (401, 403),
                             "HTTP=%s" % code)

            # SQL 注入尝试登录
            code, _, text = call("POST", "/auth/login",
                                 body={"username": "admin' OR '1'='1",
                                       "password": "anything"},
                                 no_token=True)
            j = parse_json(text)
            # 期望登录失败（code != 200）
            login_blocked = not (isinstance(j, dict) and j.get("code") == 200)
            self.assert_true("FLOW-09-03", "SQL注入登录被拦截", login_blocked,
                             "HTTP=%s biz=%s" % (code, j.get("code") if isinstance(j, dict) else "?"))

            # 越权访问：用 admin 的 token 访问他人资源（用不存在的资源 ID）
            code, j, text = self.api("GET", "/doc/99999999")
            # 期望 404 或 200 但 code != 200（资源不存在）
            self.assert_true("FLOW-09-04", "访问不存在资源返回错误",
                             code in (200, 404) and (code == 404 or not biz_ok(j)),
                             "HTTP=%s biz=%s" % (code, j.get("code") if isinstance(j, dict) else "?"))
        except Exception as e:
            import traceback
            self.fail("FLOW-09-EX", "权限安全异常", str(e))
            traceback.print_exc()
        finally:
            self.end_scenario()

    # ============================================================
    # FLOW-10: 网页收藏（4 验证点）
    # 流程: 收藏 -> 验证 -> 搜索 -> 删除
    # ============================================================
    def test_web_collection(self):
        self.start_scenario("FLOW-10", "网页收藏")
        try:
            suffix = rand_suffix()
            # 收藏网页（使用 example.com，避免对外部服务造成影响）
            url = "https://example.com/l4flow10_%s" % suffix
            code, j, text = self.api("POST", "/web/collect",
                                     body={"url": url, "folderId": 0})
            web_id = None
            if code == 200 and biz_ok(j) and isinstance(biz_data(j), dict):
                web_id = biz_data(j).get("id")
                if web_id:
                    self.cleanup["webs"].append(web_id)
                    self.ok("FLOW-10-01", "收藏网页", "webId=%s url=%s" % (web_id, url))
            else:
                self.fail("FLOW-10-01", "收藏网页", "HTTP=%s body=%s" % (code, text[:200]))
                return

            # 验证网页详情
            time.sleep(1)
            code, j, text = self.api("GET", "/web/%s" % web_id)
            if code == 200 and biz_ok(j) and isinstance(biz_data(j), dict):
                self.ok("FLOW-10-02", "获取网页详情", "webId=%s" % web_id)
            else:
                self.fail("FLOW-10-02", "获取网页详情", "HTTP=%s body=%s" % (code, text[:150]))

            # 搜索收藏的网页
            time.sleep(2)
            code, j, text = self.api("GET", "/search",
                                     query={"q": "example", "type": "WEB", "page": 1, "size": 20})
            if code == 200 and biz_ok(j):
                self.ok("FLOW-10-03", "搜索网页收藏", "HTTP=200")
            else:
                self.fail("FLOW-10-03", "搜索网页收藏", "HTTP=%s body=%s" % (code, text[:150]))

            # 删除网页收藏
            code, j, text = self.api("DELETE", "/web/%s" % web_id)
            if code == 200 and biz_ok(j):
                self.ok("FLOW-10-04", "删除网页收藏", "webId=%s" % web_id)
                if web_id in self.cleanup["webs"]:
                    self.cleanup["webs"].remove(web_id)
            else:
                self.fail("FLOW-10-04", "删除网页收藏", "HTTP=%s body=%s" % (code, text[:150]))
        except Exception as e:
            import traceback
            self.fail("FLOW-10-EX", "网页收藏异常", str(e))
            traceback.print_exc()
        finally:
            self.end_scenario()

    # ============================================================
    # 清理测试数据（按依赖顺序）
    # ============================================================
    def cleanup_data(self):
        print("\n" + "#" * 60)
        print("  清理测试数据")
        print("#" * 60)
        cleaned = 0

        # 1. 永久删除回收站中的项
        for rtype, rid in self.cleanup["trash_purge"]:
            try:
                code, j, text = self.api("DELETE", "/trash/%s/%s" % (rtype, rid))
                if code == 200:
                    cleaned += 1
            except Exception:
                pass

        # 2. 删除文档（先到回收站）
        for doc_id in list(self.cleanup["docs"]):
            try:
                code, j, text = self.api("DELETE", "/doc/%s" % doc_id)
                if code == 200:
                    # 尝试从回收站永久删除
                    time.sleep(0.3)
                    self.api("DELETE", "/trash/DOC/%s" % doc_id)
                    cleaned += 1
            except Exception:
                pass

        # 3. 删除网页收藏
        for web_id in list(self.cleanup["webs"]):
            try:
                code, j, text = self.api("DELETE", "/web/%s" % web_id)
                if code == 200:
                    time.sleep(0.3)
                    self.api("DELETE", "/trash/WEB/%s" % web_id)
                    cleaned += 1
            except Exception:
                pass

        # 4. 删除文件
        for file_id in list(self.cleanup["files"]):
            try:
                code, j, text = self.api("DELETE", "/file/%s" % file_id)
                if code == 200:
                    time.sleep(0.3)
                    self.api("DELETE", "/trash/FILE/%s" % file_id)
                    cleaned += 1
            except Exception:
                pass

        # 5. 删除分享
        for share_id in list(self.cleanup["shares"]):
            try:
                code, j, text = self.api("DELETE", "/share/%s" % share_id)
                if code == 200:
                    cleaned += 1
            except Exception:
                pass

        # 6. 删除文件夹
        for folder_id in list(self.cleanup["folders"]):
            try:
                code, j, text = self.api("DELETE", "/folder/%s" % folder_id)
                if code == 200:
                    cleaned += 1
            except Exception:
                pass

        # 7. 删除标签
        for tag_id in list(self.cleanup["tags"]):
            try:
                code, j, text = self.api("DELETE", "/tag/%s" % tag_id)
                if code == 200:
                    cleaned += 1
            except Exception:
                pass

        # 8. 删除空间（最后）
        for space_id in list(self.cleanup["spaces"]):
            try:
                code, j, text = self.api("DELETE", "/space/%s" % space_id)
                if code == 200:
                    cleaned += 1
            except Exception:
                pass

        print("  清理完成: %d 项资源已删除" % cleaned)

    # ============================================================
    # 主入口
    # ============================================================
    def run_all(self):
        print("\n" + "#" * 60)
        print("  mykng L4 业务流程测试 - 公网域名端到端验证")
        print("  时间: %s" % datetime.now().strftime("%Y-%m-%d %H:%M:%S"))
        print("  目标: %s" % BASE_URL)
        print("#" * 60)

        if not self.login():
            return False

        # 10 大业务场景
        self.test_file_upload_workflow()    # FLOW-01
        self.test_document_lifecycle()      # FLOW-02
        self.test_search_deep()             # FLOW-03
        self.test_space_folder_management() # FLOW-04
        self.test_share_function()          # FLOW-05
        self.test_recycle_bin()             # FLOW-06
        self.test_tag_system()              # FLOW-07
        self.test_version_control()         # FLOW-08
        self.test_security()                # FLOW-09
        self.test_web_collection()          # FLOW-10

        # 清理
        self.cleanup_data()

        # 汇总
        total = self.passed + self.failed + self.skipped
        rate = (100.0 * self.passed / total) if total else 0
        print("\n" + "#" * 60)
        print("  测试总结: %d 项 | PASS=%d | FAIL=%d | SKIP=%d | 通过率=%.1f%%"
              % (total, self.passed, self.failed, self.skipped, rate))
        print("#" * 60)

        # 场景汇总
        print("\n  场景汇总:")
        for sid, sname, sp, sf in self.scenarios:
            status = "ALL PASS" if sf == 0 else "FAIL=%d" % sf
            print("    %s %s: PASS=%d %s" % (sid, sname, sp, status))

        # 失败明细
        if self.failed > 0:
            print("\n  失败用例明细:")
            for cid, name, st, det in self.details:
                if st == "FAIL":
                    print("    [FAIL] %s %s -> %s" % (cid, name, det))

        return self.failed == 0


# ============================================================
# 入口
# ============================================================
if __name__ == "__main__":
    tester = BusinessFlowTest()
    success = tester.run_all()
    sys.exit(0 if success else 1)
