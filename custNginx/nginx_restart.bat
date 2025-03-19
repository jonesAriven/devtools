@echo off
:: 设置控制台编码为 UTF-8
chcp 65001 >nul

:: 查找并杀掉所有 Nginx 进程
taskkill /F /IM nginx.exe

:: 等待一段时间，确保进程完全关闭
ping -n 3 127.0.0.1 >nul

:: 重启 Nginx，这里需要你根据实际情况修改 Nginx 的启动路径
start "" "D:\huliang\softWare\root-nginx-1.21.1\nginx.exe"

echo Nginx 已成功重启，窗口将在 10 秒后自动关闭
timeout /t 10 /nobreak >nul
exit
