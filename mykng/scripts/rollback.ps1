# ============================================================
# mykng 知识库微服务回滚脚本（Windows PowerShell 版本，SOP附录G要求）
# ============================================================
# 用法:
#   .\scripts\rollback.ps1 <service>           # 回滚单个服务到上一版本镜像
#   .\scripts\rollback.ps1 <service> <tag>     # 回滚到指定镜像 tag
#   .\scripts\rollback.ps1 all                 # 回滚所有服务（含数据备份）
#   .\scripts\rollback.ps1 -Help               # 显示帮助
#
# 镜像版本策略:
#   - 每次构建会生成 kb-<svc>:latest 与 kb-<svc>:<timestamp> 两个 tag
#   - 回滚单服务: 使用上一 timestamp tag 重新 up -d --no-deps
#   - 回滚所有: 先备份 5 个数据库，再逐个服务回滚到上一 tag
# ============================================================

param(
    [Parameter(Position = 0)]
    [string]$Service = "",
    [Parameter(Position = 1)]
    [string]$Tag = "",
    [switch]$Help
)

$ErrorActionPreference = "Stop"

# ---------- 路径变量化 ----------
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Split-Path -Parent $ScriptDir
$ComposeProject = "kb-deploy"
$ComposeFile = Join-Path $ProjectRoot "docker-compose.yml"
$BackupRoot = "/data/backup/mysql"

Set-Location $ProjectRoot

# 从 .env 读取 MySQL 密码（兼容默认值）
$global:MysqlPass = if ($env:MYSQL_ROOT_PASSWORD) { $env:MYSQL_ROOT_PASSWORD } else { "kb123456" }
$envFile = Join-Path $ProjectRoot ".env"
if (Test-Path $envFile) {
    Get-Content $envFile | ForEach-Object {
        if ($_ -match '^\s*MYSQL_ROOT_PASSWORD\s*=\s*(.+)\s*$') {
            $global:MysqlPass = $matches[1].Trim()
        }
    }
}

# ---------- 颜色输出 ----------
function Write-Info { param([string]$Msg) Write-Host "[INFO ] $Msg" -ForegroundColor Green }
function Write-Step { param([string]$Msg) Write-Host "[STEP ] $Msg" -ForegroundColor Cyan }
function Write-Warn { param([string]$Msg) Write-Host "[WARN ] $Msg" -ForegroundColor Yellow }
function Write-Err  { param([string]$Msg) Write-Host "[ERROR] $Msg" -ForegroundColor Red }

# 服务到数据库的映射
$SvcDbMap = @{
    "kb-auth"         = "kb_auth"
    "kb-file"         = "kb_file"
    "kb-knowledge"    = "kb_knowledge"
    "kb-intelligence" = "kb_intelligence"
}

$AllServices = @("kb-gateway", "kb-auth", "kb-file", "kb-knowledge", "kb-intelligence")

# ---------- 帮助 ----------
function Show-Help {
    Write-Host @"
mykng 知识库微服务回滚脚本 (PowerShell)

用法:
  .\scripts\rollback.ps1 <service> [tag]
  .\scripts\rollback.ps1 all

参数:
  service   服务名: kb-gateway / kb-auth / kb-file / kb-knowledge / kb-intelligence / all
  tag       可选镜像 tag（默认使用上一 timestamp 版本）

示例:
  .\scripts\rollback.ps1 kb-intelligence
  .\scripts\rollback.ps1 kb-auth 20260628_103000
  .\scripts\rollback.ps1 all
"@
}

# ---------- 备份数据库 ----------
function Backup-Db {
    param([string]$DbName)
    $ts = Get-Date -Format "yyyyMMdd_HHmmss"
    $backupDir = "$BackupRoot/${ts}_rollback"
    # 在容器内创建备份目录（Linux 路径），通过 docker exec 执行
    & docker exec kb-mysql mkdir -p $backupDir 2>$null
    $backupFile = "$backupDir/$DbName.sql"

    Write-Info "备份数据库 $DbName → $backupFile ..."
    $dumpCmd = "mysqldump -uroot -p`"$global:MysqlPass`" --single-transaction $DbName > $backupFile 2>/dev/null"
    & docker exec kb-mysql sh -c $dumpCmd
    if ($LASTEXITCODE -eq 0) {
        Write-Info "  ✓ 备份完成"
        return $true
    } else {
        Write-Err "  ✗ 备份失败: $DbName"
        return $false
    }
}

# ---------- 列出镜像历史版本 ----------
function List-ImageTags {
    param([string]$Svc)
    $imgName = "kb-deploy-$Svc"
    Write-Info "$Svc 的镜像历史版本:"
    & docker images --format "{{.Tag}}`t{{.CreatedAt}}`t{{.Size}}" $imgName | Where-Object { $_ -notmatch "<none>" } | Select-Object -First 20
    Write-Host ""
}

# ---------- 找到上一版本 tag ----------
function Get-PreviousTag {
    param([string]$Svc)
    $imgName = "kb-deploy-$Svc"
    $tags = & docker images --format "{{.Tag}}" $imgName 2>$null | Where-Object { $_ -ne "<none>" -and $_ -ne "latest" }
    # 跳过第一个（最新 timestamp），取第二个（上一 timestamp 版本）
    if ($tags -and $tags.Count -ge 2) {
        return $tags[1]
    }
    return $null
}

# ---------- 回滚单个服务 ----------
function Invoke-RollbackOne {
    param([string]$Svc, [string]$TargetTag = "")

    if (-not $TargetTag) {
        $TargetTag = Get-PreviousTag -Svc $Svc
        if (-not $TargetTag) {
            Write-Warn "$Svc 没有上一版本镜像，列出当前可用 tag:"
            List-ImageTags -Svc $Svc
            Write-Warn "可手动指定 tag: .\scripts\rollback.ps1 $Svc <tag>"
            return $false
        }
    }

    Write-Step "回滚 $Svc 到镜像 tag=$TargetTag ..."

    # 备份对应数据库（gateway 无数据库）
    if ($SvcDbMap.ContainsKey($Svc)) {
        $ok = Backup-Db -DbName $SvcDbMap[$Svc]
        if (-not $ok) { Write-Warn "数据库备份失败，继续回滚" }
    }

    # 重新拉起服务（--no-deps 不影响依赖）
    $imgName = "kb-deploy-$Svc"
    Write-Info "切换镜像: ${imgName}:$TargetTag"
    & docker tag "${imgName}:$TargetTag" "${imgName}:rollback-prev"
    & docker compose -p $ComposeProject -f $ComposeFile up -d --no-deps $Svc
    if ($LASTEXITCODE -ne 0) {
        Write-Err "回滚 $Svc 失败"
        return $false
    }
    Write-Info "  ✓ $Svc 已回滚到 $TargetTag"
    return $true
}

# ---------- 回滚所有服务 ----------
function Invoke-RollbackAll {
    Write-Warn "即将回滚所有服务到上一版本镜像"
    Write-Warn "将先备份全部 5 个数据库到 $BackupRoot"
    $confirm = Read-Host "确认回滚？(yes/no)"
    if ($confirm -ne "yes") {
        Write-Info "已取消"
        exit 0
    }

    Write-Step "[1/2] 备份所有数据库..."
    foreach ($db in @("kb_auth", "kb_file", "kb_knowledge", "kb_ops", "kb_intelligence")) {
        $ok = Backup-Db -DbName $db
        if (-not $ok) { Write-Err "备份 $db 失败" }
    }

    Write-Step "[2/2] 逐个回滚服务..."
    foreach ($svc in $AllServices) {
        $ok = Invoke-RollbackOne -Svc $svc
        if (-not $ok) { Write-Warn "$svc 回滚失败，继续下一个" }
    }

    Write-Info "所有服务回滚完成 ✓"
}

# ---------- 入口 ----------
if ($Help -or $Service -in @("-help", "--help", "-h") -or -not $Service) {
    Show-Help
    if (-not $Service -and -not $Help) { exit 1 }
    exit 0
}

switch ($Service) {
    "all" {
        Invoke-RollbackAll
    }
    { $_ -in @("kb-gateway", "kb-auth", "kb-file", "kb-knowledge", "kb-intelligence") } {
        Invoke-RollbackOne -Svc $Service -TargetTag $Tag | Out-Null
    }
    default {
        Write-Err "未知服务: $Service"
        Show-Help
        exit 1
    }
}

Write-Host ""
Write-Info "当前服务状态:"
& docker compose -p $ComposeProject -f $ComposeFile ps
