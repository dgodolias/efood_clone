@echo off 
REM Start the Reducer 
set WORKER_ADDR=localhost:8081 
if not "%1"=="" set WORKER_ADDR=%1 
java -cp classes com.example.backend.Reducer %WORKER_ADDR% 
