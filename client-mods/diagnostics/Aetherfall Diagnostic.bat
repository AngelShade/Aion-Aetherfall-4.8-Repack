@echo off
cd /d "%~dp0"
start "" "bin64\aion.bin" -ip:127.0.0.1 -port:2106 -loginex +log_FileVerbosity 4 +log_Verbosity 4 +sys_PakLogMissingFiles 1
