# QRCodeTool (Rust 版)

基于 Rust + iced 开发的二维码生成与识别工具，支持长文本压缩编码（~9500字符），零运行时依赖，单文件分发。

## 技术栈

| 组件 | 技术 | 说明 |
|---|---|---|
| 语言 | Rust 2021 Edition | 零成本抽象，内存安全 |
| GUI 框架 | iced 0.13 + tiny-skia | CPU 软件渲染，无需 GPU |
| QR 生成 | qrcodegen 1.8 | 字母数字模式，支持 V1-V40 |
| QR 识别 | rqrr 0.9 | 多策略解码 |
| 压缩 | brotli 7 + base45 3 | Brotli 压缩 + Base45 编码 |
| 图像处理 | image 0.25 | 缩放/二值化/Otsu/反色/去噪 |
| 截图选区 | Win32 API | 全屏截图 + 半透明遮罩 + 鼠标拖选 |
| 文件对话框 | rfd 0.15 | 上传图片识别 |

## 项目结构

```
QR_GENERATORBYRUST/
├── Cargo.toml          # 项目配置与依赖
├── build.rs            # 构建脚本（嵌入图标资源）
├── src/
│   ├── main.rs         # 入口，窗口配置，图标生成
│   ├── app.rs          # iced 主界面（Elm 架构）
│   ├── compress.rs     # Brotli + Base45 / GZip 压缩编码
│   ├── qr_encode.rs    # QR 码生成（字母数字模式）
│   ├── qr_decode.rs    # 7 组多策略解码
│   ├── image_proc.rs   # 7 种图像处理算法
│   ├── screenshot.rs   # Win32 截图 + 选区遮罩
│   └── logger.rs       # 文件日志
└── target/release/
    └── qr_code_tool.exe  # 编译产物（~10MB）
```

## 功能清单

### QR 码生成

- **压缩模式**（默认开启）：原始文本 → Brotli 压缩 → Base45 编码 → "B5:" 前缀 → QR 字母数字模式
  - 容量：~9500 字符（V40-L 级）
  - 纯字母数字编码，比字节模式多约 60% 容量
- **非压缩模式**：原始文本直接编码为 QR 字节模式
- **向后兼容**：自动识别 "GZ:" 前缀（GZip 压缩）和 "B5:" 前缀（Brotli 压缩）
- **纠错等级**：L(7%) / M(15%) / Q(25%) / H(30%)，默认 L 级
- **输出**：600×600 像素 RGBA 图片

### QR 码识别

7 组串行多策略解码，每组失败后尝试下一组：

| 组 | 策略 | 说明 |
|---|---|---|
| 1 | 原图 + TRY_HARDER | 直接解码 |
| 2 | 缩放 1.5x | 放大后解码 |
| 3 | 阈值二值化 | 固定阈值 128 |
| 4 | Otsu 自适应阈值 | 自动计算最佳阈值 |
| 5 | 对比度增强 | 1.5x 对比度 |
| 6 | 反色 | 黑白反转 |
| 7 | 去噪 | 三段式去噪（<80→0, >175→255, 其余不变） |

### 截图识别

1. 全屏截图（支持多显示器虚拟屏幕）
2. 半透明遮罩覆盖全屏
3. 鼠标拖选识别区域，实时显示选区边框和尺寸
4. ESC 取消，最小选区 40×40
5. 裁剪选区后后台异步解码

### 上传识别

- 支持格式：PNG / JPG / JPEG / BMP
- 后台异步解码，不阻塞 UI

## 设计方案

### 渲染架构

采用 **iced + tiny-skia** CPU 软件渲染方案：

```
iced (GUI 框架)
  └── tiny-skia (CPU 软件渲染器)
        ├── Skia 算法子集
        ├── 纯 Rust 实现
        └── 无需 GPU / DirectX / OpenGL
```

**选择理由**：部分老旧电脑不支持 DirectX 11 或 OpenGL 2.0，GPU 渲染方案（wgpu/glow）无法运行。tiny-skia 纯 CPU 渲染，兼容所有 Windows 机器。

### 压缩编码流程

```
原始文本
  → Brotli 压缩（质量级别 11）
  → Base45 编码（RFC 9285）
  → 添加 "B5:" 前缀
  → QR 字母数字模式编码（容量 4296 字符）
  → 实际可编码 ~9500 字符原始文本
```

### Elm 架构

```
Model (状态) ←→ update(Message) ←→ view() → Element
     ↑                                    |
     └──────── Message ───────────────────┘
```

- **Model**：文本内容、压缩模式、纠错等级、QR 图片、解码状态
- **Message**：文本变化、压缩切换、纠错选择、截图/上传、解码结果
- **update**：处理消息，更新状态
- **view**：根据状态渲染界面

## 遇到的问题与解决方案

### 1. 中文乱码

**问题**：iced 默认字体不包含中文字符，工具栏和提示文字显示为方块或乱码。

**解决方案**：启动时加载系统微软雅黑字体（`C:\Windows\Fonts\msyh.ttc`），设为默认字体。

```rust
let font_bytes = std::fs::read("C:\\Windows\\Fonts\\msyh.ttc").unwrap();
iced::application(...)
    .font(Box::leak(font_bytes.into_boxed_slice()))
    .default_font(iced::Font::with_name("Microsoft YaHei"))
```

### 2. GPU 渲染兼容性

**问题**：最初使用 egui + wgpu (DirectX) 渲染，部分老旧电脑报错：
- `WGPU ERROR: FAILED TO CREATE WGPU ADAPTER, NO SUITABLE ADAPTER FOUND`
- `egui_glow: OpenGL: egui_glow requires opengl 2.0+`

**解决方案**：从 egui 切换到 iced + tiny-skia，使用 CPU 软件渲染，完全不依赖 GPU。

| 方案 | 兼容性 | 体积 | CPU 占用 |
|---|---|---|---|
| egui + wgpu | 需要 DirectX 11 | 9 MB | 低 |
| egui + glow | 需要 OpenGL 2.0 | 7 MB | 低 |
| **iced + tiny-skia** | **所有机器** | **10 MB** | **略高（~3-5%）** |

### 3. 文本输入框无法输入

**问题**：iced 0.13 的 `text_editor` 组件需要显式绑定 `on_action` 回调，否则不接收键盘输入。

**解决方案**：

```rust
text_editor(&self.text_content)
    .on_action(Message::TextChanged)  // 必须绑定
```

### 4. 文本框滚动异常

**问题**：text_editor 外层套了 scrollable，导致滚动到底部时文字消失（双层滚动冲突）。

**解决方案**：text_editor 设为 `Length::Shrink`（随内容增长），外层 scrollable 设为 `Length::Fill`（填充剩余空间），由 scrollable 统一管理滚动。

### 5. 任务栏图标

**问题**：程序运行时任务栏显示默认图标，固定到任务栏后图标不正确。

**解决方案**：通过 `build.rs` 构建脚本，用 `embed-resource` 将 ICO 图标嵌入 exe 资源段，Windows 资源管理器和任务栏都会读取该图标。

### 6. iced 0.13 API 变更

**问题**：iced 0.13 移除了 `Sandbox` trait 和 `Application` trait，改为函数式 API。

**解决方案**：

```rust
// 旧 API (iced 0.12)
impl Sandbox for App { ... }

// 新 API (iced 0.13)
iced::application("标题", Self::update, Self::view)
    .window(settings)
    .run()
```

## 编译构建

### 环境要求

- Rust 工具链（rustup）：安装到 `E:\huliang\softWare\rust`
- MSVC Build Tools（C++ 编译器）

### 编译命令

```powershell
$env:RUSTUP_HOME = "E:\huliang\softWare\rust\rustup"
$env:CARGO_HOME = "E:\huliang\softWare\rust\cargo"
$env:PATH = "E:\huliang\softWare\rust\cargo\bin;" + $env:PATH
cargo build --release
```

### 编译产物

- 路径：`target/release/qr_code_tool.exe`
- 大小：~10 MB
- 依赖：无（静态链接，单文件分发）

### Release 优化配置

```toml
[profile.release]
strip = true          # 去除调试符号
lto = "thin"          # 链接时优化
codegen-units = 1     # 单编译单元，更优优化
opt-level = "z"       # 最小体积优化
panic = "abort"       # 减少 panic 处理代码
```

## 与 C# 版对比

| 指标 | C# 版 (.NET self-contained) | Rust 版 (iced + tiny-skia) |
|---|---|---|
| exe 大小 | 49 MB | **10 MB**（缩减 80%） |
| 运行时依赖 | 无 | 无 |
| 渲染方式 | GDI+ (CPU) | tiny-skia (CPU) |
| 启动速度 | 中等 | 快 |
| 内存占用 | ~80-120 MB | ~30-50 MB |
| GPU 要求 | 无 | 无 |
| 激活码验证 | 已集成 | 待移植 |
