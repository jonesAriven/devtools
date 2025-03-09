@echo off
chcp 65001 >nul
setlocal EnableDelayedExpansion
cd /d "%~dp0"
start "" "src\QR_generatorByC\bin\Release\net6.0-windows\QR_generatorByC.exe"
