#!/bin/bash
# filename: setup_pip_server.sh

set -e

# 配置变量
PIPSERVER_USER="pypi"
PIPSERVER_HOME="/opt/pypiserver"
PIPSERVER_PORT="8080"
PACKAGES_DIR="/var/lib/pypiserver/packages"

# 检查是否以root权限运行
if [[ $EUID -ne 0 ]]; then
   echo "此脚本必须以root权限运行"
   exit 1
fi

# 更新系统包
echo "更新系统包..."
apt update && apt upgrade -y

# 安装必要软件
echo "安装必要软件..."
apt install -y python3 python3-pip nginx supervisor

# 创建pypiserver用户
echo "创建pypiserver用户..."
useradd -r -s /bin/false -d $PIPSERVER_HOME $PIPSERVER_USER || true

# 创建目录结构
echo "创建目录结构..."
mkdir -p $PACKAGES_DIR
mkdir -p $PIPSERVER_HOME
mkdir -p /var/log/pypiserver

# 设置权限
chown -R $PIPSERVER_USER:$PIPSERVER_USER $PACKAGES_DIR
chown -R $PIPSERVER_USER:$PIPSERVER_USER $PIPSERVER_HOME
chown -R $PIPSERVER_USER:$PIPSERVER_USER /var/log/pypiserver

# 安装pypiserver
echo "安装pypiserver..."
pip3 install pypiserver passlib

# 创建pypiserver配置文件
cat > $PIPSERVER_HOME/config.ini << EOF
[server:main]
use = egg:gunicorn#main
host = 0.0.0.0
port = $PIPSERVER_PORT
workers = 2
timeout = 60

[app:main]
use = egg:pypiserver#main
root = $PACKAGES_DIR
allow_upload = yes
passwords = $PIPSERVER_HOME/.htpasswd
EOF

# 创建初始密码文件（用户名:admin, 密码:password）
echo "创建初始密码文件..."
htpasswd -b -c $PIPSERVER_HOME/.htpasswd admin password

# 配置supervisor管理服务
cat > /etc/supervisor/conf.d/pypiserver.conf << EOF
[program:pypiserver]
command=pypi-server -c $PIPSERVER_HOME/config.ini
directory=$PIPSERVER_HOME
user=$PIPSERVER_USER
autostart=true
autorestart=true
redirect_stderr=true
stdout_logfile=/var/log/pypiserver/pypiserver.log
EOF

# 配置Nginx反向代理
cat > /etc/nginx/sites-available/pypiserver << EOF
server {
    listen 80;
    server_name _;

    location / {
        proxy_pass http://127.0.0.1:$PIPSERVER_PORT;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
    }
}
EOF

# 启用Nginx配置
ln -sf /etc/nginx/sites-available/pypiserver /etc/nginx/sites-enabled/
rm -f /etc/nginx/sites-enabled/default

# 重启服务
echo "重启服务..."
systemctl restart nginx
supervisorctl reread
supervisorctl update
supervisorctl restart pypiserver || supervisorctl start pypiserver

# 防火墙配置
echo "配置防火墙..."
ufw allow 80/tcp || echo "ufw未启用或配置失败"

echo "安装完成！"
echo "访问地址: http://your-server-ip"
echo "默认用户名: admin, 密码: password"
echo "包存储目录: $PACKAGES_DIR"
