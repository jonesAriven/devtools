# OmniFind 索引服务安装脚本
# 作用：把 omnifind-indexer.exe 注册为 Windows 服务，用 SYSTEM 账户跑
# 需要：管理员 PowerShell 运行
#
# 用法：
#   powershell -ExecutionPolicy Bypass -File .\scripts\install-service.ps1
#   powershell -ExecutionPolicy Bypass -File .\scripts\install-service.ps1 -ExePath "D:\Tools\omnifind\omnifind-indexer.exe"

param(
    [string]$ExePath = "",
    [string]$ServiceName = "OmniFindIndexer",
    [string]$DisplayName = "OmniFind Index Service"
)

$ErrorActionPreference = "Stop"

# 检查管理员权限
$isAdmin = ([Security.Principal.WindowsPrincipal] `
    [Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole(
    [Security.Principal.WindowsBuiltInRole] "Administrator")
if (-not $isAdmin) {
    Write-Host "❌ 需要管理员权限运行此脚本" -ForegroundColor Red
    Write-Host "   右键 PowerShell → 以管理员身份运行"
    exit 1
}

# 自动推断 exe 路径
if (-not $ExePath) {
    $script_root = Split-Path -Parent $PSScriptRoot
    $candidate1 = Join-Path $script_root "dist\omnifind\omnifind-indexer.exe"
    $candidate2 = Join-Path $script_root "dist\omnifind-indexer\omnifind-indexer.exe"
    if (Test-Path $candidate1)   { $ExePath = $candidate1 }
    elseif (Test-Path $candidate2) { $ExePath = $candidate2 }
    else {
        Write-Host "❌ 找不到 omnifind-indexer.exe，请用 -ExePath 显式指定绝对路径" -ForegroundColor Red
        exit 1
    }
}
Write-Host "==> 使用 exe: $ExePath" -ForegroundColor Cyan

# 若服务已存在，先卸载
$existing = Get-Service -Name $ServiceName -ErrorAction SilentlyContinue
if ($existing) {
    Write-Host "==> 服务已存在，先卸载" -ForegroundColor Yellow
    Stop-Service -Name $ServiceName -Force -ErrorAction SilentlyContinue
    sc.exe delete $ServiceName | Out-Null
    Start-Sleep -Seconds 2
}

# 注册服务，账户 LocalSystem = SYSTEM（拥有读 USN 的 SeManageVolumePrivilege）
Write-Host "==> 注册 Windows 服务" -ForegroundColor Cyan
sc.exe create $ServiceName `
    binPath= "`"$ExePath`"" `
    DisplayName= "$DisplayName" `
    start= auto `
    obj= LocalSystem | Out-Null

sc.exe description $ServiceName "OmniFind 本地全能搜索的索引后台，读 NTFS USN 日志建 L1 文件名索引，需 SYSTEM 权限。" | Out-Null

# 配置失败恢复：崩溃后自动重启
sc.exe failure $ServiceName reset= 86400 actions= restart/5000/restart/5000/restart/10000 | Out-Null

# 启动服务
Write-Host "==> 启动服务" -ForegroundColor Cyan
Start-Service -Name $ServiceName
Start-Sleep -Seconds 3
Get-Service -Name $ServiceName | Format-Table Status, Name, DisplayName

Write-Host ""
Write-Host "==> 服务已安装并启动 ✅" -ForegroundColor Green
Write-Host ""
Write-Host "常用命令："
Write-Host "  查看状态： Get-Service -Name $ServiceName"
Write-Host "  停止服务： Stop-Service -Name $ServiceName"
Write-Host "  启动服务： Start-Service -Name $ServiceName"
Write-Host "  查看日志： Get-Content `"$env:PROGRAMDATA\omnifind\logs\indexer-service.log`" -Tail 50 -Wait"
Write-Host "  卸载服务： powershell -ExecutionPolicy Bypass -File scripts\uninstall-service.ps1"
