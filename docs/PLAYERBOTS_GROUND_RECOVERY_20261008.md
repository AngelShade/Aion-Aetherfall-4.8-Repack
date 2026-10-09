# Shared bot ground recovery — 8 October 2026

## Scope and cause

**PB-REPAIR-NAV-004** repairs the shared bot movement pipeline. This is an
evidenced continuation of the earlier floor/door repairs, not another location
exception or a new class-strategy port. Independent next class review remains
PB-PORT-005C Cleric; other port and acceptance work remains open.

The reported Azoturan positions were reproduced against the installed native
`310100000.geo` and `models.mesh`, without starting GameServer, creating actors,
accessing the database or attaching an agent. At `(472.80038, 430.7651,
1067.8826)`, the nearest physical floor is `1062.0067`, a 5.8759m difference.
Every old movement direction returned the original position. The existing
starting-floor query reached only 1.25m below the actor, and the ground API
discarded the intended destination Z. Headless actors do not receive a human
client's gravity updates to correct an inherited elevated position.

The second reported position `(469.4193, 429.4851, 1062.0067)` already has valid
support. Its normal movement remains intact. These coordinates exist only in
this evidence record and the external diagnostic, never in production routing.

## Maintained source repair

- Ground probes now retain destination altitude through local planning,
  follow-intent refresh and each movement-controller tick.
- When the starting position has no support in the existing narrow window and
  the intended destination is lower, the shared probe searches for the nearest
  walkable physical floor beneath that starting XY, down to the intended level.
  Foot and body sweeps must prove the vertical correction unobstructed.
- An intervening physical surface, steep surface, ceiling, wall or closed door
  prevents recovery. Physical see-through floors retain their existing support
  semantics. WALK-only meshes do not become floors.
- Ground recovery applies at the origin. Ordinary walking edges retain their
  existing floor, slope, step/drop and dynamic-door checks; a lower destination
  does not permit walking across a cliff or unsupported gap.
- Local path search starts from the verified ground origin. Horizontal planning
  uses XY distance, so the inherited height cannot cause waypoint overshoot.
  Verified vertical-only recovery can also finish at the same XY waypoint.
- Existing native casting, movement permission, speed, lifecycle, hazard,
  ownership, recall and alt-build rules remain intact.

There are no world IDs, room names, coordinate lists or map-specific bypasses in
the source implementation. Native human movement is unchanged.

## Offline evidence

- Normal Maven reactor build via `tools/build-components.ps1` succeeds. Complete
  GameServer output is used unchanged; no compiled classes are rewritten.
- Existing 57 generic door/floor/route checks pass against that builder output.
- At the first reported position, all 24 directional probes now make progress
  on the physical floor. Four repeated movement traces (0.07/0.15/0.3/0.8m per
  step) reach the second position with zero remaining XY distance.
- Complete package linkage: 3,281 classes and 171,808 executable member references
  pass without game initialization.
- Against the current complete source-built Spiritmaster runtime, 3,542 JAR
  entries remain byte-identical. Only five source classes and GeoService's two
  compiler-generated nested definitions differ, plus the builder manifest.
  All unrelated gameplay classes and bundled UI entries remain byte-identical.

External build: `staging/target/playerbots-ground-recovery-final-20261008`.
External review: `staging/output/playerbots-ground-recovery-20261008`.
External mesh diagnostics: `tooling/java/ground-recovery-20261008`.

## Delivery and acceptance

The complete unchanged Maven GameServer JAR is installed offline in receipt
`playerbots-source-build-20261008-214740-770720`, after successful fresh shutdown
inspection and backup/hash guards. Installed JAR SHA-256:
`d9e0a58a7316e1e33ff4655082caf518e13e9746766ba63820dbe0a1f914bc3f`.
Only the GameServer component output was copied. Commons, launcher, runtime
settings, geodata, database and client resources were preserved. Refreshed
installed inventory passes all 20 checks and preserves 31 client file hashes.
No startup, restart, live replacement or attach was performed by this repair.

`tools/deliver-source-component.py` verifies normal builder output against the
latest source-built receipt, permits only explicitly reviewed class changes,
checks full runtime linkage without initialization, backs up the complete old
JAR and copies the complete new JAR unchanged. Failure restores the prior JAR.

**PB-VAL-005 actual in-game follow, spacing and movement acceptance remains
pending user testing.** The diagnosis proves and repairs the reproduced
unsupported-height failure; it does not establish that every unrelated geometry
or encounter defect is resolved.
