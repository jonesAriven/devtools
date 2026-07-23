# OmniFind Windows 部署完整流程

一次性把 OmniFind 装成"你的电脑上一个能用的本地搜索工具"。全程 6 步，都在 Windows 上做。

## 前置

- Windows 10/11 或 Server 2019+，NTFS 卷
- Python 3.11+ 已装（`python --version` 能看到 3.11.x 或更高）
- 已用 git 拉了本仓库到本地（`git pull` 能拿最新代码）
- **管理员 PowerShell**（右键 PowerShell → 以管理员身份运行）

## 步骤 1：拉最新代码

```powershell
cd D:\huliang\java\ideaworkspace\devtools    # 换成你实际的路径
git pull
cd omnifind
```

## 步骤 2：先跑 USN 后端独立验证（可选，验证过一次的话可跳）

管理员 PowerShell：

```powershell
python -m omnifind.layers.l1_filename.usn_backend --drive C: --limit 30
```

看到完整路径（`C:\Users\...\...`）+ 统计行"共扫描 XXX 条 / 耗时 X.XXs"就算通过。

## 步骤 3：打包成 exe

```powershell
powershell -ExecutionPolicy Bypass -File scripts\build-windows.ps1
```

流程：装 pyinstaller → 打包 `omnifind.exe`（用户态 Web+CLI）+ `omnifind-indexer.exe`（SYSTEM 索引服务）。

产物在 `dist\omnifind\`，整个目录复制/移动到你想放的地方，例如：

```powershell
Move-Item dist\omnifind D:\Tools\omnifind
```

## 步骤 4：写配置

创建 `C:\ProgramData\omnifind\config.yaml`（**这是 SYSTEM 服务和 Web 都读的官方共享位置**）：

```yaml
# ---- 索引数据存放位置（默认就是这里，一般不改）----
# data_dir: C:\ProgramData\omnifind

# ---- 要扫哪些盘 / 目录 ----
scan_roots:
  - C:\Users\13871\Documents
  - D:\huliang
  - E:\
# 或者留空 [] 让它自动扫全盘

# ---- L1 后端 ----
l1_backend: usn         # Windows 用 USN，Linux 用 walk，其他自动

# ---- L3 语义层重点目录（只对这些做向量化，防爆库）----
semantic_dirs:
  - C:\Users\13871\Documents
  - D:\huliang\java\ideaworkspace

# ---- Web 服务端口 ----
host: 127.0.0.1
port: 8899
```

保存文件后不用重启啥东西，服务启动时自动读。

PowerShell 一行创建：

```powershell
$dir = "$env:PROGRAMDATA\omnifind"
New-Item -ItemType Directory -Force -Path $dir | Out-Null
notepad "$dir\config.yaml"
```

## 步骤 5：装索引服务（SYSTEM 权限，开机自启）

```powershell
powershell -ExecutionPolicy Bypass -File D:\Tools\omnifind\scripts\install-service.ps1 `
    -ExePath D:\Tools\omnifind\omnifind-indexer.exe
```

（打包后 scripts 目录也在 dist 里，若在源码目录跑 `install-service.ps1` 会自动找 `dist\omnifind\omnifind-indexer.exe`，不用带参数）

装完服务名 `OmniFindIndexer`，自动启动，SYSTEM 权限运行。

**首次全量索引大约 15-30 秒**（如你 125 万条 13 秒的数据），查看进度：

```powershell
Get-Content "$env:PROGRAMDATA\omnifind\logs\indexer-service.log" -Tail 50 -Wait
```

## 步骤 6：启动 Web 服务（普通用户跑）

```powershell
D:\Tools\omnifind\omnifind.exe serve
```

浏览器打开 `http://127.0.0.1:8899`，输入关键字搜索。

**要让 Web 也开机自启？** 用 Windows 任务计划程序（普通用户即可，不用 SYSTEM）：

```powershell
$action = New-ScheduledTaskAction -Execute "D:\Tools\omnifind\omnifind.exe" -Argument "serve"
$trigger = New-ScheduledTaskTrigger -AtLogOn
Register-ScheduledTask -TaskName "OmniFindWeb" -Action $action -Trigger $trigger -RunLevel Limited
```

不装计划任务也没关系，Web 服务是无状态的，你想搜的时候手动开一下也行。

## 常见坑

- **打包报 "No module named jieba"**：`pip install jieba` 后重跑 build 脚本
- **服务启动后日志文件不生成**：说明 `%ProgramData%\omnifind\` 没建，手动 `mkdir "$env:PROGRAMDATA\omnifind\logs"` 一次
- **omnifind.exe serve 报端口占用**：改 `config.yaml` 的 `port` 或杀掉占用进程 `netstat -ano | findstr :8899`
- **首次 build 巨慢（分钟级）**：说明降级到了 walk 后端。检查服务日志，`l1_backend` 是不是 `usn`，Journal 有没有报错。用 `fsutil usn queryjournal C:` 确认 USN journal 是启用的（若报错用 `fsutil usn createjournal m=32m a=4m C:` 创建）

## 卸载

```powershell
# 卸载服务
powershell -ExecutionPolicy Bypass -File D:\Tools\omnifind\scripts\uninstall-service.ps1

# 删除计划任务（如果装了 Web 自启）
Unregister-ScheduledTask -TaskName "OmniFindWeb" -Confirm:$false

# 清理数据（可选）
Remove-Item -Recurse -Force "$env:PROGRAMDATA\omnifind"

# 删程序
Remove-Item -Recurse -Force D:\Tools\omnifind
```
