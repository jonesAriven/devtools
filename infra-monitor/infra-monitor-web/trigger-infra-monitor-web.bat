@echo off
REM trigger-infra-monitor-web.bat - 触发 infra-monitor-web 项目流水线
REM 用法: trigger-infra-monitor-web.bat [分支名]

set BRANCH=%1
if "%BRANCH%"=="" set BRANCH=dev

python "%~dp0\..\..\woodScript\trigger-pipeline.py" infra-monitor-web %BRANCH%
