@echo off
echo ==========================================================
echo Starting Reducer and Master for distributed system...
echo ==========================================================
echo.

:: Get the local IP address
for /f "tokens=2 delims=:" %%i in ('ipconfig ^| findstr /R /C:"IPv4"') do (
    set IP=%%i
    goto :break
)
:break
set IP=%IP:~1%
echo Local IP address is: %IP%

:: Set default worker list if none is provided
if "%~1"=="" (
    set WORKER_LIST=%IP%:8081,%IP%:8082,%IP%:8083,%IP%:8084,%IP%:8085
    echo No worker list provided, defaulting to all workers:
    echo %WORKER_LIST%
) else (
    set WORKER_LIST=%~1
    echo Using provided worker list: %WORKER_LIST%
)

:: Check if Java is installed
java -version >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Java is not installed or not in PATH.
    echo Please install Java and try again.
    goto :error
)

:: Start the Reducer in a separate window first
echo.
echo *** STEP 1: Starting Reducer on port 8090 ***
echo.
set REDUCER_CMD=cd "%~dp0" ^&^& call reducer_8090.bat %WORKER_LIST%
start "Reducer 8090" cmd /k "%REDUCER_CMD%"

:: Wait for Reducer to initialize
echo Waiting for Reducer to initialize...
timeout /t 7 /nobreak >nul

:: Check if Reducer is running on port 8090
set REDUCER_RUNNING=0
netstat -ano | findstr ":8090" | findstr "LISTENING" >nul
if %ERRORLEVEL% EQU 0 (
    set REDUCER_RUNNING=1
    echo Reducer is running on port 8090.
) else (
    echo ERROR: Reducer does not appear to be running on port 8090.
    echo Please check the Reducer window for errors.
    echo Waiting 5 more seconds before starting Master...
    timeout /t 5 /nobreak >nul
)

:: Start the Master in distributed mode in a separate window
echo.
echo *** STEP 2: Starting Master with Reducer at localhost:8090 ***
echo.
set MASTER_CMD=cd "%~dp0" ^&^& call master_8080.bat localhost 8090 %WORKER_LIST%
start "Master 8080" cmd /k "%MASTER_CMD%"

echo.
echo ==========================================================
echo System Status:
echo ==========================================================
echo Both the Reducer and Master have been started in separate windows.
echo The Master is configured to connect to the Reducer at localhost:8090.
echo Both are connected to workers at: %WORKER_LIST%
echo.
echo NOTE: The Master will only function if the Reducer is running correctly.
echo If the Master reports errors connecting to the Reducer, please restart
echo both components using this script.
echo.
echo You can close this window, but you'll need to manually close the Master
echo and Reducer windows when done.
echo ==========================================================
echo.
goto :end

:error
echo.
echo ERROR: Failed to start components. See above for details.
echo.
pause
exit /b 1

:end
pause