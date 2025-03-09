@echo off
chcp 65001 >nul
setlocal EnableDelayedExpansion

title .NET SDK 环境诊断

echo ============================================
echo          .NET SDK 环境诊断工具
echo ============================================
echo.

echo 1. 检查系统环境变量 PATH...
echo %PATH% > env_path.txt
echo 环境变量已保存到 env_path.txt

echo.
echo 2. 检查.NET SDK安装位置...
dir "C:\Program Files\dotnet" /b /s > dotnet_files_x64.txt 2>nul
dir "C:\Program Files (x86)\dotnet" /b /s > dotnet_files_x86.txt 2>nul

echo.
echo 3. 尝试直接调用dotnet...
"C:\Program Files\dotnet\dotnet.exe" --version > dotnet_x64_version.txt 2>&1
"C:\Program Files (x86)\dotnet\dotnet.exe" --version > dotnet_x86_version.txt 2>&1

echo.
echo 4. 检查注册表信息...
reg query "HKLM\SOFTWARE\dotnet" /s > dotnet_reg.txt 2>&1
reg query "HKLM\SOFTWARE\WOW6432Node\dotnet" /s >> dotnet_reg.txt 2>&1

echo.
echo 诊断信息已保存到以下文件：
echo - env_path.txt （环境变量）
echo - dotnet_files_x64.txt （64位安装文件）
echo - dotnet_files_x86.txt （32位安装文件）
echo - dotnet_x64_version.txt （64位版本信息）
echo - dotnet_x86_version.txt （32位版本信息）
echo - dotnet_reg.txt （注册表信息）
echo.

echo 建议的修复步骤：
echo 1. 卸载所有现有的.NET SDK
echo    - 使用控制面板卸载
echo    - 或使用 Visual Studio Installer 卸载
echo.
echo 2. 清理.NET相关目录
echo    - 删除 C:\Program Files\dotnet
echo    - 删除 C:\Program Files (x86)\dotnet
echo.
echo 3. 重新安装.NET SDK 6.0
echo    - 从官方网站下载：https://dotnet.microsoft.com/download/dotnet/6.0
echo    - 下载 x64 版本的 SDK
echo.
echo 4. 重启电脑
echo.
echo 5. 再次运行此脚本检查环境
echo.

pause 