# 激活码管理系统 - 开发过程文档

---

## 1. 项目初始化阶段

### 1.1 环境准备
- **JDK版本**: 确定使用 JDK 21 LTS 版本
- **Maven配置**: 配置阿里云Maven镜像加速依赖下载
- **IDE**: 使用 IntelliJ IDEA 作为开发工具

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
- 确定需要存储的字段：序列号、激活码、过期时间、创建时间、更新时间
- 添加唯一索引确保序列号唯一性

**步骤2**: 创建实体类 `ActivationRecord`
- 使用 MyBatis Plus 注解配置表映射
- 生成 getter/setter 方法

**步骤3**: 创建 Mapper 接口
- 继承 BaseMapper 获取基础 CRUD 功能
- 无需额外编写 SQL 语句

**步骤4**: 编写数据库初始化脚本 `schema.sql`
- 创建数据库和表结构
- 设置合适的字符集和存储引擎

### 2.2 加密工具类开发

**步骤1**: 设计激活码结构
```
激活码 = Base64URL(序列号|过期时间) + "." + Base64URL(RSA签名)
```

**步骤2**: 实现 CryptoUtil 工具类
- `generateKeyPair()`: 生成 RSA 密钥对
- `parsePrivateKey()`: 解析 PEM 格式私钥
- `parsePublicKey()`: 解析 PEM 格式公钥
- `generateActivationCode()`: 生成激活码
- `parseAndVerify()`: 解析并验证激活码

**步骤3**: 配置密钥加载
- 创建 `RsaKeyConfig` 配置类
- 在启动时自动加载密钥文件

### 2.3 业务逻辑层开发

**步骤1**: 实现 `ActivationService`
- `generateActivationCode()`: 生成激活码主逻辑
  - 参数校验
  - 查询是否已存在未过期激活码
  - 生成新激活码
  - 保存到数据库
- `verifyActivationCode()`: 验证激活码主逻辑
  - 解析激活码
  - 验证签名
  - 检查有效期

### 2.4 控制器层开发

**步骤1**: 实现 `ActivationController`
- `@RestController` 注解标识
- `@RequestMapping("/api/activation")` 设置基础路径
- `@PostMapping("/generate")` 生成激活码接口
- `@PostMapping("/verify")` 验证激活码接口

### 2.5 DTO 类定义

**步骤1**: 创建请求 DTO
- `GenerateRequest`: serialNumber, expireDays
- `VerifyRequest`: activationCode

**步骤2**: 创建响应 DTO
- `GenerateResponse`: success, message, activationCode, expireTime, serialNumber
- `VerifyResponse`: success, message, serialNumber, expireTime, expired

---

## 3. 数据库配置与测试

### 3.1 配置远程数据库
修改 `application.yml`:
```yaml
spring:
  datasource:
    url: jdbc:mysql://192.168.31.182:3306/tools?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
    username: tools
    password: toolsmarschat
    driver-class-name: com.mysql.cj.jdbc.Driver
```

### 3.2 数据库连接测试
- 遇到 Java 版本兼容性问题（JDK 25 存在 Socket 连接问题）
- 切换到 JDK 21 后连接成功
- 使用 Python pymysql 辅助测试数据库连接

### 3.3 表初始化
- 通过 Spring Boot `spring.sql.init` 自动执行 `schema.sql`
- 手动执行 `init_db_pymysql.py` 创建数据库和表

---

## 4. 验证工具开发

### 4.1 Java 验证工具
创建独立的 `ActivationVerifier.java`
- 仅使用公钥进行验证
- 不依赖服务端，可独立运行

### 4.2 C# 验证工具
创建 `ActivationVerifier.cs`
- 使用 .NET RSA 类进行签名验证
- 实现 Base64URL 解码
- 与 Java 版本完全兼容

---

## 5. 问题与解决方案

### 5.1 JDK 版本兼容性问题
**问题**: JDK 25 连接 MySQL 失败，报 "Socket operation on nonsocket" 错误
**原因**: JDK 25 的 Socket 实现存在问题
**解决方案**: 切换到 JDK 21 LTS 版本

### 5.2 数据库连接问题
**问题**: 无法直接连接远程 MySQL
**解决方案**: 
- 使用 Python pymysql 测试连接成功
- Spring Boot + HikariCP 连接池成功连接

### 5.3 文件夹重命名问题
**问题**: 原文件夹名称包含中文，无法直接重命名
**解决方案**: 先复制到新文件夹，停止占用进程后删除原文件夹

---

## 6. 代码优化与规范

### 6.1 代码风格
- 使用 Lombok 简化实体类（本项目未使用，保持简洁）
- 使用 Builder 模式构建响应对象
- 使用 SLF4J 进行日志记录

### 6.2 异常处理
- 统一异常处理
- 详细的错误日志记录
- 友好的错误提示信息

### 6.3 安全性
- 私钥文件不纳入版本控制
- 数据库密码通过配置文件管理
- 使用环境变量或配置中心管理敏感信息