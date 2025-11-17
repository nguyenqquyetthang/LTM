@echo off
echo === Starting Game Server (SQL Server Mode) ===
REM Thêm thư viện JDBC của SQL Server vào classpath
set CLASSPATH=out;lib\gson-2.10.1.jar;lib\mssql-jdbc-12.8.1.jre11.jar
echo Classpath: %CLASSPATH%
echo.

REM Chạy server chính
java -cp %CLASSPATH% server.core.Server

echo.
pause
