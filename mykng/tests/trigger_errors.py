import urllib.request
import urllib.error
import json
import ssl

BASE = "https://tools.marschat.online/kb/api"
CTX = ssl.create_default_context()
CTX.check_hostname = False
CTX.verify_mode = ssl.CERT_NONE

def req(method, path, data=None, token=None, raw=False):
    url = BASE + path
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    body = json.dumps(data).encode() if data else None
    r = urllib.request.Request(url, data=body, headers=headers, method=method)
    try:
        with urllib.request.urlopen(r, context=CTX, timeout=10) as resp:
            full = resp.read().decode()
            return resp.status, full if raw else full[:500]
    except urllib.error.HTTPError as e:
        full = e.read().decode()
        return e.code, full if raw else full[:500]
    except Exception as e:
        return 0, str(e)[:200]

code, body = req("POST", "/auth/login", {"username":"admin","password":"admin123"}, raw=True)
token = json.loads(body)["data"]["accessToken"]
print("Login OK, token obtained")

tests = [
    ("GET", "/space/32"),
    ("GET", "/folder/list?spaceId=32"),
    ("GET", "/folder/root?spaceId=32"),
    ("GET", "/folder/breadcrumb/34"),
    ("GET", "/token/list"),
    ("GET", "/doc/7/versions"),
]
for m, p in tests:
    c, b = req(m, p, token=token)
    print(f"  [{c}] {m} {p} -> {b[:200]}")
