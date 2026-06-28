# ============================================================
# mykng 知识库微服务状态查看脚本（Windows PowerShell 版本）
# ============================================================
# 用法:
#   .\scripts\status.ps1                  # 查看完整状态
#   .\scripts\status.ps1 -Help            # 显示帮助
# ============================================================

param([switch]$Help)

$ErrorActionPreference = "Continue"

# ---------- 路径变量化 ----------
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Split-Path -Parent $ScriptDir
$ComposeProject = "kb-deploy"
$ComposeFile = Join-Path $ProjectRoot "docker-compose.yml"

Set-Location $ProjectRoot

function Write-Info { param([string]$Msg) Write-Host "[INFO ] $Msg" -ForegroundColor Cyan }
function Write-Ok   { param([string]$Msg) Write-Host "  [OK] $Msg" -ForegroundColor Green }
function Write-Warn { param([string]$Msg) Write-Host "  [!] $Msg" -ForegroundColor Yellow }
function Write-Bad  { param([string]$Msg) Write-Host "  [X] $Msg" -ForegroundColor Red }

if ($Help) {
    Write-Host @"
mykng 知识库微服务状态查看脚本 (PowerShell)

用法:
  .\scripts\status.ps1

输出:
  1. Docker 容器状态
  2. 宿主机端口监听（3306/6379/27017/9000/9001/7700/8090）
  3. 磁盘空间使用
  4. 系统内存使用
  5. Docker 容器资源占用
"@
    exit 0
}

Write-Host "============================================================"
Write-Host "  mykng 知识库微服务状态"
Write-Host "  时间: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
Write-Host "  项目: $ComposeProject"
Write-Host "============================================================"
Write-Host ""

# 1. 容器状态
Write-Info "=== 1. Docker 容器状态 ==="
$psOutput = docker compose -p $ComposeProject -f $ComposeFile ps --format "table {{.Name}}`t{{.Service}}`t{{.Status}}`t{{.Ports}}" 2>$null
if ($psOutput -and $psOutput -match "NAME") {
    Write-Host $psOutput
} else {
    Write-Warn "无运行中的 kb-deploy 容器"
    docker ps --filter "name=kb-" --format "table {{.Names}}`t{{.Status}}`t{{.Ports}}"
}
Write-Host ""

# 2. 端口监听
Write-Info "=== 2. 宿主机端口监听 ==="
$ports = @(3306, 6379, 27017, 9000, 9001, 7700, 8090)
$connections = Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue
foreach ($port in $ports) {
    $listening = $connections | Where-Object { $_.LocalPort -eq $port }
    if ($listening) {
        Write-Ok "端口 $port 已监听"
    } else {
        Write-Bad "端口 $port 未监听"
    }
}
Write-Host ""

# 3. 磁盘空间
Write-Info "=== 3. 磁盘空间使用 ==="
Get-PSDrive -PSProvider FileSystem | Where-Object { $_.Used -ne $null } | ForEach-Object {
    $usedGB = [math]::Round($_.Used / 1GB, 2)
    $freeGB = [math]::Round($_.Free / 1GB, 2)
    $totalGB = $usedGB + $freeGB
    Write-Host ("  {0}: 已用 {1} GB / 可用 {2} GB / 总计 {3} GB" -f $_.Name, $usedGB, $freeGB, $totalGB)
}
Write-Host ""

# 4. 内存
Write-Info "=== 4. 系统内存使用 ==="
$os = Get-CimInstance Win32_OperatingSystem
$totalMemGB = [math]::Round($os.TotalVisibleMemorySize / 1MB, 2)
$freeMemGB = [math]::Round($os.FreePhysicalMemory / 1MB, 2)
$usedMemGB = [math]::Round($totalMemGB - $freeMemGB, 2)
Write-Host ("  总内存: {0} GB" -f $totalMemGB)
Write-Host ("  已用:   {0} GB" -f $usedMemGB)
Write-Host ("  可用:   {0} GB" -f $freeMemGB)
Write-Host ""

# 5. Docker 资源占用
Write-Info "=== 5. Docker 容器资源占用 ==="
$runningIds = docker compose -p $ComposeProject -f $ComposeFile ps -q 2>$null
if ($runningIds) {
    docker stats --no-stream --format "table {{.Name}}`t{{.CPUPerc}}`t{{.MemUsage}}`t{{.NetIO}}`t{{.BlockIO}}" $runningIds.Split("`n")
} else {
    Write-Warn "无运行中的容器"
}
Write-Host ""

# 6. Docker 总览
Write-Info "=== 6. Docker 系统总览 ==="
docker system df
Write-Host ""

Write-Host "============================================================"
Write-Info "状态查看完成"
Write-Host "============================================================"
