# 内网穿透使用说明文档

## 概述

本项目提供了一套基于 FRP (Fast Reverse Proxy) 实现内网穿透的工具和配置文件，帮助您将内网服务暴露到公网，或实现点对点(P2P)的安全访问。

## 准备工作

1.  **一台拥有公网 IP 的服务器**：用于运行 FRP 服务端 (frps)。
2.  **内网中的机器**：需要暴露其服务的机器，运行 FRP 客户端 (frpc)。
3.  **访问端机器** (可选，用于 P2P)：需要访问内网服务的机器，也运行 FRP 客户端 (frpc) 作为访问者。

## 一、服务端部署 (Linux 服务器)

1.  **上传脚本和配置文件**：
    *   将 `frps_install.sh` 脚本上传到您的 Linux 服务器。
    *   （可选）如果您需要自定义服务端配置，可以先修改 `配置文件\服务上的配置文件\frps.ini`，然后将其上传到服务器的 `/etc/frp/` 目录 (脚本会自动创建此目录并放入默认配置)。

2.  **执行安装脚本**：
    在服务器上，给予脚本执行权限并运行：
    ```bash
    chmod +x frps_install.sh
    sudo ./frps_install.sh
    ```
    该脚本会自动完成以下操作：
    *   检查并安装依赖。
    *   下载最新版 FRP。
    *   创建配置文件 `/etc/frp/frps.ini` (如果不存在)。
    *   设置 frps 为 systemd 服务并启动。
    *   配置防火墙 (如果检测到 firewalld 或 iptables)。

3.  **检查服务端状态**：
    ```bash
    sudo systemctl status frps
    ```
    确保服务正在运行。您可以查看日志 `/var/log/frps/frps.log`。

4.  **关键服务端配置 (`/etc/frp/frps.ini`)**：
    *   `bind_port = 7000`: FRP 客户端连接服务端的端口。
    *   `dashboard_port = 7500`: FRP 服务端仪表盘的访问端口。
    *   `dashboard_user` 和 `dashboard_pwd`: 仪表盘的登录凭据 (默认为 `admin` / `MySecurePassword@2025`)。
    *   `token = YourStrongToken!`: 客户端与服务端认证的令牌，请务必修改为强密码。
    *   `allow_ports`: 允许客户端穿透的端口列表，例如 `3381` 用于远程桌面。

## 二、内网客户端部署 (Windows - 服务提供方)

这台是您希望将其服务（如远程桌面）暴露出去的内网 Windows 机器。

1.  **运行安装脚本**：
    *   将 `frpc_install.bat` 脚本复制到内网 Windows 机器上。
    *   **以管理员身份运行** `frpc_install.bat`。
    该脚本会自动完成以下操作：
    *   下载 FRP 客户端程序。
    *   创建默认配置文件 `C:\frp\frpc.ini`。
    *   使用 NSSM 将 frpc 注册为 Windows 服务并启动。
    *   配置防火墙允许 frpc 通信。

2.  **修改客户端配置 (`C:\frp\frpc.ini`)**：
    打开 `C:\frp\frpc.ini` 文件，根据您的实际情况修改：
    ```ini
    [common]
    server_addr = 你的公网服务器IP地址
    server_port = 7000 ; 与服务端 frps.ini 中的 bind_port 一致
    token = YourStrongToken! ; 与服务端 frps.ini 中的 token 一致
    log_file = C:/frp/frpc.log
    log_level = info

    ; 示例1: 暴露本地远程桌面 (TCP 模式)
    [rdp]
    type = tcp
    local_ip = 127.0.0.1
    local_port = 3389      ; 本地远程桌面端口
    remote_port = 3381     ; 公网服务器上映射的端口 (需在服务端 allow_ports 中允许)

    ; 示例2: 暴露本地远程桌面 (XTCP P2P 模式，性能更好，但需访问端也配置frpc)
    [rdp-xtcp]
    type = xtcp
    local_ip = 127.0.0.1
    local_port = 3389      ; 本地远程桌面端口
    sk = YourSecretKey123  ; P2P 连接的密钥，请修改为强密码
    ; use_encryption = true ; 可选，启用加密
    ; use_compression = true ; 可选，启用压缩
    ```

3.  **重启 FRP 客户端服务**：
    修改配置后，需要重启服务使配置生效：
    *   打开 Windows 服务管理器 (services.msc)。
    *   找到 "FRP客户端服务" (或名为 "frpc" 的服务)。
    *   右键点击并选择 "重新启动"。
    或者使用命令：
    ```bash
    net stop frpc
    net start frpc
    ```

## 三、访问端配置 (可选，用于 XTCP P2P 模式)

如果您在内网客户端配置了 XTCP 类型的代理 (如 `[rdp-xtcp]`)，并且希望通过 P2P 方式直接连接，那么访问端也需要配置 `frpc`。

1.  **准备 `frpc` 程序和配置文件**：
    *   在访问端机器上，您可以从 FRP 的 GitHub Release 页面下载对应操作系统的 `frpc` 程序。
    *   创建一个 `frpc.ini` 配置文件，内容参考 `配置文件\需要访问内网机器的配置文件\frpc.ini`。

2.  **修改访问端 `frpc.ini` 配置**：
    ```ini
    [common]
    server_addr = 你的公网服务器IP地址
    server_port = 7000
    token = YourStrongToken! ; 与服务端 frps.ini 中的 token 一致
    log_file = ./frpc_visitor.log ; 日志路径可自定义
    log_level = info

    [rdp-visitor] ; 名称可以自定义，但 server_name 需要匹配
    type = xtcp
    role = visitor
    server_name = rdp-xtcp   ; 必须与内网客户端 frpc.ini 中暴露的 xtcp 服务名称一致
    sk = YourSecretKey123    ; 必须与内网客户端 frpc.ini 中对应 xtcp 服务的 sk 一致
    bind_addr = 127.0.0.1    ; 访问端本地监听地址
    bind_port = 6000         ; 访问端本地监听端口，访问此端口即访问内网服务
    ; use_encryption = true
    ; use_compression = true
    ```

3.  **运行访问端 `frpc`**：
    在访问端机器的命令行中运行：
    ```bash
    ./frpc -c ./frpc.ini
    ```
    (请确保 `frpc` 可执行文件和 `frpc.ini` 在当前目录，或使用绝对路径)

## 四、如何访问

*   **TCP 模式** (例如上述 `[rdp]` 配置)：
    直接通过 `公网服务器IP:remote_port` 访问。
    例如，远程桌面连接到 `你的公网服务器IP:3381`。

*   **XTCP P2P 模式** (例如上述 `[rdp-xtcp]` 和 `[rdp-visitor]` 配置)：
    在访问端运行 `frpc` 后，通过访问端 `frpc.ini` 中配置的 `bind_addr:bind_port` 来访问内网服务。
    例如，远程桌面连接到 `127.0.0.1:6000` (在访问端机器上)。

## 五、其他说明

*   **安全性**：
    *   务必修改 `frps.ini` 和 `frpc.ini` 中的 `token` 为一个强密码。
    *   务必修改 XTCP 配置中的 `sk` (secret key) 为一个强密码。
    *   定期更新 FRP 到最新版本。
*   **日志文件**：
    *   服务端日志: `/var/log/frps/frps.log` 和 `/var/log/frps/frps_error.log`
    *   内网客户端日志: `C:\frp\frpc.log` (或您在 `frpc.ini` 中配置的路径)
    *   访问端客户端日志: 您在访问端 `frpc.ini` 中配置的路径
*   **NSSM**：Windows 客户端安装脚本使用 NSSM 将 `frpc` 注册为服务。NSSM 是一个服务管理工具。
*   **脚本**：
    *   `start_frpc.bat`: 用于在 Windows 上手动启动/重启 `frpc` 服务或直接运行 `frpc.exe`。

希望这份文字说明能帮助您更好地理解和使用此内网穿透方案！