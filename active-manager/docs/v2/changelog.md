# 激活码管理系统 V2 - 迭代记录

## 迭代概述

本次迭代基于V1版本进行了多轮优化，主要涉及：安全加固、架构分工明确、管理后台完善、客户端接入简化。

---

## 迭代1：架构分工与唯一序列号

### 变更内容

1. **明确三大工程分工**
   - activation-code-server：激活码服务端，生成并管理激活码
   - activation-code-verifier：激活码验证工具，提供API供工具软件接入
   - QRCodeTool：二维码扫描工具，只做工具功能，接入verifier即可

2. **唯一序列号机制**
   - 客户端弹窗从显示"设备ID+机器码"两个字段改为只显示"唯一序列号"一个加密值
   - 唯一序列号 = `初始序列号|设备ID|机器码` → XOR 0x5A → Base64
   - 服务端自动解密唯一序列号，解析出设备ID和机器码

3. **设备绑定验证**
   - 客户端验证改为 `CheckWithAutoDevice`，激活码绑定设备
   - 定时检查改为 `StartPeriodicCheckWithAutoDevice`，过期后同样校验设备

### 涉及文件

| 文件 | 变更 |
|------|------|
| DeviceInfo.cs | 新增 GetSerialNumber / ParseSerialNumber / SerialNumberInfo |
| ActivationGuard.cs | 弹窗改为只显示唯一序列号 |
| Program.cs (QRCodeTool) | 改用 CheckWithAutoDevice |
| ActivationService.java | 新增唯一序列号解密逻辑，自动解析设备ID和机器码 |
| GenerateResponse.java | 新增 initialSerial / machineCode 字段 |
| index.html | 管理后台显示解析结果 |

---

## 迭代2：管理后台完善

### 变更内容

1. **管理后台四个标签页**
   - 激活码管理工具：生成激活码 + 验证激活码
   - 激活码记录：查询所有记录，搜索/筛选
   - 操作日志：稽核日志，按事件类型筛选
   - 解析工具：解析激活码/序列号

2. **独立生成页面**
   - 新增 tool.html，简洁的激活码生成页面
   - 有效期最长30天
   - 管理后台有效期支持5分钟/1小时/1天/30天/1年

3. **浏览器缓存问题修复**
   - 新增 WebMvcConfig.java，静态资源禁用缓存
   - HTML添加 Cache-Control / Pragma / Expires meta标签

### 涉及文件

| 文件 | 变更 |
|------|------|
| index.html | 四标签页管理后台 |
| tool.html | 新增独立生成页面 |
| admin.html | 重定向到index.html |
| WebMvcConfig.java | 新增，静态资源不缓存 |

---

## 迭代3：安全加固

### 变更内容

1. **激活码文件加密存储**
   - 新增 SecureStorage.cs
   - AES-256-CBC加密，密钥由设备ID + PBKDF2派生
   - 文件从 activation.lic(明文) 改为 activation.dat(加密)
   - 绑定设备：不同设备无法解密，防止复制文件
   - 安全删除：覆写全零后再删除

2. **错误信息脱敏**
   - VerifyResult.cs 移除 Message 字段
   - ActivationVerifier.cs 所有失败统一返回 VerifyResult.Fail()
   - 不再返回"签名验证失败""格式无效"等详细信息

3. **公钥加密增强**
   - 从单字节 XOR 0x5A 改为4字节分段循环 XOR (0x3A, 0x7C, 0xE5, 0x91)
   - 攻击者需同时猜对4个key才能还原公钥

4. **DLL项目配置更新**
   - 目标框架从 net6.0 改为 net6.0-windows
   - 新增 UseWindowsForms 支持（弹窗在DLL中实现）

5. **一站式启动保护 API**
   - 新增 ActivationGuard.LaunchWithProtection(initialSerial)
   - 一行代码完成：加载缓存→验证→弹窗→定时检查→加密存储
   - Program.cs 从268行简化到21行

### 涉及文件

| 文件 | 变更 |
|------|------|
| SecureStorage.cs | 新增，AES加密存储 |
| ActivationGuard.cs | 重写，新增LaunchWithProtection，公钥4字节XOR，弹窗内置 |
| ActivationVerifier.cs | 重写，错误脱敏，反调试检测 |
| VerifyResult.cs | 重写，移除Message字段 |
| DeviceInfo.cs | 无变更 |
| TimeGuard.cs | 无变更 |
| AntiDebug.cs | 无变更 |
| ActivationCodeVerifier.csproj | net6.0-windows + UseWindowsForms |
| Program.cs (QRCodeTool) | 简化为21行 |

---

## 迭代4：过期退出Bug修复

### 问题描述

二维码工具启动后，激活码过期弹窗提示"授权已失效"，但主窗口并未关闭，用户仍可继续使用工具。

### 根因分析

`ShowExpiredDialog` 从 `System.Threading.Timer` 的线程池线程调用：
- `MessageBox.Show` 在后台线程上不是真正模态的，主窗口仍可操作
- `Environment.Exit` 从后台线程调用不能正确终止WinForms消息循环

### 修复方案

1. 通过 `Application.OpenForms[0].Invoke()` 将弹窗和退出操作调度到UI线程
2. 先调用 `Application.Exit()` 正确关闭WinForms消息循环和所有窗口
3. 最后 `Environment.Exit(1002)` 作为兜底强制退出

### 修复后流程

```
定时检查发现过期
  → 停止定时器
  → 安全删除activation.dat
  → UI线程弹出模态对话框（主窗口不可操作）
  → 用户点击确定
  → Application.Exit() 关闭所有窗口
  → Environment.Exit(1002) 兜底退出
```

### 涉及文件

| 文件 | 变更 |
|------|------|
| ActivationGuard.cs | ShowExpiredDialog改为UI线程调用，Application.Exit + Environment.Exit |

---

## 迭代5：V2文档编写

### 变更内容

基于多轮迭代的实际代码，重新编写四份文档：

| 文档 | 角度 | 核心内容 |
|------|------|---------|
| requirements.md | 业务 | 三大工程定位、功能需求编号、业务流程、数据需求 |
| design.md | 方案 | 架构图、加密体系、安全四层防护、接口设计、数据库设计 |
| development.md | 开发 | 工程结构、编译打包、DLL接入3步法、发布流程 |
| usage.md | 使用 | 管理员/终端用户/开发者三视角操作指南、FAQ |

与V1文档的主要差异：
- 三大工程分工明确写入文档
- 安全加固完整记录（AES加密存储、4字节XOR、错误脱敏、单调时钟）
- 一站式接入API替代旧版分步接入
- 有效期单位从天改为分钟
- 稽核日志6种事件类型

---

## 迭代6：DLL强名称签名 + Obfuscar代码混淆

### 变更内容

1. **DLL强名称签名**
   - 生成RSA 2048位密钥文件 key.snk
   - csproj添加 `SignAssembly=true` + `AssemblyOriginatorKeyFile=key.snk`
   - 新增 AssemblyInfo.cs 手动管理程序集信息（因禁用自动生成）
   - 效果：DLL带有强名称公钥标记，攻击者无法用同名假DLL替换（签名不匹配会加载失败）

2. **Obfuscar代码混淆**
   - 安装 `Obfuscar.GlobalTool` 全局工具
   - 创建 obfuscar.xml 配置文件，混淆策略：
     - 公共API保留（LaunchWithProtection、CheckWithAutoDevice等），工具软件可正常调用
     - 私有方法/字段/属性/事件全部重命名为Unicode字符
     - 字符串隐藏（密钥、文件路径等不再明文可见）
     - 命名空间扁平化
     - 混淆后用key.snk重新签名，保持强名称有效
   - 输出：`bin\Obfuscated\Jones.Activation.dll`

3. **构建流程更新**

```
dotnet build -c Release          → 编译带签名的DLL
obfuscar.console obfuscar.xml    → 混淆+重签名
复制 bin\Obfuscated\Jones.Activation.dll → 工具软件\lib\
dotnet publish                   → 打包单文件exe
```

### 涉及文件

| 文件 | 变更 |
|------|------|
| key.snk | 新增，RSA 2048位强名称密钥 |
| AssemblyInfo.cs | 新增，手动管理程序集属性 |
| ActivationCodeVerifier.csproj | 添加SignAssembly、AssemblyOriginatorKeyFile、GenerateAssemblyInfo=false、排除tmp_keygen |
| obfuscar.xml | 新增，Obfuscar混淆配置 |

### 说明

- ConfuserEx不支持.NET 6程序集，改用Obfuscar替代（开源免费，支持.NET 6+）
- 强名称签名 + 代码混淆双重保护：签名防止DLL被替换，混淆防止逆向分析
- key.snk必须妥善保管，丢失后无法重新签名同名DLL

---

## 安全加固前后对比

| 安全措施 | V1 | V2 |
|---------|----|----|
| 公钥存储 | 单字节XOR 0x5A | 4字节循环XOR (0x3A,0x7C,0xE5,0x91) |
| 激活码文件 | 明文 activation.lic | AES-256-CBC加密 activation.dat |
| 文件绑定设备 | 不绑定，可复制 | PBKDF2(设备ID)派生密钥，不可跨设备 |
| 错误信息 | 返回详细原因 | 脱敏，不返回任何文字描述 |
| 过期退出 | 后台线程弹窗，主窗口不关闭 | UI线程模态弹窗，Application.Exit关闭 |
| 文件删除 | File.Delete | 覆写全零后删除 |
| 内存清理 | payload/signature清零 | 同V1 + 公钥encryptedKey清零 + AES密钥清零 |
| DLL签名 | 无强名称，可被替换 | RSA 2048位强名称签名，替换后加载失败 |
| 代码混淆 | 无混淆，IL可反编译 | Obfuscar混淆：私有成员Unicode重命名+字符串隐藏 |

---

## 待实施项

| 项目 | 优先级 | 说明 |
|------|--------|------|
| ~~ConfuserEx代码混淆~~ | ~~中~~ | 已用Obfuscar替代实现 |
| ~~DLL强名称签名~~ | ~~中~~ | 已实现 |
| 服务端Token机制 | 低 | 需客户端联网，当前场景为离线工具，暂不实施 |
| VMProtect虚拟化 | 低 | 商业工具，成本较高 |

---

## 迭代7：管理后台登录功能 + 页面路径重构

### 变更内容

1. **管理后台登录功能**
   - 新增 AdminUser 实体类 + AdminUserMapper
   - 新增 AuthController：登录/登出/会话检查/修改密码
   - 新增 AuthInterceptor：未登录API返回401，页面请求重定向到登录页
   - WebMvcConfig 注册拦截器，排除登录页/工具页/验证接口
   - 登录页 login.html：紫色渐变卡片式设计
   - 管理后台 main.html：header显示用户名+退出按钮，401自动跳转登录
   - 密码加密：SHA-256 + 随机盐值
   - 默认管理员账号：admin / admin123（启动时自动初始化）

2. **页面路径重构**
   - 所有页面迁移到 `/activecode/` 子目录
   - 旧路径 → 新路径：
     - `/index.html` → `/activecode/main.html`（管理后台）
     - `/login.html` → `/activecode/login.html`（登录页）
     - `/tool.html` → `/activecode/index.html`（独立工具页）
   - 删除旧的4个HTML文件（index.html, login.html, tool.html, admin.html）
   - 后端拦截器重定向路径同步更新

### 涉及文件

| 文件 | 变更 |
|------|------|
| AdminUser.java | 新增，管理员实体类 |
| AdminUserMapper.java | 新增，管理员Mapper |
| AuthController.java | 新增，登录/登出/会话/改密码控制器 |
| AuthInterceptor.java | 新增，认证拦截器 |
| WebMvcConfig.java | 修改，注册拦截器+更新排除路径 |
| DatabaseInitializer.java | 修改，新增admin_user建表+初始化管理员 |
| activecode/main.html | 新增，管理后台（原index.html） |
| activecode/login.html | 新增，登录页面 |
| activecode/index.html | 新增，独立工具页（原tool.html） |
| index.html (旧) | 删除 |
| login.html (旧) | 删除 |
| tool.html (旧) | 删除 |
| admin.html (旧) | 删除 |

---

## 迭代8：C++ 版验证工具（activation-code-verifier/cpp）

### 背景

原有 QRCodeTool 用 C# .NET 6 开发，自包含打包后 49MB。C++ 重写后仅 1.4MB。为使 C++ 版 QRCodeTool 也能接入激活码验证，需将 C# 版 verifier 移植为 C++ 静态库 `JonesActivation.lib`，保持与 C# 版完全兼容。

### 架构设计

遵循项目规范：**验证逻辑全部在 verifier 中，工具软件只做业务功能**。

```
activation-code-verifier/
└── cpp/                          # C++ 版（新增）
    ├── CMakeLists.txt            # 构建 JonesActivation.lib
    ├── include/Jones/            # 公开头文件
    │   ├── ActivationGuard.h
    │   ├── ActivationVerifier.h
    │   ├── ActivationVerifyResult.h
    │   ├── ActivationDeviceInfo.h
    │   ├── ActivationCrypto.h
    │   ├── ActivationAntiDebug.h
    │   ├── ActivationTimeGuard.h
    │   └── ActivationSecureStorage.h
    └── src/                      # 实现文件

QRCodeTool (C++)/
├── CMakeLists.txt                # 链接 JonesActivation.lib
└── src/main.cpp                  # #include "Jones/ActivationGuard.h"
                                   # ActivationGuard::LaunchWithProtection("QRCodeTool")
```

### 接入方式（与 C# 版一致）

```cpp
#include "Jones/ActivationGuard.h"

// 一行代码完成：加载缓存→验证→弹窗→定时检查→加密存储
if (!ActivationGuard::LaunchWithProtection("QRCodeTool")) {
    return 0;  // 用户取消激活
}
```

### 技术选型

| 功能 | C# 版 | C++ 版 |
|------|-------|--------|
| SHA256 | System.Security.Cryptography | Windows CNG (BCrypt) |
| RSA 验签 | RSACryptoServiceProvider | Windows CryptoAPI (CryptVerifySignature) |
| AES-256-CBC | Aes.Create() | Windows CNG (BCryptEncrypt/Decrypt) |
| HMAC-SHA256 / PBKDF2 | HMACSHA256 / Rfc2898DeriveBytes | Windows CNG (BCrypt + BCRYPT_ALG_HANDLE_HMAC_FLAG) |
| WMI 查询 | System.Management | COM IWbemServices |
| 设备指纹 | 同 C# | 同 C#（CPU+主板+硬盘+MAC → SHA256） |
| 序列号加密 | XOR 0x5A + Base64 | XOR 0x5A + Base64（完全一致） |
| 防调试 | IsDebuggerPresent + RemoteDebugger | 同 C# + NtQueryInformationProcess |
| 时间篡改 | Stopwatch + 文件缓存 | GetTickCount64 + 文件缓存 |
| 加密存储 | AES-256-CBC + PBKDF2 | AES-256-CBC + PBKDF2（完全一致） |
| CRT 链接 | — | /MT 静态链接，零依赖 |

### 开发过程中遇到的问题与解决方案（重点）

#### 问题1：RSA 公钥 PEM → DER 解码失败

**现象**：`CryptStringToBinary` 返回 error=13（无效数据）

**原因**：手动去掉了 PEM 头尾行（`-----BEGIN PUBLIC KEY-----`）后，剩余的纯 Base64 内容不应再用 `CRYPT_STRING_BASE64HEADER` 标志解码，因为该标志期望输入包含头尾行。

**修复**：`CRYPT_STRING_BASE64HEADER` → `CRYPT_STRING_BASE64`

```cpp
// 错误：已去掉头尾行，不能用 HEADER 标志
CryptStringToBinaryA(base64Content, 0, CRYPT_STRING_BASE64HEADER, ...);

// 正确：纯 Base64 内容用 BASE64 标志
CryptStringToBinaryA(base64Content, 0, CRYPT_STRING_BASE64, ...);
```

#### 问题2：HMAC-SHA256 算法名错误，PBKDF2 派生密钥崩溃

**现象**：`SecureStorage::Save` 日志到 `deviceId=...` 后中断，`activation.dat` 从未写入成功。程序显示"激活成功"但下次启动仍需激活。

**原因**：Windows CNG 中没有 `L"HMACSHA256"` 或 `L"HMAC_SHA256"` 这样的算法标识符。正确方式是打开 `L"SHA256"` 算法 + `BCRYPT_ALG_HANDLE_HMAC_FLAG` 标志。

**修复**：

```cpp
// 错误：CNG 中不存在这些算法名
BCryptOpenAlgorithmProvider(&hAlg, L"HMACSHA256", NULL, 0);   // ❌
BCryptOpenAlgorithmProvider(&hAlg, L"HMAC_SHA256", NULL, 0);  // ❌

// 正确：打开 SHA256 + HMAC 标志
BCryptOpenAlgorithmProvider(&hAlg, BCRYPT_SHA256_ALGORITHM, NULL, BCRYPT_ALG_HANDLE_HMAC_FLAG);  // ✅
```

**影响链**：HMAC 失败 → PBKDF2 返回空向量 → DeriveKey 返回空 → AES 密钥为空 → 加密失败 → Save 返回 false → activation.dat 未写入 → 下次启动仍需激活。

**教训**：Windows CNG 的算法标识符必须严格对照官方文档 [CNG Algorithm Identifiers](https://learn.microsoft.com/en-us/windows/win32/seccng/cng-algorithm-identifiers)，不能凭直觉命名。

#### 问题3：AES-256-CBC 加密失败

**现象**：HMAC 修复后，日志显示 `Save: AES encrypt failed`。

**原因**：`BCryptEncrypt` / `BCryptDecrypt` 的 `BCRYPT_BLOCK_PADDING` 标志应作为 `dwFlags`（最后一个参数）传递，而不是作为 `pPaddingInfo`（第5个参数）指针传递。`BCRYPT_BLOCK_PADDING` 是一个标志值（0x00000001），不是结构体指针。

**修复**：

```cpp
// 错误：把标志值当指针传
DWORD paddingInfo = BCRYPT_BLOCK_PADDING;
BCryptEncrypt(hKey, data, dataLen, &paddingInfo, iv, 16, out, outLen, &outLen, 0);  // ❌

// 正确：paddingInfo 传 NULL，标志放最后
BCryptEncrypt(hKey, data, dataLen, NULL, iv, 16, out, outLen, &outLen, BCRYPT_BLOCK_PADDING);  // ✅
```

**教训**：BCrypt 系列 API 的 padding 参数设计容易混淆。对称加密（AES-CBC）用 `BCRYPT_BLOCK_PADDING` 作为 flag，而非认证加密（AES-GCM）才需要传 `BCRYPT_AUTHENTICATED_CIPHER_MODE_INFO` 结构体作为 paddingInfo。

#### 问题4：MessageBox 中文乱码

**现象**：激活成功/失败弹窗显示乱码。

**原因**：`MessageBoxA` 使用 ANSI 编码（中文 Windows 为 GBK），而源码文件为 UTF-8 编码，中文字符串在 `MessageBoxA` 中无法正确显示。

**修复**：全部改用 `MessageBoxW` + `L"..."` 宽字符串。

```cpp
// 错误：UTF-8 源码 + ANSI API = 乱码
MessageBoxA(NULL, "激活成功！", "授权验证", MB_OK);  // ❌

// 正确：宽字符串 + Wide API
MessageBoxW(NULL, L"激活成功！", L"授权验证", MB_OK);  // ✅
```

#### 问题5：激活成功但下次启动仍需激活（综合问题）

**现象**：点击激活后弹窗提示"激活成功"，但关闭程序重新打开仍弹出激活页面。

**根因**：上述问题2+3的连锁反应：
1. HMAC-SHA256 算法名错误 → PBKDF2 派生密钥失败
2. AES 加密参数传递错误 → 加密失败
3. `activation.dat` 从未成功写入
4. 但验证逻辑（RSA 验签）是独立的，不受影响，所以显示"激活成功"
5. 下次启动时 Load 发现文件不存在，又弹出激活页面

**修复**：逐一修复问题2和问题3后，Save 成功写入 `activation.dat`，Load 成功读取并解密，验证通过。

#### 问题6：激活对话框中 Ctrl+A 无法全选

**现象**：序列号和激活码输入框中按 Ctrl+A 无法全选文本。

**原因**：Win32 原生 EDIT 控件默认不处理 Ctrl+A 消息。

**修复**：使用 `SetWindowSubclass` 子类化编辑框，拦截 `WM_CHAR` 中 `wParam==1`（Ctrl+A 的 ASCII 码），执行 `EM_SETSEL` 全选。

```cpp
LRESULT CALLBACK EditSubclassProc(HWND hWnd, UINT uMsg, WPARAM wParam,
                                   LPARAM lParam, UINT_PTR, DWORD_PTR) {
    if (uMsg == WM_CHAR && wParam == 1) {  // Ctrl+A
        SendMessage(hWnd, EM_SETSEL, 0, -1);
        return 0;
    }
    return DefSubclassProc(hWnd, uMsg, wParam, lParam);
}
```

### 涉及文件

| 文件 | 变更 |
|------|------|
| activation-code-verifier/cpp/CMakeLists.txt | 新增，构建 JonesActivation.lib |
| activation-code-verifier/cpp/include/Jones/*.h | 新增，8个公开头文件 |
| activation-code-verifier/cpp/src/*.cpp | 新增，8个实现文件 |
| QRCodeTool/CMakeLists.txt | 修改，链接 JonesActivation.lib + include/Jones |
| QRCodeTool/src/main.cpp | 修改，调用 ActivationGuard::LaunchWithProtection |

### 与 C# 版的兼容性

| 项目 | 兼容情况 |
|------|---------|
| 唯一序列号格式 | 完全一致（XOR 0x5A + Base64） |
| 激活码格式 | 完全一致（payload_b64.signature_b64） |
| RSA 公钥 | 完全一致 |
| AES 存储密钥派生 | 完全一致（PBKDF2 + 设备ID + 同盐值同IV） |
| activation.dat | C++ 版和 C# 版可互相读取（同设备上） |
| 设备指纹算法 | 完全一致（CPU+主板+硬盘+MAC → SHA256） |

### 经验总结

1. **Windows CNG API 的坑最多**：算法标识符命名、HMAC 标志位、padding 参数位置，每个细节都可能出错。必须对照官方文档，不能想当然。
2. **调试日志至关重要**：加密流程链路长（序列号→设备ID→PBKDF2→AES→文件写入），任何一环失败都会导致最终结果异常。在每一步加日志是快速定位问题的关键。
3. **验证成功 ≠ 存储成功**：RSA 验签和 AES 加密存储是独立的两个环节，验签通过不代表文件写入成功。必须在 Save 返回值处做判断。
4. **Win32 API 编码规则**：涉及中文一律用 `W` 后缀宽字符版本（`MessageBoxW`、`SetWindowTextW` 等），`A` 后缀在 UTF-8 源码下会乱码。
