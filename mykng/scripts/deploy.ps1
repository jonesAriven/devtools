# ============================================================
# mykng 知识库微服务部署脚本（Windows PowerShell 版本）
# ============================================================
# 用法:
#   .\scripts\deploy.ps1 build      # 构建所有镜像
#   .\scripts\deploy.ps1 up         # 启动所有服务
#   .\scripts\deploy.ps1 down       # 停止所有服务
#   .\scripts\deploy.ps1 restart    # 重启所有服务
#   .\scripts\deploy.ps1 all        # build + up + health-check
#   .\scripts\deploy.ps1 logs       # 查看日志
#   .\scripts\deploy.ps1 status     # 查看状态
#   .\scripts\deploy.ps1 -help      # 显示帮助
# ============================================================

param(
    [Parameter(Position = 0)]
    [string]$Command = "",
    [Parameter(Position = 1)]
    [string]$Service = ""
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

# ---------- .env 检查 ----------
function Ensure-EnvFile {
    $envFile = Join-Path $ProjectRoot ".env"
    $envExample = Join-Path $ProjectRoot ".env.example"
    if (-not (Test-Path $envFile)) {
        if (Test-Path $envExample) {
            Write-Warn ".env 不存在，从 .env.example 复制..."
            Copy-Item $envExample $envFile
            Write-Info "已生成 .env，请按需修改后重新部署"
        } else {
            Write-Err "未找到 .env 与 .env.example"
            exit 1
        }
    }
}

# ---------- docker compose 封装 ----------
function Invoke-Dc {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Args)
    & docker compose -p $ComposeProject -f $ComposeFile @Args
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

# ---------- 等待容器健康 ----------
function Wait-Healthy {
    param([string]$Container, [int]$Timeout = 120)
    $elapsed = 0
    while ($elapsed -lt $Timeout) {
        $inspect = docker inspect --format='{{.State.Health.Status}}' $Container 2>$null
        if ($inspect -eq "healthy") {
            Write-Info "  [OK] $Container 健康"
            return $true
        } elseif ($inspect -eq "no-healthcheck") {
            $status = docker inspect --format='{{.State.Status}}' $Container 2>$null
            if ($status -eq "running") {
                Write-Info "  [OK] $Container 运行中（无健康检查）"
                return $true
            }
        }
        Write-Host "  等待 $Container 健康 ($elapsed s / $Timeout s)"
        Start-Sleep -Seconds 5
        $elapsed += 5
    }
    Write-Err "$Container 在 ${Timeout}s 内未健康"
    return $false
}

# ---------- 子命令 ----------
function Invoke-Build {
    Write-Step "开始构建所有 Docker 镜像..."
    Ensure-EnvFile
    Invoke-Dc build
    Write-Info "构建完成"
    Write-Host ""
    Write-Info "镜像列表:"
    docker images --format "table {{.Repository}}`t{{.Tag}}`t{{.Size}}`t{{.CreatedAt}}" | Select-String -Pattern "REPOSITORY|kb-"
}

function Invoke-Up {
    Write-Step "启动基础设施（mysql/redis/minio/meilisearch/mongodb）..."
    Ensure-EnvFile
    Invoke-Dc up -d mysql redis minio meilisearch mongodb

    Write-Step "等待基础设施健康..."
    Wait-Healthy "kb-mysql" 120 | Out-Null
    Wait-Healthy "kb-redis" 60 | Out-Null
    Wait-Healthy "kb-minio" 60 | Out-Null
    Wait-Healthy "kb-meilisearch" 60 | Out-Null
    Wait-Healthy "kb-mongo" 60 | Out-Null

    Write-Step "启动微服务..."
    Invoke-Dc up -d kb-auth kb-file kb-knowledge kb-ops kb-intelligence kb-gateway

    Write-Step "等待微服务就绪（最多 180s）..."
    foreach ($svc in @("kb-auth","kb-file","kb-knowledge","kb-ops","kb-intelligence","kb-gateway")) {
        $ok = Wait-Healthy $svc 180
        if (-not $ok) { Write-Warn "$svc 未在规定时间内就绪，请检查日志: docker logs $svc" }
    }

    Write-Host ""
    Write-Info "部署完成"
    Write-Host ""
    Invoke-Dc ps
    Write-Host ""
    Write-Info "网关地址: http://localhost:8090/kb"
    Write-Info "查看日志: .\scripts\deploy.ps1 logs"
}

function Invoke-Down {
    Write-Step "停止并移除所有容器..."
    Invoke-Dc down
    Write-Info "已停止并移除所有容器（数据卷保留）"
}

function Invoke-Restart {
    Write-Step "重启所有服务..."
    Invoke-Dc restart
    Start-Sleep -Seconds 5
    Invoke-Dc ps
    Write-Info "重启完成"
}

function Invoke-All {
    Write-Step "[1/3] 构建镜像..."
    Invoke-Build
    Write-Step "[2/3] 启动服务..."
    Invoke-Up
    Write-Step "[3/3] 执行健康检查..."
    $hc = Join-Path $ScriptDir "health-check.ps1"
    if (Test-Path $hc) {
        & $hc
    } else {
        Write-Warn "未找到 health-check.ps1，跳过健康检查"
    }
    Write-Info "全流程部署完成"
}

function Invoke-Logs {
    if ($Service) {
        Write-Info "查看 $Service 日志..."
        Invoke-Dc logs --tail=200 -f $Service
    } else {
        Write-Info "查看所有服务日志..."
        Invoke-Dc logs --tail=200 -f
    }
}

function Show-Help {
    Write-Host @"
mykng 知识库微服务部署脚本 (PowerShell)

用法:
  .\scripts\deploy.ps1 <command> [service]

命令:
  build           构建所有 Docker 镜像
  up              启动基础设施 → 等待健康 → 启动微服务
  down            停止并移除所有容器
  restart         重启所有服务
  all             完整流程: build + up + health-check
  logs [service]  查看日志
  status          查看服务状态
  -help           显示此帮助

示例:
  .\scripts\deploy.ps1 all
  .\scripts\deploy.ps1 up
  .\scripts\deploy.ps1 logs kb-auth
"@
}

# ---------- 入口 ----------
switch ($Command) {
    "build"   { Invoke-Build }
    "up"      { Invoke-Up }
    "down"    { Invoke-Down }
    "restart" { Invoke-Restart }
    "all"     { Invoke-All }
    "logs"    { Invoke-Logs }
    "status"  {
        $st = Join-Path $ScriptDir "status.ps1"
        if (Test-Path $st) { & $st } else { Invoke-Dc ps }
    }
    "-help"   { Show-Help }
    "--help"  { Show-Help }
    "help"    { Show-Help }
    "" {
        Write-Err "未指定命令"
        Show-Help
        exit 1
    }
    default {
        Write-Err "未知命令: $Command"
        Show-Help
        exit 1
    }
}
