@ECHO OFF
TITLE Beyond Aion 4.8 - Server Stopper
ECHO ===================================================
ECHO       Stopping Beyond Aion 4.8 Servers
ECHO ===================================================
ECHO.
TASKKILL /FI "WINDOWTITLE eq Aion Emu - Game Server*" /T /F >NUL 2>&1
REM Also stop a game server started without a visible console window.
FOR /F "tokens=5" %%P IN ('NETSTAT -ANO -P TCP ^| FINDSTR /C:":7777 " ^| FINDSTR /C:"LISTENING"') DO TASKKILL /PID %%P /T /F >NUL 2>&1
TASKKILL /FI "WINDOWTITLE eq Aion Emu - Chat Server*" /T /F >NUL 2>&1
TASKKILL /FI "WINDOWTITLE eq Aion Emu - Login Server*" /T /F >NUL 2>&1
ECHO All game servers (Login, Chat, Game) stopped.
ECHO.
ECHO Note: If you also want to stop MariaDB Database, run Stop_Database.bat
ECHO ===================================================
PING 127.0.0.1 -n 3 >NUL
