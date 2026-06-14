
## 3.13 SMB 共享目录（Windows 服务端）

> 部署日期：2026-05-26
> 用途：跨 Windows / Linux 实时共享目录，所有机器可读写
> 服务端已从内网 Debian Samba 迁移到内网新 Windows SMB

### 服务信息

| 项目 | 值 |
|------|-----|
| 服务端 | 内网新 Windows（192.168.31.77） |
| Tailscale IP | `100.70.76.54` |
| 共享路径 | `D:\huliang\java\ideaworkspace` |
| 共享名 | `ideaworkspace` |
| 用户 | `share` |
| 密码 | `share123` |
| 容量 | 112G（可用 80G） |

### 各机器访问路径

| 机器 | 路径 | 说明 |
|------|------|------|
| 🪟 Windows（局域网内） | `\\192.168.31.77\ideaworkspace` | 直接访问，千兆速度 |
| 🪟 Windows（Tailscale 远程） | `\\100.70.76.54\ideaworkspace` | 通过 VPN 访问 |
| 🦐 龙虾主机 | `/mnt/shared` | mount.cifs，✅ **文件共享目录** — 日常读写 |
| 🦐 龙虾主机 | `/mnt/0000sharebak`（新增） | mount.cifs，✅ **数据备份共享目录** — 定时任务备份专用 |
| ☁️ 腾讯云2号 | `/mnt/shared` | mount.cifs，✅ 开机自挂 🆕 |
| ☁️ 阿里云 | `/mnt/shared` | ⚠️ **实际上挂的是内网Debian旧Samba**（`//100.105.196.63/shared`），**不是**新Windows SMB。云机器之间传文件阿里云端走的是Debian共享，和龙虾/旧Windows看到的不同目录
| 📦 内网 Debian | `/mnt/shared` | mount.cifs（走局域网直连），✅ 开机自挂 |
| 🖥️ mykng-debain | `/mnt/shared` | ✅ **应用共享目录**，192.168.31.77 局域网直连，fstab 开机自挂（2026-06-12）|
| 🖥️ mykng-debain | `/mnt/0000sharebak` | ✅ **备份共享目录**，192.168.31.77 局域网直连，fstab 开机自挂（2026-06-12）|

> **文件中转：** 文件共享目录下有个 `temp` 文件夹（`/mnt/shared/temp`），云主机之间传文件慢的话，先丢到这中转，走 Tailscale 局域网快很多。

> **别名对照：**
> - `文件共享目录` → `/mnt/shared` — 日常读写、文件传输、开发源码都放这
> - `数据备份共享目录` → `/mnt/0000sharebak` — 定时任务备份数据专用，平时不动它

> 旧内网 Debian Samba 服务已停用，不再作为共享服务端。

### fstab 配置（如需新增客户端）

**云机器（通过 Tailscale）：**
```bash
//100.70.76.54/ideaworkspace  /mnt/shared  cifs  username=share,password=share123,vers=3.0,_netdev  0  0
```

**家庭内网机器（局域网直连）：**
```bash
//192.168.31.77/ideaworkspace  /mnt/shared  cifs  username=share,password=share123,vers=3.0,_netdev  0  0
```

