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
echo               Windows 系统代理排除地址设置工具
echo ======================================================
echo.
echo 此工具用于添加不经过系统代理服务器的IP地址或域名
echo.
echo 注意：此设置将永久保存在系统中
echo.

:: 显示当前系统代理设置
echo [信息] 正在获取当前系统代理配置...
echo.
echo [原始输出开始]
powershell -Command "Get-ItemProperty -Path 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Internet Settings' | Select-Object ProxyEnable, ProxyServer, ProxyOverride | Format-List"
echo [原始输出结束]
echo.

:: 获取用户输入
set /p "input=请输入要排除的IP或域名(多个请用分号;分隔): "

if "%input%"=="" (
    echo [错误] 未输入任何内容，操作取消
    goto end
)

:: 获取当前系统代理绕过列表
echo [信息] 正在处理输入...

:: 使用PowerShell获取当前ProxyOverride值
for /f "tokens=*" %%a in ('powershell -Command "(Get-ItemProperty -Path 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Internet Settings' -Name ProxyOverride -ErrorAction SilentlyContinue).ProxyOverride"') do (
    set "currentBypass=%%a"
)

if defined currentBypass (
    echo [信息] 当前系统代理绕过列表: !currentBypass!
) else (
    echo [信息] 当前未配置系统代理例外地址
    set "currentBypass="
)

:: 处理新的绕过列表
if "!currentBypass!"=="" (
    set "updatedBypass=%input%"
) else (
    set "updatedBypass=!currentBypass!;%input%"
    
    :: 清理多余分号
    set "updatedBypass=!updatedBypass:;;=;!"
)

echo [信息] 更新后的绕过列表: !updatedBypass!
echo.

:: 设置新的系统代理绕过列表
echo [操作] 正在更新系统代理配置...
powershell -Command "Set-ItemProperty -Path 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Internet Settings' -Name ProxyOverride -Value '!updatedBypass!'"

:: 确保系统代理已启用
for /f "tokens=*" %%a in ('powershell -Command "(Get-ItemProperty -Path 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Internet Settings' -Name ProxyEnable).ProxyEnable"') do (
    set "proxyEnabled=%%a"
)

if "!proxyEnabled!"=="0" (
    echo [信息] 系统代理当前未启用，设置已保存但未生效
)

if %errorlevel% equ 0 (
    echo.
    echo [成功] 系统代理排除地址已成功更新
    echo.
    echo [信息] 以下地址已添加到系统代理排除列表: %input%
    echo [提示] 此设置已永久保存在系统中
) else (
    echo.
    echo [错误] 更新代理设置失败，错误代码: %errorlevel%
)

:end
echo.
echo ======================================================
echo 按任意键退出...
pause >nul