@echo off 
REM Start a Worker 
set PORT=8084 
if not "%1"=="" set PORT=%1 
java -cp classes com.example.backend.Worker %PORT% 
