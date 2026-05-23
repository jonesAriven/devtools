# 项目架构与分工

## 三大工程

### 1. activation-code-server — 激活码服务端（激活码生成工具）
- 路径: `D:\huliang\java\ideaworkspace\jonesDevtools\active-manager\activation-code-server`
- 技术栈: Java 21 + Spring Boot 3.4.5 + MyBatis-Plus + MySQL
- JDK路径: `D:\huliang\software\Java\jdk-21.0.11`（注意：不要使用同目录下的 jdk-25）
- 职责:
  - 生成并管理激活码（RSA私钥签名）
  - 验证激活码有效性
  - 管理后台（激活码管理工具、激活码记录、操作日志、解析工具）
  - 稽核日志记录
  - 接收客户端加密的唯一序列号，自动解密解析出设备ID和机器码
- 端口: 8080
- 数据库: 192.168.31.182:3306/tools (用户: tools, 密码: toolsmarschat)

### 2. activation-code-verifier — 激活码验证工具
- 路径: `D:\huliang\java\ideaworkspace\jonesDevtools\active-manager\activation-code-verifier`
- 技术栈: C# .NET 6 类库（输出 Jones.Activation.dll）
- 职责:
  - 提供API供工具软件接入，验证软件是否过期
  - 生成唯一序列号（加密: 初始序列号|设备ID|机器码 → XOR 0x5A → Base64）
  - 解析唯一序列号（ParseSerialNumber）
  - RSA公钥验证激活码签名
  - 设备指纹采集（CPU+主板+硬盘+MAC → SHA256 → 设备ID）
  - 机器码生成（MAC地址格式化）
  - 防调试检测（AntiDebug）
  - 时间篡改检测（TimeGuard）
  - 运行时定时过期检查（每60秒）
- 核心API:
  - `DeviceInfo.GetSerialNumber(initialSerial)` — 生成加密唯一序列号
  - `DeviceInfo.ParseSerialNumber(encrypted)` — 解析加密唯一序列号
  - `DeviceInfo.GetDeviceId()` — 获取设备ID
  - `DeviceInfo.GetMachineCode()` — 获取机器码
  - `ActivationGuard.Protect(code, deviceId)` — 验证并保护
  - `ActivationGuard.CheckWithAutoDevice(code)` — 自动绑定设备验证
  - `ActivationGuard.StartPeriodicCheckWithAutoDevice(code, interval, callback)` — 定时检查
- 重要: 后续可提供多种语言版本（不止C#），供不同语言开发的工具软件接入

### 3. QRCodeTool — 二维码扫描工具（示例工具软件）
- 路径: `D:\huliang\java\ideaworkspace\jonesDevtools\QR_GENERATORBYC#V1\PublishSingleFile`
- 技术栈: C# .NET 6 WinForms
- 职责:
  - 只做工具本身的功能（二维码扫描）
  - 若需要激活码验证，接入 activation-code-verifier 即可实现
- 接入方式:
  - 引用 `lib\Jones.Activation.dll`
  - 启动时调用 `ActivationGuard.CheckWithAutoDevice` 验证
  - 无有效激活码则弹出激活对话框（显示唯一序列号）
  - 运行时定时检查过期

## 数据流

```
工具软件(QRCodeTool)                    验证工具(verifier)                     服务端(server)
     |                                       |                                      |
     |-- DeviceInfo.GetSerialNumber() ------>|                                      |
     |<-- 加密的唯一序列号 ------------------|                                      |
     |                                       |                                      |
     |-- 用户复制序列号给管理员 ------------>|                                      |
     |                                       |         管理员粘贴序列号 ------------>|
     |                                       |         服务端自动解密 --------------|
     |                                       |         生成激活码(RSA签名) -------->|
     |                                       |                                      |
     |-- 用户输入激活码 -------------------->|                                      |
     |-- ActivationGuard.CheckWithAutoDevice->|                                      |
     |<-- 验证结果(有效/过期/设备不匹配) ---|                                      |
```

## 开发规范

- 工具软件只做业务功能，激活验证逻辑全部在 verifier 中
- verifier 是独立的类库，不依赖任何具体工具软件
- server 不关心客户端是什么语言，只关心加密序列号的解密和激活码的生成/验证
- 新工具软件接入时，只需引用 verifier 对应语言的库，调用 API 即可
- 唯一序列号的加密方式(XOR 0x5A + Base64)在 server 和 verifier 中必须保持一致

## 工具安装规则

- 所有需要自动安装的软件/工具，必须安装到 `E:\huliang\softWare` 目录下
- 禁止安装到其他目录（如 C:\Program Files、C:\Users 等）

## JDK 版本

- 统一使用 JDK 21（路径: `D:\huliang\software\Java\jdk-21.0.11`）
- 禁止使用 `D:\huliang\software\Java\jdk-25`，该目录仅为测试用途

## 数据库变更规范

- **禁止删除表或清空数据**，所有表结构变更必须以升级（ALTER TABLE）方式进行
- 新增列：先查询 `information_schema.COLUMNS` 判断列是否存在，不存在才 ALTER TABLE ADD COLUMN
- 新增索引：先查询 `information_schema.STATISTICS` 判断索引是否存在，不存在才添加
- `CREATE TABLE IF NOT EXISTS` 仅用于首次建表，已有表的结构变更一律用 ALTER TABLE
- `DatabaseInitializer` 中的迁移逻辑必须兼容已有数据，不能破坏现有记录
