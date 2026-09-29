# Transmog above Relic Appraiser

Adds **Transmog** immediately before **Relic Appraiser** in the native
**Additional Functions** submenu. The patch inserts one `RegisterMenu` call in
the existing RelicCalc OnLoad function. Its action is `/say .transmog`.

## Server

Deploy `game-server/data/handlers/playercommands/Transmog.java` and set
`transmog = 0` in `config/administration/commands.properties`. Reload chat
commands or restart the server. The command sends native remodeling dialog
page 19 anchored to the player. Normal remodeling restrictions, Kinah costs,
appearance-item consumption, and skin/dye transfer still apply.

The user has confirmed that `.transmog` opens the window successfully.

## Why the earlier standalone addon failed

The client discovered its folder but loaded no manifest or script files after
a full restart. Native loader inspection showed signature checks enabled when
mounting Plugin packages. The original RelicCalc signature verifies using the
client's `Pub.key`: hexadecimal DER RSA public key, hexadecimal RSA PKCS#1 v1.5
SHA1 signature over the encoded PAK bytes. Merely adding a PAK is insufficient.

## Prepare the local signed patch

Requires Python, JDK 17+, and the local Aion PAK codec scripts. Preparation does
not write to the client. Use a fresh output directory outside the client:

```powershell
python build_package.py --codec-directory '<codec directory>' --client-path '<client root>' --java '<path to java.exe>' --output '<staging directory>'
```

Preparation verifies all three existing signatures, checks that the RelicCalc
source contains exactly the expected insertion point, validates the PAK round
trip and every entry, and signs with an ephemeral local key. The private key
is never saved. It uses the legacy client's 1024-bit RSA/SHA1 format for
compatibility. This format is not suitable for a new signing system.

The five prepared replacements are:

- `Plugin/RelicCalc/RelicCalc.pak`: one menu-registration line added.
- `Plugin/RelicCalc/RelicCalc.pak.sig`: signature for the modified package.
- `bin32/bin32.pak.sig` and `Data/func_pet/func_pet.pak.sig`: signatures for the
  unchanged original packages under the local key.
- `Pub.key`: the matching local public key.

This changes the client's trusted package key. Future publisher-signed package
updates require restoring the original key and files first. No client DLL or
executable is patched. The builder requires the expected three signed packages
and refuses unknown signed packages.

## Install and restore

After approving the local key change, fully close Aion and run:

```powershell
./Install.ps1 -ClientPath '<client root>' -PreparedPath '<staging directory>'
```

The installer checks staged and current SHA256 hashes, backs up all five
originals under `TransmogMenu-backups/signed-<timestamp>`, installs and verifies
the replacements, and moves the failed standalone addon into the backup.
Installation errors trigger restoration from the backup. Keep this backup.

To undo, fully close Aion and run:

```powershell
./Restore.ps1 -ClientPath '<client root>' -BackupPath '<printed backup path>'
```

## Verification boundary

Server command: compiled, deployed, hot-reloaded, and confirmed working by the
user. Signed client patch: source insertion, archive contents/CRC, original
signatures, and new signatures verified. The signed patch was installed on
2026-09-29, and installed signatures and backup hashes were independently
verified. Backup: `TransmogMenu-backups/signed-20260929-150054-246` in the client.
In-game menu order and button click still require a full client restart and a
logged-in check. Confirm both Transmog and Relic Appraiser work under
Additional Functions.
