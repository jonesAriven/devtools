# myMaven Maven私服管理工具

## 简介

myMaven是一套用于快速部署和管理Maven私服的工具集，旨在简化团队内部依赖管理，提高构建效率，减少对外部网络的依赖。本工具基于Nexus Repository Manager实现，支持Windows和Linux环境。

## 功能特点

- **跨平台支持**: 同时支持Windows和Linux环境下的Maven私服部署
- **智能代理**: 优先从常用的中央库下载依赖，并缓存到本地
- **缓存管理**: 支持删除缓存和备份缓存功能
- **Web管理界面**: 提供完整的Web界面进行仓库控制和管理
- **多仓库支持**: 同时支持Maven中央仓库和阿里云Maven仓库(国内加速)

## 系统要求

### Windows环境
- Windows 10/11 或 Windows Server 2016+
- Java 8或更高版本
- 至少2GB内存
- 至少10GB可用磁盘空间

### Linux环境
- CentOS, Ubuntu, Debian等主流Linux发行版
- Java 8或更高版本
- 至少2GB内存
- 至少10GB可用磁盘空间

## 目录结构

```
myMaven/
├── linux/                # Linux环境下的部署脚本
│   ├── install_nexus.sh  # Nexus安装脚本
│   ├── configure_nexus.sh # Nexus配置脚本
│   └── manage_cache.sh   # 缓存管理脚本
└── windows/             # Windows环境下的部署脚本（计划中）
```

## 快速开始

### Linux环境

请参考 [Linux部署指南](./linux/README.md) 进行安装和配置。

### Windows环境

Windows环境下的部署工具正在开发中，敬请期待。

## 使用场景

- **团队开发环境**: 在团队内部搭建Maven私服，加速依赖下载
- **离线开发环境**: 在网络受限环境下提供Maven依赖服务
- **CI/CD集成**: 与Jenkins等CI/CD工具集成，提高构建效率

## 常见问题

- **启动失败**: 检查Java版本和内存配置
- **无法下载依赖**: 检查网络连接和代理设置，若无法下载nuxus安装包，请将手动下载nuxus文件到和configure_nexus.sh同一目录下
- **磁盘空间不足**: 使用缓存管理工具清理不常用的依赖

## 注意事项

- 定期备份仓库数据
- 根据团队规模和项目数量调整内存和磁盘配置
- 配置适当的安全策略，避免未授权访问