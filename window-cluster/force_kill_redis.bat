@echo off
title Force Kill Redis Processes and Free Ports Only

set REDIS_HOME=C:\Users\huliang1\myWork\software\Redis-Cluster
cd /d %REDIS_HOME%

echo ===============================================
echo    Force Kill Redis Processes and Free Ports Only
echo    (Preserving data and configuration files)
echo ===============================================
echo.

:: 显示清理前状态
echo [1/5] Current status before cleanup:
echo.

echo Redis processes:
tasklist /fi "imagename eq redis-server.exe" | find "redis-server.exe" >nul
if errorlevel 1 (
    echo No redis-server.exe processes found
) else (
    tasklist /fi "imagename eq redis-server.exe"
)

echo.
echo Port status (7001-7006):
for %%p in (7001 7002 7003 7004 7005 7006) do (
    netstat -ano | findstr ":%%p " >nul
    if errorlevel 1 (
        echo Port %%p: FREE
    ) else (
        echo Port %%p: IN USE
    )
)

:: 第一步：杀死所有Redis相关进程
echo.
echo [2/5] Killing all Redis executable processes...
echo.

set killed_processes=0

:: 杀死redis-server进程
echo Killing redis-server processes...
taskkill /f /im redis-server.exe 2>nul
if errorlevel 1 (
    echo No redis-server.exe processes found
) else (
    echo redis-server processes terminated
    set /a killed_processes+=1
)

:: 杀死redis-cli进程
echo Killing redis-cli processes...
taskkill /f /im redis-cli.exe 2>nul
if errorlevel 1 (
    echo No redis-cli.exe processes found
) else (
    echo redis-cli processes terminated
    set /a killed_processes+=1
)

:: 第二步：强力释放所有Redis集群端口
echo.
echo [3/5] Force releasing Redis cluster ports (7001-7006)...
echo.

set ports_freed=0
for %%p in (7001 7002 7003 7004 7005 7006) do (
    echo --- Port %%p ---
    
    :: 检查端口是否被占用
    netstat -ano | findstr ":%%p " >nul
    if errorlevel 1 (
        echo Port %%p is already free
    ) else (
        :: 找到并杀死占用该端口的所有进程
        for /f "tokens=1,4,5" %%a in ('netstat -ano ^| findstr ":%%p "') do (
            if not "%%c"=="" (
                echo Killing process PID %%c using port %%p (Protocol: %%a)
                taskkill /f /pid %%c 2>nul
                if errorlevel 1 (
                    echo Failed to kill process PID %%c, trying WMIC...
                    wmic process where "ProcessId=%%c" delete 2>nul
                ) else (
                    set /a ports_freed+=1
                    echo Successfully killed process PID %%c
                )
            )
        )
    )
    echo.
)

:: 第三步：检查并杀死可能残留的进程
echo [4/5] Checking for any remaining processes using Redis ports...
echo.

for %%p in (7001 7002 7003 7004 7005 7006) do (
    netstat -ano | findstr ":%%p " >nul
    if errorlevel 0 (
        echo WARNING: Port %%p is still in use!
        echo Detailed information:
        netstat -ano | findstr ":%%p "
        echo.
        
        :: 使用WMIC强力终止
        for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":%%p "') do (
            echo Using WMIC to force kill PID %%a...
            wmic process where "ProcessId=%%a" delete 2>nul
        )
    )
)

:: 第四步：最终状态验证
echo.
echo [5/5] Final status verification:
echo.

set all_ports_free=true
set remaining_redis_processes=0

echo Redis process check:
tasklist /fi "imagename eq redis-server.exe" | find "redis-server.exe" >nul
if errorlevel 1 (
    echo success No redis-server.exe processes found
) else (
    echo WARNING: redis-server processes still exist!
    tasklist /fi "imagename eq redis-server.exe"
    set all_ports_free=false
    set remaining_redis_processes=1
)

echo.
echo Port availability check:
for %%p in (7001 7002 7003 7004 7005 7006) do (
    echo -n Port %%p: 
    netstat -ano | findstr ":%%p " >nul
    if errorlevel 1 (
        echo FREE success
    ) else (
        echo IN USE error
        set all_ports_free=false
    )
)

echo.
echo ===============================================
echo Cleanup Summary:
echo - Processes killed: %killed_processes%
echo - Ports freed: %ports_freed%
echo.

if "%all_ports_free%"=="true" (
    echo   SUCCESS: All Redis processes killed and ports freed!
    echo   Data and configuration files are preserved.
) else (
    echo   WARNING: Some processes or ports may still be active!
    echo.
    echo Recommendations:
    echo 1. Run this script as Administrator
    echo 2. Reboot if problems persist
)
echo ===============================================

echo.
pause