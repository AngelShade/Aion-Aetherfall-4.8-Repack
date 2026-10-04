# AI reload, Idian Depths portals and Fire Temple Kromede

Installed on 4 October 2026, using the actual cumulative server override rather
than a complete source build. Original request references conversation
`3fa295de-8066-40cd-bc27-bbf21a2f744e`; its complete transcript was read under
`.system_generated/logs/transcript_full.jsonl` in the supplied conversation directory.

## Log failures and their cause

The 09:58:42 `No AI found for name portal` exception occurred during `//reload ai`.
The original reload cleared the shared registry before compiling handlers.
Scheduled NPC constructors then observed an empty/partial registry. Compilation
ran on `PacketProcessor:0`, causing the 09:58:56 18,440 ms chat-packet warning.

`AIRegistryReload` now builds and validates a separate registry, publishes one
immutable snapshot, then releases the previous script context. A failed compile,
duplicate handler or validation failure retains the previous working registry.
The normal authorized Reload command submits compilation to the long-running
pool and reports success/failure after completion. Concurrent command requests
are coalesced. Existing NPCs retain their current AI until they respawn.

The portal rotation now constructs both replacements before deleting the old
entrances and reschedules in `finally`. Its 3600–18000 interval now uses seconds
(1–5 hours), correcting the prior 3.6–18 second rotation. Live inspection found
no surviving scheduled rotation after the original exception. Recovery adopted
the existing entrance witnesses and restored exactly one entrance per faction.

## Kromede encounter

Both 212846 and 214621 use `kromede`; 280501 uses `kromede_trap`. The old running
NPC templates still held `aggressive`/`trap` despite the earlier disk edits; the
three live mappings were explicitly updated. The loaded instance handler also
still had the old 10% roll; its one spawn method is now 50/50. Only one variant
spawns per newly created Fire Temple instance; killing it does not spawn the
other. Mirror quests, boss stats, loot and unrelated NPC templates are retained.

The shared encounter uses native casts and completion callbacks:

- Blessing of Rock is a self defensive stat buff on spawn/home reset.
- Native chase/leash closes to range. Strong Cry precedes Repeat Impact Strikes;
  failed/interrupted steps retry and ordinary native hate selects the target.
- Guilty Verdict repeats at 30–35 seconds above half health, shortening to
  23–27 seconds below it. Successful Verdict completion creates three traps
  within 3–5 meters of the boss.
- At approximately 25% HP, one Verdict → Miserable Struggle → Verdict combo
  runs, then ordinary attacks and faster Verdict cycles resume.
- Cry, Verdict and Struggle select Kromede as the AoE center. Impact retains its
  actual target/range. Native templates retain effects, radii, cast times and
  level 28. Verdict uses an encounter-local physical template copy; the shared
  16674 template used by other NPCs remains unchanged.
- Traps arm for 5.5 seconds, then cast Area Aggravate Wound at their own position.
  Destruction cancels the cast; successful detonation waits for actual native
  hit timing before deletion. Interrupted casts have a cleanup deadline.
- Return, home, death and despawn invalidate callbacks and delete this boss's
  owned traps. Old callbacks cannot affect a later pull. Native return/reset
  retains authoritative health, aggro and home behavior.

## Installation and preservation

Core receipt: `target-deploy/game-server/backups/playerbots-recruitment-20261004-102535-101269`.
Final script receipt: `target-deploy/game-server/backups/playerbots-recruitment-20261004-102954-276296`.
The final receipt inherits the complete cumulative feature inventory. Effective
core rollback definitions remain in the core receipt's `effective-rollback.jar`.

The update transplants eight existing methods in four effective classes:
AIEngine, IdianDepthPortalSpawner, the loaded Reload command and the loaded Fire
Temple instance handler. All 79 unrelated override entries were byte-preserved.
The preceding 10:03 companion update was included when staging was refreshed.
The deployed base JAR, launcher order, companion preferences, presets, UI and
client files were retained. All original override classes were preloaded before
replacing the JAR. Revision 23 applied the core definitions; revision 26 performed
the final native verification. The next unique attach revision must exceed 26.

`stage_kromede_repair.py` and `install_kromede_repair.py` provide bounded staging,
hash checks, preloading and rollback. The deployed Reload overlay deliberately
retains its installed decomposable reload behavior; the broader source migration
remains separate work.

## Verification and remaining acceptance

- Selected core classes and all changed scripts compiled with Java 25. The actual
  server script engine compiled/validated all 459 AI handlers.
- `AIRegistryCheck`: 100,000 concurrent reads during a staged reload, plus compile,
  duplicate and validation failures retaining the previous registry.
- `KromedeEncounterCheck`: 83 opening/retry/cadence/threshold/combo/reset checks.
- `target/kromede-repair/native-runtime-check-v26.txt`: the actual Reload.execute
  returned in 0 ms while 64 portal lookups succeeded during compilation. Native
  isolated fixtures passed both boss mappings, nearby/distant/caster AoE selection,
  the physical Verdict copy, Impact range, trap targeting, owned-trap reset, a real
  trap cast and cleanup after native hit time. All fixture world objects were
  removed; human and companion identities were retained.
- Earlier verification revisions exposed incomplete fixture initialization
  (effect controller, then motion list). These were fixture failures, corrected
  in revision 26; their diagnostic logs were retained.
- Installed-mod inventory checks passed after deployment. These establish
  preservation and native behavior, not a completed player-controlled dungeon run.

Create a fresh Fire Temple instance for acceptance. Check the opening, threat
switching, half-health cadence, the single quarter-health combo, trap destruction
and wipe/re-pull reset. No existing fight was reset or replaced for this update.

Separate pre-existing issue: Tanku's periodic inventory save reported duplicate
item ID 191267. Another chat installed a bounded companion inventory ownership
transaction check in receipt `backups/playerbots-recruitment-20261004-103105-414348`.
The latest override retains this repair's AI registry and portal entries exactly;
the Kromede scripts remain recorded in `...102954-276296`. Tanku's actual subsequent
persistence outcome was not verified by this encounter repair.
