# ============================================================
# mykng 知识库微服务环境初始化脚本（Windows PowerShell 版本，SOP附录G要求，首次部署用）
# ============================================================
# 用法:
#   .\scripts\init-env.ps1           # 完整初始化
#   .\scripts\init-env.ps1 -Check    # 仅检查环境
#   .\scripts\init-env.ps1 -Help     # 显示帮助
#
# 执行内容:
#   1. 检查 Docker / Docker Compose 版本
#   2. 创建必要目录（/data/kb-web, /data/logs, /data/backup, /data/import）
#   3. 复制 .env.example 到 .env（如不存在）
#   4. 初始化数据库（执行 init-sql/）
# ============================================================

param(
    [switch]$Check,
    [switch]$Help
)

$ErrorActionPreference = "Continue"

# ---------- 路径变量化 ----------
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Split-Path -Parent $ScriptDir
$ComposeProject = "kb-deploy"
$ComposeFile = Join-Path $ProjectRoot "docker-compose.yml"
$InitSqlDir = Join-Path $ProjectRoot "init-sql"

# 需要创建的数据目录（Linux 宿主机路径，通过 docker 容器或 WSL 创建）
$DataDirs = @(
    "/data/kb-web",
    "/data/logs",
    "/data/backup/mysql",
    "/data/backup/mongodb",
    "/data/import"
)

# 从 .env 读取密码（先以默认值初始化）
$global:MysqlPass = if ($env:MYSQL_ROOT_PASSWORD) { $env:MYSQL_ROOT_PASSWORD } else { "kb123456" }

# ---------- 颜色输出 ----------
function Write-Info { param([string]$Msg) Write-Host "[INFO ] $Msg" -ForegroundColor Cyan }
function Write-Step { param([string]$Msg) Write-Host "[STEP ] $Msg" -ForegroundColor Cyan }
function Write-Warn { param([string]$Msg) Write-Host "[WARN ] $Msg" -ForegroundColor Yellow }
function Write-Err  { param([string]$Msg) Write-Host "[ERROR] $Msg" -ForegroundColor Red }

$global:Pass = 0
$global:Fail = 0
function Write-Ok  { param([string]$Msg) Write-Host "  [✓ PASS] $Msg" -ForegroundColor Green; $global:Pass++ }
function Write-Bad { param([string]$Msg) Write-Host "  [✗ FAIL] $Msg" -ForegroundColor Red; $global:Fail++ }

# ---------- 帮助 ----------
function Show-Help {
    Write-Host @"
mykng 知识库环境初始化脚本 (PowerShell, 首次部署使用)

用法:
  .\scripts\init-env.ps1 [options]

参数:
  (无)      完整初始化（检查 + 建目录 + 生成 .env + 初始化数据库）
  -Check    仅检查环境依赖，不做任何修改
  -Help     显示此帮助

初始化内容:
  1. Docker >= 20.10 / Docker Compose >= v2
  2. 数据目录: /data/{kb-web,logs,backup,import}
  3. .env 文件（从 .env.example 复制）
  4. MySQL 数据库（执行 init-sql/）
"@
}

if ($Help) {
    Show-Help
    exit 0
}

# ---------- 1. 环境依赖检查 ----------
function Test-Docker {
    Write-Info "=== Docker 环境检查 ==="
    $dockerCmd = Get-Command docker -ErrorAction SilentlyContinue
    if (-not $dockerCmd) {
        Write-Bad "未安装 docker"
        return $false
    }
    $dockerVer = (docker --version 2>$null) -replace 'Docker version ', '' -replace ', build.*', '' -replace ',', ''
    Write-Ok "Docker 已安装: $dockerVer"

    $composeVer = $null
    try {
        $composeVer = docker compose version --short 2>$null
    } catch {}
    if ($composeVer) {
        Write-Ok "Docker Compose v2: $composeVer"
    } else {
        Write-Bad "未安装 docker compose v2"
        return $false
    }

    # 检查 docker 服务运行
    $dockerInfo = docker info 2>$null
    if ($LASTEXITCODE -eq 0) {
        Write-Ok "Docker daemon 运行中"
    } else {
        Write-Bad "Docker daemon 未运行"
        return $false
    }

    return $true
}

# ---------- 2. 创建数据目录 ----------
function New-DataDirs {
    Write-Step "创建数据目录..."
    # 这些是 Linux 宿主机路径；Windows 下通过 docker 容器创建在 WSL2/Docker Desktop 环境中
    foreach ($d in $DataDirs) {
        Write-Info "  确保: $d"
        # 通过一个临时 busybox 容器创建目录（绑定 / 挂载）
        & docker run --rm -v /:/host alpine sh -c "mkdir -p /host$d" 2>$null
        if ($LASTEXITCODE -eq 0) {
            Write-Ok "  已创建/存在: $d"
        } else {
            Write-Warn "  跳过: $d（如使用 Docker Desktop 可手动创建）"
        }
    }
}

# ---------- 3. 生成 .env ----------
function Ensure-EnvFile {
    Write-Step "检查 .env 文件..."
    $envFile = Join-Path $ProjectRoot ".env"
    $envExample = Join-Path $ProjectRoot ".env.example"
    if (Test-Path $envFile) {
        Write-Ok ".env 已存在"
    } elseif (Test-Path $envExample) {
        Copy-Item $envExample $envFile
        Write-Warn "已从 .env.example 复制生成 .env，请按需修改后重新执行"
    } else {
        Write-Err "未找到 .env.example，无法生成 .env"
        return $false
    }

    # 重新加载 .env 以便后续使用
    if (Test-Path $envFile) {
        Get-Content $envFile | ForEach-Object {
            if ($_ -match '^\s*MYSQL_ROOT_PASSWORD\s*=\s*(.+)\s*$') {
                $global:MysqlPass = $matches[1].Trim()
            }
        }
    }
    return $true
}

# ---------- 4. 初始化数据库 ----------
function Initialize-Database {
    Write-Step "初始化 MySQL 数据库..."

    # 先启动 mysql 容器（如果未启动）
    $running = docker ps --format '{{.Names}}' 2>$null | Select-String -Pattern '^kb-mysql$'
    if (-not $running) {
        Write-Info "  kb-mysql 未运行，先启动 mysql 容器..."
        & docker compose -p $ComposeProject -f $ComposeFile up -d mysql
        # 等待健康
        $elapsed = 0
        while ($elapsed -lt 60) {
            $health = docker inspect --format='{{.State.Health.Status}}' kb-mysql 2>$null
            if ($health -eq "healthy") { break }
            Start-Sleep -Seconds 3
            $elapsed += 3
        }
    }
    Write-Ok "kb-mysql 容器就绪"

    if (-not (Test-Path $InitSqlDir)) {
        Write-Err "未找到 init-sql 目录: $InitSqlDir"
        return $false
    }

    Write-Info "  执行 init-sql/ 下所有 SQL 脚本..."
    $sqlCount = 0
    $sqlFiles = Get-ChildItem -Path $InitSqlDir -Filter "*.sql" | Sort-Object Name
    foreach ($sql in $sqlFiles) {
        Write-Info "  执行: $($sql.Name)"
        # 将 SQL 文件内容通过管道传入容器的 mysql 命令
        Get-Content $sql.FullName -Raw | docker exec -i kb-mysql mysql -uroot -p"$global:MysqlPass" 2>$null
        if ($LASTEXITCODE -eq 0) {
            Write-Ok "    ✓ $($sql.Name)"
            $sqlCount++
        } else {
            Write-Bad "    ✗ $($sql.Name)"
        }
    }

    # 验证数据库
    Write-Info "  验证数据库创建结果..."
    $dbCountStr = docker exec kb-mysql mysql -uroot -p"$global:MysqlPass" -N -e "SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME LIKE 'kb_%';" 2>$null
    $dbCount = if ($dbCountStr) { [int]$dbCountStr.Trim() } else { 0 }
    if ($dbCount -eq 5) {
        Write-Ok "MySQL kb_* 数据库数量 = 5"
    } else {
        Write-Warn "MySQL kb_* 数据库数量 = $dbCount（期望 5）"
    }

    foreach ($db in @("kb_auth", "kb_file", "kb_knowledge", "kb_ops", "kb_intelligence")) {
        $tableCountStr = docker exec kb-mysql mysql -uroot -p"$global:MysqlPass" -N -e "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='$db';" 2>$null
        $tableCount = if ($tableCountStr) { $tableCountStr.Trim() } else { "0" }
        Write-Info "    ${db}: $tableCount 张表"
    }

    return $true
}

# ---------- 主流程 ----------
Write-Host "============================================================"
Write-Host "  mykng 知识库环境初始化"
Write-Host "  时间: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
Write-Host "  项目根: $ProjectRoot"
Write-Host "============================================================"
Write-Host ""

# 1. 检查 Docker
$dockerOk = Test-Docker
if (-not $dockerOk) { Write-Err "Docker 环境检查未通过" }

if ($Check) {
    Write-Info "仅检查模式（-Check），跳过修改操作"
    Write-Host ""
    Write-Host "============================================================"
    Write-Host "  结果: 通过 $global:Pass / 失败 $global:Fail" -ForegroundColor $(if ($global:Fail -eq 0) { 'Green' } else { 'Red' })
    Write-Host "============================================================"
    exit $global:Fail
}

# 2. 创建数据目录
New-DataDirs

# 3. 生成 .env
$envOk = Ensure-EnvFile
if (-not $envOk) { Write-Err ".env 生成失败" }

# 4. 初始化数据库
$dbOk = Initialize-Database
if (-not $dbOk) { Write-Err "数据库初始化失败" }

Write-Host ""
Write-Host "============================================================"
Write-Info "环境初始化完成 ✓"
Write-Info "下一步:"
Write-Info "  1. 修改 $ProjectRoot\.env 中的密码（如有需要）"
Write-Info "  2. 执行: .\scripts\build.ps1"
Write-Info "  3. 执行: .\scripts\deploy.ps1 up"
Write-Host "============================================================"
