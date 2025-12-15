@echo off
echo ========================================
echo    ASL Glove Calibration Tool
echo ========================================
echo.

REM Check if Python is installed
python --version >nul 2>&1
if errorlevel 1 (
    echo ERROR: Python is not installed or not in PATH
    echo Please install Python and try again
    pause
    exit /b 1
)

REM Check if pyserial is installed
python -c "import serial" >nul 2>&1
if errorlevel 1 (
    echo Installing required packages...
    pip install pyserial numpy
)

echo.
echo Available serial ports:
python -c "import serial.tools.list_ports; [print(f'  {p.device} - {p.description}') for p in serial.tools.list_ports.comports()]"
echo.

set /p PORT="Enter serial port (e.g., COM5): "
set /p USERNAME="Enter username: "

echo.
echo Starting calibration for user: %USERNAME%
echo Port: %PORT%
echo.

python calibration_capture.py --port %PORT% --username %USERNAME%

echo.
echo Calibration complete! Check for calibration_%USERNAME%.json
pause
