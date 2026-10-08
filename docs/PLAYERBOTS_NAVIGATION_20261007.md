# PB-REPAIR-NAV-002 — generic bot ground navigation, 7 October 2026

## Scope and current status

**Initial adapter installed; user reported continued door failure. General follow-progress
correction installed offline in `190312-034119`; gameplay acceptance remains open.** This is an Aion movement
adapter repair within PB-PORT-012/PB-VAL-005, not a new encounter script or full
world-navmesh parity claim. Next broader class port remains PB-PORT-005B.

The user reports opened-door failures and stationary bots in several rooms in
Steel Rake/Cabin, including after summoning inside. A later screenshot identifies
Door 74 at the Sailor Waiting Room in Cabin; several other room names remain unknown. Bots
can enter/leave combat during the failure. The requested solution belongs in the
shared bot movement engine, without per-dungeon/per-door/per-room exceptions.

The rejected FollowRecovery teleport package was removed from source and disk in
`playerbots-recruitment-20261007-171732-465446`, restoring the complete native
metadata baseline and the package's incidental bots.js change. Historical
recovery packages remain evidence and must not be reinstalled.

## Source and native mapping

Pinned upstream `037c01418b5d01506917a3db9b44fd56ac5f965c`:

- `src/Ai/Base/Actions/FollowActions.cpp::FollowAction::Execute` maps owner
  formation following to `PlayerBotNavigation.follow`.
- `src/Ai/Base/Actions/MovementActions.cpp::MoveTo`, `MoveToLOS` map shared
  path/reach/visible firing-position movement to `Navigation.move/approach` and
  Aion's ground probes. WoW PathGenerator/MotionMaster are not available in Aion.
- Native `GeoMap`, `DespawnableNode`, `StaticDoor.setOpen`, `GeoService` and
  `PlayerBotMoveController` supply collision, per-instance door activation,
  normal movement speed/effects/packets and world position updates.

This repair is necessary for existing follow/combat/retreat/hazard actions to
execute through dynamic geometry. It does not require completing the independent
world-population, invitation, trade or class backlog first.

## Evidence and behavior

The installed models.mesh aliases the Shulack door's closed name and opened
`_state2` name to one identical closed leaf. Native `setDoorState` switches their
activation correctly, but the exported shape still blocks an opened doorway.
The matching client CGA contains an 85-degree opening animation; its hash is
`1ec03bf27876faf37cc49c152284bd18968eca4ac45deaa2971b2b50a8b50be4`.
That asset diagnosis establishes this data conflict. **No models.mesh or .geo
data was installed/changed.** The discarded data-only reconstruction package is
diagnostic evidence, not an approved installation path.

`PlayerBotGroundNavigation` handles this pattern structurally in any map:

- It pairs native closed/open door nodes by their native ID. It recognizes equal
  triangle geometry at equal world transforms, including separate identical
  mesh records. Only a thin vertical solid leaf qualifies. Shared frames,
  correctly transformed opened leaves, other obstacles and floor meshes remain.
- A collision is ignored only for the qualifying opened leaf while its opened
  node is active and closed node inactive **in the current instance**. States
  are read on every probe. No map/model/door/room IDs are hardcoded in production.
- Native movement remains the fast path. Failed/partial probes try supported
  0.35 m floor steps, bounded to 128 steps, with body collision checks and small
  height correction. Steep surfaces, cliffs, missing ground and walls still block.
- Local A* uses a 1 m grid within the existing 24 m radius, 128-node and 6 ms
  limits. Short progress below the old 0.5 m minimum can enter a tight passage.
- The controller follows ground at every movement tick instead of interpolating
  one straight XYZ line between distant destinations. It rechecks dynamic
  obstacles and retains native speed, movement modifiers, world updates,
  observers, packet/formation behavior and the existing airborne movement path.
- Ranged approach searches reachable firing positions near its preferred range
  when sight is blocked. A stale opened-door sight mesh cannot veto walking to
  the other side. Native target sight at the proposed position is required;
  native cast sight/range and the short-spell/ranged approach policy remain.

These probes are shared by follow, combat approach, retreat and hazard escape.
There is no combat-state recovery gate, stalled-follower teleport, progression
door auto-opening or owned-alt build modification.

## Initial installation and preservation (superseded by continuation below)

Initial cumulative receipt:
`D:/Proiecte/Project Restructure/Aion Development Workspace/archives/server/game-server/backups/playerbots-recruitment-20261007-175949-485037`.

Override SHA-256:
`a39133ac5c6caa958a8ce7fd65c7b4812d56481297c44b164592503f6d1c3cdc`.

Five methods in four existing definitions:

| Definition | Changed methods |
| --- | --- |
| PlayerBotNavigation | move, approach |
| PlayerBotMoveController | moveStep |
| PlayerBotPathfinder | find |
| GeoService | findGroundMovementCollision |

Two new definitions: `PlayerBotGroundNavigation` and its `DoorPair` record.
Pathfinder and GeoService are appended overrides of existing base classes, not
new runtime schemas. All other members of those definitions are preserved.
**151 earlier override entries remain byte-identical**. The base JAR, launcher,
command/UI, 93 settings/media files and 31 current client hashes are unchanged.
All earlier inventory/metadata/alt/care/trade/class/formation/revival mods survive.

Unchanged source/deployed models.mesh SHA-256:
`95f0343953fdc1b7dcf3830e54dbe685027dacc634d9b6f515652c632a8f193d`.

Maintained tools: `stage_navigation_update.py`, `install_navigation_offline.py`,
`remove_follow_recovery_offline.py`, and `tooling/PlayerBotDungeonGeoProbe.java`.
Existing method changes have explicit runtime SCOPES. Future live updates still
require applicable lifecycle authorization and a fresh agent revision/preloading;
none occurred here. Offline installation refuses an unconfirmed process state.

## Offline validation and limits

- Full Commons/GameServer production compilation passes (2,413 server sources),
  including the final follow-progress correction.
- **40 isolated native mesh/route checks** pass against the effective package
  and installed override: arbitrary maps/door IDs, independent instances,
  closed/reclosed/both-active states, equal/non-equal/transformed meshes, frames,
  ordinary walls, cliffs, gaps, steep/walkable ramps, small stairs, floor offsets,
  tick-sized floor movement, ranged sight recovery, tight/broad routes and budgets.
- Actual installed native meshes prove **15/15** opened-door walking crossings
  differ from closed-door stops: Steel Rake 10/10, Cabin 5/5. The original data
  yields identical open/closed obstacles. These are data fixtures, not production
  exceptions. Probe starts use the floor below door centre, avoiding a roof-layer
  sampling error at door 74.
- Installed mod inventory: **16 checks**, 31 client hashes, 70 client/89 server
  historical receipts. Receipt counts are not a feature/acceptance count.
- No GameServer/client startup/restart/attach, native actor fixture, DB write or
  ID allocation occurred. Development outputs stay external under
  `Aion Development Workspace/diagnostics/playerbots-door-room-20261007` and
  `staging/output/playerbots-navigation-20261007-final`.

The reported room freezes are **not proven resolved** by these probes. The
generator-room samples move normally; several stationary spawn samples are
barrels/props or points on another layer, not player/bot room evidence. Do not
move those objects or claim their counts prove the user's failure. The shared
floor/controller defects have been repaired, but actual summoned-bot movement,
party formations, combat transitions, casting and all reported rooms remain
PB-VAL-005 acceptance pending with the user. Missing/unsupported geometry is not
permission to walk through walls or invent a floor. Full world travel/transport/
escape/class strategies and the item-ID release investigation remain unfinished.

## User failure report and follow-progress continuation — 7 October

The user explicitly requires a shared engine solution for every door situation,
not a per-dungeon patch. An open-door screenshot and `.bot status` show all five
companions in FOLLOW / NON_COMBAT / ready, reporting blocked by geometry. The
initial installed adapter does not close this gameplay report.

`//info` supplies real coordinates in Cabin map 300460000, instance 2:

| Actor | X | Y | Z | Heading |
| --- | --- | --- | --- | --- |
| LeMuse | 333.34015 | 543.85626 | 951.60925 | 68 |
| Baby | 324.8792 | 557.62787 | 951.7954 | 85 |

The isolated native geometry probe can move 4 m around both actual origins and
5.25 m from the bot toward the owner. Small tick steps also traverse the sampled
opened doorway. Thus the earlier long chord counts alone do not establish the
actual follow executor's destination/progress behavior.

An old unvisited trail head can survive indefinitely while formations advance
elsewhere. `follow` scanned all later visible points, including a breadcrumb at
the bot's current position; an opened door's stale sight mesh occludes newer
owner points. The executor then repeatedly selected an already reached point and
reported geometry blockage for zero movement.

`PlayerBotNavigationTrail.consume` now consumes the prefix through the **newest**
reached point, even if earlier points were never visited. Subsequent visible-goal
selection rejects reached samples. `move` reports an already reached waypoint
accurately. No door/map IDs, combat gate, teleport fallback or formation/spacing
preference change is introduced.

A **constructed** history using the actual reported coordinates reproduces old
selection of the bot's current point while the owner is invisible; prefix
consumption selects the owner and advances 5.25 m through the native geometry.
The live trail contents were not read, so this is a verified engine defect and
counterexample consistent with the report, not proof of the complete live cause.
Seventeen progress checks and forty existing mesh/route checks pass against the
corrected staged package. Source compilation passes. Actual game acceptance is
still open for doors, rooms, parties and combat transitions.

Correction package (installed offline at 19:03 Bucharest):
`D:/Proiecte/Project Restructure/Aion Development Workspace/staging/output/playerbots-navigation-trail-20261007-final`.
SHA-256 `8fac72f9facd7b56204f6d3488fcf6eb88fdb6b2e01f65270024fc424aa2007c`.
Only Navigation.follow/move change; one new trail helper is added. **156 installed
entries are byte-identical**. All installed ground/door helpers, base JAR,
launcher, media, preferences and client are retained. Existing helper replacement
without explicit runtime SCOPES now fails staging.

Host CIM inspection confirmed GameServer PID 33600 and client PID 9948 running
after the user's startup at approximately 18:20/18:21 Bucharest time. During that running-state inspection the agent
did not start/stop/restart/attach or replace runtime files. The sandbox's
Get-Process view omitted these host processes; offline guards now require
successful CIM command-line inspection and refuse a running or unknown state.
The user closed GameServer/client; authorized host CIM confirmed both off. The
guarded installer rechecked shutdown twice and installed the correction without
starting/attaching either application. Current cumulative receipt:
`D:/Proiecte/Project Restructure/Aion Development Workspace/archives/server/game-server/backups/playerbots-recruitment-20261007-190312-034119`.
Current override SHA-256 is `8fac72f9facd7b56204f6d3488fcf6eb88fdb6b2e01f65270024fc424aa2007c`.
All 61 current settings/media files were preserved exactly at installation time;
this current snapshot supersedes the historical 93-file initial-install snapshot.
The 17 progress and 40 geometry checks also pass against the installed JAR. The
refreshed inventory passes 16 checks, retaining all 31 previous client hashes,
70 client and 91 server historical receipts. Source/effective selected methods
match and 156 prior JAR entries remain byte-identical. Gameplay acceptance remains
pending with the user; the complete live trail/cause was not inspected.

The final installed native mesh probe also passes all 15 opened/closed door
comparisons and the constructed trail counterexample at the reported coordinates;
see external `trail-installed.txt` / `trail-installed.csv`. The maintained stager
reproduces the final installed package with no method or file differences, while
preserving byte-identical installed helpers across debug-only rebuild changes.

Current cumulative installation is now `191742-640906` (PB-SCOPE-012B native
property preferences), SHA-256 `83a3143148a2e3a8f5b28894742399db05f8c302111990df41b2adf99b2dc978`; all navigation definitions remain
byte-identical to the verified `190312-034119` repair. The 52 migrated property
files were subsequently retired; nine media/preset/removal files survive.


## PB-REPAIR-NAV-003 — physical see-through ground support installed

The user confirms PB-REPAIR-NAV-002 opened-door crossing works. A new report
showed four companions blocked in Central Engine Room while one moved:
map 300100000 / instance 3, MagicDps at (538.78186,444.04572,882.08905),
owner at (548.76917,447.96683,882.0684).

A read-only native-mesh reproduction found ordinary physical floor at the bot,
but `GeoMap.getZ` returned NaN at the owner. An ALL-mask ray hit the actual floor
at precisely Z882.0684: collision flags136 (PHYSICAL_SEE_THROUGH128 + WALK8).
Horizontal collision checks already include PHYSICAL_SEE_THROUGH; the native
physical-only floor query omitted it. This is a shared engine repair for the
installed PB-PORT-012 movement adapter, not a room/door/model exception or new
WoW port. Its native sources are `CollisionIntention`, `GeoMap.getZ` and native
slope-filtered collision rays; no upstream travel algorithm was substituted.

`PlayerBotGroundSupport.floor` combines the native terrain/physical result with
native slope-filtered see-through physical hits in the same narrow vertical
window. The highest support matches native floor semantics. WALK-only, material
and skill meshes do not become floors. `PlayerBotGroundNavigation.walk` uses it
for initial and subsequent probes. Existing body checks, active instance door
states, slope/cliff/layer limits, hazards and speed-based movement ticks remain.
No geodata, human movement, location, combat policy or teleport recovery change.

The reproduction changes from 10.58m remaining/stalled to a completed direct
controller-sized walking trace; the greedy planner/tick reproduction finishes
within 0.269m (native movement arrival tolerance 0.3m). The owner-room movement
probe changes from 0m to 4m. This establishes the reported server-mesh failure and
its offline correction; actual client movement remains the user's acceptance.

- Full final core compilation: 2,420 source files; all 13 Steel Rake handlers.
- 57 generic floor/door/route checks, including transparent support, transitions,
 walls, closed doors, separate instances, cliffs, steep slopes and floor layers.
- 17 existing breadcrumb progress checks retained;39 encounter checks retained.
- All 15 native opened/closed door comparisons pass across Steel Rake and Cabin.
- 169 unrelated JAR entries retained byte-identically; freshly installed native
 encounter source/class files carried unchanged in the navigation receipt.

Installed offline in external cumulative receipt `224940-884362`; final two-script
encounter correction `225401-230689` retains the exact same engine JAR. Current
SHA-256 is `8653514fdf8956c3ffcf3e311c84ccd6341a8525e6832cf45e760fde8e4662e0`.
The installer checks actual host shutdown twice and preserves base JAR, launcher,
geodata, bundled UI and runtime settings. All development outputs/recovery
receipts are external. GameServer/client remain off; no live attach or actor
fixture ran. PB-VAL-005 new-room/combat acceptance remains pending. Broader world
travel and other independent Playerbots scope remain unfinished.
