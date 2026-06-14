
## 3.4 FRP 服务信息

| 项目 | 值 |
|------|-----|
| FRPS 服务端 | 120.26.66.182:7000 |
| FRPS 仪表盘 | http://120.26.66.182:7500 |
| 仪表盘用户名 | admin |
| 仪表盘密码 | MySecurePassword@2025 |
| FRP Token | YourStrongToken! |

> ⚠️ Token 在 `frps.ini` 和所有 `frpc.toml` 中必须一致

**服务管理**
- 阿里云：`systemctl restart frps`
- 内网：`systemctl restart frpc`

**隧道映射**
| 服务 | 公网端口 | 内网目标 | 状态 |
|------|---------|---------|------|
| RDP（内网旧 Windows） | **3381**（经腾讯云2号） | 127.0.0.1:3389 | ✅ frp.marschat.online:3381 |
| SSH（内网旧 Windows） | **3382**（经腾讯云2号） | 127.0.0.1:22 | ✅ frp.marschat.online:3382 |
| 新版激活码系统 | **18081** | 内网新 Windows 主机 | 🆕 新增 |
| SSH（内网 Debian） | **3383** | 127.0.0.1:22 | ✅ 阿里云→FRP隧道→内网 Debian |
| RDP（内网 Debian） | **3384** | 127.0.0.1:3389 | ❌ 已挂 |
| SSH（mykng-debain） | **3385** | 192.168.31.105:22 | ✅ 阿里云→FRP隧道→内网 Debian→mykng-debain（2026-06-12 新增）|
| 旧版激活码系统 | **18080** | 127.0.0.1:8080 | ❌ 已挂 |

