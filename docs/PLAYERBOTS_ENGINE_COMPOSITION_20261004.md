# State strategies and action continuers — PB-PORT-004

Implemented and installed live/on disk on 4 October 2026 after the user's
instruction to finish the reviewed update. Current cumulative receipt:
`backups/playerbots-recruitment-20261004-213711-193912`.
It retains the separately installed position repair `211935-035574` and every
earlier mod. No server startup, shutdown, restart or client replacement occurred.

## Original contract and native adaptation

Pinned source: `037c01418b5d01506917a3db9b44fd56ac5f965c`.
`Engine.cpp::Init/DoNextAction/MultiplyAndPush/PushAgain` composes strategy
triggers/defaults/multipliers, evaluates multipliers when popping each action,
expands prerequisites/alternatives with evaluated parent relevance, and queues
continuers only after successful execution. `Action.h` and `Strategy.h` define
those contracts; `AiFactory.cpp` composes strategies by state/class/role.
`ThreatStrategy.cpp` supplies threat suppression as a multiplier.

The Aion session now composes named encounter, quest interaction, loot, rest,
equipment, care/services, supplies, class skills, combat positioning/attack,
quest travel, follow and guard strategies. State filtering, enable/disable,
default actions and combined multipliers have an explicit Plan contract.
Existing settings and explicit orders control the existing behavior; no new
class rotation or custom command vocabulary is claimed by splitting this wiring.
The current native threat hold is also connected through a class-damage multiplier,
retaining the existing emergency-interrupt exception and native hate policy.

`PlayerBotEngine.tick` delegates to `PlayerBotArbitration`; existing engine,
Action/Strategy/Trigger/Basket schemas and old synthetic members are preserved.
Weights apply once at evaluation, including expanded actions. Upstream raw queue
ordering is retained: this is **not** a global re-sort of weighted seeds.
Expanded actions inherit evaluated parent relevance and then apply their own
weights, as upstream does. Stable equal-priority ordering and exact budgets remain.

Only logical action keys, relevance and expiry persist between snapshots, bounded
to 16 pending keys and 10 seconds. The next decision resolves those keys through
fresh active/admitted strategy actions. Current usefulness/possibility, native
target identity, resources, cooldown, role, threat, recipient/range/LOS and area
legality still run. Missing/inactive/invalid/suppressed successors are dropped.
Emergency actions can preempt a continuer; successful prerequisite movement
preserves the parent's intention without permitting an uncompleted prerequisite.
This intentionally preserves the Aion safety rule that failed native movement
cannot authorize a cast, rather than copying WoW's skipPrerequisites flag.

Learned native skill chains connect the continuer path in production. Successor
keys come from the exact native predecessor category, same action kind and
recipient object/world/instance identity. Aion's ChainCondition has no public
preCategory getter, so this new helper reads that verified metadata field without
changing native class schema or chain state. Some native casts activate the chain
asynchronously: keys are scheduled after the cast is accepted, while the next
snapshot still requires the actual proc, count and unexpired chain. Nothing
forces a proc or pays MP/advances a chain during planning. Cross-kind optimized
class sequences remain PB-PORT-005, not a fabricated generic rotation.

State/map/instance/spawn/death/flight/group/role/order/explicit-target/mission
context changes clear pending work. Preferences, direct orders/target commands,
dismissal and transfer/catch-up clear it explicitly. An action/actor reference
from a previous decision is never retained. Owned-alt skills/builds/equipment/
Stigmas remain untouched by this engine update.

## Verification and installation

- The full source/commons/command/companion/HTTP suite passed in
  `target/playerbots-composition-source-checks-final.log`. Subsequent bounded
  chain-adapter/fixture refinements were recompiled and checked separately.
- Final effective package checks:
  `target/playerbots-composition-effective-checks-final.log`: **49 engine/
  composition/weighted-expansion/continuation/native-chain tests**, plus 99 engine
  regressions, 44 final offense/native-planning, 18 intermediate conversation,
  35 shared quest arbitration and 38 formation/range/cast-prerequisite cases.
  Fixtures perform no native casts, world registration, DB writes or ID operations.
- `target/playerbots-strategy-composition-package-final/verification.json`:
  **six existing methods in two classes**, nine new helper classes, **120 earlier
  cumulative entries byte-identical**, all unselected methods/schema/enum switches
  retained. Command, menu media, launcher, base JAR and client are unchanged.
- Fresh update agent **40** preloaded every original override class before JAR
  replacement, checked payload hashes, appended/resolved helpers and atomically
  redefined the two existing classes with explicit effective rollback definitions.
  Receipt preflight/runtime files verify five spawned companions, seven roster
  rows, unchanged session identity/roles/orders/preferences and one human connection.
- `target/playerbots-composition-runtime/live-v1/runtime-check.txt`: the same
  **49 behavior tests pass against the actual loaded engine/helpers**; all five
  naturally scheduled sessions have the new context. No session tick, movement
  or skill cast was forced on real characters. Eleven loaded definitions were
  captured observationally for comparison.
- `target/playerbots-composition-runtime/postinstall.json`: **81 effective loaded
  methods match the reviewed package**, 31 client hashes and 15 mod checks pass,
  base JAR/launcher/config preserved, 43 settings/preset/archive files reviewed.
  Two native generated-item provenance lists grew during ordinary gameplay;
  all their preference values stayed equal and no provenance was removed.
- `target/playerbots-composition-postinstall-audit/report.json`: 51 original
  reference hashes and 115 existing effective port methods match the compiled
  snapshot. The 17 extra scope sources and complete tree have a separate hashed
  manifest; original reference hashes/attribution were not overwritten.

Installed override SHA-256:
`5f9cc6bff2bdb9802dc43dd1dc0de17264c5784cb294341dd0cf80345fc153c4`.
Base JAR:
`31948ed051a7d896ee377f5eaf4dbc7d9f9ba5416067139c8fe4f975c6c2d5b2`.

Actual client combat/animation/native chain casts and lifecycle transitions remain
acceptance work (PB-VAL-009). The isolated loaded-engine tests are not a real-party
combat test. Next implementation is **PB-PORT-005**, the first source-reviewed
complete native class strategy slice. Independent world bots, invitations, trade,
global travel, broader encounters and item-ID release investigation remain open
in the full inventory/tracker. Do not restart the completed inventory pass.

Use `stage_strategy_composition_update.py` and the stopped-only
`install_strategy_composition_offline.py`, or an explicitly authorized guarded
live install with original preloading, hashes and rollback. Future changes to
already-loaded Arbitration/StrategyComposition helpers require explicit runtime
SCOPES, not HELPERS alone. Next live update revision must exceed 40.
