@echo off
:: 生成干净的一行一个依赖
set "tag=%~1"
if "%tag%"=="" set "tag=now"

mvn dependency:list -Dsort=true -DoutputFile=temp-dep.txt
(
    echo # 依赖列表: %tag%
    echo # 时间: %date% %time%
    echo.
    type temp-dep.txt | findstr /r "[^:]+:[^:]+:[^:]+:[^:]+"
) > deps-%tag%.txt

del temp-dep.txt
echo 完成: deps-%tag%.txt