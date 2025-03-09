# QR_generatorByC 二维码生成器

## 项目简介

QR_generatorByC 是一个基于C#开发的简单易用的二维码生成工具，使用Windows Forms构建用户界面，可以快速将文本、URL等内容转换为二维码图像。

## 功能特点

- 简洁直观的用户界面
- 快速生成高质量二维码
- 支持任意文本内容（网址、文本、联系方式等）
- 使用QRCoder库，生成的二维码符合标准规范
- 纯净安装，无需复杂配置

## 系统要求

- Windows 操作系统
- .NET 6.0 运行时或更高版本

## 安装说明

### 方法一：使用安装脚本（推荐）

1. 克隆或下载本仓库
2. 运行 `setup_simple.bat` 脚本
3. 脚本将自动完成以下操作：
   - 创建项目结构
   - 检查.NET环境
   - 还原NuGet包
   - 编译项目
   - 发布应用程序
4. 安装完成后，可执行文件将位于 `bin` 目录中

### 方法二：手动编译

如果您希望手动编译项目：

1. 确保已安装.NET 6.0 SDK或更高版本
2. 打开命令提示符，进入项目src/QR_generatorByC目录
3. 执行以下命令：
   ```
   dotnet restore
   dotnet build -c Release
   dotnet publish -c Release -r win-x64 --self-contained true -p:PublishSingleFile=true -p:PublishTrimmed=true -o "../../bin"
   ```

## 使用方法

1. 运行bin目录中的QR_generatorByC.exe
2. 在文本框中输入需要转换为二维码的内容（如网址、文本等）
3. 点击"生成二维码"按钮
4. 二维码图像将显示在下方区域

## 开发说明

本项目使用以下技术和库：

- C# 编程语言
- .NET 6.0 框架
- Windows Forms UI框架
- QRCoder 库 (v1.4.3) 用于二维码生成

## 许可证

本项目采用MIT许可证。详情请参阅LICENSE文件。

## 贡献

欢迎提交问题和改进建议！如果您想为项目做出贡献，请提交Pull Request。