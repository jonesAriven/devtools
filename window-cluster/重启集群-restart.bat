@echo off
title Force Create Redis Cluster

set REDIS_HOME=C:\Users\huliang1\myWork\software\Redis-Cluster
cd /d %REDIS_HOME%

echo ====================================
echo    Force Create Redis Cluster
echo ====================================
echo.

echo This will force cluster creation even if nodes have existing data
echo.

:: 首先清理集群配置
echo Cleaning existing cluster configuration...
for %%p in (7001 7002 7003 7004 7005 7006) do (
    if exist "node%%p\nodes.conf" del "node%%p\nodes.conf"
    if exist "node%%p\nodes-*.conf" del "node%%p\nodes-*.conf"
)

:: 重启节点以确保干净状态
echo Restarting nodes...
call force_kill_redis.bat >nul 2>&1
timeout /t 2 /nobreak >nul

for %%p in (7001 7002 7003 7004 7005 7006) do (
    start /B "Redis-Node-%%p" "%REDIS_HOME%\redis-server.exe" "node%%p\redis.conf"
    timeout /t 1 /nobreak >nul
)

echo Waiting for nodes to initialize...
timeout /t 8 /nobreak >nul

:: 使用--cluster-replicas的yes选项强制创建
echo Creating cluster with force option...
echo.

"%REDIS_HOME%\redis-cli.exe" --cluster create 127.0.0.1:7001 127.0.0.1:7002 127.0.0.1:7003 127.0.0.1:7004 127.0.0.1:7005 127.0.0.1:7006 --cluster-replicas 1 --cluster-yes -a test@123

if %errorlevel% equ 0 (
    echo.
    echo ====================================
    echo Cluster created successfully!
    echo ====================================
) else (
    echo.
    echo ====================================
    echo Cluster creation failed!
    echo ====================================
    echo Try manual cleanup steps:
    echo 1. Run 'force_kill_redis.bat'
    echo 2. Run this script again
    echo 3. try delete nodes-*.conf,dump.rdb, appendonlydir in this directory
)

pause