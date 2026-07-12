@echo off
REM trigger-portal-server.bat - 触发 portal-server 项目流水线
REM 用法: trigger-portal-server.bat [分支名]

set BRANCH=%1
if "%BRANCH%"=="" set BRANCH=dev

python "%~dp0\..\woodScript\trigger-pipeline.py" portal-server %BRANCH%
