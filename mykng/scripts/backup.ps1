# ============================================================
# mykng 知识库数据库备份脚本（Windows PowerShell 版本）
# ============================================================
# 用法:
#   .\scripts\backup.ps1                  # 立即备份
#   .\scripts\backup.ps1 -Verify          # 验证最近一次备份
#   .\scripts\backup.ps1 -List            # 列出所有备份
#   .\scripts\backup.ps1 -Clean           # 清理 7 天前备份
#   .\scripts\backup.ps1 -Help            # 显示帮助
#
# 备份路径:
#   $BackupRoot\mysql\<ts>\<db>.sql
#   $BackupRoot\mongodb\<ts>\mongodb.archive
# ============================================================

param(
    [switch]$Verify,
    [switch]$List,
    [switch]$Clean,
    [switch]$Help
)

$ErrorActionPreference = "Stop"

# ---------- 路径变量化 ----------
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Split-Path -Parent $ScriptDir

# Windows 备份目录（可改为网络共享路径）
$BackupRoot = "D:\kb-backup"
$MysqlBackupRoot = Join-Path $BackupRoot "mysql"
$MongoBackupRoot = Join-Path $BackupRoot "mongodb"
$LogFile = Join-Path $BackupRoot "backup.log"

New-Item -ItemType Directory -Force -Path $MysqlBackupRoot, $MongoBackupRoot | Out-Null

# 从 .env 读取密码
$mysqlPass = "kb123456"
$mongoUser = "kb"
$mongoPass = "kb123456"
$envFile = Join-Path $ProjectRoot ".env"
if (Test-Path $envFile) {
    Get-Content $envFile | ForEach-Object {
        if ($_ -match '^MYSQL_ROOT_PASSWORD=(.*)$') { $mysqlPass = $matches[1] }
        if ($_ -match '^MONGO_ROOT_USER=(.*)$') { $mongoUser = $matches[1] }
        if ($_ -match '^MONGO_ROOT_PASSWORD=(.*)$') { $mongoPass = $matches[1] }
    }
}

$mysqlDbs = @("kb_auth","kb_file","kb_knowledge","kb_ops","kb_intelligence")
$retentionDays = 7

# ---------- 颜色输出 ----------
function Write-Log  { param([string]$Msg) $ts = Get-Date -Format 'yyyy-MM-dd HH:mm:ss'; $line = "[$ts] [INFO ] $Msg"; Write-Host $line -ForegroundColor Green; Add-Content -Path $LogFile -Value $line }
function Write-Info { param([string]$Msg) Write-Host "[INFO ] $Msg" -ForegroundColor Cyan }
function Write-Warn { param([string]$Msg) Write-Host "[WARN ] $Msg" -ForegroundColor Yellow }
function Write-Err  { param([string]$Msg) Write-Host "[ERROR] $Msg" -ForegroundColor Red }

if ($Help) {
    Write-Host @"
mykng 知识库数据库备份脚本 (PowerShell)

用法:
  .\scripts\backup.ps1 [options]

参数:
  (无)      立即执行备份
  -Verify   验证最近一次备份
  -List     列出所有备份
  -Clean    清理 $retentionDays 天前的备份
  -Help     显示此帮助

备份路径:
  MySQL:  $MysqlBackupRoot\<ts>\<db>.sql
  MongoDB: $MongoBackupRoot\<ts>\mongodb.archive
"@
    exit 0
}

# ---------- 容器检查 ----------
function Test-Containers {
    $running = docker ps --format '{{.Names}}' 2>$null
    if ($running -notcontains "kb-mysql") {
        Write-Err "kb-mysql 容器未运行"
        exit 1
    }
    if ($running -notcontains "kb-mongo") {
        Write-Warn "kb-mongo 容器未运行，将跳过 MongoDB 备份"
        return $false
    }
    return $true
}

# ---------- MySQL 备份 ----------
function Backup-Mysql {
    param([string]$Ts)
    $backupDir = Join-Path $MysqlBackupRoot $Ts
    New-Item -ItemType Directory -Force -Path $backupDir | Out-Null

    Write-Log "=== MySQL 备份开始 (5 个库) ==="
    $ok = 0; $fail = 0
    foreach ($db in $mysqlDbs) {
        $file = Join-Path $backupDir "$db.sql"
        Write-Log "  导出 $db → $file"
        $args = @("exec","kb-mysql","mysqldump","-uroot","-p$mysqlPass","--single-transaction","--routines","--triggers","--events","--default-character-set=utf8mb4",$db)
        & docker @args 2>$null | Out-File -FilePath $file -Encoding utf8
        if ($LASTEXITCODE -eq 0 -and (Test-Path $file) -and ((Get-Item $file).Length -gt 0)) {
            $size = (Get-Item $file).Length
            Write-Log "    [OK] $db ($([math]::Round($size/1KB,1)) KB)"
            $ok++
        } else {
            Write-Err "    [FAIL] $db 备份失败"
            $fail++
            Remove-Item $file -ErrorAction SilentlyContinue
        }
    }
    Write-Log "MySQL 备份结束: 成功 $ok / 失败 $fail"
}

# ---------- MongoDB 备份 ----------
function Backup-Mongodb {
    param([string]$Ts)
    $backupDir = Join-Path $MongoBackupRoot $Ts
    New-Item -ItemType Directory -Force -Path $backupDir | Out-Null
    $file = Join-Path $backupDir "mongodb.archive"

    Write-Log "=== MongoDB 备份开始 ==="
    $uri = "mongodb://${mongoUser}:${mongoPass}@localhost:27017"
    & docker exec kb-mongo mongodump --quiet --uri=$uri --archive 2>$null | Out-File -FilePath $file -Encoding ascii
    if ($LASTEXITCODE -eq 0 -and (Test-Path $file) -and ((Get-Item $file).Length -gt 0)) {
        $size = (Get-Item $file).Length
        Write-Log "  [OK] MongoDB ($([math]::Round($size/1KB,1)) KB)"
    } else {
        Write-Err "  [FAIL] MongoDB 备份失败"
        Remove-Item $file -ErrorAction SilentlyContinue
    }
}

# ---------- 验证 ----------
function Verify-Latest {
    Write-Info "=== 验证最近一次备份 ==="

    $latestMysql = Get-ChildItem $MysqlBackupRoot -Directory | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if (-not $latestMysql) {
        Write-Warn "未找到 MySQL 备份"
    } else {
        Write-Info "MySQL 最近备份: $($latestMysql.FullName)"
        Get-ChildItem $latestMysql.FullName -Filter "*.sql" | ForEach-Object {
            $size = $_.Length
            $lines = (Get-Content $_.FullName | Measure-Object -Line).Lines
            $head = Get-Content $_.FullName -TotalCount 50 -ErrorAction SilentlyContinue
            if ($head -match "MySQL dump") {
                Write-Info "  [OK] $($_.Name) ($([math]::Round($size/1KB,1)) KB, $lines 行) - 文件头合法"
            } else {
                Write-Err "  [FAIL] $($_.Name) - 文件无效"
            }
        }
    }

    $latestMongo = Get-ChildItem $MongoBackupRoot -Directory | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if (-not $latestMongo) {
        Write-Warn "未找到 MongoDB 备份"
    } else {
        Write-Info "MongoDB 最近备份: $($latestMongo.FullName)"
        $arc = Join-Path $latestMongo.FullName "mongodb.archive"
        if (Test-Path $arc) {
            $size = (Get-Item $arc).Length
            if ($size -gt 0) {
                Write-Info "  [OK] mongodb.archive ($([math]::Round($size/1KB,1)) KB) - 非空"
            } else {
                Write-Err "  [FAIL] mongodb.archive 为空"
            }
        }
    }
}

# ---------- 列出 ----------
function List-Backups {
    Write-Info "=== MySQL 备份列表 ==="
    if (Test-Path $MysqlBackupRoot) {
        Get-ChildItem $MysqlBackupRoot -Directory | Sort-Object Name -Descending | ForEach-Object {
            $count = (Get-ChildItem $_.FullName -Filter "*.sql").Count
            $size = (Get-ChildItem $_.FullName | Measure-Object -Property Length -Sum).Sum
            Write-Host "  $($_.Name)  $([math]::Round($size/1MB,2)) MB  $count 个 SQL"
        }
    } else {
        Write-Warn "MySQL 备份目录不存在"
    }

    Write-Info "=== MongoDB 备份列表 ==="
    if (Test-Path $MongoBackupRoot) {
        Get-ChildItem $MongoBackupRoot -Directory | Sort-Object Name -Descending | ForEach-Object {
            $size = (Get-ChildItem $_.FullName | Measure-Object -Property Length -Sum).Sum
            Write-Host "  $($_.Name)  $([math]::Round($size/1MB,2)) MB"
        }
    } else {
        Write-Warn "MongoDB 备份目录不存在"
    }
}

# ---------- 清理 ----------
function Clean-Old {
    Write-Info "=== 清理 $retentionDays 天前的备份 ==="
    $cutoff = (Get-Date).AddDays(-$retentionDays)

    if (Test-Path $MysqlBackupRoot) {
        Get-ChildItem $MysqlBackupRoot -Directory | Where-Object { $_.LastWriteTime -lt $cutoff } | ForEach-Object {
            Write-Warn "  删除: $($_.FullName)"
            Remove-Item $_.FullName -Recurse -Force
        }
    }
    if (Test-Path $MongoBackupRoot) {
        Get-ChildItem $MongoBackupRoot -Directory | Where-Object { $_.LastWriteTime -lt $cutoff } | ForEach-Object {
            Write-Warn "  删除: $($_.FullName)"
            Remove-Item $_.FullName -Recurse -Force
        }
    }
    Write-Info "清理完成"
}

# ---------- 主流程 ----------
if ($Verify) { Verify-Latest; exit 0 }
if ($List) { List-Backups; exit 0 }
if ($Clean) { Clean-Old; exit 0 }

# 默认执行备份
$ts = Get-Date -Format "yyyyMMdd_HHmm"
Write-Log "============================================================"
Write-Log "  mykng 数据库备份开始"
Write-Log "  时间戳: $ts"
Write-Log "============================================================"

$mongoAvailable = Test-Containers
Backup-Mysql -Ts $ts
if ($mongoAvailable) {
    Backup-Mongodb -Ts $ts
}

Clean-Old

Write-Log "============================================================"
Write-Log "  备份完成"
Write-Log "  MySQL 路径:  $(Join-Path $MysqlBackupRoot $ts)"
Write-Log "  MongoDB 路径: $(Join-Path $MongoBackupRoot $ts)"
Write-Log "============================================================"
