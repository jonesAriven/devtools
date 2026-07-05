#!/bin/bash
set -e

echo "=== 步骤 1: 生成 ADMIN_TOKEN ==="
ADMIN_TOKEN=$(openssl rand -base64 48)
echo "生成的 ADMIN_TOKEN: $ADMIN_TOKEN"

echo ""
echo "=== 步骤 2: 创建 Nginx 配置文件 ==="
cat > /etc/nginx/conf.d/vault.conf << 'EOF'
# ============================================================
# Vaultwarden 密码管理器 Nginx 配置
# 域名: vault.marschat.online
# SSL 在腾讯云 2 号终结，此处为 HTTP
# ============================================================

server {
    listen 80;
    server_name vault.marschat.online;

    client_max_body_size 528m;

    # 根路径直接反代到 Vaultwarden
    location / {
        proxy_pass http://127.0.0.1:8222;
        proxy_http_version 1.1;
        proxy_set_header Host              $host;
        proxy_set_header X-Real-IP         $remote_addr;
        proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header Connection        "";
        proxy_connect_timeout 60s;
        proxy_send_timeout    300s;
        proxy_read_timeout    300s;
        proxy_buffering off;
    }
}
EOF
echo "已创建 /etc/nginx/conf.d/vault.conf"

echo ""
echo "=== 步骤 3: 测试 Nginx 配置 ==="
nginx -t 2>&1

echo ""
echo "=== 步骤 4: 重载 Nginx ==="
nginx -s reload 2>&1
echo "Nginx 重载完成"

echo ""
echo "=== 步骤 5: 保存 ADMIN_TOKEN 到文件（仅 root 可读）==="
echo "ADMIN_TOKEN=$ADMIN_TOKEN" > /root/vaultwarden-data/.admin_token
chmod 600 /root/vaultwarden-data/.admin_token
echo "已保存到 /root/vaultwarden-data/.admin_token"

echo ""
echo "=== 步骤 6: 停止旧 Vaultwarden 容器 ==="
docker stop vaultwarden 2>/dev/null
docker rm vaultwarden 2>/dev/null
echo "旧容器已移除"

echo ""
echo "=== 步骤 7: 启动新 Vaultwarden 容器（带 ADMIN_TOKEN）==="
docker run -d \
  --name vaultwarden \
  --restart always \
  -p 8222:80 \
  -v /root/vaultwarden-data:/data \
  -e ROCKET_PROFILE=release \
  -e ROCKET_ADDRESS=0.0.0.0 \
  -e ROCKET_PORT=80 \
  -e ADMIN_TOKEN="$ADMIN_TOKEN" \
  -e DOMAIN=https://vault.marschat.online \
  -e SIGNUPS_ALLOWED=true \
  -e SHOW_PASSWORD_HINT=false \
  -e INVITATIONS_ALLOWED=true \
  -e WEBSOCKET_ENABLED=true \
  -e IP_HEADER=X-Forwarded-For \
  vaultwarden/server:latest

echo ""
echo "=== 步骤 8: 等待容器启动 ==="
sleep 5
docker ps --filter name=vaultwarden --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

echo ""
echo "=== 步骤 9: 验证访问 ==="
echo "--- 直接访问 http://localhost:8222 ---"
curl -sI http://localhost:8222 2>/dev/null | head -3

echo ""
echo "--- 通过 Nginx 访问 vault.marschat.online ---"
curl -sI --resolve vault.marschat.online:80:127.0.0.1 http://vault.marschat.online/ 2>/dev/null | head -3

echo ""
echo "--- 管理后台 /admin ---"
curl -sI --resolve vault.marschat.online:80:127.0.0.1 http://vault.marschat.online/admin 2>/dev/null | head -3

echo ""
echo "=============================================="
echo "=== 加固完成 ==="
echo "=============================================="
echo "ADMIN_TOKEN: $ADMIN_TOKEN"
echo "管理后台地址: https://vault.marschat.online/admin"
echo "用户访问地址: https://vault.marschat.online/"
echo "ADMIN_TOKEN 已保存到: /root/vaultwarden-data/.admin_token"
echo "=============================================="
