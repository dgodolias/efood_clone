@echo off 
REM Start a Worker 
set PORT=8084
set ROOT_DIR=..

if not "%1"=="" set PORT=%1 
java -cp "%ROOT_DIR%\classes" com.example.backend.Worker %PORT%
