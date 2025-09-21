@echo off
cls

REM 检查Python和pip是否可用
python --version >nul 2>&1
if %errorlevel% neq 0 (
    echo 错误：未找到Python。请确保Python已正确安装并添加到环境变量中。
    echo 您可以尝试使用完整路径执行Python，例如：C:\Python311\python.exe
    pause
    exit /b 1
)

REM 查找pip的完整路径
for /f "delims=" %%i in ('where pip 2^>nul') do set "PIP_PATH=%%i"
if not defined PIP_PATH (
    echo 警告：未在环境变量中找到pip。正在尝试使用Python -m pip...
    set "PIP_COMMAND=python -m pip"
) else (
    echo 找到pip路径：%PIP_PATH%
    set "PIP_COMMAND=pip"
)

REM 验证pip命令
%PIP_COMMAND% --version >nul 2>&1
if %errorlevel% neq 0 (
    echo 错误：pip命令无法执行。
    echo 请参考windows_pip_config_guide.md文件中的解决方案进行修复。
    pause
    exit /b 1
)

REM 安装qrcode包进行测试
set TEST_PACKAGE=qrcode

echo.
echo 开始安装测试包：%TEST_PACKAGE%
%PIP_COMMAND% install %TEST_PACKAGE% -v

if %errorlevel% equ 0 (
    echo.
echo 成功安装%TEST_PACKAGE%！pip命令验证通过。
    echo 您的pip环境已正常工作。
    
    REM 创建简单的测试脚本
    echo import qrcode > test_qrcode.py
    echo img = qrcode.make('Hello from your pip server!') >> test_qrcode.py
    echo img.save('test_qrcode.png') >> test_qrcode.py
    echo print('QR code generated successfully!') >> test_qrcode.py
    
    echo.
echo 已创建测试脚本：test_qrcode.py
    echo 您可以运行：python test_qrcode.py 来生成一个测试QR码。
) else (
    echo.
echo 安装%TEST_PACKAGE%失败。请检查：
    echo 1. 网络连接是否正常
    echo 2. pip配置是否正确
    echo 3. 您的pip私服是否可用
    echo 4. 是否有足够的权限
)

echo.
echo 验证完成。按任意键退出...
pause >nul