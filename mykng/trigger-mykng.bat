@echo off
REM trigger-mykng.bat - 触发 mykng 项目流水线
REM 用法: trigger-mykng.bat [分支名]

set BRANCH=%1
if "%BRANCH%"=="" set BRANCH=dev

python "%~dp0\trigger-pipeline.py" mykng %BRANCH%
