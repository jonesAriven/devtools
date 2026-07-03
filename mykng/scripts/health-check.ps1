# ============================================================
# mykng 知识库微服务健康检查脚本（Windows PowerShell 版本）
# ============================================================
# 用法:
#   .\scripts\health-check.ps1                 # 全量检查
#   .\scripts\health-check.ps1 -Service kb-auth # 仅检查单个服务
#   .\scripts\health-check.ps1 -Help            # 显示帮助
# ============================================================

param(
    [string]$Service = "",
    [switch]$Help
)

$ErrorActionPreference = "Continue"

# ---------- 路径变量化 ----------
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Split-Path -Parent $ScriptDir
$ComposeProject = "kb-deploy"
$ComposeFile = Join-Path $ProjectRoot "docker-compose.yml"

Set-Location $ProjectRoot

# ---------- 颜色输出 ----------
$script:Pass = 0
$script:Warn = 0
$script:Fail = 0

function Write-Ok   { param([string]$Msg) Write-Host "  [PASS] $Msg" -ForegroundColor Green; $script:Pass++ }
function Write-Warn2{ param([string]$Msg) Write-Host "  [WARN] $Msg" -ForegroundColor Yellow; $script:Warn++ }
function Write-Bad  { param([string]$Msg) Write-Host "  [FAIL] $Msg" -ForegroundColor Red; $script:Fail++ }
function Write-Info { param([string]$Msg) Write-Host "[INFO ] $Msg" -ForegroundColor Cyan }

if ($Help) {
    Write-Host @"
mykng 知识库微服务健康检查脚本 (PowerShell)

用法:
  .\scripts\health-check.ps1 [options]

参数:
  -Service <name>  仅检查指定服务
  -Help            显示此帮助
"@
    exit 0
}

# 微服务: name=port
$services = @(
    @{ Name = "kb-gateway"; Port = 8080 },
    @{ Name = "kb-auth"; Port = 8081 },
    @{ Name = "kb-file"; Port = 8082 },
    @{ Name = "kb-knowledge"; Port = 8083 },
    @{ Name = "kb-intelligence"; Port = 8086 }
)

$infra = @("kb-mysql","kb-redis","kb-mongo","kb-minio","kb-meilisearch")

Write-Host "============================================================"
Write-Host "  mykng 知识库健康检查"
Write-Host "  时间: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
Write-Host "  项目: $ComposeProject"
Write-Host "============================================================"
Write-Host ""

# 1. 容器列表概览
Write-Info "=== Docker 容器列表 ==="
docker compose -p $ComposeProject -f $ComposeFile ps --format "table {{.Name}}`t{{.Status}}`t{{.Ports}}" 2>$null
if ($LASTEXITCODE -ne 0) {
    docker ps --filter "name=kb-" --format "table {{.Names}}`t{{.Status}}`t{{.Ports}}"
}
Write-Host ""

# 2. 微服务容器状态
Write-Info "=== 微服务容器状态 ==="
foreach ($svc in $services) {
    if ($Service -and $svc.Name -ne $Service) { continue }
    $status = docker inspect --format='{{.State.Status}}' $svc.Name 2>$null
    $health = docker inspect --format='{{.State.Health.Status}}' $svc.Name 2>$null
    if (-not $status) { $status = "not-found" }
    if (-not $health) { $health = "no-healthcheck" }

    if ($status -eq "running") {
        if ($health -eq "healthy" -or $health -eq "no-healthcheck") {
            Write-Ok "$($svc.Name) (status=$status, health=$health)"
        } else {
            Write-Warn2 "$($svc.Name) (status=$status, health=$health)"
        }
    } else {
        Write-Bad "$($svc.Name) (status=$status)"
    }
}
Write-Host ""

# 3. 基础设施容器状态
if (-not $Service) {
    Write-Info "=== 基础设施容器状态 ==="
    foreach ($name in $infra) {
        $status = docker inspect --format='{{.State.Status}}' $name 2>$null
        $health = docker inspect --format='{{.State.Health.Status}}' $name 2>$null
        if (-not $status) { $status = "not-found" }
        if (-not $health) { $health = "no-healthcheck" }

        if ($status -eq "running") {
            if ($health -eq "healthy" -or $health -eq "no-healthcheck") {
                Write-Ok "$name (status=$status, health=$health)"
            } else {
                Write-Warn2 "$name (status=$status, health=$health)"
            }
        } else {
            Write-Bad "$name (status=$status)"
        }
    }
    Write-Host ""
}

# 4. 微服务 actuator/health 端点
Write-Info "=== 微服务 actuator/health 端点 ==="
foreach ($svc in $services) {
    if ($Service -and $svc.Name -ne $Service) { continue }
    $status = docker inspect --format='{{.State.Status}}' $svc.Name 2>$null
    if ($status -ne "running") {
        Write-Warn2 "$($svc.Name) 容器未运行，跳过 actuator 检查"
        continue
    }
    $result = docker exec $svc.Name wget -qO- --timeout=5 "http://localhost:$($svc.Port)/actuator/health" 2>$null
    if ($result -match '"status":"UP"') {
        Write-Ok "$($svc.Name) actuator/health = UP"
    } elseif ($result -match '"status":"DOWN"') {
        Write-Bad "$($svc.Name) actuator/health = DOWN"
    } else {
        Write-Bad "$($svc.Name) actuator/health 不可达"
    }
}
Write-Host ""

# 5. 基础设施连通性
if (-not $Service) {
    Write-Info "=== 基础设施连通性 ==="

    # MySQL
    $mysqlPass = $env:MYSQL_ROOT_PASSWORD; if (-not $mysqlPass) { $mysqlPass = "kb123456" }
    $envFile = Join-Path $ProjectRoot ".env"
    if (Test-Path $envFile) {
        Get-Content $envFile | ForEach-Object {
            if ($_ -match '^MYSQL_ROOT_PASSWORD=(.*)$') { $mysqlPass = $matches[1] }
        }
    }
    $mysqlPing = docker exec kb-mysql mysqladmin -uroot -p"$mysqlPass" ping 2>$null
    if ($mysqlPing -match "mysqld is alive") {
        Write-Ok "MySQL ping 成功"
    } else {
        Write-Bad "MySQL ping 失败"
    }

    # Redis
    $redisPing = docker exec kb-redis redis-cli ping 2>$null
    if ($redisPing -eq "PONG") {
        Write-Ok "Redis PING = PONG"
    } else {
        Write-Bad "Redis PING 失败"
    }

    # MongoDB
    $mongoPing = docker exec kb-mongo mongosh --quiet --eval "db.adminCommand('ping').ok" 2>$null
    if ($mongoPing -eq "1") {
        Write-Ok "MongoDB ping = 1"
    } else {
        Write-Bad "MongoDB ping 失败"
    }

    # MinIO
    $minioResp = docker exec kb-minio wget --spider --server-response "http://localhost:9000/minio/health/live" 2>&1
    if ($minioResp -match "HTTP/[\d.]+\s+200") {
        Write-Ok "MinIO /minio/health/live = 200"
    } else {
        Write-Bad "MinIO 健康检查失败"
    }

    # MeiliSearch
    $meiliResp = docker exec kb-meilisearch wget --spider --server-response "http://localhost:7700/health" 2>&1
    if ($meiliResp -match "HTTP/[\d.]+\s+200") {
        Write-Ok "MeiliSearch /health = 200"
    } else {
        Write-Bad "MeiliSearch 健康检查失败"
    }
    Write-Host ""
}

# 6. 汇总
Write-Host "============================================================"
Write-Host "  检查结果汇总: 通过 $script:Pass / 警告 $script:Warn / 失败 $script:Fail"
Write-Host "============================================================"

if ($script:Fail -gt 0) { exit 1 }
exit 0
