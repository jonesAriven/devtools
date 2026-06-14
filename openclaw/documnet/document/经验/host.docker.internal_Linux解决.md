# host.docker.internal 在 Linux 上不解析的问题

> 记录于：2026-05-23
> 最后更新：2026-05-23

## 问题

Docker 容器内的应用配置了 `host.docker.internal` 作为数据库连接地址（Spring Boot 常见配置，尤其在 Mac/Windows 上开发时），但在 **Linux 宿主机的 Docker** 中运行时，该 hostname 无法解析，导致应用启动失败。

```
java.net.UnknownHostException: host.docker.internal
```

类似场景：激活码系统容器 `activecode` 连接 MySQL 时报此错误。

## 为什么宿主机的 /etc/hosts 不行

Docker 容器**不会读取宿主机 `/etc/hosts`**，每个容器有自己独立的 `/etc/hosts`，由 Docker 启动时动态生成。所以：

```bash
# 在宿主机加这个，只有宿主机自己认识，容器不认识
echo "192.168.31.182  host.docker.internal" >> /etc/hosts
```

同样的道理，NetworkManager 的 dnsmasq 模式、`/etc/network/interfaces` 等宿主机层面的 DNS 配置，**容器一概不认**。

Docker 容器的 DNS 机制：

| 网络模式 | 容器 DNS 走哪 | 能否被宿主机 DNS 影响 |
|---------|-------------|-------------------|
| 默认 bridge | 启动时 copy 宿主机 `/etc/resolv.conf`，但 `/etc/hosts` 不 copy | ❌ |
| 用户自定义网络 | Docker 内建 DNS（127.0.0.11），完全隔离 | ❌ |

## 解决方案

### 方案一：每容器加 --add-host（最通用，但烦）

```bash
docker run -d --name activecode \
  --restart unless-stopped \
  --add-host host.docker.internal:host-gateway \
  -p 18080:8080 \
  -e SPRING_DATASOURCE_URL="jdbc:mysql://host.docker.internal:3306/tools?..." \
  activecode
```

`host-gateway` 会自动解析为 Docker 宿主机网关 IP（docker0 的网关，即宿主机）。

### 方案二：改应用配置用宿主机真实 IP（最干净）

直接把 `host.docker.internal` 换成宿主机真实 IP：

```bash
docker run -d --name activecode \
  --restart unless-stopped \
  -p 18080:8080 \
  -e SPRING_DATASOURCE_URL="jdbc:mysql://192.168.31.182:3306/tools?..." \
  -e SPRING_DATASOURCE_USERNAME=tools \
  -e SPRING_DATASOURCE_PASSWORD=toolsmarschat \
  activecode
```

### 方案三：docker-compose（适合多容器项目）

```yaml
services:
  activecode:
    image: activecode
    extra_hosts:
      - "host.docker.internal:host-gateway"
    ports:
      - "18080:8080"
    environment:
      - SPRING_DATASOURCE_URL=jdbc:mysql://host.docker.internal:3306/tools?...
```

### 方案四 ⭐：dnsmasq 全局接管（一台机器配一次，所有容器生效）

#### 原理

```
容器内查询 host.docker.internal
    ↓
Docker 内建 DNS → 172.17.0.1（docker0 网桥）
    ↓
dnsmasq（监听 docker0）→ 查到配置 → 返回 192.168.31.182
    ↓
容器拿到 IP，直连 MySQL
```

#### 配置步骤

```bash
# 1. 安装 dnsmasq
apt install -y dnsmasq

# 2. 创建配置（监听 docker0，解析 host.docker.internal → 宿主机IP）
cat > /etc/dnsmasq.d/docker-bridge <<'EOF'
interface=docker0
bind-interfaces
address=/host.docker.internal/192.168.31.182
server=192.168.31.1
no-hosts
no-resolv
EOF

# 3. 配置 Docker 全局 DNS 指向 dnsmasq
cat > /etc/docker/daemon.json <<'EOF'
{
  "dns": ["172.17.0.1", "8.8.8.8"]
}
EOF

# 4. 重启服务
systemctl restart dnsmasq
systemctl restart docker

# 5. 验证
docker run --rm busybox nslookup host.docker.internal
# 应返回 192.168.31.182
```

#### 效果

- ✅ **一台机器配一次，所有容器自动解析** `host.docker.internal` → 宿主机 IP
- ✅ 创建新容器**完全不需要** `--add-host` 或改环境变量
- ✅ 不影响宿主机自身 DNS
- ✅ 外部域名查询走 dnsmasq 正常转发

## 方案对比总结

| 方案 | 优点 | 缺点 | 适用场景 |
|------|------|------|---------|
| ① `--add-host` | 不改应用配置 | 每容器都要加 | 临时测试、单个容器 |
| ② 换宿主机 IP | 最干净，不依赖 Docker 特性 | 要改应用环境变量 | 自己能控制配置的项目 |
| ③ docker-compose | 声明式，可重复 | 要写 compose 文件 | 已有 compose 的项目 |
| ④ **dnsmasq 全局** | **一次配置，全机容器生效** | 要装 dnsmasq | **推荐**，适合容器多的机器 |

## 实战记录

- **内网 Debian（192.168.31.182）**：方案④已配置 ✅
- **activecode 容器**：原依赖 `--add-host`，现无需任何额外参数，`host.docker.internal` 自动解析 ✅
