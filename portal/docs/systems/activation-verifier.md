# 激活码使用页面（客户端验证库）

> 激活码服务（activation-code）的「验票终端」：一套可嵌入各类客户端软件的激活码验证库，离线用 RSA 公钥验签，校验激活码有效性、过期时间与设备绑定，决定软件是否放行运行。Portal 中以「激活码使用页面」登记，本质是无独立门户的客户端 SDK。

## 基本信息

| 项 | 值 |
|----|----|
| 分类 | Web 系统 / 工具库（自研，客户端嵌入） |
| 版本 | 多语言实现同源：C#/.NET 主实现（`ActivationCodeVerifier.csproj`）+ C++ 原生反调试层（`cpp/`）+ Java 移植（`ActivationVerifier.java`） |
| 部署位置 | 非独立部署——编译进客户端软件（如二维码工具 QRCodeTools）随包分发；服务端 `activecode` 容器另托管其下载页 `/activecode/downloads.html` |
| 源码位置 | 本地 `D:\huliang\java\ideaworkspace\devtools\active-manager\activation-code-verifier\`（与 `activation-code-server` 同仓 `active-manager`） |
| CI/CD | 随 `active-manager` 仓库构建；客户端二进制经激活码服务 `/download` 接口分发（无独立 Web 流水线） |

## 访问入口

- 公网：无独立公网 Web 页面（验证库嵌入客户端软件运行；其下载说明页挂在 `https://tools.marschat.online/activecode/downloads.html`）。
- 内网：`http://192.168.31.182:18080/activecode/downloads.html`（同上下载说明页）。
- 使用形态：终端用户在客户端软件的「激活」对话框中粘贴激活码 → 软件内调用本库完成验证。是否另有纯网页版激活页 **(待确认)**，当前源码未见独立 verify 页面（服务端 `PageController` 仅提供 login/main/index/downloads 四类静态页）。

## 全链路（验证过程）

```
客户端软件「激活」对话框
  → 用户输入激活码（"序列号|设备ID|过期时间戳" 经 Base64URL 编码 + RSA 签名，格式 payload.signature）
  → 调用 ActivationVerifier.verify(code, expectedDeviceId)
       1. 拆分 payload / signature，Base64URL 解码
       2. SHA256withRSA 用内置公钥验签（防伪造）
       3. 校验设备绑定：payload 中 deviceId 与本地 MAC 派生 deviceId 是否一致
       4. 校验过期：payload 中过期时间戳 vs 当前时间
  → 返回 VerifyResult{success, message, serialNumber, deviceId, expireTimestamp, expired, deviceMismatch}
  → 成功则软件放行，失败则提示激活失败
```

> 与服务端关系：服务端 `ActivationController.generate` 用 RSA **私钥**签名生成激活码；本库用配套 RSA **公钥**离线验签。公钥随客户端分发，无需联网即可验证（联网场景可再调服务端 `/activecode/api/activation/verify` 做在线二次校验/流水记录）。

## 核心功能与使用

### 验证能力（以 Java 实现 `ActivationVerifier` 为准，C#/C++ 同语义）
- `verify(String code)` / `verify(code, expectedDeviceId)`：核心验证，返回结构化结果。
  - 失败原因细分：格式无效、签名验证失败（伪造）、过期、设备不匹配（deviceMismatch）。
  - 验证通过条件：签名有效 **且** 未过期 **且**（未绑设备 或 设备一致）。
- **离线优先**：纯本地 RSA 验签，不依赖服务端在线（断网也能激活）。
- **设备绑定**：`getDeviceId()` 由 MAC 地址经 SHA-256 派生 16 位十六进制串；`getDeviceInfo()` 返回 deviceId/MAC/OS 信息，用于生成序列号与比对。
- **防破解**：C++ 原生层（`AntiDebug.cs` / `ActivationGuard.cs` / cpp 构建产物）做反调试保护；验证后内存中清零 payload/signature 字节数组，降低内存抓取风险。
- **多语言嵌入**：设计目标即嵌入各语言软件，优先 C#（.NET `ActivationCodeVerifier.csproj`），并提供 Java 移植与 C++ 原生层；客户端按语言引用对应实现。

### 客户端集成要点（能力描述，非逐步操作）
- 客户端在「激活」入口收集本地 `deviceId`（MAC 派生），连同用户输入激活码调用 `verify`。
- 激活成功后可本地缓存结果（建议带过期时间，到期重新验），避免每次启动都弹窗。
- 与服务端对齐：序列号 = 软件初始序列号 + 机器码（MAC）加密串；激活码 = 序列号 + 过期时间戳 的签名串。

## 依赖与关联

- 无独立运行依赖（纯客户端库）；验证依赖随客户端分发的 RSA **公钥**（须与服务端私钥配对，公钥更新需重新发版客户端）。
- 关联系统：
  - **激活码服务（activation-code）**：发证端，生成/管理激活码、记录激活流水、配置默认有效期与版本校验策略。
  - **客户端软件（QRCodeTools 等）**：本库的实际载体，经激活码服务 `/download` 分发。

## 运维要点

- 版本管理：验证库随客户端发版；若更换 RSA 密钥对，须同步更新服务端私钥与所有在途客户端的公钥（否则旧客户端验签失败）。
- 分发：客户端 exe 放在内网 Debian `/mnt/shared/www/download/QRCodeTools`，由激活码服务 `DownloadController` 暴露 `/activecode/api/download/list` 与文件下载。
- 排查：客户端激活失败时，先用服务端 `/activecode/api/activation/parse-code` 解析激活码，确认是否过期或设备不匹配；再用 `getDeviceInfo()` 核对本地 deviceId。
- 安全提示：公钥虽公开分发，但验签机制保证无法伪造有效激活码；切勿把服务端私钥提交进仓库或写入本文档（账密/密钥见 Vaultwarden 或 infrastructure-map 技能）。

## 变更记录

- 2026-09-05 首次生成（portal 文档补全任务，AI 基于 `activation-code-verifier` 源码 `ActivationVerifier.java`/`ActivationCodeVerifier.csproj` + 服务端 `ActivationController` 对照 + `秘钥激活码` 设计说明生成）
