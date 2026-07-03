# ============================================================
# mykng 知识库微服务启动脚本（Windows PowerShell 版本，SOP附录G要求）
# ============================================================
# 用法:
#   .\scripts\start.ps1              # 启动所有服务
#   .\scripts\start.ps1 kb-auth      # 启动单个服务
#   .\scripts\start.ps1 -Help        # 显示帮助
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
mykng 知识库微服务启动脚本 (PowerShell)

用法:
  .\scripts\start.ps1 [service]

参数:
  service   可选服务名，不指定则启动全部
            可选: mysql / redis / minio / meilisearch / mongodb
                kb-gateway / kb-auth / kb-file / kb-knowledge / kb-intelligence

示例:
  .\scripts\start.ps1
  .\scripts\start.ps1 kb-auth
"@
}

if ($Help -or $Service -in @("-help", "--help", "-h")) {
    Show-Help
    exit 0
}

# .env 检查
$envFile = Join-Path $ProjectRoot ".env"
$envExample = Join-Path $ProjectRoot ".env.example"
if (-not (Test-Path $envFile)) {
    if (Test-Path $envExample) {
        Write-Warn ".env 不存在，从 .env.example 复制..."
        Copy-Item $envExample $envFile
    } else {
        Write-Err "未找到 .env 与 .env.example"
        exit 1
    }
}

Write-Info "启动 mykng 服务..."
Write-Info "  项目: $ComposeProject"
Write-Info "  时间: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
Write-Host ""

if ($Service) {
    Write-Info "启动单个服务: $Service"
    & docker compose -p $ComposeProject -f $ComposeFile up -d $Service
    if ($LASTEXITCODE -ne 0) { Write-Err "启动 $Service 失败"; exit $LASTEXITCODE }
} else {
    Write-Info "启动所有服务..."
    & docker compose -p $ComposeProject -f $ComposeFile up -d
    if ($LASTEXITCODE -ne 0) { Write-Err "启动失败"; exit $LASTEXITCODE }
}

Start-Sleep -Seconds 3
Write-Host ""
Write-Info "当前服务状态:"
& docker compose -p $ComposeProject -f $ComposeFile ps
Write-Host ""

$kbContext = if ($env:KB_CONTEXT) { $env:KB_CONTEXT } else { "/kb" }
Write-Info "网关地址: http://localhost:8090$kbContext"
Write-Info "查看日志: .\scripts\deploy.ps1 logs [service]"
Write-Info "健康检查: .\scripts\health-check.ps1"
