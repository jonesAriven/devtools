# USN 后端 Windows 验证步骤

## 前置
- Windows 10/11 或 Server 2019+
- NTFS 卷（USN 是 NTFS 独有特性，FAT/exFAT 没有）
- Python 3.11+
- **管理员权限**（首次验证）或 **SYSTEM 权限**（服务化后）

## 一、拉最新代码

```powershell
# 假设 devtools 已经通过 SMB 或 git 同步到 D:\devtools
cd D:\devtools\omnifind
git pull   # 或从 SMB 复制最新的 usn_backend.py
```

如果没有 git，最简单：从 mykng 直接复制单个文件
```
\\192.168.31.105\ideaworkspace\... （若 SMB 通）
或者 scp:
scp -P 3385 root@120.26.66.182:/root/devtools/omnifind/omnifind/layers/l1_filename/usn_backend.py D:\devtools\omnifind\omnifind\layers\l1_filename\
```

## 二、单文件独立验证（不装 OmniFind 全部依赖）

USN 后端**零第三方依赖**，只用标准库 ctypes，装完 Python 就能跑：

```powershell
cd D:\devtools\omnifind
# 以管理员身份打开 PowerShell（右键 → 以管理员身份运行）

# 1. 打印 C 盘前 100 个文件
python -m omnifind.layers.l1_filename.usn_backend --drive C: --limit 100

# 2. 只数总数不打印（衡量速率）
python -m omnifind.layers.l1_filename.usn_backend --drive C: --count-only

# 3. 只看某目录下的（例如只看用户目录）
python -m omnifind.layers.l1_filename.usn_backend --drive C: --root "C:\Users\administrator" --limit 50
```

## 三、验证成功标准

- **能打印真实文件路径**（不是报错 `Access Denied` / 错误码 5）
- **速率 ≥ 50 万条/秒**（C 盘 100 万文件应该 2-3 秒内完成）
- **统计行显示** `共扫描 N 条（X 文件 / Y 目录），耗时 Z.ZZs`

## 四、常见报错

- **错误码 5 (ERROR_ACCESS_DENIED)**：PowerShell 没有以管理员身份运行。右键 PowerShell 图标 → 以管理员身份运行，重跑。
- **错误码 87 (ERROR_INVALID_PARAMETER)**：`--drive` 参数错误，用 `C:` 或 `C` 都行，别加 `\`
- **错误码 1179 (ERROR_JOURNAL_NOT_ACTIVE)**：目标卷未启用 USN journal。修复：`fsutil usn createjournal m=32m a=4m C:`（管理员执行）
- **`FSCTL_ENUM_USN_DATA 失败，错误码 38`**：这不是错误，是枚举正常结束的标记，代码已经吞掉了

## 五、报什么给我

跑完把这几行贴给我：
1. 最后的统计行（共扫描 X 条 / 耗时 Z 秒 / 速率）
2. 前 5 行文件路径示例（看看中文/长路径有没有乱码）
3. 有任何报错就贴完整错误

我根据结果决定：
- 通过 → 继续做增量 watch + PyInstaller 打包
- 不通过 → 修 bug 再来一轮

## 六、如果 SSH 有卷权限（想跑远程验证）

memory 里的踩坑记录说 OpenSSH 服务权限不够，要改 services.msc 让 sshd 用"本地系统账户"跑。**这一步你自己做**：
1. Win + R → `services.msc`
2. 找 `OpenSSH SSH Server` → 右键属性 → 登录 tab
3. 选"本地系统账户" + 勾"允许服务与桌面交互"
4. 确定 → 右键服务 → 重启

改完后，即使我远程 SSH 过去跑，也会拿到 SYSTEM 权限，就能读 USN 了。

**但你不做也没关系**——你在本机管理员 PowerShell 里跑一次也是一样的效果，不必开这个权限缺口。
