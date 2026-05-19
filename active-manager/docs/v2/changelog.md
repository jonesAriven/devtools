# 激活码管理系统 V2 - 迭代记录

## 迭代概述

本次迭代基于V1版本进行了多轮优化，主要涉及：安全加固、架构分工明确、管理后台完善、客户端接入简化。

---

## 迭代1：架构分工与唯一序列号

### 变更内容

1. **明确三大工程分工**
   - activation-code-server：激活码服务端，生成并管理激活码
   - activation-code-verifier：激活码验证工具，提供API供工具软件接入
   - QRCodeTool：二维码扫描工具，只做工具功能，接入verifier即可

2. **唯一序列号机制**
   - 客户端弹窗从显示"设备ID+机器码"两个字段改为只显示"唯一序列号"一个加密值
   - 唯一序列号 = `初始序列号|设备ID|机器码` → XOR 0x5A → Base64
   - 服务端自动解密唯一序列号，解析出设备ID和机器码

3. **设备绑定验证**
   - 客户端验证改为 `CheckWithAutoDevice`，激活码绑定设备
   - 定时检查改为 `StartPeriodicCheckWithAutoDevice`，过期后同样校验设备

### 涉及文件

| 文件 | 变更 |
|------|------|
| DeviceInfo.cs | 新增 GetSerialNumber / ParseSerialNumber / SerialNumberInfo |
| ActivationGuard.cs | 弹窗改为只显示唯一序列号 |
| Program.cs (QRCodeTool) | 改用 CheckWithAutoDevice |
| ActivationService.java | 新增唯一序列号解密逻辑，自动解析设备ID和机器码 |
| GenerateResponse.java | 新增 initialSerial / machineCode 字段 |
| index.html | 管理后台显示解析结果 |

---

## 迭代2：管理后台完善

### 变更内容

1. **管理后台四个标签页**
   - 激活码管理工具：生成激活码 + 验证激活码
   - 激活码记录：查询所有记录，搜索/筛选
   - 操作日志：稽核日志，按事件类型筛选
   - 解析工具：解析激活码/序列号

2. **独立生成页面**
   - 新增 tool.html，简洁的激活码生成页面
   - 有效期最长30天
   - 管理后台有效期支持5分钟/1小时/1天/30天/1年

3. **浏览器缓存问题修复**
   - 新增 WebMvcConfig.java，静态资源禁用缓存
   - HTML添加 Cache-Control / Pragma / Expires meta标签

### 涉及文件

| 文件 | 变更 |
|------|------|
| index.html | 四标签页管理后台 |
| tool.html | 新增独立生成页面 |
| admin.html | 重定向到index.html |
| WebMvcConfig.java | 新增，静态资源不缓存 |

---

## 迭代3：安全加固

### 变更内容

1. **激活码文件加密存储**
   - 新增 SecureStorage.cs
   - AES-256-CBC加密，密钥由设备ID + PBKDF2派生
   - 文件从 activation.lic(明文) 改为 activation.dat(加密)
   - 绑定设备：不同设备无法解密，防止复制文件
   - 安全删除：覆写全零后再删除

2. **错误信息脱敏**
   - VerifyResult.cs 移除 Message 字段
   - ActivationVerifier.cs 所有失败统一返回 VerifyResult.Fail()
   - 不再返回"签名验证失败""格式无效"等详细信息

3. **公钥加密增强**
   - 从单字节 XOR 0x5A 改为4字节分段循环 XOR (0x3A, 0x7C, 0xE5, 0x91)
   - 攻击者需同时猜对4个key才能还原公钥

4. **DLL项目配置更新**
   - 目标框架从 net6.0 改为 net6.0-windows
   - 新增 UseWindowsForms 支持（弹窗在DLL中实现）

5. **一站式启动保护 API**
   - 新增 ActivationGuard.LaunchWithProtection(initialSerial)
   - 一行代码完成：加载缓存→验证→弹窗→定时检查→加密存储
   - Program.cs 从268行简化到21行

### 涉及文件

| 文件 | 变更 |
|------|------|
| SecureStorage.cs | 新增，AES加密存储 |
| ActivationGuard.cs | 重写，新增LaunchWithProtection，公钥4字节XOR，弹窗内置 |
| ActivationVerifier.cs | 重写，错误脱敏，反调试检测 |
| VerifyResult.cs | 重写，移除Message字段 |
| DeviceInfo.cs | 无变更 |
| TimeGuard.cs | 无变更 |
| AntiDebug.cs | 无变更 |
| ActivationCodeVerifier.csproj | net6.0-windows + UseWindowsForms |
| Program.cs (QRCodeTool) | 简化为21行 |

---

## 迭代4：过期退出Bug修复

### 问题描述

二维码工具启动后，激活码过期弹窗提示"授权已失效"，但主窗口并未关闭，用户仍可继续使用工具。

### 根因分析

`ShowExpiredDialog` 从 `System.Threading.Timer` 的线程池线程调用：
- `MessageBox.Show` 在后台线程上不是真正模态的，主窗口仍可操作
- `Environment.Exit` 从后台线程调用不能正确终止WinForms消息循环

### 修复方案

1. 通过 `Application.OpenForms[0].Invoke()` 将弹窗和退出操作调度到UI线程
2. 先调用 `Application.Exit()` 正确关闭WinForms消息循环和所有窗口
3. 最后 `Environment.Exit(1002)` 作为兜底强制退出

### 修复后流程

```
定时检查发现过期
  → 停止定时器
  → 安全删除activation.dat
  → UI线程弹出模态对话框（主窗口不可操作）
  → 用户点击确定
  → Application.Exit() 关闭所有窗口
  → Environment.Exit(1002) 兜底退出
```

### 涉及文件

| 文件 | 变更 |
|------|------|
| ActivationGuard.cs | ShowExpiredDialog改为UI线程调用，Application.Exit + Environment.Exit |

---

## 迭代5：V2文档编写

### 变更内容

基于多轮迭代的实际代码，重新编写四份文档：

| 文档 | 角度 | 核心内容 |
|------|------|---------|
| requirements.md | 业务 | 三大工程定位、功能需求编号、业务流程、数据需求 |
| design.md | 方案 | 架构图、加密体系、安全四层防护、接口设计、数据库设计 |
| development.md | 开发 | 工程结构、编译打包、DLL接入3步法、发布流程 |
| usage.md | 使用 | 管理员/终端用户/开发者三视角操作指南、FAQ |

与V1文档的主要差异：
- 三大工程分工明确写入文档
- 安全加固完整记录（AES加密存储、4字节XOR、错误脱敏、单调时钟）
- 一站式接入API替代旧版分步接入
- 有效期单位从天改为分钟
- 稽核日志6种事件类型

---

## 迭代6：DLL强名称签名 + Obfuscar代码混淆

### 变更内容

1. **DLL强名称签名**
   - 生成RSA 2048位密钥文件 key.snk
   - csproj添加 `SignAssembly=true` + `AssemblyOriginatorKeyFile=key.snk`
   - 新增 AssemblyInfo.cs 手动管理程序集信息（因禁用自动生成）
   - 效果：DLL带有强名称公钥标记，攻击者无法用同名假DLL替换（签名不匹配会加载失败）

2. **Obfuscar代码混淆**
   - 安装 `Obfuscar.GlobalTool` 全局工具
   - 创建 obfuscar.xml 配置文件，混淆策略：
     - 公共API保留（LaunchWithProtection、CheckWithAutoDevice等），工具软件可正常调用
     - 私有方法/字段/属性/事件全部重命名为Unicode字符
     - 字符串隐藏（密钥、文件路径等不再明文可见）
     - 命名空间扁平化
     - 混淆后用key.snk重新签名，保持强名称有效
   - 输出：`bin\Obfuscated\Jones.Activation.dll`

3. **构建流程更新**

```
dotnet build -c Release          → 编译带签名的DLL
obfuscar.console obfuscar.xml    → 混淆+重签名
复制 bin\Obfuscated\Jones.Activation.dll → 工具软件\lib\
dotnet publish                   → 打包单文件exe
```

### 涉及文件

| 文件 | 变更 |
|------|------|
| key.snk | 新增，RSA 2048位强名称密钥 |
| AssemblyInfo.cs | 新增，手动管理程序集属性 |
| ActivationCodeVerifier.csproj | 添加SignAssembly、AssemblyOriginatorKeyFile、GenerateAssemblyInfo=false、排除tmp_keygen |
| obfuscar.xml | 新增，Obfuscar混淆配置 |

### 说明

- ConfuserEx不支持.NET 6程序集，改用Obfuscar替代（开源免费，支持.NET 6+）
- 强名称签名 + 代码混淆双重保护：签名防止DLL被替换，混淆防止逆向分析
- key.snk必须妥善保管，丢失后无法重新签名同名DLL

---

## 安全加固前后对比

| 安全措施 | V1 | V2 |
|---------|----|----|
| 公钥存储 | 单字节XOR 0x5A | 4字节循环XOR (0x3A,0x7C,0xE5,0x91) |
| 激活码文件 | 明文 activation.lic | AES-256-CBC加密 activation.dat |
| 文件绑定设备 | 不绑定，可复制 | PBKDF2(设备ID)派生密钥，不可跨设备 |
| 错误信息 | 返回详细原因 | 脱敏，不返回任何文字描述 |
| 过期退出 | 后台线程弹窗，主窗口不关闭 | UI线程模态弹窗，Application.Exit关闭 |
| 文件删除 | File.Delete | 覆写全零后删除 |
| 内存清理 | payload/signature清零 | 同V1 + 公钥encryptedKey清零 + AES密钥清零 |
| DLL签名 | 无强名称，可被替换 | RSA 2048位强名称签名，替换后加载失败 |
| 代码混淆 | 无混淆，IL可反编译 | Obfuscar混淆：私有成员Unicode重命名+字符串隐藏 |

---

## 待实施项

| 项目 | 优先级 | 说明 |
|------|--------|------|
| ~~ConfuserEx代码混淆~~ | ~~中~~ | 已用Obfuscar替代实现 |
| ~~DLL强名称签名~~ | ~~中~~ | 已实现 |
| 服务端Token机制 | 低 | 需客户端联网，当前场景为离线工具，暂不实施 |
| VMProtect虚拟化 | 低 | 商业工具，成本较高 |
