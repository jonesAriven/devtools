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

## 开发踩坑记录

开发过程中遇到多个反复调试才定位的问题，记录如下供参考。

### 1. PictureBox 显示尺寸过小导致间歇性识别失败（反复最多次的问题）

**现象**：截图识别二维码时，时好时坏，有一定概率识别失败。

**错误尝试**：
- 添加 GlobalHistogramBinarizer 作为 HybridBinarizer 的后备 → 识别率反而下降，回退
- 尝试 NearestNeighbor 缩放 + SizeMode.Normal + PixelOffsetMode.Half 组合 → 100% 失败，回退
- 多次调整解码策略参数 → 效果不明显

**根因**：二维码本身生成时是 600×600 像素，没有任何问题。问题出在显示区域太小。当 PictureBox 只有 300×300 时，600×600 的二维码要压缩到 300×300 显示，Version 40 二维码有 177×177 个黑白方块，每个方块平均只有 1.66 个像素来表示，方块边界就模糊了，解码器分不清这个方块是黑还是白，所以时好时坏。调到 400×400 后，每个方块有 2.2 个像素，超过 2.0 的安全线，就稳定了。

**通俗理解**：就像一幅画缩得太小，细节就糊了。二维码的黑白方块就是细节，显示区域越小，方块越糊，解码器越容易认错。

**最终解决**：将 PictureBox 从 300×300 调大到 400×400，像素密度从 1.66 提升至 2.20 px/模块，超过安全阈值 ~2.0 px/模块，识别稳定。

**教训**：高密度二维码（Version 40）的显示区域不能太小，否则缩放渲染时像素密度不足，导致识别不稳定。二维码源图本身没问题，是显示区域太小导致的。

### 2. 副屏截图截的是主屏

**现象**：在副屏上截图识别时，截取到的始终是主屏内容。

**根因**：原代码使用 `Screen.PrimaryScreen.Bounds` 获取屏幕尺寸，`CopyFromScreen(0, 0, ...)` 从坐标 (0,0) 开始截取，只能截到主屏。

**解决**：使用 `Screen.AllScreens` 计算所有显示器的虚拟屏幕边界（最小 X/Y 到最大 Right/Bottom），然后从虚拟屏幕原点开始截取完整画面。

### 3. 异步解码取消竞态覆盖文本

**现象**：快速连续截图时，前一次解码被取消后，文本框显示"识别已取消"，触发 TextChanged 事件调用 GenerateQr，覆盖了当前正在显示的二维码。

**根因**：旧解码任务的 `catch (OperationCanceledException)` 设置了 `_txtContent.Text = "识别已取消"`，这个赋值触发了 TextChanged → GenerateQr，生成了"识别已取消"的二维码，覆盖了新解码的结果。

**解决**：添加 `_decodeId` 计数器，每次新解码递增。回调中检查当前 decodeId 是否过期，过期的回调跳过文本更新。

### 4. SearchReplace 误删代码

**现象**：添加策略组进度日志时，SearchReplace 意外删除了 `using var binary = ApplyThreshold(...)` 和 `foreach (var scale in...)` 两行关键代码，导致阈值+缩放策略组完全失效。

**解决**：手动修复恢复完整代码块。

**教训**：使用 SearchReplace 时，old_str 的上下文范围要仔细确认，避免匹配到错误的区域导致代码被意外删除。

### 5. exe 文件被占用导致 publish 失败

**现象**：多次出现 `Access to the path 'QRCodeTool.exe' is denied` 错误。

**根因**：上一次运行的 QRCodeTool.exe 进程未退出，文件被锁定。

**解决**：打包前先执行 `Get-Process -Name "QRCodeTool" | Stop-Process -Force` 强制关闭进程。
