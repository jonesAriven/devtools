# active-manager 常见问题与解决方案速查手册

本文档聚合了激活码管理系统开发过程中遇到的所有问题、根因分析和解决方案，按问题类型分类索引。

---

## 一、Win7 兼容性问题（最高优先级）

### 1.1 std::chrono 时间戳在 Win7 上崩溃

**问题现象**：C++ 客户端在 Win7 上启动崩溃，调试发现 `std::chrono::system_clock::now()` 调用失败

**根本原因**：
- MSVC 的 `std::chrono::system_clock` 在 Win7 上依赖 `GetSystemTimePreciseAsFileTime` API
- 该 API 是 Win8 新增，Win7 不存在，导致运行时找不到入口点崩溃

**解决方案**（提交 `29e1fa3`）：
- 替换 `std::chrono::system_clock` 为 Win32 API `GetLocalTime` + `SystemTimeToFileTime`
- 纯 Win32 API 在所有 Windows 版本都可用

---

### 1.2 未声明版本宏导致隐式链接 Win8+ API

**问题现象**：exe 在 Win7 上启动失败，提示「不是有效的 Win32 应用程序」

**根本原因**：
- 未显式声明 `_WIN32_WINNT` 版本宏时，编译器默认目标是 Win8+
- 链接器会隐式链接 Win8 新增的 API，Win7 上找不到入口

**解决方案**（提交 `18c0fd9` / `c2e060c`）：
- CMakeLists.txt 全局声明：
  ```cmake
  add_compile_definitions(_WIN32_WINNT=0x0601 WINVER=0x0601 NTDDI_VERSION=0x06010000)
  ```
- 强制所有编译单元最低支持 Win7（0x0601 = Windows 7）

---

## 二、序列号解析与协议问题

### 2.1 序列号分隔符冲突

**问题现象**：C++ 客户端生成的序列号用 `|` 分隔，但服务端解析时与 payload 的 `|` 冲突，导致解析失败

**根本原因**：
- 唯一序列号格式：`初始序列号|设备ID|机器码|版本号`
- 激活码 payload 本身也用 `|` 分隔，混淆了解析逻辑

**解决方案**（提交 `d03a2e7` / `823fc76`）：
- 客户端序列号分隔符从 `|` 改为 `:`
- 服务端同步修改解析逻辑，用 `:` 拆分序列号

---

### 2.2 null deviceId 导致 payload 段数不足

**问题现象**：生成激活码时 deviceId 为 null，只生成 2 段 payload，客户端解析失败（期望固定 3 段）

**根本原因**：
- payload 格式：`expiryTime|deviceId|machineCode`
- deviceId 为 null 时变成 `expiryTime|machineCode` 只有 2 段

**解决方案**（提交 `e4f4f78` / `c2e06af`）：
- 生成激活码时固定 3 段 payload
- null/空 deviceId 用空字符串占位，确保段数一致

---

## 三、时间戳与时区问题

### 3.1 时区偏移导致过期误判

**问题现象**：不同时区的客户端激活码过期时间计算错误，提前或延后几小时过期

**根本原因**：
- 服务端用本地时间生成时间戳，客户端用 UTC 时间验证
- 时区差导致计算出的过期时间偏移

**解决方案**（提交 `d60dbc8`）：
- 统一用 UTC 时间：`GetSystemTimeAsFileTime` 获取 UTC 时间
- 服务端和客户端都用 UTC 时间计算过期时间，消除时区差影响

---

## 四、前端 UI 缺陷

### 4.1 操作日志页序列号丢失

**问题现象**：操作日志页序列号列显示为空，后端接口返回的序列号字段丢失

**解决方案**（提交 `d9c0303`）：
- 修复后端接口，确保序列号字段正确返回
- 同步修复前端 UI 显示缺陷

---

### 4.2 日志页缺少每页条数选择器

**问题现象**：操作日志页没有每页条数选择器，与激活码记录页 UI 不一致

**解决方案**（提交 `cd90e37`）：
- 操作日志页补齐每页条数选择器，与记录页保持一致

---

### 4.3 错误信息不匹配错误码

**问题现象**：前端显示的错误信息与实际错误码不匹配，误导用户

**解决方案**（提交 `ee5b3aa`）：
- 统一错误码与错误信息映射关系
- 确保前端根据错误码显示对应提示

---

## 五、安全与加密问题

### 5.1 无效序列号被接受

**问题现象**：格式无效的序列号被服务端接受，没有做格式校验

**解决方案**（提交 `dd3ed1a`）：
- 增加序列号格式校验逻辑
- 拒绝无效格式的序列号请求

---

### 5.2 BCrypt API 参数传递错误

**问题现象**：`BCryptEncrypt` / `BCryptDecrypt` 调用失败，返回无效参数错误

**根本原因**：
- `BCRYPT_BLOCK_PADDING` 标志应作为 `dwFlags`（最后一个参数）传递，而不是作为 `pPaddingInfo`（第5个参数）指针传递
- `BCRYPT_BLOCK_PADDING` 是一个标志值（0x00000001），不是结构体指针

**正确用法**：
```cpp
// ❌ 错误：paddingInfo 作为指针传递
BCryptEncrypt(hKey, data, dataLen, NULL, iv, 16, out, outLen, &outLen, &paddingInfo);

// ✅ 正确：padding 作为 flag 传递
BCryptEncrypt(hKey, data, dataLen, NULL, iv, 16, out, outLen, &outLen, BCRYPT_BLOCK_PADDING);
```

**教训**：BCrypt 系列 API 的 padding 参数设计容易混淆。对称加密（AES-CBC）用 `BCRYPT_BLOCK_PADDING` 作为 flag，而非认证加密（AES-GCM）才需要传 `BCRYPT_AUTHENTICATED_CIPHER_MODE_INFO` 结构体作为 paddingInfo。

---

## 六、数据库变更问题

### 6.1 ALTER TABLE ADD COLUMN IF NOT EXISTS 在 MySQL 上静默失败

**问题现象**：新增列的 SQL 执行后列不存在，无报错，静默失败

**根本原因**：
- `ALTER TABLE ADD COLUMN IF NOT EXISTS` 是 MariaDB 特有语法，MySQL 不支持
- MySQL 遇到不认识的语法直接忽略，不报错

**解决方案**：
- 新增列：先查询 `information_schema.COLUMNS` 判断列是否存在，不存在才 `ALTER TABLE ADD COLUMN`
- 新增索引：先查询 `information_schema.STATISTICS` 判断索引是否存在，不存在才 `ALTER TABLE ADD INDEX`

```java
// 伪代码示例
boolean exists = jdbc.queryForObject(
    "SELECT COUNT(*) FROM information_schema.COLUMNS " +
    "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? AND COLUMN_NAME = ?",
    Integer.class, dbName, tableName, columnName) > 0;

if (!exists) {
    jdbc.execute("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " ...");
}
```

---

### 5.3 RSA 密钥对不匹配（激活码永远验证失败）

**问题现象**：
- 服务端生成的激活码，客户端验证永远返回失败
- 前端显示「激活码无效」或「签名验证失败」
- 服务端日志无报错，客户端激活失败

**根本原因**：
- **服务端生成激活码的私钥** 与 **客户端验证签名的公钥** 不是一对
- 密钥轮换后，客户端内嵌的公钥没有同步更新
- 测试环境和生产环境用了不同的密钥对

**解决方案**：
1. **确认密钥对一致性**：
   - 服务端私钥位置：`activation-code-server/src/main/resources/rsa-private.pem`
   - 客户端公钥位置：`activation-code-verifier/cpp/include/Jones/RsaKey.h`
   - 用 `openssl rsa -in private.pem -pubout` 导出公钥，与客户端硬编码的公钥对比

2. **密钥轮换流程**：
   - 先生成新的密钥对，同时保留旧密钥 30 天用于过渡
   - 先更新客户端内嵌的公钥，发布新版客户端
   - 再更新服务端的私钥，生成新格式的激活码
   - 过渡期允许旧格式激活码继续有效

3. **快速验证命令**：
   ```bash
   # 从私钥导出公钥
   openssl rsa -in rsa-private.pem -pubout -out derived-public.pem
   
   # 对比客户端内嵌的公钥
   diff derived-public.pem client-public.pem
   ```

---

## 七、架构与运维决策

### 7.1 容器 restart 策略设计

**设计决策**：active-manager docker-compose 用 `restart: on-failure:5`，**不是** `restart: always`

**理由**：
- 若容器 crash 循环重启会把宿主机 CPU 打满
- 挂掉的正确处理路径：
  1. 排查 crash 根因（logs/inspect ExitCode）
  2. 修复根因
  3. 走 Woodpecker 流水线重新部署
- **禁止**：私自改成 `restart: always` 掩盖问题
- **禁止**：绕过流水线手工 docker run

---

## 八、开发规范与禁止事项

1. **禁止修改已上线的协议格式**：M5: 多页协议、B5: 压缩前缀，修改会导致版本不兼容
2. **禁止换回 quirc 解码库**：zxing-cpp 识别率远高于 quirc（>95% vs ~60%）
3. **禁止修改 CRT 链接方式**：保持 `/MT` 静态链接，确保零运行时依赖
4. **所有改动必须走 Woodpecker 流水线部署**，禁止手工 docker run 覆盖部署
5. Win7 兼容性是硬性要求，所有 C++ 代码必须在 Win7 上测试通过才能合并
6. **数据库变更必须先查 information_schema**，禁止使用 MariaDB 特有语法

---

## 九、多页二维码协议参考

### 多页二维码分页协议

**协议格式**：`M5:<页码>/<总页数>/<内容>`
- 例：`M5:1/3/...` 表示第 1 页，共 3 页

**注意事项**：
- 前缀格式不可随意修改，必须与扫描端保持一致
- Brotli+Base45 压缩算法与 C# 版一致，改换会导致跨版本不兼容
- 纯文本 QR 码任何扫码器都能识别

---

*文档索引：按问题类型分类，共 9 大类，16 个常见问题*
*最后更新：2026-07-31*
