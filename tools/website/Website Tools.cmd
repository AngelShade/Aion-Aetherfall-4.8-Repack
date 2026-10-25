@echo off
setlocal
echo Aetherfall Website Tools
echo 1. Open website
echo 2. Create or reset website administrator
echo 3. Website settings and optional Gemini API key
echo 4. Initialize native website integration using configured databases
choice /c 1234 /n /m "Choose: "
set "action=Open"
if errorlevel 4 (set "action=Initialize") else if errorlevel 3 (set "action=Configure") else if errorlevel 2 (set "action=Admin")
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0Website.ps1" -Action %action%
if errorlevel 1 pause
