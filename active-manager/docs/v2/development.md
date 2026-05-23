# 激活码管理系统 V2 - 开发文档

## 1. 工程结构

```
active-manager/
├── activation-code-server/              # 激活码服务端
│   ├── src/main/java/com/jones/activation/
│   │   ├── ActivationCodeServerApplication.java
│   │   ├── controller/
│   │   │   ├── ActivationController.java    # 7个REST端点
│   │   │   └── AuthController.java          # 登录/登出/会话/改密码
│   │   ├── service/
│   │   │   └── ActivationService.java       # 生成/验证/查询/日志/解析
│   │   ├── mapper/
│   │   │   ├── ActivationRecordMapper.java
│   │   │   ├── ActivationLogMapper.java
│   │   │   └── AdminUserMapper.java
│   │   ├── entity/
│   │   │   ├── ActivationRecord.java        # 11个字段
│   │   │   ├── ActivationLog.java           # 8个字段
│   │   │   └── AdminUser.java               # 管理员用户
│   │   ├── dto/
│   │   │   ├── GenerateRequest.java         # serialNumber, deviceId, expireMinutes
│   │   │   ├── GenerateResponse.java        # 含initialSerial, machineCode
│   │   │   ├── VerifyRequest.java
│   │   │   └── VerifyResponse.java
│   │   ├── util/
│   │   │   └── CryptoUtil.java              # RSA签名/验签/序列号解密
│   │   └── config/
│   │       ├── RsaKeyConfig.java            # 密钥加载
│   │       ├── AuthInterceptor.java         # 认证拦截器
│   │       └── WebMvcConfig.java            # 静态资源不缓存+拦截器注册
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   ├── schema.sql
│   │   └── static/
│   │       └── activecode/
│   │           ├── main.html            # 管理后台（4标签）
│   │           ├── login.html           # 登录页面
│   │           └── index.html           # 独立生成页面
│   ├── rsa_keys/
│   │   ├── private_key.pem
│   │   └── public_key.pem
│   └── pom.xml
│
├── activation-code-verifier/            # 激活码验证工具
│   ├── ActivationGuard.cs               # 核心入口+弹窗+定时检查
│   ├── ActivationVerifier.cs            # RSA验证+反调试
│   ├── VerifyResult.cs                  # 验证结果（脱敏）
│   ├── DeviceInfo.cs                    # 设备指纹+序列号加密
│   ├── SecureStorage.cs                 # AES加密存储
│   ├── TimeGuard.cs                     # 时间篡改防护
│   ├── AntiDebug.cs                     # 反调试检测
│   └── ActivationCodeVerifier.csproj    # net6.0-windows类库
│
└── docs/                                # 文档
    ├── v1/                              # 旧版文档
    └── v2/                              # 当前文档
```

---

## 2. 服务端开发

### 2.1 环境要求

| 环境 | 版本 | 路径 |
|------|------|------|
| JDK | 21 | 系统默认 |
| Maven | 3.x | 系统默认 |
| MySQL | 8.0+ | 192.168.31.182:3306 |

### 2.2 数据库初始化

```sql
-- 数据库已存在时跳过
CREATE DATABASE IF NOT EXISTS tools CHARACTER SET utf8mb4;

-- 表结构见 schema.sql，主要两张表：
-- activation_record: 激活码记录
-- activation_log: 稽核日志
```

注意：`spring.sql.init.mode=never`，不会自动执行schema.sql，需手动建表。

### 2.3 启动服务端

```bash
cd activation-code-server
mvn spring-boot:run
```

服务启动在 http://localhost:8080

### 2.4 关键开发说明

#### 序列号解密逻辑（CryptoUtil）

服务端接收加密序列号后，解密流程：
1. Base64解码
2. 逐字节XOR 0x5A
3. 按 `|` 分割得到 [初始序列号, 设备ID, 机器码]
4. 组装serialNumber = 初始序列号-机器码

此解密逻辑必须与verifier的`DeviceInfo.GetSerialNumber()`加密逻辑保持一致。

#### 有效期单位

有效期单位为**分钟**（不是天），默认525600分钟（=365天）。

---

## 3. 验证工具开发

### 3.1 环境要求

| 环境 | 版本 |
|------|------|
| .NET SDK | 6.0+ |
| 目标框架 | net6.0-windows |

### 3.2 编译DLL

```bash
cd activation-code-verifier
dotnet build -c Release
```

输出：`bin/Release/net6.0-windows/Jones.Activation.dll`

### 3.3 源文件说明

| 文件 | 可见性 | 职责 |
|------|--------|------|
| ActivationGuard.cs | public | 核心入口，一站式API，弹窗，定时检查 |
| ActivationVerifier.cs | public | RSA签名验证 |
| VerifyResult.cs | public | 验证结果（脱敏，无Message字段） |
| DeviceInfo.cs | public | 设备指纹采集，序列号加密/解析 |
| SecureStorage.cs | internal | AES加密存储激活码文件 |
| TimeGuard.cs | internal | 单调时钟防时间篡改 |
| AntiDebug.cs | internal | 反调试检测 |

### 3.4 公钥更新流程

当服务端重新生成RSA密钥对时，需要同步更新verifier中的公钥：

1. 读取新的 `public_key.pem`
2. 用Python脚本生成分段XOR加密的字节数组：
   ```python
   keys = [0x3A, 0x7C, 0xE5, 0x91]
   encrypted = [b ^ keys[i % 4] for i, b in enumerate(pem_bytes)]
   ```
3. 替换 `ActivationGuard.cs` 中的 `_encryptedKey` 数组
4. 重新编译DLL

### 3.5 安全加固要点

| 加固项 | 实现方式 | 文件 |
|--------|---------|------|
| 公钥加密 | 4字节循环XOR，使用后清零 | ActivationGuard.cs |
| 激活码加密存储 | AES-256-CBC，PBKDF2派生密钥 | SecureStorage.cs |
| 反调试 | 3种检测方式，静默失败 | AntiDebug.cs, ActivationVerifier.cs |
| 时间篡改 | 单调时钟+缓存文件 | TimeGuard.cs |
| 错误脱敏 | 移除Message字段 | VerifyResult.cs |
| 内存清理 | Array.Clear清零敏感数据 | ActivationVerifier.cs, SecureStorage.cs |

---

## 4. 工具软件接入开发

### 4.1 接入步骤（C#项目）

**步骤1**：复制DLL

将 `Jones.Activation.dll` 复制到工具软件项目的 `lib/` 目录。

**步骤2**：修改csproj，添加引用

```xml
<ItemGroup>
  <Reference Include="Jones.Activation">
    <HintPath>lib\Jones.Activation.dll</HintPath>
    <Private>true</Private>
  </Reference>
</ItemGroup>

<ItemGroup>
  <PackageReference Include="System.Management" Version="8.0.0" />
</ItemGroup>
```

**步骤3**：修改Program.cs

```csharp
using System.Windows.Forms;
using Jones.Activation;

namespace YourTool;

static class Program
{
    [STAThread]
    static void Main()
    {
        ApplicationConfiguration.Initialize();

        // 一行代码接入激活验证
        if (!ActivationGuard.LaunchWithProtection("YOURTOOL"))
            return;

        Application.Run(new Form1());  // 业务代码完全不动
        ActivationGuard.StopPeriodicCheck();
    }
}
```

**步骤4**：发布

```bash
dotnet publish -c Release -r win-x64 --self-contained true \
  -p:PublishSingleFile=true \
  -p:IncludeNativeLibrariesForSelfExtract=true \
  -o publish
```

### 4.2 接入要点

| 要点 | 说明 |
|------|------|
| 初始序列号 | 每个工具软件定义自己的标识，如"QRTOOL"、"TOOLB" |
| 业务代码不动 | Form1.cs等业务代码不需要任何修改 |
| activation.dat | 激活码加密存储文件，自动生成在exe同目录 |
| activation_cache/ | 时间篡改防护缓存目录，自动生成 |
| 旧版activation.lic | V2改用activation.dat，旧文件需手动删除 |

### 4.3 QRCodeTool接入示例

```
QRCodeTool/
├── lib/
│   └── Jones.Activation.dll     # 验证工具DLL
├── Program.cs                    # 仅21行，调用LaunchWithProtection
├── Form1.cs                      # 业务代码，完全不动
├── QRCodeTool.csproj             # 添加DLL引用和System.Management包
└── publish/
    └── QRCodeTool.exe            # 单文件发布产物
```

---

## 5. 打包发布流程

### 5.1 完整发布流程

```
1. 编译verifier DLL
   cd activation-code-verifier
   dotnet build -c Release

2. 复制DLL到工具软件
   copy bin\Release\net6.0-windows\Jones.Activation.dll → 工具软件\lib\

3. 编译并发布工具软件
   cd 工具软件目录
   dotnet publish -c Release -r win-x64 --self-contained true \
     -p:PublishSingleFile=true -o publish

4. 启动服务端
   cd activation-code-server
   mvn spring-boot:run
```

### 5.2 发布产物

| 产物 | 路径 | 说明 |
|------|------|------|
| Jones.Activation.dll | activation-code-verifier/bin/Release/net6.0-windows/ | 验证工具DLL |
| QRCodeTool.exe | QR_GENERATORBYC#V1/PublishSingleFile/publish/ | 单文件exe |
| 服务端JAR | activation-code-server/target/ | Spring Boot JAR |

---

## 6. 常见开发问题

### 6.1 JDK版本

必须使用JDK 21，JDK 25会导致MySQL连接问题。

### 6.2 静态资源缓存

开发时浏览器可能缓存旧版HTML，需Ctrl+Shift+R强制刷新。生产环境已配置WebMvcConfig禁用缓存。

### 6.3 QRCodeTool.exe被占用

发布时如果exe正在运行，会报Access Denied。需先关闭exe再发布，或用`Get-Process QRCodeTool | Stop-Process -Force`强制关闭。

### 6.4 序列号加密一致性

verifier的XOR 0x5A加密必须与服务端CryptoUtil的解密逻辑一致。如果修改了加密方式，两端需同步更新。

### 6.5 net6.0-windows目标框架

verifier使用`net6.0-windows`（因为WinForms弹窗），工具软件也必须是`net6.0-windows`。虽然.NET 6已EOL，但当前系统SDK兼容。
