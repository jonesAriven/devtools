@echo off
chcp 65001 >nul
set PYTHONIOENCODING=utf-8

echo 正在启动程序...
echo.

python qr_generator_v2.py
if %errorlevel% neq 0 (
    echo 程序运行出错！
    pause
    exit /b 1
)
