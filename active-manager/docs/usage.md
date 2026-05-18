# 激活码管理系统 - 使用说明文档

---

## 1. 环境要求

| 环境 | 版本要求 |
| :--- | :--- |
| JDK | 21 或更高版本 |
| Maven | 3.8 或更高版本 |
| MySQL | 8.0 或更高版本 |

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
  "expireDays": 365
}
```

**参数说明**:
| 参数 | 类型 | 必填 | 说明 |
| :--- | :--- | :--- | :--- |
| serialNumber | String | 是 | 设备唯一序列号 |
| expireDays | Integer | 否 | 有效期天数，默认365天 |

**成功响应**:
```json
{
  "success": true,
  "message": "激活码生成成功",
  "activationCode": "ZGV2aWNlLTAwMXwxNzM1Njg5NjAwMDA.wfv7K...",
  "expireTime": 1735689600000,
  "serialNumber": "device-001"
}
```

**失败响应**:
```json
{
  "success": false,
  "message": "序列号不能为空",
  "activationCode": null,
  "expireTime": null,
  "serialNumber": null
}
```

### 3.2 验证激活码

**请求**:
```bash
POST http://localhost:8080/api/activation/verify
Content-Type: application/json

{
  "activationCode": "ZGV2aWNlLTAwMXwxNzM1Njg5NjAwMDA.wfv7K..."
}
```

**参数说明**:
| 参数 | 类型 | 必填 | 说明 |
| :--- | :--- | :--- | :--- |
| activationCode | String | 是 | 需要验证的激活码 |

**成功响应**:
```json
{
  "success": true,
  "message": "激活码验证成功",
  "serialNumber": "device-001",
  "expireTime": 1735689600000,
  "expired": false
}
```

**失败响应**:
```json
{
  "success": false,
  "message": "激活码签名验证失败",
  "serialNumber": null,
  "expireTime": null,
  "expired": false
}
```

---

## 4. 离线验证工具使用

### 4.1 Java 验证工具

**使用方法**:
```java
import java.nio.file.Files;
import java.nio.file.Paths;

// 加载公钥
String publicKeyPem = Files.readString(Paths.get("rsa_keys/public_key.pem"));

// 创建验证器
ActivationVerifier verifier = new ActivationVerifier(publicKeyPem);

// 验证激活码
ActivationVerifier.VerifyResult result = verifier.verify(activationCode);

if (result.isSuccess()) {
    System.out.println("验证成功，序列号: " + result.getSerialNumber());
    System.out.println("过期时间: " + result.getExpireTimestamp());
} else {
    System.out.println("验证失败: " + result.getMessage());
}
```

### 4.2 C# 验证工具

**使用方法**:
```csharp
using System;
using System.IO;

// 加载公钥
string publicKeyPem = File.ReadAllText("rsa_keys/public_key.pem");

// 创建验证器
ActivationVerifier verifier = new ActivationVerifier(publicKeyPem);

// 验证激活码
VerifyResult result = verifier.Verify(activationCode);

if (result.Success) {
    Console.WriteLine($"验证成功，序列号: {result.SerialNumber}");
    Console.WriteLine($"过期时间: {result.ExpireTimestamp}");
} else {
    Console.WriteLine($"验证失败: {result.Message}");
}
```

---

## 5. 密钥管理

### 5.1 生成密钥对
应用启动时会自动检测密钥文件，如果不存在则自动生成：
- 私钥路径: `./rsa_keys/private_key.pem`
- 公钥路径: `./rsa_keys/public_key.pem`

### 5.2 密钥安全
- **私钥**: 必须严格保密，仅存储在服务端，禁止分发
- **公钥**: 可以安全地分发给客户端用于验证

---

## 6. 配置说明

### 6.1 application.yml 主要配置

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

## 7. 常见问题

### 7.1 数据库连接失败
- 检查 MySQL 服务是否运行
- 检查防火墙是否允许 3306 端口
- 检查用户名和密码是否正确
- 确认数据库 `tools` 已创建

### 7.2 激活码验证失败
- 检查激活码格式是否正确（应为两段 Base64URL 编码用 "." 连接）
- 检查公钥文件是否正确
- 检查激活码是否过期

### 7.3 服务启动失败
- 检查端口 8080 是否被占用
- 检查密钥文件是否存在
- 检查数据库连接配置
- 确认 JDK 版本为 21 或更高

---

## 8. 日志查看

日志文件位于 `./logs/activation-code-server.log`，包含：
- 请求日志（INFO级别）
- 错误信息（ERROR级别）
- 数据库操作日志（DEBUG级别）

---

## 9. 项目结构说明

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
│   └── ActivationVerifier.cs   # C# 版本验证器
└── docs/                       # 文档目录
    ├── requirements.md         # 需求文档
    ├── design.md               # 设计文档
    ├── development.md          # 开发过程文档
    └── usage.md                # 使用说明文档
```

---

## 10. 安全注意事项

1. **私钥保护**: 私钥文件 `private_key.pem` 必须严格保密，不应提交到版本控制系统
2. **密码管理**: 数据库密码应通过环境变量或配置中心管理，不应硬编码
3. **密钥轮换**: 定期更换 RSA 密钥对，旧密钥可保留用于验证旧激活码
4. **传输安全**: 生产环境应配置 HTTPS，确保数据传输安全