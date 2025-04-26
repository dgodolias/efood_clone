@echo off
echo Starting all worker instances...

REM Get current IP address automatically
FOR /F "tokens=2 delims=:" %%a IN ('ipconfig ^| findstr /R /C:"IPv4 Address"') DO (
    SET CURRENT_IP=%%a
    GOTO :found_ip
)

:found_ip
REM Remove leading space from IP address
SET CURRENT_IP=%CURRENT_IP:~1%
echo Detected IP address: %CURRENT_IP%

REM Create a new window for each worker instance
echo Starting worker on port 8081...
start cmd /k worker_8081.bat

echo Starting worker on port 8082...
start cmd /k worker_8082.bat

echo Starting worker on port 8083...
start cmd /k worker_8083.bat

echo Starting worker on port 8084...
start cmd /k worker_8084.bat

echo Starting worker on port 8085...
start cmd /k worker_8085.bat

echo Starting worker on port 8086...
start cmd /k worker_8086.bat

echo All workers are now running.
echo Use CTRL+C to stop this batch file, but you'll need to close each worker window manually.

REM Keep the main window open
pause