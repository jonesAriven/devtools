@echo off
REM trigger-portal-web.bat - 触发 portal-web 项目流水线
REM 用法: trigger-portal-web.bat [分支名]

set BRANCH=%1
if "%BRANCH%"=="" set BRANCH=dev

python "%~dp0\..\woodScript\trigger-pipeline.py" portal-web %BRANCH%
