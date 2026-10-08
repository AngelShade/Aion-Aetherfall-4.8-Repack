# Login announcement and remembered credentials on return

The Reset storage placement described here is superseded by the installed
8 October reconnect crash repair in `LOGIN_RECONNECT_CRASH_20261008.md`.
Native reconnect writes overwrote the old cave; the observer now lives in the
dedicated `.rreturn` section, including independently corrected restore images.
The native notice loader and production login extension remain unchanged.

Installed locally on 7 October 2026, with actual fresh-launch and in-game Logout
acceptance pending. Aion and GameServer were closed during replacement; neither
was started, stopped, restarted or attached by this repair. No GitHub push.

The user reported a blank Announcement even on a fresh launch, and empty account/
password fields with an unchecked Remember checkbox after in-game Logout. The
status line still said the login was saved. Static source and disassembly found
two defects in the earlier return patch:

- Its refresh called generic HTML file reader `Game+0x4ce620` on
  `ui/loginnotice.xml`. That file is a `LoginNotice` XML wrapper containing HTML,
  rather than an HTML file. The active English archive has nonempty `Contents`;
  the login dialog's native loader `Game+0x79df40` parses `Contents`/`URL` and
  populates its native notice widget. The production DLL now uses that routine.
- The restore depended on a hidden-to-visible flag transition. The native login
  dialog also has Reset at `0x79e7f0` (vtable slot `0x20`), which delegates child
  resets and can clear fields while the visible flag remains unchanged. A bounded
  Reset observer arms the next visible draw after all login widgets are ready.

Initial setup and native visibility notifications still work. Restoration is
consumed once per arm; subsequent draws and unrelated resets leave typed fields
alone. The Windows vault target, opt-in save, uncheck/delete, password masking,
native actions and error messages remain unchanged. No credentials were read or
recorded during this repair.

## Bounded package and installation

External development root:
`D:/Proiecte/Project Restructure/Aion Development Workspace`.

- Package: `staging/output/login-return-reset-20261007`.
- Receipt: `archives/client/remember-login-return-20261007-225046-935256`.
- Production compile: `staging/output/login-return-20261007-v2-compile`.
- Current audit: `diagnostics/login-return-installed.json`.

`prepare_reset.py` begins with the actual cumulative client and checks its current
inventory, previous production login DLL, native routines, existing return hooks,
exact observer import, unused executable padding and localized notice. It changes
only six bytes at `0x79e7f0` and 230 bytes at `0x144eb80` in Game.dll, without
changing its length, PE headers or imports. The trampoline uses the existing
Visibility import with a private null-widget Reset marker, preserves arguments/
volatile registers and XMM values, then delegates the original prologue/body.

The twelve-file transaction includes the rebuilt `AionRememberLogin.dll`,
Game.dll, active graphics payload/state, native cursor tracking and both native
restore baselines. All 26 other inventoried resources remained hash-identical.
The five earlier login hooks, queue batching fix, English UI, native icons,
Wardrobe, model key, addon key, pet/Lua archives and launchers survive.
GameServer files were not part of the transaction. Concurrent server work is
reflected only by refreshing the continuity inventory.

Installed SHA256:

| File | SHA256 |
| --- | --- |
| `bin64/Game.dll` | `1ce45e0a753f17c857dd35f2ce93ee66abc880c0e5ef6a040e3fb8c87639c6ef` |
| `bin64/AionRememberLogin.dll` | `9905219708c96e6f56349a0147e2b21a55a8b1740b814bb691383cee721e63f3` |

## Verification and remaining acceptance

Production C++ compilation passed; the DLL was not loaded by the checks. The pure
`test_lifecycle.cpp` passed initial/hidden/reset scheduling, widget readiness,
unrelated dialog rejection, repeated draws and ten resets without visibility
changes. Two Python checks interpret the actual generated machine code with a
clobbering callback and verify exact patch ownership, stack/argument preservation
and original-handler delegation. No native game code was executed.

Three installer checks passed, including denied process inspection and failure
after login DLL replacement restoring every written original file. The disposable
file-only graphics fixture passed launcher acceptance, intentional later-edit
rejection and graphics/cursor restore preservation of both Reset and queue hooks.
Hash guards were retained. All twelve installed hashes matched after replacement
and after launcher preparation. Normal launcher preparation updated only
`system.cfg` and `SystemOptionGraphics.cfg` among the preserved resources; their
current hashes are recorded in the refreshed inventory.

Installed ApplyGraphicsMenu, SelectRenderer, InstallNativeCursorPatch,
QualityProfile Startup, Enable-FreeFlight and ApplyGlobal all passed without
launching the game. Vulkan remains selected. A read-only RSA/SHA1 verification
passed all three addon archive signatures; the original stock model key hash
also matches. Current inventory passes 19 checks and records 31 client hashes,
72 client receipts and 96 server receipts.

The user must verify Announcement content before fresh login, then native
in-game Logout restoring saved account/password, checked state and Announcement.
Typed edits, unchecking Remember, normal login, first custom-window latency,
Wardrobe and pet/menu coexistence remain actual game acceptance. Offline checks
do not establish these visual/gameplay results. The optional synthetic Windows
vault/native verifier was updated but not executed under the current policy.
