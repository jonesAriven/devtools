@echo off
REM ============================================================
REM trigger-active-manager.bat - 触发激活码系统流水线
REM ============================================================
REM 
REM 固定流水线编号: #176 (active-manager)
REM   - 日常重跑: 基于同一 commit 重复部署（代码不变）
REM   - 新代码部署: 需手动用 python 触发 --alias active-manager
REM
REM 用法:
REM   trigger-active-manager.bat              重跑 #176 (日常使用)
REM   trigger-active-manager.bat "修复bug"    重跑 #176 (带备注)
REM
REM 底层调用: woodScript/trigger-pipeline.py --rerun 176
REM ============================================================

chcp 65001 >nul
setlocal

set NOTE=%1
if "%NOTE%"=="" set NOTE=日常重跑

echo ============================================
echo   触发激活码系统流水线 (固定 #176)
echo ============================================
echo.

python "%~dp0\..\woodScript\trigger-pipeline.py" --rerun 176 --note %NOTE%

endlocal
