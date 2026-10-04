# Tank boss-dragging repair — 4 October 2026

**PB-REPAIR-TANK-001: installed live/on disk; offline and loaded-method verified.**
PB-VAL-005 actual boss positioning/terrain/client combat remains pending.
GameServer was already running as PID 24908. Automatic approval review initially
rejected deployment because the earlier approval covered a different repair.
The user then explicitly approved **Apply this tank repair live**. Fresh agent 44
preloaded original classes and atomically replaced one class/two methods,
preserving five companion sessions and one human connection. No server start,
stop/restart, client replacement, forced tick/cast/movement or DB/ID write occurred.
Latest cumulative receipt: `backups/playerbots-recruitment-20261004-232048-163778`.

## Cause and bounded behavior

`PlayerBotCoordination.tankFacing` recalculated a destination beyond the enemy's
current center, away from nearby party members, at radius
`max(2, boss.frontRadius + 1.5)`. Session tick repeatedly offered it above normal
combat movement priority. When the enemy followed its tank, the destination
moved again. This produced an unbounded pull rather than a stable facing change.
The targeted-area spread branch could also repeatedly move an active tank six
metres away when its own enemy cast at it.

The repaired `tankFacing` returns no automatic walking goal. Native melee reach,
lost-aggro acquisition, offense and real hazard escape remain authoritative.
Targeted-area spread skips a tank only when that caster currently targets it;
ranged/non-tank victims and secondary casters still retain their spread behavior.
Ground hazard detection and movement/cast/resource/animation/ownership gates are
unchanged. The fix prevents this self-generated boss dragging; it does not change
boss spawn locations, native reset/leash rules, scripted boss movement or provide
full encounter-specific tank/pull coordination.

## Upstream and native mapping

This is a repair to the installed Aion companion movement adapter, not a new
class strategy or a claim of full PB-PORT-007 parity. It was selected because the
user reported concrete new PB-VAL-005 movement failures. The broader next port
slice remains PB-PORT-005; pull/main-offtank assignment remains partial.

Pinned revision: `037c01418b5d01506917a3db9b44fd56ac5f965c`.
Exact cached references are
`third-party/playerbots/upstream/src/Ai/Base/Actions/MovementActions.cpp`
(`MovementAction::MoveTo(WorldObject*, float, MovementPriority)` and
`SetFacingTargetAction::Execute/isUseful`) and
`third-party/playerbots/upstream/src/Ai/Class/Warrior/Strategy/TankWarriorStrategy.cpp`
(`getDefaultActions`, `InitTriggers`, reach-melee alternatives/lose-aggro actions).
Their reach/facing/combat distinction maps to Aion Session ReachAction,
PlayerBotNavigation.approach, native player attacks/skills and PlayerBotTank hate
priorities. The continuously recentered boss-facing walk was an Aion-specific
rule, not a verified upstream requirement. No WoW spells, coordinates or threat
thresholds were copied into Aion.

## Package and preservation

Package: `target/playerbots-tank-position/package`; stager:
`client-mods/playerbots/stage_tank_position_update.py`.
Actual latest baseline: `backups/playerbots-recruitment-20261004-224922-627052`,
including the native custody repair, ranged spacing, engine composition and all
earlier mods. Baseline override SHA-256:
`b94e8d41b0a4acb24430694f768548f4d523a750ce84a67beefc749c3aa3b4b2`.
Installed override SHA-256:
`c14e843ed5c47dfac22da04e04dfa2e803ccba1b19989ead78c76a1ce7fe9c3a`.

Only `PlayerBotCoordination.tankFacing` and `spread` are replaced. The original
class schema and unused synthetic methods are retained for native redefinition;
both changed methods are explicitly in runtime SCOPES. All 134 earlier cumulative
entries remain byte-identical. No new helper class, media, command, base JAR or
launcher change occurred. Agent 44 used compatible original-class preloading,
hash guards and effective rollback. Existing helpers still need explicit runtime
SCOPES when edited; future update agent revisions must exceed 44. If another
install advances the baseline, restage/reverify before applying.

## Verification and remaining acceptance

- Full server/commons/command/test source compilation and all offline companion
  checks passed. The initial new world-free fixture required map/instance
  overrides; after correction its 22 tests passed and the remaining suite checks
  were completed against the same compiled snapshot.
- The 22-test moving-boss/moving-party/large-body/targeted-cast regression fails
  against the old actual effective override plus base JAR and passes against the
  repair. These fixtures allocate no native world actors, DB rows or IDs.
- Effective staged checks also pass: eight native hate/taunt assertions,
  35 encounter/hazard assertions, 38 final position/formation/cast assertions,
  and all 85 production custody checks (including expected rollback injection).
- The package verifier checks exact selected methods, unchanged schema,
  compiled-source linkage, ZIP integrity and preservation of every earlier entry
  and launcher/media file. Evidence is `package/verification.json`.
- Captured effective definitions match installed bytecode for 57 selected tank,
  Session/ReachAction/CastAction, navigation and hazard methods/lambdas. A real
  read-only Tanku policy check found no facing-movement goal; Tanku had no target,
  so this is not a live boss-fight acceptance claim. Six definitions were captured
  using a null transformer; no AI tick, cast, movement or character writes.
- The postinstall disk/source audit passes 51 pinned hashes and 140 effective
  methods/lambdas, with the older ClassCombat rune veto still inactive. Obsolete
  facing synthetic methods remain only for original schema compatibility.
- Current installed mod inventory passes 15 checks with 31 unchanged client hashes,
  70 client/79 server historical receipts. Base JAR, launcher, media, all 134 earlier
  package entries and all 44 settings/preset/archive files are unchanged. There
  was no provenance growth during this verification. Before snapshot is
  `target/playerbots-tank-position/inventory-before.json`.

Loaded-definition and real read-only tank policy verification passed via
`PlayerBotTankPositionCaptureAgent1` and `verify_tank_position_runtime.py`.
Evidence: `target/playerbots-tank-position/runtime-capture`, `postinstall-audit`
and `preservation.json`, plus the cumulative receipt's runtime/preflight records.
Actual boss fight, stable tank position near
the engagement/spawn, terrain, necessary hazard escape, attacks and recovery are
still client/native acceptance. Historical item-ID release-path investigation,
Tanku's separate wipe/summon error, post-revive acceptance and full port remain open.
