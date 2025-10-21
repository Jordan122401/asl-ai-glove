@echo off
echo ========================================
echo   Copy Calibration to Android Device
echo ========================================
echo.

REM Check if ADB is available
adb version >nul 2>&1
if errorlevel 1 (
    echo ERROR: ADB is not installed or not in PATH
    echo Please install Android SDK Platform Tools
    pause
    exit /b 1
)

REM Check if device is connected
adb devices | findstr "device" >nul
if errorlevel 1 (
    echo ERROR: No Android device connected
    echo Please connect your device and enable USB debugging
    pause
    exit /b 1
)

echo Connected devices:
adb devices
echo.

set /p CALIBRATION_FILE="Enter calibration JSON filename (e.g., calibration_john.json): "

if not exist "%CALIBRATION_FILE%" (
    echo ERROR: File '%CALIBRATION_FILE%' not found
    echo Please make sure the file exists in the current directory
    pause
    exit /b 1
)

echo.
echo Copying %CALIBRATION_FILE% to Android device...
adb push "%CALIBRATION_FILE%" /sdcard/

if errorlevel 1 (
    echo ERROR: Failed to copy file to device
    pause
    exit /b 1
)

echo.
echo ✅ File copied successfully!
echo.
echo Next steps:
echo 1. Open the ASL Glove app
echo 2. Go to Calibration screen
echo 3. Tap "Import from Python Script"
echo 4. Select %CALIBRATION_FILE%
echo.

pause
