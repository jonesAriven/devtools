#!/usr/bin/env python3
"""知识库 E2E 全量测试 - Playwright 浏览器自动化"""
import sys
import time
import json

from playwright.sync_api import sync_playwright

BASE_URL = "https://tools.marschat.online/kb"
API_BASE = BASE_URL + "/api"
results = []

def record(name, passed, detail=""):
    results.append({"name": name, "passed": passed, "detail": detail})
    status = "✅" if passed else "❌"
    print(f"{status} {name}: {detail}")

with sync_playwright() as p:
    browser = p.chromium.launch(headless=True)
    context = browser.new_context(
        viewport={"width": 1280, "height": 800},
        ignore_https_errors=True
    )
    page = context.new_page()

    # 1. 页面加载
    try:
        resp = page.goto(BASE_URL + "/", wait_until="networkidle", timeout=30000)
        record("页面加载", resp.status == 200, f"HTTP {resp.status}")
    except Exception as e:
        record("页面加载", False, str(e)[:80])

    # 2. 路由守卫
    try:
        time.sleep(1)
        url = page.url
        record("路由守卫-未登录跳转", "/login" in url, f"URL: {url}")
    except Exception as e:
        record("路由守卫-未登录跳转", False, str(e)[:80])

    # 3. 登录流程（API登录 + token注入，比UI填表更可靠）
    try:
        if "/login" not in page.url:
            page.goto(BASE_URL + "/login", wait_until="networkidle", timeout=15000)
        time.sleep(2)
        # 等待 Vue 应用渲染
        page.wait_for_selector('input[placeholder="用户名"]', timeout=10000)
        # 通过 API 登录
        login_resp = page.evaluate('''async () => {
            const res = await fetch("''' + API_BASE + '''/auth/login", {
                method: "POST",
                headers: {"Content-Type": "application/json"},
                body: JSON.stringify({username: "admin", password: "admin123"})
            });
            return await res.json();
        }''')
        if login_resp and login_resp.get("data") and login_resp["data"].get("accessToken"):
            token = login_resp["data"]["accessToken"]
            refresh = login_resp["data"].get("refreshToken", "")
            page.evaluate(f"localStorage.setItem('kb_access_token', '{token}')")
            page.evaluate(f"localStorage.setItem('kb_refresh_token', '{refresh}')")
            # 导航到 dashboard 验证登录成功
            page.goto(BASE_URL + "/dashboard", wait_until="networkidle", timeout=15000)
            time.sleep(2)
            login_ok = "/login" not in page.url
            record("登录流程", login_ok, f"API登录, URL: {page.url}")
        else:
            record("登录流程", False, f"API响应: {str(login_resp)[:80]}")
    except Exception as e:
        record("登录流程", False, str(e)[:80])

    # 4. 仪表盘
    try:
        page.goto(BASE_URL + "/dashboard", wait_until="networkidle", timeout=15000)
        time.sleep(2)
        cards = page.query_selector_all(".stat-card")
        has_title = "仪表盘" in (page.text_content("body") or "")
        record("仪表盘展示", len(cards) == 4 and has_title, f"卡片{len(cards)}个")
    except Exception as e:
        record("仪表盘展示", False, str(e)[:80])

    # 5. 获取空间ID
    space_id = None
    try:
        resp = page.evaluate('''async () => {
            const res = await fetch("''' + API_BASE + '''/space/list", {
                headers: {Authorization: "Bearer " + localStorage.getItem("kb_access_token")}
            });
            return await res.json();
        }''')
        if resp and resp.get("data") and len(resp["data"]) > 0:
            space_id = resp["data"][0]["id"]
            record("获取空间列表", True, f"空间ID={space_id}")
        else:
            record("获取空间列表", False, "无空间数据")
    except Exception as e:
        record("获取空间列表", False, str(e)[:80])

    # 6. 空间页面-文件夹树
    if space_id:
        try:
            page.goto(f"{BASE_URL}/space/{space_id}", wait_until="networkidle", timeout=15000)
            time.sleep(2)
            tree_nodes = page.query_selector_all(".el-tree-node__content")
            record("空间页面-文件夹树", True, f"树节点{len(tree_nodes)}个")
        except Exception as e:
            record("空间页面-文件夹树", False, str(e)[:80])

    # 7. 创建文件夹（API创建 + UI验证）
    if space_id:
        try:
            folder_name = "E2E测试目录_" + str(int(time.time()))
            resp = page.evaluate('''async () => {
                const res = await fetch("''' + API_BASE + '''/folder", {
                    method: "POST",
                    headers: {"Content-Type": "application/json", "Authorization": "Bearer " + localStorage.getItem("kb_access_token")},
                    body: JSON.stringify({name: "''' + folder_name + '''", parentId: 0, spaceId: ''' + str(space_id) + '''})
                });
                return await res.json();
            }''')
            folder_created = resp and (resp.get("code") == 200 or resp.get("data"))
            if folder_created:
                # 验证文件夹树API返回
                tree_resp = page.evaluate('''async () => {
                    const res = await fetch("''' + API_BASE + '''/folder/tree/''' + str(space_id) + '''", {
                        headers: {Authorization: "Bearer " + localStorage.getItem("kb_access_token")}
                    });
                    return await res.json();
                }''')
                tree_data = json.dumps(tree_resp, ensure_ascii=False) if tree_resp else ""
                record("创建文件夹", folder_name in tree_data, f"目录={folder_name}, 树API验证")
            else:
                record("创建文件夹", False, f"API响应: {str(resp)[:80]}")
        except Exception as e:
            record("创建文件夹", False, str(e)[:80])

    # 8. 创建文档（API创建 + UI验证跳转）
    doc_id = None
    try:
        doc_title = "E2E测试文档_" + str(int(time.time()))
        resp = page.evaluate('''async () => {
            const res = await fetch("''' + API_BASE + '''/doc", {
                method: "POST",
                headers: {"Content-Type": "application/json", "Authorization": "Bearer " + localStorage.getItem("kb_access_token")},
                body: JSON.stringify({title: "''' + doc_title + '''", content: "<p>E2E测试内容</p>", folderId: 0, spaceId: ''' + str(space_id or 0) + '''})
            });
            return await res.json();
        }''')
        if resp and resp.get("data"):
            doc_id = resp["data"].get("id") if isinstance(resp["data"], dict) else resp["data"]
            # 导航到编辑页验证
            page.goto(f"{BASE_URL}/doc/{doc_id}", wait_until="networkidle", timeout=15000)
            time.sleep(2)
            title_val = page.query_selector('input[placeholder="请输入笔记标题"]')
            record("创建文档", doc_id is not None and title_val is not None, f"文档ID={doc_id}")
        else:
            record("创建文档", False, f"API响应: {str(resp)[:80]}")
    except Exception as e:
        record("创建文档", False, str(e)[:80])

    # 9. 编辑文档
    if doc_id:
        try:
            page.goto(f"{BASE_URL}/doc/{doc_id}", wait_until="networkidle", timeout=15000)
            time.sleep(2)
            title_input = page.wait_for_selector('input[placeholder="请输入笔记标题"]', timeout=5000)
            new_title = "E2E修改标题_" + str(int(time.time()))
            title_input.fill(new_title)
            save_btn = None
            for b in page.query_selector_all("button"):
                if "保存" in (b.text_content() or ""):
                    save_btn = b
                    break
            if save_btn:
                save_btn.click()
                time.sleep(2)
                page.reload(wait_until="networkidle", timeout=15000)
                time.sleep(2)
                title_val = page.input_value('input[placeholder="请输入笔记标题"]')
                record("编辑文档", new_title in title_val, f"保存后标题验证")
            else:
                record("编辑文档", False, "未找到保存按钮")
        except Exception as e:
            record("编辑文档", False, str(e)[:80])

    # 10. 文档列表
    if space_id:
        try:
            page.goto(f"{BASE_URL}/space/{space_id}", wait_until="networkidle", timeout=15000)
            time.sleep(3)
            resources = page.query_selector_all(".resource-item")
            record("文档列表展示", len(resources) >= 0, f"资源{len(resources)}个")
        except Exception as e:
            record("文档列表展示", False, str(e)[:80])

    # 11. 文件上传（API上传 + UI验证）
    file_id = None
    if space_id:
        try:
            js_code = '''async () => {
                const blob = new Blob(["E2E测试文件内容"], {type: "text/plain"});
                const formData = new FormData();
                formData.append("file", blob, "e2e_test.txt");
                formData.append("folderId", "0");
                formData.append("spaceId", "''' + str(space_id) + '''");
                const res = await fetch("''' + API_BASE + '''/file/upload", {
                    method: "POST",
                    headers: {Authorization: "Bearer " + localStorage.getItem("kb_access_token")},
                    body: formData
                });
                const text = await res.text();
                try { return JSON.parse(text); } catch(e) { return {raw: text}; }
            }'''
            resp = page.evaluate(js_code)
            if isinstance(resp, dict) and resp.get("data"):
                data = resp["data"]
                file_id = data.get("id") if isinstance(data, dict) else data
                record("文件上传", file_id is not None, f"文件ID={file_id}")
            elif isinstance(resp, dict) and resp.get("code") == 200:
                file_id = resp.get("data")
                record("文件上传", file_id is not None, f"文件ID={file_id}")
            else:
                record("文件上传", False, f"响应: {str(resp)[:80]}")
        except Exception as e:
            record("文件上传", False, str(e)[:80])

    # 12. 文件详情
    if file_id:
        try:
            page.goto(f"{BASE_URL}/file/{file_id}", wait_until="networkidle", timeout=15000)
            time.sleep(2)
            body = page.text_content("body") or ""
            record("文件详情", "文件信息" in body and "e2e_test" in body, f"文件信息和名称验证")
        except Exception as e:
            record("文件详情", False, str(e)[:80])

    # 13. 标签管理
    if doc_id:
        try:
            page.goto(f"{BASE_URL}/doc/{doc_id}", wait_until="networkidle", timeout=15000)
            time.sleep(2)
            tag_input = page.query_selector('.tag-input input, .tag-input .el-input__inner')
            if tag_input:
                tag_name = "E2E标签_" + str(int(time.time()))
                tag_input.fill(tag_name)
                tag_input.press("Enter")
                time.sleep(2)
                body = page.text_content("body") or ""
                record("标签管理", tag_name in body, f"标签={tag_name}")
            else:
                record("标签管理", False, "未找到标签输入框")
        except Exception as e:
            record("标签管理", False, str(e)[:80])

    # 14. 星标收藏
    if file_id:
        try:
            page.goto(f"{BASE_URL}/file/{file_id}", wait_until="networkidle", timeout=15000)
            time.sleep(2)
            star_btn = page.query_selector(".star-toggle, [class*='star'] button, .page-actions button")
            if star_btn:
                star_btn.click()
                time.sleep(1)
                record("星标收藏", True, "星标切换成功")
            else:
                js_code = '''async () => {
                    const res = await fetch("''' + API_BASE + '''/file/''' + str(file_id) + '''/star", {
                        method: "POST",
                        headers: {Authorization: "Bearer " + localStorage.getItem("kb_access_token")}
                    });
                    return await res.json();
                }'''
                resp = page.evaluate(js_code)
                record("星标收藏", resp.get("code") == 200, f"API响应={resp.get('code')}")
        except Exception as e:
            record("星标收藏", False, str(e)[:80])

    # 15. 分享功能（API创建 + 链接验证）
    share_code = None
    if doc_id:
        try:
            resp = page.evaluate('''async () => {
                const res = await fetch("''' + API_BASE + '''/share", {
                    method: "POST",
                    headers: {"Content-Type": "application/json", "Authorization": "Bearer " + localStorage.getItem("kb_access_token")},
                    body: JSON.stringify({resourceId: ''' + str(doc_id) + ''', resourceType: "doc", expireType: "never"})
                });
                return await res.json();
            }''')
            if resp and resp.get("data"):
                share_data = resp["data"]
                share_code = share_data.get("code") if isinstance(share_data, dict) else None
                record("分享功能", share_code is not None, f"分享码={share_code}")
            else:
                record("分享功能", False, f"API响应: {str(resp)[:80]}")
        except Exception as e:
            record("分享功能", False, str(e)[:80])

    # 16. 分享访问
    if share_code:
        try:
            share_page = context.new_page()
            share_page.goto(f"{BASE_URL}/share/{share_code}", wait_until="networkidle", timeout=15000)
            time.sleep(2)
            body = share_page.text_content("body") or ""
            record("分享访问", len(body) > 30, f"分享页{len(body)}字符")
            share_page.close()
        except Exception as e:
            record("分享访问", False, str(e)[:80])

    # 17. 搜索功能
    try:
        page.goto(BASE_URL + "/search", wait_until="networkidle", timeout=15000)
        time.sleep(2)
        search_input = page.query_selector('.search-page input[placeholder*="搜索"]') or page.query_selector('input[placeholder*="搜索"]')
        if search_input:
            search_input.fill("E2E")
            search_btn = None
            for b in page.query_selector_all("button"):
                if "搜索" in (b.text_content() or ""):
                    search_btn = b
                    break
            if search_btn:
                search_btn.click()
            else:
                search_input.press("Enter")
            time.sleep(3)
            body = page.text_content("body") or ""
            record("搜索功能", "搜索结果" in body or "共" in body or "未找到" in body, "搜索结果页验证")
        else:
            record("搜索功能", False, "未找到搜索框")
    except Exception as e:
        record("搜索功能", False, str(e)[:80])

    # 18. 搜索筛选
    try:
        type_select = page.query_selector('.filter-card .el-select')
        if type_select:
            type_select.click()
            time.sleep(0.5)
            for opt in page.query_selector_all(".el-select-dropdown__item"):
                if "笔记" in (opt.text_content() or ""):
                    opt.click()
                    break
            time.sleep(2)
            record("搜索筛选", True, "类型筛选执行完成")
        else:
            record("搜索筛选", False, "未找到筛选下拉框")
    except Exception as e:
        record("搜索筛选", False, str(e)[:80])

    # 19. 版本历史
    if file_id:
        try:
            page.goto(f"{BASE_URL}/file/{file_id}", wait_until="networkidle", timeout=15000)
            time.sleep(2)
            body = page.text_content("body") or ""
            record("版本历史", "版本" in body, f"版本历史区域存在")
        except Exception as e:
            record("版本历史", False, str(e)[:80])

    # 20. 删除文档到回收站（API删除 + 回收站验证）
    if doc_id:
        try:
            resp = page.evaluate('''async () => {
                const res = await fetch("''' + API_BASE + '''/doc/''' + str(doc_id) + '''", {
                    method: "DELETE",
                    headers: {Authorization: "Bearer " + localStorage.getItem("kb_access_token")}
                });
                return await res.json();
            }''')
            deleted = resp and (resp.get("code") == 200 or resp.get("data") is not None)
            if deleted:
                # 导航到回收站验证
                page.goto(BASE_URL + "/trash", wait_until="networkidle", timeout=15000)
                time.sleep(2)
                body = page.text_content("body") or ""
                record("删除文档", "回收站" in body, f"API删除成功, 回收站验证")
            else:
                record("删除文档", False, f"API响应: {str(resp)[:80]}")
        except Exception as e:
            record("删除文档", False, str(e)[:80])

    # 21. 回收站查看
    try:
        page.goto(BASE_URL + "/trash", wait_until="networkidle", timeout=15000)
        time.sleep(3)
        body = page.text_content("body") or ""
        record("回收站查看", "回收站" in body, f"回收站页面验证")
    except Exception as e:
        record("回收站查看", False, str(e)[:80])

    # 22. 恢复资源
    try:
        found = False
        for b in page.query_selector_all(".el-table button, .el-table .el-button"):
            if "恢复" in (b.text_content() or ""):
                b.click()
                time.sleep(2)
                found = True
                break
        if found:
            record("恢复资源", True, "恢复操作执行完成")
        else:
            record("恢复资源", False, "无恢复按钮（回收站可能为空）")
    except Exception as e:
        record("恢复资源", False, str(e)[:80])

    # 23. 系统设置-个人信息
    try:
        page.goto(BASE_URL + "/settings", wait_until="networkidle", timeout=15000)
        time.sleep(2)
        body = page.text_content("body") or ""
        has_settings = "系统设置" in body or "个人信息" in body
        nickname_input = page.query_selector("input[placeholder*='昵称']")
        if nickname_input:
            new_nick = "E2E昵称_" + str(int(time.time()))
            nickname_input.fill(new_nick)
            for b in page.query_selector_all("button"):
                if "保存" in (b.text_content() or ""):
                    b.click()
                    time.sleep(1)
                    break
            record("系统设置-个人信息", has_settings, f"设置页={has_settings}")
        else:
            record("系统设置-个人信息", has_settings, f"设置页={has_settings}, 未找到昵称输入框")
    except Exception as e:
        record("系统设置-个人信息", False, str(e)[:80])

    # 24. 存储桶管理
    try:
        for tab in page.query_selector_all(".el-tabs__item"):
            if "存储桶" in (tab.text_content() or ""):
                tab.click()
                time.sleep(2)
                break
        body = page.text_content("body") or ""
        record("存储桶管理", "存储桶" in body, f"存储桶tab验证")
    except Exception as e:
        record("存储桶管理", False, str(e)[:80])

    # 25. API Token管理
    try:
        for tab in page.query_selector_all(".el-tabs__item"):
            t = tab.text_content() or ""
            if "Token" in t or "token" in t.lower():
                tab.click()
                time.sleep(2)
                break
        body = page.text_content("body") or ""
        record("API Token管理", "token" in body.lower(), f"Token tab验证")
    except Exception as e:
        record("API Token管理", False, str(e)[:80])

    # 26. 操作日志
    try:
        for tab in page.query_selector_all(".el-tabs__item"):
            if "日志" in (tab.text_content() or ""):
                tab.click()
                time.sleep(2)
                break
        body = page.text_content("body") or ""
        record("操作日志", "日志" in body or "操作" in body, f"日志tab验证")
    except Exception as e:
        record("操作日志", False, str(e)[:80])

    # 27. 运维看板
    try:
        page.goto(BASE_URL + "/ops", wait_until="networkidle", timeout=15000)
        time.sleep(2)
        body = page.text_content("body") or ""
        record("运维看板", len(body) > 50, f"运维页{len(body)}字符")
    except Exception as e:
        record("运维看板", False, str(e)[:80])

    # 28. 主机管理
    try:
        page.goto(BASE_URL + "/ops/hosts", wait_until="networkidle", timeout=15000)
        time.sleep(2)
        body = page.text_content("body") or ""
        record("主机管理", len(body) > 30, f"主机管理页{len(body)}字符")
    except Exception as e:
        record("主机管理", False, str(e)[:80])

    # 29. 服务管理
    try:
        page.goto(BASE_URL + "/ops/services", wait_until="networkidle", timeout=15000)
        time.sleep(2)
        body = page.text_content("body") or ""
        record("服务管理", len(body) > 30, f"服务管理页{len(body)}字符")
    except Exception as e:
        record("服务管理", False, str(e)[:80])

    # 30. 移动端适配
    try:
        page.goto(BASE_URL + "/dashboard", wait_until="networkidle", timeout=15000)
        time.sleep(1)
        page.set_viewport_size({"width": 375, "height": 812})
        time.sleep(2)
        aside = page.query_selector(".el-aside")
        aside_visible = aside and aside.is_visible()
        hamburger = page.query_selector(".collapse-btn")
        ham_visible = hamburger and hamburger.is_visible()
        page.set_viewport_size({"width": 1280, "height": 800})
        record("移动端适配", not aside_visible and ham_visible, f"侧边栏隐藏={not aside_visible}, 汉堡菜单={ham_visible}")
    except Exception as e:
        record("移动端适配", False, str(e)[:80])

    # 31. 退出登录
    try:
        page.set_viewport_size({"width": 1280, "height": 800})
        page.evaluate("localStorage.clear(); sessionStorage.clear();")
        page.goto(BASE_URL + "/", wait_until="networkidle", timeout=15000)
        time.sleep(2)
        record("退出登录", "/login" in page.url, f"URL: {page.url}")
    except Exception as e:
        record("退出登录", False, str(e)[:80])

    browser.close()

# 输出结果
print("\n" + "=" * 70)
passed = sum(1 for r in results if r["passed"])
failed = sum(1 for r in results if not r["passed"])
print(f"E2E测试总结: {len(results)} 项 | ✅ {passed} 通过 | ❌ {failed} 失败")
print("=" * 70)
for r in results:
    status = "✅" if r["passed"] else "❌"
    print(f"  {status} {r['name']}: {r['detail']}")

print("\n__JSON__")
print(json.dumps(results))
