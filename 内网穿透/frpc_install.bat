@echo off
:: 设置控制台编码为 UTF-8
chcp 65001 >nul
setlocal enabledelayedexpansion

:: 设置日志文件
set LOG_FILE=%TEMP%\frpc_install.log
echo FRP客户端安装日志 - %date% %time% > %LOG_FILE%

:: 检查管理员权限
echo ▄ 检查管理员权限...
net session >nul 2>&1
if %errorlevel% neq 0 (
    echo 错误: 必须以管理员身份运行此脚本! >> %LOG_FILE%
    echo 请右键点击此脚本，选择"以管理员身份运行"
    pause
    exit /b 1
)
echo ✔️ 管理员权限检查通过 >> %LOG_FILE%

:: 设置安装目录
set INSTALL_DIR=C:\frp
echo ▄ 安装目录: %INSTALL_DIR% >> %LOG_FILE%

:: 卸载旧版本
echo ▄ 检查并卸载旧版本...
if exist "%INSTALL_DIR%" (
    echo 发现旧版本，正在卸载... >> %LOG_FILE%
    
    :: 停止并删除服务
    sc query frpc >nul 2>&1
    if %errorlevel% equ 0 (
        echo 停止并删除FRP客户端服务... >> %LOG_FILE%
        sc stop frpc >nul 2>&1
        sc delete frpc >nul 2>&1
    )
    
    :: 删除旧文件
    rd /s /q "%INSTALL_DIR%" >nul 2>&1
    echo ✔️ 旧版本卸载完成 >> %LOG_FILE%
) else (
    echo 未发现旧版本 >> %LOG_FILE%
)

:: 创建安装目录
echo ▄ 创建安装目录...
mkdir "%INSTALL_DIR%" 2>nul
echo ✔️ 安装目录创建完成 >> %LOG_FILE%

:: 下载FRP客户端
echo ▄ 下载FRP客户端...
set DEFAULT_VERSION=v0.53.2
set TEMP_DIR=%TEMP%\frp_download
mkdir "%TEMP_DIR%" 2>nul

:: 尝试获取最新版本
echo 尝试获取最新版本... >> %LOG_FILE%
powershell -Command "$ErrorActionPreference = 'SilentlyContinue'; $version = (Invoke-RestMethod -Uri 'https://api.github.com/repos/fatedier/frp/releases/latest' -TimeoutSec 10).tag_name; if ($version) { echo $version } else { echo 'failed' }" > "%TEMP_DIR%\version.txt"
set /p VERSION=<"%TEMP_DIR%\version.txt"

if "%VERSION%"=="failed" (
    echo 无法获取最新版本，使用默认版本: %DEFAULT_VERSION% >> %LOG_FILE%
    set VERSION=%DEFAULT_VERSION%
) else (
    echo 成功获取最新版本: %VERSION% >> %LOG_FILE%
)

:: 下载FRP
set DOWNLOAD_URL=https://github.com/fatedier/frp/releases/download/%VERSION%/frp_%VERSION:~1%_windows_amd64.zip
echo 下载地址: %DOWNLOAD_URL% >> %LOG_FILE%

echo 正在下载FRP客户端...
powershell -Command "$ErrorActionPreference = 'SilentlyContinue'; Invoke-WebRequest -Uri '%DOWNLOAD_URL%' -OutFile '%TEMP_DIR%\frp.zip'" >nul 2>&1

if not exist "%TEMP_DIR%\frp.zip" (
    echo 下载失败，尝试使用固定版本 %DEFAULT_VERSION% >> %LOG_FILE%
    set FIXED_URL=https://github.com/fatedier/frp/releases/download/%DEFAULT_VERSION%/frp_%DEFAULT_VERSION:~1%_windows_amd64.zip
    powershell -Command "$ErrorActionPreference = 'SilentlyContinue'; Invoke-WebRequest -Uri '%FIXED_URL%' -OutFile '%TEMP_DIR%\frp.zip'" >nul 2>&1
    
    if not exist "%TEMP_DIR%\frp.zip" (
        echo 错误: 下载失败，请检查网络连接 >> %LOG_FILE%
        echo 下载失败，请检查网络连接或手动下载FRP客户端
        pause
        exit /b 1
    )
    set VERSION=%DEFAULT_VERSION%
)

echo ✔️ 下载完成 >> %LOG_FILE%

:: 解压文件
echo ▄ 解压文件...
powershell -Command "Expand-Archive -Path '%TEMP_DIR%\frp.zip' -DestinationPath '%TEMP_DIR%' -Force" >nul 2>&1
echo ✔️ 解压完成 >> %LOG_FILE%

:: 复制文件
echo ▄ 安装FRP客户端...
copy "%TEMP_DIR%\frp_%VERSION:~1%_windows_amd64\frpc.exe" "%INSTALL_DIR%\" >nul 2>&1
copy "%TEMP_DIR%\frp_%VERSION:~1%_windows_amd64\frpc_full.ini" "%INSTALL_DIR%\frpc_full.ini" >nul 2>&1
echo ✔️ 文件复制完成 >> %LOG_FILE%

:: 创建配置文件
echo ▄ 创建配置文件...
echo [common] > "%INSTALL_DIR%\frpc.ini"
echo server_addr = 120.26.66.182 >> "%INSTALL_DIR%\frpc.ini"
echo server_port = 7000 >> "%INSTALL_DIR%\frpc.ini"
echo token = YourStrongToken! >> "%INSTALL_DIR%\frpc.ini"
echo. >> "%INSTALL_DIR%\frpc.ini"
echo [rdp] >> "%INSTALL_DIR%\frpc.ini"
echo type = tcp >> "%INSTALL_DIR%\frpc.ini"
echo local_ip = 127.0.0.1 >> "%INSTALL_DIR%\frpc.ini"
echo local_port = 3389 >> "%INSTALL_DIR%\frpc.ini"
echo remote_port = 3381 >> "%INSTALL_DIR%\frpc.ini"
echo ✔️ 配置文件创建完成 >> %LOG_FILE%

:: 创建启动脚本
echo ▄ 创建启动脚本...
echo @echo off > "%INSTALL_DIR%\start_frpc.bat"
echo cd /d "%INSTALL_DIR%" >> "%INSTALL_DIR%\start_frpc.bat"
echo start /b frpc.exe -c frpc.ini >> "%INSTALL_DIR%\start_frpc.bat"
echo ✔️ 启动脚本创建完成 >> %LOG_FILE%

:: 创建服务
echo ▄ 注册系统服务...
:: 下载NSSM工具
echo 下载NSSM服务管理工具... >> %LOG_FILE%
powershell -Command "$ErrorActionPreference = 'SilentlyContinue'; Invoke-WebRequest -Uri 'https://nssm.cc/release/nssm-2.24.zip' -OutFile '%TEMP_DIR%\nssm.zip'" >nul 2>&1

if not exist "%TEMP_DIR%\nssm.zip" (
    echo 警告: NSSM下载失败，无法创建系统服务 >> %LOG_FILE%
    echo 警告: 无法下载NSSM工具，将跳过服务注册步骤
) else (
    powershell -Command "Expand-Archive -Path '%TEMP_DIR%\nssm.zip' -DestinationPath '%TEMP_DIR%' -Force" >nul 2>&1
    
    if exist "%TEMP_DIR%\nssm-2.24\win64\nssm.exe" (
        copy "%TEMP_DIR%\nssm-2.24\win64\nssm.exe" "%INSTALL_DIR%\" >nul 2>&1
        
        :: 使用NSSM创建服务
        "%INSTALL_DIR%\nssm.exe" install frpc "%INSTALL_DIR%\frpc.exe" "-c %INSTALL_DIR%\frpc.ini" >nul 2>&1
        "%INSTALL_DIR%\nssm.exe" set frpc DisplayName "FRP Client Service" >nul 2>&1
        "%INSTALL_DIR%\nssm.exe" set frpc Description "FRP内网穿透客户端服务" >nul 2>&1
        "%INSTALL_DIR%\nssm.exe" set frpc Start SERVICE_AUTO_START >nul 2>&1
        "%INSTALL_DIR%\nssm.exe" set frpc AppStdout "%INSTALL_DIR%\frpc.log" >nul 2>&1
        "%INSTALL_DIR%\nssm.exe" set frpc AppStderr "%INSTALL_DIR%\frpc_error.log" >nul 2>&1
        
        :: 启动服务
        net start frpc >nul 2>&1
        echo ✔️ 服务注册并启动成功 >> %LOG_FILE%
    ) else (
        echo 警告: NSSM解压失败，无法创建系统服务 >> %LOG_FILE%
        echo 警告: NSSM工具解压失败，将跳过服务注册步骤
    )
)

:: 配置防火墙
echo ▄ 配置防火墙规则...
netsh advfirewall firewall add rule name="FRP Client" dir=in action=allow program="%INSTALL_DIR%\frpc.exe" enable=yes >nul 2>&1
netsh advfirewall firewall add rule name="FRP Client" dir=out action=allow program="%INSTALL_DIR%\frpc.exe" enable=yes >nul 2>&1
echo ✔️ 防火墙规则配置完成 >> %LOG_FILE%

:: 创建桌面快捷方式
echo ▄ 创建桌面快捷方式...
powershell -Command "$WshShell = New-Object -ComObject WScript.Shell; $Shortcut = $WshShell.CreateShortcut([System.Environment]::GetFolderPath('Desktop') + '\FRP客户端.lnk'); $Shortcut.TargetPath = '%INSTALL_DIR%\start_frpc.bat'; $Shortcut.WorkingDirectory = '%INSTALL_DIR%'; $Shortcut.Save()" >nul 2>&1
echo ✔️ 桌面快捷方式创建完成 >> %LOG_FILE%

:: 清理临时文件
echo ▄ 清理临时文件...
rd /s /q "%TEMP_DIR%" >nul 2>&1
echo ✔️ 临时文件清理完成 >> %LOG_FILE%

:: 显示安装信息
echo.
echo ===================================================
echo          FRP客户端安装完成 (版本: %VERSION%)
echo ===================================================
echo.
echo 安装信息:
echo  - 安装目录: %INSTALL_DIR%
echo  - 配置文件: %INSTALL_DIR%\frpc.ini
echo  - 服务名称: frpc
echo.
echo 重要提示:
echo  1. 请修改配置文件中的服务器地址和Token:
echo     %INSTALL_DIR%\frpc.ini
echo.
echo  2. 修改配置后重启服务:
echo     net stop frpc
echo     net start frpc
echo.
echo  3. 如需手动启动:
echo     - 使用桌面快捷方式
echo     - 或运行 %INSTALL_DIR%\start_frpc.bat
echo.
echo  4. 日志文件位置:
echo     - 安装日志: %LOG_FILE%
echo     - 运行日志: %INSTALL_DIR%\frpc.log
echo.
echo ===================================================
echo.

pause
endlocal