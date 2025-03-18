@echo off
echo Starting eFood System...

REM Enable delayed expansion for dynamic variable updates in loops
setlocal EnableDelayedExpansion

REM Set project root directory
set PROJECT_ROOT=C:\Users\USER\Desktop\PROJECTS\efood

REM Set the number of workers to launch
set WORKER_COUNT=3
REM Starting port for workers
set START_PORT=8081

REM Pre-calculate the end value for the loop
set /a END_COUNT=%WORKER_COUNT%-1

REM Initialize an empty variable to store worker addresses for the Master
set "WORKER_ADDRESSES="

REM Launch workers in a loop
for /l %%i in (0,1,%END_COUNT%) do (
    set /a PORT=%START_PORT% + %%i
    echo Starting Worker on port !PORT!...
    start "Worker !PORT!" cmd /k java -cp %PROJECT_ROOT%\backend\build\classes\java\main com.example.backend.Worker !PORT!
    REM Append worker address to the list
    if defined WORKER_ADDRESSES (
        set "WORKER_ADDRESSES=!WORKER_ADDRESSES! localhost:!PORT!"
    ) else (
        set "WORKER_ADDRESSES=localhost:!PORT!"
    )
)

REM Wait briefly to ensure workers are initialized
echo Waiting for workers to start...
timeout /t 3 /nobreak >nul

REM Launch Master with all worker addresses
echo Starting Master...
start "Master" cmd /k "cd /d %PROJECT_ROOT%\backend && java -cp build\classes\java\main com.example.backend.Master %WORKER_ADDRESSES%"

echo eFood system startup initiated. This window will close in 5 seconds...
timeout /t 5 /nobreak >nul
endlocal
exit