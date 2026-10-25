@echo off
setlocal
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0Website.ps1" -Action Open -AdminPanel
if errorlevel 1 pause
