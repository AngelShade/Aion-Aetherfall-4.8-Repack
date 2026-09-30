# Client diagnostics

`Aetherfall Diagnostic.bat` launches the same local Aion client with its native
`log_FileVerbosity`, `log_Verbosity`, and `sys_PakLogMissingFiles` settings enabled.
The names were checked against the installed `bin64/crysystem.dll`. The engine's
acceptance of these startup values and useful log output still require a live launch.
It does not change the regular launcher or saved graphics settings.

`Watch-AionBrowser.ps1` records Aion, Brave, and Awesomium process starts and exits.
It uses Windows process events when available and falls back to polling. It stops
15 seconds after Aion exits or after the timeout. It does not close processes,
record browser contents, or collect command lines. Polling exit status may be unavailable.

## Pet investigation, 2026-09-30

- Pet templates and checked model archives pass archive integrity checks.
- Saved pet object IDs do not match the client's known invalid NPC ID pattern.
- A live snapshot showed Red spawned beside its owner, visible in the owner's
  known list, with a spawn packet containing the correct owner and template IDs.
- The client sent pet position updates while the model was invisible.
- Initializing the movement target to the owner's position did not fix the issue;
  the user reported the name also missing. That candidate was reverted in source
  and in the running server. The backed-up server archive was restored while
  stopped after a confirmed successful save at 18:44, then the server restarted.
- Updating a JAR in place while the JVM used it caused class-loading errors. A
  fresh server process recovered login. Never overwrite an open server JAR.
- The first recovery shutdown did not reach its final save routine. The subsequent
  clean shutdown logged `Data successfully saved` before restoring the archive.

The diagnostic launch passed its logging arguments to Aion, but this build did
not produce a new engine log. The user reported Brave staying open after exit.

## Model signing cause

The CGF files for NyancoGold, DebriePET, and SatanBaby contain RSA signatures.
Recovering those signatures with the original 1024-bit public key yields valid
PKCS#1 SHA-1 blocks. The replacement menu key yields invalid blocks for all three.
The original key is retained in the first signed-menu installation backup.

The correction restores the stock model key. Both signed archive loaders must
use `Addon.key`, with all three archives signed by that key; model signatures
continue using `Pub.key`.

The six-file correction was installed and all installed hashes verified on
2026-09-30. Backup: `TransmogMenu-backups/signed-20260930-191743-540`.
The full menu builder also passed against the repaired client, verifying stock
package signatures and the separate plugin signature. The login announcement
archive retains its previously verified hash.

The user confirmed pet models render after that first correction, but the
Additional Functions entries disappeared. A read-only live process check found
RelicCalc in the stock-key loader's failed mount list, and bin32 in the addon-key
loader's failed mount list. The corrected patch changes all three archive key
references, preserving the model signature verifier's original key reference.

The archive-v2 correction was installed with all six hashes verified. Backup:
`TransmogMenu-backups/signed-20260930-194050-740`. The original model key and
login announcement hashes are unchanged. Live verification of the restored
menus together with visible pet models remains pending.
