# SSL 证书说明

> 本目录用于存放 mykng 知识库平台的 SSL 证书文件（不提交 Git，仅本地保留）。

## 证书获取方式

使用 **acme.sh** + **Let's Encrypt** 申请泛域名证书（通配符证书，覆盖 `*.marschat.online`）。

### 1. 安装 acme.sh

```bash
curl https://get.acme.sh | sh
source ~/.bashrc
```

### 2. 配置 DNS API（推荐）

泛域名证书必须通过 DNS-01 验证。以腾讯云 DNSPod 为例：

```bash
export Tencent_SecretId="你的SecretId"
export Tencent_SecretKey="你的SecretKey"
```

> 其他 DNS 服务商的 API 配置参考：https://github.com/acmesh-official/acme.sh/wiki/dnsapi

### 3. 申请泛域名证书

```bash
acme.sh --issue --dns dns_tencent -d marschat.online -d *.marschat.online
```

### 4. 安装证书到本目录

```bash
acme.sh --install-cert -d marschat.online \
    --key-file       /path/to/docker/nginx/ssl/marschat.online.key \
    --fullchain-file /path/to/docker/nginx/ssl/marschat.online.crt \
    --reloadcmd      "nginx -s reload"
```

### 5. 自动续期

acme.sh 默认安装 cron 任务，每 60 天自动续期并执行 reloadcmd，无需手动干预。

## 证书文件清单

| 文件 | 说明 | 是否提交 Git |
|------|------|------------|
| marschat.online.crt | 证书文件（含完整链） | ❌ 不提交 |
| marschat.online.key | 私钥文件 | ❌ 不提交 |

## Nginx HTTPS 配置示例

申请证书后，在 `docker/nginx/conf.d/` 下新增 HTTPS server 块：

```nginx
server {
    listen 443 ssl http2;
    server_name kb.marschat.online;

    ssl_certificate     /etc/nginx/ssl/marschat.online.crt;
    ssl_certificate_key /etc/nginx/ssl/marschat.online.key;
    ssl_protocols       TLSv1.2 TLSv1.3;
    ssl_ciphers         HIGH:!aNULL:!MD5;
    ssl_session_cache   shared:SSL:10m;
    ssl_session_timeout 10m;

    # 其余 location 配置与 default.conf 一致
}

# HTTP → HTTPS 跳转
server {
    listen 80;
    server_name kb.marschat.online;
    return 301 https://$host$request_uri;
}
```

## 注意事项

- 证书文件为敏感信息，**严禁提交 Git**（已在 `.gitignore` 中忽略 `*.key`、`*.crt`、`*.pem`）。
- 生产环境建议在腾讯云/Cloudflare 等 CDN 层终结 SSL，本地 Nginx 仅走 HTTP（参考 `nginx/kb.marschat.online.tencent.conf`）。
- 证书过期前 acme.sh 会自动续期；如需手动续期：`acme.sh --renew -d marschat.online --force`。
