import requests
import json

# 通过Tailscale IP直接HTTP访问（绕过腾讯云SSL）
base = 'http://100.93.36.113/kb/api'

print('=== 1. 登录测试 ===')
login_resp = requests.post(f'{base}/auth/login', json={'username':'admin','password':'admin123'}, timeout=10)
print(f'状态码: {login_resp.status_code}')
login_data = login_resp.json()
print(f'登录成功: {login_data.get("code") == 200}')

if login_data.get('code') == 200:
    token = login_data['data']['accessToken']
    headers = {'Authorization': f'Bearer {token}'}
    
    print('\n=== 2. /share/my 接口测试 ===')
    share_resp = requests.get(f'{base}/share/my', headers=headers, timeout=10)
    print(f'状态码: {share_resp.status_code}')
    
    print('\n=== 3. /space/list 接口测试 ===')
    space_resp = requests.get(f'{base}/space/list', headers=headers, timeout=10)
    print(f'状态码: {space_resp.status_code}')
    space_data = space_resp.json()
    
    if space_data.get('code') == 200 and space_data.get('data'):
        space_id = space_data['data'][0]['id']
        print(f'使用空间ID: {space_id}')
        
        print(f'\n=== 4. /folder/tree/{space_id} 接口测试 ===')
        folder_resp = requests.get(f'{base}/folder/tree/{space_id}', headers=headers, timeout=10)
        print(f'状态码: {folder_resp.status_code}')
        
        print(f'\n=== 5. /doc/list?spaceId={space_id} 接口测试 ===')
        doc_resp = requests.get(f'{base}/doc/list', params={'spaceId': space_id}, headers=headers, timeout=10)
        print(f'状态码: {doc_resp.status_code}')
        print(f'响应前200字: {doc_resp.text[:200]}')
        
        print(f'\n=== 6. /tag/list 接口测试 ===')
        tag_resp = requests.get(f'{base}/tag/list', headers=headers, timeout=10)
        print(f'状态码: {tag_resp.status_code}')
        print(f'响应前200字: {tag_resp.text[:200]}')
        
        print(f'\n=== 7. /trash/list 接口测试 ===')
        trash_resp = requests.get(f'{base}/trash/list', params={'page':1,'size':10}, headers=headers, timeout=10)
        print(f'状态码: {trash_resp.status_code}')
        print(f'响应前200字: {trash_resp.text[:200]}')
    
    print('\n=== 8. /user/info 接口测试 ===')
    user_resp = requests.get(f'{base}/user/info', headers=headers, timeout=10)
    print(f'状态码: {user_resp.status_code}')
    print(f'响应前200字: {user_resp.text[:200]}')
    
    print('\n=== 所有核心接口测试完成 ===')
else:
    print('登录失败')
