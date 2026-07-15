@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

echo ========================================
echo   QRCodeTool C++ Build Script
echo ========================================
echo.

set CMAKE="C:\Program Files\Microsoft Visual Studio\18\Community\Common7\IDE\CommonExtensions\Microsoft\CMake\CMake\bin\cmake.exe"
set VSDEVCMD="C:\Program Files\Microsoft Visual Studio\18\Community\Common7\Tools\VsDevCmd.bat"

if not exist %CMAKE% (
    echo [ERROR] CMake not found at %CMAKE%
    pause
    exit /b 1
)

set BUILD_DIR=build
if not exist %BUILD_DIR% mkdir %BUILD_DIR%

set SHARED_DIR=D:\huliang\java\ideaworkspace\www\download\QRCodeTools
if not exist %SHARED_DIR% mkdir %SHARED_DIR%

echo [1/4] Configuring with CMake...
%CMAKE% -B %BUILD_DIR% -G "Visual Studio 18 2026" -A Win32 -DCMAKE_BUILD_TYPE=Release
if %errorlevel% neq 0 (
    echo [INFO] VS 2026 generator failed, trying with v143 toolset...
    REM Try using the VS 2026 with v143 toolset for compatibility
    %CMAKE% -B %BUILD_DIR% -G "Visual Studio 18 2026" -A Win32 -DCMAKE_BUILD_TYPE=Release -T v143
    if %errorlevel% neq 0 (
        echo [ERROR] CMake configuration failed!
        echo Please make sure Visual Studio Build Tools with C++ workload is installed.
        pause
        exit /b 1
    )
)

echo [2/4] Building Release...
%CMAKE% --build %BUILD_DIR% --config Release --parallel
if %errorlevel% neq 0 (
    echo [ERROR] Build failed!
    pause
    exit /b 1
)

echo [3/4] Build complete!
echo.
for %%F in (%BUILD_DIR%\bin\Release\QRCodeTool-*.exe) do (
    echo Output: %%F
    echo Size: %%~zF bytes
)

echo.
echo [4/4] Copying to shared download directory...
for %%F in (%BUILD_DIR%\bin\Release\QRCodeTool-*.exe) do (
    copy /Y "%%F" "%SHARED_DIR%\" >nul
    if !errorlevel! equ 0 (
        echo   OK  Copied: %%~nxF → %SHARED_DIR%
    ) else (
        echo   FAIL Copy to %SHARED_DIR% failed!
    )
)
echo   Shared dir: %SHARED_DIR%
echo.

pause
