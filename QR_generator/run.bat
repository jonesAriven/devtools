@echo off
chcp 65001 >nul
set PYTHONIOENCODING=utf-8

rem 设置Python路径包含lib目录和.libs目录
set PYTHONPATH=%~dp0lib;%~dp0lib\.libs;%PYTHONPATH%

echo 正在启动程序...
echo.

rem 运行Python程序并捕获错误
python qr_generator.py 2>error.log
if %errorlevel% neq 0 (
    echo 程序运行出错！错误信息：
    type error.log
    echo.
    echo 请检查以上错误信息，或联系开发者获取帮助。
    pause
    exit /b 1
)

del error.log >nul 2>nul 