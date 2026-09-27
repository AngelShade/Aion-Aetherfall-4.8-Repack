@ECHO OFF
TITLE Beyond Aion 4.8 - Server Launcher
ECHO ===================================================
ECHO       Starting Beyond Aion 4.8 Complete Servers
ECHO ===================================================
ECHO.

SET MYSQLD_EXE=C:\Program Files\MariaDB 11.8\bin\mysqld.exe
SET MY_INI=C:\Program Files\MariaDB 11.8\data\my.ini

REM [0/3] Check and start Database if not already running
tasklist /FI "IMAGENAME eq mysqld.exe" 2>NUL | find /I /N "mysqld.exe" >NUL
IF "%ERRORLEVEL%"=="0" (
    ECHO [Database] MariaDB is already running on port 3306.
) ELSE (
    ECHO [Database] Starting MariaDB Database Server...
    START "MariaDB 11.8 Database Server" "%MYSQLD_EXE%" --defaults-file="%MY_INI%" --console
    PING 127.0.0.1 -n 5 >NUL
)

ECHO.
ECHO [1/3] Starting Login Server...
START "Aion Emu - Login Server" /D "C:\Git\aion-server\target-deploy\login-server" start.bat

PING 127.0.0.1 -n 4 >NUL

ECHO [2/3] Starting Chat Server...
START "Aion Emu - Chat Server" /D "C:\Git\aion-server\target-deploy\chat-server" start.bat

PING 127.0.0.1 -n 4 >NUL

ECHO [3/3] Starting Game Server...
NETSTAT -ANO -P TCP | FINDSTR /C:":7777 " | FINDSTR /C:"LISTENING" >NUL
IF "%ERRORLEVEL%"=="0" (
    ECHO [Game Server] Already running on port 7777.
) ELSE (
    START "Aion Emu - Game Server" /D "C:\Git\aion-server\target-deploy\game-server" start.bat
)

ECHO.
ECHO ===================================================
ECHO All servers launched! Keep all console windows open while playing.
ECHO - Database:     Port 3306 (MariaDB)
ECHO - Login Server: Port 2106
ECHO - Chat Server:  Port 10241
ECHO - Game Server:  Port 7777
ECHO.
ECHO Auto-account creation is ENABLED (log in with any username/password).
ECHO ===================================================
PING 127.0.0.1 -n 6 >NUL
