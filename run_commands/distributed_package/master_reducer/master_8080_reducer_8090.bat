@echo off
echo Starting Master server on port 8080 with Reducer on port 8090...

:: Get the local IP address
for /f "tokens=2 delims=:" %%i in ('ipconfig ^| findstr /R /C:"IPv4"') do (
    set IP=%%i
    goto :break
)
:break
set IP=%IP:~1%
echo Local IP address is: %IP%

:: Set the correct classpath to match the worker script
set ROOT_DIR=..
echo Using classpath: %ROOT_DIR%\classes

:: Set default worker list with all workers if none is provided
if "%~1"=="" (
    set WORKER_LIST=%IP%:8081,%IP%:8082,%IP%:8083,%IP%:8084,%IP%:8085
    echo No worker list provided, defaulting to all workers:
    echo %WORKER_LIST%
) else (
    set WORKER_LIST=%~1
    echo Using provided worker list: %WORKER_LIST%
)

:: Start the Master server in distributed mode
:: --distributed flag enables binding to all network interfaces (0.0.0.0)
:: --reducer specifies the reducer host (localhost since reducer runs on same machine)
echo Starting Master with workers: %WORKER_LIST%
java -cp "%ROOT_DIR%\classes" com.example.backend.Master --distributed --reducer localhost --workers %WORKER_LIST%

if %ERRORLEVEL% NEQ 0 (
  echo Master failed to start! Error code: %ERRORLEVEL%
  echo Make sure Java is installed and in your PATH.
)

echo Master server stopped
pause