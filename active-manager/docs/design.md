# 激活码管理系统 - 设计文档

## 1. 架构设计

### 1.1 系统架构图

```
┌─────────────────────────────────────────────────────────────────────┐
│                        客户端/前端（离线验证）                        │
└───────────────────────────┬─────────────────────────────────────────┘
                            │ HTTP/REST
                            ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    Spring Boot Application                         │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────────┐ │
│  │Controller层  │  │ Service层    │  │  Configuration          │ │
│  │Activation    │→ │ Activation   │→ │  RsaKeyConfig           │ │
│  │Controller    │  │ Service      │  │  DataSourceConfig       │ │
│  └──────────────┘  └──────────────┘  └──────────────────────────┘ │
│        │                  │                                       │
│        │                  ▼                                       │
│        │         ┌──────────────┐                                  │
│        │         │ CryptoUtil   │ ← RSA密钥对（2048位）           │
│        │         │ (加密工具类) │                                  │
│        │         └──────────────┘                                  │
│        │                  │                                       │
│        │                  ▼                                       │
│        │         ┌──────────────┐                                  │
│        └────────→│ Mapper层     │→ MySQL数据库                     │
│                  │ Activation   │                                  │
│                  │ RecordMapper │                                  │
│                  └──────────────┘                                  │
└─────────────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    MySQL Database                                  │
│              activation_record 表                                  │
└─────────────────────────────────────────────────────────────────────┘
```

### 1.2 客户端验证工具架构

```
┌─────────────────────────────────────────────────────────────────────┐
│                    客户端验证工具（离线）                             │
│  ┌─────────────────────────────────────────────────────────────┐  │
│  │  ActivationVerifier                                         │  │
│  │  ├── AntiDebug (反调试检测)                                  │  │
│  │  │   ├── IsBeingDebugged()                                  │  │
│  │  │   └── IsRunningInDebugger()                              │  │
│  │  ├── RSA验证                                                 │  │
│  │  │   ├── 公钥验证                                            │  │
│  │  │   └── 签名校验                                            │  │
│  │  ├── 设备绑定验证                                            │  │
│  │  │   └── DeviceId比对                                       │  │
│  │  ├── 有效期检查                                              │  │
│  │  │   └── 时间戳校验                                          │  │
│  │  └── 内存清理                                                │  │
│  │      └── Arrays.fill((byte)0)                               │  │
│  └─────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────┘
```

### 1.3 模块划分

| 模块 | 职责 | 状态 |
| :--- | :--- | :--- |
| controller | REST API控制层，处理HTTP请求 | 已实现 |
| service | 业务逻辑层，激活码生成与验证 | 已实现 |
| mapper | 数据访问层，基于MyBatis Plus | 已实现 |
| entity | 数据库实体模型 | 已实现 |
| dto | 数据传输对象（请求/响应） | 已实现 |
| util | 加密工具类（RSA签名） | 已实现 |
| config | 配置类（密钥加载、数据源） | 已实现 |
| verifier | 客户端离线验证工具 | 已实现 |
| anti-debug | 反调试检测模块 | 已实现 |

---

## 2. 核心流程图

### 2.1 完整流程图

```mermaid
flowchart TD
    subgraph 服务端生成流程
        A[开始] --> B[接收生成请求]
        B --> C{参数校验}
        C -->|失败| D[返回错误]
        C -->|成功| E[获取设备ID]
        E --> F[构造Payload]
        F --> G[私钥签名]
        G --> H[生成激活码]
        H --> I[保存到数据库]
        I --> J[返回激活码]
        J --> K[结束]
    end

    subgraph 客户端验证流程
        L[开始] --> M[反调试检测]
        M -->|检测到调试器| N[静默失败]
        N --> O[结束]
        M -->|安全| P[解密公钥]
        P --> Q[输入激活码]
        Q --> R{格式校验}
        R -->|失败| S[返回错误]
        S --> O
        R -->|成功| T[解析激活码]
        T --> U[验证签名]
        U -->|失败| V[返回错误]
        V --> O
        U -->|成功| W[获取设备ID]
        W --> X{设备匹配?}
        X -->|不匹配| Y[返回错误]
        Y --> O
        X -->|匹配| Z{是否过期?}
        Z -->|已过期| AA[返回错误]
        AA --> O
        Z -->|未过期| AB[内存清理]
        AB --> AC[验证成功]
        AC --> O
    end

    subgraph 安全保护机制
        AD[ConfuserEx混淆] --> AE[类名/方法名随机化]
        AD --> AF[控制流混淆]
        AD --> AG[常量加密]
        
        AH[VMProtect虚拟化] --> AI[核心函数保护]
        AH --> AJ[反调试]
        AH --> AK[反Dump]
        
        AL[内存安全] --> AM[敏感数据用完即清]
    end

    服务端生成流程 --> 客户端验证流程
    客户端验证流程 --> 安全保护机制
```

### 2.2 服务端生成激活码流程图

```mermaid
flowchart LR
    A[开始] --> B[接收请求]
    B --> C{序列号必填?}
    C -->|否| D[返回错误: 序列号不能为空]
    C -->|是| E{查询数据库}
    E --> F{已存在且未过期?}
    F -->|是| G[返回已存在的激活码]
    F -->|否| H{计算过期时间}
    H --> I[构造Payload]
    I --> J[serialNumber]
    J --> K[deviceId]
    K --> L[expireTimestamp]
    L --> M[使用私钥签名]
    M --> N[SHA256withRSA]
    N --> O[生成激活码]
    O --> P[Base64URL编码]
    P --> Q[payload.signature]
    Q --> R[保存到数据库]
    R --> S[返回激活码]
    S --> T[结束]
    
    G --> T
    D --> T

    style A fill:#90EE90,stroke:#333,stroke-width:2px
    style T fill:#90EE90,stroke:#333,stroke-width:2px
    style D fill:#FFB6C1,stroke:#333,stroke-width:2px
```

### 2.3 客户端离线验证流程图

```mermaid
flowchart TD
    A[用户输入激活码] --> B[反调试检测]
    B --> C{检测到调试器?}
    C -->|是| D[静默返回失败]
    D --> Z[结束]
    C -->|否| E[解析激活码格式]
    E --> F{格式正确?}
    F -->|否| G[返回: 格式无效]
    G --> Z
    F -->|是| H[Base64URL解码]
    H --> I[提取Payload和Signature]
    I --> J[使用公钥验证签名]
    J --> K{签名有效?}
    K -->|否| L[返回: 签名验证失败]
    L --> Z
    K -->|是| M[解析Payload]
    M --> N[提取serialNumber]
    N --> O[提取deviceId]
    O --> P[提取expireTimestamp]
    P --> Q{需要设备验证?}
    Q -->|否| R[跳过设备检查]
    Q -->|是| S{设备匹配?}
    S -->|否| T[返回: 设备不匹配]
    T --> Z
    S -->|是| R
    R --> U{未过期?}
    U -->|是| V[内存清理]
    V --> W[验证成功!]
    W --> Z
    U -->|否| X[返回: 已过期]
    X --> Z

    style A fill:#87CEEB,stroke:#333,stroke-width:2px
    style Z fill:#90EE90,stroke:#333,stroke-width:2px
    style D fill:#FFB6C1,stroke:#333,stroke-width:2px
    style W fill:#90EE90,stroke:#333,stroke-width:2px
```

### 2.4 安全层级图

```mermaid
graph TD
    subgraph 应用层["🔒 应用层安全"]
        A1[反调试检测] --> A2[设备绑定验证]
        A2 --> A3[有效期检查]
        A3 --> A4[静默失败策略]
    end

    subgraph 代码层["🛡️ 代码层保护"]
        B1[ConfuserEx混淆] --> B2[类名/方法名随机化]
        B1 --> B3[控制流混淆]
        B1 --> B4[常量加密]
        B5[VMProtect虚拟化] --> B6[代码虚拟化]
        B5 --> B7[反Dump]
        B5 --> B8[反篡改]
    end

    subgraph 数据层["🔐 数据层安全"]
        C1[公钥加密存储] --> C2[运行时动态解密]
        C3[内存清理] --> C4[敏感数据用完即清]
        C3 --> C5[防止内存Dump]
    end

    subgraph 协议层["📡 协议层"]
        D1[RSA 2048位加密] --> D2[数字签名验证]
        D1 --> D3[Base64URL编码]
        D2 --> D4[不可伪造]
        D2 --> D5[不可篡改]
    end

    A1 --> D1
    B1 --> A1
    C1 --> B1

    style 应用层 fill:#f9f,stroke:#333,stroke-width:2px
    style 代码层 fill:#bbf,stroke:#333,stroke-width:2px
    style 数据层 fill:#bfb,stroke:#333,stroke-width:2px
    style 协议层 fill:#ff9,stroke:#333,stroke-width:2px
```

### 2.5 激活码生成流程（时序图）

```mermaid
sequenceDiagram
    participant Admin as 管理员
    participant Server as 激活码服务端
    participant Crypto as CryptoUtil
    participant DB as MySQL数据库

    Admin->>Server: POST /api/activation/generate
    Note over Admin,Server: { "serialNumber": "xxx", "deviceId": "xxx", "expireDays": 365 }
    
    Server->>Server: 参数校验
    alt 参数无效
        Server-->>Admin: { "success": false, "message": "参数错误" }
    else 参数有效
        Server->>DB: 查询是否已存在
        DB-->>Server: 返回记录
        alt 已存在且未过期
            Server-->>Admin: { "success": false, "message": "已存在" }
        else 不存在或已过期
            Server->>Server: 计算过期时间戳
            Server->>Crypto: generateActivationCode(serialNumber, deviceId, expireTime)
            Crypto->>Crypto: 构造payload=serialNumber|deviceId|expireTime
            Crypto->>Crypto: 使用私钥签名(SHA256withRSA)
            Crypto-->>Server: 返回激活码
            Server->>DB: INSERT/UPDATE activation_record
            DB-->>Server: 保存成功
            Server-->>Admin: { "success": true, "activationCode": "..." }
        end
    end
```

### 2.6 客户端离线验证时序图

```mermaid
sequenceDiagram
    participant User as 用户
    participant App as 客户端应用
    participant Verifier as ActivationVerifier
    participant AntiDebug as AntiDebug
    participant RSA as RSA公钥

    User->>App: 输入激活码
    App->>AntiDebug: 检测调试器
    alt 检测到调试器
        AntiDebug-->>App: 检测到异常
        App-->>User: 验证失败(静默)
    else 安全
        App->>Verifier: 验证激活码
        Verifier->>Verifier: 解析激活码
        Verifier->>RSA: 验证签名
        alt 签名验证失败
            RSA-->>Verifier: 验证失败
            Verifier-->>App: { "success": false }
            App-->>User: 验证失败
        else 签名验证成功
            Verifier->>Verifier: 检查设备绑定
            alt 设备不匹配
                Verifier-->>App: { "success": false, "deviceMismatch": true }
                App-->>User: 验证失败
            else 设备匹配
                Verifier->>Verifier: 检查有效期
                alt 已过期
                    Verifier-->>App: { "success": false, "expired": true }
                    App-->>User: 验证失败
                else 未过期
                    Verifier->>Verifier: 清理内存敏感数据
                    Verifier-->>App: { "success": true }
                    App-->>User: 验证成功!
                end
            end
        end
    end
```

### 2.7 激活码结构图

```mermaid
graph LR
    A[激活码] --> B[Payload]
    A --> C[Signature]
    
    B --> D[serialNumber]
    B --> E[deviceId]
    B --> F[expireTimestamp]
    
    C --> G[私钥签名(SHA256withRSA)]
    
    style A fill:#f9f,stroke:#333,stroke-width:2px
    style B fill:#bbf,stroke:#333,stroke-width:2px
    style C fill:#bfb,stroke:#333,stroke-width:2px
```

---

## 3. 技术选型

### 3.1 技术栈

| 分类 | 技术 | 版本 | 说明 |
| :--- | :--- | :--- | :--- |
| 语言 | Java | 21 | LTS版本，性能稳定 |
| 框架 | Spring Boot | 3.4.5 | 社区成熟，生态完善 |
| ORM | MyBatis Plus | 3.5.6 | 简化数据库操作 |
| 数据库 | MySQL | 8.0+ | 开源稳定，性能优异 |
| 连接池 | HikariCP | 5.x | Spring Boot默认连接池 |
| 加密 | JCE | Java内置 | RSA非对称加密 |
| 客户端 | C# / Java | - | 跨平台离线验证 |

### 3.2 客户端技术

| 平台 | 语言 | 框架/库 |
| :--- | :--- | :--- |
| Windows | C# | .NET Framework/Core |
| 跨平台 | Java | 标准库 |

---

## 4. 目录结构

### 4.1 服务端结构

```
activation-code-server/
├── src/
│   └── main/
│       ├── java/
│       │   └── com/jones/activation/
│       │       ├── controller/
│       │       │   └── ActivationController.java
│       │       ├── service/
│       │       │   └── ActivationService.java
│       │       ├── mapper/
│       │       │   └── ActivationRecordMapper.java
│       │       ├── entity/
│       │       │   └── ActivationRecord.java
│       │       ├── dto/
│       │       │   ├── GenerateRequest.java
│       │       │   ├── GenerateResponse.java
│       │       │   ├── VerifyRequest.java
│       │       │   └── VerifyResponse.java
│       │       ├── util/
│       │       │   └── CryptoUtil.java
│       │       ├── config/
│       │       │   └── RsaKeyConfig.java
│       │       └── ActivationCodeServerApplication.java
│       └── resources/
│           ├── application.yml
│           ├── logback-spring.xml
│           └── schema.sql
├── rsa_keys/
│   ├── private_key.pem
│   └── public_key.pem
├── logs/
└── pom.xml
```

### 4.2 客户端验证工具结构

```
activation-code-verifier/
├── ActivationVerifier.java    # Java版验证工具
└── ActivationVerifier.cs      # C#版验证工具（含反调试）
```

---

## 5. 关键类设计

### 5.1 CryptoUtil (加密工具类)

**文件路径**: [CryptoUtil.java](file:///D:/huliang/java/ideaworkspace/jonesDevtools/active-manager/activation-code-server/src/main/java/com/jones/activation/util/CryptoUtil.java)

| 方法名 | 功能说明 | 参数 | 返回值 |
| :--- | :--- | :--- | :--- |
| generateKeyPair | 生成RSA密钥对 | keySize: int | KeyPair |
| parsePrivateKey | 解析PEM格式私钥 | privateKeyPem: String | PrivateKey |
| parsePublicKey | 解析PEM格式公钥 | publicKeyPem: String | PublicKey |
| generateActivationCode | 生成激活码 | serialNumber, deviceId, expireTimestamp | String |
| parseAndVerify | 解析并验证激活码 | activationCode, expectedDeviceId | ActivationCodeParseResult |

### 5.2 ActivationService (业务服务)

**文件路径**: [ActivationService.java](file:///D:/huliang/java/ideaworkspace/jonesDevtools/active-manager/activation-code-server/src/main/java/com/jones/activation/service/ActivationService.java)

| 方法名 | 功能说明 | 参数 | 返回值 |
| :--- | :--- | :--- | :--- |
| generateActivationCode | 生成激活码主逻辑 | GenerateRequest | GenerateResponse |
| verifyActivationCode | 验证激活码主逻辑 | VerifyRequest | VerifyResponse |

### 5.3 ActivationVerifier (客户端验证工具)

**文件路径**: [ActivationVerifier.cs](file:///D:/huliang/java/ideaworkspace/jonesDevtools/active-manager/activation-code-verifier/ActivationVerifier.cs)

| 方法名 | 功能说明 | 参数 | 返回值 |
| :--- | :--- | :--- | :--- |
| Verify | 验证激活码 | activationCode, expectedDeviceId | VerifyResult |
| - | - | - | - |
| AntiDebug.IsBeingDebugged | 反调试检测 | 无 | boolean |

### 5.4 实体类

**ActivationRecord** - [ActivationRecord.java](file:///D:/huliang/java/ideaworkspace/jonesDevtools/active-manager/activation-code-server/src/main/java/com/jones/activation/entity/ActivationRecord.java)

| 字段名 | 类型 | 说明 |
| :--- | :--- | :--- |
| id | Long | 主键ID |
| serialNumber | String | 设备序列号 |
| deviceId | String | 绑定的设备ID |
| activationCode | String | 激活码 |
| expireTime | Long | 过期时间戳(毫秒) |
| createTime | LocalDateTime | 创建时间 |
| updateTime | LocalDateTime | 更新时间 |

---

## 6. 数据库设计

### 6.1 数据库配置

| 配置项 | 值 | 说明 |
| :--- | :--- | :--- |
| 数据库类型 | MySQL | 关系型数据库 |
| 数据库名 | tools | 数据库名称 |
| 用户名 | tools | 数据库用户 |
| 密码 | toolsmarschat | 数据库密码 |
| 主机 | 192.168.31.182 | 数据库服务器地址 |
| 端口 | 3306 | MySQL默认端口 |
| 字符集 | utf8mb4 | 支持完整Unicode |

### 6.2 表结构

**表名**: `activation_record`

| 字段名 | 类型 | 约束 | 说明 |
| :--- | :--- | :--- | :--- |
| id | BIGINT | PRIMARY KEY, AUTO_INCREMENT | 主键ID |
| serial_number | VARCHAR(512) | NOT NULL, UNIQUE KEY | 设备序列号 |
| device_id | VARCHAR(128) | DEFAULT '' | 绑定的设备ID |
| activation_code | TEXT | NOT NULL | 激活码 |
| expire_time | BIGINT | NOT NULL | 过期时间戳(毫秒) |
| create_time | DATETIME | NOT NULL | 创建时间 |
| update_time | DATETIME | NOT NULL | 更新时间 |

### 6.3 ER图

```mermaid
erDiagram
    ACTIVATION_RECORD {
        bigint id PK "主键"
        varchar serial_number UK "设备序列号"
        varchar device_id "绑定的设备ID"
        text activation_code "激活码"
        bigint expire_time "过期时间戳"
        datetime create_time "创建时间"
        datetime update_time "更新时间"
    }
```

---

## 7. 接口设计

### 7.1 生成激活码接口

| 属性 | 值 |
| :--- | :--- |
| URL | `POST /api/activation/generate` |
| 方法 | POST |
| 所属Controller | ActivationController |
| 功能描述 | 根据序列号和设备ID生成激活码 |

**请求体**:
```json
{
  "serialNumber": "设备序列号",
  "deviceId": "可选：设备ID",
  "expireDays": 365
}
```

**成功响应**:
```json
{
  "success": true,
  "message": "激活码生成成功",
  "activationCode": "payload.signature",
  "expireTime": 1735689600000,
  "serialNumber": "设备序列号",
  "deviceId": "设备ID"
}
```

### 7.2 验证激活码接口

| 属性 | 值 |
| :--- | :--- |
| URL | `POST /api/activation/verify` |
| 方法 | POST |
| 所属Controller | ActivationController |
| 功能描述 | 验证激活码有效性（支持设备绑定验证） |

**请求体**:
```json
{
  "activationCode": "payload.signature",
  "deviceId": "可选：当前设备ID"
}
```

**成功响应**:
```json
{
  "success": true,
  "message": "激活码验证成功",
  "serialNumber": "设备序列号",
  "deviceId": "设备ID",
  "expireTime": 1735689600000,
  "expired": false,
  "deviceMismatch": false
}
```

---

## 8. 安全性设计

### 8.1 加密机制

**激活码结构**:
```
激活码 = Base64URL(payload) + "." + Base64URL(signature)
payload = serialNumber + "|" + deviceId + "|" + expireTimestamp
signature = SHA256withRSA(payload, privateKey)
```

**加密流程**:
1. 构造payload: `序列号|设备ID|过期时间戳`
2. 使用私钥对payload进行SHA256withRSA签名
3. 将payload和signature分别进行Base64URL编码
4. 用"."连接两部分形成最终激活码

### 8.2 反调试检测（C#版本）

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
    
    if (end - start < 10) return true; // 执行太快，可能在调试

    return false;
}
```

### 8.3 内存清理

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

### 8.4 安全特性汇总

| 特性 | 实现方式 | 说明 |
| :--- | :--- | :--- |
| 不可伪造 | RSA非对称加密 | 没有私钥无法生成有效激活码 |
| 不可篡改 | 数字签名 | 任何修改都会导致签名验证失败 |
| 设备绑定 | 激活码包含设备ID | 激活码只能在绑定设备上使用 |
| 反调试 | AntiDebug检测 | 检测到调试器时静默失败 |
| 内存安全 | 用完即清 | 敏感数据验证后立即清零 |
| 静默失败 | 统一错误信息 | 不暴露详细错误信息 |

---

## 9. 向后兼容性设计

### 9.1 旧格式兼容

激活码支持两种格式：
- **新格式**: `serialNumber|deviceId|expireTimestamp.signature`
- **旧格式**: `serialNumber|expireTimestamp.signature`

验证逻辑自动检测并兼容两种格式。

### 9.2 设备ID兼容

- 如果激活码中deviceId为空，验证时不进行设备匹配
- 如果请求中expectedDeviceId为空，验证时不进行设备匹配
- 只有两者都有值时才进行匹配

---

## 10. 部署与集成设计

### 10.1 环境要求

| 环境 | 要求 |
| :--- | :--- |
| JDK | 21+ |
| Maven | 3.8+ |
| MySQL | 8.0+ |
| .NET | 6.0+ (仅C#客户端) |

### 10.2 启动方式

**开发态运行**:
```bash
cd activation-code-server
mvn spring-boot:run
```

**打包构建**:
```bash
cd activation-code-server
mvn clean package
```

### 10.3 客户端集成

**Java版本**:
```java
String publicKeyPem = Files.readString(Paths.get("public_key.pem"));
ActivationVerifier verifier = new ActivationVerifier(publicKeyPem);
VerifyResult result = verifier.verify(activationCode, deviceId);
```

**C#版本**:
```csharp
string publicKeyPem = File.ReadAllText("public_key.pem");
ActivationVerifier verifier = new ActivationVerifier(publicKeyPem);
VerifyResult result = verifier.Verify(activationCode, deviceId);
```