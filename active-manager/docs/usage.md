# 激活码管理系统 - 使用说明文档

---

## 1. 环境要求

| 环境 | 版本要求 |
| :--- | :--- |
| JDK | 21 或更高版本 |
| Maven | 3.8 或更高版本 |
| MySQL | 8.0 或更高版本 |
| .NET | 6.0+ (仅C#客户端) |

---

## 2. 快速开始

### 2.1 进入项目目录
```bash
cd D:\huliang\java\ideaworkspace\jonesDevtools\active-manager
```

### 2.2 配置数据库
确保 MySQL 服务运行在 `192.168.31.182:3306`，并创建用户：
```sql
CREATE USER 'tools'@'%' IDENTIFIED BY 'toolsmarschat';
GRANT ALL PRIVILEGES ON tools.* TO 'tools'@'%';
FLUSH PRIVILEGES;
```

### 2.3 运行应用
```bash
cd activation-code-server
mvn spring-boot:run
```

应用将在 `http://localhost:8080` 启动。

---

## 3. API 接口使用

### 3.1 生成激活码

**请求**:
```bash
POST http://localhost:8080/api/activation/generate
Content-Type: application/json

{
  "serialNumber": "device-001",
  "deviceId": "可选：设备唯一ID",
  "expireDays": 365
}
```

**参数说明**:
| 参数 | 类型 | 必填 | 说明 |
| :--- | :--- | :--- | :--- |
| serialNumber | String | 是 | 设备唯一序列号 |
| deviceId | String | 否 | 设备ID，为空则不绑定设备 |
| expireDays | Integer | 否 | 有效期天数，默认365天 |

**成功响应**:
```json
{
  "success": true,
  "message": "激活码生成成功",
  "activationCode": "ZGV2aWNlLTAwMXxERTAxMjM0NTY3ODl8MTczNTY4OTYwMDAw.wfv7K...",
  "expireTime": 1735689600000,
  "serialNumber": "device-001",
  "deviceId": "DE123456789"
}
```

**失败响应**:
```json
{
  "success": false,
  "message": "序列号不能为空",
  "activationCode": null,
  "expireTime": null,
  "serialNumber": null,
  "deviceId": null
}
```

### 3.2 验证激活码

**请求**:
```bash
POST http://localhost:8080/api/activation/verify
Content-Type: application/json

{
  "activationCode": "ZGV2aWNlLTAwMXxERTAxMjM0NTY3ODl8MTczNTY4OTYwMDAw.wfv7K...",
  "deviceId": "可选：当前设备ID"
}
```

**参数说明**:
| 参数 | 类型 | 必填 | 说明 |
| :--- | :--- | :--- | :--- |
| activationCode | String | 是 | 需要验证的激活码 |
| deviceId | String | 否 | 当前设备ID，用于设备绑定验证 |

**成功响应**:
```json
{
  "success": true,
  "message": "激活码验证成功",
  "serialNumber": "device-001",
  "deviceId": "DE123456789",
  "expireTime": 1735689600000,
  "expired": false,
  "deviceMismatch": false
}
```

**失败响应**:
```json
{
  "success": false,
  "message": "激活码签名验证失败",
  "serialNumber": null,
  "deviceId": null,
  "expireTime": null,
  "expired": false,
  "deviceMismatch": false
}
```

---

## 4. 客户端离线验证工具 - 多语言嵌入指南

### 4.1 C# 项目嵌入（优先推荐）

C# 版验证工具已编译为类库 `Jones.Activation.dll`，内含公钥XOR加密存储、反调试检测、设备指纹采集、防时间篡改等安全机制。

**步骤1**: 将 `Jones.Activation.dll` 和 `System.Management.dll` 复制到你的项目引用目录

**步骤2**: 在项目中添加对 `Jones.Activation.dll` 的引用

**步骤3**: 在程序入口处调用验证

```csharp
using Jones.Activation;

class Program
{
    static void Main(string[] args)
    {
        // 方式1：最简单 - 验证失败直接退出进程
        ActivationGuard.Protect("你的激活码");

        // 方式2：自动获取设备ID + 验证
        ActivationGuard.ProtectWithAutoDevice("你的激活码");

        // 方式3：手动指定设备ID
        ActivationGuard.Protect("你的激活码", "指定设备ID");

        // 方式4：只检查不退出，自行处理结果
        VerifyResult result = ActivationGuard.Check("你的激活码");
        if (!result.Success)
        {
            Console.WriteLine("验证失败: " + result.Message);
            Environment.Exit(1);
        }

        // 验证通过后，程序正常运行
        Console.WriteLine("软件正常运行...");
    }
}
```

**步骤4**: 获取当前设备ID（用于生成激活码时绑定设备）

```csharp
string deviceId = DeviceInfo.GetDeviceId();
string macAddress = DeviceInfo.GetMacAddress();
Console.WriteLine($"设备ID: {deviceId}");
Console.WriteLine($"MAC地址: {macAddress}");
```

### 4.2 Java 项目嵌入

**步骤1**: 将 `ActivationVerifier.java` 复制到你的项目源码目录

**步骤2**: 将公钥文件 `public_key.pem` 放到项目资源目录

**步骤3**: 在程序入口处调用验证

```java
import com.jones.activation.verifier.ActivationVerifier;
import com.jones.activation.verifier.ActivationVerifier.VerifyResult;
import java.nio.file.Files;
import java.nio.file.Paths;

public class MainApp {
    public static void main(String[] args) {
        // 加载公钥
        String publicKeyPem = new String(Files.readAllBytes(
            Paths.get("public_key.pem")));

        // 创建验证器
        ActivationVerifier verifier = new ActivationVerifier(publicKeyPem);

        // 获取当前设备ID（基于MAC地址）
        String deviceId = ActivationVerifier.getDeviceId();

        // 验证激活码
        VerifyResult result = verifier.verify("你的激活码", deviceId);

        if (!result.isSuccess()) {
            System.err.println("授权验证失败: " + result.getMessage());
            System.exit(1);
        }

        // 验证通过后，程序正常运行
        System.out.println("软件正常运行...");
    }
}
```

### 4.3 Python 项目嵌入

通过命令行调用编译好的验证工具：

**步骤1**: 将 `Jones.Activation.dll` 和运行时放到固定目录

**步骤2**: Python 调用

```python
import subprocess
import json

def verify_activation(activation_code, device_id=None):
    args = ["dotnet", "ActivationVerifier.dll", "verify", activation_code]
    if device_id:
        args.append(device_id)
    result = subprocess.run(args, capture_output=True, text=True)
    return json.loads(result.stdout)

def get_device_id():
    result = subprocess.run(
        ["dotnet", "ActivationVerifier.dll", "deviceid"],
        capture_output=True, text=True
    )
    return json.loads(result.stdout)["deviceId"]
```

### 4.4 C/C++ 项目嵌入

通过 COM 互操作或命令行调用：

```c
#include <stdlib.h>
#include <stdio.h>

int verify_activation(const char* activation_code) {
    char cmd[1024];
    snprintf(cmd, sizeof(cmd),
        "dotnet ActivationVerifier.dll verify \"%s\"", activation_code);
    int ret = system(cmd);
    return ret;  // 0=成功, 1001=验证失败
}
```

### 4.5 其他语言嵌入

对于其他语言（Go、Rust、Node.js等），有两种方案：

**方案A**: 命令行调用
- 编译一个独立的验证工具 exe
- 通过子进程调用，解析 JSON 输出

**方案B**: HTTP 本地服务
- 启动一个本地验证服务
- 其他语言通过 HTTP 请求调用验证接口

### 4.6 验证结果说明

| 字段 | 类型 | 说明 |
| :--- | :--- | :--- |
| Success | boolean | 验证是否成功 |
| Message | string | 结果消息 |
| SerialNumber | string | 激活码绑定的序列号 |
| DeviceId | string | 激活码绑定的设备ID |
| ExpireTimestamp | long | 过期时间戳（毫秒） |
| Expired | boolean | 是否已过期 |
| DeviceMismatch | boolean | 设备是否不匹配 |

---

## 5. 激活码结构

### 5.1 激活码格式
```
激活码 = Base64URL(payload) + "." + Base64URL(signature)
payload = serialNumber + "|" + deviceId + "|" + expireTimestamp
signature = SHA256withRSA(payload, privateKey)
```

### 5.2 字段说明
| 字段 | 说明 |
| :--- | :--- |
| serialNumber | 设备序列号 |
| deviceId | 设备ID（可为空） |
| expireTimestamp | 过期时间戳（毫秒） |
| RSA签名 | 使用私钥对payload进行SHA256withRSA签名 |

### 5.3 兼容性说明
- **新格式**: `serialNumber|deviceId|expireTimestamp` (含设备ID)
- **旧格式**: `serialNumber|expireTimestamp` (不含设备ID)
- 验证工具自动兼容两种格式

---

## 6. 设备绑定功能

### 6.1 生成带设备绑定的激活码
```json
{
  "serialNumber": "device-001",
  "deviceId": "DE123456789",
  "expireDays": 365
}
```

### 6.2 验证时检查设备绑定
```json
{
  "activationCode": "...",
  "deviceId": "DE123456789"
}
```

### 6.3 设备不匹配情况
如果激活码绑定了设备A，但用户在设备B上验证，会返回失败：
```json
{
  "success": false,
  "message": "设备不匹配",
  "deviceMismatch": true
}
```

---

## 7. 密钥管理

### 7.1 生成密钥对
应用启动时会自动检测密钥文件，如果不存在则自动生成：
- 私钥路径: `./rsa_keys/private_key.pem`
- 公钥路径: `./rsa_keys/public_key.pem`

### 7.2 密钥安全
- **私钥**: 必须严格保密，仅存储在服务端，禁止分发
- **公钥**: 可以安全地分发给客户端用于验证

---

## 8. 配置说明

### 8.1 application.yml 主要配置

```yaml
server:
  port: 8080                    # 服务端口

spring:
  datasource:
    url: jdbc:mysql://192.168.31.182:3306/tools  # 数据库连接URL
    username: tools             # 数据库用户名
    password: toolsmarschat     # 数据库密码
    hikari:
      maximum-pool-size: 20     # 最大连接数

activation:
  rsa:
    private-key-path: ./rsa_keys/private_key.pem  # 私钥路径
    public-key-path: ./rsa_keys/public_key.pem    # 公钥路径
    key-size: 2048              # 密钥长度
```

---

## 9. 安全特性

### 9.1 RSA签名验证
- 使用2048位RSA密钥
- SHA256withRSA签名算法
- 无私钥无法伪造激活码

### 9.2 设备绑定
- 激活码可绑定特定设备ID
- 绑定后只能在指定设备使用
- 防止激活码共享

### 9.3 反调试检测（C#）
- 检测调试器附加
- 检测执行时间异常
- 静默失败，不暴露信息

### 9.4 内存清理
- 敏感数据用完即清
- 防止内存dump攻击
- 无论成功失败都会清理

### 9.5 静默失败
- 验证失败不暴露详细原因
- 统一返回"验证失败"
- 避免泄露验证逻辑

---

## 10. 常见问题

### 10.1 数据库连接失败
- 检查 MySQL 服务是否运行
- 检查防火墙是否允许 3306 端口
- 检查用户名和密码是否正确
- 确认数据库 `tools` 已创建

### 10.2 激活码验证失败
- 检查激活码格式是否正确
- 检查公钥文件是否正确
- 检查激活码是否过期
- 检查设备ID是否匹配

### 10.3 服务启动失败
- 检查端口 8080 是否被占用
- 检查密钥文件是否存在
- 检查数据库连接配置
- 确认 JDK 版本为 21 或更高

---

## 11. 日志查看

日志文件位于 `./logs/activation-code-server.log`，包含：
- 请求日志（INFO级别）
- 错误信息（ERROR级别）
- 数据库操作日志（DEBUG级别）

---

## 12. 项目结构说明

```
active-manager/
├── activation-code-server/     # Spring Boot 服务端应用
│   ├── src/main/java/          # Java 源代码
│   ├── src/main/resources/     # 配置文件
│   ├── rsa_keys/               # RSA 密钥文件
│   ├── logs/                   # 日志目录
│   └── pom.xml                 # Maven 配置
├── activation-code-verifier/   # 离线验证工具
│   ├── ActivationVerifier.java # Java 版本验证器
│   └── ActivationVerifier.cs   # C# 版本验证器（含反调试）
└── docs/                      # 文档目录
    ├── requirements.md        # 需求文档
    ├── design.md              # 设计文档
    ├── development.md         # 开发过程文档
    └── usage.md               # 使用说明文档
```

---

## 13. 安全注意事项

1. **私钥保护**: 私钥文件 `private_key.pem` 必须严格保密，不应提交到版本控制系统
2. **密码管理**: 数据库密码应通过环境变量或配置中心管理，不应硬编码
3. **密钥轮换**: 定期更换 RSA 密钥对，旧密钥可保留用于验证旧激活码
4. **传输安全**: 生产环境应配置 HTTPS，确保数据传输安全
5. **设备ID安全**: 设备ID应包含足够随机性，避免被猜测