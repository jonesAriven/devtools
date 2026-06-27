import requests
import json

base = 'http://localhost:8090/kb/api'

print('=== 1. 登录测试 ===')
login_resp = requests.post(f'{base}/auth/login', json={'username':'admin','password':'admin123'}, timeout=10)
print(f'状态码: {login_resp.status_code}')
login_data = login_resp.json()
print(f'响应: {json.dumps(login_data, ensure_ascii=False, indent=2)}')

if login_data.get('code') == 200:
    token = login_data['data']['accessToken']
    headers = {'Authorization': f'Bearer {token}'}
    
    print('\n=== 2. /share/my 接口测试 ===')
    share_resp = requests.get(f'{base}/share/my', headers=headers, timeout=10)
    print(f'状态码: {share_resp.status_code}')
    print(f'响应: {share_resp.text[:300]}')
    
    print('\n=== 3. /space/list 接口测试 ===')
    space_resp = requests.get(f'{base}/space/list', headers=headers, timeout=10)
    print(f'状态码: {space_resp.status_code}')
    space_data = space_resp.json()
    print(f'响应: {json.dumps(space_data, ensure_ascii=False)[:300]}')
    
    if space_data.get('code') == 200 and space_data.get('data'):
        space_id = space_data['data'][0]['id']
        print(f'\n=== 4. /folder/tree/{space_id} 接口测试 ===')
        folder_resp = requests.get(f'{base}/folder/tree/{space_id}', headers=headers, timeout=10)
        print(f'状态码: {folder_resp.status_code}')
        print(f'响应: {folder_resp.text[:300]}')
    
    print('\n=== 5. /space/current 接口测试 ===')
    current_resp = requests.get(f'{base}/space/current', headers=headers, timeout=10)
    print(f'状态码: {current_resp.status_code}')
    print(f'响应: {current_resp.text[:300]}')
else:
    print('登录失败，无法继续测试')
