@ECHO OFF
TITLE Beyond Aion - MariaDB Database Starter
ECHO ===================================================
ECHO       Starting MariaDB 11.8 Database Server
ECHO ===================================================
ECHO.

SET MYSQLD_EXE=C:\Program Files\MariaDB 11.8\bin\mysqld.exe
SET MY_INI=C:\Program Files\MariaDB 11.8\data\my.ini

REM Check if mysqld.exe is already running
tasklist /FI "IMAGENAME eq mysqld.exe" 2>NUL | find /I /N "mysqld.exe" >NUL
IF "%ERRORLEVEL%"=="0" (
    ECHO [INFO] MariaDB / MySQL is ALREADY RUNNING.
    ECHO [INFO] Database is active on 127.0.0.1:3306.
    ECHO.
    PING 127.0.0.1 -n 3 >NUL
    EXIT /B 0
)

REM Verify executable exists
IF NOT EXIST "%MYSQLD_EXE%" (
    ECHO [ERROR] MariaDB executable not found at:
    ECHO         "%MYSQLD_EXE%"
    PAUSE
    EXIT /B 1
)

ECHO Starting MariaDB Server in console mode...
START "MariaDB 11.8 Database Server" "%MYSQLD_EXE%" --defaults-file="%MY_INI%" --console

ECHO Waiting for database to initialize...
PING 127.0.0.1 -n 5 >NUL

tasklist /FI "IMAGENAME eq mysqld.exe" 2>NUL | find /I /N "mysqld.exe" >NUL
IF "%ERRORLEVEL%"=="0" (
    ECHO [SUCCESS] MariaDB started successfully on 127.0.0.1:3306.
) ELSE (
    ECHO [ERROR] Failed to start MariaDB. Please check my.ini or error logs.
)

ECHO ===================================================
PING 127.0.0.1 -n 3 >NUL
