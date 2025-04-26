@echo off 
REM Start both the Master and Reducer in one script

REM Default settings
set REDUCER_HOST=localhost
set REDUCER_PORT=8090
set ROOT_DIR=..

REM Parse command-line arguments
if not "%1"=="" set WORKER_ADDRS=%1
if not "%2"=="" set REDUCER_PORT=%2

REM Default worker addresses if not specified
if "%WORKER_ADDRS%"=="" set WORKER_ADDRS=localhost:8081,localhost:8082,localhost:8083,localhost:8084,localhost:8085

echo Starting Master and Reducer on the same machine...
echo - Connecting to Workers at: %WORKER_ADDRS%
echo - Reducer will use port: %REDUCER_PORT%
echo.

REM Start the Reducer in a new window with custom port
start "Reducer" cmd /K java -Dreducer.port=%REDUCER_PORT% -cp "%ROOT_DIR%\classes" com.example.backend.Reducer %WORKER_ADDRS:,= %

REM Wait briefly for the Reducer to initialize
timeout /t 2 /nobreak >nul

REM Start the Master in the current window
echo Starting Master in distributed mode...
echo - Connecting to Reducer at: %REDUCER_HOST%:%REDUCER_PORT%
echo - Worker addresses: %WORKER_ADDRS%
echo.

REM Start Master with all worker addresses
CMD /K java -cp "%ROOT_DIR%\classes" com.example.backend.Master --distributed --reducer %REDUCER_HOST% --workers %WORKER_ADDRS%