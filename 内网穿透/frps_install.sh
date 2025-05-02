#!/bin/bash
set -euo pipefail

# 日志文件路径
LOG_FILE="/var/log/frps_install.log"
exec > >(tee -a "$LOG_FILE") 2>&1

# 检查root权限
check_root() {
    if [ "$EUID" -ne 0 ]; then
        echo "错误: 必须使用root权限运行本脚本" | tee -a "$LOG_FILE"
        exit 1
    fi
}

# 卸载旧版本FRP
uninstall_old() {
    echo "▄ 卸载旧版本FRP服务..." | tee -a "$LOG_FILE"
    if systemctl list-unit-files | grep -q frps.service; then
        systemctl stop frps || true
        systemctl disable frps || true
        rm -f /etc/systemd/system/frps.service
        systemctl daemon-reload
    fi
    rm -rf /usr/local/frp /etc/frp/frps.ini
    echo "✔️ 旧版本卸载完成" | tee -a "$LOG_FILE"
}

# 安装基础依赖
install_deps() {
    echo "▄ 安装必要依赖..." | tee -a "$LOG_FILE"
    yum install -y curl wget unzip || {
        echo "错误: 依赖安装失败" | tee -a "$LOG_FILE"
        exit 1
    }
    echo "✔️ 依赖安装成功" | tee -a "$LOG_FILE"
}

# 获取最新稳定版FRP
get_latest_version() {
    echo "▄ 获取最新稳定版本..." | tee -a "$LOG_FILE"
    VERSION=$(curl -sL "https://api.github.com/repos/fatedier/frp/releases/latest" | grep '"tag_name":' | sed -E 's/.*"([^"]+)".*/\1/') || VERSION="v0.62.1"
    echo "▄ 使用版本: $VERSION" | tee -a "$LOG_FILE"
}

# 下载并安装FRP
install_frp() {
    echo "▄ 下载并安装FRP..." | tee -a "$LOG_FILE"
    DOWNLOAD_URL="https://github.com/fatedier/frp/releases/download/${VERSION}/frp_${VERSION#v}_linux_amd64.tar.gz"
    wget -O /tmp/frp.tar.gz "$DOWNLOAD_URL" || {
        echo "错误: 下载失败，请检查网络" | tee -a "$LOG_FILE"
        exit 1
    }
    tar zxvf /tmp/frp.tar.gz -C /tmp/
    mkdir -p /usr/local/frp /etc/frp
    cp "/tmp/frp_${VERSION#v}_linux_amd64/frps" /usr/local/frp/
    chmod +x /usr/local/frp/frps
    echo "✔️ FRP安装成功" | tee -a "$LOG_FILE"
}

# 创建配置文件
create_config() {
    echo "▄ 生成配置文件..." | tee -a "$LOG_FILE"
    cat > /etc/frp/frps.ini <<EOF
[common]
bind_port = 7000
dashboard_port = 7500
dashboard_user = admin
dashboard_pwd = MySecurePassword@2025
token = YourStrongToken!
vhost_http_port = 80
vhost_https_port = 443

EOF
    echo "✔️ 配置文件已创建 (/etc/frp/frps.ini)" | tee -a "$LOG_FILE"
}

# 创建系统服务
create_service() {
    echo "▄ 创建系统服务..." | tee -a "$LOG_FILE"
    cat > /etc/systemd/system/frps.service <<EOF
[Unit]
Description=Frp Server Service
After=network.target

[Service]
Type=simple
User=root
ExecStart=/usr/local/frp/frps -c /etc/frp/frps.ini
Restart=always
RestartSec=10
StandardOutput=append:/var/log/frps.log
StandardError=append:/var/log/frps_error.log

[Install]
WantedBy=multi-user.target
EOF

    systemctl daemon-reload
    systemctl enable frps
    systemctl start frps
    echo "✔️ 服务已创建并启动" | tee -a "$LOG_FILE"
}

# 验证服务状态
verify_install() {
    echo "▄ 验证安装..." | tee -a "$LOG_FILE"
    sleep 3
    STATUS=$(systemctl is-active frps)
    if [ "$STATUS" = "active" ]; then
        echo "✔️ FRP服务正在运行" | tee -a "$LOG_FILE"
        echo "▄ 检查端口监听状态:" | tee -a "$LOG_FILE"
        ss -tulnp | grep -E '7000|7500' || true
    else
        echo "警告: 服务未正常启动，检查日志 /var/log/frps_error.log" | tee -a "$LOG_FILE"
    fi
}

# 输出后续指引
show_instructions() {
    echo -e "\n\033[1;36m● 安装完成后的重要操作指引 ●\033[0m" | tee -a "$LOG_FILE"
    echo "1. 需要调整配置时，编辑配置文件：" | tee -a "$LOG_FILE"
    echo "   vim /etc/frp/frps.ini" | tee -a "$LOG_FILE"
    echo | tee -a "$LOG_FILE"
    echo "2. 修改配置后的重启命令：" | tee -a "$LOG_FILE"
    echo "   systemctl restart frps" | tee -a "$LOG_FILE"
    echo | tee -a "$LOG_FILE"
    echo "3. 关键配置说明：" | tee -a "$LOG_FILE"
    echo "   - bind_port: 客户端连接端口 (默认7000)" | tee -a "$LOG_FILE"
    echo "   - dashboard_port/web界面管理端口 (7500)" | tee -a "$LOG_FILE"
    echo "   - token需要与客户端保持一致 (当前配置值为 YourStrongToken!)" | tee -a "$LOG_FILE"
    echo | tee -a "$LOG_FILE"
    echo "4. 防火墙需放行端口:" | tee -a "$LOG_FILE"
    echo "   firewall-cmd --permanent --add-port={7000,7500,80,443}/tcp" | tee -a "$LOG_FILE"
    echo "   firewall-cmd --reload" | tee -a "$LOG_FILE"
    echo | tee -a "$LOG_FILE"
    echo "完整日志见: $LOG_FILE"
}

main() {
    check_root
    uninstall_old
    install_deps
    get_latest_version
    install_frp
    create_config
    create_service
    verify_install
    show_instructions
}

main
