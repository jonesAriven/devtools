# ============================================================
# mykng 知识库微服务构建脚本（Windows PowerShell 版本）
# ============================================================
# 用法:
#   .\scripts\build.ps1                          # Maven 编译 + Docker 构建所有
#   .\scripts\build.ps1 -Service kb-intelligence # 仅构建单个服务
#   .\scripts\build.ps1 -NoCache                # 不使用缓存
#   .\scripts\build.ps1 -SkipMvn                # 跳过 Maven 编译
# ============================================================

param(
    [string]$Service = "",
    [switch]$NoCache,
    [switch]$SkipMvn,
    [switch]$Help
)

$ErrorActionPreference = "Stop"

# ---------- 路径变量化 ----------
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Split-Path -Parent $ScriptDir
$ComposeProject = "kb-deploy"
$ComposeFile = Join-Path $ProjectRoot "docker-compose.yml"
$ParentDir = Join-Path $ProjectRoot "kb-parent"

Set-Location $ProjectRoot

# ---------- 颜色输出 ----------
function Write-Info { param([string]$Msg) Write-Host "[INFO ] $Msg" -ForegroundColor Green }
function Write-Step { param([string]$Msg) Write-Host "[STEP ] $Msg" -ForegroundColor Cyan }
function Write-Warn { param([string]$Msg) Write-Host "[WARN ] $Msg" -ForegroundColor Yellow }
function Write-Err  { param([string]$Msg) Write-Host "[ERROR] $Msg" -ForegroundColor Red }

if ($Help) {
    Write-Host @"
mykng 知识库微服务构建脚本 (PowerShell)

用法:
  .\scripts\build.ps1 [options]

参数:
  -Service <name>  指定单个服务构建（如 kb-intelligence）
  -NoCache          不使用 Docker 缓存
  -SkipMvn          跳过 Maven 编译步骤
  -Help             显示此帮助

服务名: kb-gateway / kb-auth / kb-file / kb-knowledge / kb-intelligence
"@
    exit 0
}

# ---------- Maven 编译 ----------
if (-not $SkipMvn) {
    if (-not (Test-Path $ParentDir)) {
        Write-Err "未找到 kb-parent 目录: $ParentDir"
        exit 1
    }
    $mvn = Get-Command mvn -ErrorAction SilentlyContinue
    if (-not $mvn) {
        Write-Err "未找到 mvn 命令，请安装 Maven 或使用 -SkipMvn"
        exit 1
    }
    Write-Step "[Maven] 编译 kb-parent 全工程..."
    Set-Location $ParentDir
    & mvn clean package -DskipTests -q
    if ($LASTEXITCODE -ne 0) {
        Write-Err "Maven 编译失败"
        Set-Location $ProjectRoot
        exit 1
    }
    Set-Location $ProjectRoot
    Write-Info "Maven 编译完成"
} else {
    Write-Warn "已跳过 Maven 编译（-SkipMvn）"
}

# ---------- Docker 构建 ----------
$allServices = @("kb-gateway","kb-auth","kb-file","kb-knowledge","kb-intelligence")
$cacheArg = if ($NoCache) { "--no-cache" } else { "" }
$buildArgs = @("build")
if ($NoCache) { $buildArgs += "--no-cache" }

$startTs = Get-Date

if ($Service) {
    Write-Step "[Docker] 构建单个服务: $Service"
    & docker compose -p $ComposeProject -f $ComposeFile @buildArgs $Service
} else {
    Write-Step "[Docker] 构建所有微服务镜像..."
    & docker compose -p $ComposeProject -f $ComposeFile @buildArgs $allServices
}

if ($LASTEXITCODE -ne 0) {
    Write-Err "Docker 构建失败"
    exit 1
}

$duration = (Get-Date) - $startTs
Write-Host ""
Write-Info "构建完成 (耗时 $([int]$duration.TotalSeconds)s)"
Write-Host ""
Write-Info "镜像列表:"
docker images --format "table {{.Repository}}`t{{.Tag}}`t{{.Size}}`t{{.CreatedAt}}" | Select-String -Pattern "REPOSITORY|kb-"
