@echo off
chcp 65001 >nul
setlocal

set CMAKE="C:\Program Files\Microsoft Visual Studio\18\Community\Common7\IDE\CommonExtensions\Microsoft\CMake\CMake\bin\cmake.exe"

set BUILD_DIR=build
if not exist %BUILD_DIR% mkdir %BUILD_DIR%

echo [1/3] Configuring JonesActivation library for Win7 compatibility (Win32 + v143 + Windows 7 SDK)...
%CMAKE% -B %BUILD_DIR% -G "Visual Studio 18 2026" -A Win32 -DCMAKE_BUILD_TYPE=Release -DCMAKE_SYSTEM_VERSION=7.0
if %errorlevel% neq 0 (
    echo [ERROR] CMake configuration failed for Win7 target!
    echo Please install VS 2022 Build Tools with "VC++ v143 toolset" and "Windows 7.1 SDK" components.
    exit /b 1
)

echo [2/3] Building JonesActivation library (Release)...
%CMAKE% --build %BUILD_DIR% --config Release --parallel
if %errorlevel% neq 0 (
    echo [ERROR] Build failed!
    exit /b 1
)

echo [3/3] Build complete!
echo Output: %BUILD_DIR%\Release\JonesActivation.lib
for %%F in (%BUILD_DIR%\Release\JonesActivation.lib) do (
    echo Size: %%~zF bytes
)

endlocal
