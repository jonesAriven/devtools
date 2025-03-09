@echo off
chcp 65001 >nul
setlocal EnableDelayedExpansion

echo ============================================ > test_log.txt
echo 测试日志 - %date% %time% >> test_log.txt
echo ============================================ >> test_log.txt
echo. >> test_log.txt

echo 检查.NET版本...
echo 检查.NET版本... >> test_log.txt
dotnet --version >> test_log.txt 2>&1

echo 检查Windows Forms支持...
echo 检查Windows Forms支持... >> test_log.txt
dotnet new winforms --list >> test_log.txt 2>&1

echo 检查项目文件...
if exist "src\QR_generatorByC\QR_generatorByC.csproj" (
    echo 项目文件存在 >> test_log.txt
    type "src\QR_generatorByC\QR_generatorByC.csproj" >> test_log.txt
) else (
    echo 项目文件不存在 >> test_log.txt
)

echo 检查Form1.cs...
if exist "src\QR_generatorByC\Form1.cs" (
    echo Form1.cs存在 >> test_log.txt
    echo Form1.cs内容： >> test_log.txt
    type "src\QR_generatorByC\Form1.cs" >> test_log.txt
) else (
    echo Form1.cs不存在 >> test_log.txt
)

echo 检查编译输出...
if exist "src\QR_generatorByC\bin\Release\net6.0-windows\QR_generatorByC.exe" (
    echo 编译输出文件存在 >> test_log.txt
) else (
    echo 编译输出文件不存在 >> test_log.txt
)

echo 尝试编译...
dotnet build src\QR_generatorByC\QR_generatorByC.csproj --configuration Release -v d >> test_log.txt 2>&1

echo.
echo 测试完成，请查看test_log.txt了解详细信息
echo.
pause