@echo off
REM Start both the Master and Reducer in one script

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
if not "%1"=="" set WORKER_ADDRS=%1
if not "%2"=="" set REDUCER_PORT=%2

REM Default worker addresses if not specified
if "%WORKER_ADDRS%"=="" set WORKER_ADDRS=%CURRENT_IP%:8081,%CURRENT_IP%:8082,%CURRENT_IP%:8083,%CURRENT_IP%:8084,%CURRENT_IP%:8085

REM Create main data directory if it doesn't exist
if not exist "%ROOT_DIR%\data" mkdir "%ROOT_DIR%\data"

echo Starting Master and Reducer on the same machine...
echo - IP Address: %CURRENT_IP%
echo - Connecting to Workers at: %WORKER_ADDRS%
echo - Reducer will use port: %REDUCER_PORT%
echo.

REM Start the Reducer in a new window with custom port
start "Reducer" cmd /K "java -Dreducer.port=%REDUCER_PORT% -cp "%ROOT_DIR%\classes" com.example.backend.Reducer %WORKER_ADDRS:,= %"

REM Wait briefly for the Reducer to initialize
timeout /t 2 /nobreak >nul

REM Start the Master in the current window
echo Starting Master in distributed mode...
echo - Connecting to Reducer at: %REDUCER_HOST%:%REDUCER_PORT%
echo - Worker addresses: %WORKER_ADDRS%
echo.

REM Start Master with all worker addresses
java -cp "%ROOT_DIR%\classes" com.example.backend.Master --distributed --reducer %REDUCER_HOST% --workers %WORKER_ADDRS%

if %ERRORLEVEL% NEQ 0 (
  echo Master failed to start! Error code: %ERRORLEVEL%
  echo Make sure Java is installed and in your PATH.
  pause
  exit /b %ERRORLEVEL%
)

pause