# QRCodeTool (C++ Win32 版)

轻量级 Windows 二维码生成与识别工具，使用纯 C++ + Win32 API 开发，无需 .NET 运行时。

## 项目定位

本项目是 [QRCodeTool C# 版](../QR_GENERATORBYC%23V1) 的 C++ 重写版本。原 C# 版自包含打包后约 49MB（因需内嵌 .NET 运行时），C++ 版静态链接后仅约 1.4MB，拷贝即运行，零依赖。

## 功能特性

| 功能 | 说明 |
|------|------|
| 文字转二维码 | 输入文字实时生成 QR Code，支持 Version 1-40 |
| 多页二维码 | 长文本自动分页生成多个二维码（M5: 协议），工具栏翻页切换 |
| 截图识别 | 全屏截图 + 鼠标拖选区域，识别屏幕上的二维码 |
| 上传识别 | 选择本地图片文件（png/jpg/bmp）识别二维码 |
| 多页扫描 | 扫描多页二维码时自动拼合，提示缺失页码 |
| 自适应压缩 | 短文本直接编码（任何扫码器可读），长文本自动 Brotli+Base45 压缩（容量 ≥9000 字符） |
| 激活码验证 | 内嵌 JonesActivation.lib，启动时验证激活码，过期自动弹窗 |
| 纠错等级 | 工具栏下拉选择 L/M/Q/H 四级纠错 |
| 多显示器 | 支持多屏截图，DPI 感知 |

## 技术指标

| 指标 | C# 版 | C++ 版 |
|------|-------|--------|
| 自包含部署体积 | ~49MB | **~1.4MB** |
| 运行时依赖 | .NET 6 Runtime | **无** |
| 识别容量（压缩模式） | ~9500 字符 | **~9500 字符**（相同算法） |
| 内存占用 | 30-50MB | **5-15MB** |

## 项目结构

```
QR_GENERATORBYCCC/
├── CMakeLists.txt          # CMake 构建配置
├── build.bat               # 一键构建脚本
├── res/
│   └── resource.rc         # Windows 资源文件
├── src/
│   ├── main.cpp            # 程序入口，GDI+ 初始化，DPI 感知
│   ├── MainWindow.h/cpp    # 主窗口（Win32 API），UI 布局与事件处理
│   ├── QrGenerator.h/cpp   # QR 码生成（qrcodegen + 压缩 + 多页分片）
│   ├── QrDecoder.h/cpp     # QR 码解码（zxing-cpp，多策略）
│   ├── Compressor.h/cpp    # 压缩/解压（Brotli + GZip 兼容）+ MultiPageAssembler 多页拼合
│   ├── Base45.h/cpp        # Base45 编解码（RFC 9285）
│   ├── ImageProcess.h/cpp  # 图像处理（灰度转换、缩放、二值化、Otsu 等）
│   ├── ScreenCapture.h/cpp # 屏幕截图选区（Win32 API，半透明遮罩）
│   ├── Activation/         # 激活码验证模块（JonesActivation.lib）
│   │   ├── ActivationGuard.h/cpp  # 激活码验证与保护
│   │   ├── DeviceInfo.h/cpp       # 设备指纹采集
│   │   ├── CryptoUtil.h/cpp       # RSA/AES/HMAC 加密工具
│   │   ├── LicenseStore.h/cpp     # 激活码本地存储
│   │   └── RsaKey.h               # RSA 公钥（PEM）
│   └── Resource.h          # 资源 ID 定义
└── build/                  # 构建输出目录
    └── bin/Release/QRCodeTool.exe
```

## 依赖库

所有依赖通过 CMake FetchContent 自动下载，无需手动安装：

| 库 | 版本 | 用途 | 链接方式 |
|----|------|------|----------|
| [qrcodegen](https://github.com/nayuki/QR-Code-generator) | v1.8.0 | QR 码生成 | 静态（仅 C 版 qrcodegen.c） |
| [zxing-cpp](https://github.com/zxing-cpp/zxing-cpp) | v2.2.1 | QR 码解码 | 静态（精简版，仅 QR Reader） |
| [brotli](https://github.com/google/brotli) | v1.1.0 | Brotli 压缩/解压 | 静态 |
| [zlib](https://github.com/madler/zlib) | v1.3.1 | GZip 解压（兼容旧 "GZ:" 格式） | 静态 |

## 构建方法

### 环境要求

- **Visual Studio Build Tools 2022**（或 2019），需安装 "使用 C++ 的桌面开发" 工作负载
- **CMake** 3.16+
- **Git**（FetchContent 需要拉取依赖）

### 一键构建

```batch
build.bat
```

输出文件：`build\bin\Release\QRCodeTool.exe`

### 手动构建

```batch
cmake -B build -G "Visual Studio 17 2022" -A Win32
cmake --build build --config Release --parallel
```

### 构建选项说明

CMakeLists.txt 中的关键编译选项：

| 选项 | 值 | 说明 |
|------|-----|------|
| `CMAKE_MSVC_RUNTIME_LIBRARY` | `MultiThreaded` | 静态链接 CRT（/MT），无需 VC++ Redistributable |
| `/O2 /Os` | 优化 | 最大速度 + 优先小代码 |
| `/GL /LTCG` | 链接时优化 | 全程序优化，减小体积 |
| `/OPT:REF /OPT:ICF` | 链接器 | 移除未引用函数，COMDAT 折叠 |
| `NOMINMAX` | 宏定义 | 避免 Windows min/max 宏与 std::min/std::max 冲突 |

## 架构设计

### 数据流

```
┌─────────────────────────────────────────────────────────┐
│                      MainWindow                         │
│                                                         │
│  文本输入 ──→ QrGenerator ──→ HBITMAP ──→ WM_PAINT 绘制 │
│                                                         │
│  截图/上传 ──→ HBITMAP ──→ QrDecoder ──→ 解码文本       │
│                              ↓                          │
│                         Compressor                       │
│                    (Brotli+Base45 解压)                   │
│                              ↓                          │
│                        SetText → GenerateQr              │
└─────────────────────────────────────────────────────────┘
```

### QR 码生成流程

```
输入文本
  │
  ├─ 短文本 → qrcodegen_encodeText(纯文本) → BYTE 模式 QR 码
  │
  ├─ 长文本 → compressText(Brotli+Base45) → "B5:..."
  │           → qrcodegen_encodeText(压缩文本) → ALPHANUMERIC 模式 QR 码
  │
  └─ 超长文本 → compressText(Brotli+Base45) → 按容量切分
               → 每片加 "M5:<页码>/<总页数>/" 头 → 多个 QR 码
               → 工具栏显示翻页控件 ◀ 1/3 ▶
```

### 多页二维码协议（M5:）

当压缩后的文本仍超出单个 QR 码容量时，自动启用多页模式：

| 字段 | 格式 | 示例 |
|------|------|------|
| 前缀 | `M5:` | `M5:` |
| 页码 | `<当前页>/<总页数>/` | `1/3/` |
| 数据 | Base45 编码的分片 | `...` |

完整示例：`M5:1/3/ABCDE...`、`M5:2/3/FGHIJ...`、`M5:3/3/KLMNO...`

**扫描拼合流程：**
1. 扫描到 `M5:` 前缀 → 识别为多页二维码
2. `MultiPageAssembler` 收集各页分片
3. 弹窗提示"已扫描第 X 页，还缺第 Y、Z 页"
4. 全部收齐 → 按页码排序拼合 → Brotli 解压 → 显示原文

### QR 码解码流程（多策略）

```
HBITMAP → toGrayscale(统一转 32bppARGB) → 灰度数组
  │
  ├─ Strategy 0: 直接灰度 → ZXing HybridBinarizer 解码
  │
  ├─ Strategy 1: 二值化(5阈值) + 最近邻缩放(2-5x) → ZXing 解码
  │
  ├─ Strategy 2: 最近邻缩放(2-5x) → ZXing 解码
  │
  └─ Strategy 3: 反色 + 二值化 + 缩放 → ZXing 解码
```

### 压缩编解码

| 前缀 | 编码 | 解码 | 状态 |
|------|------|------|------|
| `M5:` | Brotli 压缩 → Base45 编码 → 按容量分页 | 收集全部分片 → Base45 解码 → Brotli 解压 | **多页模式** |
| `B5:` | UTF-8 → Brotli 压缩 → Base45 编码 | Base45 解码 → Brotli 解压 | **单页压缩** |
| `GZ:` | （不再生成） | Base64 解码 → GZip 解压 | 仅兼容旧数据 |
| 无前缀 | 原文直接编码 | 原文 | 短文本 |

### 纠错等级

| 等级 | 容错率 | 数据容量 | 适用场景 |
|------|--------|----------|----------|
| L | 7% | 最大 | 默认，追求最大容量 |
| M | 15% | 较大 | 一般使用 |
| Q | 25% | 中等 | 有损环境 |
| H | 30% | 最小 | 高容错需求 |

## 关键源文件说明

### MainWindow.h/cpp — 主窗口

- 纯 Win32 API 构建 UI，无设计器拖拽
- `WM_NCCREATE` 阶段设置 `m_hWnd`（确保 `BuildUI` 时窗口句柄有效）
- `WM_PAINT` 自定义绘制 QR 码（1:1 等比缩放，居中显示）
- `EditSubclassProc` 处理 Ctrl+A 全选
- 300ms 防抖定时器处理文本变化
- `SetText` 后直接调用 `GenerateQr()`（`SetWindowTextW` 不保证触发 `EN_CHANGE`）

### QrGenerator.h/cpp — QR 码生成

- 使用 qrcodegen C 版 API（`qrcodegen_encodeText`）
- 自动压缩策略：先尝试纯文本，失败再压缩，压缩后仍超限则自动分页
- `generateQrPages()` — 多页生成：整体压缩后按容量切分，每片加 `M5:` 头
- 强制 4 模块静区（quiet zone），符合 QR 码标准
- 输出 24 位 DIB 位图，白底黑模块

### QrDecoder.h/cpp — QR 码解码

- 使用 zxing-cpp 的 `QRCode::Reader`（非 `MultiFormatReader`，避免拉入其他条码格式）
- `HybridBinarizer` 自适应二值化（和 C# 版 ZXing.Net 同源）
- `tryHarder` + `tryRotate` 提高识别率
- 最近邻缩放（`scaleGrayscale`）保持黑白锐利边界，避免 GDI+ 双三次插值模糊

### Compressor.h/cpp — 压缩/解压

- Brotli 压缩：quality=11, lgwin=22, mode=GENERIC
- Brotli 解压：先尝试一次性解压，失败回退流式解压
- GZip 解压：zlib `inflateInit2` + windowBits=31（仅兼容旧 "GZ:" 格式）
- `MultiPageAssembler` — 多页二维码拼合器
  - `addPage(page, total, data)` — 添加扫描到的分片
  - `isComplete()` — 判断是否收齐所有页
  - `assemble()` — 按页码排序拼合 → Base45 解码 → Brotli 解压
  - `getMissingPages()` — 获取缺失页码列表
  - `reset()` — 清空已收集数据

### Activation/ — 激活码验证模块

- 从 C# 版 `activation-code-verifier` 移植为 C++ 静态库
- `ActivationGuard` — 验证并保护，启动时检查激活码有效性
- `DeviceInfo` — 设备指纹采集（CPU+主板+硬盘+MAC → SHA256 → 设备ID）
- `CryptoUtil` — RSA 签名验证、AES-256-CBC 加解密、HMAC-SHA256
- `LicenseStore` — 激活码本地存储（AES 加密存储到文件）
- `RsaKey.h` — 内嵌 RSA 公钥（PEM 格式）
- 激活弹窗显示唯一序列号和激活地址，支持输入激活码

### Base45.h/cpp — Base45 编解码

- RFC 9285 标准实现
- 字母表：`0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ $%*+-./:`
- 编码：2 字节 → 3 字符，1 字节 → 2 字符
- 解码：3 字符 → 2 字节，2 字符 → 1 字节

### ImageProcess.h/cpp — 图像处理

- `toGrayscale`：统一转换为 32bppARGB 再处理（兼容所有源像素格式）
- BT.601 灰度公式：`0.299*R + 0.587*G + 0.114*B`
- Otsu 大津法自动阈值
- 对比度增强、反色、三段式去噪

### ScreenCapture.h/cpp — 屏幕截图

- `GetSystemMetrics(SM_XVIRTUALSCREEN)` 获取虚拟屏幕尺寸（支持多显示器）
- 全屏顶层弹出窗口 + 30% 半透明暗色遮罩
- 鼠标拖选区域，绿色边框实时反馈
- ESC 取消，选区小于 40x40 自动取消
- 使用屏幕 DC 创建兼容位图（确保 32 位真彩色）

## 开发过程中遇到的问题与解决方案

### 1. 窗口创建后点击没反应

**问题**：`WM_CREATE` 触发 `BuildUI()` 时，`m_hWnd` 仍为 NULL（`CreateWindowExW` 尚未返回），导致所有子控件以 NULL 父窗口创建，控件不可见也不响应。

**解决**：在 `WM_NCCREATE` 阶段提前设置 `pThis->m_hWnd = hWnd`，确保后续 `WM_CREATE` → `BuildUI()` 时 `m_hWnd` 已有效。

### 2. 编辑框不支持 Ctrl+A 全选

**问题**：Win32 标准 EDIT 控件默认不处理 Ctrl+A 快捷键。

**解决**：通过 `SetWindowSubclass` 子类化编辑框，拦截 `WM_CHAR` 消息中 `wParam == 1`（Ctrl+A），发送 `EM_SETSEL(0, -1)` 全选文本。

### 3. QR 码生成后无法识别

**问题**：多个原因叠加导致：
- 小版本 QR 码静区不足 4 模块
- STATIC 控件 `SS_REALSIZECONTROL` 拉伸位图导致 QR 码变形
- `CreateCompatibleBitmap(NULL DC)` 创建的是 1 位单色位图

**解决**：
- 强制保留 4 模块宽度的白色静区
- 移除 STATIC 控件，改为 `WM_PAINT` 自定义绘制，1:1 等比缩放
- 使用屏幕 DC 创建兼容位图，确保 32 位真彩色

### 4. quirc 解码器识别能力不足

**问题**：quirc 使用简单全局阈值，对截图中的 QR 码解码能力远不如 C# 版 ZXing.Net（带 HybridBinarizer 自适应二值化）。quirc 能检测到 QR 码位置但解码失败（ECC 校验错误）。

**解决**：将 quirc 替换为 zxing-cpp（ZXing 的 C++ 移植版），和 C# 版使用同一套解码引擎。使用 `QRCode::Reader` 直接解码（而非 `MultiFormatReader`），避免拉入其他条码格式的代码。

### 5. GDI+ 缩放导致 QR 码解码失败

**问题**：`InterpolationModeHighQualityBicubic` 双三次插值在黑白 QR 码边缘产生灰色中间值，导致解码器无法正确识别模块边界。

**解决**：改用最近邻缩放（`scaleGrayscale`），直接对灰度数组做整数倍放大，保持黑白锐利边界。同时在解码前做二值化处理（0 或 255），消除抗锯齿。

### 6. toGrayscale 不支持所有像素格式

**问题**：`toGrayscale` 只处理 `PixelFormat24bppRGB` 和 `PixelFormat32bppARGB`，截图位图通常是 `PixelFormat32bppPARGB`（预乘 Alpha），导致灰度数组全是 255（白色），解码器看到白图。

**解决**：统一用 `LockBits` 转换为 `PixelFormat32bppARGB` 再处理，兼容所有源像素格式。

### 7. Base45 解码 bug

**问题**：`base45Decode` 中检查尾部 2 字符时用了 `i + 1 == len`，应该是 `i + 2 == len`。循环 `while (i + 2 < len)` 结束后 `i` 指向剩余字符起始位置，剩余 2 字符时 `i + 2 == len` 才正确。

**解决**：修正条件为 `i + 2 == len`。

### 8. 识别后二维码不回显

**问题**：截图识别成功后文本框填入文字，但二维码区域不更新。`SetWindowTextW` 程序化设置文本时，`EN_CHANGE` 通知不一定可靠触发。

**解决**：`SetText` 在设置文本后直接调用 `GenerateQr()`，确保二维码同步更新。

### 9. Runtime Error 异常崩溃

**问题**：`compressText` 和 `brotliCompress` 会抛出 `std::runtime_error` 异常，但 `GenerateQr()` 等调用方没有捕获。

**解决**：给 `GenerateQr()`、`OnCapture()`、`OnUpload()` 等关键函数加 try-catch，防止未捕获异常导致程序崩溃。

### 10. 多显示器 DPI 缩放导致坐标偏移

**问题**：Windows 在副屏上自动 DPI 缩放，导致截图坐标和实际位置不匹配。

**解决**：在 `WinMain` 中调用 `SetProcessDPIAware()` 声明 DPI 感知，避免系统自动缩放。

### 11. Windows min/max 宏与 std::min/std::max 冲突

**问题**：`<windows.h>` 定义了 `min`/`max` 宏，与 `<algorithm>` 中的 `std::min`/`std::max` 冲突。

**解决**：在 CMakeLists.txt 中全局定义 `NOMINMAX` 宏，并在代码中使用 `(std::min)` 带括号的写法防止宏展开。

### 12. CRT 链接不一致

**问题**：主程序使用 `/MT`（静态 CRT），但 brotli/zlib 默认使用 `/MD`（动态 CRT），混合链接会导致运行时问题。

**解决**：通过 `set_property(TARGET ... PROPERTY MSVC_RUNTIME_LIBRARY "MultiThreaded")` 强制所有依赖库使用 `/MT`。

### 13. exe 体积偏大

**问题**：初始构建约 1.8MB，主要因为 zxing-cpp 包含所有条码格式的解码器。

**解决**：
- 不使用 `MultiFormatReader`，改用 `QRCode::Reader` 直接解码
- 只编译 QR 相关源文件（约 30 个 .cpp）到自定义 `zxing_qr` 静态库
- 启用 `/GL /LTCG /OPT:REF /OPT:ICF` 链接时优化
- 最终体积降至 ~1.4MB

## 已知限制与后续优化方向

### 待优化项

1. **多页扫描体验** — 当前每扫一页弹一次 MessageBox，需反复点击确定。可改为界面内嵌进度条，多页时进入自动连续截图模式（轻量浮层提示进度，自动重新进入截图选区），无需手动切换。

2. **多页二维码视觉标识** — 每页二维码看起来一样，无法肉眼区分页码。可在二维码下方渲染页码文字（如"第1页/共3页"）。

3. **重复扫描同一页无提示** — 扫到已收集的页只写日志，用户无感知。应弹窗或浮层提示"该页已扫描"。

4. **截图扫描窗口遮挡** — 多页扫描时，每次都要"截图 → 回主窗口 → 看弹窗 → 再点截图"，来回切换割裂。连续截图模式可解决。

5. **日志系统** — 当前使用简单的文件追加写入（`qr_debug.log`），生产环境应移除或改为条件编译。

6. **移除 zlib 依赖** — 当前仅用于兼容旧 "GZ:" 格式的 GZip 解压。如果确认不再需要兼容旧数据，可移除 zlib 和 `gzipDecompress` 函数，预计减小约 100KB。

### 待扩展功能

1. **二维码导出** — 加"保存为 PNG"按钮，多页时批量导出所有页面。

2. **拖拽识别** — 支持拖拽图片文件到窗口直接识别二维码，不用点上传。

3. **剪贴板监听** — 监听剪贴板中的图片，自动识别二维码。

4. **历史记录** — 记录最近生成/扫描的内容，方便回看，支持点击重新加载。

5. **批量生成** — 输入多行文本，每行生成一个二维码，方便批量打印。

6. **二维码美化** — 加 Logo、改颜色、圆角模块等，提升视觉辨识度。

7. **WiFi 二维码** — 输入 WiFi 信息生成 `WIFI:T:WPA;S:xxx;P:xxx;;` 格式二维码，手机扫了直接连网。

8. **名片二维码** — 生成 vCard 格式二维码。

### 不建议改动

- **解码引擎**：zxing-cpp 的 `HybridBinarizer` 是识别率的关键，不要换回 quirc
- **CRT 链接方式**：保持 `/MT` 静态链接，确保零运行时依赖
- **压缩算法**：Brotli+Base45 是和 C# 版一致的方案，改换会导致跨版本不兼容；且 Brotli 已是通用压缩算法中压缩率最高的，换其他算法提升微乎其微
- **多页协议**：M5: 协议需与扫描端保持一致，不可随意修改前缀格式
- **编码方式**：Base45 + QR 字母数字模式 vs 二进制模式的容量差异仅约 3%，不值得改

## 与 C# 版的兼容性

两个版本的 QR 码完全互通：

- C# 版生成的压缩 QR 码（B5: 前缀）→ C++ 版可识别并解压
- C++ 版生成的压缩 QR 码（B5: 前缀）→ C# 版可识别并解压
- C++ 版还兼容 C# 旧版的 GZ: 前缀格式
- 纯文本 QR 码任何扫码器都能识别

## 许可证

本项目使用的第三方库许可证：

| 库 | 许可证 |
|----|--------|
| qrcodegen | MIT |
| zxing-cpp | Apache 2.0 |
| brotli | MIT |
| zlib | zlib License |
