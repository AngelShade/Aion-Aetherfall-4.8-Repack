# Camera fix mode

`bin64/version.dll` is the Beyond Aion compatibility DLL (1.5.1, MIT). Besides
the IP, shader and stack fixes it hooks the cursor to fix stuttering camera
rotation on Windows 10 and later. For the first 200 ms of a right/left-drag it
rewrites mouse moves that Windows still reports from the last visible cursor
position. Upstream adds that offset reversed, so the first moment of a drag
turns the camera the wrong way. When the drag starts within 100 px of the
re-centre point the rewrite is skipped and the raw jump passes through.

`camera_fix_mode.patch` applies to upstream commit `0231025` (the 1.5.1 source)
and adds `[CameraFix] Mode` in `bin64/mods.ini`, read once at startup:

| Mode | Behaviour |
| --- | --- |
| `corrected` (default) | first-drag moves keep their direction; re-arms on every mouse-button press |
| `upstream` | original 1.5.1 behaviour |
| `off` | camera fix hooks not installed (stock Windows input) |

With the native UI cursor (DXVK cursor mode 1) Windows never shows the system
cursor again, so upstream never re-arms after the first drag and a stale move
from the click point leaks in after the game re-centres the cursor (a 600+ px
snap). `corrected` re-arms from each button-down message instead.

Only `camera_fix.cpp` changes; exports and all other fixes are unchanged.

```powershell
./build.ps1 -OutputRoot <fresh short path>
./Install.ps1 -ClientPath <client> -Dll <OutputRoot>/version.dll -BackupRoot <external backups> [-Mode corrected|upstream|off]
./Restore.ps1 -ClientPath <client> -Backup <backup folder from Install>
```

Install requires the client closed and the verified official 1.5.1 x64 file; it
backs up `version.dll` and `mods.ini` and writes a receipt in the backup folder.
To switch modes later, edit `bin64/mods.ini` and restart the client.
The DXVK `Install.ps1`/`verify_install.ps1` check the official 1.5.1 hash and
will report the patched DLL; restore first before reinstalling DXVK.
The 32-bit client keeps the official DLL.
