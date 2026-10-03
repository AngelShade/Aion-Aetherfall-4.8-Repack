@echo off
setlocal
title Aetherfall - GameServer
cd /d "%~dp0"

netstat -ano -p tcp | findstr /C:":7777 " | findstr /C:"LISTENING" >nul
if not errorlevel 1 goto LOG

:START
echo Starting Aetherfall GameServer in this console...
java -Xms1024m -Xmx2560m -XX:+UseNUMA -XX:+UseCompactObjectHeaders -DconsoleEncoding=CP850 -cp "libs/*" com.aionemu.gameserver.GameServer
if errorlevel 3 goto FAILED
if errorlevel 2 goto START
if errorlevel 1 goto FAILED
echo GameServer has shut down normally.
exit /b 0

:FAILED
echo GameServer terminated abnormally. See the output above and the log directory.
exit /b 1

:LOG
echo GameServer is already running. Showing its live log in this CMD window.
echo Ctrl+C stops this log view; the running GameServer stays online.
echo.
if not exist "log\server_console.log" (
    echo No server console log exists yet.
    exit /b 1
)
powershell -NoProfile -Command "Get-Content -LiteralPath 'log\server_console.log' -Tail 50 -Wait"
exit /b 0
