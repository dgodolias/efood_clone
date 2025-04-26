@echo off 
Essential components for running the distributed system 
 
USAGE: 
 
1. For Worker machine: 
   - Run: start_worker.bat [PORT] 
   - Example: start_worker.bat 8081 
 
2. For Master+Reducer machine: 
   - First run: start_reducer.bat [WORKER_IP:PORT] 
   - Example: start_reducer.bat 192.168.1.101:8081 
   - Then run: start_master.bat [REDUCER_HOST] [WORKER_IP:PORT] 
   - Example: start_master.bat localhost 192.168.1.101:8081 
 
REQUIREMENTS: 
- Java must be installed on all machines 
- Ensure network connectivity between machines 
- Firewalls must allow connections on ports 8080 (Master), 8090 (Reducer), and 8081+ (Workers) 
