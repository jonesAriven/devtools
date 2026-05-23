# 二维码工具（长文本增强版）

基于 C# WinForms 开发的二维码生成与识别工具，支持长文本压缩编码，容量提升至 ~9500 字符。

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
- **压缩模式**：开启 Brotli 压缩 + Base45 编码，利用 QR 字母数字模式，容量从 ~2700 字符提升至 ~9500 字符
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

## 技术架构

### 如何提升二维码的文字容量

普通二维码能容纳的文字有限，随机文本大约只能放 ~2700 个字符就达到上限（Version 40，177×177 模块）。本工具通过 **Brotli 压缩 + Base45 编码 + QR 字母数字模式** 将容量提升至 ~9500 字符，原理如下：

```
原始文本 → Brotli压缩 → Base45编码 → 加 "B5:" 前缀 → 生成二维码（字母数字模式）
```

1. **Brotli 压缩**：比 GZip 压缩率更高，普通文字通常能压缩到原来的 25%~35%
2. **Base45 编码**：RFC 9285 标准，专为 QR 码设计，只使用 QR 字母数字模式的 45 个字符（0-9, A-Z, 空格, $%*+-./:），2 字节编码为 3 字符
3. **QR 字母数字模式**：Version 40-L 下字母数字模式容量为 4296 字符，比字节模式的 2953 字节多 45%
4. **B5: 前缀**：解码时通过前缀判断编码格式，B5: 用 Base45+Brotli 解码，GZ: 用 Base64+GZip 解码（向后兼容），无前缀原样返回

**为什么 Base45 比 Base64 更适合 QR 码**：

| 编码 | 转换比例 | 空间开销 | 触发 QR 模式 | 模式容量 |
|---|---|---|---|---|
| Base64 | 3 字节 → 4 字符 | +33% | Byte 模式 | 2953 字节 |
| **Base45** | **2 字节 → 3 字符** | **+50%** | **字母数字模式** | **4296 字符** |

Base45 虽然编码开销更大（+50% vs +33%），但它能触发 QR 字母数字模式，4296 字符的容量远超 Byte 模式的 2953 字节，净效果是容量提升约 50%。

**容量对比**：

| 模式 | 随机文本容量 | 中文文本容量 |
|---|---|---|
| 不压缩 | ~2700 字符 | ~900 字符 |
| GZip+Base64（旧方案） | ~6300 字符 | ~3000 字符 |
| **Brotli+Base45（当前方案）** | **~9500 字符** | **~4500 字符** |

**代价**：压缩模式生成的二维码内容是 `B5:xxxxx` 编码串，第三方扫码器扫出来是乱码，只有本工具能正确解码还原。关闭压缩模式则任何扫码器都能识别原文。

**向后兼容**：解码时自动识别 `B5:` 和 `GZ:` 前缀，旧版 GZ: 格式的二维码仍可正常识别。

### 多策略解码

截图识别时，截取的图片可能存在光照不均、模糊、对比度不足等问题，单一解码方式容易失败。本工具采用 7 组串行解码策略，依次尝试：

| 策略组 | 方法 | 适用场景 |
|---|---|---|
| 1 | 缩放 2x-5x | 图片太小，模块像素不足 |
| 2 | 阈值二值化 50-210 | 光照不均，需要手动阈值分割 |
| 3 | 阈值 + 缩放 | 光照不均且图片太小 |
| 4 | Otsu 自适应阈值 + 缩放 | 自动计算最佳阈值，适合光照变化 |
| 5 | 对比度增强 + 缩放 | 图片对比度不足，黑白不分明 |
| 6 | 反色 | 白底黑码变成黑底白码的情况 |
| 7 | 去噪 + 缩放 | 图片有噪点干扰 |

每组策略内会快速失败跳过，找到第一个成功的结果就立即返回，不会全部跑完。

### 二维码显示像素密度

高密度二维码（Version 40，177×177 模块）对显示区域的像素密度有下限要求：

- **安全阈值**：每个 QR 模块至少 2.0 个显示像素
- **当前配置**：PictureBox 400×400，像素密度 = 400/177 ≈ 2.20 px/模块 ✅
- **危险区域**：PictureBox 300×300，像素密度 = 300/177 ≈ 1.66 px/模块 ❌

像素密度低于 2.0 时，GDI+ 缩放渲染会导致模块边界模糊，解码器无法稳定识别。

### 双屏截图

截图识别支持多显示器环境，通过 `Screen.AllScreens` 计算虚拟屏幕边界，将所有显示器的画面合并截取，确保副屏上的二维码也能被识别到。

### 内存优化

- 每次生成新二维码前，释放旧的 `_picQr.Image`，避免 GDI 对象泄漏
- 去掉了生成时的自检解码（之前每次生成都会额外拷贝一份 600×600 位图做解码验证），减少内存占用和输入延迟
- SelfContained 模式下运行时内存约 50MB，其中 ~30-35MB 是 .NET 运行时本身，属于正常水平
- WinForms 不支持 IL Trimming（裁剪），无法进一步裁减运行时体积

## 源码结构详解

### 文件清单

| 文件 | 作用 |
|---|---|
| `Program.cs` | 程序入口，启动 Form1 |
| `Form1.cs` | 全部业务逻辑（UI 构建、二维码生成/解码、截图、上传、图像处理） |
| `Form1.Designer.cs` | WinForms 设计器自动生成，仅初始化 components，UI 在 Form1.cs 的 BuildUI 中手动构建 |
| `QRCodeTool.csproj` | 项目配置文件 |

### Form1 字段说明

```csharp
private readonly PictureBox _picQr;       // 二维码显示区域，400×400，SizeMode=Zoom
private readonly TextBox _txtContent;      // 文本输入框，TextChanged 触发 GenerateQr
private Point _startPoint;                 // 截图选区起点
private bool _isSelecting;                 // 是否正在拖选截图区域
private Form? _maskForm;                   // 截图遮罩窗体（半透明黑色覆盖全屏）
private readonly string _logPath;          // 日志文件路径（exe 同目录下 qrcode_tool.log）
private CancellationTokenSource? _cancelToken; // 异步解码取消令牌，新截图时取消旧解码
private bool _compressMode = true;         // 压缩模式开关，默认开启
private readonly ToolTip _toolTip;         // 控件提示信息
private string? _firstDecodeError;         // 首次 ZXing 解码异常信息，用于日志诊断
```

### 方法调用关系

```
用户操作                    方法                     说明
─────────────────────────────────────────────────────────────────
输入文字                 → GenerateQr()             TextChanged 事件触发
                            ├─ CompressText()         Brotli压缩+Base45+B5:前缀
                            ├─ BarcodeWriter.Write()  ZXing生成600×600位图
                            └─ Dispose旧Image         防止GDI对象泄漏

点击截图图标             → BeginCapture()           清空输入框→截图→选区→解码
                            ├─ Screen.AllScreens      计算虚拟屏幕边界
                            ├─ CopyFromScreen()       截取全屏画面
                            ├─ 遮罩窗体交互           鼠标拖选区域
                            └─ DecodeQr(cropBmp)      异步解码

点击上传图标             → UploadImage()            选择图片文件→解码
                            └─ DecodeQr(img)          异步解码

解码入口                → DecodeQr()                克隆位图→异步多策略解码→回写文本
                            ├─ _cancelToken.Cancel()  取消旧解码任务
                            └─ TryDecodeWithProgress() 7组串行策略解码
                                 ├─ QuickDecode()      单次ZXing解码尝试
                                 ├─ ScaleImage()       缩放
                                 ├─ ApplyThreshold()   阈值二值化
                                 ├─ ApplyOtsuThreshold() Otsu自适应阈值
                                 ├─ EnhanceContrast()  对比度增强
                                 ├─ InvertColors()     反色
                                 └─ RemoveNoise()      去噪
                            └─ TryDecompressText()    检测B5:/GZ:前缀→解压还原
```

### UI 布局参数

```
┌──────────────────────────────────────────┐
│ 工具栏 Panel (0,0) 440×30 浅灰色背景      │
│ [☑压缩模式] [📷截图] [📁上传]              │
├──────────────────────────────────────────┤
│                                          │
│   PictureBox (10,34) 400×400             │
│   SizeMode=Zoom, BackColor=White         │
│   BorderStyle=FixedSingle                │
│                                          │
├──────────────────────────────────────────┤
│   TextBox (10,438) 412×100               │
│   Multiline, 微软雅黑 10pt                │
│   Anchor=四向拉伸                         │
└──────────────────────────────────────────┘

窗口默认: 440×560, 最小: 440×460
FormBorderStyle=Sizable（可拖拽缩放）
```

### 关键配置参数

| 参数 | 当前值 | 说明 | 修改注意 |
|---|---|---|---|
| QR 生成尺寸 | 600×600 | `QrCodeEncodingOptions.Width/Height` | 不要改小，否则高密度二维码模块像素不足 |
| QR Margin | 2 | `QrCodeEncodingOptions.Margin` | 安静区，太小会导致边缘模块被裁剪 |
| 纠错等级 | L（7%） | `ErrorCorrectionLevel.L` | 最低纠错，容量最大；提高纠错会降低容量 |
| PictureBox 尺寸 | 400×400 | `_picQr.Size` | **不能小于 400×400**，否则像素密度低于 2.0 安全阈值 |
| 像素密度安全阈值 | ~2.0 px/模块 | 400/177≈2.20 | Version 40 二维码（177×177模块）的最低稳定密度 |
| 缩放策略 | 2x,3x,4x,5x | `scales` 数组 | 截图图片太小时通过放大增加像素密度 |
| 阈值范围 | 50-210 步长20 | `thresholds` 数组 | 覆盖从暗到亮的多种光照条件 |
| 选区最小尺寸 | 40×40 | `w < 40 \|\| h < 40` | 太小的选区没有有效内容 |

### 压缩编解码流程

**编码（CompressText）**：
```
"你好世界" → UTF8.GetBytes → [字节流] → Brotli压缩 → [更短字节流] → Base45编码 → "B5:BB8..."
```

**解码（TryDecompressText）**：
```
"B5:BB8..." → 检测"B5:"前缀 → Base45解码 → [字节流] → Brotli解压 → UTF8解码 → "你好世界"
"GZ:H4sIAA..." → 检测"GZ:"前缀 → Base64解码 → [字节流] → GZip解压 → UTF8解码 → "你好世界"（向后兼容）
无前缀 → 原样返回（兼容非压缩二维码）
```

### 截图识别流程

1. 清空输入框（避免旧文本触发 GenerateQr 覆盖识别结果）
2. 取消正在进行的旧解码任务（`_cancelToken.Cancel()`）
3. 计算虚拟屏幕边界（`Screen.AllScreens`，支持多显示器）
4. 截取全屏画面（`CopyFromScreen`）
5. 创建半透明遮罩窗体，用户鼠标拖选区域
6. 裁剪选区图片，送入异步解码
7. 7 组串行策略依次尝试，任一成功即返回
8. 解码成功后检测 `B5:` 或 `GZ:` 前缀，有则解压还原，无则原样显示

### 图像处理算法说明

| 方法 | 算法 | 说明 |
|---|---|---|
| `ScaleImage` | HighQualityBicubic 插值 | 放大图片增加像素密度，用高质量双三次插值避免锯齿 |
| `ApplyThreshold` | 灰度 > 阈值 → 白，否则 → 黑 | 手动阈值二值化，遍历 50-210 共 9 个阈值 |
| `ApplyOtsuThreshold` | 大津法（Otsu） | 自动计算最佳阈值，最大化类间方差，适合光照不均 |
| `EnhanceContrast` | `(gray-128)*2+128` | 以 128 为中心拉伸对比度，让黑白更分明 |
| `RemoveNoise` | 灰度 < 80 → 0，> 180 → 255，其余 → 128 | 三段式去噪，消除中间灰度干扰 |
| `InvertColors` | `255 - R/G/B` | 反色处理，应对黑底白码的情况 |
| `ApplyAdaptiveThreshold` | 局部均值自适应阈值 | 当前未使用（保留备用），按 blockSize 计算局部平均灰度作为阈值 |

### RGBLuminanceSource 类

ZXing.Net 自带的 `BitmapLuminanceSource` 在某些场景下有兼容问题，因此自定义了 `RGBLuminanceSource`：

- 构造函数：遍历 Bitmap 每个像素，用 ITU-R BT.601 公式 `0.299R + 0.587G + 0.114B` 转为灰度字节数组
- `getRow`：按行拷贝灰度数据
- `crop`：按区域拷贝灰度数据，支持 ZXing 内部的裁剪优化

### 日志系统

- 日志文件：`qrcode_tool.log`，与 exe 同目录
- 写入方式：`File.AppendAllText`，每次追加一行，带时间戳
- 日志内容：程序启动、UI 构建、二维码生成/解码、策略组进度、异常信息
- 已注释掉的调试截图保存代码（第 368-370 行），需要时可取消注释用于诊断

### 已知限制与二次开发建议

1. **PictureBox 不能再缩小**：400×400 是 Version 40 二维码的稳定识别下限，缩小会导致间歇性识别失败
2. **WinForms 不支持 IL Trimming**：无法通过裁剪减少运行时体积，50MB 内存是 SelfContained 模式的正常水平
3. **ApplyAdaptiveThreshold 未使用**：代码中已实现但未加入解码策略组，如遇局部光照不均的场景可启用
4. **图像处理用 GetPixel/SetPixel**：性能较低，大图处理较慢。如需优化可改用 `LockBits` 直接操作内存，速度可提升 10 倍以上
5. **压缩模式与第三方不兼容**：B5: 前缀是本工具私有协议，如需第三方扫码器兼容需关闭压缩模式
6. **异步竞态**：当前用 `_cancelToken` 取消旧任务，但 `DecodeQr` 中 `catch (OperationCanceledException)` 仍会设置文本。如频繁截图可考虑加 `_decodeId` 计数器（代码中已定义但未使用）彻底跳过过期回调
7. **截图遮罩窗体**：用 `ShowDialog()` 阻塞主窗体，截图期间主窗体无法操作，这是设计如此

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

## 版本更新记录

### v2.0 — 容量大幅提升 + 布局优化 + 独立打包

**编码方案升级（容量 +50%）**：

| 项目 | 旧方案 | 新方案 |
|---|---|---|
| 压缩算法 | GZip | **Brotli**（压缩率更好） |
| 二进制编码 | Base64 | **Base45**（RFC 9285，专为 QR 码设计） |
| QR 编码模式 | Byte 模式（2953 字节上限） | **字母数字模式**（4296 字符上限） |
| 前缀标识 | `GZ:` | `B5:` |
| 随机文本容量 | ~6300 字符 | **~9500 字符** |
| 中文文本容量 | ~3000 字符 | **~4500 字符** |
| 旧格式兼容 | — | ✅ 仍支持 `GZ:` 解码 |

**UI 布局优化**：

| 改动 | 说明 |
|---|---|
| 顶部工具栏 | 压缩模式复选框 + 截图/上传图标按钮，替代底部文字按钮 |
| 去掉 GroupBox | PictureBox 直接放表单上，消除阴影区域 |
| 间距压缩 | 各元素间距从 15-45px 缩减到 4-10px |
| 输入框增高 | 从 55px → 100px，多出约 2 行显示空间 |
| 窗口更紧凑 | 480×600 → 440×560 |

**其他改进**：

- 截图识别前自动清空输入框，避免旧文本覆盖识别结果
- 每次生成新二维码前释放旧 Image，防止 GDI 对象泄漏
- 去掉生成时的自检解码，减少内存占用和输入延迟
- 独立打包（PublishSingleFile）：单个 exe 约 49MB，无需安装 .NET
- 依赖发布（PublishDepFIle）：约 750KB，需安装 .NET 10 运行时
- 添加 `.gitignore`，排除编译过程文件
