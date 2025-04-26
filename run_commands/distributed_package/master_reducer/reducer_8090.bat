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
set CONFIG_DIR=%ROOT_DIR%\workers
set WORKER_INFO_FILE=%CONFIG_DIR%\worker_info.txt

REM Parse command-line arguments
if not "%1"=="" set WORKER_ADDRS=%1
if not "%2"=="" set REDUCER_PORT=%2

REM Check if worker_info.txt exists and read worker addresses from it
if exist "%WORKER_INFO_FILE%" (
    set WORKER_ADDRS=
    echo Reading worker information from %WORKER_INFO_FILE%...
    
    REM Need to enable delayed expansion for variables inside the for loop
    setlocal EnableDelayedExpansion
    
    for /f "tokens=*" %%a in (%WORKER_INFO_FILE%) do (
        if not defined WORKER_ADDRS (
            set WORKER_ADDRS=%%a
        ) else (
            set WORKER_ADDRS=!WORKER_ADDRS!,%%a
        )
        echo - Found worker: %%a
    )
    
    REM Pass the value back to the main environment
    endlocal & set WORKER_ADDRS=%WORKER_ADDRS%
) else (
    echo %WORKER_INFO_FILE% not found. 
    echo You can create this file manually with worker information (one IP:port per line)
    echo or start workers on this or other machines first.
    
    REM If worker_info.txt doesn't exist, use default or command-line values
    if "%WORKER_ADDRS%"=="" (
        echo Using default worker addresses.
        set WORKER_ADDRS=%CURRENT_IP%:8081,%CURRENT_IP%:8082,%CURRENT_IP%:8083,%CURRENT_IP%:8084,%CURRENT_IP%:8085
    )
)

echo Starting Reducer...
echo - IP Address: %CURRENT_IP%
echo - Port: %REDUCER_PORT%
echo - Connected to Workers at: %WORKER_ADDRS%
echo.

REM Write reducer information to shared file for component discovery
if not exist "%CONFIG_DIR%" mkdir "%CONFIG_DIR%"
echo %CURRENT_IP%:%REDUCER_PORT%> "%CONFIG_DIR%\reducer_info.txt"
echo Reducer information (%CURRENT_IP%:%REDUCER_PORT%) written to %CONFIG_DIR%\reducer_info.txt

REM Start the Reducer
java -Dreducer.port=%REDUCER_PORT% -cp "%ROOT_DIR%\classes" com.example.backend.Reducer %WORKER_ADDRS:,= %

if %ERRORLEVEL% NEQ 0 (
  echo Reducer failed to start! Error code: %ERRORLEVEL%
  echo Make sure Java is installed and in your PATH.
  pause
  exit /b %ERRORLEVEL%
)

pause
