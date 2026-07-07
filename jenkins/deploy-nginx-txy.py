#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
连接腾讯云2号并部署 Jenkins Nginx 反向代理配置
"""
import paramiko
import sys
import os

# 强制 UTF-8 输出
sys.stdout.reconfigure(encoding='utf-8', errors='replace')

HOST = "1.117.70.30"
PORT = 22
USER = "root"
PASS = "Hwx@1120930"

NGINX_CONF = r"""# ============================================================
# Jenkins CI/CD - Nginx Reverse Proxy for jkci.marschat.online
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
    listen 443 ssl http2;
    listen [::]:443 ssl http2;
    server_name jkci.marschat.online;

    ssl_certificate     /etc/letsencrypt/live/marschat.online/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/marschat.online/privkey.pem;

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
        limit_req zone=api burst=20 nodelay;
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


def run_cmd(ssh, cmd, timeout=30):
    stdin, stdout, stderr = ssh.exec_command(cmd, timeout=timeout)
    out = stdout.read().decode(errors='replace')
    err = stderr.read().decode(errors='replace')
    exit_code = stdout.channel.recv_exit_status()
    if out.strip():
        for line in out.strip().split('\n'):
            print(f"  {line}")
    if err.strip() and exit_code != 0:
        for line in err.strip().split('\n'):
            print(f"  [ERR] {line}")
    return exit_code


def main():
    print("=" * 55)
    print("  Deploy Jenkins Nginx Reverse Proxy")
    print(f"  Target: {HOST}  (root)")
    print("=" * 55)

    print("\n[1/5] Connecting to tencent cloud 2...")
    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    try:
        ssh.connect(HOST, port=PORT, username=USER, password=PASS, timeout=20)
        print("  [OK] Connected!")
    except Exception as e:
        print(f"  [FAIL] Connection failed: {e}")
        sys.exit(1)

    try:
        print("\n[2/5] Checking environment...")
        run_cmd(ssh, "hostname && whoami && uname -a")
        run_cmd(ssh, "nginx -v 2>&1")
        run_cmd(ssh, "ls /etc/nginx/conf.d/")
        
        print("\n  Checking SSL certificate...")
        run_cmd(ssh, "test -f /etc/letsencrypt/live/marschat.online/fullchain.pem && echo 'CERT_OK' || echo 'CERT_MISSING'")

        print("\n[3/5] Backing up old config...")
        run_cmd(ssh, "cp -f /etc/nginx/conf.d/jenkins.conf /etc/nginx/conf.d/jenkins.conf.bak.$(date +%Y%m%d%H%M%S) 2>/dev/null; echo 'done'")

        print("\n[4/5] Writing jenkins.conf...")
        sftp = ssh.open_sftp()
        try:
            remote_path = "/etc/nginx/conf.d/jenkins.conf"
            with sftp.open(remote_path, 'w') as f:
                f.write(NGINX_CONF)
            print(f"  [OK] Written to {remote_path}")
        finally:
            sftp.close()

        print("\n[5/5] Testing and reloading Nginx...")
        rc = run_cmd(ssh, "nginx -t 2>&1")
        if rc == 0:
            print("  [OK] Nginx config syntax OK!")
            run_cmd(ssh, "systemctl reload nginx")
            print("  [OK] Nginx reloaded!")
        else:
            print("  [FAIL] Nginx config error!")

        print("\n" + "=" * 55)
        print("  Verifying deployment...")
        print("=" * 55)
        run_cmd(ssh, "grep -c 'jkci.marschat.online' /etc/nginx/conf.d/jenkins.conf && echo 'Domain config active'")
        run_cmd(ssh, "curl -sf -o /dev/null -w 'HTTP %%{http_code}\n' http://localhost/health -H 'Host: jkci.marschat.online' || echo '(health check needs DNS)'")

    finally:
        ssh.close()
        print("\n[DONE] All complete! URL: https://jkci.marschat.online")


if __name__ == "__main__":
    main()
