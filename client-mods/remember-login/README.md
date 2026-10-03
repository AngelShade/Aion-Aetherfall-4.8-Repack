# Remember account and password

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

`prepare_return.py` upgrades an existing Remember Login installation. It stages two native observers: the login dialog draw entry (`79e820`) and the existing flag-change handler (`4ae000`). The latter only observes visibility transitions for the actual login dialog singleton and otherwise immediately returns. All native flag/render handlers, arguments, volatile registers, XMM registers and flags are preserved. The initial setup and each hidden-to-visible transition arm one restore before the first login draw. Repeated draws and unrelated flag changes do not reload credentials or overwrite typed edits. The same restore reloads the existing `ui/loginnotice.xml` in `htmlview_notice` through the native HTML reader. No timer, AFK service or client diagnostic is added.

Stage and verify the incremental upgrade before closing the client:

```powershell
python client-mods/remember-login/prepare_return.py --client '<CLIENT_ROOT>' --output output/reconnect-fix/client-return-1
python client-mods/remember-login/verify_return.py output/reconnect-fix/client-return-1
python client-mods/remember-login/verify_native.py
```

Install the verified package with the existing guarded `install.ps1` after closing Aion. Archives and login layout remain unchanged. The native verifier uses only its separate synthetic credential and tests repeated visibility transitions, reload after scene reset, exactly one announcement load per return and retention of typed edits. The actual Game.dll observers are executed at a different mapping address to validate relative branches and original-handler delegation. A fresh installation using the original `prepare.py` should receive this incremental upgrade afterward.
