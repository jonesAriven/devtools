@echo off
:: 设置控制台编码为 UTF-8
chcp 65001 >nul
setlocal enabledelayedexpansion

:: Define repository path global variables
set NGCARD_PATH=D:\huliang\cursorProjects\ngcard
set NGCARDH5_PATH=D:\huliang\cursorProjects\ngcardh5
set NGCARDBO_PATH=D:\huliang\cursorProjects\ngcardbo
pause
:: Add repository URL definitions
set NGCARD_REPO=https://gitee.com/jonesAriven/ngcard.git
set NGCARDH5_REPO=https://gitee.com/jonesAriven/ngcardh5.git
set NGCARDBO_REPO=https://gitee.com/jonesAriven/ngcardbo.git

:: Add local repository existence check
if not exist "%NGCARD_PATH%" (
    echo ngcard repository does not exist, cloning...

    :: Disable credential cache
    git config --global --unset credential.helper

    git clone %NGCARD_REPO% "%NGCARD_PATH%"
    if %errorlevel% neq 0 (
        echo Failed to clone ngcard repository. Error details:
        echo 1. Check your network connection.
        echo 2. Verify the repository address is correct.
        echo 3. Ensure Git is properly installed and configured.
        pause
        exit /b
    )
)

if not exist "%NGCARDH5_PATH%" (
    echo ngcardh5 repository does not exist, cloning...

    :: Disable credential cache
    git config --global --unset credential.helper

    git clone %NGCARDH5_REPO% "%NGCARDH5_PATH%"
    if %errorlevel% neq 0 (
        echo Failed to clone ngcardh5 repository. Error details:
        echo 1. Check your network connection.
        echo 2. Verify the repository address is correct.
        echo 3. Ensure Git is properly installed and configured.
        pause
        exit /b
    )
)
if not exist "%NGCARDBO_PATH%" (
    echo ngcardbo repository does not exist, cloning...

    :: Disable credential cache
    git config --global --unset credential.helper

    git clone %NGCARDBO_REPO% "%NGCARDBO_PATH%"
    if %errorlevel% neq 0 (
        echo Failed to clone ngcardbo repository. Error details:
        echo 1. Check your network connection.
        echo 2. Verify the repository address is correct.
        echo 3. Ensure Git is properly installed and configured.
        pause
        exit /b
    )
)

:: 1. Input iteration date
set /p iteration="Please enter the iteration date (e.g., 20250605) or type 'exit' to quit: "
if /i "%iteration%"=="exit" (
    echo Exiting script...
    pause
    exit /b
)
set version=%iteration%

:: 2. Input release reason
:input_reason
set /p reason="Please enter the release reason (e.g., lt, release) or type 'exit' to quit: "
if /i "%reason%"=="exit" (
    echo Exiting script...
    pause
    exit /b
)

if /i "%reason%"=="lt" (
    goto next
) else if /i "%reason%"=="release" (
    goto next
) else (
    echo Invalid release reason! Please enter either 'lt' or 'release'.
    goto input_reason
)

:next
:: Branch overwrite logic
if "%branch_type%"=="lt" (
    :: Overwrite all branches
    for %%A in (ngcard ngcardh5 ngcardbo) do (
        echo Overwriting %%A branch...
        cd /d !%%A_PATH!
        git fetch origin release-%%A-!version!
        git fetch origin lt-%%A
        git checkout lt-%%A
        git checkout release-%%A-!version! -- .
        git add .
        git commit -m "Update lt-%%A to match release-%%A-!version!"
        git push origin lt-%%A
    )
)

:: 3-5. Tagging logic
call :process_tag ngcard lt-ngcard release-ngcard-!version!
call :process_tag ngcardh5 lt-ngcardh5 release-ngcardh5-!version!
call :process_tag ngcardbo lt-ngcardbo release-ngcardbo-!version!

echo All operations completed
pause
exit /b

:: Subroutine to handle tagging logic
:process_tag
set app_name=%1
set lt_branch=%2
set release_branch=%3

echo Currently processing: %app_name%
set /p tag="Please enter the tag number for %app_name% application or type 'exit' to quit: "
if /i "%tag%"=="exit" (
    echo Exiting script...
    pause
    exit /b
)

cd /d !%app_name%_PATH!
if "%branch_type%"=="lt" (
    git checkout %lt_branch%
) else (
    git checkout %release_branch%
)
git pull origin %~2
if %errorlevel% neq 0 (
    echo Failed to pull %app_name% branch, please check network or branch existence.
    pause
    exit /b
)
git tag -a %tag% -m "%reason%"
git push origin %tag%
if %errorlevel% neq 0 (
    echo Failed to push %app_name% tag, please check if the tag already exists or network issues.
    pause
    exit /b
)
goto :eof



pause
endlocal