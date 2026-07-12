@echo off
REM ============================================================
REM trigger-active-manager.bat - 触发激活码系统流水线
REM ============================================================
REM 
REM 用法:
REM   trigger-active-manager.bat              触发并显示编号
REM   trigger-active-manager.bat "备注"        带备注触发
REM ============================================================

chcp 65001 >nul
setlocal

set "SCRIPT_DIR=%~dp0"
set "WOOD_SCRIPT=%SCRIPT_DIR%..\woodScript"
set "PYTHONIOENCODING=utf-8"
set "NOTE=%1"

if "%NOTE%"=="" (
    python "%WOOD_SCRIPT%\trigger-pipeline.py" active-manager dev
) else (
    python "%WOOD_SCRIPT%\trigger-pipeline.py" active-manager dev --note %NOTE%
)

endlocal
