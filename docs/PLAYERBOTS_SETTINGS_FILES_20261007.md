# PB-REPAIR-SETTINGS-002 — uncovered Windows gear settings save

Installed **offline** on 7 October 2026 in
`backups/playerbots-recruitment-20261007-081522-429924`. GameServer/client remain
off; no startup, restart, live attach or gameplay/native fixture execution.
This is an evidenced native persistence repair, not a new upstream port.

## Diagnosis

The user's pasted care traceback is the same 04:15:05 Tancul incident documented
in `PLAYERBOTS_SETTINGS_FILES_20261005.md`. The actual current cumulative override
still calls `PlayerBotSettingsFiles.replace` from QuestSync.State.save; that
earlier fix has not been dropped. The target care file has Archive attributes,
not ReadOnly. This does not prove which external process held the old file or
rule out future sustained locks/permissions failures.

The newest retained `server_errors.log` instead contains **gear** replacement
failures for MagicDps (6 October 00:27:44) and LeMuse (00:45:33). Both occur in
GearPolicy.State.save called by SupplyCatalog.record during supplies provisioning.
That method still attempted a single `Files.move` and did not use the installed
care retry helper. Thus the prior repair covered one writer, while an evidenced
second automated writer could still abort bot ticks with the same Windows error.

## Repair and boundaries

Only **PlayerBotGearPolicy$State.save** now uses the existing SettingsFiles helper.
Atomic complete-file replacement remains first; unsupported atomic move retains
the existing fallback. AccessDeniedException retries at 25/50/100 ms (four total
attempts, 175 ms maximum deliberate delay). Unrelated errors propagate, interruption
is retained, persistent denial remains visible and the committed target is never
truncated. Temporary-file cleanup and all serialized values remain unchanged.

Gear acquisition/provenance, supply settings, protected generated IDs, quest
witnesses, consent, budgets and owned-alt policies remain unchanged. No user
settings, attributes, ACLs or antivirus exclusions were changed. There is no
evidence identifying the locking process; do not label it antivirus as fact.
Other unreported writers were not broadened into this repair.

The source purpose remains pinned PlayerbotFactory/EquipAction/BuyAction/
ItemUsageValue at `037c01418b5d01506917a3db9b44fd56ac5f965c`, mapped to Aion's
native item/provenance policy. The OS-level file replacement is a local adapter;
no WoW algorithm needs re-porting for this failure. PB-PORT-005B remains the next
broader class slice. Full parity and earlier acceptance/repair tracks stay open.

## Verification and installation

- Offline Maven Commons/GameServer compilation passes (2,410 GameServer sources).
- **36 focused checks** pass against source and the final effective staged JAR:
  21 retry/atomic/fallback/interruption/care serialization checks, nine production
  gear owner/provenance/settings replacement/cleanup checks, and six real Windows
  deny-delete-lock preservation/bounded-failure/unlock-recovery checks. All use
  private fixture files and recording/injected operations, no world, DB, character,
  native casts, gameplay, network client or ID allocation/release.
- The initial sandboxed file test hit a sandbox rename denial. After correcting
  PowerShell's Java system-property quoting, isolated tests outside that sandbox
  passed, including deliberately holding an actual Windows fixture file open.
  This test harness issue is not evidence that the game's current care save failed.
- Stage/verify scripts restrict the payload to one existing save method, no new
  classes, unchanged schemas, and **146 other cumulative JAR entries byte-identical**.
  Care retry, trade, Sorcerer, tank, custody and native shield overlays survive.
- Offline installer independently confirmed GameServer stopped and retained hash
  guards/rollback. Process inventory separately confirmed no game/server process.
  Client hashes, base JAR, launcher, command/media and every bot preference file
  remain unchanged. Actual inventory/receipt verification is recorded under
  `target/playerbots-gear-settings`.
- Current override SHA-256:
  `670595d86d35463fb523debdde92c9cf61f2ca1c71a85cb61e79c0e7aa594e80`.

Scripts: `stage_gear_settings_update.py`, `verify_gear_settings_package.py`,
`install_gear_settings_offline.py`; private-file OS test:
`check_gear_settings_files.ps1`. Already loaded helper methods must still have
explicit runtime SCOPES in future authorized live updates; this update did not
attach to a process or change the helper.

## Acceptance and staging continuity

User gameplay acceptance is pending: native quest acceptance/care checkpoints and
supplies/gear provenance writes across bots should no longer fail on brief sharing
denials. A genuinely long lock/read-only/ACL/disk problem can still produce an error;
the repair does not suppress that evidence or guarantee all file-lock incidents end.

Appearance and follow recovery remain source/staged only. Their earlier packages
were preserved, not deployed here; packages based on `042546-945113` must be
restaged against `081522-429924` before installation to retain this save correction.
The full upstream port, historical item-ID release investigation, latency cause
and separate wipe/summon issue remain unfinished.
