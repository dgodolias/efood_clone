@echo off
REM Script to package essential components for distributed system execution
REM This script creates a minimal package with only what's needed to run the system

echo Creating package with essential components for distributed system...

REM Create package directory structure
set PACKAGE_DIR=distributed_package
mkdir %PACKAGE_DIR%
mkdir %PACKAGE_DIR%\classes\com\example\backend
mkdir %PACKAGE_DIR%\data
mkdir %PACKAGE_DIR%\run_commands

REM Copy compiled classes
echo Copying essential compiled classes...
copy backend\build\classes\java\main\com\example\backend\Master.class %PACKAGE_DIR%\classes\com\example\backend\
copy backend\build\classes\java\main\com\example\backend\Reducer.class %PACKAGE_DIR%\classes\com\example\backend\
copy backend\build\classes\java\main\com\example\backend\Worker.class %PACKAGE_DIR%\classes\com\example\backend\
copy backend\build\classes\java\main\com\example\backend\WorkerConnection.class %PACKAGE_DIR%\classes\com\example\backend\
copy backend\build\classes\java\main\com\example\backend\Store.class %PACKAGE_DIR%\classes\com\example\backend\
copy backend\build\classes\java\main\com\example\backend\Product.class %PACKAGE_DIR%\classes\com\example\backend\

REM Copy inner classes - IMPORTANT!
echo Copying inner classes...
copy backend\build\classes\java\main\com\example\backend\Worker$WorkerThread.class %PACKAGE_DIR%\classes\com\example\backend\
copy backend\build\classes\java\main\com\example\backend\Master$MasterThread.class %PACKAGE_DIR%\classes\com\example\backend\
copy backend\build\classes\java\main\com\example\backend\Reducer$ReducerThread.class %PACKAGE_DIR%\classes\com\example\backend\

REM Copy any other inner classes that might exist
if exist backend\build\classes\java\main\com\example\backend\MasterThread.class (
    copy backend\build\classes\java\main\com\example\backend\MasterThread.class %PACKAGE_DIR%\classes\com\example\backend\
)

if exist backend\build\classes\java\main\com\example\backend\ReducerThread.class (
    copy backend\build\classes\java\main\com\example\backend\ReducerThread.class %PACKAGE_DIR%\classes\com\example\backend\
)

if exist backend\build\classes\java\main\com\example\backend\WorkerThread.class (
    copy backend\build\classes\java\main\com\example\backend\WorkerThread.class %PACKAGE_DIR%\classes\com\example\backend\
)

REM Copy data files
echo Copying essential data files...
copy data\stores.json %PACKAGE_DIR%\data\

REM Copy run command files
echo Copying run commands...
copy run_commands\distributed\master_reducer_together.txt %PACKAGE_DIR%\run_commands\
copy run_commands\distributed\start_worker.txt %PACKAGE_DIR%\run_commands\

REM Create simplified runner scripts
echo Creating simplified runner scripts...

echo @echo off > %PACKAGE_DIR%\start_worker.bat
echo REM Start a Worker >> %PACKAGE_DIR%\start_worker.bat
echo set PORT=8081 >> %PACKAGE_DIR%\start_worker.bat
echo if not "%%1"=="" set PORT=%%1 >> %PACKAGE_DIR%\start_worker.bat
echo java -cp classes com.example.backend.Worker %%PORT%% >> %PACKAGE_DIR%\start_worker.bat

echo @echo off > %PACKAGE_DIR%\start_reducer.bat
echo REM Start the Reducer >> %PACKAGE_DIR%\start_reducer.bat
echo set WORKER_ADDR=localhost:8081 >> %PACKAGE_DIR%\start_reducer.bat
echo if not "%%1"=="" set WORKER_ADDR=%%1 >> %PACKAGE_DIR%\start_reducer.bat
echo java -cp classes com.example.backend.Reducer %%WORKER_ADDR%% >> %PACKAGE_DIR%\start_reducer.bat

echo @echo off > %PACKAGE_DIR%\start_master.bat
echo REM Start the Master in distributed mode with reducer and workers >> %PACKAGE_DIR%\start_master.bat
echo set REDUCER_HOST=localhost >> %PACKAGE_DIR%\start_master.bat
echo set WORKER_ADDRS=localhost:8081 >> %PACKAGE_DIR%\start_master.bat
echo if not "%%1"=="" set REDUCER_HOST=%%1 >> %PACKAGE_DIR%\start_master.bat
echo if not "%%2"=="" set WORKER_ADDRS=%%2 >> %PACKAGE_DIR%\start_master.bat
echo java -cp classes com.example.backend.Master --distributed --reducer %%REDUCER_HOST%% --workers %%WORKER_ADDRS%% >> %PACKAGE_DIR%\start_master.bat

echo @echo off > %PACKAGE_DIR%\README.txt
echo Essential components for running the distributed system >> %PACKAGE_DIR%\README.txt
echo. >> %PACKAGE_DIR%\README.txt
echo USAGE: >> %PACKAGE_DIR%\README.txt
echo. >> %PACKAGE_DIR%\README.txt
echo 1. For Worker machine: >> %PACKAGE_DIR%\README.txt
echo    - Run: start_worker.bat [PORT] >> %PACKAGE_DIR%\README.txt
echo    - Example: start_worker.bat 8081 >> %PACKAGE_DIR%\README.txt
echo. >> %PACKAGE_DIR%\README.txt
echo 2. For Master+Reducer machine: >> %PACKAGE_DIR%\README.txt
echo    - First run: start_reducer.bat [WORKER_IP:PORT] >> %PACKAGE_DIR%\README.txt
echo    - Example: start_reducer.bat 192.168.1.101:8081 >> %PACKAGE_DIR%\README.txt
echo    - Then run: start_master.bat [REDUCER_HOST] [WORKER_IP:PORT] >> %PACKAGE_DIR%\README.txt
echo    - Example: start_master.bat localhost 192.168.1.101:8081 >> %PACKAGE_DIR%\README.txt
echo. >> %PACKAGE_DIR%\README.txt
echo REQUIREMENTS: >> %PACKAGE_DIR%\README.txt
echo - Java must be installed on all machines >> %PACKAGE_DIR%\README.txt
echo - Ensure network connectivity between machines >> %PACKAGE_DIR%\README.txt
echo - Firewalls must allow connections on ports 8080 (Master), 8090 (Reducer), and 8081+ (Workers) >> %PACKAGE_DIR%\README.txt

REM Create a zip archive of the package
echo Creating zip archive...
powershell Compress-Archive -Path %PACKAGE_DIR% -DestinationPath %PACKAGE_DIR%.zip -Force

echo Package created: %PACKAGE_DIR%.zip
echo You can now transfer this package to other machines.
echo.
echo Instructions:
echo 1. Extract the zip file on each machine
echo 2. Run the appropriate batch files as described in README.txt