@echo off
rem Owner settings for Windows GameServer launchers. Environment values take precedence.
rem Initial heap is independent of the maximum; both may be changed for this host.
if not defined AETHERFALL_GS_MIN_HEAP set "AETHERFALL_GS_MIN_HEAP=1024m"
rem Automatic maximum scales with system/container memory; no fixed GB ceiling.
if not defined AETHERFALL_GS_MAX_RAM_PERCENT set "AETHERFALL_GS_MAX_RAM_PERCENT=25"
rem For an explicit maximum, uncomment and edit (for example 8g, 16g or 64g):
rem set "AETHERFALL_GS_MAX_HEAP=8g"
