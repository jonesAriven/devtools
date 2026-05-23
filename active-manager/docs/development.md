# 激活码管理系统 - 开发过程文档

---

## 1. 项目初始化阶段

### 1.1 环境准备
- **JDK版本**: JDK 21 LTS 版本
- **Maven配置**: 配置阿里云Maven镜像加速依赖下载
- **IDE**: IntelliJ IDEA

### 1.2 Spring Boot项目创建
使用 Spring Initializr 创建项目，选择以下依赖：
- Spring Web
- MyBatis Plus Starter
- MySQL Driver
- HikariCP

### 1.3 目录结构规划
```
activation-code-server/
├── src/main/java/com/jones/activation/
│   ├── controller/
│   ├── service/
│   ├── mapper/
│   ├── entity/
│   ├── dto/
│   ├── util/
│   └── config/
├── src/main/resources/
├── rsa_keys/
└── logs/
```

---

## 2. 核心功能开发

### 2.1 数据库设计与实现

**步骤1**: 设计数据库表结构
- 确定需要存储的字段：序列号、设备ID、激活码、过期时间、创建时间、更新时间
- 添加唯一索引确保序列号唯一性
- 添加设备ID字段支持设备绑定

**步骤2**: 创建实体类 `ActivationRecord`
- 使用 MyBatis Plus 注解配置表映射
- 添加 deviceId 字段支持设备绑定
- 生成 getter/setter 方法

**步骤3**: 创建 Mapper 接口
- 继承 BaseMapper 获取基础 CRUD 功能
- 无需额外编写 SQL 语句

**步骤4**: 编写数据库初始化脚本 `schema.sql`
- 创建数据库和表结构
- 设置合适的字符集和存储引擎
- 添加 device_id 字段

### 2.2 加密工具类开发

**步骤1**: 设计激活码结构
```
激活码 = Base64URL(serialNumber|deviceId|expireTimestamp) + "." + Base64URL(RSA签名)
```

**步骤2**: 实现 CryptoUtil 工具类
- `generateKeyPair()`: 生成 RSA 密钥对
- `parsePrivateKey()`: 解析 PEM 格式私钥
- `parsePublicKey()`: 解析 PEM 格式公钥
- `generateActivationCode()`: 生成激活码（支持设备ID）
- `parseAndVerify()`: 解析并验证激活码（支持设备验证）

**步骤3**: 配置密钥加载
- 创建 `RsaKeyConfig` 配置类
- 在启动时自动加载密钥文件

**步骤4**: 添加内存清理机制
- 使用 try-finally 确保敏感数据清理
- 验证完成后清零 payloadBytes 和 signatureBytes

### 2.3 业务逻辑层开发

**步骤1**: 实现 `ActivationService`
- `generateActivationCode()`: 生成激活码主逻辑
  - 参数校验
  - 查询是否已存在未过期激活码
  - 支持设备ID绑定
  - 生成新激活码
  - 保存到数据库
- `verifyActivationCode()`: 验证激活码主逻辑
  - 解析激活码
  - 验证签名
  - 设备匹配检查（可选）
  - 检查有效期

### 2.4 控制器层开发

**步骤1**: 实现 `ActivationController`
- `@RestController` 注解标识
- `@RequestMapping("/api/activation")` 设置基础路径
- `@PostMapping("/generate")` 生成激活码接口
- `@PostMapping("/verify")` 验证激活码接口

### 2.5 DTO 类定义

**步骤1**: 创建请求 DTO
- `GenerateRequest`: serialNumber, deviceId, expireDays
- `VerifyRequest`: activationCode, deviceId

**步骤2**: 创建响应 DTO
- `GenerateResponse`: success, message, activationCode, expireTime, serialNumber, deviceId
- `VerifyResponse`: success, message, serialNumber, deviceId, expireTime, expired, deviceMismatch

---

## 3. 客户端验证工具开发

### 3.1 Java 验证工具

**步骤1**: 创建 `ActivationVerifier.java`
- 仅使用公钥进行验证
- 不依赖服务端，可独立运行
- 支持设备绑定验证
- 添加内存清理机制

**步骤2**: 验证结果类
```java
public static class VerifyResult {
    private final boolean success;
    private final String message;
    private final String serialNumber;
    private final String deviceId;
    private final long expireTimestamp;
    private final boolean expired;
    private final boolean deviceMismatch;
}
```

### 3.2 C# 验证工具

**步骤1**: 创建 `ActivationVerifier.cs`
- 使用 .NET RSA 类进行签名验证
- 实现 Base64URL 解码
- 与 Java 版本完全兼容

**步骤2**: 实现反调试检测 `AntiDebug` 类
```csharp
public static bool IsBeingDebugged()
{
    if (Debugger.IsAttached) return true;
    
    bool isDebuggerPresent = false;
    CheckRemoteDebuggerPresent(Process.GetCurrentProcess().Handle, ref isDebuggerPresent);
    if (isDebuggerPresent) return true;

    uint start = GetTickCount();
    for (int i = 0; i < 100000000; i++) { }
    uint end = GetTickCount();
    
    if (end - start < 10) return true;

    return false;
}
```

**步骤3**: 添加内存清理
- 使用 `Array.Clear()` 清理敏感字节数组

---

## 4. 数据库配置与测试

### 4.1 配置远程数据库
修改 `application.yml`:
```yaml
spring:
  datasource:
    url: jdbc:mysql://192.168.31.182:3306/tools
    username: tools
    password: toolsmarschat
    driver-class-name: com.mysql.cj.jdbc.Driver
```

### 4.2 数据库连接测试
- JDK 21 连接测试成功
- 使用 Python pymysql 辅助测试数据库连接
- Spring Boot + HikariCP 连接池成功连接

### 4.3 表初始化
- 通过 Spring Boot `spring.sql.init` 自动执行 `schema.sql`
- 添加 device_id 字段到表结构

---

## 5. 安全增强功能开发

### 5.1 设备绑定机制

**服务端实现**:
- `ActivationService.generateActivationCode()` 支持 deviceId 参数
- `CryptoUtil.generateActivationCode()` 生成包含设备ID的激活码

**客户端实现**:
- `ActivationVerifier.Verify()` 支持 expectedDeviceId 参数
- 激活码格式升级为: `serialNumber|deviceId|expireTimestamp`

### 5.2 反调试检测

**实现位置**: `AntiDebug` 类

**检测方法**:
1. 检测 `Debugger.IsAttached`
2. 调用 `CheckRemoteDebuggerPresent` API
3. 执行时间检测

**处理策略**:
- 检测到调试器时静默返回验证失败
- 不暴露任何调试相关信息

### 5.3 内存清理

**实现位置**: 所有验证方法的 finally 块

**清理内容**:
- payloadBytes 数组
- signatureBytes 数组

```java
finally {
    if (payloadBytes != null) {
        Arrays.fill(payloadBytes, (byte) 0);
    }
    if (signatureBytes != null) {
        Arrays.fill(signatureBytes, (byte) 0);
    }
}
```

---

## 6. 向后兼容性处理

### 6.1 旧格式激活码兼容

**兼容策略**:
- 解析时检测 payload 分段数量
- 2段格式: `serialNumber|expireTimestamp` (旧格式)
- 3段格式: `serialNumber|deviceId|expireTimestamp` (新格式)

### 6.2 设备ID可选

**兼容策略**:
- deviceId 为空时，激活码不绑定设备
- 验证时不进行设备匹配检查

---

## 7. 问题与解决方案

### 7.1 JDK 版本兼容性问题
**问题**: JDK 25 连接 MySQL 失败
**解决方案**: 切换到 JDK 21 LTS 版本

### 7.2 数据库连接问题
**问题**: 无法直接连接远程 MySQL
**解决方案**: 
- 使用 Python pymysql 测试连接成功
- Spring Boot + HikariCP 连接池成功连接

### 7.3 文件夹重命名问题
**问题**: 原文件夹名称包含中文，无法直接重命名
**解决方案**: 先复制到新文件夹，停止占用进程后删除原文件夹

### 7.4 设备绑定需求
**问题**: 激活码可在任意设备使用
**解决方案**: 在激活码中包含设备ID，验证时进行设备匹配

### 7.5 破解风险
**问题**: 验证工具易被调试分析
**解决方案**: 添加反调试检测和静默失败机制

---

## 8. 代码优化与规范

### 8.1 代码风格
- 使用 Builder 模式构建响应对象
- 使用 SLF4J 进行日志记录
- 敏感数据及时清理

### 8.2 异常处理
- 静默失败，不暴露详细错误信息
- 详细的错误日志记录
- 使用 try-finally 确保资源清理

### 8.3 安全性
- 私钥文件不纳入版本控制
- 数据库密码通过配置文件管理
- 反调试检测
- 内存数据清理
- 静默失败策略

---

## 9. 测试验证

### 9.1 功能测试
- [x] 激活码生成成功
- [x] 签名验证通过
- [x] 重复序列号检测
- [x] 有效期设置
- [x] 过期检测
- [x] 设备绑定
- [x] 设备不匹配检测

### 9.2 安全测试
- [x] 伪造激活码验证失败
- [x] 篡改激活码验证失败
- [x] 反调试检测
- [x] 内存清理
- [x] 静默失败

### 9.3 兼容性测试
- [x] 旧格式激活码验证
- [x] 服务端和客户端版本兼容