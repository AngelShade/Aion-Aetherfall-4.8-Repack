@ECHO OFF
TITLE Beyond Aion - MariaDB Database Stopper
ECHO ===================================================
ECHO       Stopping MariaDB 11.8 Database Server
ECHO ===================================================
ECHO.

SET MYSQLADMIN_EXE=C:\Program Files\MariaDB 11.8\bin\mysqladmin.exe
SET "MYSQLADMIN_CONFIG=%~dp0.aion-dev\mariadb-admin.ini"

REM Check if mysqld.exe is running
tasklist /FI "IMAGENAME eq mysqld.exe" 2>NUL | find /I /N "mysqld.exe" >NUL
IF NOT "%ERRORLEVEL%"=="0" (
    ECHO [INFO] MariaDB is not running.
    ECHO.
    PING 127.0.0.1 -n 3 >NUL
    EXIT /B 0
)

IF NOT EXIST "%MYSQLADMIN_EXE%" (
    ECHO [ERROR] mysqladmin.exe not found. Database left running.
    EXIT /B 1
)

ECHO Shutting down MariaDB cleanly via mysqladmin...
REM The optional client option file is local and excluded from Git.
IF EXIST "%MYSQLADMIN_CONFIG%" (
    "%MYSQLADMIN_EXE%" --defaults-extra-file="%MYSQLADMIN_CONFIG%" shutdown
) ELSE (
    REM Without local options, ask interactively instead of storing a password here.
    "%MYSQLADMIN_EXE%" --user=root --password shutdown
)
IF ERRORLEVEL 1 (
    ECHO [ERROR] Graceful shutdown failed. Database left running.
    EXIT /B 1
)

PING 127.0.0.1 -n 3 >NUL

REM Verify if process stopped
tasklist /FI "IMAGENAME eq mysqld.exe" 2>NUL | find /I /N "mysqld.exe" >NUL
IF NOT "%ERRORLEVEL%"=="0" (
    ECHO [SUCCESS] MariaDB stopped cleanly.
) ELSE (
    ECHO [WARN] MariaDB is still shutting down. Check its console before continuing.
    EXIT /B 1
)

ECHO ===================================================
PING 127.0.0.1 -n 3 >NUL
