import urllib.request, json

BASE = 'http://100.93.36.113:8090/kb/api'

# Login
data = json.dumps({'username':'admin','password':'admin123'}).encode()
req = urllib.request.Request(BASE+'/auth/login', data=data, headers={'Content-Type':'application/json'})
resp = urllib.request.urlopen(req, timeout=15)
login = json.loads(resp.read())
token = login['data']['accessToken']
print('Login OK')

def api(method, path, body=None):
    headers = {'Authorization': f'Bearer {token}'}
    if body:
        headers['Content-Type'] = 'application/json'
        data = json.dumps(body).encode()
    else:
        data = None
    req = urllib.request.Request(BASE+path, data=data, headers=headers, method=method)
    try:
        r = urllib.request.urlopen(req, timeout=15)
        return r.status, json.loads(r.read())
    except urllib.error.HTTPError as e:
        try: return e.code, json.loads(e.read())
        except: return e.code, str(e)[:200]

# Test GET /doc/{id} - verify content field is returned
print('\n=== GET /doc/list to find a doc ===')
code, resp = api('GET', '/doc/list?page=1&size=3')
if code == 200 and resp.get('code') == 200:
    docs = resp['data']['list']
    print(f'Found {len(docs)} docs')
    if docs:
        doc_id = docs[0]['id']
        print(f'\n=== GET /doc/{doc_id} - verify content field ===')
        code2, resp2 = api('GET', f'/doc/{doc_id}')
        print(f'  code={code2}')
        if code2 == 200 and resp2.get('code') == 200:
            d = resp2['data']
            print(f'  title={d.get("title","")}')
            print(f'  content={"YES (" + str(len(d.get("content",""))) + " chars)" if d.get("content") else "NO"}')
            print(f'  spaceId={d.get("spaceId","MISSING")}')
            print(f'  wordCount={d.get("wordCount","MISSING")}')
            print(f'  folderId={d.get("folderId","MISSING")}')
        else:
            print(f'  ERROR: {json.dumps(resp2, ensure_ascii=False)[:300]}')

        # Test PUT /doc/{id} - verify update works
        print(f'\n=== PUT /doc/{doc_id} - verify update ===')
        code3, resp3 = api('PUT', f'/doc/{doc_id}', {
            'title': docs[0].get('title','test'),
            'content': '<p>API test content</p>'
        })
        print(f'  code={code3}')
        if code3 == 200 and resp3.get('code') == 200:
            print(f'  Update OK')
        else:
            print(f'  ERROR: {json.dumps(resp3, ensure_ascii=False)[:300]}')

# Test folder tree with query param
print('\n=== GET /folder/tree?spaceId=32 ===')
code4, resp4 = api('GET', '/folder/tree?spaceId=32')
print(f'  code={code4}')
if code4 == 200 and resp4.get('code') == 200:
    print(f'  folders={len(resp4["data"])}')
else:
    print(f'  ERROR: {json.dumps(resp4, ensure_ascii=False)[:300]}')

# Test public access
print('\n=== Public API test ===')
import ssl
ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE
data = json.dumps({'username':'admin','password':'admin123'}).encode()
req = urllib.request.Request('https://kb.marschat.online/kb/api/auth/login', data=data, headers={'Content-Type':'application/json'})
r = urllib.request.urlopen(req, timeout=15, context=ctx)
pub_login = json.loads(r.read())
pub_token = pub_login['data']['accessToken']
print(f'  Public login OK')

# Get doc via public
req = urllib.request.Request(f'https://kb.marschat.online/kb/api/doc/{doc_id}', headers={'Authorization': f'Bearer {pub_token}'})
r = urllib.request.urlopen(req, timeout=15, context=ctx)
pub_doc = json.loads(r.read())
d = pub_doc['data']
print(f'  Public doc content={"YES" if d.get("content") else "NO"}')
print(f'  Public doc spaceId={d.get("spaceId","MISSING")}')

# Check public frontend version
print('\n=== Public frontend version ===')
req = urllib.request.Request('https://kb.marschat.online/kb/')
r = urllib.request.urlopen(req, timeout=15, context=ctx)
html = r.read().decode()
import re
js = re.findall(r'src="(/kb/s/assets/[^"]+)"', html)
print(f'  Public JS: {js}')