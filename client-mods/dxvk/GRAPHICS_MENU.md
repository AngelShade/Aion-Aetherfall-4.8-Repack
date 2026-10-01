# Vulkan option in Aion's Graphics menu

The normal **64-bit** client now has a prepared native checkbox under
**Options → Graphics → Screen → Use Vulkan (requires restart)**.

Uncheck it, press **Apply** or **OK**, exit Aion normally, and reopen
**Aion Start.bat**. The launcher selects Windows Direct3D 9. Check it and
repeat these steps to return to DXVK 3.1.1 / Vulkan. **Cancel** discards an
unapplied checkbox change. The status line identifies the current renderer
or explains how to apply the next one. Launching aion.bin directly bypasses
the renderer selector; use the normal launcher.

The terrain textures, native lighting/water changes, High Quality profile,
and repaired cursor remain with either backend. The private Vulkan ReShade
effects are active only with Vulkan. They are not installed as a Direct3D 9
effect wrapper in this implementation.

## Implementation

- `build_graphics_menu.py` requires the exact currently modified Game.dll
  SHA256. It adds one import section and two entry hooks to its native
  graphics dialog. Existing custom menus, inventory/search/browser hooks,
  and both native cursor instruction patches are retained.
- Both `Data/ui/game/game.pak` and the English override
  `L10N/enu/Data/data.pak` gain the checkbox and status label only.
  Every other archive entry is verified byte-identical: 269 base UI entries
  and 18,506 English entries. The English archive takes precedence in this
  client; patching only the base archive leaves the visible menu unchanged.
- `AionGraphicsMenu.dll` uses the verified native widget methods to load
  checkbox state, mark the video options dirty, save on Apply/OK, and reset
  on Cancel. Existing actions delegate to the original callback.
- `DXVK/renderer.ini` stores `Vulkan=0/1` (next launch) and
  `ActiveVulkan=0/1` (selected at the most recent normal launch).
- `SelectRenderer.ps1` validates both DXVK DLL hashes before changing either
  file, refuses while Aion is running, and parks DLLs as
  `bin32/d3d9.dxvk-disabled.dll` and `bin64/d3d9.dxvk-disabled.dll` when off.
  It restores those exact DLLs when on. The client compatibility wrapper
  supports an optional local DXVK DLL; its documented normal installation
  works without one: [Beyond Aion version.dll](https://github.com/beyond-aion/aion-version-dll#installation).
- Cursor/install verification and full DXVK restoration understand the
  parked DLLs. Cursor tracking is updated when the menu extends Game.dll.
- `Pub.key`, `Addon.key`, signed plugin archives, and pet/model validation
  are unchanged.

## Deploy and verify

```powershell
python .\client-mods\dxvk\build_graphics_menu.py 'CLIENT'
.\client-mods\dxvk\InstallGraphicsMenu.ps1 -ClientPath 'CLIENT'
```

If Aion is running, installation is queued. `Aion Start.bat` applies the
menu before checking the cursor patch and selecting the renderer. Backups
are stored under `DXVK-backups/graphics-menu-*` and
`DXVK-backups/menu-launcher-*`. Payload and original/current client checks
stop installation if a later modification conflicts.

Passed checks: compiled bridge behavior with native widget ABI fixtures;
queued install; closed-client guard; repeat application; both architecture
switches; preserved cursor validation while Vulkan is off; tamper rejection
before changing other files; exact menu restore with later launcher changes
preserved; hidden GPU shader draw, exact readback and presentation with
Windows Direct3D 9 in x64 and x86. DXVK GPU checks passed earlier.

The first launch installed the native helper, but the user's screenshot
confirmed the checkbox was absent. The English override was then corrected,
installed while Aion was closed, and the normal launcher relaunched Aion.
The unchanged native helper loaded in responsive process 28040. Both cursor
instructions remain active. Incremental archive application and exact
restoration passed with the corrected English package.

**Pending:** visible layout, Apply/OK/Cancel in the real dialog, and an
actual game restart using each renderer. The bridge fixture and GPU checks
do not prove those live paths.
The 32-bit renderer can be selected, but this menu patch targets only the
64-bit executable used by the normal launcher.

## Restore

Exit Aion normally, then run:

```powershell
& 'CLIENT\DXVK\graphics-menu\RestoreGraphicsMenu.ps1' -ClientPath 'CLIENT'
```

This restores the exact previous Game.dll and UI archive, retaining the
working cursor and other earlier modifications. The selector remains in
the DXVK launcher block, keeping the last selected backend. Full
`DXVK/Restore.ps1` also restores the menu before uninstalling DXVK and its
cursor changes.

For recovery when the menu is not accessible, close Aion, edit only
`Vulkan=0` or `Vulkan=1` in `DXVK/renderer.ini`, then use Aion Start.bat.
