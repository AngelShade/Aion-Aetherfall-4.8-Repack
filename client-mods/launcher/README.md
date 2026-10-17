# Aetherfall launcher

`Aetherfall Launcher.exe` is the graphical entry point in the original client.
It uses WPF on Windows .NET Framework 4.8, with no browser, additional runtime
download, account storage, DLL injection or server updater. Support assets and
preferences are in `Mods/Launcher`; installed game binaries stay in place.

The artwork and transparent feather-and-metal frame were generated with the
built-in imagegen tool. Their complete prompts are in `assets/prompts.md`.
The header uses the original client's AION image and its installed Aetherfall
wordmark, with their native source rectangles. `assets/logo-source.json` records
the source files and hashes; neither logo is generated lettering.
The interface adds drifting aether particles, a pulsing Play glow, hover/focus
states and a loading bar. Motion can be disabled. After Play, preparation details
stay in the local log; the bar is the only loading indicator. Completed helper
steps advance it to 60%, the game window to 85%, and native login readiness to
100%. These are milestones, not a measured percentage of game assets.

Play runs the existing Mods startup helpers in their established order, with
hidden child PowerShell windows and captured output: Graphics menu, renderer,
cursor, graphics settings, flight, then the queued world-art package. Failed
helpers stop the launch and expose a local log. These are the existing helpers'
checks, not additional full-client scans. Renderer selection still uses native
`DXVK/renderer.ini`; Vulkan effects use the existing per-process environment.
The connection arguments are read directly from `Mods/Launcher/connection.json`.
The installer migrates the existing BAT's exact arguments into that file and
archives the BAT externally before removing it from the client root. The EXE
starts Aion directly; no BAT is required. Edit `Arguments` in connection.json
to change the server address or login switches.

The launcher reads local listening ports passively. It never probes LoginServer
with empty TCP connections. A port being open is not authenticated login proof.
Remote endpoints are shown as remote, without a manufactured online status.

Closing during preparation waits for the current helper and stops before the
next step. It does not terminate a helper while it may be copying files. A
pending `game.dll.patched` requires its update to be completed separately.
The original client is the installation target. Repack delivery is separate,
including the repack's portability preflight; don't copy this EXE there alone.

Build with `build.ps1 -OutputPath <external-stage> -IconPath <client>/AionClient.ico`.
`install.py --stage <stage> --client <client> --backup <external-backup>` installs
the EXE/assets and adds the launcher to the current mod inventory. It retains
the existing connection arguments, game files, renderer and user settings. Preview rendering uses
`--preview <png> [--page Overview|Mods|Settings]` against a staging copy and does
not launch Aion or execute preparation helpers. No gameplay is claimed by a
preview or successful build.

The existing AionRememberLogin module receives a unique event name through the
launched game's environment. Its native login draw callback signals that event
after widgets and the announcement have been restored and subsequent visible
draws have run for at least 250 ms. Creating a game window alone never completes
the bar. The launcher waits up to four minutes, then offers an error and log if
readiness was not reported. It minimizes (or closes, when selected) only after
this signal. This uses no process attachment, memory reader or screenshot probe.
Build the existing native module with `remember-login/prepare.compile_dll` into
the same stage, then install with `--login-module` to deliver the matched pair.

Startup presentation starts at the native splash routine, before it loads a
bitmap or creates its window. The guarded `prepare_startup.py` bridge extends
only the supported x64 aion.bin and imports the existing login module. During a
validated launcher session the splash routine is skipped entirely. Ordinary
starts without the private event keep their original splash behavior.

The native module holds AIONClientWndClass1.0 hidden at creation and blocks
show/activation requests until the login draw signal. Its UI-thread window
procedure also filters visibility changes. Native window API references in the
named game modules are redirected inside the client; Windows DLL code is not
patched. The renderer's visibility queries retain logical visibility so its
loading and login draw loop can continue while the actual window stays hidden.
This replaces DWM cloaking and polling after a splash has already appeared.
Unrelated windows and error prompts retain their normal behavior.

After native login readiness, the launcher releases the per-launch event. The
client restores its taskbar style and shows the completed login on its UI thread.
The launcher waits for that window to become visible before minimizing. Parent
exit, cancellation or the bounded deadline also release the client. The local
.presentation.log records early gate activation, renderer creation, native login
readiness and actual reveal without account or password values. Install the
matched launcher/module/bridge with `--login-module --startup-bridge`; the
installer backs up aion.bin and checks its exact supported source hash.

The launcher never forces Topmost during loading. Window discovery is read-only; presentation changes happen inside the client
on its own UI thread. Login receives focus only if the launcher was
still the foreground application; switching to another app is respected.

ReShade 6.8's existing persistent .i/.cso/.asm cache, performance mode and disabled-
effect skipping remain enabled. Its runtime.cpp logs 'Successfully compiled' even
when its shader bytecode came from cache. The observed latest two effect timings
were 0.068/0.097 seconds with unchanged cache files. That log message does not
prove an expensive full compilation or explain the much longer startup delay.
No shader effects, presets, cache files or graphics quality are removed to hide
the message. Actual next-launch visual and responsiveness acceptance is pending.
Sources: https://github.com/crosire/reshade/blob/v6.8.0/source/runtime.cpp
and https://learn.microsoft.com/en-us/windows/win32/api/winuser/nf-winuser-showwindowasync.

The release caption reads `Mods/release.json` each time the launcher opens. The
single maintained release source is the repository's `release.json`; the normal
launcher builder/installer distributes it. Changing the release metadata does
not require editing launcher code or rebuilding the executable.

Installed Mods discovers the immediate `Mods` folders and reads each folder's
`mod.json` (`Title`, `Description`, `Listed`, optional client-relative `Files`).
The installer seeds missing descriptions; the launcher also creates missing
metadata for newly added folders. Existing descriptions are retained. Backups
are excluded; support-only entries can set `Listed` to false. Registered native
files come from `Mods/InstalledMods.json`, with optional extra required files
from the per-mod metadata. Missing payloads are reported, never created. Reading
these descriptions does not hash or reinstall the client. Edit a mod's metadata
and reopen the launcher to update its title/description without a rebuild.

Portable repacks are recognized through their parent REPACK.json. Before ordinary
startup helpers, the launcher runs that package's fixed Control/Prepare-Client.ps1
with the current Client path in a hidden process. The helper checks the current
Game.dll and aion.bin ownership and rebinds metadata/shader paths after a move.
The existing root client CMD is only a compatibility shortcut to the graphical
EXE; the client BAT is archived and removed. tools/update-repack.py distributes
installed server/client updates with portable metadata, active recovery baselines,
fresh-schema compatibility and changed-file hashes, without copying player rows
or starting any component. Original-client startup remains independent of this
conditional repack preflight.

Multi-client is built into this launcher. An existing game process no longer
blocks Play. The existing helpers reuse the current renderer/profile and retain
all no-live-mutation guards; renderer changes and pending patches require every
client closed. Reopening a running launcher signals its per-install activation
event and restores its window, rather than reporting an already-open error.
After each login handoff it can launch another child with an independent ready
and reveal event. Use separate game accounts. Mods/MultiClient documents the
feature and requires the launcher EXE. --launcher-only installs this correction
without replacing native game modules, startup bridge or generated artwork.
