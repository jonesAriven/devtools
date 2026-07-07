#!/usr/bin/env python3
import paramiko
import sys
sys.stdout.reconfigure(encoding='utf-8', errors='replace')

HOST = "1.117.70.30"
USER = "root"
PASS = "Hwx@1120930"

# 修正后的配置 - 使用正确的证书路径
NGINX_CONF = r"""# ============================================================
# Jenkins CI/CD - Nginx Reverse Proxy for jkci.marschat.online
# Backend: mykng Tailscale -> 100.93.36.113:8097
# Certificate: /etc/nginx/ssl/marschat.online/
# ============================================================

server {
    listen 80;
    listen [::]:80;
    server_name jkci.marschat.online;

    location /.well-known/acme-challenge/ {
        root /var/www/certbot;
        allow all;
    }

    location / {
        return 301 https://$host$request_uri;
    }
}

server {
    listen 443 ssl;
    listen [::]:443 ssl;
    http2 on;
    server_name jkci.marschat.online;

    ssl_certificate     /etc/nginx/ssl/marschat.online/fullchain.pem;
    ssl_certificate_key /etc/nginx/ssl/marschat.online/privkey.pem;

    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers ECDHE-ECDSA-AES128-GCM-SHA256:ECDHE-RSA-AES128-GCM-SHA256:ECDHE-ECDSA-AES256-GCM-SHA384:ECDHE-RSA-AES256-GCM-SHA384;
    ssl_prefer_server_ciphers off;
    ssl_session_cache shared:SSL:10m;
    ssl_session_timeout 1d;
    ssl_session_tickets off;

    add_header Strict-Transport-Security "max-age=15768000; includeSubDomains; preload" always;

    access_log /var/log/nginx/jenkins_access.log;
    error_log  /var/log/nginx/jenkins_error.log;

    location / {
        proxy_pass http://100.93.36.113:8097;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header X-Forwarded-Host $host;
        proxy_set_header X-Forwarded-Port $server_port;
        client_max_body_size 500m;
        proxy_connect_timeout 60s;
        proxy_send_timeout    300s;
        proxy_read_timeout    300s;
        proxy_buffering off;
        proxy_cache off;
    }

    location /cli {
        proxy_pass http://100.93.36.113:8097/cli;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_read_timeout 3600s;
    }

    location /gitee-project {
        proxy_pass http://100.93.36.113:8097/gitee-project;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location ~ ^/(script|manage|reload) {
        proxy_pass http://100.93.36.113:8097;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg|woff|woff2|ttf|eot)$ {
        proxy_pass http://100.93.36.113:8097;
        proxy_set_header Host $host;
        expires 7d;
        add_header Cache-Control "public, immutable";
    }

    location /health {
        access_log off;
        return 200 'OK';
        add_header Content-Type text/plain;
    }
}
"""


def run(ssh, cmd, t=20):
    i, o, e = ssh.exec_command(cmd, timeout=t)
    out = o.read().decode(errors='replace').strip()
    err = e.read().decode(errors='replace').strip()
    if out:
        for line in out.split('\n'):
            print(f"  {line}")
    if err and o.channel.recv_exit_status() != 0:
        print(f"  [ERR] {err}")
    return o.channel.recv_exit_status()


ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
print("Connecting to Tencent Cloud 2...")
ssh.connect(HOST, username=USER, password=PASS, timeout=20)
print("[OK] Connected!")

try:
    # 1. Verify cert path exists
    print("\n[1] Verifying certificate path...")
    rc = run(ssh, "ls -la /etc/nginx/ssl/marschat.online/")
    
    # 2. Write corrected config
    print("\n[2] Writing corrected jenkins.conf...")
    sftp = ssh.open_sftp()
    with sftp.open("/etc/nginx/conf.d/jenkins.conf", 'w') as f:
        f.write(NGINX_CONF)
    sftp.close()
    print("  [OK] Written")
    
    # 3. Test nginx
    print("\n[3] Testing nginx config...")
    rc = run(ssh, "nginx -t 2>&1")
    
    if rc == 0:
        # 4. Reload
        print("\n[4] Reloading nginx...")
        run(ssh, "systemctl reload nginx")
        print("  [OK] Nginx reloaded!")
        
        # 5. Verify
        print("\n[5] Verification...")
        run(ssh, "curl -sk -o /dev/null -w 'HTTP %%{http_code}\n' https://localhost/ -H 'Host: jkci.marschat.online' --resolve 'jkci.marschat.online:443:127.0.0.1'")
        run(ssh, "ss -tlnp | grep ':443 ' | head -3")
    else:
        print("  [FAIL] Nginx config has errors!")
        
finally:
    ssh.close()
    print("\n[DONE]")
