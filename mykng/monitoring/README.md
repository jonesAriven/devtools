# 📊 mykng 监控平台

> 基于 **Prometheus + Grafana + Loki** 的完整可观测性平台  
> 包含：**指标监控 + 日志聚合 + 告警通知**

---

## 🚀 快速开始（3 步启动）

### 前提条件

- ✅ Docker 已安装并运行
- ✅ Docker Compose V2 已安装
- ✅ 服务器内存 ≥ 2GB（推荐 4GB）

### Step 1: 进入目录

```bash
cd mykng/monitoring/
```

### Step 2: 启动服务

```bash
# Linux/Mac
./start-monitoring.sh start

# Windows (PowerShell)
.\start-monitoring.ps1 -Action Start

# 或直接使用 Docker Compose
docker compose -f docker-compose.monitoring.yml up -d
```

### Step 3: 访问看板

| 服务 | 地址 | 账号密码 |
|------|------|----------|
| **Grafana 看板** | http://<你的IP>:3000 | admin / admin |
| **Prometheus** | http://<你的IP>:9090 | 无需登录 |
| **AlertManager** | http://<你的IP>:9093 | 无需登录 |

---

## 📋 功能清单

| 功能 | 组件 | 说明 |
|------|------|------|
| 📈 **指标监控** | Prometheus + Node Exporter | CPU、内存、磁盘、网络 |
| 🐳 **容器监控** | cAdvisor | Docker 容器资源使用情况 |
| 📝 **日志查看** | Loki + Promtail | 容器部署日志、应用运行日志 |
| 🔔 **告警通知** | AlertManager | 邮件、钉钉、企业微信等 |
| 🖥️ **可视化大屏** | Grafana | 专业 Dashboard，支持自定义 |

---

## 🎨 推荐的 Grafana Dashboard

启动后，在 Grafana 中导入以下 Dashboard（社区验证的高质量模板）：

| ID | 名称 | 用途 | 导入方式 |
|----|------|------|----------|
| **1860** | Node Exporter Full | 服务器全面监控 | Import → 输入 1860 → Load |
| **193** | Docker & Container Monitoring | Docker 容器监控 | 同上 |
| **4701** | JVM (Micrometer) | Spring Boot 应用监控 | 同上 |
| **13639** | Loki Logging | 日志搜索和查看 | 同上 |
| **9578** | AlertManager Overview | 告警状态总览 | 同上 |

### 导入步骤

1. 打开 Grafana → 左侧菜单 ☰ → **Dashboards** → **Import**
2. 输入 Dashboard ID（如 `1860`）→ 点击 **Load**
3. 选择数据源为 **Prometheus** → 点击 **Import**
4. 完成！🎉

---

## 🏗️ 架构图

```
┌─────────────────────────────────────────────────────────────┐
│                     数据采集层                                │
│                                                             │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │ Node Exporter │  │   cAdvisor   │  │   Promtail   │      │
│  │ (服务器指标)   │  │ (容器指标)    │  │ (日志采集)    │      │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘      │
│         ↓                 ↓                 ↓               │
├─────────────────────────────────────────────────────────────┤
│                     存储处理层                                │
│                                                             │
│  ┌──────────────┐                  ┌──────────────┐         │
│  │  Prometheus  │                  │     Loki     │         │
│  │ (时序数据库)  │                  │ (日志存储)    │         │
│  └──────┬───────┘                  └──────┬───────┘         │
│         ↓                                 ↓                 │
├─────────────────────────────────────────────────────────────┤
│                     可视化 & 告警层                             │
│                                                             │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐       │
│  │   Grafana    │←→│ AlertManager │  │  告警通知     │       │
│  │ (统一看板)    │  │ (告警管理)    │  │ (邮件/钉钉)  │       │
│  └──────────────┘  └──────────────┘  └──────────────┘       │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## 📁 目录结构

```
monitoring/
├── docker-compose.monitoring.yml    # 主编排文件（一键启动）
├── config/
│   ├── prometheus.yml              # Prometheus 采集配置
│   ├── alert_rules.yml             # 告警规则定义
│   ├── loki.yml                    # Loki 日志系统配置
│   ├── promtail.yml                # Promtail 日志采集配置
│   ├── alertmanager.yml            # 告警通知渠道配置
│   └── grafana/
│       └── provisioning/
│           ├── datasources/
│           │   └── datasource.yml   # 自动创建数据源
│           └── dashboards/
│               └── dashboard.yml    # Dashboard 加载配置
├── start-monitoring.sh             # Linux/Mac 管理脚本
├── start-monitoring.ps1            # Windows 管理脚本
└── README.md                       # 本文件
```

---

## ⚙️ 管理命令

### 使用管理脚本（推荐）

```bash
# Linux/Mac
./start-monitoring.sh start          # 启动
./start-monitoring.sh stop           # 停止
./start-monitoring.sh restart        # 重启
./start-monitoring.sh status         # 查看状态
./start-monitoring.sh logs grafana    # 查看日志
./start-monitoring.sh reset          # ⚠️ 清除所有数据

# Windows (PowerShell)
.\start-monitoring.ps1 -Action Start
.\start-monitoring.ps1 -Action Status
.\start-monitoring.ps1 -Action Logs -Service grafana
```

### 直接使用 Docker Compose

```bash
# 启动
docker compose -f docker-compose.monitoring.yml up -d

# 查看状态
docker compose -f docker-compose.monitoring.yml ps

# 查看日志
docker compose -f docker-compose.monitoring.yml logs -f grafana

# 停止
docker compose -f docker-compose.monitoring.yml down

# 重置（删除数据）
docker compose -f docker-compose.monitoring.yml down -v
```

---

## 🔧 配置说明

### 修改告警通知渠道

编辑 `config/alertmanager.yml`：

```yaml
receivers:
  - name: 'critical-alerts'
    webhook_configs:
      # 钉钉机器人
      - url: 'https://oapi.dingtalk.com/robot/send?access_token=YOUR_TOKEN'
      
      # 企业微信
      # - url: 'https://qyapi.weixin.qq.com/cgi-bin/webhook?key=YOUR_KEY'
```

### 修改数据保留时间

| 组件 | 配置位置 | 默认值 | 说明 |
|------|----------|--------|------|
| Prometheus | `docker-compose.monitoring.yml` | 30 天 | `--storage.tsdb.retention.time=30d` |
| Loki | `config/loki.yml` | 30 天 | `retention_period: 720h` |

### 添加 Spring Boot 应用监控

确保你的应用已启用 Actuator：

```yaml
# application.yml
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus,metrics
  metrics:
    tags:
      application: ${spring.application.name}
    export:
      prometheus:
        enabled: true
```

然后在 `config/prometheus.yml` 中添加对应的 Job。

---

## 🔒 安全建议（生产环境必读）

⚠️ **当前配置仅用于开发/测试环境！生产环境请务必：**

1. **修改默认密码**
   ```yaml
   # docker-compose.monitoring.yml
   environment:
     - GF_SECURITY_ADMIN_PASSWORD=<强密码>
   ```

2. **启用 HTTPS/TLS**
   - 使用 Nginx/Caddy 反向代理
   - 配置 SSL 证书

3. **限制访问 IP**
   ```yaml
   # docker-compose.monitoring.yml
   ports:
     - "127.0.0.1:3000:3000"    # 仅本地访问
     # 或使用防火墙规则
   ```

4. **关闭匿名统计**
   ```yaml
   - GF_ANALYTICS_REPORTING_ENABLED=false  # 已默认关闭 ✓
   ```

5. **定期备份数据**
   ```bash
   # 备份 Grafana 配置和数据
   docker exec kb-grafana tar czf - /var/lib/grafana > backup-grafana.tar.gz
   
   # 备份 Prometheus 数据
   docker exec kb-prometheus tar czf - /prometheus > backup-prometheus.tar.gz
   ```

---

## ❓ 常见问题

### Q1: Grafana 无法连接到 Prometheus？

检查网络：
```bash
docker network ls | grep monitoring
docker exec kb-grafana wget -qO- http://prometheus:9090/-/healthy
```

### Q2: 日志没有显示？

检查 Promtail 是否正常运行：
```bash
docker compose -f docker-compose.monitoring.yml logs promtail
```

确认日志路径存在：
```bash
ls -la /var/lib/docker/containers/*/*.log
```

### Q3: 内存占用过高？

调整保留时间和采样间隔：
```yaml
# prometheus.yml
global:
  scrape_interval: 30s    # 从 15s 改为 30s

# loki.yml
limits_config:
  retention_period: 168h  # 从 30 天改为 7 天
```

### Q4: 如何扩容到多台服务器？

在每台服务器上部署 Node Exporter 和 cAdvisor，然后修改 `prometheus.yml` 添加多个 target：

```yaml
static_configs:
  - targets:
      - 'server1:9100'
      - 'server2:9100'
      - 'server3:9100'
```

---

## 📚 参考链接

- [Prometheus 官方文档](https://prometheus.io/docs/)
- [Grafana 文档](https://grafana.com/docs/)
- [Loki 文档](https://grafana.com/docs/loki/latest/)
- [Node Exporter Collector](https://github.com/prometheus/node_exporter#collectors)
- [Grafana Dashboard 市场](https://grafana.com/grafana/dashboards/)

---

## 📄 License

MIT License - 可自由使用和修改

---

**最后更新**: 2026-07-04  
**维护者**: huliang
