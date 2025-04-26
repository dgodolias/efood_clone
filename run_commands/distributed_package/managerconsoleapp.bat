@echo off
REM Start the Manager Console Application

echo Starting Manager Console Application...

REM Get the local IP address
for /f "tokens=2 delims=:" %%i in ('ipconfig ^| findstr /R /C:"IPv4"') do (
    set IP=%%i
    goto :break
)
:break
set IP=%IP:~1%
echo Local IP address is: %IP%

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
echo Connecting to Master at: %IP%:8080
java com.example.efood_clone_2.frontend.ManagerConsoleApp %IP%
pause