# Maven私服部署指南

## 简介

本工具用于在Linux服务器上快速搭建Maven私服，基于Nexus Repository Manager实现。Maven私服可以作为团队内部的依赖仓库，加速构建过程并减少对外部网络的依赖。

## 功能特点

- **智能代理**: 优先从常用的中央库下载依赖，并缓存到本地
- **缓存管理**: 支持删除缓存和备份缓存功能
- **Web管理界面**: 提供完整的Web界面进行仓库控制和管理
- **多仓库支持**: 同时支持Maven中央仓库和阿里云Maven仓库(国内加速)

## 系统要求

- Linux服务器 (CentOS, Ubuntu, Debian等)
- Java 8或更高版本
- 至少2GB内存
- 至少10GB可用磁盘空间

## 快速部署

### 步骤1: 准备环境

确保服务器已安装Java 8或更高版本：

```bash
# 检查Java版本
java -version

# 如果未安装Java，可以使用以下命令安装
# Debian/Ubuntu
sudo apt-get update
sudo apt-get install openjdk-8-jdk

# CentOS/RHEL
sudo yum install java-1.8.0-openjdk
```

### 步骤2: 安装Nexus

```bash
# 给脚本添加执行权限
chmod +x install_nexus.sh

# 运行安装脚本
./install_nexus.sh
```

安装过程会自动下载Nexus、解压并配置基本参数。安装完成后，Nexus服务会自动启动。

### 步骤3: 配置Maven私服

```bash
# 给脚本添加执行权限
chmod +x configure_nexus.sh

# 运行配置脚本
./configure_nexus.sh
```

配置脚本会自动设置以下内容：
- 配置Maven中央仓库代理
- 配置阿里云Maven仓库代理（国内加速）
- 创建Maven仓库组，整合多个仓库
- 生成Maven客户端配置示例

### 步骤4: 访问Web管理界面

安装完成后，可以通过以下地址访问Nexus管理界面：

```
http://服务器IP:8081
```

首次登录信息：
- 用户名: admin
- 密码: 在服务器上查看初始密码文件
  ```bash
  cat ./nexus-repository/sonatype-work/nexus3/admin.password
  ```

首次登录后，系统会要求修改密码并设置匿名访问权限。

## 缓存管理

本工具提供了缓存管理功能，可以备份、恢复和清理Maven依赖缓存。

```bash
# 给脚本添加执行权限
chmod +x manage_cache.sh

# 运行缓存管理工具
./manage_cache.sh
```

缓存管理工具提供以下功能：
1. 备份缓存 - 将当前缓存打包备份
2. 恢复缓存 - 从备份文件恢复缓存
3. 清理缓存 - 删除所有缓存的依赖包
4. 显示缓存统计信息 - 查看当前缓存使用情况

## 服务管理

安装过程会创建一个服务管理脚本，用于控制Nexus服务的启动、停止和重启。

```bash
# 启动服务
./nexus-service.sh start

# 停止服务
./nexus-service.sh stop

# 重启服务
./nexus-service.sh restart

# 查看服务状态
./nexus-service.sh status
```

## 客户端配置

要让Maven客户端使用私服，需要修改Maven的settings.xml文件。配置脚本会在examples目录下生成一个settings.xml示例文件，您需要根据实际情况修改服务器IP地址和认证信息。

主要配置内容：

```xml
<!-- 配置镜像，使所有Maven仓库请求都指向私服 -->
<mirrors>
  <mirror>
    <id>nexus</id>
    <mirrorOf>*</mirrorOf>
    <url>http://服务器IP:8081/repository/maven-public/</url>
  </mirror>
</mirrors>
```

## 常见问题

### Q: Nexus服务无法启动

检查Java版本是否满足要求，确保服务器内存至少有2GB。查看日志文件：
```bash
cat ./nexus-repository/nexus/logs/nexus.log
```

### Q: 无法访问Web界面

检查防火墙设置，确保8081端口已开放：
```bash
# 检查防火墙状态
sudo firewall-cmd --list-all

# 开放8081端口
sudo firewall-cmd --permanent --add-port=8081/tcp
sudo firewall-cmd --reload
```

### Q: Maven客户端无法连接私服

检查settings.xml配置是否正确，特别是服务器IP地址和认证信息。确保网络连接正常，可以使用以下命令测试连接：
```bash
curl -v http://服务器IP:8081/repository/maven-public/
```

## 维护建议

1. **定期备份**: 使用缓存管理工具定期备份仓库数据
2. **监控磁盘空间**: 定期检查服务器磁盘使用情况，避免空间不足
3. **更新Nexus**: 定期更新Nexus版本以获取安全补丁和新功能

## 安全建议

1. **修改默认密码**: 首次登录后立即修改管理员密码
2. **配置HTTPS**: 在生产环境中建议配置HTTPS以加密传输
3. **设置访问控制**: 根据需要配置仓库的访问权限
4. **启用审计日志**: 在Web界面中启用审计日志，记录重要操作

## 许可证

本工具基于开源软件Nexus Repository Manager，使用时请遵守相关许可协议。

## 支持与反馈

如有问题或建议，请联系系统管理员或提交Issue。