@echo off
chcp 65001 > nul 
setlocal enabledelayedexpansion

:: 检查管理员权限
>nul 2>&1 "%SYSTEMROOT%\system32\cacls.exe" "%SYSTEMROOT%\system32\config\system"
if %errorlevel% neq 0 (
    echo [警告] 需要管理员权限才能修改代理设置
    echo.
    echo [信息] 正在请求管理员权限...
    
    :: 使用PowerShell重新以管理员身份启动此脚本
    powershell -Command "Start-Process '%~dpnx0' -Verb RunAs"
    exit /b
)

echo ======================================================
echo               Windows 系统代理自动开启工具
echo ======================================================
echo.
echo 此工具用于开启Windows系统代理设置
echo.
echo 注意：此操作将永久保存在系统中
echo.

:: 显示当前系统代理设置
echo [信息] 正在获取当前系统代理配置...
echo.
echo [原始输出开始]
powershell -Command "Get-ItemProperty -Path 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Internet Settings' | Select-Object ProxyEnable, ProxyServer, ProxyOverride | Format-List"
echo [原始输出结束]
echo.

:: 获取当前代理状态
for /f "tokens=*" %%a in ('powershell -Command "(Get-ItemProperty -Path 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Internet Settings' -Name ProxyEnable).ProxyEnable"') do (
    set "proxyEnabled=%%a"
)

:: 获取当前代理服务器地址
for /f "tokens=*" %%a in ('powershell -Command "(Get-ItemProperty -Path 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Internet Settings' -Name ProxyServer -ErrorAction SilentlyContinue).ProxyServer"') do (
    set "proxyServer=%%a"
)

if "!proxyServer!"=="" (
    echo [错误] 未设置代理服务器地址，无法开启系统代理
    echo [提示] 请先在系统设置中配置代理服务器地址
    goto end
)

if "!proxyEnabled!"=="1" (
    echo [信息] 系统代理当前已经处于开启状态
    echo [提示] 无需进行任何操作
) else (
    echo [操作] 正在开启系统代理...
    powershell -Command "Set-ItemProperty -Path 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Internet Settings' -Name ProxyEnable -Value 1"
    
    if %errorlevel% equ 0 (
        echo.
        echo [成功] 系统代理已成功开启
        echo [信息] 当前代理服务器地址: !proxyServer!
        echo [提示] 此设置已永久保存在系统中
    ) else (
        echo.
        echo [错误] 开启代理设置失败，错误代码: %errorlevel%
    )
)

:end
echo.
echo ======================================================
echo 按任意键退出...
pause >nul