@echo off
REM Start the Master in distributed mode with reducer and workers

REM Get current IP address automatically
FOR /F "tokens=2 delims=:" %%a IN ('ipconfig ^| findstr /R /C:"IPv4 Address"') DO (
    SET CURRENT_IP=%%a
    GOTO :found_ip
)

:found_ip
REM Remove leading space from IP address
SET CURRENT_IP=%CURRENT_IP:~1%
echo Detected IP address: %CURRENT_IP%

set REDUCER_HOST=%CURRENT_IP%
set WORKER_ADDRS=%CURRENT_IP%:8081
set ROOT_DIR=..

echo Starting Master in distributed mode
echo - IP Address: %CURRENT_IP%
echo - Reducer host: %REDUCER_HOST%
echo - Worker addresses: %WORKER_ADDRS%
echo - Using classpath: %ROOT_DIR%\classes
echo.

REM Create main data directory if it doesn't exist
if not exist "%ROOT_DIR%\data" mkdir "%ROOT_DIR%\data"

if not "%1"=="" set REDUCER_HOST=%1
if not "%2"=="" set WORKER_ADDRS=%2
java -cp "%ROOT_DIR%\classes" com.example.backend.Master --distributed --reducer %REDUCER_HOST% --workers %WORKER_ADDRS%

if %ERRORLEVEL% NEQ 0 (
  echo Master failed to start! Error code: %ERRORLEVEL%
  echo Make sure Java is installed and in your PATH.
  pause
  exit /b %ERRORLEVEL%
)

pause
