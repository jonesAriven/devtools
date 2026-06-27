import urllib.request, json, ssl
ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE
BASE = "https://kb.marschat.online/kb/api"

def api(method, path, data=None, token=None):
    url = BASE + path
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    body = json.dumps(data).encode() if data else None
    req = urllib.request.Request(url, data=body, headers=headers, method=method)
    try:
        resp = urllib.request.urlopen(req, context=ctx, timeout=10)
        return json.loads(resp.read().decode())
    except urllib.error.HTTPError as e:
        err_body = e.read().decode()[:300]
        return {"code": e.code, "error": err_body}
    except Exception as e:
        return {"code": -1, "error": str(e)}

results = []

login_r = api("POST", "/auth/login", {"username": "admin", "password": "admin123"})
login_ok = login_r.get("code") == 200 and "user" in (login_r.get("data") or {})
results.append(("POST /auth/login", login_ok, "user in response" if login_ok else str(login_r)[:80]))
token = login_r.get("data", {}).get("accessToken", "") if login_r.get("code") == 200 else ""
rt = login_r.get("data", {}).get("refreshToken", "") if login_r.get("code") == 200 else ""

if token:
    spaces_r = api("GET", "/space/list", token=token)
    spaces = spaces_r.get("data", []) if spaces_r.get("code") == 200 else []
    spaceId = spaces[0]["id"] if spaces else 1

    tests = [
        ("GET /user/profile", "GET", "/user/profile", None),
        ("GET /space/list", "GET", "/space/list", None),
        (f"GET /folder/tree?spaceId={spaceId}", "GET", f"/folder/tree?spaceId={spaceId}", None),
        ("GET /doc/list?page=1&size=10", "GET", "/doc/list?page=1&size=10", None),
        ("GET /tag/list", "GET", "/tag/list", None),
        ("GET /share/my?page=1&size=10", "GET", "/share/my?page=1&size=10", None),
        ("GET /trash/list?page=1&size=10", "GET", "/trash/list?page=1&size=10", None),
        ("GET /file/list?page=1&size=10", "GET", "/file/list?page=1&size=10", None),
        ("GET /web/list?page=1&size=10", "GET", "/web/list?page=1&size=10", None),
        ("GET /ops/log/list?page=1&size=10", "GET", "/ops/log/list?page=1&size=10", None),
        ("GET /ops/dashboard", "GET", "/ops/dashboard", None),
        ("POST /auth/refresh", "POST", "/auth/refresh", {"refreshToken": rt}),
        ("POST /auth/logout", "POST", "/auth/logout", None),
    ]
    for name, method, path, data in tests:
        r = api(method, path, data, token)
        ok = r.get("code") == 200
        results.append((name, ok, f"code={r.get('code')}"))

    print(f"Using first spaceId={spaceId}")

print("=" * 65)
print(f"{'API':<35} {'STATUS':<8} {'DETAIL'}")
print("-" * 65)
passed = sum(1 for _, ok, _ in results if ok)
failed = len(results) - passed
for name, ok, detail in results:
    status = "PASS" if ok else "FAIL"
    print(f"{name:<35} {status:<8} {str(detail)[:60]}")
print("-" * 65)
print(f"Total: {len(results)}, Passed: {passed}, Failed: {failed}")
