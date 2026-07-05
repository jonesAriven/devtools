# ============================================================
# mykng 监控平台 - Windows 启动脚本 (PowerShell)
# ============================================================
# 用法：
#   .\start-monitoring.ps1 -Action Start
#
# 参数：
#   Action: Start | Stop | Restart | Status | Logs | Reset | Help
#   Service: 可选，指定服务名（仅 Logs 命令使用）
#
# 示例：
#   .\start-monitoring.ps1                    # 启动全部
#   .\start-monitoring.ps1 -Action Start      # 启动
#   .\start-monitoring.ps1 -Action Logs -Service grafana  # 查看日志
#   .\start-monitoring.ps1 -Action Status     # 查看状态
# ============================================================

param(
    [ValidateSet("Start", "Stop", "Restart", "Status", "Logs", "Reset", "Help")]
    [string]$Action = "Start",
    
    [string]$Service = ""
)

# 错误处理
$ErrorActionPreference = "Stop"

# 脚本所在目录
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ComposeFile = Join-Path $ScriptDir "docker-compose.monitoring.yml"

# 颜色函数
function Write-Success { param([string]$Msg) Write-Host "[✅] $Msg" -ForegroundColor Green }
function Write-WarningMsg { param([string]$Msg) Write-Host "[⚠️]  $Msg" -ForegroundColor Yellow }
function Write-ErrorMsg { param([string]$Msg) Write-Host "[❌] $Msg" -ForegroundColor Red }
function Write-Info { param([string]$Msg) Write-Host "[ℹ️]  $Msg" -ForegroundColor Cyan }

# ============================================================
# 辅助函数
# ============================================================

function Test-DockerInstalled {
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
        Write-ErrorMsg "Docker 未安装！请先安装 Docker Desktop。"
        return $false
    }
    return $true
}

function Test-DockerRunning {
    try {
        docker info | Out-Null
        return $true
    } catch {
        Write-ErrorMsg "Docker 服务未运行！请先启动 Docker Desktop。"
        return $false
    }
}

function Test-ComposeFile {
    if (-not (Test-Path $ComposeFile)) {
        Write-ErrorMsg "找不到配置文件: $ComposeFile"
        return $false
    }
    return $true
}

function Show-AccessInfo {
    # 获取本机 IP
    $ipAddress = (Get-NetIPAddress -AddressFamily IPv4 | Where-Object { $_.InterfaceAlias -notmatch "Loopback" }).IPAddress | Select-Object -First 1
    
    Write-Host ""
    Write-Success "监控平台已启动！"
    Write-Host ""
    Write-Host "  访问地址：" -ForegroundColor Blue
    Write-Host "    📊 Grafana 看板:    http://${ipAddress}:3000  (admin/admin)" -ForegroundColor Yellow
    Write-Host "    📈 Prometheus:    http://${ipAddress}:9090" -ForegroundColor Yellow
    Write-Host "    🔔 AlertManager:   http://${ipAddress}:9093" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "  推荐 Dashboard（在 Grafana 中导入）：" -ForegroundColor Blue
    Write-Host "    • 服务器监控:  ID 1860 (Node Exporter Full)"
    Write-Host "    • 容器监控:    ID 193 (Docker & Container Monitoring)"
    Write-Host "    • JVM 监控:     ID 4701 (JVM Micrometer)"
    Write-Host "    • 日志查看:     ID 13639 (Loki Logging)"
    Write-Host "    • 告警总览:     ID 9578 (AlertManager Overview)"
    Write-Host ""
    Write-Host "  快速开始：" -ForegroundColor Blue
    Write-Host "    1. 打开 Grafana (http://localhost:3000)"
    Write-Host "    2. 登录 (admin / admin)"
    Write-Host "    3. 左侧菜单 → Dashboards → Import"
    Write-Host "    4. 输入 Dashboard ID (如 1860) → Load"
    Write-Host "    5. 选择 Prometheus 数据源 → Import"
    Write-Host ""
}

# ============================================================
# 主逻辑
# ============================================================

Write-Host ""
Write-Host "══════════════════════════════════════════" -ForegroundColor Blue
Write-Host "  mykng 监控平台管理工具 (Windows)" -ForegroundColor Blue
Write-Host "══════════════════════════════════════════" -ForegroundColor Blue
Write-Host ""

switch ($Action) {

    # ----------------------------------------------------------
    # 启动服务
    # ----------------------------------------------------------
    "Start" {
        if (-not (Test-DockerInstalled)) { exit 1 }
        if (-not (Test-DockerRunning)) { exit 1 }
        if (-not (Test-ComposeFile)) { exit 1 }
        
        Write-Success "正在启动 mykng 监控平台..."
        Write-Host ""
        
        Push-Location $ScriptDir
        
        # 创建必要的目录
        @("config/grafana/provisioning/datasources",
          "config/grafana/provisioning/dashboards",
          "config/grafana/dashboards") | ForEach-Object {
            $dir = Join-Path $ScriptDir $_
            if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
        }
        
        # 启动服务
        docker compose -f $ComposeFile up -d
        
        # 等待服务就绪
        Write-Info "等待服务启动（约 10 秒）..."
        Start-Sleep -Seconds 10
        
        # 显示状态
        Write-Host ""
        Write-Success "检查服务状态："
        docker compose -f $ComposeFile ps
        
        Show-AccessInfo
        
        Pop-Location
    }

    # ----------------------------------------------------------
    # 停止服务
    # ----------------------------------------------------------
    "Stop" {
        if (-not (Test-DockerInstalled)) { exit 1 }
        if (-not (Test-ComposeFile)) { exit 1 }
        
        Write-WarningMsg "正在停止 mykng 监控平台..."
        
        Push-Location $ScriptDir
        docker compose -f $ComposeFile down
        Pop-Location
        
        Write-Success "监控平台已停止。"
    }

    # ----------------------------------------------------------
    # 重启服务
    # ----------------------------------------------------------
    "Restart" {
        if (-not (Test-DockerInstalled)) { exit 1 }
        if (-not (Test-DockerRunning)) { exit 1 }
        if (-not (Test-ComposeFile)) { exit 1 }
        
        Write-WarningMsg "正在重启 mykng 监控平台..."
        
        Push-Location $ScriptDir
        docker compose -f $ComposeFile restart
        Pop-Location
        
        Start-Sleep -Seconds 5
        Write-Success "重启完成！"
        Show-AccessInfo
    }

    # ----------------------------------------------------------
    # 查看状态
    # ----------------------------------------------------------
    "Status" {
        if (-not (Test-DockerInstalled)) { exit 1 }
        if (-not (Test-ComposeFile)) { exit 1 }
        
        Push-Location $ScriptDir
        
        Write-Host "容器状态：" -ForegroundColor Blue
        docker compose -f $ComposeFile ps
        
        Write-Host ""
        Write-Host "资源使用情况：" -ForegroundColor Blue
        $containers = (docker compose -f $ComposeFile ps -q)
        if ($containers) {
            docker stats --no-stream --format "table {{.Name}}`t{{.CPerc}}`t{{.MemUsage}}`t{{.NetIO}}" $containers
        } else {
            Write-WarningMsg "没有运行中的容器"
        }
        
        Write-Host ""
        Write-Host "数据卷使用情况：" -ForegroundColor Blue
        docker volume ls --filter "name=kb-*"
        
        Pop-Location
    }

    # ----------------------------------------------------------
    # 查看日志
    # ----------------------------------------------------------
    "Logs" {
        if (-not (Test-DockerInstalled)) { exit 1 }
        if (-not (Test-ComposeFile)) { exit 1 }
        
        Push-Location $ScriptDir
        
        if ($Service) {
            Write-Success "查看 $Service 的最近 100 行日志："
            docker compose -f $ComposeFile logs --tail=100 -f $Service
        } else {
            Write-Success "查看所有服务的最近 50 行日志："
            docker compose -f $ComposeFile logs --tail=50
        }
        
        Pop-Location
    }

    # ----------------------------------------------------------
    # 重置（清除所有数据）
    # ----------------------------------------------------------
    "Reset" {
        Write-ErrorMsg "⚠️  此操作将清除所有监控数据和配置！"
        $confirm = Read-Host "确定要继续吗？(输入 YES 确认)"
        
        if ($confirm -eq "YES") {
            if (-not (Test-DockerInstalled)) { exit 1 }
            if (-not (Test-ComposeFile)) { exit 1 }
            
            Push-Location $ScriptDir
            
            # 停止并删除容器、网络、数据卷
            docker compose -f $ComposeFile down -v --remove-orphans
            
            # 删除命名卷
            @("kb-prometheus-data", "kb-grafana-data", "kb-loki-data", "kb-alertmanager-data") | ForEach-Object {
                docker volume rm $_ 2>$null
            }
            
            Pop-Location
            
            Write-Success "重置完成！所有数据已清除。"
            Write-Success "运行 '.\start-monitoring.ps1' 重新启动。"
        } else {
            Write-WarningMsg "操作已取消。"
        }
    }

    # ----------------------------------------------------------
    # 显示帮助
    # ----------------------------------------------------------
    "Help" {
        Write-Host "用法：.\start-monitoring.ps1 -Action <命令>" 
        Write-Host ""
        Write-Host "参数："
        Write-Host "  -Action: Start | Stop | Restart | Status | Logs | Reset | Help"
        Write-Host "  -Service: 服务名（仅 Logs 使用）"
        Write-Host ""
        Write-Host "示例："
        Write-Host "  .\start-monitoring.ps1                     # 启动全部"
        Write-Host "  .\start-monitoring.ps1 -Action Start       # 启动"
        Write-Host "  .\start-monitoring.ps1 -Action Logs -Service grafana  # 查看日志"
        Write-Host "  .\start-monitoring.ps1 -Action Status      # 查看状态"
        Write-Host ""
    }
}
