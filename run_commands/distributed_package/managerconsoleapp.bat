@echo off
REM Start the Manager Console Application

echo Starting Manager Console Application...

REM Default settings
set MASTER_HOST=localhost
set MASTER_PORT=8080
set CONFIG_DIR=master_reducer
set MASTER_INFO_FILE=%CONFIG_DIR%\master_info.txt

REM Get the local IP address as fallback
for /f "tokens=2 delims=:" %%i in ('ipconfig ^| findstr /R /C:"IPv4"') do (
    set IP=%%i
    goto :break
)
:break
set IP=%IP:~1%
echo Local IP address is: %IP%

REM Check if master_info.txt exists and read master address from it
if exist "%MASTER_INFO_FILE%" (
    echo Reading master information from %MASTER_INFO_FILE%...
    
    REM Read the first line from the file
    set /p MASTER_INFO=<"%MASTER_INFO_FILE%"
    echo - Found master: %MASTER_INFO%
    
    REM Parse the IP:PORT format
    for /f "tokens=1,2 delims=:" %%a in ("%MASTER_INFO%") do (
        set MASTER_HOST=%%a
        set MASTER_PORT=%%b
    )
) else (
    echo %MASTER_INFO_FILE% not found. 
    echo You can create this file manually with master information (IP:port)
    echo or start the master on this or another machine first.
    
    REM If master_info.txt doesn't exist, use local IP as default
    set MASTER_HOST=%IP%
    echo Using default master host: %MASTER_HOST% and port: %MASTER_PORT%
)

cd C:\Users\USER\Desktop\PROJECTS\efood_clone_2
cd app\src\main\java

REM Compile and run the application
javac com/example/efood_clone_2/frontend/ManagerConsoleApp.java
if %ERRORLEVEL% NEQ 0 (
    echo Compilation failed!
    pause
    exit /b %ERRORLEVEL%
)

echo Running Manager Console Application...
echo Connecting to Master at: %MASTER_HOST%:%MASTER_PORT%
java com.example.efood_clone_2.frontend.ManagerConsoleApp %MASTER_HOST%
pause