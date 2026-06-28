# ============================================================
# mykng 知识库微服务停止脚本（Windows PowerShell 版本，SOP附录G要求）
# ============================================================
# 用法:
#   .\scripts\stop.ps1              # 停止所有服务（保留容器）
#   .\scripts\stop.ps1 kb-auth      # 停止单个服务
#   .\scripts\stop.ps1 -Down        # 停止并移除容器（保留数据卷）
#   .\scripts\stop.ps1 -Down -Volumes  # 停止并移除容器和数据卷（危险！）
#   .\scripts\stop.ps1 -Help        # 显示帮助
# ============================================================

param(
    [Parameter(Position = 0)]
    [string]$Service = "",
    [switch]$Down,
    [switch]$Volumes,
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
mykng 知识库微服务停止脚本 (PowerShell)

用法:
  .\scripts\stop.ps1 [options] [service]

参数:
  (无)            停止所有容器（容器保留，可重新 start）
  -Down           停止并移除所有容器（数据卷保留）
  -Down -Volumes  停止并移除所有容器和数据卷（危险！）
  service         可选服务名，仅停止单个服务

示例:
  .\scripts\stop.ps1
  .\scripts\stop.ps1 kb-auth
  .\scripts\stop.ps1 -Down
"@
}

if ($Help) {
    Show-Help
    exit 0
}

Write-Info "停止 mykng 服务..."
Write-Info "  项目: $ComposeProject"
Write-Info "  时间: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
Write-Host ""

if ($Volumes) {
    Write-Warn "⚠ 即将停止并删除所有容器和数据卷（不可恢复）！"
    $confirm = Read-Host "确认？(yes/no)"
    if ($confirm -ne "yes") {
        Write-Info "已取消"
        exit 0
    }
    & docker compose -p $ComposeProject -f $ComposeFile down -v
    if ($LASTEXITCODE -ne 0) { Write-Err "停止失败"; exit $LASTEXITCODE }
    Write-Info "已停止并删除所有容器和数据卷"
} elseif ($Down) {
    if ($Service) {
        & docker compose -p $ComposeProject -f $ComposeFile stop $Service
        & docker compose -p $ComposeProject -f $ComposeFile rm -f $Service
        if ($LASTEXITCODE -ne 0) { Write-Err "停止 $Service 失败"; exit $LASTEXITCODE }
        Write-Info "已停止并移除 $Service"
    } else {
        & docker compose -p $ComposeProject -f $ComposeFile down
        if ($LASTEXITCODE -ne 0) { Write-Err "停止失败"; exit $LASTEXITCODE }
        Write-Info "已停止并移除所有容器（数据卷保留）"
    }
} else {
    if ($Service) {
        & docker compose -p $ComposeProject -f $ComposeFile stop $Service
        if ($LASTEXITCODE -ne 0) { Write-Err "停止 $Service 失败"; exit $LASTEXITCODE }
        Write-Info "已停止 $Service（容器保留）"
    } else {
        & docker compose -p $ComposeProject -f $ComposeFile stop
        if ($LASTEXITCODE -ne 0) { Write-Err "停止失败"; exit $LASTEXITCODE }
        Write-Info "已停止所有服务（容器保留，可 start 重新启动）"
    }
}

Write-Host ""
Write-Info "当前服务状态:"
& docker compose -p $ComposeProject -f $ComposeFile ps 2>$null
if ($LASTEXITCODE -ne 0) {
    Write-Host "  无运行中的容器"
}
