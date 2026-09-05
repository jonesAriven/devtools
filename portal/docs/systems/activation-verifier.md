# 激活码使用页面（客户端验证库）

> 激活码服务（activation-code）的「验票终端」：一套可嵌入各类客户端软件的激活码验证库，离线用 RSA 公钥验签，校验激活码有效性、过期时间与设备绑定，决定软件是否放行运行。Portal 中以「激活码使用页面」登记，本质是无独立门户的客户端 SDK。

## 基本信息

| 项 | 值 |
|----|----|
| 分类 | Web 系统 / 工具库（自研，客户端嵌入） |
| 版本 | 多语言实现同源：C#/.NET 主实现（`ActivationCodeVerifier.csproj`）+ C++ 原生层（`cpp/`，CMake 构建）+ Java 移植（`ActivationVerifier.java`） |
| 部署位置 | 非独立部署——编译进客户端软件（如二维码工具 QRCodeTool）随包分发；服务端 `activecode` 容器另托管其下载页 `/activecode/downloads.html` |
| 源码位置 | 本地 `D:\huliang\java\ideaworkspace\devtools\active-manager\activation-code-verifier\`（与 `activation-code-server` 同仓 `active-manager`） |
| CI/CD | 随 `active-manager` 仓库构建；客户端二进制经激活码服务 `/download` 接口分发（无独立 Web 流水线） |

## 访问入口

- 公网：无独立公网 Web 页面（验证库嵌入客户端软件运行；其下载说明页挂在 `https://tools.marschat.online/activecode/downloads.html`）。
- 内网：`http://192.168.31.182:18080/activecode/downloads.html`（同上下载说明页）。
- 使用形态：客户端软件启动时调 `ActivationGuard.LaunchWithProtection(initialSerial, appVersion)` → 未激活/过期则弹窗显示唯一序列号 + 二维码 → 用户扫码到 `https://tools.marschat.online/activecode/index.html?sn=<序列号>` 自助生成激活码（或把序列号发给管理员生成）→ 粘贴激活码完成激活。

## 全链路（验证过程）

```
客户端软件启动
  → LaunchWithProtection(initialSerial, appVersion)（ActivationGuard 统一入口）
       1. 加载 activation.dat（SecureStorage）
       2. AES-256-CBC 解密 → 读取激活码
       3. RSA 验签 + 设备绑定检查（ActivationVerifier）
       4. TimeGuard 可信时间校验（防回拨）
  → 首次使用/已过期：
       弹窗显示唯一序列号（明文 = 初始序列号|设备ID|机器码|appVersion，
       逐字节 XOR 0x5A → Base64）+ 二维码
  → 用户回传激活码后：
       Protect / Check(activationCode, deviceId) 完成验证
       → 验证通过 AES 加密保存 activation.dat
       → StartPeriodicCheck 每 60 秒定时复查（默认间隔）
  → 软件放行运行
```

> 与服务端关系：服务端 `ActivationController.generate` 用 RSA **私钥**签名生成激活码；本库用配套 RSA **公钥**离线验签。公钥随客户端分发，无需联网即可验证（联网场景可再调服务端 `/activecode/api/activation/verify` 做在线二次校验/流水记录）。

## 系统设计

### 组件构成（C# 实现）

| 组件 | 关键方法 | 职责 |
|------|---------|------|
| `ActivationGuard` | `LaunchWithProtection(initialSerial, appVersion, checkIntervalMs=60000)`、`Protect(activationCode[, deviceId])`、`ProtectWithAutoDevice`、`Check(...)`、`StartPeriodicCheck(..., onExpired)`、`StopPeriodicCheck` | 对外统一入口：启动保护、绑定激活码、验证、60 秒定时复查（到期回调 onExpired） |
| `ActivationVerifier` | verify 核心 | 拆 payload/signature → Base64URL 解码 → SHA256withRSA 公钥验签 → 设备/过期比对 |
| `DeviceInfo` | `GetDeviceId()`、`GetMacAddress()`、`GetMacAddressFormatted()`、`GetMachineCode()`、`GetSerialNumber(initialSerial[, version])`、`ParseSerialNumber()` | 设备指纹与序列号：MAC 派生 deviceId、4 段序列号生成/解析 |
| `SecureStorage` | `Save(filePath, code)` / `Load(filePath)` / `Delete(filePath)` | activation.dat 读写删：AES-256-CBC + PBKDF2（设备 ID 派生密钥，10000 次 SHA256），安全删除先覆写全零 |
| `AntiDebug` | `IsBeingDebugged()` | 反调试检测（见下表） |
| `TimeGuard` | `GetTrustedTimestamp(serial, expireTimestamp)`、`RecordActivation(...)` | 时间防篡改：系统时间 + 单调时钟双轨（`ActivationCache` 记录 LastSystemTime/LastMonotonicMs/ExpireTimestamp），检测回拨 |
| `VerifyResult` | — | 结构化结果：success / message / serialNumber / deviceId / expireTimestamp / expired / deviceMismatch |
| `cpp/` | CMakeLists.txt + src/include（`build_lib.bat` 构建） | C++ 原生层，跨语言复用同一套验签逻辑 |
| `ActivationVerifier.java` | — | Java 移植（同语义），供 Java 客户端嵌入 |

### 反调试检测方式（AntiDebug）

| 检测方式 | 实现原理 | 检测目标 |
|---------|---------|---------|
| `Debugger.IsAttached` | .NET 托管调试器检测 | dnSpy、Visual Studio 调试 |
| `CheckRemoteDebuggerPresent` | Win32 API 远程调试器检测 | x64dbg、OllyDbg |
| 第三种检测（静默退出） | 检测到异常静默退出，不返回详细原因 | 综合防护（docs/v2/design.md 第 3.2 节） |

### 序列号与激活码格式对照

两代数据格式（docs/v2/design.md，两端必须一致）：

```
【序列号】（verifier 生成 → 服务端解密）
  明文  = 初始序列号 | 设备ID | 机器码 | 应用版本        （4 段，| 分隔）
  例    = QRCodeTool | A1B2C3D4E5F6... | AA-BB-CC-DD-EE-FF | V202607152347
  编码  = 逐字节 XOR 0x5A → Base64
  解码  = Base64 解码 → 逐字节 XOR 0x5A → 按 "|" 分割

【激活码】（服务端 RSA 私钥签名 → verifier 公钥验签）
  payload   = 序列号 | 设备ID | 过期时间戳(毫秒)
  signature = SHA256withRSA(payload, privateKey)
  激活码    = Base64URL(payload) + "." + Base64URL(signature)
```

校验通过条件：签名有效 **且** 未过期 **且**（未绑设备 或 设备一致）。失败原因细分：格式无效、签名验证失败（伪造）、过期（expired）、设备不匹配（deviceMismatch）。

### 关键设计决策

1. **四层安全纵深**（docs/v2/design.md）：

```
协议层  RSA 签名验证        不可伪造、不可篡改
数据层  加密存储             公钥 XOR 混淆 / 激活码 AES / 序列号 XOR，用完即清零
运行层  反调试 + 防篡改       3 种反调试、单调时钟、60 秒定时过期检查
信息层  静默失败             验证失败不返回详细原因，检测异常静默退出
```

2. **RSA 公钥保护**：公钥 PEM 逐字节 XOR `[0x3A, 0x7C, 0xE5, 0x91]` 4 字节循环后硬编码为 byte[]；运行时解密一次即 `ImportFromPem` 并清零密文数组——攻击者需同时猜对 4 字节密钥，提高静态逆向成本。
3. **激活状态设备绑定存储**：activation.dat 文件结构 = salt(16 字节) + AES-256-CBC 密文（PKCS7 填充，固定 16 字节 IV），密钥由 PBKDF2(设备ID, salt, 10000 次, SHA256) 派生——拷贝到其他设备无法解密；到期重新验证，防止无限期离线。
4. **版本号透明容器**：verifier 库自身不持有版本定义（已移除内部 version.h 依赖），appVersion 由宿主程序经 `LaunchWithProtection` 传入并嵌入序列号第 4 段，宿主改版本只动自己的一处配置。
5. **多语言同语义**：同一套验签逻辑以 C#（主实现）、C++（原生层，CMake 构建）、Java（移植）三份代码承载，宿主按语言引用；任何一端修改协议（序列号/激活码格式）必须三端同步。

### 三语言实现对照

| 维度 | C#（主实现） | C++（原生层） | Java（移植） |
|------|-------------|--------------|-------------|
| 工程 | `ActivationCodeVerifier.csproj`（obfuscar.xml 混淆 + key.snk 强命名） | `cpp/CMakeLists.txt`（`build_lib.bat` 构建） | `ActivationVerifier.java` 单文件 |
| 典型宿主 | QRCodeTool（C# WinForm） | QR 项目（CMake 工程，经 version.h 传版本） | Java 客户端（待确认具体宿主） |
| 反调试/Win32 | 完整（Debugger.IsAttached + CheckRemoteDebuggerPresent 等） | 原生层实现 | 无 Win32 依赖，防护降级（待确认范围） |
| 本地存储 | activation.dat（SecureStorage） | 同协议 | 同语义实现 |

### 客户端集成自检清单

1. 宿主启动第一入口调用 `LaunchWithProtection(initialSerial, APP_VERSION)`（版本号来自宿主 version.h / 等价配置，单一来源）。
2. 序列号弹窗：确认显示完整 4 段密文序列号 + 二维码（URL 指向 `tools.marschat.online/activecode/index.html?sn=<序列号>`）。
3. 激活码粘贴框 → 调用 `Protect`/`Check` → 处理 `VerifyResult` 四种失败分支（格式/签名/过期/设备）。
4. `onExpired` 回调：到期后按产品策略重新弹激活窗（库已每 60 秒复查）。
5. 升级 RSA 公钥：所有在途客户端同步发版，避免新旧客户端混跑验签失败。

### 对外接口概览

库级 API（非 HTTP）：

- 生命周期：`LaunchWithProtection(initialSerial, appVersion, checkIntervalMs=60000)`——启动即保护，未激活/过期自动弹序列号 + 二维码
- 激活：`Protect(activationCode[, deviceId])`、`ProtectWithAutoDevice`、`Check(...)`、`CheckWithAutoDevice`（返回 `VerifyResult`）
- 周期检查：`StartPeriodicCheck(..., onExpired)` / `StopPeriodicCheck`（默认 60 秒间隔，过期触发回调）
- 设备信息：`GetDeviceId` / `GetMacAddress` / `GetMachineCode` / `GetSerialNumber` / `ParseSerialNumber`
- 版本号维护链（宿主侧）：CMakeLists.txt（VERSION_MODE）→ version.h（`#define APP_VERSION`）→ `LaunchWithProtection(initialSerial, APP_VERSION)` → verifier `s_appVersion` 静态字段 → 序列号第 4 段

## 部署与发布

- **无独立部署**：本库编译进客户端软件（如 QRCodeTool 的 CMake/构建链引用 cpp/ 源码，.NET 项目引用 csproj），随客户端发版。
- 构建入口：C# 用 `ActivationCodeVerifier.csproj`（含 obfuscar.xml 混淆配置、key.snk 强命名）；C++ 原生层用 `cpp/build_lib.bat` / CMakeLists.txt；Java 端直接引用 `ActivationVerifier.java`。
- 客户端发版流程：构建出 exe → 放入内网 Debian `/mnt/shared/www/download/QRCodeTools` → 激活码服务 `DownloadController` 自动按修改时间倒序列出，`https://tools.marschat.online/activecode/downloads.html` 下载页即生效。
- 版本对齐：序列号第 4 段 appVersion 更新后，服务端 `version-check` 策略决定旧版本能否继续领码（策略在 activation-code 后台配置）。
- 客户端集成要点（能力描述，非逐步操作）：
  - 宿主在「激活」入口收集本地 `deviceId`（MAC 派生），连同用户输入激活码调用 `Check`/`Protect`。
  - 激活成功后库内已做本地缓存（activation.dat，带过期时间），无需宿主自行持久化。
  - 与服务端对齐：序列号 = 初始序列号|设备ID|机器码|appVersion（XOR 0x5A + Base64）；激活码 = 序列号|设备ID|过期时间戳 的 RSA 签名串。

## 核心功能与使用

### 功能清单

- **离线激活验证**：纯本地 RSA 验签，不依赖服务端在线（断网也能激活）。
- **设备绑定**：`getDeviceId()` 由 MAC 地址派生；激活码 payload 中设备 ID 与本地不一致即拒绝。
- **过期管理**：payload 内过期时间戳比对 + 60 秒周期复查 + 单调时钟防回拨，到期自动触发回调。
- **防破解**：反调试检测、公钥/激活码内存用后清零、静默失败策略，C++ 原生层复用。
- **多语言嵌入**：C#（主）、C++（原生）、Java（移植）三套实现同语义，客户端按语言引用。

### 典型操作路径

- **首次激活**：客户端启动 → 弹窗显示序列号 + 二维码 → 扫码打开 `index.html?sn=xxx`（自动填充）→ 生成激活码 → 粘贴回客户端 → 验证通过写入 activation.dat。
- **续期/换机**：激活码过期 → 客户端再次弹序列号 → 重新生成新激活码；换设备则设备 ID 变化，必须重新领码。
- **到期自动拦截**：运行中每 60 秒周期复查（StartPeriodicCheck），到期触发 `onExpired` 回调 → 宿主弹窗要求重新激活。
- **激活失败排查**：先用服务端 `/activecode/api/activation/parse-code` 解析激活码 → 确认是过期还是设备不匹配 → 用 `GetDeviceInfo()` 核对本地 deviceId。
- **管理员代发（无扫码条件时）**：用户把弹窗序列号复制发给管理员 → 管理员在 `main.html` 后台生成激活码 → 回传用户粘贴激活。

## 依赖与关联

- 无独立运行依赖（纯客户端库）；验证依赖随客户端分发的 RSA **公钥**（须与服务端私钥配对，公钥更新需重新发版客户端）。
- 关联系统：
  - **激活码服务（activation-code）**：发证端，生成/管理激活码、记录激活流水、配置默认有效期与版本校验策略。
  - **客户端软件（QRCodeTool 等）**：本库的实际载体，经激活码服务 `/download` 分发。

## 运维要点

- 版本管理：验证库随客户端发版；若更换 RSA 密钥对，须同步更新服务端私钥与所有在途客户端的公钥（否则旧客户端验签失败）。
- 分发：客户端 exe 放在内网 Debian `/mnt/shared/www/download/QRCodeTools`，由激活码服务 `DownloadController` 暴露 `/activecode/api/download/list` 与文件下载。
- 排查：客户端激活失败时，先用服务端 `/activecode/api/activation/parse-code` 解析激活码，确认是否过期或设备不匹配；再用 `GetDeviceInfo()` 核对本地 deviceId。
- 排查步骤序列：
  1. `/parse-code` 解析激活码 → 判断过期 / 设备不匹配 / 签名无效；
  2. 签名无效 → 核对客户端内置公钥与服务端私钥是否同一密钥对（发版配对）；
  3. 设备不匹配 → 用户重装系统/换网卡会改变 MAC 派生 deviceId，需重新领码；
  4. 时间异常 → TimeGuard 检测到系统回拨，核对设备系统时间。
- 已知限制：
  - 设备绑定基于 MAC 派生，虚拟网卡/换网卡会导致 deviceId 变化（需重新领码）。
  - 离线激活模式下，服务端流水仅在线二次校验时补记（`/activation/verify`）。
  - Java 移植版为同语义实现，反调试/Win32 相关能力仅在 C#/C++ 宿主中生效（**待确认** Java 端防护降级范围）。
- 安全提示：公钥虽公开分发，但验签机制保证无法伪造有效激活码；切勿把服务端私钥提交进仓库或写入本文档（账密/密钥见 Vaultwarden 或 infrastructure-map 技能）。

## 变更记录

- 2026-09-05 v2 补全设计/部署/使用三维度（新增系统设计：9 组件职责表/四层安全纵深/公钥 XOR 保护/activation.dat 设备绑定存储；新增部署与发布：随宿主发版 + 分发链路；使用节补扫码自助与排查路径；序列号修正为 4 段格式含 appVersion）
- 2026-09-05 v1 首次生成（portal 文档补全任务，AI 基于 `activation-code-verifier` 源码 `ActivationVerifier.java`/`ActivationCodeVerifier.csproj` + 服务端 `ActivationController` 对照 + `秘钥激活码` 设计说明生成）
