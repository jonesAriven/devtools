import urllib.request, re

def check(url, label):
    try:
        req = urllib.request.Request(url)
        resp = urllib.request.urlopen(req, timeout=10)
        html = resp.read().decode('utf-8')
        js_match = re.search(r'src="(/kb/s/assets/index-[^"]+\.js)"', html)
        if js_match:
            js_file = js_match.group(1)
            latest = 'JaL_9to8' in js_file
            status = 'LATEST' if latest else 'OLD'
            print(f'  [{status}] {label} -> {js_file}')
            return latest
        else:
            print(f'  [???] {label} -> no JS found, html[:200]={html[:200]}')
            return False
    except Exception as e:
        print(f'  [ERR] {label} -> {e}')
        return False

print('=== Mykng direct (Tailscale 100.93.36.113) ===')
r1 = check('http://100.93.36.113/kb/', '/kb/')
r2 = check('http://100.93.36.113/kb/s/', '/kb/s/')

print()
print('=== Public domain kb.marschat.online ===')
import ssl
ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE
def check_ssl(url, label):
    try:
        req = urllib.request.Request(url)
        resp = urllib.request.urlopen(req, timeout=15, context=ctx)
        html = resp.read().decode('utf-8')
        js_match = re.search(r'src="(/kb/s/assets/index-[^"]+\.js)"', html)
        if js_match:
            js_file = js_match.group(1)
            latest = 'JaL_9to8' in js_file
            status = 'LATEST' if latest else 'OLD'
            print(f'  [{status}] {label} -> {js_file}')
            return latest
        else:
            print(f'  [???] {label} -> no JS found')
            return False
    except Exception as e:
        print(f'  [ERR] {label} -> {e}')
        return False

r3 = check_ssl('https://kb.marschat.online/kb/', '/kb/')
r4 = check_ssl('https://kb.marschat.online/kb/s/', '/kb/s/')

print()
if r1 and r2:
    print('mykng local: OK (latest frontend)')
else:
    print('mykng local: PROBLEM - not serving latest frontend')
if r3 and r4:
    print('public domain: OK (latest frontend)')
else:
    print('public domain: OLD version - DNS may point to Tencent Cloud 2, not mykng')