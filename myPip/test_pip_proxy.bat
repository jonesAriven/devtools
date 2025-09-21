@echo off
REM 测试pip缓存代理功能的批处理脚本
REM 使用方法：双击运行或在命令提示符中执行

SETLOCAL ENABLEDELAYEDEXPANSION

REM 配置服务器信息
SET "PYPI_SERVER_IP=服务器IP"  REM 请替换为您的pypi-server服务器IP
SET "PYPI_SERVER_PORT=8080"
SET "TEST_PACKAGE=requests"

ECHO ================================
ECHO Pip缓存代理功能测试脚本
ECHO ================================
ECHO 1. 正在检查当前pip配置...
ECHO ================================
pip config list
ECHO 

ECHO ================================
ECHO 2. 检查是否能访问私服...
ECHO ================================
ping %PYPI_SERVER_IP% -n 3 >nul
IF %ERRORLEVEL% NEQ 0 (
    ECHO 错误：无法访问服务器 %PYPI_SERVER_IP%，请检查网络连接和服务器状态。
    PAUSE
    EXIT /B 1
)

ECHO 尝试访问私服Web界面...
start http://%PYPI_SERVER_IP%:%PYPI_SERVER_PORT%/simple/
ECHO 请查看浏览器中是否能看到"Simple Index"页面。
ECHO 

ECHO ================================
ECHO 3. 测试从私服安装包 %TEST_PACKAGE%...
ECHO ================================
REM 卸载测试包（如果已安装）
pip uninstall -y %TEST_PACKAGE% >nul 2>&1

REM 清理pip缓存
python -m pip cache purge >nul 2>&1

REM 从私服安装测试包并显示详细信息
pip install %TEST_PACKAGE% -v --no-cache-dir

IF %ERRORLEVEL% NEQ 0 (
    ECHO 错误：从私服安装包失败，请检查服务器配置和网络连接。
    PAUSE
    EXIT /B 1
)

ECHO 
ECHO ================================
ECHO 4. 验证包是否通过私服下载...
ECHO ================================
REM 检查安装日志中是否包含私服地址
FINDSTR /I "%PYPI_SERVER_IP%" pip_install_log.txt >nul
IF %ERRORLEVEL% NEQ 0 (
    ECHO 警告：安装日志中未找到私服地址，可能未通过私服下载。
) ELSE (
    ECHO 成功：包通过私服下载。
)

ECHO 
ECHO ================================
ECHO 5. 验证缓存效果...
ECHO ================================
ECHO 请访问以下URL查看服务器上是否已缓存此包：
ECHO http://%PYPI_SERVER_IP%:%PYPI_SERVER_PORT%/simple/%TEST_PACKAGE%/
ECHO 
ECHO （如果是首次安装，服务器可能需要一些时间来缓存包）
ECHO 

ECHO ================================
ECHO 测试完成！
ECHO ================================
ECHO 请记住以下几点：
ECHO 1. 如果一切正常，您的pip请求已经通过私服代理。
ECHO 2. 私服会自动缓存下载过的包，下次请求将直接从私服获取。
ECHO 3. 如果遇到问题，请参考windows_pip_config_guide.md中的故障排除部分。
ECHO 
PAUSE