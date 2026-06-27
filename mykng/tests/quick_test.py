import urllib.request, json

BASE = 'http://100.93.36.113:8090/kb/api'
data = json.dumps({'username':'admin','password':'admin123'}).encode()
req = urllib.request.Request(BASE+'/auth/login', data=data, headers={'Content-Type':'application/json'})
resp = urllib.request.urlopen(req, timeout=10)
login = json.loads(resp.read())
token = login['data']['accessToken']
print('Login OK')

def get(path):
    req = urllib.request.Request(BASE+path, headers={'Authorization': f'Bearer {token}'})
    try:
        r = urllib.request.urlopen(req, timeout=10)
        return r.status, json.loads(r.read())
    except urllib.error.HTTPError as e:
        try: return e.code, json.loads(e.read())
        except: return e.code, str(e)

tests = [
    ('/user/profile', 'User profile'),
    ('/space/list', 'Space list'),
    ('/space/32', 'Space detail (id=32)'),
    ('/folder/tree/32', 'Folder tree for space 32'),
    ('/doc/list?spaceId=32&page=1&size=5', 'Doc list for space 32'),
    ('/doc/7', 'Doc detail (id=7)'),
    ('/search?q=test&page=1&size=10', 'Search'),
    ('/search/suggest?q=test', 'Search suggest'),
    ('/tag/list', 'Tag list'),
    ('/share/my', 'My shares'),
    ('/trash/list?page=1&size=10', 'Trash list'),
    ('/token?page=1&size=10', 'Token list'),
    ('/version/list?resourceType=doc&resourceId=7', 'Version list for doc 7'),
    ('/web/list?page=1&size=5', 'Web page list'),
]

ok = 0
fail = 0
for path, name in tests:
    code, resp = get(path)
    is_ok = code == 200 and (isinstance(resp, dict) and resp.get('code') == 200)
    if is_ok:
        ok += 1
        d = resp.get('data')
        if isinstance(d, list):
            extra = f'[{len(d)} items]'
        elif isinstance(d, dict) and 'list' in d:
            extra = f'[page: {len(d["list"])} items]'
        elif isinstance(d, dict):
            label = d.get('name', d.get('title', ''))
            extra = f'[{label}]' if label else ''
        else:
            extra = ''
        print(f'  [OK] {name} {extra}')
    else:
        fail += 1
        msg = resp.get('message', str(resp))[:80] if isinstance(resp, dict) else str(resp)[:80]
        print(f'  [FAIL({code})] {name}: {msg}')

print(f'\n=== {ok} OK, {fail} FAILED ===')