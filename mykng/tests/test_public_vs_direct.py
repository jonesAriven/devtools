import urllib.request, json, ssl

# Test both public and direct access
BASE_PUBLIC = 'https://kb.marschat.online/kb/api'
BASE_DIRECT = 'http://100.93.36.113:8090/kb/api'

ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE

def test_api(base, label, path, method='GET', body=None, token=None):
    headers = {}
    if token: headers['Authorization'] = f'Bearer {token}'
    if body:
        headers['Content-Type'] = 'application/json'
        data = json.dumps(body).encode()
    else:
        data = None
    url = base + path
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        if url.startswith('https'):
            r = urllib.request.urlopen(req, timeout=15, context=ctx)
        else:
            r = urllib.request.urlopen(req, timeout=15)
        return r.status, json.loads(r.read())
    except urllib.error.HTTPError as e:
        body_text = e.read().decode('utf-8', errors='replace')[:300]
        return e.code, body_text
    except Exception as e:
        return -1, str(e)[:300]

# Step 1: Login via public
print('=== 1. Login via PUBLIC (kb.marschat.online) ===')
code, resp = test_api(BASE_PUBLIC, 'public', '/auth/login', 'POST', {'username':'admin','password':'admin123'})
print(f'  Login: code={code}')
if code == 200 and isinstance(resp, dict) and resp.get('code') == 200:
    pub_token = resp['data']['accessToken']
    print(f'  Token: {pub_token[:30]}...')
else:
    print(f'  FAILED: {resp}')
    pub_token = None

# Step 2: Login via direct
print('\n=== 2. Login via DIRECT (100.93.36.113) ===')
code, resp = test_api(BASE_DIRECT, 'direct', '/auth/login', 'POST', {'username':'admin','password':'admin123'})
print(f'  Login: code={code}')
if code == 200 and isinstance(resp, dict) and resp.get('code') == 200:
    dir_token = resp['data']['accessToken']
    print(f'  Token: {dir_token[:30]}...')
else:
    print(f'  FAILED: {resp}')
    dir_token = None

# Step 3: Compare API responses - doc list
if pub_token and dir_token:
    print('\n=== 3. Compare doc/list (PUBLIC vs DIRECT) ===')
    code_p, resp_p = test_api(BASE_PUBLIC, 'public', '/doc/list?page=1&size=5', 'GET', token=pub_token)
    code_d, resp_d = test_api(BASE_DIRECT, 'direct', '/doc/list?page=1&size=5', 'GET', token=dir_token)
    print(f'  Public:  code={code_p}')
    print(f'  Direct:  code={code_d}')
    if code_p == 200 and isinstance(resp_p, dict):
        print(f'  Public resp code: {resp_p.get("code")}, msg: {resp_p.get("message","")}')
        if resp_p.get('code') != 200:
            print(f'  Public ERROR: {json.dumps(resp_p, ensure_ascii=False)[:300]}')
    else:
        print(f'  Public raw: {str(resp_p)[:300]}')

    # Step 4: Test doc detail (the edit page API)
    print('\n=== 4. Doc detail via PUBLIC (edit page API) ===')
    # Get first doc id from direct
    if code_d == 200 and isinstance(resp_d, dict) and resp_d.get('code') == 200:
        docs = resp_d['data'].get('list', [])
        if docs:
            doc_id = docs[0]['id']
            print(f'  Testing GET /doc/{doc_id} via public...')
            code_p2, resp_p2 = test_api(BASE_PUBLIC, 'public', f'/doc/{doc_id}', 'GET', token=pub_token)
            print(f'  Public:  code={code_p2}')
            if code_p2 == 200 and isinstance(resp_p2, dict):
                print(f'  resp code: {resp_p2.get("code")}, msg: {resp_p2.get("message","")}')
                if resp_p2.get('code') != 200:
                    print(f'  ERROR: {json.dumps(resp_p2, ensure_ascii=False)[:500]}')
            else:
                print(f'  RAW: {str(resp_p2)[:300]}')

            # Step 5: Test folder tree (edit page loads this too)
            print(f'\n=== 5. Folder tree via PUBLIC ===')
            space_id = docs[0].get('spaceId') or 32
            code_p3, resp_p3 = test_api(BASE_PUBLIC, 'public', f'/folder/tree?spaceId={space_id}', 'GET', token=pub_token)
            print(f'  GET /folder/tree?spaceId={space_id} via public: code={code_p3}')
            if code_p3 == 200 and isinstance(resp_p3, dict):
                print(f'  resp code: {resp_p3.get("code")}, msg: {resp_p3.get("message","")}')
            else:
                print(f'  RAW: {str(resp_p3)[:300]}')

            # Step 6: Test PUT /doc/{id} (save edit)
            print(f'\n=== 6. Update doc via PUBLIC ===')
            code_p4, resp_p4 = test_api(BASE_PUBLIC, 'public', f'/doc/{doc_id}', 'PUT', {
                'title': docs[0].get('title', 'test'),
                'content': '<p>test</p>',
                'folderId': docs[0].get('folderId'),
            }, token=pub_token)
            print(f'  PUT /doc/{doc_id} via public: code={code_p4}')
            if code_p4 == 200 and isinstance(resp_p4, dict):
                print(f'  resp code: {resp_p4.get("code")}, msg: {resp_p4.get("message","")}')
                if resp_p4.get('code') != 200:
                    print(f'  ERROR: {json.dumps(resp_p4, ensure_ascii=False)[:500]}')
            else:
                print(f'  RAW: {str(resp_p4)[:300]}')

# Step 7: Check what the public serves as frontend
print('\n=== 7. Check public frontend HTML ===')
try:
    req = urllib.request.Request('https://kb.marschat.online/kb/')
    r = urllib.request.urlopen(req, timeout=15, context=ctx)
    html = r.read().decode('utf-8')
    # Find the JS file reference
    import re
    js_files = re.findall(r'src="(/kb/s/assets/[^"]+)"', html)
    css_files = re.findall(r'href="(/kb/s/assets/[^"]+)"', html)
    print(f'  JS files: {js_files}')
    print(f'  CSS files: {css_files}')
except Exception as e:
    print(f'  ERROR: {e}')

# Step 8: Check if public serves the same JS as direct
print('\n=== 8. Compare frontend version ===')
try:
    req = urllib.request.Request('http://100.93.36.113/kb/')
    r = urllib.request.urlopen(req, timeout=10)
    html_direct = r.read().decode('utf-8')
    js_direct = re.findall(r'src="(/kb/s/assets/[^"]+)"', html_direct)
    print(f'  Direct JS: {js_direct}')
except Exception as e:
    print(f'  Direct ERROR: {e}')