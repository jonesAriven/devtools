# 激活码管理系统 V2 - 设计文档

## 1. 系统架构

### 1.1 三大工程架构图

```
┌───────────────────────────────────────────────────────────────────┐
│                     工具软件层（可多个）                            │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐            │
│  │  QRCodeTool  │  │  Tool B      │  │  Tool C      │  ...       │
│  │  (C# WinForm)│  │  (Java)      │  │  (Python)    │            │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘            │
│         │                 │                 │                     │
│         ▼                 ▼                 ▼                     │
│  ┌─────────────────────────────────────────────────────────┐     │
│  │          activation-code-verifier（验证工具层）           │     │
│  │  ┌────────────┐ ┌──────────┐ ┌──────────┐ ┌─────────┐ │     │
│  │  │Activation  │ │DeviceInfo│ │Secure    │ │Anti     │ │     │
│  │  │Guard       │ │          │ │Storage   │ │Debug    │ │     │
│  │  └────────────┘ └──────────┘ └──────────┘ └─────────┘ │     │
│  │  ┌────────────┐ ┌──────────┐ ┌──────────┐             │     │
│  │  │Activation  │ │TimeGuard │ │Verify    │             │     │
│  │  │Verifier    │ │          │ │Result    │             │     │
│  │  └────────────┘ └──────────┘ └──────────┘             │     │
│  └─────────────────────────────────────────────────────────┘     │
│                          │ 加密序列号（上行）                      │
│                          │ 激活码（下行）                          │
│                          ▼                                       │
│  ┌─────────────────────────────────────────────────────────┐     │
│  │          activation-code-server（服务端层）               │     │
│  │  ┌────────────┐ ┌──────────┐ ┌──────────┐ ┌─────────┐ │     │
│  │  │Controller  │ │Service   │ │CryptoUtil│ │RsaKey   │ │     │
│  │  │            │ │          │ │          │ │Config   │ │     │
│  │  └────────────┘ └──────────┘ └──────────┘ └─────────┘ │     │
│  │  ┌────────────┐ ┌──────────┐ ┌──────────────────────┐ │     │
│  │  │Mapper      │ │Entity    │ │管理后台(HTML)         │ │     │
│  │  │            │ │          │ │index.html / tool.html│ │     │
│  │  └────────────┘ └──────────┘ └──────────────────────┘ │     │
│  └─────────────────────────────────────────────────────────┘     │
│                          │                                       │
│                          ▼                                       │
│  ┌─────────────────────────────────────────────────────────┐     │
│  │                    MySQL Database                        │     │
│  │         activation_record / activation_log               │     │
│  └─────────────────────────────────────────────────────────┘     │
└───────────────────────────────────────────────────────────────────┘
```

### 1.2 数据流图

```
工具软件                     验证工具(verifier)                  服务端(server)
   │                              │                                  │
   │── LaunchWithProtection() ──→│                                  │
   │                              │── 加载activation.dat             │
   │                              │── AES解密 → 读取激活码           │
   │                              │── RSA验证 + 设备绑定检查         │
   │                              │                                  │
   │  （首次使用/已过期）          │                                  │
   │                              │── 弹窗显示唯一序列号             │
   │←── 用户复制序列号 ──────────│                                  │
   │                              │                                  │
   │── 用户粘贴序列号给管理员 ───────────────────────────────────→│
   │                              │                   服务端解密序列号│
   │                              │                   生成激活码      │
   │←── 管理员返回激活码 ────────────────────────────────────────│
   │                              │                                  │
   │── 用户粘贴激活码 ──────────→│                                  │
   │                              │── RSA验证 + 设备绑定             │
   │                              │── AES加密保存activation.dat      │
   │←── 验证成功 ────────────────│                                  │
   │                              │                                  │
   │── 软件正常运行 ─────────────│── 每60秒定时检查 ──→│            │
   │                              │                                  │
```

---

## 2. 加密体系设计

### 2.1 激活码签名机制

```
激活码 = Base64URL(payload) + "." + Base64URL(signature)

payload = 序列号 + "|" + 设备ID + "|" + 过期时间戳(毫秒)
signature = SHA256withRSA(payload, privateKey)
```

- **不可伪造**：无私钥无法生成有效签名
- **不可篡改**：任何修改导致签名验证失败
- **设备绑定**：设备ID嵌入payload，验证时比对

### 2.2 唯一序列号加密机制

```
明文 = 初始序列号 + "|" + 设备ID + "|" + 机器码
       例: QRTOOL|A1B2C3D4E5F6...|AA-BB-CC-DD-EE-FF

加密: 逐字节 XOR 0x5A → Base64编码
解密: Base64解码 → 逐字节 XOR 0x5A → 按 "|" 分割
```

- 客户端verifier生成，服务端解密
- 加密方式两端必须一致

### 2.3 RSA公钥保护机制

```
公钥PEM → 逐字节 XOR [0x3A, 0x7C, 0xE5, 0x91] 循环 → 硬编码为byte[]
运行时: XOR解密 → ImportFromPem → 清零encryptedKey数组
```

- 4字节循环XOR，攻击者需同时猜对4个key
- 使用后立即清零内存中的加密数据
- 公钥只在首次验证时解密一次，之后RSA对象持有密钥

### 2.4 激活码本地存储加密

```
文件: activation.dat
算法: AES-256-CBC, PKCS7填充
密钥派生: PBKDF2(设备ID, salt, 10000次, SHA256) → 32字节
IV: 固定16字节
文件结构: salt(16字节) + AES密文
```

- 绑定设备ID：不同设备无法解密
- 安全删除：覆写全零后再删除文件

---

## 3. 安全防护设计

### 3.1 安全层级

```
┌─────────────────────────────────────────────┐
│  第一层：协议层 - RSA签名验证                  │
│  ── 不可伪造、不可篡改                         │
├─────────────────────────────────────────────┤
│  第二层：数据层 - 加密存储                      │
│  ── 公钥XOR加密、激活码AES加密、序列号XOR加密   │
│  ── 敏感数据用完即清零                         │
├─────────────────────────────────────────────┤
│  第三层：运行层 - 反调试+防篡改                 │
│  ── 反调试检测（3种方式）                       │
│  ── 时间篡改防护（单调时钟）                    │
│  ── 定时过期检查（60秒）                       │
├─────────────────────────────────────────────┤
│  第四层：信息层 - 静默失败                      │
│  ── 验证失败不返回详细原因                      │
│  ── 检测到异常静默退出                         │
└─────────────────────────────────────────────┘
```

### 3.2 反调试检测

| 检测方式 | 实现原理 | 检测目标 |
|---------|---------|---------|
| Debugger.IsAttached | .NET托管调试器检测 | dnSpy、Visual Studio调试 |
| CheckRemoteDebuggerPresent | Win32 API远程调试器检测 | x64dbg、OllyDbg |
| GetTickCount时间差 | 1亿次空循环计时检测 | 单步调试、断点 |

**处理策略**：检测到调试器时，`Verify()`直接返回`VerifyResult.Fail()`，不抛异常、不弹窗、不暴露任何信息。

### 3.3 时间篡改防护

```
正常验证成功 → 记录 {系统时间, 单调时钟, 过期时间戳} 到缓存文件
下次验证时 → 推算时间 = 缓存系统时间 + (当前单调时钟 - 缓存单调时钟)
若 |推算时间 - 系统时间| > 24小时 且 推算时间已过期 → 使用推算时间
```

- 使用`System.Threading.Stopwatch`作为单调时钟（基于高精度性能计数器，不受系统时间修改影响）
- 缓存文件路径：`activation_cache/activation_{SHA256(序列号)}.dat`

### 3.4 激活码文件防复制

| 攻击方式 | 防护措施 |
|---------|---------|
| 复制activation.dat到其他电脑 | AES密钥由设备ID派生，不同设备无法解密 |
| 直接读取文件内容 | AES-256加密，无密钥无法还原 |
| 替换DLL绕过验证 | 建议使用强名称签名（待实施） |
| 内存dump提取密钥 | 公钥使用后清零，敏感数据用完即清 |

### 3.5 错误信息脱敏

| 场景 | 旧版返回 | V2返回 |
|------|---------|--------|
| 格式错误 | "激活码格式无效" | Fail()（无信息） |
| 签名失败 | "签名验证失败" | Fail()（无信息） |
| 调试检测 | "检测到调试器" | Fail()（无信息） |
| 过期 | "激活码已过期" | FailExpired()（仅标记expired=true） |
| 设备不匹配 | "设备不匹配" | FailDeviceMismatch()（仅标记deviceMismatch=true） |

VerifyResult移除了Message字段，不返回任何文字描述。

---

## 4. 接口设计

### 4.1 服务端API

| 方法 | 路径 | 功能 |
|------|------|------|
| POST | /activecode/api/activation/generate | 生成激活码 |
| POST | /activecode/api/activation/verify | 验证激活码 |
| GET | /activecode/api/activation/list | 查询激活码记录 |
| GET | /activecode/api/activation/logs | 查询操作日志 |
| GET | /activecode/api/activation/parse-code | 解析激活码内容 |
| GET | /activecode/api/activation/parse-serial | 解析序列号信息 |
| DELETE | /activecode/api/activation/{id} | 删除激活码记录 |

### 4.2 验证工具公共API

| 类 | 方法 | 说明 |
|----|------|------|
| ActivationGuard | LaunchWithProtection(initialSerial, checkIntervalMs) | 一站式启动保护 |
| ActivationGuard | CheckWithAutoDevice(activationCode) | 验证激活码（自动绑定设备） |
| ActivationGuard | StartPeriodicCheckWithAutoDevice(code, interval, callback) | 定时检查 |
| ActivationGuard | StopPeriodicCheck() | 停止定时检查 |
| ActivationGuard | ProtectWithAutoDevice(activationCode) | 严格模式（失败退出进程） |
| DeviceInfo | GetSerialNumber(initialSerial) | 生成加密唯一序列号 |
| DeviceInfo | GetDeviceId() | 获取设备ID |
| DeviceInfo | GetMachineCode() | 获取机器码 |
| DeviceInfo | ParseSerialNumber(encrypted) | 解析加密唯一序列号 |

---

## 5. 数据库设计

### 5.1 activation_record表

| 列名 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| serial_number | VARCHAR(512) | NOT NULL, UNIQUE | 唯一序列号 |
| device_id | VARCHAR(128) | DEFAULT '' | 设备ID |
| activation_code | TEXT | NOT NULL | 激活码 |
| expire_time | BIGINT | NOT NULL | 过期时间戳(毫秒) |
| activated_time | DATETIME | DEFAULT NULL | 首次激活时间 |
| expire_minutes | INT | DEFAULT NULL | 有效期分钟数 |
| initial_serial | VARCHAR(256) | DEFAULT NULL | 初始序列号 |
| machine_code | VARCHAR(256) | DEFAULT NULL | 机器码 |
| create_time | DATETIME | NOT NULL | 创建时间 |
| update_time | DATETIME | NOT NULL | 更新时间 |

### 5.2 activation_log表

| 列名 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| record_id | BIGINT | DEFAULT NULL | 关联记录ID |
| serial_number | VARCHAR(512) | DEFAULT NULL | 序列号 |
| device_id | VARCHAR(128) | DEFAULT NULL | 设备ID |
| event_type | VARCHAR(32) | NOT NULL | 事件类型 |
| event_message | VARCHAR(512) | DEFAULT NULL | 事件描述 |
| client_ip | VARCHAR(64) | DEFAULT NULL | 客户端IP |
| create_time | DATETIME | NOT NULL | 创建时间 |

---

## 6. 技术选型

| 分类 | 技术 | 版本 | 说明 |
|------|------|------|------|
| 服务端语言 | Java | 21 | LTS版本 |
| 服务端框架 | Spring Boot | 3.4.5 | 成熟稳定 |
| ORM | MyBatis Plus | 3.5.6 | 简化数据库操作 |
| 数据库 | MySQL | 8.0+ | 关系型数据库 |
| 验证工具 | C# | .NET 6 | 类库输出DLL |
| 加密算法 | RSA | 2048位 | 非对称签名 |
| 本地存储加密 | AES | 256位 CBC | 对称加密 |
| 密钥派生 | PBKDF2 | 10000次迭代 | 设备ID派生AES密钥 |
