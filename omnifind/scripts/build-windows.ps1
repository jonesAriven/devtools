# OmniFind Windows 打包脚本
# 作用：拉依赖 → 打包 omnifind.exe（Web + CLI） + omnifind-indexer.exe（SYSTEM 索引服务）
#
# 前置：Python 3.11+ 已装并在 PATH 里
#
# 用法：
#   powershell -ExecutionPolicy Bypass -File .\scripts\build-windows.ps1
#
# 产物：
#   dist\omnifind\omnifind.exe            用户态 Web + CLI
#   dist\omnifind\omnifind-indexer.exe    SYSTEM 索引服务

param(
    [switch]$SkipInstall  # 已装好依赖时跳过 pip install
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

Write-Host "==> OmniFind Windows 打包" -ForegroundColor Cyan
Write-Host "    项目根: $root"

# 1. 装依赖
if (-not $SkipInstall) {
    Write-Host "==> 安装 Python 依赖" -ForegroundColor Cyan
    python -m pip install --upgrade pip
    python -m pip install -r requirements.txt
    python -m pip install pyinstaller
}

# 2. 清理旧产物
if (Test-Path build)  { Remove-Item -Recurse -Force build }
if (Test-Path dist)   { Remove-Item -Recurse -Force dist }

# 3. 打包 omnifind.exe（用户态：Web + CLI）
Write-Host "==> 打包 omnifind.exe" -ForegroundColor Cyan
python -m PyInstaller `
    --name omnifind `
    --onedir `
    --console `
    --clean `
    --noconfirm `
    --distpath dist `
    --workpath build `
    --hidden-import omnifind.extractors.plaintext `
    --hidden-import omnifind.extractors.office `
    --hidden-import omnifind.extractors.pdf `
    --hidden-import omnifind.layers.l1_filename.usn_backend `
    --hidden-import omnifind.layers.l1_filename.index `
    --hidden-import omnifind.layers.l2_fulltext.index `
    --hidden-import omnifind.layers.l3_semantic.index `
    --hidden-import omnifind.layers.l3_semantic.builder `
    --hidden-import omnifind.layers.l3_semantic.embedder `
    --hidden-import omnifind.web.server `
    --collect-data jieba `
    --collect-data onnxruntime `
    --collect-data lancedb `
    --collect-data tokenizers `
    --add-data "config.yaml;." `
    --add-data "models/bge-small-zh-v1.5;models/bge-small-zh-v1.5" `
    omnifind\__main__.py

# 4. 打包 omnifind-indexer.exe（SYSTEM 索引服务）
Write-Host "==> 打包 omnifind-indexer.exe" -ForegroundColor Cyan
python -m PyInstaller `
    --name omnifind-indexer `
    --onedir `
    --console `
    --clean `
    --noconfirm `
    --distpath dist `
    --workpath build `
    --hidden-import omnifind.layers.l1_filename.usn_backend `
    --hidden-import omnifind.layers.l1_filename.index `
    --hidden-import omnifind.core.config `
    --hidden-import omnifind.core.indexer `
    --hidden-import omnifind.layers.l3_semantic.index `
    --hidden-import omnifind.layers.l3_semantic.embedder `
    --hidden-import omnifind.layers.l3_semantic.builder `
    --collect-data onnxruntime `
    --collect-data lancedb `
    --collect-data tokenizers `
    --add-data "config.yaml;." `
    --add-data "models/bge-small-zh-v1.5;models/bge-small-zh-v1.5" `
    omnifind\service\indexer_service.py

# 5. 合并两份 dist 到 dist\omnifind（共享 site-packages 等大文件，省磁盘）
Write-Host "==> 合并产物到 dist\omnifind\" -ForegroundColor Cyan
$final = Join-Path $root "dist\omnifind"
$indexer = Join-Path $root "dist\omnifind-indexer"
if (Test-Path $indexer) {
    # 复制 indexer 目录里 omnifind 目录没有的文件
    Copy-Item -Path "$indexer\*" -Destination $final -Recurse -Force -ErrorAction SilentlyContinue
    # 保留 indexer.exe 主入口
    Copy-Item -Path "$indexer\omnifind-indexer.exe" -Destination "$final\omnifind-indexer.exe" -Force
    Remove-Item -Recurse -Force $indexer
}

Write-Host ""
Write-Host "==> 打包完成 ✅" -ForegroundColor Green
Write-Host "    产物目录：$final"
Write-Host ""
Write-Host "下一步："
Write-Host "  1. 移动/复制 dist\omnifind 到你想放的位置（如 D:\Tools\omnifind）"
Write-Host "  2. 编辑 %ProgramData%\omnifind\config.yaml 定义 scan_roots / semantic_dirs"
Write-Host "  3. 装服务：powershell -ExecutionPolicy Bypass -File scripts\install-service.ps1"
Write-Host "  4. 启动 Web：dist\omnifind\omnifind.exe serve"
