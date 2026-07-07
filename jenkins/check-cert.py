#!/usr/bin/env python3
import paramiko
import sys
sys.stdout.reconfigure(encoding='utf-8', errors='replace')

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect("1.117.70.30", port=22, username="root", password="Hwx@1120930", timeout=20)

def run(cmd):
    i, o, e = ssh.exec_command(cmd, timeout=15)
    print(o.read().decode(errors='replace').strip())
    err = e.read().decode(errors='replace').strip()
    if err: print(f"  ERR: {err}")

print("=== Certificate locations ===")
run("find /etc/letsencrypt -name '*.pem' 2>/dev/null | head -20")
print("---")
run("ls -la /etc/letsencrypt/live/ 2>/dev/null || echo 'no live dir'")
print("---")
run("ls -la /etc/nginx/ssl/ 2>/dev/null || echo 'no ssl dir'")
print("---")
run("ls -la /etc/nginx/certs/ 2>/dev/null || echo 'no certs dir'")
print("=== Other domain configs for reference ===")
run("grep -r 'ssl_certificate' /etc/nginx/conf.d/ /etc/nginx/sites-enabled/ 2>/dev/null | head -10")
print("=== Check if acme.sh installed ===")
run("which acme.sh 2>/dev/null && acme.sh --version || echo 'acme.sh not found'")
print("=== Check certbot ===")
run("which certbot 2>/dev/null && certbot certificates 2>&1 || echo 'certbot not found'")

ssh.close()
