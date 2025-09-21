# Debian Linux pip私服搭建说明

## 简介

本文档介绍如何在Debian Linux服务器上使用自动化脚本搭建pip私服（PyPI镜像服务器）。

## 系统要求

- Debian 10或更高版本
- 至少1GB内存
- 至少10GB可用磁盘空间（用于存储Python包）

## 部署步骤

### 1. 准备工作

确保服务器已连接互联网，并具有root权限。

### 2. 获取脚本

将 `setup_pip_server.sh` 脚本上传到服务器，或直接在服务器上创建：

bash wget -O setup_pip_server.sh [脚本URL]
或者创建脚本文件并粘贴内容


### 3. 执行脚本
```bash
 chmod +x setup_pip_server.sh sudo ./setup_pip_server.sh
```
### 4. 验证安装

脚本执行完成后，可以通过以下方式验证：
```bash
检查服务状态
sudo supervisorctl status pypiserver
检查端口监听
netstat -tlnp | grep :80
测试访问
curl http://localhost
```

## 配置说明

### 默认配置

- 服务端口: 80 (通过Nginx反向代理)
- 包存储目录: `/var/lib/pypiserver/packages`
- 用户认证: 启用（默认用户admin/password）
- 上传权限: 允许

### 修改配置

1. **修改端口**:
   编辑脚本中的 `PIPSERVER_PORT` 变量并重新运行

2. **修改存储目录**:
   编辑脚本中的 `PACKAGES_DIR` 变量并重新运行

3. **修改认证信息**:
```bash
生成新用户
htpasswd /opt/pypiserver/.htpasswd newuser
删除用户
htpasswd -D /opt/pypiserver/.htpasswd username
```
## 使用方法

### 1. 上传包
```bash
使用twine上传包
pip install twine twine upload --repository-url http://your-server-ip/ -u admin -p password your-package.tar.gz
```

### 2. 从私服安装包
```bash
方法1: 使用pip参数
pip install --index-url http://admin:password@your-server-ip/simple/ package-name
方法2: 配置pip.conf文件
创建 ~/.pip/pip.conf 文件:
[global] index-url = http://admin:password@your-server-ip/simple/ trusted-host = your-server-ip
```

### 3. 批量下载并上传包
```bash
下载包到本地
pip download -r requirements.txt -d /var/lib/pypiserver/packages
重新扫描包目录
supervisorctl restart pypiserver
```

## 日志和监控

### 查看日志
```bash
查看pypiserver日志
tail -f /var/log/pypiserver/pypiserver.log
查看Nginx访问日志
tail -f /var/log/nginx/access.log
查看Nginx错误日志
tail -f /var/log/nginx/error.log
```

### 服务管理
```bash
查看服务状态
sudo supervisorctl status
重启服务
sudo supervisorctl restart pypiserver
停止服务
sudo supervisorctl stop pypiserver
启动服务
sudo supervisorctl start pypiserver
```
## 安全建议

1. **修改默认密码**:
 ```bash 
htpasswd /opt/pypiserver/.htpasswd admin
```

2. **启用HTTPS**:
   配置Nginx SSL证书以启用HTTPS访问

3. **限制上传权限**:
   在生产环境中考虑禁用包上传功能

4. **定期备份**:
   定期备份 `/var/lib/pypiserver/packages` 目录

## 故障排除

### 服务无法启动

1. 检查日志文件: `/var/log/pypiserver/pypiserver.log`
2. 检查端口占用: `netstat -tlnp | grep :8080`
3. 检查权限设置: 确保 `pypi` 用户对相关目录有读写权限

### 无法访问

1. 检查防火墙设置
2. 检查Nginx配置: `sudo nginx -t`
3. 检查SELinux设置（如果启用）

### 包无法显示

1. 重启pypiserver服务以重新扫描包目录
2. 检查包文件权限
3. 确保包文件格式正确




   



