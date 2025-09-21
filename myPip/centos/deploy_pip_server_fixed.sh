#!/bin/bash

# 在Linux CentOS下部署pip私服的自动化脚本（修复版）
# 使用pypi-server搭建简易的pip私服

# 颜色定义
green='\033[0;32m'
red='\033[0;31m'
NC='\033[0m' # 无颜色

# 检查是否以root用户运行
if [ "$(id -u)" != "0" ]; then
   echo -e "${red}此脚本必须以root用户运行${NC}"
   exit 1
fi

# 定义变量
PYPI_SERVER_PORT=8080
PYPI_SERVER_USER="pypi"
PYPI_SERVER_DIR="/data/pypi-server"
PYPI_PACKAGES_DIR="${PYPI_SERVER_DIR}/packages"
PYPI_LOG_DIR="${PYPI_SERVER_DIR}/logs"
PYPI_CONFIG_FILE="${PYPI_SERVER_DIR}/config.cfg"
PYPI_SERVICE_FILE="/etc/systemd/system/pypi-server.service"
PYPI_SERVER_PATH=""

# 函数：打印信息
print_info() {
    echo -e "${green}[INFO] $1${NC}"
}

print_error() {
    echo -e "${red}[ERROR] $1${NC}"
}

# 函数：检查命令是否存在
command_exists() {
    command -v "$1" >/dev/null 2>&1
}

# 函数：检查端口是否被占用
check_port() {
    netstat -tuln | grep -q ":$1 "
    return $?
}

# 函数：查找pypi-server路径
find_pypi_server_path() {
    # 尝试多种可能的路径
    possible_paths=("/usr/local/bin/pypi-server" "/usr/bin/pypi-server" "$(which pypi-server 2>/dev/null)")
    
    for path in "${possible_paths[@]}"; do
        if [ -x "$path" ]; then
            PYPI_SERVER_PATH="$path"
            print_info "找到pypi-server: $PYPI_SERVER_PATH"
            return 0
        fi
    done
    
    return 1
}

# 函数：安装Python和pip
install_python_pip() {
    print_info "安装Python3和pip..."
    yum install -y python3 python3-pip
    
    if ! command_exists python3 || ! command_exists pip3; then
        print_error "Python3或pip3安装失败"
        exit 1
    fi
    
    print_info "升级pip到最新版本..."
    pip3 install --upgrade pip
}

# 函数：安装pypi-server
install_pypi_server() {
    print_info "安装pypi-server..."
    pip3 install pypiserver[passlib] || pip install pypiserver[passlib]
    
    if ! find_pypi_server_path; then
        print_error "pypi-server安装失败，请检查安装日志"
        # 尝试查找可能的安装位置
        print_info "正在搜索可能的pypi-server安装位置..."
        find /usr -name "pypi-server" 2>/dev/null
        find /opt -name "pypi-server" 2>/dev/null
        find /home -name "pypi-server" 2>/dev/null
        exit 1
    fi
}

# 函数：创建用户和目录
create_user_and_dirs() {
    print_info "创建pypi-server用户和目录..."
    
    # 创建用户
    if ! id "$PYPI_SERVER_USER" &>/dev/null; then
        useradd -r -s /sbin/nologin $PYPI_SERVER_USER
        if [ $? -ne 0 ]; then
            print_error "创建用户 $PYPI_SERVER_USER 失败"
            exit 1
        fi
    fi
    
    # 创建目录
    mkdir -p $PYPI_PACKAGES_DIR
    mkdir -p $PYPI_LOG_DIR
    
    if [ $? -ne 0 ]; then
        print_error "创建目录失败，请检查权限"
        exit 1
    fi
    
    # 设置权限
    chown -R $PYPI_SERVER_USER:$PYPI_SERVER_USER $PYPI_SERVER_DIR
    chmod -R 755 $PYPI_SERVER_DIR
    
    # 确保包目录可写
    chmod 775 $PYPI_PACKAGES_DIR
    
    print_info "目录结构和权限设置完成"
    ls -la $PYPI_SERVER_DIR
}

# 函数：创建配置文件
create_config_file() {
    print_info "创建配置文件..."
    
    cat > $PYPI_CONFIG_FILE << EOF
# pypi-server配置文件

# 服务器端口
port = $PYPI_SERVER_PORT

# 包存储目录
root = $PYPI_PACKAGES_DIR

# 日志目录
log_file = $PYPI_LOG_DIR/pypi-server.log

# 允许上传
allow_upload=*

# 认证（可选）
# --passwords=./htpasswd
EOF
    
    chown $PYPI_SERVER_USER:$PYPI_SERVER_USER $PYPI_CONFIG_FILE
    chmod 644 $PYPI_CONFIG_FILE
}

# 函数：创建systemd服务文件
create_systemd_service() {
    print_info "创建systemd服务文件..."
    
    # 确保PYPI_SERVER_PATH已设置
    if [ -z "$PYPI_SERVER_PATH" ]; then
        if ! find_pypi_server_path; then
            print_error "无法找到pypi-server路径，无法创建服务文件"
            exit 1
        fi
    fi
    
    cat > $PYPI_SERVICE_FILE << EOF
[Unit]
Description=PyPI Server
After=network.target

[Service]
Type=simple
User=$PYPI_SERVER_USER
Group=$PYPI_SERVER_USER
ExecStart=$PYPI_SERVER_PATH run -p $PYPI_SERVER_PORT $PYPI_PACKAGES_DIR
Restart=on-failure
RestartSec=5s
# 环境变量配置
Environment=PYTHONUNBUFFERED=1

[Install]
WantedBy=multi-user.target
EOF
    
    # 重载systemd配置
    systemctl daemon-reload
    
    # 启用服务开机自启
    systemctl enable pypi-server
}

# 函数：测试pypi-server命令
 test_pypi_server() {
    print_info "测试pypi-server命令是否能正常执行..."
    
    # 确保PYPI_SERVER_PATH已设置
    if [ -z "$PYPI_SERVER_PATH" ]; then
        if ! find_pypi_server_path; then
            print_error "无法找到pypi-server路径"
            return 1
        fi
    fi
    
    # 使用--help参数测试命令是否可用
    $PYPI_SERVER_PATH --help >/dev/null 2>&1
    if [ $? -eq 0 ]; then
        print_info "pypi-server命令测试通过"
        return 0
    else
        print_error "pypi-server命令执行失败"
        print_info "尝试直接运行命令查看详细错误..."
        $PYPI_SERVER_PATH --help
        return 1
    fi
}

# 函数：启动服务
start_service() {
    print_info "启动pypi-server服务..."
    
    # 检查端口是否被占用
    if check_port $PYPI_SERVER_PORT; then
        print_error "端口 $PYPI_SERVER_PORT 已被占用，请修改配置中的端口号"
        print_info "占用端口的进程信息："
        netstat -tuln | grep ":$PYPI_SERVER_PORT "
        lsof -i :$PYPI_SERVER_PORT 2>/dev/null || echo "lsof命令未找到"
        return 1
    fi
    
    # 停止可能存在的服务实例
    systemctl stop pypi-server >/dev/null 2>&1
    
    # 启动服务
    systemctl start pypi-server
    
    # 检查服务状态
    sleep 5
    systemctl status pypi-server --no-pager
    
    if systemctl is-active --quiet pypi-server; then
        print_info "pypi-server服务启动成功！"
        print_info "访问地址: http://服务器IP:$PYPI_SERVER_PORT/simple"
        
        # 测试访问
        print_info "测试服务访问..."
        curl -s http://localhost:$PYPI_SERVER_PORT/simple/ >/dev/null
        if [ $? -eq 0 ]; then
            print_info "服务访问测试通过"
        else
            print_error "服务访问测试失败，请检查防火墙设置"
        fi
        return 0
    else
        print_error "pypi-server服务启动失败，请检查以下内容："
        print_error "1. 查看systemd日志：journalctl -u pypi-server -n 50"
        print_error "2. 检查pypi-server日志：$PYPI_LOG_DIR/pypi-server.log"
        print_error "3. 手动测试启动命令：$PYPI_SERVER_PATH run -p $PYPI_SERVER_PORT $PYPI_PACKAGES_DIR"
        return 1
    fi
}

# 函数：配置防火墙
configure_firewall() {
    print_info "配置防火墙..."
    
    if command_exists firewall-cmd; then
        firewall-cmd --permanent --add-port=$PYPI_SERVER_PORT/tcp
        firewall-cmd --reload
        print_info "防火墙已开放端口 $PYPI_SERVER_PORT"
    elif command_exists iptables; then
        iptables -A INPUT -p tcp --dport $PYPI_SERVER_PORT -j ACCEPT
        service iptables save
        print_info "防火墙已开放端口 $PYPI_SERVER_PORT"
    else
        print_info "未检测到firewall-cmd或iptables，跳过防火墙配置"
    fi
}

# 函数：安装上传工具
install_upload_tools() {
    print_info "安装twine（用于上传包到私服）..."
    pip3 install twine || pip install twine
}

# 函数：显示使用说明和故障排除指南
show_usage() {
    print_info "\n===== pip私服使用说明 ====="
    print_info "1. 上传包到私服:"
    print_info "   twine upload --repository-url http://服务器IP:$PYPI_SERVER_PORT/simple/ dist/*"
    print_info "\n2. 从私服安装包:"
    print_info "   pip install --index-url http://服务器IP:$PYPI_SERVER_PORT/simple/ 包名"
    print_info "\n3. 配置pip默认使用私服（可选）:" 
    print_info "   创建或编辑 ~/.pip/pip.conf 文件，添加以下内容:" 
    print_info "   [global]"
    print_info "   index-url = http://服务器IP:$PYPI_SERVER_PORT/simple/"
    print_info "\n4. 服务管理命令:" 
    print_info "   systemctl start pypi-server    # 启动服务"
    print_info "   systemctl stop pypi-server     # 停止服务"
    print_info "   systemctl restart pypi-server  # 重启服务"
    print_info "   systemctl status pypi-server   # 查看服务状态"
    print_info "\n5. 查看日志:" 
    print_info "   tail -f $PYPI_LOG_DIR/pypi-server.log"
    print_info "   journalctl -u pypi-server -f   # 查看systemd日志"
    print_info "\n===== 故障排除指南 ====="
    print_info "1. 服务启动失败？"
    print_info "   - 检查端口是否被占用：netstat -tuln | grep $PYPI_SERVER_PORT"
    print_info "   - 检查pypi-server路径是否正确：which pypi-server"
    print_info "   - 查看详细错误日志：journalctl -u pypi-server -n 100"
    print_info "   - 手动测试命令：$PYPI_SERVER_PATH run -p $PYPI_SERVER_PORT $PYPI_PACKAGES_DIR --allow-upload=*"
    print_info "\n2. 无法访问Web界面？"
    print_info "   - 检查防火墙设置：firewall-cmd --list-ports"
    print_info "   - 检查SELinux状态：sestatus && getenforce"
    print_info "   - 临时禁用SELinux测试：setenforce 0"
    print_info "\n3. 权限问题？"
    print_info "   - 检查目录权限：ls -la $PYPI_SERVER_DIR"
    print_info "   - 确保pypi用户有访问权限：chown -R $PYPI_SERVER_USER:$PYPI_SERVER_USER $PYPI_SERVER_DIR"
    print_info "=========================\n"
}

# 主函数
main() {
    print_info "开始在CentOS上部署pip私服（修复版）..."
    
    install_python_pip
    install_pypi_server
    
    # 测试pypi-server命令是否可用
    if ! test_pypi_server; then
        print_error "pypi-server命令测试失败，无法继续部署"
        exit 1
    fi
    
    create_user_and_dirs
    create_config_file
    create_systemd_service
    
    # 启动服务并检查结果
    if ! start_service; then
        print_info "尝试以调试模式手动启动pypi-server（前台运行）..."
        print_info "按Ctrl+C停止手动测试"
        su -s /bin/bash -c "$PYPI_SERVER_PATH run -p $PYPI_SERVER_PORT $PYPI_PACKAGES_DIR" $PYPI_SERVER_USER
    fi
    
    configure_firewall
    install_upload_tools
    
    print_info "pip私服部署完成！"
    show_usage
}

# 执行主函数
main