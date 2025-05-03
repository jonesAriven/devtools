@echo off
chcp 65001 >nul
cd /d "C:\frp"
title FRP客户端控制台

:: 检查是否以管理员身份运行
>nul 2>&1 "%SYSTEMROOT%\system32\cacls.exe" "%SYSTEMROOT%\system32\config\system"
if %errorlevel% neq 0 (
    echo 错误: 请右键以管理员身份运行此脚本!
    echo.
    pause
    exit /b 1
)

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
    :: 服务不存在，尝试注册服务
    echo 未检测到FRP客户端服务，正在尝试注册服务...
    
    :: 检查nssm.exe是否存在，如果不存在则下载
    if not exist "C:\frp\nssm.exe" (
        echo 未找到nssm.exe工具，正在下载...
        powershell -Command "& {Invoke-WebRequest -Uri 'https://nssm.cc/release/nssm-2.24.zip' -OutFile '%TEMP%\nssm.zip'}"
        powershell -Command "& {Expand-Archive -Path '%TEMP%\nssm.zip' -DestinationPath '%TEMP%\nssm'}"
        powershell -Command "& {Copy-Item -Path '%TEMP%\nssm\nssm-2.24\win64\nssm.exe' -Destination 'C:\frp\nssm.exe'}"
        del /q "%TEMP%\nssm.zip" >nul 2>&1
        rmdir /s /q "%TEMP%\nssm" >nul 2>&1
    )
    
    :: 使用nssm注册服务
    if exist "C:\frp\nssm.exe" (
        echo 正在注册FRP客户端服务...
        C:\frp\nssm.exe install frpc "C:\frp\frpc.exe" "-c C:\frp\frpc.ini"
        C:\frp\nssm.exe set frpc DisplayName "FRP客户端服务"
        C:\frp\nssm.exe set frpc Description "FRP内网穿透客户端服务"
        C:\frp\nssm.exe set frpc Start SERVICE_AUTO_START
        
        echo 正在启动FRP客户端服务...
        net start frpc
        if %errorlevel% equ 0 (
            echo FRP客户端服务已成功注册并启动!
            echo 程序将在后台运行，窗口将在2秒后关闭...
            timeout /t 2 >nul
            exit
        ) else (
            echo 服务启动失败，尝试直接运行程序...
            goto RunDirect
        )
    ) else (
        echo 无法注册服务，尝试直接运行程序...
        goto RunDirect
    )
)

:RunDirect
echo 未检测到FRP客户端服务，将直接运行程序...

echo 正在启动FRP客户端程序...

:: 修改启动命令，使用完整路径并避免引号嵌套问题
start /min cmd /c C:\frp\frpc.exe -c C:\frp\frpc.ini

:: 等待一小段时间再检查进程
timeout /t 1 >nul

:: 检查进程是否成功启动
tasklist /fi "imagename eq frpc.exe" | find "frpc.exe" > nul
if %errorlevel% equ 0 (
    echo FRP客户端程序已在后台启动!
    echo 程序将在后台运行，窗口将在2秒后关闭...
    timeout /t 2 >nul
) else (
    echo 启动失败，请检查程序是否存在!
    pause
    exit /b 1
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
