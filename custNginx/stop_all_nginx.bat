@echo off
:: 设置控制台编码为 UTF-8
chcp 65001 >nul

:: 查找并杀掉所有 Nginx 进程
taskkill /F /IM nginx.exe

echo 所有 Nginx 进程已停止。
pause