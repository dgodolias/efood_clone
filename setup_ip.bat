@echo off
REM Setup IP address for distributed system
REM This script detects your current IP address and updates batch files to use it

echo Setting up distributed system with your current IP address...

REM Get current IP address
FOR /F "tokens=2 delims=:" %%a IN ('ipconfig ^| findstr /R /C:"IPv4 Address"') DO (
    SET CURRENT_IP=%%a
    GOTO :found_ip
)

:found_ip
REM Remove leading space from IP address
SET CURRENT_IP=%CURRENT_IP:~1%
echo Detected IP address: %CURRENT_IP%

echo.
echo Updating batch files in distributed package...
echo.

set PACKAGE_DIR=run_commands\distributed_package

REM Update master and reducer scripts to use current IP
echo Updating master and reducer scripts...

REM Update the standalone master script
powershell -Command "(Get-Content %PACKAGE_DIR%\master_reducer\master_8080.bat) -replace 'set REDUCER_HOST=localhost', 'set REDUCER_HOST=%CURRENT_IP%' -replace 'set WORKER_ADDRS=localhost:', 'set WORKER_ADDRS=%CURRENT_IP%:' | Set-Content %PACKAGE_DIR%\master_reducer\master_8080.bat"

REM Update the standalone reducer script
powershell -Command "(Get-Content %PACKAGE_DIR%\master_reducer\reducer_8090.bat) -replace 'set WORKER_ADDR=localhost:', 'set WORKER_ADDR=%CURRENT_IP%:' | Set-Content %PACKAGE_DIR%\master_reducer\reducer_8090.bat"

REM Update the combined master+reducer script
powershell -Command "(Get-Content %PACKAGE_DIR%\master_reducer\master_8080_reducer_8090.bat) -replace 'set REDUCER_HOST=localhost', 'set REDUCER_HOST=%CURRENT_IP%' -replace 'localhost:', '%CURRENT_IP%:' | Set-Content %PACKAGE_DIR%\master_reducer\master_8080_reducer_8090.bat"

echo Master and reducer scripts updated.
echo.

REM Create a new readme file with network deployment instructions
echo Creating updated README with network deployment instructions...

powershell -Command "(Get-Content %PACKAGE_DIR%\README.txt) -replace 'localhost:', '%CURRENT_IP%:' | Set-Content %PACKAGE_DIR%\README.txt.tmp"

echo. >> %PACKAGE_DIR%\README.txt.tmp
echo NETWORK DEPLOYMENT UPDATE: >> %PACKAGE_DIR%\README.txt.tmp
echo Your system is now configured to use IP address: %CURRENT_IP% >> %PACKAGE_DIR%\README.txt.tmp
echo. >> %PACKAGE_DIR%\README.txt.tmp
echo If you want to run everything on a single machine: >> %PACKAGE_DIR%\README.txt.tmp
echo - No changes needed, workers will connect to your IP address instead of localhost >> %PACKAGE_DIR%\README.txt.tmp
echo. >> %PACKAGE_DIR%\README.txt.tmp
echo If you want to run on multiple machines across a network: >> %PACKAGE_DIR%\README.txt.tmp
echo 1. Copy the distributed_package folder to each worker machine >> %PACKAGE_DIR%\README.txt.tmp
echo 2. On each worker machine: >> %PACKAGE_DIR%\README.txt.tmp
echo    - Run a worker batch file with that machine's port >> %PACKAGE_DIR%\README.txt.tmp
echo 3. On the master machine: >> %PACKAGE_DIR%\README.txt.tmp
echo    - Run master_8080_reducer_8090.bat with a comma-separated list of worker addresses >> %PACKAGE_DIR%\README.txt.tmp
echo    - Example: master_8080_reducer_8090.bat 192.168.1.101:8081,192.168.1.102:8082 >> %PACKAGE_DIR%\README.txt.tmp
echo. >> %PACKAGE_DIR%\README.txt.tmp
echo IMPORTANT NETWORK REQUIREMENTS: >> %PACKAGE_DIR%\README.txt.tmp
echo - All machines must be on the same network >> %PACKAGE_DIR%\README.txt.tmp
echo - Firewall rules must allow Java to communicate on the required ports >> %PACKAGE_DIR%\README.txt.tmp
echo - For Windows: Check Windows Defender Firewall settings >> %PACKAGE_DIR%\README.txt.tmp
echo - Master machine must be able to reach all worker machines on their respective ports >> %PACKAGE_DIR%\README.txt.tmp
echo - Worker machines must be able to reach the master machine on port 8080 >> %PACKAGE_DIR%\README.txt.tmp

move /Y %PACKAGE_DIR%\README.txt.tmp %PACKAGE_DIR%\README.txt

echo.
echo ===============================
echo Setup complete! Your system is now configured to use IP address: %CURRENT_IP%
echo.
echo To run the distributed system:
echo 1. Navigate to %PACKAGE_DIR%\workers and run the worker batch files
echo 2. Navigate to %PACKAGE_DIR%\master_reducer and run master_8080_reducer_8090.bat
echo.
echo For network deployment across multiple machines, check the updated README.txt
echo ===============================
echo.

pause