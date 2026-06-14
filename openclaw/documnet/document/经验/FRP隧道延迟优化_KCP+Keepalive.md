# FRP 隧道延迟优化方案

> 记录于：2026-05-23
> 场景：激活码系统正式环境（tools.marschat.online），跨腾讯云→阿里云→内网 Debian

## 问题

通过 FRP 访问内网服务时，跨云延迟导致请求慢。

**原始链路：**
```
用户 → 龙虾主机(Nginx) → 阿里云(frps) → FRP TCP 隧道 → 内网 Debian → Docker 服务
```

**原始延迟：**
| 环节 | 延迟 |
|------|------|
| 龙虾主机(腾讯云) → 阿里云 | ~142ms RTT |
| FRP TCP 隧道（TCP 套 TCP） | 握手额外 ~300ms |
| 首请求全链路 | ~0.7s |
| 热请求 | ~0.3-0.4s |

## 优化方案

### 方案一：FRP 隧道改 KCP 协议

**问题：** FRP 默认用 TCP，隧道内层也是 TCP，造成 TCP-over-TCP 问题。丢包时内外层同时退避重传，延迟加倍。

**解决：** 将 proxy 的传输层改为 KCP（基于 UDP），避免 TCP 嵌套。

#### 配置方法

frpc（内网机器）`/etc/frp/frpc.toml`：

```toml
[[proxies.activation]]
name = "activation"
type = "tcp"                                    # 代理类型保持 tcp
local_ip = "127.0.0.1"
local_port = 18080
remote_port = 18080
transport = { protocol = "kcp" }                # 传输层改用 KCP
```

**注意：** frps 服务端不需要额外配置，KCP 默认使用与 `bind_port` 相同的端口。

重启 frpc：
```bash
systemctl restart frpc
```

#### 效果
- ✅ 消除 TCP-over-TCP 嵌套问题
- ✅ UDP 传输，丢包恢复快
- ✅ 连接建立时间减少约 40%

---

### 方案二：Nginx 对 upstream 启用 keepalive 连接池

**问题：** 每次请求 Nginx 都要新建 TCP 连接到上游（跨腾讯云→阿里云 142ms 三次握手），累积耗时约 528ms。

**解决：** Nginx 与上游保持长连接池，复用连接。

#### 配置方法

龙虾主机 `/etc/nginx/nginx.conf`：

```nginx
# 1. 在 http 块新增 upstream 连接池
upstream activation_backend {
    server aliyun.marschat.online:18080;
    keepalive 8;                # 保持 8 个长连接
}

# 2. 在 server 块指向 upstream 并启用 HTTP/1.1
server {
    server_name tools.marschat.online;

    location / {
        proxy_pass http://activation_backend;    # 指向上游池
        proxy_http_version 1.1;                  # 启用 HTTP/1.1 长连接
        proxy_set_header Connection "";          # 清除 Connection 头，让后端保持长连
        proxy_set_header Host $host;
        ...
    }
}
```

**影响评估：**
| 方面 | 影响 |
|------|------|
| 内存 | 多占 8 个连接，< 1MB |
| FRP 隧道 | KCP 是 UDP，互不干扰 |
| 后端兼容性 | Spring Boot 原生支持 HTTP/1.1 keepalive |
| 空闲超时 | 超时后自动断开，下次请求静默重建，用户无感 |

#### 效果
- ✅ 热连接 connect 时间从 ~280ms 降到 ~0.8ms（99% 降幅）
- ✅ 热请求全链路从 ~0.4s 降到 ~0.19s
- ✅ 零副作用

---

## 优化前后对比

| 指标 | 优化前 (TCP) | 优化后 (KCP + keepalive) | 降幅 |
|------|-------------|------------------------|------|
| FRP 传输层 | TCP（套 TCP） | KCP（UDP） | 解决嵌套 |
| 连接建立 | ~280ms（三次握手） | ~0.8ms（热连接） | **99%** |
| 首请求全链路 | ~0.7s | ~0.5s | 30% |
| 热请求全链路 | ~0.3-0.4s | **~0.19s** | **50%** |

## 相关机器

| 机器 | 做了什么 | 状态 |
|------|---------|------|
| 内网 Debian（192.168.31.182） | frpc 启用 KCP | ✅ 已应用 |
| 龙虾主机（49.51.245.134） | Nginx keepalive 连接池 | ✅ 已应用 |

---

## 补充：SSH 隧道 DNS 污染问题

**场景：** 通过 FRP + SSH 隧道（SOCKS5）访问 Google 失败，但访问 GitHub 正常。

**现象：** 同样走 SSH 隧道，`socks5`（本地 DNS）超时，`socks5h`（远程 DNS）成功。

**根因：** 家用路由器 DNS（192.168.31.1）对 Google 域名返回了被污染的 IP。SOCKS5 客户端默认本地解析 DNS，拿到的 IP 是错误的。

**教训：** 在涉及代理的 SSH 隧道场景中，优先使用远程 DNS 解析（socks5h），避免本地 DNS 污染影响代理效果。Clash 等代理工具内部有完善的 DNS fallback 机制（本地 DNS + 海外 DNS 双查），可以自动处理此问题。
