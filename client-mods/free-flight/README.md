# Free flight for the Aion 4.8 NA client

The server's `gameserver.administration.flight.free_fly` option permits a
flight request, but the client checks its own flight zones before sending it.
This client also needs `g_freefly = "1"` in its XOR-encoded `system.cfg`.

`Enable-FreeFlight.ps1` updates only that option, preserving the other settings,
comments, and line endings. `Install.ps1` backs up the client configuration and
launcher, installs the helper, and makes `Aion Start.bat` run it before launch.
Applying it at each launch preserves the setting if Aion rewrites `system.cfg`.
The launcher retains its existing pending-DLL installation and launch arguments.

```powershell
./Install.ps1 -ClientPath '<Aion 4.8 NA root>'
```

Restart Aion through `Aion Start.bat` after installation. This does not restart
GameServer. Both server flight access thresholds must allow the player.

To restore, close Aion, copy the backed-up `Aion Start.bat` and `system.cfg` to
the client root, and restore the previous helper if the backup contains one.
