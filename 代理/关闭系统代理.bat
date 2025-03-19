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
echo               Windows 系统代理自动关闭工具
echo ======================================================
echo.
echo 此工具用于关闭Windows系统代理设置
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

:: 获取当前代理排除地址
for /f "tokens=*" %%a in ('powershell -Command "(Get-ItemProperty -Path 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Internet Settings' -Name ProxyOverride -ErrorAction SilentlyContinue).ProxyOverride"') do (
    set "proxyOverride=%%a"
)

if defined proxyOverride (
    echo [信息] 当前系统代理排除列表: !proxyOverride!
) else (
    echo [信息] 当前未配置系统代理排除地址
    set "proxyOverride="
)

if "!proxyEnabled!"=="0" (
    echo [信息] 系统代理当前已经处于关闭状态
    
    :: 清除代理排除地址
    if defined proxyOverride (
        echo [操作] 正在清除代理排除地址...
        powershell -Command "Remove-ItemProperty -Path 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Internet Settings' -Name ProxyOverride -ErrorAction SilentlyContinue"
        
        if %errorlevel% equ 0 (
            echo [成功] 代理排除地址已成功清除
        ) else (
            echo [错误] 清除代理排除地址失败，错误代码: %errorlevel%
        )
    ) else (
        echo [提示] 代理排除地址已为空，无需清除
    )
    
    :: 清除代理服务器地址
    if "!proxyServer!"=="" (
        echo [提示] 代理服务器地址已为空，无需清除
    ) else (
        echo [操作] 正在清除代理服务器地址...
        powershell -Command "Remove-ItemProperty -Path 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Internet Settings' -Name ProxyServer -ErrorAction SilentlyContinue"
        
        if %errorlevel% equ 0 (
            echo [成功] 代理服务器地址已成功清除
        ) else (
            echo [错误] 清除代理服务器地址失败，错误代码: %errorlevel%
        )
    )
) else (
    echo [操作] 正在关闭系统代理并清除代理服务器地址...
    
    :: 关闭代理
    powershell -Command "Set-ItemProperty -Path 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Internet Settings' -Name ProxyEnable -Value 0"
    set "proxyDisabled=%errorlevel%"
    
    :: 清除代理服务器地址
    powershell -Command "Remove-ItemProperty -Path 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Internet Settings' -Name ProxyServer -ErrorAction SilentlyContinue"
    set "proxyServerCleared=%errorlevel%"
    
    if !proxyDisabled! equ 0 (
        echo.
        echo [成功] 系统代理已成功关闭
        
        if !proxyServerCleared! equ 0 (
            echo [成功] 代理服务器地址已成功清除
        ) else (
            echo [警告] 清除代理服务器地址失败，错误代码: !proxyServerCleared!
        )
        
        echo [提示] 此设置已永久保存在系统中
    ) else (
        echo.
        echo [错误] 关闭代理设置失败，错误代码: !proxyDisabled!
    )
)

:end
echo.
echo ======================================================
echo 代理已关闭并清除所有代理设置（包括代理服务器地址和排除地址），窗口将在 10 秒后自动关闭
timeout /t 10 /nobreak >nul
exit