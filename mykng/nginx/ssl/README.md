# SSL 证书说明

> 本目录仅存放证书说明文档，实际证书文件部署在腾讯云2号服务器。

## 一、证书路径

### 目标路径（kb.marschat.online 独立证书）

```
/etc/letsencrypt/live/kb.marschat.online/
├── fullchain.pem      # 证书链（nginx ssl_certificate）
├── privkey.pem        # 私钥（nginx ssl_certificate_key）
├── chain.pem          # 中间证书链
├── cert.pem           # 域名证书（不含中间链）
└── README             # Let's Encrypt 自动生成的说明
```

### 当前过渡（复用 nexus.marschat.online 证书）

kb.marschat.online 未备案，临时复用 nexus.marschat.online 证书（SAN 含 `nexus` + `tools`），浏览器访问 `kb` 会有 SNI 警告。

```
/etc/letsencrypt/live/nexus.marschat.online/
├── fullchain.pem
└── privkey.pem
```

切换方式见 `nginx/kb.marschat.online.tencent.conf` 中的注释。

## 二、证书申请方式（acme.sh + 阿里云 DNS-01）

DNS-01 验证不需要 HTTP 可访问，适合未备案域名和通配符证书。

### 2.1 安装 acme.sh

```bash
# 在腾讯云2号（1.117.70.30）执行
curl https://get.acme.sh | sh
source ~/.bashrc

# 验证
acme.sh --version
```

### 2.2 配置阿里云 DNS API

```bash
# 阿里云控制台 → AccessKey 管理 → 创建 RAM 子账号
# 授予权限：AliyunDNSFullAccess
# 获取 AccessKey ID 和 Secret

export Ali_Key="你的AccessKeyId"
export Ali_Secret="你的AccessKeySecret"
```

### 2.3 申请证书

```bash
# 申请 kb.marschat.online 单域名证书（DNS-01）
acme.sh --issue --dns dns_ali \
  -d kb.marschat.online \
  --keylength ec-256

# 如需通配符证书（覆盖所有子域名）
# acme.sh --issue --dns dns_ali \
#   -d marschat.online -d '*.marschat.online' \
#   --keylength ec-256
```

### 2.4 安装证书到 Nginx 目录

```bash
# 安装到 Let's Encrypt 标准路径（与 nginx 配置一致）
acme.sh --install-cert -d kb.marschat.online --ecc \
  --key-file       /etc/letsencrypt/live/kb.marschat.online/privkey.pem \
  --fullchain-file /etc/letsencrypt/live/kb.marschat.online/fullchain.pem \
  --reloadcmd      "nginx -t && nginx -s reload"
```

> acme.sh 会自动创建 `/etc/letsencrypt/live/kb.marschat.online/` 目录并安装证书，同时配置自动续期 cron。

## 三、续期

### 自动续期

acme.sh 安装证书时已自动写入 cron（每天检查），默认 60 天自动续期：

```bash
# 查看已安装证书
acme.sh --list

# 查看续期 cron
crontab -l | grep acme
```

### 手动续期

```bash
# 强制续期（测试）
acme.sh --renew -d kb.marschat.online --ecc --force

# 续期后自动 reload nginx（已在 --install-cert 的 --reloadcmd 中配置）
```

## 四、验证

```bash
# 证书有效期
acme.sh --list | grep kb.marschat.online

# 在线验证（需 DNS 已解析到 1.117.70.30）
curl -vI https://kb.marschat.online/kb/ 2>&1 | grep -E "subject|issuer|expire"

# SSL Labs 评级
# 浏览器访问: https://www.ssllabs.com/ssltest/analyze.html?d=kb.marschat.online
```

## 五、切换回独立证书后

证书安装完成后，编辑腾讯云2号 Nginx 配置：

```bash
# 编辑配置
vi /etc/nginx/sites-available/kb

# 确认使用 kb.marschat.online 独立证书路径（取消注释，注释掉 nexus 证书行）
# ssl_certificate     /etc/letsencrypt/live/kb.marschat.online/fullchain.pem;
# ssl_certificate_key /etc/letsencrypt/live/kb.marschat.online/privkey.pem;

# 测试并重载
nginx -t && nginx -s reload
```

切换后浏览器不再有 SNI 警告。

## 六、备份

```bash
# 证书文件备份到安全位置
tar -czf kb-ssl-backup-$(date +%Y%m%d).tar.gz /etc/letsencrypt/live/kb.marschat.online/
# 妥善保管私钥，不要提交到代码仓库
```
