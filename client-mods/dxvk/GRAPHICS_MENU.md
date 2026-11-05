# Graphics menu and cursor ownership

Use the normal Aetherfall launcher. Under Options > Graphics > Screen, **Use Vulkan
(requires restart)** saves on Apply/OK; Cancel discards an unapplied change. Exit
normally and relaunch to change renderer. The status label reports the active
renderer. High Quality, native graphics improvements and cursor behavior remain
available with either backend. `DXVK/renderer.ini` remains the native settings path.

## Launch checks

`ApplyGraphicsMenu.ps1` installs as `Mods/GraphicsMenu/Install-GraphicsMenu.ps1`.
`ClientOwnership.ps1` and `ClientOwnership.cs` live in `Mods/Renderer`; they require
Windows PowerShell/.NET, with PowerShell Core also supported. No player Python or
extra installation is needed. The C# archive codec follows the maintained launcher
codec and preserves the raw compressed bytes of other archive members.

Graphics owns the SHA-256 of `bin64/AionGraphicsMenu.dll`, the two native hooks at
RVAs `0x6f8650` and `0x6fca60`, its `.agfx` section header and descriptor/payload,
and its descriptor in the current import table (which another mod can relocate).
Copied descriptors belonging to other DLLs are outside its byte ownership.
`ownership.json` stores the trusted bytes and the two complete widget definitions:
`cb_use_vulkan` and `st_vulkan_status`, direct children of `page_screen` in
`global_option_dialog.xml` in base game.pak and `ui/game/global_option_dialog.xml`
in the active English data.pak. Other widgets, members and native bytes are free
to change. Damaged owned parts still throw and stop the launch script.

CursorFix owns only `9090` at file offsets `0x551c96` in Game.dll and `0x1202d7`
in XRenderD3D9.dll. Its original pairs are `7818` and `7c0c`. Renderer selection
continues to validate the complete owned DXVK wrappers and dxvk.conf.

## Records and restore

Legacy installed records automatically become `ownershipSchema: 2` only after the
owned checks pass. Each JSON replacement is atomic; interrupted migration can be
retried. Settings, timestamps and other unrelated metadata survive. A second check
does not write. Graphics `files` now contains only its own DLL; cursor records
contain instruction pairs instead of whole-file hashes or image paths. Historical
Graphics backupRoot is retained for reference and is no longer a dependency.

Graphics restore restores the two prologues, removes just its active import
(despite later mods moving the table), clears its own descriptor/code payload,
removes its two widgets and its DLL. The PE section allocation stays reserved so
later mods' section layout never moves. Other archive members retain their exact
compressed payloads. Cursor restore changes only its four instruction bytes:

```powershell
& 'CLIENT/Mods/GraphicsMenu/RestoreGraphicsMenu.ps1' -ClientPath 'CLIENT'
& 'CLIENT/Mods/CursorFix/Install-CursorFix.ps1' -ClientPath 'CLIENT' -Restore
```

Both restored states persist through launch. Explicitly enable them again using
`-Reinstall` on their installed installer (Aion must be closed). A fresh native
installation appends a section or reuses the verified reserved Graphics section;
it preserves current imports, resources, renderer choice and other mods. Full
renderer restore delegates native reversal to these same byte owners.

No new whole Game.dll/PAK package copies or Graphics/cursor recovery images are
required. Existing recovery folders and old package images are left in place.
They may contain other recovery material; do not remove them automatically.

## Build and validation

```powershell
python client-mods/dxvk/build_graphics_menu.py 'CLIENT' 'EXTERNAL_OUTPUT'
& client-mods/dxvk/InstallGraphicsMenu.ps1 -ClientPath 'CLIENT' -PackagePath 'EXTERNAL_OUTPUT'
& client-mods/dxvk/verify_graphics_menu.ps1 -ClientPath 'CLIENT' -OutputRoot 'EXTERNAL_FIXTURE'
& client-mods/dxvk/verify_native_cursor.ps1 -ClientPath 'CLIENT' -OutputRoot 'EXTERNAL_CURSOR_FIXTURE'
& client-mods/dxvk/verify_localized_menu.ps1 -ClientPath 'CLIENT'
```

The compact builder outputs only the DLL, manifest and ownership definition as
runtime payloads. Object/library/compiler files stay in the external build output.
The Graphics test accepts a fresh directory or its own marked scratch fixture,
never the original client. It exercises real installed script entry points,
corruption refusals, unrelated edits, old-record migration/no-op, own-part restores,
fresh install, renderer switches and High Quality. Other maintained checks are
`verify_install.ps1`, `verify_multi_client.ps1` and `verify_graphics_bridge.py`.
Live rendering, native clicks and mouse alignment still require the owner in game.

Ownership JSON is written as ASCII with Unicode escapes. The shared reader tries
strict UTF-8 (with or without a BOM), then the Windows ANSI code page for legacy
bytes. Run `verify_ownership_encoding.ps1` under Windows PowerShell 5.1 with
`-ClientPath <client> -OutputRoot <fresh-external-directory>`. It uses real launcher
script invocations in an accented Latin/Hangul scratch folder, checks all three
legacy encodings, six read-only launches, a moved client, owned-byte tamper refusal
and portable preparation of escaped paths. It writes `proof.json` externally.

## Compatibility callers and the next repack refresh

`client_ownership.py` is the shared read-only authoring check. The old transmog
`prepare`/`prepare_incremental` signatures now return a schema-2 compatibility
record and no extra files; `compatibility_paths` and the PowerShell path helpers
return empty sets. Browser, speech, reconnect and NPC sync entry points are
harmless validators. Core HUD/shortcut/target, Codex, account/Stigma, inventory/
window, login, service, quest and companion authoring paths skip their legacy
Graphics sync work. Their feature edits and their own transaction receipts remain.
Legacy branches and old path allowlists remain for old prepared transactions;
`service-menu-graphics-*` creation branches are unreachable for active ownership
clients. The launcher's Interface sync loops over Graphics' DLL-only `files` and
has no matching archive to sync, so no launcher source change/build is needed.

A repack refresh must carry the current original's Graphics installers/restore,
`ownership.json`, schema-2 state and compact package manifest/DLL; CursorFix's
installer; Renderer `ClientOwnership.ps1`, `ClientOwnership.cs`, `Restore.ps1`,
`SelectRenderer.ps1`, `VerifyActive.ps1` and schema-2 renderer state. Carry the
maintained updater/settings/recovery rules and `Control/Prepare-Client.ps1` too.
Old records also migrate if retained. Keep the player's renderer.ini and settings.
Ship current native binaries/archives as usual, once as native files; this slice
changes none of those three shared files. Exclude their obsolete Graphics package
copies and the unused Graphics/cursor whole-file baselines from new payloads.
The existing DXVK wrappers/config, SelectRenderer, High Quality and profile-original
files still belong in the renderer package. Publication and any repack refresh
are separate owner-authorized actions.
