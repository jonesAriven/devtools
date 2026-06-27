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

# Step 1: Get folder tree to find folderId
print('\n=== 1. Get folder tree ===')
code, resp = api('GET', '/folder/tree?spaceId=32')
if code == 200 and resp.get('code') == 200:
    folders = resp['data']
    folder_id = None
    for f in folders:
        print(f'  folder id={f["id"]}, name={f.get("name","")}')
        if folder_id is None:
            folder_id = f['id']

# Step 2: Create a doc with content
print(f'\n=== 2. Create doc with content (folderId={folder_id}) ===')
code, resp = api('POST', '/doc', {
    'title': 'Content测试文档',
    'content': '<p>这是测试内容</p>',
    'spaceId': 32,
    'folderId': folder_id,
})
print(f'  code={code}')
if code == 200 and resp.get('code') == 200:
    new_doc = resp['data']
    new_id = new_doc['id']
    print(f'  Created doc id={new_id}')

    # Step 3: GET /doc/{id} - verify content is returned
    print(f'\n=== 3. GET /doc/{new_id} - verify content ===')
    code2, resp2 = api('GET', f'/doc/{new_id}')
    print(f'  code={code2}')
    if code2 == 200 and resp2.get('code') == 200:
        d = resp2['data']
        print(f'  title={d.get("title","")}')
        content = d.get('content', '')
        print(f'  content={"YES (" + str(len(content)) + " chars): " + content[:50] if content else "NO/EMPTY"}')
        print(f'  spaceId={d.get("spaceId","MISSING")}')
        print(f'  wordCount={d.get("wordCount","MISSING")}')
        print(f'  folderId={d.get("folderId","MISSING")}')
    else:
        print(f'  ERROR: {json.dumps(resp2, ensure_ascii=False)[:300]}')

    # Cleanup: delete the test doc
    api('DELETE', f'/doc/{new_id}')
    print(f'\n  Cleaned up doc {new_id}')
else:
    print(f'  ERROR: {json.dumps(resp, ensure_ascii=False)[:300]}')

# Step 4: Check all docs and their content
print('\n=== 4. Check all docs content ===')
code, resp = api('GET', '/doc/list?page=1&size=20')
if code == 200 and resp.get('code') == 200:
    docs = resp['data']['list']
    for d in docs:
        doc_id = d['id']
        code2, resp2 = api('GET', f'/doc/{doc_id}')
        if code2 == 200 and resp2.get('code') == 200:
            content = resp2['data'].get('content', '')
            print(f'  doc {doc_id}: title={d.get("title","")}, content={"YES" if content else "NO"}, folderId={d.get("folderId")}')