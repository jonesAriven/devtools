import urllib.request, json

BASE = 'http://100.93.36.113:8090/kb/api'

# Login
data = json.dumps({'username':'admin','password':'admin123'}).encode()
req = urllib.request.Request(BASE+'/auth/login', data=data, headers={'Content-Type':'application/json'})
resp = urllib.request.urlopen(req, timeout=10)
login = json.loads(resp.read())
token = login['data']['accessToken']
print('Login OK')

def request_api(method, path, body=None):
    headers = {'Authorization': f'Bearer {token}'}
    if body:
        headers['Content-Type'] = 'application/json'
        data = json.dumps(body).encode()
    else:
        data = None
    req = urllib.request.Request(BASE+path, data=data, headers=headers, method=method)
    try:
        r = urllib.request.urlopen(req, timeout=10)
        return r.status, json.loads(r.read())
    except urllib.error.HTTPError as e:
        try: return e.code, json.loads(e.read())
        except: return e.code, str(e)

# Step 1: Get doc list to find a real doc ID
print('\n=== 1. Get doc list ===')
code, resp = request_api('GET', '/doc/list?page=1&size=5')
print(f'  /doc/list: code={code}')
if code == 200 and resp.get('code') == 200:
    docs = resp['data'].get('list', [])
    print(f'  Found {len(docs)} docs')
    for d in docs[:3]:
        print(f'    id={d["id"]}, title={d.get("title","")}, type={d.get("type","")}')
else:
    print(f'  ERROR: {resp}')
    docs = []

# Step 2: Get doc detail (this is what "edit" triggers first)
if docs:
    doc_id = docs[0]['id']
    print(f'\n=== 2. Get doc detail (id={doc_id}) ===')
    code, resp = request_api('GET', f'/doc/{doc_id}')
    print(f'  GET /doc/{doc_id}: code={code}')
    if code == 200 and resp.get('code') == 200:
        d = resp['data']
        print(f'  title={d.get("title","")}, content_len={len(str(d.get("content","")))}')
    else:
        print(f'  ERROR: {json.dumps(resp, ensure_ascii=False)[:300]}')

    # Step 3: Try updating doc (PUT /doc/{id})
    print(f'\n=== 3. Update doc (id={doc_id}) ===')
    update_body = {
        'title': docs[0].get('title', 'test'),
        'content': docs[0].get('content', 'test content'),
        'folderId': docs[0].get('folderId'),
    }
    code, resp = request_api('PUT', f'/doc/{doc_id}', update_body)
    print(f'  PUT /doc/{doc_id}: code={code}')
    if code != 200 or resp.get('code') != 200:
        print(f'  ERROR: {json.dumps(resp, ensure_ascii=False)[:500]}')
    else:
        print(f'  Update OK')

# Step 4: Test doc list with spaceId (may be what space page uses)
print('\n=== 4. Doc list with spaceId ===')
code, resp = request_api('GET', '/doc/list?spaceId=32&page=1&size=5')
print(f'  /doc/list?spaceId=32: code={code}')
if code == 200:
    print(f'  code={resp.get("code")}, items={len(resp.get("data",{}).get("list",[]))}')
else:
    print(f'  ERROR: {resp}')

# Step 5: Test folder tree and folder detail
print('\n=== 5. Folder operations ===')
code, resp = request_api('GET', '/folder/tree/32')
print(f'  /folder/tree/32: code={code}')
if code == 200 and resp.get('code') == 200:
    folders = resp['data']
    print(f'  Found {len(folders)} root folders')
    if folders:
        fid = folders[0].get('id')
        print(f'  First folder id={fid}')
        code2, resp2 = request_api('GET', f'/folder/{fid}')
        print(f'  GET /folder/{fid}: code={code2}')
        if code2 != 200 or resp2.get('code') != 200:
            print(f'  ERROR: {json.dumps(resp2, ensure_ascii=False)[:300]}')

# Step 6: Test doc create + update flow
print('\n=== 6. Create + Update doc flow ===')
code, resp = request_api('POST', '/doc', {'title':'API测试文档', 'content':'测试内容', 'spaceId':32, 'folderId':None})
print(f'  POST /doc: code={code}')
if code == 200 and resp.get('code') == 200:
    new_doc = resp['data']
    new_id = new_doc['id']
    print(f'  Created doc id={new_id}')
    # Now update it
    code2, resp2 = request_api('PUT', f'/doc/{new_id}', {'title':'API测试文档-改', 'content':'修改后内容', 'folderId':None})
    print(f'  PUT /doc/{new_id}: code={code2}')
    if code2 != 200 or resp2.get('code') != 200:
        print(f'  ERROR: {json.dumps(resp2, ensure_ascii=False)[:500]}')
    # Delete it
    code3, resp3 = request_api('DELETE', f'/doc/{new_id}')
    print(f'  DELETE /doc/{new_id}: code={code3}')
else:
    print(f'  ERROR: {json.dumps(resp, ensure_ascii=False)[:500]}')