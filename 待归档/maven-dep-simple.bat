@echo off
:: ���ɸɾ���һ��һ������
set "tag=%~1"
if "%tag%"=="" set "tag=now"

mvn dependency:list -Dsort=true -DoutputFile=temp-dep.txt
(
 echo # �����б�: %tag%
    echo # ʱ��: %date% %time%
    echo.
    type temp-dep.txt | findstr /r "[^:]+:[^:]+:[^:]+:[^:]+"
) > deps-%tag%.txt

del temp-dep.txt
echo ���: deps-%tag%.txt