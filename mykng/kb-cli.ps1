<#
.SYNOPSIS
    mykng 知识库微服务一键启停管理脚本
.DESCRIPTION
    基于 Docker Compose profiles 机制，支持按环境（dev/test/prod）启停模块，
    也支持单独启停某个服务、查看状态、日志、重建等操作。
.NOTES
    使用前请确保已安装 Docker Desktop 并启动 Docker 引擎。
    脚本需在 docker-compose.yml 所在目录运行，或通过路径调用。
.EXAMPLE
    .\kb-cli.ps1 up dev            # 启动开发环境（最小化）
    .\kb-cli.ps1 up test           # 启动测试环境（全量基础设施+全部服务）
    .\kb-cli.ps1 up prod           # 启动生产环境（全量）
    .\kb-cli.ps1 down              # 停止所有服务
    .\kb-cli.ps1 stop kb-file      # 停止指定模块
    .\kb-cli.ps1 start kb-auth     # 启动指定模块（不启动依赖）
    .\kb-cli.ps1 status            # 查看状态
    .\kb-cli.ps1 logs kb-gateway   # 查看日志
    .\kb-cli.ps1 rebuild kb-auth   # 重新构建并启动
    .\kb-cli.ps1 list              # 列出所有模块
#>

param(
    [Parameter(Position = 0)]
    [ValidateSet('up', 'down', 'start', 'stop', 'status', 'logs', 'rebuild', 'list', 'help')]
    [string]$Command,

    [Parameter(Position = 1)]
    [string]$Target
)

# ============================================================
# 全局配置
# ============================================================

# 脚本所在目录（定位 docker-compose.yml）
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ComposeFile = Join-Path $ScriptDir "docker-compose.yml"

# 支持的环境列表
$ValidEnvironments = @('dev', 'test', 'prod')

# 所有服务列表（基础设施 + 微服务）
$InfrastructureServices = @('mysql', 'redis', 'nacos', 'minio', 'meilisearch', 'mongodb')
$MicroServices = @('kb-gateway', 'kb-auth', 'kb-file', 'kb-knowledge', 'kb-intelligence')
$AllServices = $InfrastructureServices + $MicroServices

# 可构建的微服务（有 Dockerfile 的服务）
$BuildableServices = $MicroServices

# 服务到 profile 的映射（与 docker-compose.yml 保持一致）
$ServiceProfiles = @{
    'mysql'           = @('dev', 'test', 'prod')
    'redis'           = @('dev', 'test', 'prod')
    'nacos'           = @('dev', 'test', 'prod')
    'minio'           = @('test', 'prod')
    'meilisearch'     = @('test', 'prod')
    'mongodb'         = @('test', 'prod')
    'kb-gateway'      = @('dev', 'test', 'prod')
    'kb-auth'         = @('dev', 'test', 'prod')
    'kb-file'         = @('test', 'prod')
    'kb-knowledge'    = @('dev', 'test', 'prod')
    'kb-intelligence' = @('test', 'prod')
}

# ============================================================
# 辅助函数
# ============================================================

# 彩色输出
function Write-Info { param([string]$Msg) Write-Host "[INFO]  $Msg" -ForegroundColor Cyan }
function Write-Ok   { param([string]$Msg) Write-Host "[OK]    $Msg" -ForegroundColor Green }
function Write-Warn { param([string]$Msg) Write-Host "[WARN]  $Msg" -ForegroundColor Yellow }
function Write-Err  { param([string]$Msg) Write-Host "[ERROR] $Msg" -ForegroundColor Red }

# 校验 docker 是否可用
function Test-DockerAvailable {
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
        Write-Err "未检测到 docker 命令，请先安装 Docker Desktop"
        exit 1
    }
    $null = docker info 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-Err "Docker 引擎未运行，请先启动 Docker Desktop"
        exit 1
    }
}

# 校验 docker-compose.yml 是否存在
function Test-ComposeFile {
    if (-not (Test-Path $ComposeFile)) {
        Write-Err "未找到 docker-compose.yml: $ComposeFile"
        exit 1
    }
}

# 构造并执行 docker compose 命令
function Invoke-Compose {
    param(
        [string[]]$Profiles = @(),
        [Parameter(ValueFromRemainingArguments = $true)]
        [string[]]$ComposeArgs
    )
    $allArgs = @('compose', '-f', $ComposeFile)
    foreach ($p in $Profiles) {
        $allArgs += @('--profile', $p)
    }
    if ($ComposeArgs) {
        $allArgs += $ComposeArgs
    }
    & docker @allArgs
    return $LASTEXITCODE
}

# 根据服务名获取所属 profile 列表
function Get-ServiceProfile {
    param([string]$ServiceName)
    if ($ServiceProfiles.ContainsKey($ServiceName)) {
        return $ServiceProfiles[$ServiceName]
    }
    return $null
}

# 校验服务名是否合法
function Test-ServiceName {
    param([string]$ServiceName)
    if (-not $ServiceName) {
        Write-Err "请指定服务名"
        Write-Host "可用服务: $($AllServices -join ', ')"
        return $false
    }
    if ($ServiceName -notin $AllServices) {
        Write-Err "未知服务: $ServiceName"
        Write-Host "可用服务: $($AllServices -join ', ')"
        return $false
    }
    return $true
}

# ============================================================
# 命令实现
# ============================================================

# ---------- up: 启动指定环境 ----------
function Invoke-Up {
    if (-not $Target) {
        Write-Err "请指定环境: up <dev|test|prod>"
        Write-Host "用法: .\kb-cli.ps1 up dev"
        exit 1
    }
    if ($Target -notin $ValidEnvironments) {
        Write-Err "无效的环境: $Target (支持: $($ValidEnvironments -join ', '))"
        exit 1
    }

    $envDesc = switch ($Target) {
        'dev'  { '最小化启动 (mysql + redis + nacos + gateway + auth + knowledge)' }
        'test' { '核心功能验证 (全量基础设施 + 全部服务)' }
        'prod' { '生产环境 (全量启动)' }
    }
    Write-Info "启动 [$Target] 环境: $envDesc"

    $code = Invoke-Compose -Profiles @($Target) -ComposeArgs @('up', '-d')
    if ($code -eq 0) {
        Write-Ok "[$Target] 环境已启动"
        Write-Host ""
        Invoke-Compose -Profiles @($Target) -ComposeArgs @('ps')
    } else {
        Write-Err "[$target] 环境启动失败 (exit code: $code)"
        exit $code
    }
}

# ---------- down: 停止所有服务 ----------
function Invoke-Down {
    Write-Info "停止所有服务（所有环境）..."
    $code = Invoke-Compose -Profiles $ValidEnvironments -ComposeArgs @('down')
    if ($code -eq 0) {
        Write-Ok "所有服务已停止并移除容器（数据卷保留）"
    } else {
        Write-Err "停止失败 (exit code: $code)"
        exit $code
    }
}

# ---------- start: 启动指定模块（不启动依赖） ----------
function Invoke-Start {
    if (-not (Test-ServiceName $Target)) { exit 1 }

    $profiles = Get-ServiceProfile -ServiceName $Target
    if (-not $profiles) {
        Write-Err "无法确定服务 [$Target] 的 profile"
        exit 1
    }

    Write-Info "启动服务 [$Target] (profiles: $($profiles -join ','))..."
    # 使用 up -d --no-deps: 仅启动该服务，不自动启动依赖
    $code = Invoke-Compose -Profiles $profiles -ComposeArgs @('up', '-d', '--no-deps', $Target)
    if ($code -eq 0) {
        Write-Ok "服务 [$Target] 已启动"
    } else {
        Write-Err "服务 [$Target] 启动失败 (exit code: $code)"
        Write-Warn "提示: 请确保依赖服务已启动，或使用 'up <env>' 启动完整环境"
        exit $code
    }
}

# ---------- stop: 停止指定模块 ----------
function Invoke-Stop {
    if (-not (Test-ServiceName $Target)) { exit 1 }

    $profiles = Get-ServiceProfile -ServiceName $Target
    if (-not $profiles) {
        Write-Err "无法确定服务 [$Target] 的 profile"
        exit 1
    }

    Write-Info "停止服务 [$Target]..."
    $code = Invoke-Compose -Profiles $profiles -ComposeArgs @('stop', $Target)
    if ($code -eq 0) {
        Write-Ok "服务 [$Target] 已停止"
    } else {
        Write-Err "服务 [$Target] 停止失败 (exit code: $code)"
        exit $code
    }
}

# ---------- status: 查看状态 ----------
function Invoke-Status {
    Write-Info "当前服务状态："
    Write-Host ""
    $code = Invoke-Compose -Profiles $ValidEnvironments -ComposeArgs @('ps')
    if ($code -ne 0) {
        Write-Warn "状态查询异常 (exit code: $code)"
    }
}

# ---------- logs: 查看日志 ----------
function Invoke-Logs {
    if ($Target) {
        if (-not (Test-ServiceName $Target)) { exit 1 }
        $profiles = Get-ServiceProfile -ServiceName $Target
        Write-Info "查看 [$Target] 日志（Ctrl+C 退出）..."
        $null = Invoke-Compose -Profiles $profiles -ComposeArgs @('logs', '-f', '--tail=100', $Target)
    } else {
        Write-Info "查看所有服务日志（Ctrl+C 退出）..."
        $null = Invoke-Compose -Profiles $ValidEnvironments -ComposeArgs @('logs', '-f', '--tail=100')
    }
}

# ---------- rebuild: 重新构建并启动 ----------
function Invoke-Rebuild {
    if (-not (Test-ServiceName $Target)) { exit 1 }

    if ($Target -notin $BuildableServices) {
        Write-Err "服务 [$Target] 不可重建（仅微服务支持构建）"
        Write-Host "可重建服务: $($BuildableServices -join ', ')"
        exit 1
    }

    $profiles = Get-ServiceProfile -ServiceName $Target
    Write-Info "重新构建并启动 [$Target]..."
    $code = Invoke-Compose -Profiles $profiles -ComposeArgs @('up', '-d', '--build', $Target)
    if ($code -eq 0) {
        Write-Ok "服务 [$Target] 已重建并启动"
    } else {
        Write-Err "服务 [$Target] 重建失败 (exit code: $code)"
        exit $code
    }
}

# ---------- list: 列出所有模块 ----------
function Invoke-List {
    Write-Host ""
    Write-Host "========== mykng 模块列表 ==========" -ForegroundColor Cyan
    Write-Host ""

    Write-Host "【基础设施】" -ForegroundColor Yellow
    foreach ($svc in $InfrastructureServices) {
        $p = $ServiceProfiles[$svc] -join ', '
        $port = switch ($svc) {
            'mysql'       { '3306' }
            'redis'       { '6379' }
            'nacos'       { '8848, 9848' }
            'minio'       { '9000, 9001' }
            'meilisearch' { '7700' }
            'mongodb'     { '27017' }
            default       { '-' }
        }
        Write-Host ("  {0,-14} ports: {1,-14} profiles: [{2}]" -f $svc, $port, $p)
    }

    Write-Host ""
    Write-Host "【微服务】" -ForegroundColor Yellow
    foreach ($svc in $MicroServices) {
        $p = $ServiceProfiles[$svc] -join ', '
        $port = switch ($svc) {
            'kb-gateway'      { '8090->8080' }
            'kb-auth'         { '8081 (内网)' }
            'kb-file'         { '8082 (内网)' }
            'kb-knowledge'    { '8083 (内网)' }
            'kb-intelligence' { '8086 (内网)' }
            default           { '-' }
        }
        Write-Host ("  {0,-14} ports: {1,-14} profiles: [{2}]" -f $svc, $port, $p)
    }

    Write-Host ""
    Write-Host "【环境说明】" -ForegroundColor Yellow
    Write-Host "  dev  - 最小化启动（mysql + redis + nacos + gateway + auth + knowledge）"
    Write-Host "  test - 核心验证（全量基础设施 + 全部微服务）"
    Write-Host "  prod - 生产环境（全量启动）"
    Write-Host ""

    Write-Host "【常用命令】" -ForegroundColor Yellow
    Write-Host "  .\kb-cli.ps1 up dev             启动开发环境"
    Write-Host "  .\kb-cli.ps1 up test            启动测试环境"
    Write-Host "  .\kb-cli.ps1 down               停止所有服务"
    Write-Host "  .\kb-cli.ps1 start kb-auth      单独启动某服务"
    Write-Host "  .\kb-cli.ps1 stop kb-file       单独停止某服务"
    Write-Host "  .\kb-cli.ps1 status             查看运行状态"
    Write-Host "  .\kb-cli.ps1 logs kb-gateway    查看服务日志"
    Write-Host "  .\kb-cli.ps1 rebuild kb-auth    重新构建并启动"
    Write-Host ""
}

# ---------- help: 显示帮助 ----------
function Show-Help {
    Write-Host ""
    Write-Host "========== mykng 知识库微服务管理脚本 ==========" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "用法:" -ForegroundColor Yellow
    Write-Host "  .\kb-cli.ps1 up <dev|test|prod>   启动指定环境"
    Write-Host "  .\kb-cli.ps1 down                 停止所有服务"
    Write-Host "  .\kb-cli.ps1 start <service>      启动指定模块（不启动依赖）"
    Write-Host "  .\kb-cli.ps1 stop <service>       停止指定模块"
    Write-Host "  .\kb-cli.ps1 status               查看运行状态"
    Write-Host "  .\kb-cli.ps1 logs [service]       查看日志（不指定则查看全部）"
    Write-Host "  .\kb-cli.ps1 rebuild <service>    重新构建并启动微服务"
    Write-Host "  .\kb-cli.ps1 list                 列出所有模块"
    Write-Host "  .\kb-cli.ps1 help                 显示此帮助"
    Write-Host ""
    Write-Host "可用服务:" -ForegroundColor Yellow
    Write-Host "  基础设施: $($InfrastructureServices -join ', ')"
    Write-Host "  微服务:   $($MicroServices -join ', ')"
    Write-Host ""
    Write-Host "环境说明:" -ForegroundColor Yellow
    Write-Host "  dev  - 最小化启动（mysql + redis + nacos + gateway + auth + knowledge）"
    Write-Host "  test - 核心验证（全量基础设施 + 全部微服务）"
    Write-Host "  prod - 生产环境（全量启动）"
    Write-Host ""
}

# ============================================================
# Tab 补全注册（可选功能）
# ============================================================
# 将以下代码加入 PowerShell 配置文件 ($PROFILE) 即可启用 tab 补全:
#
# Register-ArgumentCompleter -Native -CommandName 'kb-cli.ps1' -ScriptBlock {
#     param($wordToComplete, $commandAst, $cursorPosition)
#     $commands = @('up','down','start','stop','status','logs','rebuild','list','help')
#     $services = @('mysql','redis','nacos','minio','meilisearch','mongodb',
#                    'kb-gateway','kb-auth','kb-file','kb-knowledge','kb-intelligence')
#     $envs = @('dev','test','prod')
#     $elements = $commandAst.CommandElements | Select-Object -Skip 1 -ExpandProperty Value
#     if ($elements.Count -eq 0) {
#         $commands | Where-Object { $_ -like "$wordToComplete*" } |
#             ForEach-Object { [System.Management.Automation.CompletionResult]::new($_, $_, 'ParameterValue', $_) }
#     } elseif ($elements[0] -eq 'up' -and $elements.Count -eq 1) {
#         $envs | Where-Object { $_ -like "$wordToComplete*" } |
#             ForEach-Object { [System.Management.Automation.CompletionResult]::new($_, $_, 'ParameterValue', $_) }
#     } elseif ($elements[0] -in @('start','stop','logs','rebuild') -and $elements.Count -eq 1) {
#         $services | Where-Object { $_ -like "$wordToComplete*" } |
#             ForEach-Object { [System.Management.Automation.CompletionResult]::new($_, $_, 'ParameterValue', $_) }
#     }
# }
# ============================================================

# ============================================================
# 主入口
# ============================================================

# 无命令时显示帮助
if (-not $Command) {
    Show-Help
    exit 0
}

# help 命令
if ($Command -eq 'help') {
    Show-Help
    exit 0
}

# 校验环境（list 和 help 不需要 docker）
if ($Command -ne 'list') {
    Test-ComposeFile
    Test-DockerAvailable
}

# 分发命令
switch ($Command) {
    'up'      { Invoke-Up }
    'down'    { Invoke-Down }
    'start'   { Invoke-Start }
    'stop'    { Invoke-Stop }
    'status'  { Invoke-Status }
    'logs'    { Invoke-Logs }
    'rebuild' { Invoke-Rebuild }
    'list'    { Invoke-List }
}
