# OmniFind 索引服务卸载脚本
# 用法：powershell -ExecutionPolicy Bypass -File .\scripts\uninstall-service.ps1

param(
    [string]$ServiceName = "OmniFindIndexer"
)

$ErrorActionPreference = "Stop"

$isAdmin = ([Security.Principal.WindowsPrincipal] `
    [Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole(
    [Security.Principal.WindowsBuiltInRole] "Administrator")
if (-not $isAdmin) {
    Write-Host "❌ 需要管理员权限运行此脚本" -ForegroundColor Red
    exit 1
}

$svc = Get-Service -Name $ServiceName -ErrorAction SilentlyContinue
if (-not $svc) {
    Write-Host "服务 $ServiceName 不存在，无需卸载" -ForegroundColor Yellow
    exit 0
}

Write-Host "==> 停止服务" -ForegroundColor Cyan
Stop-Service -Name $ServiceName -Force -ErrorAction SilentlyContinue

Write-Host "==> 删除服务" -ForegroundColor Cyan
sc.exe delete $ServiceName | Out-Null

Start-Sleep -Seconds 2
$still = Get-Service -Name $ServiceName -ErrorAction SilentlyContinue
if ($still) {
    Write-Host "⚠️ 服务未删除，可能需要重启 Windows" -ForegroundColor Yellow
} else {
    Write-Host "==> 服务已卸载 ✅" -ForegroundColor Green
    Write-Host "   注：%ProgramData%\omnifind\ 索引数据未删除，若要清理请手动删除"
}
