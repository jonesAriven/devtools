@echo off
REM trigger-all.bat - 触发所有项目流水线
REM 用法: trigger-all.bat [分支名]

set BRANCH=%1
if "%BRANCH%"=="" set BRANCH=dev

python "%~dp0\trigger-pipeline.py" all %BRANCH%
