@echo off
REM trigger-kb-ops.bat - 触发 kb-ops 项目流水线
REM 用法: trigger-kb-ops.bat [分支名]

set BRANCH=%1
if "%BRANCH%"=="" set BRANCH=dev

python "%~dp0\..\woodScript\trigger-pipeline.py" kb-ops %BRANCH%
