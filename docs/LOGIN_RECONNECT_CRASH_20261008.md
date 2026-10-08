# AFK reconnect/login crash repair — 8 October 2026

Installed and verified offline. The user reported a client crash after AFK,
Reconnect, then Login. Actual AFK reconnect/login and ordinary Logout/Login
acceptance remain pending with the user. No game/server startup, restart, stop,
live attach or native game fixture was performed.

## Evidence and cause

Windows WER and `aion.bin.27904.dmp`, captured 8 October at 04:48 by the host,
both identify `Game.dll+0x144ec36`, exception `0x80000004` (single step).
This is inside the Reset observer added by the 7 October login-return repair.
The dump's mapped observer differs from the installed file: native runtime data
overwrote its instructions around `0x144ec00`. Two unchanged native instructions,
at `0x144e213` and `0x144eb02`, reference that same runtime address.

The overwritten instructions disturbed stack restoration before `popfq`, causing
the single-step exception. Zero-filled file bytes in the stock image were
mistaken for unused executable padding. This was a defect in the recent patch;
the earlier pure instruction interpreter did not model native writes into that
storage. No logout-delay change was made.

The dump was inspected for exception metadata, code and module-address stack
entries only. Credentials and character heap contents were not extracted or
recorded. The dump remains in its original Windows CrashDumps location.

## Correction and preservation

`patch_reset.relocate` restores all 230 legacy cave bytes to their original zeros
and moves the observer into the unused file-alignment tail of the dedicated
`.rreturn` patch section, extending only that section's VirtualSize.
The installed observer is at `0x190f4c0`, with the same Visibility callback import
and original native Reset continuation. PE length, section count, imports and
all five previous login hooks are retained. The production
`AionRememberLogin.dll` is byte-identical; vault behavior, native notice loader,
password masking and opt-out remain unchanged.

The graphics/cursor restore images have a different layout. Their observer is
independently relocated to `0x190e4a0`, using their own Visibility IAT at
`0x190e2c0`. The prior shared-byte transplant had also copied the main image's
IAT displacement into these differently laid-out baselines. The bounded repair
corrects that while retaining their original graphics/cursor differences.
All old recovery paths remain intact.

Package:
`D:/Proiecte/Project Restructure/Aion Development Workspace/staging/output/login-reconnect-relocation-20261008-v2`.

Installed receipt:
`D:/Proiecte/Project Restructure/Aion Development Workspace/archives/client/remember-login-return-20261008-050005-613742`.

The twelve-file transaction includes Game.dll, the unchanged login extension,
graphics package/state, native cursor tracking and both corrected restore images.
Installer process inspection confirmed Aion closed after the user's “closed”
response. Hash guards and verified rollback copies remain enabled.

Installed Game.dll SHA256:
`41008662f6011538cb1abf869faa671703063dfa8884fe6dfb716b66f52a99a2`.

Installed login extension SHA256, unchanged:
`9905219708c96e6f56349a0147e2b21a55a8b1740b814bb691383cee721e63f3`.

## Verification

- Four offline Reset checks pass, including argument/stack preservation at all
  three observer locations, simulated native storage overwrites, exact changed
  byte bounds and correct per-image restore imports. No native game code ran.
- Four isolated installer checks pass, including failed process inspection and
  rollback after interrupted reconnect replacement.
- Disposable file-only launcher/graphics/cursor restoration passes; later
  unrecognized edits are rejected, and both restored images retain the repair.
- All twelve installed payload hashes match after launcher preparation. Of the
  26 preserved files, only `system.cfg` and `SystemOptionGraphics.cfg` changed
  through normal installed launcher preparation. All other preserved hashes
  match, including English UI, native icons, Wardrobe, signed addons and keys.
- Installed ApplyGraphicsMenu, SelectRenderer, InstallNativeCursorPatch,
  QualityProfile Startup, Enable-FreeFlight and ApplyGlobal pass without launching
  Aion. Vulkan remains selected. All three addon RSA/SHA1 signatures pass.
- The refreshed inventory passes all 15 client checks, including safe Reset
  storage in the main and both restore images; it records 73 client and 97 server
  historical receipts. The separate `recruitment_override_and_launcher` server
  check already failed before this repair and remains unresolved. This is not a
  passing whole-installation audit. Server JAR, override hashes and launcher are
  unchanged by this client repair.

Current evidence is in external `diagnostics/reconnect-installed-20261008.json`;
`docs/INSTALLED_MODS.json` contains the same refreshed snapshot. Failed first
staging output is retained externally and is not an installable package.

The user should verify AFK → Reconnect → Login, then ordinary in-game Logout →
Login, including remembered fields and Announcement. Offline success does not
establish acceptance of these real-client sequences.
