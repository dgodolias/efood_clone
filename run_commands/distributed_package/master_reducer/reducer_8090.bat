@echo off
REM Start the Reducer

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
set REDUCER_PORT=8090
set ROOT_DIR=..

REM Parse command-line arguments
if not "%1"=="" set WORKER_ADDRS=%1
if not "%2"=="" set REDUCER_PORT=%2

REM Default worker addresses if not specified
if "%WORKER_ADDRS%"=="" set WORKER_ADDRS=%CURRENT_IP%:8081,%CURRENT_IP%:8082,%CURRENT_IP%:8083,%CURRENT_IP%:8084,%CURRENT_IP%:8085

echo Starting Reducer...
echo - IP Address: %CURRENT_IP%
echo - Port: %REDUCER_PORT%
echo - Connected to Workers at: %WORKER_ADDRS%
echo.

REM Start the Reducer
java -Dreducer.port=%REDUCER_PORT% -cp "%ROOT_DIR%\classes" com.example.backend.Reducer %WORKER_ADDRS:,= %

if %ERRORLEVEL% NEQ 0 (
  echo Reducer failed to start! Error code: %ERRORLEVEL%
  echo Make sure Java is installed and in your PATH.
  pause
  exit /b %ERRORLEVEL%
)

pause
