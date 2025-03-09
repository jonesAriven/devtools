@echo off
chcp 65001 >nul

echo 正在检查Python环境...
python --version >nul 2>nul
if %errorlevel% neq 0 (
    echo 未检测到Python，正在打开下载页面...
    start https://www.python.org/downloads/
    echo 请安装Python 3.7或更高版本
    pause
    exit /b 1
)

echo.
echo 正在安装基础依赖包...
echo 注意：首次安装可能需要几分钟，请耐心等待...

rem 先尝试升级pip
python -m pip install --upgrade pip

rem 设置临时环境变量跳过SSL验证
set PYTHONHTTPSVERIFY=0

rem 定义镜像源列表
set MIRRORS=https://pypi.douban.com/simple --trusted-host pypi.douban.com https://pypi.tuna.tsinghua.edu.cn/simple --trusted-host pypi.tuna.tsinghua.edu.cn https://mirrors.aliyun.com/pypi/simple/ --trusted-host mirrors.aliyun.com https://pypi.org/simple

rem 基础功能依赖
set BASIC_DEPS=pillow qrcode pyzbar
rem OCR功能依赖
set OCR_DEPS=paddlepaddle paddleocr
rem 摄像头功能依赖
set CAMERA_DEPS=opencv-python

rem 创建功能状态文件
echo 0 > basic_installed.tmp
echo 0 > ocr_installed.tmp
echo 0 > camera_installed.tmp

rem 安装基础依赖
for %%m in (%MIRRORS%) do (
    if exist basic_installed.tmp (
        for %%d in (%BASIC_DEPS%) do (
            echo 正在尝试从 %%m 安装 %%d ...
            pip install -i %%m %%d
            if !errorlevel! equ 0 (
                echo 1 > basic_installed.tmp
            )
        )
    )
)

rem 安装OCR依赖
for %%m in (%MIRRORS%) do (
    if exist ocr_installed.tmp (
        for %%d in (%OCR_DEPS%) do (
            echo 正在尝试从 %%m 安装 %%d ...
            pip install -i %%m %%d
            if !errorlevel! equ 0 (
                echo 1 > ocr_installed.tmp
            )
        )
    )
)

rem 安装摄像头依赖
for %%m in (%MIRRORS%) do (
    if exist camera_installed.tmp (
        echo 正在尝试从 %%m 安装摄像头支持...
        pip install -i %%m %CAMERA_DEPS%
        if !errorlevel! equ 0 (
            echo 1 > camera_installed.tmp
        )
    )
)

echo.
echo 初始化完成！
echo 功能支持状态：
type basic_installed.tmp | find "1" >nul && (
    echo √ 二维码生成和识别 [可用]
) || (
    echo × 二维码生成和识别 [不可用]
)

type ocr_installed.tmp | find "1" >nul && (
    echo √ OCR文字识别 [可用]
) || (
    echo × OCR文字识别 [不可用]
)

type camera_installed.tmp | find "1" >nul && (
    echo √ 摄像头扫码 [可用]
) || (
    echo × 摄像头扫码 [不可用]
)

rem 清理临时文件
del basic_installed.tmp ocr_installed.tmp camera_installed.tmp

echo.
echo 您现在可以运行run.bat启动程序了！
pause 