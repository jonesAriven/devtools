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

set LIB_DIR=..\active-manager\activation-code-verifier\cpp
if not exist %LIB_DIR%\build_lib.bat (
    echo [ERROR] JonesActivation lib project not found at %LIB_DIR%
    pause
    exit /b 1
)

echo [1/5] Rebuilding JonesActivation library first (must pick up latest lib source)...
pushd %LIB_DIR%
call build_lib.bat
if errorlevel 1 (
    echo [ERROR] JonesActivation lib build failed!
    popd
    pause
    exit /b 1
)
popd
echo.

echo [2/5] Configuring with CMake for Win7 compatibility (Win32 + v143 + Windows 7 SDK)...
%CMAKE% -B %BUILD_DIR% -G "Visual Studio 18 2026" -A Win32 -DCMAKE_BUILD_TYPE=Release -DCMAKE_SYSTEM_VERSION=7.0
if %errorlevel% neq 0 (
    echo [ERROR] CMake configuration failed for Win7 target!
    echo Please install VS 2022 Build Tools with "VC++ v143 toolset" and "Windows 7.1 SDK" components.
    pause
    exit /b 1
)

echo [3/5] Building Release...
%CMAKE% --build %BUILD_DIR% --config Release --parallel
if %errorlevel% neq 0 (
    echo [ERROR] Build failed!
    pause
    exit /b 1
)

echo [4/5] Build complete!
echo.
for %%F in (%BUILD_DIR%\bin\Release\QRCodeTool-*.exe) do (
    echo Output: %%F
    echo Size: %%~zF bytes
)

echo.
echo [5/5] Copying to shared download directory...
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
