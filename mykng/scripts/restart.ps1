# ============================================================
# mykng 知识库微服务重启脚本（Windows PowerShell 版本，SOP附录G要求）
# ============================================================
# 用法:
#   .\scripts\restart.ps1              # 重启所有服务
#   .\scripts\restart.ps1 kb-auth      # 重启单个服务
#   .\scripts\restart.ps1 -Help        # 显示帮助
# ============================================================

param(
    [Parameter(Position = 0)]
    [string]$Service = "",
    [switch]$Help
)

$ErrorActionPreference = "Stop"

# ---------- 路径变量化 ----------
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Split-Path -Parent $ScriptDir
$ComposeProject = "kb-deploy"
$ComposeFile = Join-Path $ProjectRoot "docker-compose.yml"

Set-Location $ProjectRoot

# ---------- 颜色输出 ----------
function Write-Info { param([string]$Msg) Write-Host "[INFO ] $Msg" -ForegroundColor Green }
function Write-Step { param([string]$Msg) Write-Host "[STEP ] $Msg" -ForegroundColor Cyan }
function Write-Warn { param([string]$Msg) Write-Host "[WARN ] $Msg" -ForegroundColor Yellow }
function Write-Err  { param([string]$Msg) Write-Host "[ERROR] $Msg" -ForegroundColor Red }

function Show-Help {
    Write-Host @"
mykng 知识库微服务重启脚本 (PowerShell)

用法:
  .\scripts\restart.ps1 [service]

参数:
  service   可选服务名，不指定则重启全部
            可选: mysql / redis / minio / meilisearch / mongodb
                kb-gateway / kb-auth / kb-file / kb-knowledge / kb-intelligence

示例:
  .\scripts\restart.ps1
  .\scripts\restart.ps1 kb-auth
"@
}

if ($Help -or $Service -in @("-help", "--help", "-h")) {
    Show-Help
    exit 0
}

Write-Info "重启 mykng 服务..."
Write-Info "  项目: $ComposeProject"
Write-Info "  时间: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
Write-Host ""

if ($Service) {
    Write-Info "重启单个服务: $Service"
    & docker compose -p $ComposeProject -f $ComposeFile restart $Service
    if ($LASTEXITCODE -ne 0) { Write-Err "重启 $Service 失败"; exit $LASTEXITCODE }
} else {
    Write-Info "重启所有服务..."
    & docker compose -p $ComposeProject -f $ComposeFile restart
    if ($LASTEXITCODE -ne 0) { Write-Err "重启失败"; exit $LASTEXITCODE }
}

# 等待服务就绪
Start-Sleep -Seconds 5

Write-Host ""
Write-Info "当前服务状态:"
& docker compose -p $ComposeProject -f $ComposeFile ps
Write-Host ""

$kbContext = if ($env:KB_CONTEXT) { $env:KB_CONTEXT } else { "/kb" }
Write-Info "网关地址: http://localhost:8090$kbContext"
Write-Info "建议执行健康检查: .\scripts\health-check.ps1"
