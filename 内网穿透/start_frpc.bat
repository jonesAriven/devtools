@echo off
chcp 65001 >nul
cd /d "C:\frp"

echo 正在检查并关闭已存在的 frpc 进程...
tasklist /fi "imagename eq frpc.exe" | find "frpc.exe" > nul
if %errorlevel% equ 0 (
    echo 发现正在运行的 frpc 进程，正在关闭...
    taskkill /f /im frpc.exe > nul 2>&1
    timeout /t 1 > nul
)

echo 正在启动 frpc 服务...
start /b frpc.exe -c frpc.ini
echo frpc 服务已启动！
