---
name: "infrastructure-status"
description: "实时查询所有主机的基础能力信息（Docker容器、监听端口、运行服务、系统资源、定时任务等）。本技能别名：基础设施查询能力 / 基础信息查询能力 / 基础能力信息查询。Invoke when user asks about host deployments, application inventory, port usage, service status, or infrastructure overview."
---

# 基础能力信息实时查询

> **技能别名**：基础设施查询能力 / 基础信息查询能力 / 基础能力信息查询
> **铁律：以实测为准，不信静态文档。** 文档可能过时，实测才是真相。

## 一、触发场景

当用户询问以下问题时，**必须调用此技能**：
- "主机上都部署了什么应用/组件"
- "某台机器跑了哪些服务"
- "XX端口被谁占用了"
- "主机资源使用情况"
- "哪些容器在运行/挂了"
- "基础能力信息" / "基础能力信息查询"
- "基础设施" / "基础设施查询" / "基础设施查询能力"
- "基础信息" / "基础信息查询" / "基础信息查询能力"
- "应用清单" / "服务清单"
- 任何涉及主机部署状态的查询

## 二、主机清单（7 台）

### Linux 主机（5 台，SSH 可查）

| 主机名 | 公网 IP | Tailscale IP | 角色 | SSH 用户 |
|--------|---------|--------------|------|----------|
| 龙虾 | 49.51.245.134 | 100.122.231.95 | OpenClaw + SS 代理 + Nginx 下载 | root |
| 腾讯云2号 | 1.117.70.30 | 100.110.114.16 | 公网 Nginx 入口 + Nexus + Clash | root |
| 阿里云 | 120.26.66.182 | 100.89.102.74 | FRP 服务端 + Clash + FRP管理平台 | root |
| 内网Debian | 192.168.31.182 | 100.105.196.63 | Hive + RAG + 激活码 + FRP客户端 | root |
| mykng | 192.168.31.105 | 100.93.36.113 | 知识库部署 + CI/CD + Nexus + Vaultwarden | root |

### Windows 主机（2 台，仅本机可查）

| 主机名 | IP | 角色 | 查询方式 |
|--------|-----|------|---------|
| 旧Windows | 192.168.31.243 | VirtualBox 宿主（跑 mykng-debain）| RDP，无 SSH |
| 新Windows | 192.168.31.77 | SMB 共享 + 本机开发 | 本机 PowerShell 直查 |

## 三、SSH 连接方式

**优先级链：Tailscale > 公网 > FRP 隧道**

所有 Linux 主机已配置免密登录（ed25519 密钥），直接 `ssh root@<IP>` 即可。

```bash
# 公网直连（首选）
ssh root@49.51.245.134       # 龙虾
ssh root@1.117.70.30         # 腾讯云2号
ssh root@120.26.66.182       # 阿里云
ssh root@192.168.31.182      # 内网Debian（局域网）
ssh root@192.168.31.105      # mykng（局域网）

# Tailscale 备用（公网不通时）
ssh root@100.122.231.95      # 龙虾
ssh root@100.110.114.16      # 腾讯云2号
ssh root@100.89.102.74       # 阿里云
ssh root@100.105.196.63      # 内网Debian
ssh root@100.93.36.113       # mykng

# FRP 隧道（Tailscale 也挂了时）
ssh -p 3383 root@120.26.66.182   # 内网 Debian
ssh -p 3385 root@120.26.66.182   # mykng
```

**Windows OpenSSH 间歇性 bug**：连接瞬间失败（`socket() ERROR:10038`）时，重试 2-3 次即可，非配置问题。

## 四、标准化查询脚本（Linux 主机）

对每台 Linux 主机执行以下综合查询，**一次 SSH 获取全部信息**：

```bash
ssh root@<IP> 'bash -s' << 'EOF'
echo "========== 系统信息 =========="
hostname
uname -r
cat /etc/os-release 2>/dev/null | grep PRETTY_NAME
uptime
echo

echo "========== CPU/内存/磁盘 =========="
free -h
echo "--- 磁盘 ---"
df -h | grep -vE 'tmpfs|overlay'
echo "--- 负载 ---"
cat /proc/loadavg
echo

echo "========== Docker 容器（运行中）=========="
docker ps --format 'table {{.Names}}\t{{.Image}}\t{{.Ports}}\t{{.Status}}' 2>/dev/null
echo

echo "========== Docker 容器（异常/已停止）=========="
docker ps -a --filter "status=exited" --filter "status=restarting" --filter "status=dead" --format 'table {{.Names}}\t{{.Image}}\t{{.Status}}' 2>/dev/null
echo

echo "========== Docker 镜像 =========="
docker images --format 'table {{.Repository}}\t{{.Tag}}\t{{.Size}}' 2>/dev/null | head -30
echo

echo "========== 监听端口（TCP）=========="
ss -tlnp 2>/dev/null
echo

echo "========== 监听端口（UDP）=========="
ss -ulnp 2>/dev/null | head -20
echo

echo "========== 关键进程 =========="
ps aux 2>/dev/null | grep -E 'java|python|node|nginx|redis|mysql|mongo|clash|frp|qdrant|hive|nexus|vault|woodpecker|drone' | grep -v grep
echo

echo "========== systemd 运行服务 =========="
systemctl list-units --type=service --state=running --no-pager 2>/dev/null | grep -vE 'systemd|dbus|getty|user@|session|cron|sshd|networking|polkit|rsyslog|udev|fwupd|udisks|accounts|ModemManager|NetworkManager|wpa_supplicant|cups|snapd|unattended|packagekit|bolt|colord|switcheroo|power|thermald|irqbalance|apport|multipathd|openvpn|chrony|systemd-' | head -40
echo

echo "========== 定时任务 =========="
echo "--- root crontab ---"
crontab -l 2>/dev/null
echo "--- /etc/cron.d/ ---"
ls /etc/cron.d/ 2>/dev/null
echo "--- systemd timers ---"
systemctl list-timers --no-pager 2>/dev/null | head -15
echo

echo "========== 网络连接数 =========="
ss -s 2>/dev/null
echo

echo "========== 最近重启的容器（24h内）=========="
docker ps --format '{{.Names}} {{.RunningFor}}' 2>/dev/null | grep -iE 'minute|hour'
EOF
```

## 五、并行查询策略

**必须并行 SSH 所有 5 台 Linux 主机**，在单条消息中发起 5 个 RunCommand 调用，最大化效率。

查询完成后，**对照"预期服务清单"**（见第六节）逐台核对，找出：
- ❌ 预期运行但实际未运行的服务
- ⚠️ 异常状态（restarting/exited/dead）的容器
- 🔴 安全隐患（如 :2375 暴露、弱密码、未授权访问）
- 📊 资源告警（磁盘 >80%、内存 >90%、负载 >CPU 核数）

## 六、各主机预期服务清单（用于对照实测）

### 龙虾（49.51.245.134）
- Docker: shadowsocks
- 服务: nginx, smbd, embedding-proxy, cron, docker
- 端口: 22, 80, 443, 139, 445, 8388, 8787(RAG MCP?)

### 腾讯云2号（1.117.70.30）
- 无 Docker 容器
- 服务: nexus(systemd), nginx, nginx-ui, clash-meta, cron, docker
- 端口: 22, 80, 443, 3381, 3382, 51820, 8081-8083(Nexus), 8087, 9000(nginx-ui), 9999

### 阿里云（120.26.66.182）
- Docker: frp-manager, frp-manager-frontend
- 服务: frps, nginx, clash-meta, aria2c, tinyproxy, crond, docker
- 端口: 22, 80, 443, 111, 6800, 7000, 7500, 7890, 8899, 8888, 8889, 18080-18085, 3381-3385

### 内网Debian（100.105.196.63）
- Docker: activecode, frp-manager, rag-qdrant, rag-embedding, hive-metastore, hive-mysql, hive-server2(易挂)
- 服务: apache2, clash-meta, frpc, xrdp, dnsmasq, cron, docker
- 端口: 22, 80, 3306, 6333-6334, 7890, 8081, 18080, 18082, 3389, 10809, 8890

### mykng（100.93.36.113）
- Docker: kb-mysql, nexus, woodpecker-server, woodpecker-agent, woodpecker-db, vaultwarden, kb-web, kb-ops
- 服务: nginx, clash-meta, frpc, cron, docker
- 端口: 22, 80, 3306, 5433, 631, 7890, 8000, 8080-8084, 8087, 8091, 8222, 8787, 9002, 3456
- **注意**：文档提到 kb-redis/kb-meilisearch/kb-nacos/kb-minio/kb-mongo/kb-gateway/drone-server/infra-monitor，但实测多未运行，需实时确认

## 七、输出格式要求

查询结果必须按以下结构输出：

### 1. 主机状态总表
```
| 主机 | 公网IP | 系统 | 容器数 | 运行端口数 | 异常 | 资源告警 |
```

### 2. 各主机详情
- Docker 容器表（名称/镜像/端口/状态）
- 监听端口表（端口/进程/用途）
- 运行服务列表
- 资源使用情况

### 3. 异常清单（重点突出）
- 🔴 安全隐患
- ⚠️ 服务异常
- 📊 资源告警

### 4. 组件分布矩阵
```
| 组件 | 龙虾 | 腾讯云2号 | 阿里云 | 内网Debian | mykng |
```

## 八、Windows 主机查询（本机）

对 Windows 主机（192.168.31.77 本机），用 PowerShell 查询：

```powershell
# 监听端口
Get-NetTCPConnection -State Listen | Select-Object LocalAddress,LocalPort,OwningProcess | Sort-Object LocalPort

# 运行的服务
Get-Service | Where-Object {$_.Status -eq 'Running'} | Select-Object Name,DisplayName

# 磁盘
Get-PSDrive -PSProvider FileSystem | Select-Object Name,Used,Free

# SMB 共享
Get-SmbShare
```

## 九、安全检查项

每次查询时，**必须检查以下安全隐患**并标注：

| 检查项 | 判定标准 | 处理建议 |
|--------|---------|---------|
| Docker daemon 暴露 | :2375 对公网监听 | 🔴 改用 TLS 或仅监听 127.0.0.1 |
| 弱密码服务 | MySQL/Redis/Mongo 无密码或弱密码 | 🔴 加密码或改仅本机监听 |
| FRP 仪表盘未授权 | :7500 无密码可访问 | 🔴 配置密码 |
| 未加密的 HTTP 服务 | :80 暴露敏感应用 | ⚠️ 加 HTTPS |
| 容器重启循环 | 状态 = Restarting | ⚠️ 查日志修复 |
| 磁盘空间不足 | 使用率 > 80% | 📊 清理 |
| 内存不足 | 可用 < 10% | 📊 扩容或清理 |
| 负载过高 | load > CPU 核数 | 📊 排查进程 |

## 十、维护说明

- 本技能的"预期服务清单"会随部署变化而过时，**以实测结果为准**
- 如发现实测与预期严重不符，应提示用户更新本技能的预期清单
- 新增主机时，在第二节主机清单和第六节预期清单中补充
