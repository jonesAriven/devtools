# 自定义Nginx配置工具

## 简介

这是一个预配置的Nginx服务器配置文件，专为开发环境定制，提供了TCP和HTTP代理功能，可以快速搭建本地开发代理服务器。

## 功能特点

- **TCP代理**: 支持MySQL等数据库服务的TCP端口转发
- **HTTP反向代理**: 支持将请求转发到不同的后端服务
- **负载均衡**: 内置简单的负载均衡配置示例
- **静态文件服务**: 可用作静态资源服务器
- **开发环境优化**: 针对本地开发环境进行了优化配置

## 使用方法

### 前提条件

- 已安装Nginx服务器
- 基本了解Nginx配置语法

### 使用步骤

1. 将`nginx.conf`文件复制到Nginx安装目录的`conf`文件夹中
2. 根据实际需求修改配置文件中的端口和服务器地址
3. 重启Nginx服务器使配置生效

```bash
# Windows环境下重启Nginx
nginx -s reload

# Linux环境下重启Nginx
sudo systemctl restart nginx
```

## 配置说明

### TCP代理配置

配置文件中已包含MySQL数据库的TCP代理示例，监听20000端口，转发到本地3306端口：

```nginx
stream {
    server {
        listen 20000;
        proxy_pass mysql;
        proxy_connect_timeout 60s;
        proxy_timeout 300s;
    }
    
    upstream mysql {
        server 127.0.0.1:3306;
    }
}
```

### HTTP代理配置

配置文件中包含了HTTP反向代理的示例配置，可以根据需要进行修改：

```nginx
http {
    server {
        listen 80;
        server_name localhost;
        
        location /api/ {
            proxy_pass http://backend_servers/;
        }
    }
    
    upstream backend_servers {
        server 127.0.0.1:8080;
        server 127.0.0.1:8081;
    }
}
```

## 注意事项

- 使用前请备份原有的Nginx配置文件
- 修改配置后请先使用`nginx -t`命令检查配置是否有语法错误
- 根据实际项目需求调整端口号和服务器地址