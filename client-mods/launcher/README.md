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
helpers stop the launch and expose a local log. When `Mods/CustomQuests/Quests`
exists, Play first loads its quest packages through the native archive merger.
The Mods page shows only the parent **Custom Quests** entry; individual quests
remain nested inside it. Unchanged packages cause no native archive writes.
New/changed packages preserve existing records and untouched compressed members,
reject ID/ownership conflicts, require the game closed before resource changes,
and keep backups at the external `BackupRoot` in the folder's settings.json.
No Python or additional runtime is required on the client. These are the existing helpers'
checks, not additional full-client scans. Renderer selection still uses native
`DXVK/renderer.ini`; Vulkan effects use the existing per-process environment.
The connection arguments are read directly from `Mods/Launcher/connection.json`.

When the Companions artwork index is installed, the final preparation step checks
its two source UI archives and rebuilds matching offsets, decoding keys, CRCs and
archive fingerprints from the maintained sprite recipe. Interface/theme switches
also update that index in their existing rollback transaction. This keeps a skin
change from silently removing Companions buttons, tabs, slots and class emblems.
Invalid DDS data stops preparation; archive hash guards remain enabled. No Python
is needed on the client. `--companion-art-sync --client <folder> --report <file>`
runs this same focused preparation without opening the launcher or starting Aion.

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

The sidebar's **Languages** page has themed **On-screen text** and **Character
& NPC voices** dropdowns. Choices save immediately for the next Play; **Apply
languages** remains available. Refreshing the page or pack library never saves
temporary English defaults. The original development client selects languages
only in its game launcher; the original Hub retains its server dashboard. The
repack offers Languages in both its Hub and game launcher, using the same saved
choices. Language selection, checkboxes and renderer controls use the Aetherfall theme. English uses the original locale when both choices are English.
Other combinations compose `L10N/fra` and pass `-lang:fra` to the actual native
client without changing the country, connection or original English archives.
This is a reserved native resource slot, not a claim that the selected content
is French. The NA executable admits only ENU/DEU/FRA for country 1 and rejects
invented locale names. French source packs belong under `Mods/Languages/Packs/fra`,
so the generated slot is never mistaken for a French text/voice pack. An existing
unowned FRA folder blocks preparation rather than overwriting its resources.
Preparation runs after the existing mod helpers and regenerates only when its
inputs or selections change. Close Aion normally before regeneration; additional
clients can reuse an unchanged prepared locale.

Korean text comes from this client's native 4.8 base string tables and dialogues.
The composer preserves current IDs, layouts, custom keys and dialogue actions;
unmatched/custom text stays English. Native Korean font definitions are included.
Voice packs are adapted by `prepare_voice_pack.py` to exact installed 4.8 audio
paths, with CRC-checked output and an explicit English fallback count. Videos
keep their original audio unless a selected voice pack includes matching videos.
Text and voice packs for other languages are discovered independently under
`Mods/Languages/Packs/<code>/data/data.pak` and `sounds/`, or `L10N/<code>`.
The shared selector reads the small `Mods/Languages/catalog.json` shipped by the
core updater. **Languages in the repack Aetherfall Hub** owns the pack library; each absent
text or voice component has its own **Download** button and size. The game launcher
selects installed languages and has no download controls. English is included and is the default for fresh installations. Native
Korean text is included; Korean and Japanese voice downloads are independent.
German, French, Spanish, Russian and Simplified Chinese have separate text packs.
Japanese text remains unavailable. Existing selections are never changed by a
download or pack installation. Selecting an installed language saves it immediately
for the next Play, including when the game launcher is already open.

Hub downloads come from the Aetherfall GitHub release repository
(`AngelShade/Aion-Aetherfall-4.8-Repack`) and use the shared updater transfer helper with 64-bit progress, percentage,
speed, remaining bytes, ETA and package counts. **Pause** retains partial bytes;
**Resume** continues them. The expected size and SHA-256 are verified before
installation. **Install ZIP** accepts the identical catalogued archive for offline
use. Close Aion normally before installation; a verified download is retained when
the game is still running. Staging, backups, copied-file hashes and rollback protect
existing packs; edited/unowned files block replacement. No game component is started
or stopped. Updating the core preserves installed packs, cache and selections.

Only installed packs can be selected. Missing catalogued components are disabled
in the dropdown until installed; voice-only languages do not invent text options. Imported text must match native string keys
and IDs; its gameplay resources never replace the installed modded layouts.
`prepare_text_pack.py` emits only admitted translated string rows, compatible-path
native dialogues, font faces and font definitions. It excludes foreign server
branding, URLs, regional settings, layouts and custom override archives. String
format-token mismatches retain English. The runtime merges font faces into current
styles, preserving sizes, colours, custom definitions and native glyph policy.

Existing downloaded ZIPs can also be installed when their exact version is retained
in the trusted catalog's `previousVersions`. The library lists current packs only.
Unchanged pack content retains its original version, URL and checksum across core
updates; changed content gets a new immutable version while old trusted entries
remain available for offline import. `--previous-assets` supplies verified earlier
ZIPs when preparing a fresh release output, without recompressing them.

`tools/distribution/build_language_packs.py` creates one verified ZIP per language
component from normal text outputs and the original's adapted voice packs. The core
inventory excludes `Mods/Languages/Packs`, `Cache` and `Installed`; updates never
delete existing optional packs. The maintained publisher validates and uploads
optional ZIPs alongside the matching release only when publication is requested.
Local preparation does not make their future HTTPS links available online.

Offline language preparation uses the same code as Play:
`--language-sync --client <client> --text kor --voice kor --output <external-stage>
--packs <external-stage>/Mods/Languages/Packs --report <external-report>`.
Install that exact generated cohort with `install.py --launcher-only --languages
--stage <stage> --languages-stage <separate-composed-output-if-used> --client <client>
--backup <external-backup>` after normal closure. `--check-only` validates the
cohort and computes the delivery without writing to the installation.
It selects the prepared pair while retaining other launcher preferences. Future
switch backups use the external `Mods/Languages/settings.json` BackupRoot.

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

Mods window (`ModsWindow.cs`, `Mods.cs`): **Mods** in the side menu opens one
browsable window listing every `Mods/<name>/mod.json` (title, description,
`Listed`, `Files` plus `InstalledMods.json` records, missing-file status), with
search and a "Show support components" filter for unlisted mods. Choices are
saved at once and applied by Play while every Aion client is closed:

- **Aetherfall Interface** maps onto its own states: Off / Skin / Full HUD
  (`off`/`on`/`layout` in `Mods/AetherfallUI/state.json`). When its manifest has a
  `themes` block (interface 1.3.6+), a Full HUD theme picker sets `Theme`; Play
  uses `themes.layout[theme][archive][entry]` payloads for the layout state (else
  `entries[*].layout`) and records `AppliedTheme`. A theme change rewrites only the
  archives that theme varies. Theme payloads count as recorded entry states, so
  switching away from a theme is never mistaken for another mod's change.
- A mod whose DLL is imported by `bin64/Game.dll` or `bin64/aion.bin` (read from
  their PE import tables when the window opens) is locked as "Required by
  Game.dll/aion.bin"; a mod that records Game.dll or aion.bin itself is "Part of".
  If the import tables cannot be read, every mod DLL is treated as required.
- Other mods are "Always on" unless their mod.json opts in with
  `"Switch": "files"`. Off then parks the listed loose files outside `Mods/` in
  `Mods/<name>/disabled/` (hash-checked against InstalledMods.json, all-or-nothing,
  recorded in `Mods/<name>/state.json`); On moves them back after verifying the
  parked hashes. Archives, signatures, keys, executables and files recorded by
  another mod are never switchable.

`--mods-apply --client <root> [--report <file>]` applies the file switches
headlessly. Aetherfall Interface (`AetherfallInterface.cs`): Play applies the
chosen state before Custom Quests, only while Aion is closed. Failures are logged and do
not block Play. `--aetherfall-interface --client <root> [--state off|on|layout]
[--theme <id>] [--report <file>]` applies a state from the command line (used by the installer).
