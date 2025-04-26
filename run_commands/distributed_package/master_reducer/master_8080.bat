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
set CONFIG_DIR=%ROOT_DIR%\master_reducer
set REDUCER_INFO_FILE=%CONFIG_DIR%\reducer_info.txt
set MASTER_PORT=8080

REM Parse command-line arguments
if not "%1"=="" set REDUCER_HOST=%1
if not "%2"=="" set REDUCER_PORT=%2
if not "%3"=="" set WORKER_ADDRS=%3

REM Check if reducer_info.txt exists and read reducer address from it
if exist "%REDUCER_INFO_FILE%" (
    echo Reading reducer information from %REDUCER_INFO_FILE%...
    for /f "tokens=*" %%a in (%REDUCER_INFO_FILE%) do (
        set REDUCER_INFO=%%a
        echo - Found reducer: %%a
        
        REM Parse the IP:PORT format
        for /f "tokens=1,2 delims=:" %%b in ("%%a") do (
            set REDUCER_HOST=%%b
            set REDUCER_PORT=%%c
        )
    )
) else (
    echo %REDUCER_INFO_FILE% not found. 
    echo You can create this file manually with reducer information (IP:port)
    echo or start the reducer on this or another machine first.
    
    REM If reducer_info.txt doesn't exist, use default or command-line values
    echo Using default reducer host: %REDUCER_HOST% and port: %REDUCER_PORT%
)

REM Default worker addresses if not specified
if "%WORKER_ADDRS%"=="" set WORKER_ADDRS=%CURRENT_IP%:8081,%CURRENT_IP%:8082,%CURRENT_IP%:8083,%CURRENT_IP%:8084,%CURRENT_IP%:8085

echo Starting Master in distributed mode...
echo - IP Address: %CURRENT_IP%
echo - Connecting to Reducer at: %REDUCER_HOST%:%REDUCER_PORT%
echo - Worker addresses: %WORKER_ADDRS%
echo.

REM Write master information to shared file for component discovery
if not exist "%CONFIG_DIR%" mkdir "%CONFIG_DIR%"
echo %CURRENT_IP%:%MASTER_PORT%> "%CONFIG_DIR%\master_info.txt"
echo Master information (%CURRENT_IP%:%MASTER_PORT%) written to %CONFIG_DIR%\master_info.txt

REM Start Master with all worker addresses - Fixed to pass just the host to --reducer flag
java -cp "%ROOT_DIR%\classes" com.example.backend.Master --distributed --reducer %REDUCER_HOST% --workers %WORKER_ADDRS%

if %ERRORLEVEL% NEQ 0 (
  echo Master failed to start! Error code: %ERRORLEVEL%
  echo Make sure Java is installed and in your PATH.
  pause
  exit /b %ERRORLEVEL%
)

pause
