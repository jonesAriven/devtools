# 二维码工具（长文本增强版）

基于 C# WinForms 开发的二维码生成与识别工具，支持长文本压缩编码，容量提升 2~3 倍。

## 开发环境

| 项目 | 版本 |
|---|---|
| 框架 | .NET 10.0 (Windows) |
| UI 框架 | WinForms |
| 二维码库 | ZXing.Net 0.16.11 |
| ZXing 兼容层 | ZXing.Net.Bindings.Windows.Compatibility 0.16.14 |
| 绘图库 | System.Drawing.Common 10.0.8 |
| 操作系统 | Windows x64 |

## 功能说明

- **文字转二维码**：在输入框输入文字，实时生成二维码
- **截图识别**：点击截图按钮，框选屏幕区域识别二维码
- **上传识别**：点击上传按钮，选择本地图片识别二维码
- **压缩模式**：开启 GZip 压缩 + Base64 编码，二维码容量从 ~2700 字符提升至 ~6300 字符
- **多策略解码**：截图识别采用 7 组串行解码策略（缩放、阈值二值化、Otsu、对比度增强、去噪、反色及其组合），提高识别成功率
- **双屏支持**：截图识别支持多显示器虚拟屏幕

## 使用方法

1. 双击 `QRCodeTool.exe` 启动程序
2. 在下方输入框输入文字，自动生成二维码
3. 点击顶部工具栏的截图图标，框选屏幕上的二维码进行识别
4. 点击顶部工具栏的上传图标，选择本地图片进行识别
5. 压缩模式默认开启，生成的二维码需使用本工具识别；关闭后第三方扫码器也可识别原文

## 目录结构

```
QR_GENERATORBYC#V1/
├── PublishSingleFile/        独立打包版本
│   ├── Form1.cs              主窗体逻辑
│   ├── QRCodeTool.csproj     项目文件（含独立打包配置）
│   └── publish/
│       └── QRCodeTool.exe    单个可执行文件（~49MB）
│
├── PublishDepFIle/           依赖发布版本
│   ├── Form1.cs              主窗体逻辑
│   ├── QRCodeTool.csproj     项目文件（依赖发布配置）
│   └── publish/
│       ├── QRCodeTool.exe    启动器（~158KB）
│       ├── QRCodeTool.dll    业务逻辑
│       ├── zxing.dll         二维码库
│       └── ...               其他依赖文件
│
└── README.md
```

## 两个版本差异

| | PublishSingleFile（独立打包） | PublishDepFIle（依赖发布） |
|---|---|---|
| **publish 产物** | 单个 QRCodeTool.exe | exe + dll + json 等多个文件 |
| **总体积** | ~49 MB | ~750 KB |
| **是否需要安装 .NET** | 不需要，已内置运行时 | 需要，目标电脑需安装 .NET 10 运行时 |
| **部署方式** | 拷贝 1 个 exe 即可 | 拷贝整个 publish 文件夹 |
| **适用场景** | 分发给其他电脑使用 | 本机开发调试，或已安装 .NET 的环境 |
| **csproj 差异** | 含 SelfContained、PublishSingleFile、RuntimeIdentifier 等配置 | 无这些配置，使用默认依赖发布模式 |

### csproj 配置差异

独立打包额外配置：

```xml
<PublishSingleFile>true</PublishSingleFile>
<SelfContained>true</SelfContained>
<RuntimeIdentifier>win-x64</RuntimeIdentifier>
<IncludeNativeLibrariesForSelfExtract>true</IncludeNativeLibrariesForSelfExtract>
<EnableCompressionInSingleFile>true</EnableCompressionInSingleFile>
```

依赖发布无需以上配置。

## 打包命令

```bash
# 独立打包
dotnet publish -c Release -o publish

# 依赖发布
dotnet publish -c Release -o publish
```

打包模式由 csproj 中的配置决定，命令相同。
