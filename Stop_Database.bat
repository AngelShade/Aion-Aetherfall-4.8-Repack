@ECHO OFF
TITLE Beyond Aion - MariaDB Database Stopper
ECHO ===================================================
ECHO       Stopping MariaDB 11.8 Database Server
ECHO ===================================================
ECHO.

SET MYSQLADMIN_EXE=C:\Program Files\MariaDB 11.8\bin\mysqladmin.exe

REM Check if mysqld.exe is running
tasklist /FI "IMAGENAME eq mysqld.exe" 2>NUL | find /I /N "mysqld.exe" >NUL
IF NOT "%ERRORLEVEL%"=="0" (
    ECHO [INFO] MariaDB is not running.
    ECHO.
    PING 127.0.0.1 -n 3 >NUL
    EXIT /B 0
)

IF EXIST "%MYSQLADMIN_EXE%" (
    ECHO Shutting down MariaDB cleanly via mysqladmin...
    "%MYSQLADMIN_EXE%" -u root --password shutdown >NUL 2>&1
) ELSE (
    ECHO mysqladmin.exe not found, terminating mysqld process directly...
    TASKKILL /F /IM mysqld.exe >NUL 2>&1
)

PING 127.0.0.1 -n 3 >NUL

REM Verify if process stopped
tasklist /FI "IMAGENAME eq mysqld.exe" 2>NUL | find /I /N "mysqld.exe" >NUL
IF NOT "%ERRORLEVEL%"=="0" (
    ECHO [SUCCESS] MariaDB stopped cleanly.
) ELSE (
    ECHO [WARN] Graceful shutdown taking longer or requires force stop.
    ECHO Terminating remaining mysqld processes...
    TASKKILL /F /IM mysqld.exe >NUL 2>&1
    ECHO [SUCCESS] MariaDB stopped.
)

ECHO ===================================================
PING 127.0.0.1 -n 3 >NUL
