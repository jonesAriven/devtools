@echo off
REM trigger-infra-monitor.bat - 触发 infra-monitor 项目流水线
REM 用法: trigger-infra-monitor.bat [分支名]

set BRANCH=%1
if "%BRANCH%"=="" set BRANCH=dev

python "%~dp0\..\woodScript\trigger-pipeline.py" infra-monitor %BRANCH%
