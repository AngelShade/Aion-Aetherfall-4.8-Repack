# Bot tick root integration repair - 8 October 2026

PB-REPAIR-PACKAGING-001 is **installed offline** in
`D:/Proiecte/Project Restructure/Aion Development Workspace/archives/server/game-server/backups/playerbots-recruitment-20261008-191905-805583`.

The cumulative override SHA-256 is
`6f72603b8a385f5f9f39c3bdd9b9a8783c06ab17b549e648195e915b9503c5c4`.
Actual client following is **PB-VAL-005 pending user retest**. Both GameServer and
Aion remain off; no startup, restart, attach or native gameplay fixture occurred.

## Evidence and cause

The user reported that bots stopped following after the latest follow update,
then supplied repeated `NoClassDefFoundError: PlayerBotAppearance` from
`PlayerBotSession.tick:227`, caused by `ClassNotFoundException`. The executable
installed tick calls that helper immediately after Temporary.tick, before loot,
party synchronization, combat planning and follow action selection.

No runtime library contains PlayerBotAppearance. It belongs to the unfinished
PB-CUSTOM-APPEARANCE-001 source/staged feature. The backup in initial follow receipt
`061841-232356` has no appearance call; the installed initial update, preserved in
the later `062930-344543` backup, does. The follow stager transplanted the whole
selected tick from full source, which also contained this uninstalled feature.
Method-difference approval and movement-helper tests did not check that all
references in the transplanted method were deployed.

The error escapes the service's Exception handler because it is a LinkageError.
That aborts the tick before follow can execute. The repair does not catch or hide
that error, disable appearance validation, add a teleport fallback, or alter
movement to compensate. It removes the premature source/deployment integration
and fixes the package validation boundary.

## Source and deployment correction

- PlayerBotSession source no longer invokes the unfinished appearance tick hook.
  The appearance helper and other source-only feature work remain intact. Restore
  tick integration only with the complete reviewed appearance installation.
- The shared companion stager now checks the final cumulative executable class
  references, member descriptors, inheritance, static/instance kinds, catch types
  and lambda/bootstrap references against runtime libraries. It examines code
  instructions rather than unused constant-pool entries left by transplants.
- The follow stager checks again after its config merge; the existing follow
  installer and new root repair installer check immediately before installation.
  Missing references fail closed. No compiled full-source classes are on the
  verifier classpath, and no game class is initialized or executed by the audit.
- Delivery changes only `PlayerBotSession.tick`. Normalized bytecode verification
  proves the change is exactly deletion of the receiver load and appearance call,
  accounting for instruction-offset shifts. All other Session methods and all
  176 other cumulative JAR entries remain unchanged.

This is an Aion source/packaging repair, not a new upstream behavior. The existing
follow contract remains mapped from pinned `FollowActions.cpp` / `MovementActions.cpp`
to native PlayerBotNavigation, PlayerBotFormation and PlayerBotMoveController;
the repaired tick can reach those actions again. The next independent class
implementation remains PB-PORT-005B Spiritmaster.

## Preserved current baseline

Before staging, inventory correctly refused eight stale receipt hashes after the
user's server run. Read-only checkpoint `191458-608072` reconciles seven Steel Rake
cache classes whose complete executable methods and member schemas match the
receipt, plus the observed `instance_follow = false` to `true` setting change.
Every other file matches its previous receipt. Current cache files and settings
were copied to recovery storage and preserved, not replaced by older artifacts.

The core source/base JAR, first-position override launcher, client files, geodata,
Steel Rake source/scripts, native cast guards, follow intent/geometry, summon
controls, configuration, bundled UI and owned-alt builds remain intact. Final
inventory passes 20 checks and 31 client hashes, recording 73 client and 104
server historical receipts. Receipt counts are inventory evidence, not gameplay.

## Validation

The old installed package fails the new production audit with exactly the reported
missing appearance dependency. The corrected staged and installed packages pass:
174 effective classes and 24,383 executable member references. Five isolated
guard regressions cover missing classes, lambda dependencies, missing methods,
static/instance mismatches and absence of initializer execution.

All 2,423 server source files and 52 standalone Playerbots check sources compile
externally. Effective-package regressions pass: follow/summon 679, cast execution
72, engine 99, composition 49, offense 44, native speed packets 6, trail 17,
ground navigation 57 and formation 314. These do not execute a full native bot
tick, real casts, DB operations, world actors or item IDs.

The installer checks actual process state twice, current original hashes, client,
base JAR, settings and geodata, writes an external recovery receipt and repeats
the linkage audit against the installed JAR. One attempt safely refused while
another GameServer PID existed; the user identified it as another worktree and
then closed it. Installation succeeded after refreshed process inspection.

Maintained tools: `stage_linkage_repair.py`, `verify_linkage_repair.py`,
`verify_runtime_linkage.py`, `java/PlayerBotLinkageCheck.java`,
`test_runtime_linkage.py`, `reconcile_linkage_baseline.py` and
`install_linkage_repair_offline.py`, under `client-mods/playerbots`.
Compile outputs, packages and diagnostics are under the external development root;
the deferred migration and historical recovery paths were not moved.

Remaining acceptance: the user checks all recruited bots resume following and
normal combat, without the appearance linkage errors. Rendered formation,
ground/flight/door movement and summon behavior remain actual-game tests; this
repair does not establish full Playerbots parity or resolve other open investigations.
