# 激活码管理系统 - 安全优化方案

---

## 一、当前实现分析

### 1.1 现有安全机制

| 机制 | 当前状态 | 评估 |
| :--- | :--- | :--- |
| RSA签名验证 | ✅ 已实现 | 使用SHA256withRSA签名 |
| 激活码过期校验 | ✅ 已实现 | 通过时间戳判断 |
| 独立验证工具 | ✅ 已实现 | Java和C#版本 |
| 公钥加密验证 | ✅ 已实现 | 仅使用公钥验证 |

### 1.2 安全风险识别

| 风险点 | 风险等级 | 说明 |
| :--- | :--- | :--- |
| 公钥硬编码 | 高 | 公钥明文存储易被提取 |
| 无反调试检测 | 高 | 易被dnSpy/调试器分析 |
| 无内存清理 | 中 | 密钥/激活码可能被内存dump |
| 无设备绑定 | 中 | 激活码可在任意设备使用 |
| 无服务端校验 | 高 | 本地验证可被绕过 |
| 无反篡改检测 | 中 | 程序被篡改后无法检测 |

---

## 二、优化方案

### 2.1 公钥加密存储（字符串加密）

**问题**：公钥明文存储在代码中，容易被提取

**优化方案**：将公钥拆分成多段，使用异或加密，运行时动态解密

#### C# 版本优化

```csharp
using System;
using System.Security.Cryptography;
using System.Text;

namespace Jones.Activation
{
    public class ActivationVerifier
    {
        private readonly RSA _rsa;
        private const byte ENCRYPT_KEY = 0x3F;

        public ActivationVerifier()
        {
            _rsa = RSA.Create();
            string decryptedKey = DecryptPublicKey();
            _rsa.ImportFromPem(decryptedKey);
        }

        private static string DecryptPublicKey()
        {
            byte[] encryptedKey = new byte[] {
                0x2D, 0x2D, 0x2D, 0x2D, 0x2D, 0x42, 0x45, 0x47, 0x49, 0x4E,
                0x20, 0x50, 0x55, 0x42, 0x4C, 0x49, 0x43, 0x20, 0x4B, 0x45,
                0x59, 0x2D, 0x2D, 0x2D, 0x2D, 0x2D, 0x0A, 0x4D, 0x49, 0x47,
                0x46, 0x54, 0x4D, 0x45, 0x76, 0x47, 0x75, 0x65, 0x72, 0x79,
                0x42, 0x68, 0x6B, 0x68, 0x41, 0x57, 0x45, 0x43, 0x41, 0x51,
                0x41, 0x4D, 0x41, 0x41, 0x4F, 0x43, 0x50, 0x51, 0x42, 0x41,
                0x4D, 0x41, 0x41, 0x4F, 0x43, 0x50, 0x51, 0x42, 0x41, 0x4D,
                0x41, 0x41, 0x4F, 0x43, 0x50, 0x51, 0x42, 0x41, 0x4D, 0x41
            };

            for (int i = 0; i < encryptedKey.Length; i++)
            {
                encryptedKey[i] ^= ENCRYPT_KEY;
            }
            
            string key = Encoding.ASCII.GetString(encryptedKey);
            Array.Clear(encryptedKey, 0, encryptedKey.Length);
            
            return key;
        }

        public VerifyResult Verify(string activationCode)
        {
            try
            {
                if (string.IsNullOrWhiteSpace(activationCode))
                {
                    return VerifyResult.Fail("激活码不能为空");
                }

                string[] parts = activationCode.Split('.');
                if (parts.Length != 2)
                {
                    return VerifyResult.Fail("激活码格式无效");
                }

                byte[] payloadBytes = Base64UrlDecode(parts[0]);
                byte[] signatureBytes = Base64UrlDecode(parts[1]);

                string payload = Encoding.UTF8.GetString(payloadBytes);
                string[] payloadParts = payload.Split('|');
                if (payloadParts.Length != 2)
                {
                    return VerifyResult.Fail("激活码载荷格式无效");
                }

                string serialNumber = payloadParts[0];
                long expireTimestamp;
                if (!long.TryParse(payloadParts[1], out expireTimestamp))
                {
                    return VerifyResult.Fail("激活码过期时间格式无效");
                }

                bool verified = _rsa.VerifyData(
                    payloadBytes,
                    signatureBytes,
                    HashAlgorithmName.SHA256,
                    RSASignaturePadding.Pkcs1
                );

                if (!verified)
                {
                    return VerifyResult.Fail("激活码签名验证失败");
                }

                bool expired = expireTimestamp < DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
                if (expired)
                {
                    return VerifyResult.Fail("激活码已过期", serialNumber, expireTimestamp, true);
                }

                Array.Clear(payloadBytes, 0, payloadBytes.Length);
                Array.Clear(signatureBytes, 0, signatureBytes.Length);

                return VerifyResult.Ok(serialNumber, expireTimestamp);
            }
            catch (Exception ex)
            {
                return VerifyResult.Fail("验证激活码异常");
            }
        }

        private static byte[] Base64UrlDecode(string input)
        {
            string padded = input;
            int pad = padded.Length % 4;
            if (pad > 0)
            {
                padded += new string('=', 4 - pad);
            }
            padded = padded.Replace('-', '+').Replace('_', '/');
            return Convert.FromBase64String(padded);
        }
    }

    public class VerifyResult
    {
        public bool Success { get; }
        public string Message { get; }
        public string SerialNumber { get; }
        public long ExpireTimestamp { get; }
        public bool Expired { get; }

        private VerifyResult(bool success, string message, string serialNumber, long expireTimestamp, bool expired)
        {
            Success = success;
            Message = message;
            SerialNumber = serialNumber;
            ExpireTimestamp = expireTimestamp;
            Expired = expired;
        }

        public static VerifyResult Ok(string serialNumber, long expireTimestamp)
        {
            return new VerifyResult(true, "验证成功", serialNumber, expireTimestamp, false);
        }

        public static VerifyResult Fail(string message, string serialNumber = null, long expireTimestamp = 0, bool expired = false)
        {
            return new VerifyResult(false, message, serialNumber, expireTimestamp, expired);
        }
    }
}
```

### 2.2 反调试检测

**问题**：程序易被调试器附加分析

**优化方案**：添加调试器检测，检测到异常时静默处理

#### C# 版本添加反调试检测

```csharp
using System;
using System.Diagnostics;
using System.Runtime.InteropServices;

public static class AntiDebug
{
    [DllImport("kernel32.dll", SetLastError = true)]
    private static extern bool CheckRemoteDebuggerPresent(IntPtr hProcess, ref bool isDebuggerPresent);

    [DllImport("kernel32.dll")]
    private static extern uint GetTickCount();

    public static bool IsDebuggerAttached()
    {
        if (Debugger.IsAttached)
            return true;

        bool isDebuggerPresent = false;
        CheckRemoteDebuggerPresent(Process.GetCurrentProcess().Handle, ref isDebuggerPresent);
        if (isDebuggerPresent)
            return true;

        return false;
    }

    public static bool IsRunningInDebugger()
    {
        uint start = GetTickCount();
        for (int i = 0; i < 100000000; i++) { }
        uint end = GetTickCount();
        
        if (end - start < 10)
            return true;

        return false;
    }

    public static void CheckAndExit()
    {
        if (IsDebuggerAttached() || IsRunningInDebugger())
        {
            Environment.Exit(1);
        }
    }
}
```

### 2.3 设备绑定验证

**问题**：激活码可在任意设备使用

**优化方案**：激活码绑定设备唯一ID

#### 服务端新增设备验证接口

```java
// ActivationController.java
@PostMapping("/verify-with-device")
public VerifyResponse verifyWithDevice(@RequestBody VerifyWithDeviceRequest request) {
    log.info("收到设备绑定验证请求");
    return activationService.verifyWithDevice(request);
}

// VerifyWithDeviceRequest.java
public class VerifyWithDeviceRequest {
    private String activationCode;
    private String deviceId;
    // getters and setters
}

// ActivationService.java
public VerifyResponse verifyWithDevice(VerifyWithDeviceRequest request) {
    VerifyResponse verifyResult = verifyActivationCode(new VerifyRequest(request.getActivationCode()));
    
    if (!verifyResult.isSuccess()) {
        return verifyResult;
    }

    String deviceId = request.getDeviceId();
    if (deviceId == null || deviceId.trim().isEmpty()) {
        return VerifyResponse.builder()
                .success(false)
                .message("设备ID不能为空")
                .build();
    }

    LambdaQueryWrapper<ActivationRecord> queryWrapper = new LambdaQueryWrapper<>();
    queryWrapper.eq(ActivationRecord::getSerialNumber, verifyResult.getSerialNumber());
    ActivationRecord record = activationRecordMapper.selectOne(queryWrapper);

    if (record != null && record.getDeviceId() != null && 
        !record.getDeviceId().equals(deviceId)) {
        log.warn("设备不匹配, 激活码绑定设备: {}, 当前设备: {}", 
                 record.getDeviceId(), deviceId);
        return VerifyResponse.builder()
                .success(false)
                .message("激活码已绑定其他设备")
                .build();
    }

    if (record != null && record.getDeviceId() == null) {
        record.setDeviceId(deviceId);
        record.setUpdateTime(LocalDateTime.now());
        activationRecordMapper.updateById(record);
    }

    return verifyResult;
}
```

### 2.4 服务端Token校验机制

**问题**：本地验证可被绕过

**优化方案**：引入服务端Token机制，定期刷新

#### TokenDTO定义

```java
public class TokenRequest {
    private String activationCode;
    private String deviceId;
    // getters and setters
}

public class TokenResponse {
    private boolean success;
    private String message;
    private String token;
    private long expireTime;
    // getters and setters
}
```

#### Token接口实现

```java
// ActivationController.java
@PostMapping("/token")
public TokenResponse getToken(@RequestBody TokenRequest request) {
    return activationService.generateToken(request);
}

@PostMapping("/validate-token")
public VerifyResponse validateToken(@RequestBody TokenValidateRequest request) {
    return activationService.validateToken(request.getToken());
}

// ActivationService.java
public TokenResponse generateToken(TokenRequest request) {
    VerifyResponse verifyResult = verifyActivationCode(new VerifyRequest(request.getActivationCode()));
    
    if (!verifyResult.isSuccess()) {
        return TokenResponse.builder()
                .success(false)
                .message(verifyResult.getMessage())
                .build();
    }

    String token = UUID.randomUUID().toString().replace("-", "");
    long expireTime = System.currentTimeMillis() + 5 * 60 * 1000;

    tokenCache.put(token, new TokenInfo(verifyResult.getSerialNumber(), expireTime));

    return TokenResponse.builder()
            .success(true)
            .message("Token生成成功")
            .token(token)
            .expireTime(expireTime)
            .build();
}
```

### 2.5 代码混淆与虚拟化建议

#### ConfuserEx 配置文件示例

创建 `ActivationVerifier.crproj`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<project outputDir=".\Confused" xmlns="http://confuser.codeplex.com">
  <rule pattern="true" regex="true">
    <protection id="rename" />
    <protection id="controlflow" />
    <protection id="anti-debug" />
    <protection id="anti-tamper" />
    <protection id="constants" />
    <protection id="reference proxy" />
  </rule>
  <module path="ActivationVerifier.dll" />
</project>
```

#### VMProtect 集成

```csharp
using System;

public static class VMProtectSDK
{
    [DllImport("VMProtectSDK64.dll")]
    public static extern void VMProtectBeginUltra(string marker);

    [DllImport("VMProtectSDK64.dll")]
    public static extern void VMProtectEnd();

    [DllImport("VMProtectSDK64.dll")]
    public static extern bool VMProtectIsValid();
}

public class ProtectedActivationVerifier
{
    public VerifyResult Verify(string activationCode)
    {
        VMProtectSDK.VMProtectBeginUltra("VerifyActivationCode");
        
        try
        {
            ActivationVerifier verifier = new ActivationVerifier();
            return verifier.Verify(activationCode);
        }
        finally
        {
            VMProtectSDK.VMProtectEnd();
        }
    }
}
```

---

## 三、优化前后对比

### 3.1 安全性提升

| 优化项 | 优化前 | 优化后 |
| :--- | :--- | :--- |
| 公钥存储 | 明文硬编码 | 异或加密+运行时解密 |
| 内存安全 | 无清理机制 | 敏感数据用完即清 |
| 反调试 | 无 | 检测调试器附加 |
| 设备绑定 | 无 | 激活码绑定设备ID |
| 服务端校验 | 无 | Token机制+定期刷新 |
| 代码保护 | 无 | ConfuserEx+VMProtect |

### 3.2 破解难度对比

| 攻击者类型 | 优化前难度 | 优化后难度 |
| :--- | :--- | :--- |
| 小白 | 低（直接读取公钥） | 高（无法获取有效密钥） |
| 中级 | 中（调试分析） | 高（反调试+混淆） |
| 专业 | 中（修改内存） | 极高（虚拟机保护） |

---

## 四、实施建议

### 4.1 实施步骤

| 步骤 | 内容 | 优先级 |
| :--- | :--- | :--- |
| 1 | 公钥加密存储 | 高 |
| 2 | 内存清理机制 | 高 |
| 3 | 反调试检测 | 高 |
| 4 | 服务端Token机制 | 高 |
| 5 | 设备绑定验证 | 中 |
| 6 | ConfuserEx混淆 | 中 |
| 7 | VMProtect虚拟化 | 中 |

### 4.2 注意事项

1. **异常处理**：检测到异常时不要弹窗提示，直接静默退出或随机崩溃
2. **日志策略**：生产环境关闭详细日志，避免泄露敏感信息
3. **密钥轮换**：定期更换RSA密钥对，旧密钥可保留用于验证旧激活码
4. **代码签名**：使用强名称签名防止DLL被替换

---

## 五、总结

当前项目的激活码验证机制已经具备基础的安全性（RSA签名），但在防破解方面存在明显不足。通过以下优化可以将破解难度提升到专业级别：

1. **源码层面**：公钥加密、内存清理、反调试检测
2. **编译层面**：ConfuserEx混淆
3. **运行层面**：VMProtect虚拟化
4. **架构层面**：服务端Token校验、设备绑定

建议按照优先级逐步实施，先完成高优先级的安全加固，再考虑代码保护工具的集成。