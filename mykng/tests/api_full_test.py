import urllib.request
import urllib.error
import json
import ssl
import sys

BASE = "https://tools.marschat.online/kb/api"
CTX = ssl.create_default_context()
CTX.check_hostname = False
CTX.verify_mode = ssl.CERT_NONE

def req(method, path, data=None, token=None):
    url = BASE + path
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    body = json.dumps(data).encode() if data else None
    r = urllib.request.Request(url, data=body, headers=headers, method=method)
    try:
        with urllib.request.urlopen(r, context=CTX, timeout=10) as resp:
            return resp.status, json.loads(resp.read().decode())
    except urllib.error.HTTPError as e:
        try:
            err_body = json.loads(e.read().decode())
        except:
            err_body = str(e)
        return e.code, err_body
    except Exception as e:
        return 0, str(e)

def t(name, method, path, data=None, token=None, expected=(200,)):
    code, body = req(method, path, data, token)
    ok = code in expected
    if not ok:
        print(f"  FAIL [{code}] {method} {path} -> {body}")
    else:
        msg = body.get("msg","") if isinstance(body, dict) else ""
        if msg and "fail" in msg.lower() or "error" in str(msg).lower() or "异常" in str(msg):
            print(f"  WARN [{code}] {method} {path} -> msg={msg}")
        else:
            print(f"  OK   [{code}] {method} {path}")
    return code, body

# 1. Login
print("=== 1. Auth ===")
code, login = t("Login", "POST", "/auth/login", {"username":"admin","password":"admin123"})
token = None
if code == 200 and isinstance(login, dict):
    inner = login.get("data", {})
    token = inner.get("accessToken") if isinstance(inner, dict) else None
    print(f"  Token: {token[:30]}..." if token else "  NO TOKEN!")

if not token:
    print("FATAL: Cannot login, aborting")
    sys.exit(1)

# 2. User
print("\n=== 2. User ===")
t("Profile", "GET", "/user/profile", token=token)

# 3. Space
print("\n=== 3. Space ===")
code, spaces = t("SpaceList", "GET", "/space/list", token=token)
space_id = None
if code == 200 and isinstance(spaces, dict):
    slist = spaces.get("data", [])
    if isinstance(slist, list) and len(slist) > 0:
        space_id = slist[0].get("id")
        print(f"  First space id: {space_id}")
if space_id:
    t("SpaceDetail", "GET", f"/space/{space_id}", token=token)

# 4. Folder
print("\n=== 4. Folder ===")
if space_id:
    code, folders = t("FolderTree", "GET", f"/folder/tree?spaceId={space_id}", token=token)
    folder_id = None
    if code == 200 and isinstance(folders, dict):
        flist = folders.get("data", [])
        if isinstance(flist, list) and len(flist) > 0:
            folder_id = flist[0].get("id")
            print(f"  First folder id: {folder_id}")
    t("FolderList", "GET", f"/folder/list?spaceId={space_id}", token=token)
    t("FolderRoot", "GET", f"/folder/root?spaceId={space_id}", token=token)
    t("FolderBreadcrumb", "GET", f"/folder/breadcrumb/1", token=token)

# 5. Doc
print("\n=== 5. Doc ===")
code, docs = t("DocList", "GET", "/doc/list?page=1&size=10", token=token)
doc_id = None
if code == 200 and isinstance(docs, dict):
    ddata = docs.get("data", {})
    dlist = ddata.get("list", []) if isinstance(ddata, dict) else []
    if isinstance(dlist, list) and len(dlist) > 0:
        doc_id = dlist[0].get("id")
        print(f"  First doc id: {doc_id}")
if doc_id:
    t("DocDetail", "GET", f"/doc/{doc_id}", token=token)
    t("DocVersions", "GET", f"/doc/{doc_id}/versions", token=token)

# 6. Tag
print("\n=== 6. Tag ===")
code, tags = t("TagList", "GET", "/tag/list", token=token)
tag_id = None
if code == 200 and isinstance(tags, dict):
    tlist = tags.get("data", [])
    if isinstance(tlist, list) and len(tlist) > 0:
        tag_id = tlist[0].get("id")
        print(f"  First tag id: {tag_id}")

# 7. Share
print("\n=== 7. Share ===")
t("MyShares", "GET", "/share/my", token=token)
t("ShareList", "GET", "/share/list?page=1&size=10", token=token)

# 8. File
print("\n=== 8. File ===")
t("FileList", "GET", "/file/list?page=1&size=10", token=token)

# 9. Trash
print("\n=== 9. Trash ===")
t("TrashList", "GET", "/trash/list?page=1&size=10", token=token)

# 10. Search
print("\n=== 10. Search ===")
t("Search", "GET", "/search?keyword=test&page=1&size=10", token=token)

# 11. Ops/Log
print("\n=== 11. Ops ===")
t("LogList", "GET", "/ops/log/list?page=1&size=10", token=token)

# 12. Bucket
print("\n=== 12. Bucket ===")
t("BucketList", "GET", "/bucket/list", token=token)

# 13. Token
print("\n=== 13. Token ===")
t("TokenList", "GET", "/token/list", token=token)

print("\n=== Done ===")
