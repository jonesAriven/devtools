@echo off
chcp 65001 >nul
setlocal EnableDelayedExpansion

echo ====================================================
echo        二维码工具依赖安装程序 - GitHub源码版
echo ====================================================
echo.

:: 创建日志目录
if not exist logs mkdir logs
set LOG_FILE=logs\install_%date:~0,4%%date:~5,2%%date:~8,2%_%time:~0,2%%time:~3,2%%time:~6,2%.log
set LOG_FILE=%LOG_FILE: =0%

:: 初始化日志
echo 安装开始时间: %date% %time% > %LOG_FILE%
echo. >> %LOG_FILE%

echo 正在检查Python环境...
python --version > temp_version.txt 2>&1
set /p PYTHON_VERSION=<temp_version.txt
del temp_version.txt

echo Python版本: %PYTHON_VERSION% >> %LOG_FILE%

if "%PYTHON_VERSION%"=="" (
    echo [错误] 未检测到Python，请先安装Python 3.7或更高版本
    echo [错误] 未检测到Python >> %LOG_FILE%
    echo 正在打开Python下载页面...
    start https://www.python.org/downloads/
    echo 请安装Python 3.7或更高版本后重试
    goto end
)

:: 创建和准备lib目录
echo 准备lib目录...
if not exist lib mkdir lib
if not exist lib\.libs mkdir lib\.libs

:: 创建临时目录
if not exist temp mkdir temp
cd temp

echo 正在准备下载工具...
echo 设置PowerShell允许执行脚本
powershell -Command "Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass" >> ..\%LOG_FILE% 2>&1

echo.
echo 步骤1: 从GitHub下载基础依赖包源码
echo 下载源码... >> ..\%LOG_FILE%

:: 下载qrcode源码
echo 下载qrcode源码...
powershell -Command "& {[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri 'https://github.com/lincolnloop/python-qrcode/archive/refs/tags/v7.4.2.zip' -OutFile 'qrcode.zip'}" >> ..\%LOG_FILE% 2>&1
if exist qrcode.zip (
    echo qrcode源码下载成功
    echo qrcode源码下载成功 >> ..\%LOG_FILE%
) else (
    echo qrcode源码下载失败
    echo qrcode源码下载失败 >> ..\%LOG_FILE%
)

:: 下载pillow源码
echo 下载pillow源码...
powershell -Command "& {[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri 'https://github.com/python-pillow/Pillow/archive/refs/tags/10.0.0.zip' -OutFile 'pillow.zip'}" >> ..\%LOG_FILE% 2>&1
if exist pillow.zip (
    echo pillow源码下载成功
    echo pillow源码下载成功 >> ..\%LOG_FILE%
) else (
    echo pillow源码下载失败
    echo pillow源码下载失败 >> ..\%LOG_FILE%
)

:: 由于pyzbar和numpy较难从源码编译，改用预编译wheel文件
echo 下载pyzbar预编译wheel...
powershell -Command "& {[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri 'https://files.pythonhosted.org/packages/05/78/8707fa016f4c2f1b4cf4b1f56c7a800aa67a21470398896102bcd7b6b03c/pyzbar-0.1.9-py2.py3-none-any.whl' -OutFile 'pyzbar.whl'}" >> ..\%LOG_FILE% 2>&1
if exist pyzbar.whl (
    echo pyzbar wheel下载成功
    echo pyzbar wheel下载成功 >> ..\%LOG_FILE%
) else (
    echo pyzbar wheel下载失败
    echo pyzbar wheel下载失败 >> ..\%LOG_FILE%
)

echo 下载numpy预编译wheel...
powershell -Command "& {[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri 'https://files.pythonhosted.org/packages/89/89/b173caedc517319419c979adc9153e47694bbfd3429cd134501e493a6346/numpy-1.24.3-cp310-cp310-win_amd64.whl' -OutFile 'numpy.whl'}" >> ..\%LOG_FILE% 2>&1
if exist numpy.whl (
    echo numpy wheel下载成功
    echo numpy wheel下载成功 >> ..\%LOG_FILE%
) else (
    echo numpy wheel下载失败
    echo numpy wheel下载失败 >> ..\%LOG_FILE%
)

echo 处理下载的源码和wheel文件...
echo 处理下载的源码和wheel文件... >> ..\%LOG_FILE%

:: 解压和安装qrcode
if exist qrcode.zip (
    echo 解压qrcode...
    powershell -Command "& {Expand-Archive -Path 'qrcode.zip' -DestinationPath 'qrcode_src' -Force}" >> ..\%LOG_FILE% 2>&1
    if exist qrcode_src (
        cd qrcode_src\*
        echo 安装qrcode...
        python setup.py build >> ..\..\%LOG_FILE% 2>&1
        xcopy /E /Y build\lib\* ..\..\lib\ >> ..\..\%LOG_FILE% 2>&1
        cd ..\..
        echo qrcode安装完成
        echo qrcode安装完成 >> ..\%LOG_FILE%
    )
)

:: 解压和安装pillow
if exist pillow.zip (
    echo 解压pillow...
    powershell -Command "& {Expand-Archive -Path 'pillow.zip' -DestinationPath 'pillow_src' -Force}" >> ..\%LOG_FILE% 2>&1
    if exist pillow_src (
        cd pillow_src\*
        echo 安装pillow...
        python setup.py build >> ..\..\%LOG_FILE% 2>&1
        xcopy /E /Y build\lib\* ..\..\lib\ >> ..\..\%LOG_FILE% 2>&1
        cd ..\..
        echo pillow安装完成
        echo pillow安装完成 >> ..\%LOG_FILE%
    )
)

:: 解压wheel文件（zip格式）
if exist pyzbar.whl (
    echo 解压pyzbar wheel...
    powershell -Command "& {Expand-Archive -Path 'pyzbar.whl' -DestinationPath 'pyzbar_wheel' -Force}" >> ..\%LOG_FILE% 2>&1
    if exist pyzbar_wheel (
        echo 复制pyzbar到lib目录...
        xcopy /E /Y pyzbar_wheel\pyzbar ..\lib\pyzbar\ >> ..\%LOG_FILE% 2>&1
        echo pyzbar安装完成
        echo pyzbar安装完成 >> ..\%LOG_FILE%
    )
)

if exist numpy.whl (
    echo 解压numpy wheel...
    powershell -Command "& {Expand-Archive -Path 'numpy.whl' -DestinationPath 'numpy_wheel' -Force}" >> ..\%LOG_FILE% 2>&1
    if exist numpy_wheel (
        echo 复制numpy到lib目录...
        xcopy /E /Y numpy_wheel\numpy ..\lib\numpy\ >> ..\%LOG_FILE% 2>&1
        if exist numpy_wheel\*.libs (
            xcopy /E /Y numpy_wheel\*.libs ..\lib\.libs\ >> ..\%LOG_FILE% 2>&1
        )
        echo numpy安装完成
        echo numpy安装完成 >> ..\%LOG_FILE%
    )
)

:: 步骤2: 下载OpenCV二进制文件
echo.
echo 步骤2: 下载OpenCV (摄像头支持)
echo 下载OpenCV二进制文件... >> ..\%LOG_FILE%

:: 下载预编译的OpenCV Python包
echo 下载OpenCV预编译wheel...
powershell -Command "& {[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri 'https://github.com/opencv/opencv-python/releases/download/4.7.0.72/opencv_python-4.7.0.72-cp37-abi3-win_amd64.whl' -OutFile 'opencv.whl'}" >> ..\%LOG_FILE% 2>&1

if exist opencv.whl (
    echo OpenCV wheel下载成功
    echo OpenCV wheel下载成功 >> ..\%LOG_FILE%
    
    echo 解压OpenCV wheel...
    powershell -Command "& {Expand-Archive -Path 'opencv.whl' -DestinationPath 'opencv_wheel' -Force}" >> ..\%LOG_FILE% 2>&1
    if exist opencv_wheel (
        echo 复制OpenCV到lib目录...
        xcopy /E /Y opencv_wheel\cv2 ..\lib\cv2\ >> ..\%LOG_FILE% 2>&1
        echo OpenCV安装完成
        echo OpenCV安装完成 >> ..\%LOG_FILE%
        set OPENCV_INSTALLED=1
    ) else (
        echo OpenCV解压失败
        echo OpenCV解压失败 >> ..\%LOG_FILE%
        set OPENCV_INSTALLED=0
    )
) else (
    echo OpenCV wheel下载失败，尝试备用方法...
    echo OpenCV wheel下载失败 >> ..\%LOG_FILE%
    set OPENCV_INSTALLED=0
    
    :: 备用方法：下载完整OpenCV二进制包
    echo 尝试下载完整OpenCV二进制包...
    powershell -Command "& {[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri 'https://github.com/opencv/opencv/releases/download/4.8.0/opencv-4.8.0-windows.exe' -OutFile 'opencv_windows.exe'}" >> ..\%LOG_FILE% 2>&1
    
    if exist opencv_windows.exe (
        echo OpenCV二进制包下载成功
        echo OpenCV二进制包下载成功 >> ..\%LOG_FILE%
        
        :: 创建opencv目录
        mkdir opencv_extract
        echo 正在解压OpenCV...
        echo 请在弹出的安装向导中选择"解压"并选择当前目录下的 opencv_extract 文件夹
        echo 当前目录: %CD%\opencv_extract
        start /wait opencv_windows.exe
        
        :: 等待用户完成解压
        echo 按任意键继续(请先完成OpenCV解压)...
        pause > nul
        
        if exist opencv_extract\build (
            echo OpenCV解压成功！
            echo 正在复制OpenCV文件到lib目录...
            
            :: 复制必要的Python绑定和DLL
            xcopy /E /Y opencv_extract\build\python\cv2 ..\lib\cv2\ >> ..\%LOG_FILE% 2>&1
            if exist opencv_extract\build\x64\vc15\bin\*.dll (
                xcopy /Y opencv_extract\build\x64\vc15\bin\*.dll ..\lib\.libs\ >> ..\%LOG_FILE% 2>&1
            )
            
            echo OpenCV安装完成！
            echo OpenCV安装成功 >> ..\%LOG_FILE%
            set OPENCV_INSTALLED=1
        ) else (
            echo 未找到OpenCV解压文件
            echo OpenCV解压失败 >> ..\%LOG_FILE%
            set OPENCV_INSTALLED=0
        )
    )
)

:: 步骤3: 下载并安装Tesseract OCR
echo.
echo 步骤3: 安装OCR支持
echo 下载Tesseract OCR... >> ..\%LOG_FILE%

:: 下载pytesseract wheel
echo 下载pytesseract wheel...
powershell -Command "& {[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri 'https://files.pythonhosted.org/packages/8e/38/88d3fd10e3a179b44177c10c3cd5e6b2e9979a90eff34d51ec60726877c6/pytesseract-0.3.10-py3-none-any.whl' -OutFile 'pytesseract.whl'}" >> ..\%LOG_FILE% 2>&1

if exist pytesseract.whl (
    echo pytesseract wheel下载成功
    echo pytesseract wheel下载成功 >> ..\%LOG_FILE%
    
    echo 解压pytesseract wheel...
    powershell -Command "& {Expand-Archive -Path 'pytesseract.whl' -DestinationPath 'pytesseract_wheel' -Force}" >> ..\%LOG_FILE% 2>&1
    if exist pytesseract_wheel (
        echo 复制pytesseract到lib目录...
        xcopy /E /Y pytesseract_wheel\pytesseract ..\lib\pytesseract\ >> ..\%LOG_FILE% 2>&1
        echo pytesseract安装完成
        echo pytesseract安装完成 >> ..\%LOG_FILE%
        set OCR_INSTALLED=1
    ) else (
        echo pytesseract解压失败
        echo pytesseract解压失败 >> ..\%LOG_FILE%
        set OCR_INSTALLED=0
    )
) else (
    echo pytesseract wheel下载失败
    echo pytesseract wheel下载失败 >> ..\%LOG_FILE%
    set OCR_INSTALLED=0
)

echo 下载Tesseract-OCR二进制安装程序...
powershell -Command "& {[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri 'https://digi.bib.uni-mannheim.de/tesseract/tesseract-ocr-w64-setup-5.3.3.20231005.exe' -OutFile 'tesseract-setup.exe'}" >> ..\%LOG_FILE% 2>&1

if exist tesseract-setup.exe (
    echo Tesseract-OCR下载成功！
    echo 请运行安装程序并完成Tesseract-OCR安装
    start tesseract-setup.exe
    
    echo 按任意键继续(请先完成Tesseract-OCR安装)...
    pause > nul
    
    echo Tesseract-OCR安装完成 >> ..\%LOG_FILE%
) else (
    echo Tesseract-OCR下载失败
    echo Tesseract-OCR下载失败 >> ..\%LOG_FILE%
)

:: 清理临时文件
cd ..
echo 清理临时文件...
:: rd /s /q temp

:: 添加修改qr_generator.py的辅助脚本
echo.
echo 正在创建补充脚本，用于修改qr_generator.py适配Tesseract OCR...
(
echo # -*- coding: utf-8 -*-
echo import os
echo import re
echo.
echo # 读取原始文件
echo with open('qr_generator.py', 'r', encoding='utf-8') as f:
echo     content = f.read()
echo.
echo # 添加Tesseract配置
echo tesseract_config = """
echo # Tesseract OCR配置
echo try:
echo     import pytesseract
echo     pytesseract.pytesseract.tesseract_cmd = r'C:\\Program Files\\Tesseract-OCR\\tesseract.exe'
echo     OCR_SUPPORTED = True
echo except Exception as e:
echo     OCR_SUPPORTED = False
echo     print(f"OCR功能不可用：{str(e)}")
echo """
echo.
echo # 替换PaddleOCR部分
echo pattern = r"# OCR支持.*?OCR_SUPPORTED = False.*?print\(f\"OCR功能不可用：{str\(e\)}\"\)"
echo replacement = tesseract_config
echo.
echo # 使用re.DOTALL模式匹配多行
echo new_content = re.sub(pattern, replacement, content, flags=re.DOTALL)
echo.
echo # 写回文件
echo with open('qr_generator.py', 'w', encoding='utf-8') as f:
echo     f.write(new_content)
echo.
echo print("qr_generator.py已更新为使用Tesseract OCR")
) > update_ocr.py

:: 创建运行目录下的__init__.py文件
echo 创建运行目录下的__init__.py文件...
echo # This file helps Python recognize this directory as a package > __init__.py
echo 创建完成 >> %LOG_FILE%

:: 输出功能支持状态
echo.
echo 初始化完成！
echo 功能支持状态：

if exist lib\qrcode (
    echo √ 二维码生成和识别 [可用]
    echo 二维码功能安装成功 >> %LOG_FILE%
) else (
    echo × 二维码生成和识别 [不可用]
    echo 二维码功能安装失败 >> %LOG_FILE%
)

if exist lib\pytesseract (
    echo √ OCR文字识别 [可用] (需运行 python update_ocr.py 更新配置)
    echo OCR功能安装成功 >> %LOG_FILE%
) else (
    echo × OCR文字识别 [不可用]
    echo OCR功能安装失败 >> %LOG_FILE%
)

if exist lib\cv2 (
    echo √ 摄像头扫码 [可用]
    echo 摄像头功能安装成功 >> %LOG_FILE%
) else (
    echo × 摄像头扫码 [不可用]
    echo 摄像头功能安装失败 >> %LOG_FILE%
)

:: 修改run.bat添加更多的路径支持
echo 更新run.bat以支持从源码安装的库...
(
echo @echo off
echo chcp 65001 ^>nul
echo set PYTHONIOENCODING=utf-8
echo.
echo rem 设置Python路径包含lib目录和.libs目录
echo set PYTHONPATH=%%~dp0;%%~dp0lib;%%~dp0lib\.libs;%%PYTHONPATH%%
echo.
echo echo 正在启动程序...
echo echo.
echo.
echo rem 运行Python程序并捕获错误
echo python qr_generator.py 2^>error.log
echo if %%errorlevel%% neq 0 ^(
echo     echo 程序运行出错！错误信息：
echo     type error.log
echo     echo.
echo     echo 请检查以上错误信息，或联系开发者获取帮助。
echo     pause
echo     exit /b 1
echo ^)
echo.
echo del error.log ^>nul 2^>nul
) > run.bat_new
move /Y run.bat_new run.bat

echo.
echo 安装日志已保存到 %LOG_FILE%
echo.
echo 下一步操作：
echo 1. 运行 python update_ocr.py 更新OCR配置
echo 2. 运行 run.bat 启动程序
echo.

:end
echo 安装结束时间: %date% %time% >> %LOG_FILE%
pause 