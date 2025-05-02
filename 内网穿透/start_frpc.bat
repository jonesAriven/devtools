@echo off
chcp 65001 >nul
cd /d "C:\frp"

:: 检查并关闭已存在的 frpc 进程
tasklist /fi "imagename eq frpc.exe" | find "frpc.exe" > nul
if %errorlevel% equ 0 (
    echo 发现正在运行的 frpc 进程，正在关闭...
    taskkill /f /im frpc.exe > nul 2>&1
    timeout /t 1 > nul
)

:: 检查frpc服务是否存在
sc query frpc >nul 2>&1
if %errorlevel% equ 0 (
    echo 检测到FRP客户端服务，正在重启服务...
    net stop frpc >nul 2>&1
    timeout /t 2 >nul
    net start frpc >nul 2>&1
    if %errorlevel% equ 0 (
        echo FRP客户端服务已成功重启!
        echo 程序将在后台运行，窗口将在2秒后关闭...
        timeout /t 2 >nul
        exit
    ) else (
        echo 服务启动失败，尝试直接运行程序...
        goto RunDirect
    )
) else (
    :RunDirect
    echo 未检测到FRP客户端服务，将直接运行程序...
    
    :: 启动frpc程序（使用start命令并添加/min参数使其最小化运行）
    echo 正在启动FRP客户端程序...
    start /min "" "frpc.exe" -c frpc.ini
    
    echo FRP客户端已在后台启动，窗口将在2秒后关闭...
    timeout /t 2 >nul
    exit
)
title FRP客户端控制台

echo ===================================================
echo               FRP客户端控制程序
echo ===================================================
echo.

:: 检查是否以管理员身份运行
>nul 2>&1 "%SYSTEMROOT%\system32\cacls.exe" "%SYSTEMROOT%\system32\config\system"
if %errorlevel% neq 0 (
    echo 错误: 请右键以管理员身份运行此脚本!
    echo.
    pause
    exit /b 1
)

:: 检查frpc服务是否存在
sc query frpc >nul 2>&1
if %errorlevel% equ 0 (
    echo 检测到FRP客户端服务，正在重启服务...
    net stop frpc >nul 2>&1
    timeout /t 2 >nul
    net start frpc >nul 2>&1
    if %errorlevel% equ 0 (
        echo ✓ FRP客户端服务已成功重启!
    ) else (
        echo × 服务启动失败，尝试直接运行程序...
        goto RunDirect
    )
) else (
    :RunDirect
    echo 未检测到FRP客户端服务，将直接运行程序...
    
    :: 检查并关闭已存在的frpc进程
    tasklist /fi "imagename eq frpc.exe" | find "frpc.exe" >nul
    if %errorlevel% equ 0 (
        echo 发现正在运行的frpc进程，正在关闭...
        taskkill /f /im frpc.exe >nul 2>&1
        timeout /t 1 >nul
    )
    
    :: 启动frpc程序
    echo 正在启动FRP客户端程序...
    start /b "" "frpc.exe" -c frpc.ini
    if %errorlevel% equ 0 (
        echo ✓ FRP客户端程序已在后台启动!
    ) else (
        echo × 启动失败，请检查程序是否存在!
        pause
        exit /b 1
    )
)

echo.
echo ===================================================
echo  FRP客户端已成功启动，可以关闭此窗口
echo  如需查看日志，请打开 C:\frp\frpc.log
echo ===================================================
echo.

:: 等待3秒后自动关闭窗口
timeout /t 3 >nul
exit
