import requests
import json

base = 'http://100.93.36.113/kb/api'

print('=== 1. 登录测试 ===')
login_resp = requests.post(f'{base}/auth/login', json={'username':'admin','password':'admin123'}, timeout=10)
login_data = login_resp.json()
print(f'登录响应 keys: {list(login_data["data"].keys())}')

if login_data.get('code') == 200:
    token = login_data['data']['accessToken']
    headers = {'Authorization': f'Bearer {token}'}
    
    print('\n=== 2. /user/profile 接口测试 ===')
    profile_resp = requests.get(f'{base}/user/profile', headers=headers, timeout=10)
    print(f'状态码: {profile_resp.status_code}')
    print(f'响应: {profile_resp.text}')
