#!/bin/bash
set -euo pipefail

# 日志目录和文件路径
LOG_DIR="/var/log/frps"
LOG_FILE="$LOG_DIR/frps_install.log"

# 创建日志目录
mkdir -p "$LOG_DIR"
touch "$LOG_FILE"
chmod 755 "$LOG_DIR"
chmod 644 "$LOG_FILE"

exec > >(tee -a "$LOG_FILE") 2>&1

# 检查root权限
check_root() {
    if [ "$EUID" -ne 0 ]; then
        echo "错误: 必须使用root权限运行本脚本" | tee -a "$LOG_FILE"
        exit 1
    fi
}

# 清理所有相关文件
clean_all_files() {
    echo "▄ 清理所有相关文件..." | tee -a "$LOG_FILE"
    
    # 停止并删除服务
    if systemctl list-unit-files | grep -q frps.service; then
        echo "停止并删除FRP服务..." | tee -a "$LOG_FILE"
        systemctl stop frps || true
        systemctl disable frps || true
        rm -f /etc/systemd/system/frps.service
        systemctl daemon-reload
    fi
    
    # 删除程序文件
    echo "删除程序文件..." | tee -a "$LOG_FILE"
    rm -rf /usr/local/frp
    rm -rf /etc/frp
    
    # 删除日志文件
    echo "删除日志文件..." | tee -a "$LOG_FILE"
    rm -f "$LOG_DIR/frps.log"
    rm -f "$LOG_DIR/frps_error.log"
    
    # 删除临时文件
    echo "删除临时文件..." | tee -a "$LOG_FILE"
    rm -rf /tmp/frp*
    
    echo "✔️ 所有相关文件清理完成" | tee -a "$LOG_FILE"
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
allow_ports = 3381
log_file = /var/log/frps/frps.log
log_level = info
EOF
    echo "✔️ 配置文件已创建 (/etc/frp/frps.ini)" | tee -a "$LOG_FILE"
}

# 创建日志文件
create_log_files() {
    echo "▄ 创建日志文件..." | tee -a "$LOG_FILE"
    touch "$LOG_DIR/frps.log"
    touch "$LOG_DIR/frps_error.log"
    chmod 644 "$LOG_DIR/frps.log"
    chmod 644 "$LOG_DIR/frps_error.log"
    chown root:root "$LOG_DIR/frps.log"
    chown root:root "$LOG_DIR/frps_error.log"
    echo "✔️ 日志文件创建完成" | tee -a "$LOG_FILE"
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
StandardOutput=append:/var/log/frps/frps.log
StandardError=append:/var/log/frps/frps_error.log

[Install]
WantedBy=multi-user.target
EOF

    systemctl daemon-reload
    systemctl enable frps
    systemctl start frps
    echo "✔️ 服务已创建并启动" | tee -a "$LOG_FILE"
}

# 配置防火墙
configure_firewall() {
    echo "▄ 配置防火墙规则..." | tee -a "$LOG_FILE"
    
    # 检查是否安装了firewalld
    if command -v firewall-cmd &> /dev/null; then
        echo "使用firewalld配置防火墙..." | tee -a "$LOG_FILE"
        firewall-cmd --permanent --add-port=7000/tcp
        firewall-cmd --permanent --add-port=7500/tcp
        firewall-cmd --permanent --add-port=80/tcp
        firewall-cmd --permanent --add-port=443/tcp
        firewall-cmd --permanent --add-port=3381/tcp
        firewall-cmd --reload
    # 检查是否安装了iptables
    elif command -v iptables &> /dev/null; then
        echo "使用iptables配置防火墙..." | tee -a "$LOG_FILE"
        iptables -A INPUT -p tcp --dport 7000 -j ACCEPT
        iptables -A INPUT -p tcp --dport 7500 -j ACCEPT
        iptables -A INPUT -p tcp --dport 80 -j ACCEPT
        iptables -A INPUT -p tcp --dport 443 -j ACCEPT
        iptables -A INPUT -p tcp --dport 3381 -j ACCEPT
        
        # 尝试保存iptables规则
        if command -v iptables-save &> /dev/null; then
            iptables-save > /etc/sysconfig/iptables
        fi
    else
        echo "警告: 未检测到防火墙工具，请手动配置防火墙规则" | tee -a "$LOG_FILE"
    fi
    
    echo "✔️ 防火墙规则配置完成" | tee -a "$LOG_FILE"
}

# 验证服务状态
verify_install() {
    echo "▄ 验证安装..." | tee -a "$LOG_FILE"
    sleep 3
    STATUS=$(systemctl is-active frps)
    if [ "$STATUS" = "active" ]; then
        echo "✔️ FRP服务正在运行" | tee -a "$LOG_FILE"
        echo "▄ 检查端口监听状态:" | tee -a "$LOG_FILE"
        ss -tulnp | grep -E '7000|7500|3381' || true
    else
        echo "警告: 服务未正常启动，检查日志 /var/log/frps/frps_error.log" | tee -a "$LOG_FILE"
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
    echo "   - allow_ports: 允许的端口范围 (当前配置值为 3381)" | tee -a "$LOG_FILE"
    echo | tee -a "$LOG_FILE"
    echo "4. 防火墙已自动配置，开放了以下端口:" | tee -a "$LOG_FILE"
    echo "   - 7000: FRP服务端口" | tee -a "$LOG_FILE"
    echo "   - 7500: 管理面板端口" | tee -a "$LOG_FILE"
    echo "   - 80/443: HTTP/HTTPS端口" | tee -a "$LOG_FILE"
    echo "   - 3381: 远程桌面转发端口" | tee -a "$LOG_FILE"
    echo | tee -a "$LOG_FILE"
    echo "5. 日志文件位置:" | tee -a "$LOG_FILE"
    echo "   - 安装日志: $LOG_FILE" | tee -a "$LOG_FILE"
    echo "   - 运行日志: /var/log/frps/frps.log" | tee -a "$LOG_FILE"
    echo "   - 错误日志: /var/log/frps/frps_error.log" | tee -a "$LOG_FILE"
    echo | tee -a "$LOG_FILE"
    echo "6. 管理面板访问:" | tee -a "$LOG_FILE"
    echo "   - 地址: http://服务器IP:7500" | tee -a "$LOG_FILE"
    echo "   - 用户名: admin" | tee -a "$LOG_FILE"
    echo "   - 密码: MySecurePassword@2025" | tee -a "$LOG_FILE"
    echo | tee -a "$LOG_FILE"
    echo "完整日志见: $LOG_FILE"
}

main() {
    check_root
    clean_all_files  # 新增：在安装前清理所有相关文件
    install_deps
    get_latest_version
    install_frp
    create_config
    create_log_files
    create_service
    configure_firewall  # 新增：自动配置防火墙
    verify_install
    show_instructions
}

main
