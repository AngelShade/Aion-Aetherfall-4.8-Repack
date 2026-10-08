# Continuous formation following and configured summons — 8 October 2026

## Current summon and distance recall - installed offline 8 October 2026

**PB-REPAIR-SUMMON-001 / PB-CUSTOM-RECALL-001** are installed in external
receipt `20261008-195139-913545`; override SHA-256
`0524e84d93eae1aba6b0e966e47475e54b82f4f07362dc080e1f031b8b11b7ff`.
Manual Summon retains the owner's own combat restriction and native ownership/
party/lifecycle/configuration checks, but cancels bot casts, trade, loot and
channels rather than requiring every companion to be idle or out of combat.
Living owned bots beyond 60m or separated by map/instance recall before trade,
transfer, movement failure and strategy handling, regardless of their current
activity/order or owner combat, and resume FOLLOW. Native dead-bot recovery and
custody-held/dismissing sessions remain protected.

Actual Java source and maintained tests/staging/install scripts are updated;
the installed delivery changes eight methods in six existing definitions and
adds PlayerBotRecall. All 171 other JAR entries and unselected members survive,
including prior follow/formation/cast/Steel Rake and missing-appearance correction.
Full 2,424-source and 53-check-source compiles, 165 recall checks, prior
679/72/99/49/44/6/17/57/314 checks, and 175-class / 24,476-reference runtime
linkage audit pass. Current client exit-saved graphics preferences are preserved.
**PB-VAL-005 native/client Summon, cancellation, far-bot recall and following
acceptance remains pending user testing.** Server/client remain off; no native
world/DB/ID fixtures or startup/attach. Next class slice remains PB-PORT-005B.
See [recall behavior and source record](PLAYERBOTS_RECALL_20261008.md).


## Previous follow regression repair - installed offline 8 October 2026

**PB-REPAIR-PACKAGING-001** fixes the reported `NoClassDefFoundError` in
`PlayerBotSession.tick`: the previous follow package imported a tick hook to
source-only `PlayerBotAppearance`, which is absent from every runtime library.
The tick aborted before AI/follow actions. The source hook is removed until that
complete custom feature is explicitly integrated; no error swallowing or new
movement workaround was added. Appearance remains source/staged only.

Installed receipt: `20261008-191905-805583`; override SHA-256
`6f72603b8a385f5f9f39c3bdd9b9a8783c06ab17b549e648195e915b9503c5c4`.
Exactly one method changes; all 176 other JAR entries and all other Session
methods are preserved. Prior follow/summon controls, Steel Rake, native cast
safety, settings and owned-alt builds survive. A read-only baseline checkpoint
`191458-608072` records seven equivalent startup-recompiled Steel Rake cache
classes and the preserved legacy instance_follow=true setting.

The new shared staging/final-package/preinstall gate checks actual executable
class/member/lambda references against runtime libraries, excluding source build
classes. It reproduces the old failure and passes the installed 174 classes /
24,383 executable member references. Full 2,423-source compilation, 52 Playerbots
check-source compilation, five gate regressions and prior 679/72/99/49/44/6/17/57/
314 production checks pass. These isolated checks do not establish gameplay.
**PB-VAL-005 actual following remains pending user retest.** Both processes remain
off; no startup/attach or native gameplay/DB/ID execution occurred. Independent
next class port remains **PB-PORT-005B Spiritmaster**. See
[the root-cause and installation record](PLAYERBOTS_LINKAGE_REPAIR_20261008.md).


## Status and scope

PB-REPAIR-FORMATION-002 and PB-CONFIG-001 are installed offline in:
`D:/Proiecte/Project Restructure/Aion Development Workspace/archives/server/game-server/backups/playerbots-recruitment-20261008-062930-344543`.

Override SHA-256: `f4855888ee12bea913327c8be2462835e292cb3c4f5ec5db5cd4e891221c5a27`.
Deployed main/playerbots.properties SHA-256:
`4083d281046f9c19c287e24c8315320613006995440d500358ef24a2586396f0`.

This repairs the existing PB-PORT-012 follow adapter and implements the user's
server configuration request. It does not replace the next independent class
slice, PB-PORT-005B Spiritmaster. Full world population/travel, broader class and
encounter parity and actual client acceptance remain unfinished.

## Upstream purpose and native mapping

Pinned mod-playerbots commit: `037c01418b5d01506917a3db9b44fd56ac5f965c`.

- `FollowActions.cpp::FollowAction::Execute/isUseful`: follow a formation value
  and resume movement when outside its allowed distance.
- `MovementActions.cpp::Follow`: a persistent native MotionMaster moving-target
  generator, plus movement/control/flight restrictions.
- Aion uses `PlayerBotFormationLayout`, `PlayerBotNavigation`, native ground
  collision and flight probes, and `PlayerBotMoveController`/shared movement tasks.
  It has no WoW MotionMaster. `PlayerBotFollowIntent` keeps only an admitted
  native follow target alive and refreshes a directly reachable formation slot.
- The pinned source's suggested group speed increase is commented out. The
  requested dynamic speed matching/catch-up policy and new summon configuration
  are local user requirements, not a claim that this exact speed formula or
  setting exists in upstream WoW Playerbots.

Both cached source hashes were verified against SHA256SUMS:
FollowActions `6be063397bf0221436761f48d945744b8ea72019bf5d07ed7caf8efc5551ecae`;
MovementActions `e98fdef2caf7c80f53c5d03d2a8b85f7e14e4539c4a4a0e75d3fe94990ef779c`.

## Lock-order refinement before handoff

The initial offline receipt was `061841-232356`. Final review identified an
evidenced dependency: calling Formation.destination from the mover or speed
adapter would reach CombatPosition.formationOrder/Service.companions under the
mover monitor. Native AI locks service/session before entering the mover, so
the reverse ordering could deadlock. This package was corrected before handoff.

The AI now resolves native role/formation/spacing geometry once and stores its
local coordinates. Movement turns/translates that snapshot around the live owner
without party, session or settings lookups. Final effective-code verification
rejects those lock-taking references in the movement/speed/goal paths. Three
Formation methods and the FollowIntent refresh/cache initialization changed;
three cache methods, its map and Geometry record were added. All other old helper
methods and 174 other prior JAR entries are retained. This schema refinement is
also cold-load-only, not eligible for live attach. Future changes to the now
existing FollowIntent helper must enumerate the changed methods in SCOPES.

## Behavior and limits

Before, follow was a DEFAULT idle action below buffs/recovery items, early quest
interactions could walk away while the owner traveled, and the movement task
stopped at a fixed slot snapshot. Catch-up measured distance to the owner's body;
a hard ratio ceiling could leave a naturally slower class permanently behind.

Following now takes MOVE relevance during owner travel, while emergency support
and encounter safety retain higher priority. NPC/object quest excursions wait
for the owner to stop; owner quest synchronization itself remains active.
Circle/Box/Line/Spread remain centered on the owner, with the existing stable role
slots and damped heading. No forward formation offset or new teleport fallback
was introduced. Explicit STAY/GUARD orders and class combat positioning remain.

Direct follow intent refreshes on the existing movement task. Arrival at one
moving-slot snapshot does not emit repeated stop/start callbacks. Every refreshed
ground destination must pass native collision/height and hazard checks; airborne
destinations retain native visibility and hazard checks. A detour or partial
collision step retains its navigator waypoint and cannot be replaced by the
direct refresh. Other actions, stop/dismiss and actual casting clear the intent.
The earlier atomic cast admission and stale callback repair remain effective.

Travel speed matches the faster of native speed and owner speed, then recovers
formation-slot error with an extra bounded 0–50 percent. Native slow/fear/confusion,
root/cannot-move, combat and map/instance restrictions still apply. Server movement
and advertised native client speed share the same adapter. Turning and obstacles
can cause a temporary gap; this is not a promise of exact geometry while casting,
rooted, navigating a blocked route or performing combat duties.

## Server options

Installed file: `target-deploy/game-server/config/main/playerbots.properties`.

```properties
gameserver.playerbots.enable = true
gameserver.playerbots.summon.enable = true
```

The existing master switch blocks recruitment when false and dismisses active
sessions through their normal tick/save lifecycle. The new switch controls
individual/party Summon commands and menu actions. Existing native config loading
consumes both keys; other limits/values were preserved. Stale PvE-only/instance
approval comments were corrected; legacy instance keys remain unused admission
gates. Neither PvP flags, duel, flight nor location is a summon refusal criterion.

Manual summons require a valid living/online/spawned owner and native destination,
owned active sessions, matching party, no party combat timer/active living aggro,
and no owner/selected-bot casting, trading, looting, item use or NPC device channel.
The whole requested list is checked before moving anyone, then checked again
before each native transition. Dead companions use native recovery. Native
relocation failure still reports failure; this does not invent a world-transition
rollback transaction. Automatic map following and wipe recovery do not call the
manual permission switch, so disabling manual summons does not disconnect them.

`PlayerBotConfig.SUMMON_ENABLED` is a new field: this package is **cold-load-only**.
No live class redefinition or config-field hot attach is supported by this update.

## Evidence and preservation

All 2,423 current GameServer sources and all Playerbots check sources compile. The full GameServer compile is recorded
externally; unrelated JUnit/script tests require their normal test dependencies
and were not run through the standalone production-JAR classpath.

Effective staged package checks:

| Check | Result |
| --- | --- |
| Production summon eligibility/config/arbitration/follow-intent/cache isolation plus 180 production-layout trajectories | 679 passed |
| Core stale mover/atomic cast/idempotent command and preference regressions | 72 passed |
| Engine/policy/lease/appearance | 99 passed; recording-JDBC preferences checked separately |
| State composition/continuers | 49 passed |
| Final spell planning | 44 passed |
| Native server/client speed packet equality and reset | 6 passed |
| Breadcrumb progress | 17 passed |
| Door/floor/routes | 57 passed |
| Movement clock/separate slots/legacy trajectory regressions | 314 passed |

The new measured trajectories use the actual centered production layout for all
four modes, five slots, owner speeds 6/12/30 and native bot speeds 1/4/8. They
require continual progress, bounded transient turn error below 8m, and recovered
steady/final slot error below 1m. They are simulations, not native client movement
or a full-party dynamic collision acceptance test.

Thirteen methods in six existing definitions were transplanted; all their
unselected methods and 166 other JAR entries are unchanged. Two helpers and their
intent record plus the cold-load config class were added. Only the cumulative
override JAR and main Playerbots config changed at installation. The newest
baseline was Steel Rake `20261008-061243-633233`, retaining core execution
`054856-494763`; both survive. Base JAR, launcher order, geometry, native scripts,
bundled UI, all 31 client hashes and native alt/build metadata were preserved.
Runtime config/playerbots remains empty; no file-backed bot metadata was recreated.
Postinstall inventory passes all 20 checks, with 73 client/102 server receipts.

Maintained scripts: `stage_follow_summon_update.py`,
`verify_follow_summon_package.py`, `install_follow_summon_offline.py`.
Changed existing helpers have explicit runtime SCOPES; the config schema addition
still requires an offline install. Artifact output and receipts live externally.

## User acceptance — PB-VAL-005 and summon controls

GameServer and client stayed off, with host process inspection before installation.
No server/client startup, restart, attach, real DB writes, world actor/ID allocation
or forced native gameplay occurred.

When the user tests, check five bots in each formation while walking/running,
turning, stopping, flying and crossing an opened door; verify regroup after fights
and unchanged caster behavior. Check individual/party summons out of combat;
requests during combat/trade/cast/loot/item/channel must refuse safely. Test both
enable switches on a normal user-controlled cold start. Actual dungeon/map/wipe
behavior and rendered smoothness remain pending acceptance.
