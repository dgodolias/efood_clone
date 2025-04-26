@echo off
REM Start the Manager Console Application

echo Starting Manager Console Application...
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
java com.example.efood_clone_2.frontend.ManagerConsoleApp
pause