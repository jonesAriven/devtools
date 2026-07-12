@echo off
REM trigger-active-manager.bat - 触发 active-manager 项目流水线
REM 用法: trigger-active-manager.bat [分支名]

set BRANCH=%1
if "%BRANCH%"=="" set BRANCH=dev

python "%~dp0\..\woodScript\trigger-pipeline.py" active-manager %BRANCH%
