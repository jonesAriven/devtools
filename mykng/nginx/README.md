# Nginx 配置说明

> mykng 知识库平台 Nginx 配置（双层架构）

## 一、双层架构说明

mykng 采用双层 Nginx 架构，SSL 终结与静态资源服务分离：

```
用户（公网）
    │ HTTPS
    ▼
┌─────────────────────────────────────────────┐
│  腾讯云2号 Nginx (:443)                      │
│  kb.marschat.online (1.117.70.30)            │
│  SSL 终结 + 纯反向代理（不存静态文件）        │
└──────────┬──────────────────────────────────┘
           │ Tailscale 直连 (100.93.36.113:80)
           ▼
┌─────────────────────────────────────────────┐
│  mykng 本地 Nginx (:80)                      │
│  kb.marschat.online (192.168.31.105)         │
│  静态资源服务 + API 反代                     │
│  /data/kb-web/ (前端产物)                    │
└──────────┬──────────────────────────────────┘
           │
           ▼
        kb-gateway (127.0.0.1:8090)
```

### 设计原则

| 原则 | 说明 |
|------|------|
| **SSL 终结在边缘** | 证书管理集中在腾讯云2号，内网全 HTTP |
| **静态文件本地化** | 前端产物只部署在 mykng 本地，无需 scp 到腾讯云 |
| **Tailscale 直连** | 腾讯云2号 ↔ mykng 通过 Tailscale 私有网络直连，延迟 < 1ms |
| **单点暴露** | 只有 kb-gateway 映射到宿主机 8090，其余服务走 Docker 内网 |
| **traceId 全链路** | 腾讯云 Nginx 生成 `$request_id` → 透传到本地 Nginx → 网关 → 各服务 |

## 二、文件清单

| 文件 | 部署位置 | 职责 |
|------|---------|------|
| `kb.marschat.online.conf` | mykng-debian `/etc/nginx/sites-available/kb.marschat.online` | 静态资源 + API 反代 + SPA 路由 + MinIO 控制台 |
| `kb.marschat.online.tencent.conf` | 腾讯云2号 `/etc/nginx/sites-available/kb` | SSL 终结 + 纯反向代理（HTTP→HTTPS 跳转） |
| `conf.d/default.conf` | Docker `docker/nginx/conf.d/default.conf` | 通用容器化部署模板（基础反代） |
| `ssl/README.md` | — | SSL 证书申请/续期说明（acme.sh + 阿里云 DNS-01） |

> 根目录 `kb-nginx.conf` 为早期兼容配置，保留不动；新部署统一使用 `nginx/` 目录下的文件。

## 三、部署步骤

### 3.1 mykng 本地 Nginx（192.168.31.105）

```bash
# 在 mykng-debian 执行

# 1. 复制配置文件
sudo cp /root/devtools/mykng/nginx/kb.marschat.online.conf /etc/nginx/sites-available/kb.marschat.online

# 2. 创建软链
sudo ln -sf /etc/nginx/sites-available/kb.marschat.online /etc/nginx/sites-enabled/kb.marschat.online

# 3. 移除默认站点（如有）
sudo rm -f /etc/nginx/sites-enabled/default

# 4. 如有 Apache2 占用 80 端口，先停止
sudo systemctl stop apache2 2>/dev/null
sudo systemctl disable apache2 2>/dev/null

# 5. 测试配置
sudo nginx -t

# 6. 重载
sudo nginx -s reload
```

### 3.2 腾讯云2号 Nginx（1.117.70.30）

```bash
# SSH 到腾讯云2号
ssh root@1.117.70.30

# 1. 复制配置文件（从项目同步或手动创建）
sudo cp /root/devtools/mykng/nginx/kb.marschat.online.tencent.conf /etc/nginx/sites-available/kb

# 2. 创建软链
sudo ln -sf /etc/nginx/sites-available/kb /etc/nginx/sites-enabled/kb

# 3. 确认证书路径（当前过渡期复用 nexus 证书，见 ssl/README.md 申请独立证书）

# 4. 测试配置
sudo nginx -t

# 5. 重载
sudo nginx -s reload
```

## 四、验证命令

### 4.1 配置语法验证

```bash
# mykng 本地
sudo nginx -t
# 期望输出:
#   nginx: the configuration file /etc/nginx/nginx.conf syntax is ok
#   nginx: configuration file /etc/nginx/nginx.conf test is successful

# 腾讯云2号
ssh root@1.117.70.30 "nginx -t"
```

### 4.2 本地访问验证（mykng-debian）

```bash
# 静态资源
curl -I http://localhost/kb/
# 期望: 200 + text/html

# API（需服务已启动）
curl http://localhost/kb/api/auth/login -X POST \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'
# 期望: JSON 响应

# 健康检查
curl http://localhost/health
# 期望: {"status":"ok"}
```

### 4.3 Tailscale 链路验证（腾讯云2号 → mykng 本地）

```bash
ssh root@1.117.70.30 "curl -I http://100.93.36.113/kb/"
# 期望: 200 + text/html
```

### 4.4 公网验证

```bash
# 前端页面（-k 忽略 SNI 警告，独立证书就绪后可去掉）
curl -k -I https://kb.marschat.online/kb/
# 期望: 200 + text/html

# API
curl -k -X POST https://kb.marschat.online/kb/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'
# 期望: JSON 响应
```

## 五、配置要点

### 5.1 限流

mykng 本地配置对 `/kb/api/` 启用 `limit_req`：

```nginx
limit_req_zone $binary_remote_addr zone=kb_api_limit:10m rate=10r/s;
# burst=20 nodelay：允许 20 个突发请求，超出立即 503
```

### 5.2 gzip 压缩

mykng 本地配置对文本类资源启用 gzip（level 6），静态资源已带 hash 走长缓存。

### 5.3 安全头

| 头 | 本地 Nginx | 腾讯云 Nginx | 说明 |
|----|-----------|-------------|------|
| Strict-Transport-Security | — | ✅ | HSTS，强制 HTTPS（仅边缘层） |
| X-Frame-Options | ✅ | ✅ | 防点击劫持 |
| X-Content-Type-Options | ✅ | ✅ | 防 MIME 嗅探 |
| X-XSS-Protection | ✅ | ✅ | XSS 过滤 |
| Referrer-Policy | ✅ | ✅ | Referer 控制 |

### 5.4 traceId 链路

```
腾讯云 Nginx ($request_id)
  → proxy_set_header X-Trace-Id → 本地 Nginx
    → map $http_x_trace_id $kb_trace_id（透传或补生成）
      → proxy_set_header X-Trace-Id → kb-gateway
        → TraceIdFilter → 各微服务日志 [traceId]
```

本地 Nginx 日志格式 `kb_main` 包含 `trace_id` 字段，可与后端日志对齐排查。

## 六、端口变更检查清单

如需修改网关端口（如 8090 → 其他）：

| 文件 | 修改内容 |
|------|---------|
| `docker-compose.yml` | `ports: - "新端口:8080"` |
| `nginx/kb.marschat.online.conf` | `proxy_pass http://127.0.0.1:新端口;` |
| 腾讯云 Nginx | 无需修改（反代到本地 Nginx 80 端口，与网关端口无关） |
