@echo off
REM Start a Worker

REM Get current IP address automatically
FOR /F "tokens=2 delims=:" %%a IN ('ipconfig ^| findstr /R /C:"IPv4 Address"') DO (
    SET CURRENT_IP=%%a
    GOTO :found_ip
)

:found_ip
REM Remove leading space from IP address
SET CURRENT_IP=%CURRENT_IP:~1%
echo Detected IP address: %CURRENT_IP%

set PORT=8084
set ROOT_DIR=..
set CONFIG_DIR=%ROOT_DIR%\workers

echo Starting Worker on port %PORT% with IP: %CURRENT_IP%
echo Using classpath: %ROOT_DIR%\classes
echo.

REM Create worker data directory if it doesn't exist
if not exist "data\worker_%PORT%" mkdir data\worker_%PORT%

REM Write worker information to shared file for component discovery
if not exist "%CONFIG_DIR%" mkdir "%CONFIG_DIR%"
echo %CURRENT_IP%:%PORT%>> "%CONFIG_DIR%\worker_info.txt"
echo Worker information (%CURRENT_IP%:%PORT%) written to %CONFIG_DIR%\worker_info.txt

if not "%1"=="" set PORT=%1
java -cp "%ROOT_DIR%\classes" com.example.backend.Worker "%PORT%"

if %ERRORLEVEL% NEQ 0 (
  echo Worker failed to start! Error code: %ERRORLEVEL%
  echo Make sure Java is installed and in your PATH.
  pause
  exit /b %ERRORLEVEL%
)

pause
