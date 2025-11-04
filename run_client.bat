@echo off
echo === Starting Client ===
set CLASSPATH=lib\gson-2.10.1.jar;src
java -cp %CLASSPATH% client.ClientMain
pause
