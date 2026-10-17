# Remember account and password

The checkbox aligns with the account/password input fields. Its status line aligns
with the checkbox text, uses a larger warm-gold font and has a native dark text
shadow. The checkbox and account buttons use bright shaded labels, with additional
space above the button row. The maintained layout is in `prepare.py`.

Native Aion 4.8 NA x64 login checkbox, labeled **Remember Account & Password**, below the password field, using the existing `v5_check` skin. The New Account and password buttons sit beneath the checkbox and status line; all share the login form's middle vertical anchor. Default is unchecked. Checking it saves both fields when Login or Enter dispatches `login_auth_server`; the next client launch restores both fields and the checked state. The original password widget remains masked. Unchecking immediately deletes the saved login. Storage failures are shown beneath the checkbox.

Credentials use the current Windows user's Credential Manager, with local-computer persistence and a separate target per client installation. There are no plaintext settings files, launcher password arguments, credential diagnostics, automatic login or server changes. Temporary credential buffers are wiped. Windows API reference: [CredWriteW](https://learn.microsoft.com/en-us/windows/win32/api/wincred/nf-wincred-credwritew).

`prepare.py` stages the DLL, the base and active English login layouts, one English string, and composed graphics/cursor recovery baselines. All other archive entries and existing executable hooks are retained. The three guarded native sites are login initialization (`79e7c2`), login button event dispatch (`79ec50`), and the common UI action dispatcher (`4f0c0`). The latter covers Login and the password field's Enter action. Each trampoline preserves the native arguments, volatile registers, flags and XMM registers, and delegates original actions.

Prepare and verify:

```powershell
python client-mods/remember-login/prepare.py --client '<CLIENT_ROOT>' --output output/remember-login/client-v3
python client-mods/remember-login/verify_package.py output/remember-login/client-v3
python client-mods/remember-login/verify_native.py
```

The vault test requires a normal Windows logon session; the restricted tool session returns Windows error 1312. It uses an isolated synthetic test credential and deletes it in `finally`. No real account/password is read by the verification scripts.

With Aion closed, `install.ps1 -PreparedPath <package>` backs up every replaced file, validates hashes and paths, copies the extension before Game.dll, and restores the originals on installation failure. `restore.ps1 -BackupPath <RememberLogin-backups/timestamp>` restores this patch after validating that no later patch changed the files. Remove a saved login with the checkbox before removing the feature, or remove its `Aetherfall/Aion48/RememberLogin/…` entry in Windows Credential Manager.

For clients with the initial version installed, `revise_layout.py --client <client> --output <fresh-output>` stages only the two button positions and shorter label, including the graphics locale recovery copy. Apply it with the same installer. Its archive verification checks every other entry and XML widget remains unchanged; it preserves the native login DLL and vault behavior.

Native fixtures and trampoline execution establish functional and preservation behavior. Actual login-screen rendering and an authenticated restart test require opening Aion and using the player's own credentials.

## Returning to login without restarting the client

The first `prepare_return.py` upgrade added two observers: the login dialog draw entry (`79e820`) and the flag-change handler (`4ae000`). That version misses native resets that leave the visible flag unchanged. Its generic HTML-file reload also clears the special `LoginNotice` XML content, including on a fresh launch. The installed repair below supersedes that return behavior.

Stage and verify the incremental upgrade before closing the client:

```powershell
python client-mods/remember-login/prepare_return.py --client '<CLIENT_ROOT>' --output output/reconnect-fix/client-return-1
python client-mods/remember-login/verify_return.py output/reconnect-fix/client-return-1
python client-mods/remember-login/verify_native.py
```

These are historical first-upgrade commands. Use the current repair for an existing cumulative client; do not install an old package over later mods.

## Native reset and announcement repair, 7 October 2026

The original Reset cave placement is superseded by the 8 October repair below.

`prepare_reset.py` stages a bounded observer at the login dialog's Reset entry (`79e7f0`) and the rebuilt production DLL. Reset arms one restore on the next visible draw when all login widgets exist, even when the visible bit did not change. Initial setup and visibility notifications remain supported. Repeated draws and unrelated resets do not overwrite typed fields. The production DLL calls the login dialog's own localized `LoginNotice` XML loader (`79df40`), which handles `Contents` and `URL`, rather than treating the XML wrapper as an HTML file.

The installation retains all five existing login hooks, the recent custom-window queue repair, archives, vault target/save/delete behavior and masked password widget. Graphics package/state, cursor tracking and both native restore baselines are updated together. Recovery receipts are external under `AION_DEV_ROOT/archives/client/remember-login-return-*`.

Stage with `prepare_reset.py --client <client> --output <fresh external package> --dll <compiled production DLL> --inventory <fresh passing inventory>`. Compile the production DLL with `prepare.compile_dll` into the external staging directory. Verify launcher/restore preservation with `client-mods/transmog-menu/tests/verify_window_queue_package.py --package <package>`. Install using `install_reset.py --package <package>` after process inspection establishes all Aion clients are closed; `--verify-only` changes no files. The shared installer retains hash guards, bounded paths, verified recovery copies and rollback.

The pure C++ `test_lifecycle.cpp` and Python `test_reset_patch.py` check scheduling and interpret the generated trampoline without loading game code or accessing credentials. The optional `verify_native.py` uses a separate synthetic credential and is not part of the current keep-client/server-off checks. Actual fresh-launch Announcement rendering, saved credentials after in-game Logout and continued typing/opt-out behavior require the user's in-game test. See `docs/LOGIN_RETURN_20261007.md`.

## AFK reconnect crash repair, 8 October 2026

The crash dump showed native runtime writes overwriting the Reset cave at
`0x144ec00`. `prepare_reconnect.py --client <client> --output <fresh external
package> --inventory <current inventory>` relocates the exact installed legacy
hook into the dedicated `.rreturn` section and independently repairs graphics/
cursor restore images using their own imports. The login extension remains
byte-identical. `install_reset.py --package <package>` supports this guarded
twelve-file transaction and requires every Aion client closed. Use
`--verify-only` for hash validation without installation.

The stager requires all client inventory checks and exact current client hashes;
it records separate server checks without mutating server files. The current
server receipt mismatch remains visible in the refreshed inventory. Offline
interpreter, runtime-storage-write regression, rollback and disposable restore
checks pass. Actual AFK Reconnect/Login and ordinary Logout/Login remain pending.
See `docs/LOGIN_RECONNECT_CRASH_20261008.md`.

## Launcher startup presentation, 17 October 2026

The launcher startup bridge imports this existing DLL before the native splash
routine. `AionStartupSuppressSplash` validates the private launch events and
starts `startup_gate.h` outside the loader lock. The splash routine is skipped;
the renderer stays hidden until the existing native account/password draw signal.
Presentation and Credential Manager behavior remain separate. Normal launches
without that session retain their original presentation. Source and installation
records are maintained with `client-mods/launcher`; visual acceptance remains the
player's next launch, not a successful build.
