@echo off 
REM Start both the Master and Reducer in one script
set WORKER_ADDR=localhost:8081 
set REDUCER_HOST=localhost
set REDUCER_PORT=8090
if not "%1"=="" set WORKER_ADDR=%1
if not "%2"=="" set REDUCER_PORT=%2

echo Starting Master and Reducer on the same machine...
echo - Connecting to Worker at: %WORKER_ADDR%
echo - Reducer will use port: %REDUCER_PORT%
echo.

REM Start the Reducer in a new window with custom port (if specified)
start "Reducer" cmd /K java -Dreducer.port=%REDUCER_PORT% -cp classes com.example.backend.Reducer %WORKER_ADDR%

REM Wait briefly for the Reducer to initialize
timeout /t 2 /nobreak >nul

REM Start the Master in the current window
echo Starting Master in distributed mode...
echo - Connecting to Reducer at: %REDUCER_HOST%:%REDUCER_PORT%
echo - Worker addresses: %WORKER_ADDR%
echo.

REM The CMD /K keeps the window open after command execution
CMD /K java -cp classes com.example.backend.Master --distributed --reducer %REDUCER_HOST% --workers %WORKER_ADDR%