@echo off
REM Start the Master

REM Get current IP address automatically
FOR /F "tokens=2 delims=:" %%a IN ('ipconfig ^| findstr /R /C:"IPv4 Address"') DO (
    SET CURRENT_IP=%%a
    GOTO :found_ip
)

:found_ip
REM Remove leading space from IP address
SET CURRENT_IP=%CURRENT_IP:~1%
echo Detected IP address: %CURRENT_IP%

REM Default settings
set REDUCER_HOST=%CURRENT_IP%
set REDUCER_PORT=8090
set ROOT_DIR=..

REM Parse command-line arguments
if not "%1"=="" set REDUCER_HOST=%1
if not "%2"=="" set REDUCER_PORT=%2
if not "%3"=="" set WORKER_ADDRS=%3

REM Default worker addresses if not specified
if "%WORKER_ADDRS%"=="" set WORKER_ADDRS=%CURRENT_IP%:8081,%CURRENT_IP%:8082,%CURRENT_IP%:8083,%CURRENT_IP%:8084,%CURRENT_IP%:8085

echo Starting Master in distributed mode...
echo - IP Address: %CURRENT_IP%
echo - Connecting to Reducer at: %REDUCER_HOST%:%REDUCER_PORT%
echo - Worker addresses: %WORKER_ADDRS%
echo.

REM Start Master with all worker addresses - Fixed to pass just the host to --reducer flag
java -cp "%ROOT_DIR%\classes" com.example.backend.Master --distributed --reducer %REDUCER_HOST% --workers %WORKER_ADDRS%

if %ERRORLEVEL% NEQ 0 (
  echo Master failed to start! Error code: %ERRORLEVEL%
  echo Make sure Java is installed and in your PATH.
  pause
  exit /b %ERRORLEVEL%
)

pause
