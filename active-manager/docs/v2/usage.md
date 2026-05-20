# 激活码管理系统 V2 - 使用文档

## 1. 管理员使用指南

### 1.1 启动服务端

```bash
cd activation-code-server
mvn spring-boot:run
```

启动后访问：
- 管理后台：http://localhost:8080/activecode/main.html（会自动跳转到登录页）
- 登录页：http://localhost:8080/activecode/login.html
- 独立生成页面：http://localhost:8080/activecode/index.html

**默认管理员账号**：`admin` / `admin123`（首次启动时自动初始化，请及时修改密码）

### 1.2 登录

访问管理后台会自动跳转到登录页，输入用户名和密码登录。登录后可点击右上角"退出登录"退出。

### 1.3 管理后台（http://localhost:8080/activecode/main.html）

管理后台包含四个标签页：

#### 标签1：激活码管理工具

**生成激活码**：
1. 终端用户提供"唯一序列号"（加密字符串）
2. 管理员粘贴到"唯一序列号"输入框
3. 服务端自动解密出设备ID和机器码
4. 选择有效期（快捷按钮：5分钟/1小时/1天/30天/1年，也可手动输入分钟数）
5. 点击"生成激活码"
6. 将生成的激活码复制给终端用户

**验证激活码**：
1. 输入激活码
2. 可选输入设备ID
3. 点击"验证"查看结果

#### 标签2：激活码记录

- 查看所有激活码记录
- 支持按关键字搜索（序列号/设备ID/初始序列号/机器码）
- 支持按状态筛选（全部/有效/已过期）
- 统计卡片显示总数、有效数、已过期数
- 支持复制激活码、查看日志、删除操作

#### 标签3：操作日志

- 查看所有稽核日志
- 支持按序列号和事件类型筛选
- 事件类型带彩色徽章区分：
  - GENERATE（生成成功）
  - GENERATE_DUPLICATE（重复生成拒绝）
  - VERIFY_SUCCESS（验证成功）
  - VERIFY_FAIL（验证失败）
  - EXPIRED（已过期）
  - DEVICE_MISMATCH（设备不匹配）

#### 标签4：解析工具

- 解析激活码：输入激活码，查看序列号、设备ID、过期时间、初始序列号、机器码
- 解析序列号：输入序列号，查看初始序列号和机器码，关联数据库显示记录详情

### 1.4 独立生成页面（http://localhost:8080/activecode/index.html）

简洁的单卡片页面，仅提供生成激活码功能：
- 使用步骤说明（3步流程）
- 有效期最长30天（快捷按钮：5分钟/1小时/1天/30天）
- 适合日常快速生成场景

---

## 2. 终端用户使用指南

### 2.1 首次激活

1. 双击打开工具软件（如QRCodeTool.exe）
2. 弹出"软件激活"对话框
3. 点击"复制序列号"按钮，将唯一序列号发给管理员
4. 从管理员处获取激活码
5. 将激活码粘贴到输入框
6. 点击"激活"
7. 激活成功后软件正常使用

### 2.2 日常使用

- 已激活的软件直接打开即可使用，无需再次输入激活码
- 激活码已加密保存在本地 `activation.dat` 文件中
- 运行期间每60秒自动检查有效期

### 2.3 激活码过期

- 过期后弹窗提示"授权已失效，程序即将退出"
- 需要重新联系管理员获取新的激活码

### 2.4 设备更换

- 激活码绑定设备，换电脑后需要重新获取激活码
- 新设备会生成新的唯一序列号

---

## 3. 开发者接入指南

### 3.1 C#项目接入（3步）

**步骤1**：复制 `Jones.Activation.dll` 到项目 `lib/` 目录

**步骤2**：修改csproj添加引用

```xml
<Reference Include="Jones.Activation">
  <HintPath>lib\Jones.Activation.dll</HintPath>
  <Private>true</Private>
</Reference>
<PackageReference Include="System.Management" Version="8.0.0" />
```

**步骤3**：修改Program.cs

```csharp
using System.Windows.Forms;
using Jones.Activation;

static class Program
{
    [STAThread]
    static void Main()
    {
        ApplicationConfiguration.Initialize();

        if (!ActivationGuard.LaunchWithProtection("YOURTOOL"))
            return;

        Application.Run(new Form1());
        ActivationGuard.StopPeriodicCheck();
    }
}
```

### 3.2 API参考

#### 一站式接入（推荐）

```csharp
// 一行代码完成：加载缓存→验证→弹窗→定时检查
bool ok = ActivationGuard.LaunchWithProtection("初始序列号");
```

#### 分步接入

```csharp
// 1. 验证激活码（自动绑定设备）
VerifyResult result = ActivationGuard.CheckWithAutoDevice("激活码");

// 2. 启动定时检查
ActivationGuard.StartPeriodicCheckWithAutoDevice("激活码", 60000, msg => {
    // 过期回调
});

// 3. 停止定时检查
ActivationGuard.StopPeriodicCheck();
```

#### 严格保护模式

```csharp
// 验证失败直接退出进程（退出码1001）
ActivationGuard.ProtectWithAutoDevice("激活码");
```

#### 辅助API

```csharp
string serial = DeviceInfo.GetSerialNumber("QRTOOL");  // 生成唯一序列号
string deviceId = DeviceInfo.GetDeviceId();              // 获取设备ID
string machineCode = DeviceInfo.GetMachineCode();        // 获取机器码
var info = DeviceInfo.ParseSerialNumber("加密序列号");    // 解析序列号
```

### 3.3 VerifyResult字段

| 字段 | 类型 | 说明 |
|------|------|------|
| Success | bool | 验证是否成功 |
| SerialNumber | string | 激活码中的序列号 |
| DeviceId | string | 激活码中的设备ID |
| ExpireTimestamp | long | 过期时间戳（毫秒） |
| Expired | bool | 是否已过期 |
| DeviceMismatch | bool | 是否设备不匹配 |

注意：V2版本移除了Message字段，不返回详细错误描述。

---

## 4. 文件说明

### 4.1 运行时生成的文件

| 文件/目录 | 位置 | 说明 |
|----------|------|------|
| activation.dat | exe同目录 | AES加密的激活码文件 |
| activation_cache/ | exe同目录 | 时间篡改防护缓存目录 |

### 4.2 文件安全

| 文件 | 加密方式 | 能否复制到其他电脑 |
|------|---------|------------------|
| activation.dat | AES-256-CBC，密钥由设备ID派生 | 不能，不同设备无法解密 |
| activation_cache/*.dat | JSON格式，记录单调时钟 | 复制无效，设备ID不同 |

---

## 5. 退出码说明

| 退出码 | 含义 |
|--------|------|
| 0 | 正常退出 |
| 1001 | 激活码验证失败（Protect模式） |
| 1002 | 运行时检测到过期（定时检查） |

---

## 6. 常见问题

### Q1：打开软件直接闪退

可能原因：
- 激活码已过期，弹窗后自动退出
- 激活码文件损坏，删除`activation.dat`重新激活

### Q2：提示"设备不匹配"

激活码绑定了其他设备，需要在本机重新获取激活码。每台电脑的设备ID不同。

### Q3：修改系统时间能否延长有效期

不能。TimeGuard使用单调时钟检测时间篡改，将系统时间往回拨会被检测到。

### Q4：复制activation.dat到其他电脑能用吗

不能。activation.dat使用AES加密，密钥由设备ID派生，不同设备无法解密。

### Q5：如何查看本机的唯一序列号

打开工具软件，激活弹窗中会自动显示唯一序列号，可复制。

### Q6：管理后台页面显示不正常

按 Ctrl+Shift+R 强制刷新浏览器缓存。

### Q7：服务端启动报错Whitelabel Error Page

检查数据库连接配置，确认MySQL服务正常运行，数据库`tools`已创建。

---

## 7. 安全注意事项

1. **私钥保护**：`private_key.pem`必须严格保密，禁止提交到版本控制
2. **数据库密码**：生产环境应通过环境变量管理，不应硬编码
3. **传输安全**：生产环境应配置HTTPS
4. **密钥轮换**：更换RSA密钥对时，需同步更新verifier中的公钥并重新编译DLL
5. **DLL保护**：建议使用ConfuserEx对Jones.Activation.dll进行代码混淆（待实施）
6. **强名称签名**：建议对DLL进行强名称签名，防止被替换（待实施）
