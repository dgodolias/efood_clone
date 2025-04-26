@echo off 
REM Start the Master in distributed mode with reducer and workers 
set REDUCER_HOST=localhost 
set WORKER_ADDRS=localhost:8081 
set ROOT_DIR=..

if not "%1"=="" set REDUCER_HOST=%1 
if not "%2"=="" set WORKER_ADDRS=%2 
java -cp "%ROOT_DIR%\classes" com.example.backend.Master --distributed --reducer %REDUCER_HOST% --workers %WORKER_ADDRS%
