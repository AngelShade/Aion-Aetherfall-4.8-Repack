# Shared quest objective continuation — 4 October 2026

PB-PORT-003 is **installed on disk and offline verified; native/gameplay
acceptance remains pending**. GameServer was kept stopped as requested.
The canonical backlog remains in `PLAYERBOTS_PORT_REVIEW_20261004.md`.
Next offline implementation: PB-PORT-004, strategy composition, expanded
multiplier semantics and continuers. The full Playerbots port is unfinished.

## Original purpose and Aion mapping

Unchanged primary source at `037c01418b5d01506917a3db9b44fd56ac5f965c`:
`third-party/playerbots/upstream/src/Ai/Base/Actions/ChooseTravelTargetAction.cpp`,
especially `SetGroupTarget`, `SetCurrentTarget` and `setNewTarget`; and
`LootAction.cpp::OpenLootAction::DoLoot` for native quest-gated objects.
All 51 reference hashes remain required by the source checker.

Upstream copies an actual active destination and point from another group bot,
excludes copied/inactive destinations, checks the destination is active for the
follower, and revalidates destination/retry state. WoW object loot must be useful
for that bot's native quest. The previous Aion planner was reachable as idle
navigation, but executors independently chose conversations, objects, NPC jobs
and hunts. Its group bonus applied to every destination sharing a quest ID.

`PlayerBotQuestObjectives` now owns the decision before those executors run:
quest ID, full local quest-variable/status stamp, kind, exact native actor ID or
static hint, owner map/instance, progress, pause and failure state. Existing route,
conversation, object, turn-in and new-pull paths consume this decision. Acceptance
remains the native nearby fallback when no approved active objective is available.
Explicit missions retain their existing range/order contract.

Peers copy the actual destination from an active original peer in the same party
and owner session. Each follower independently validates its own native objective.
Different variable words can legitimately need the same NPC; equality with the
peer's word is not required. The follower records **its own** variables, preventing
stale actions after its progress changes. A genuinely different objective cannot
copy a peer solely because the quest ID matches. Copied peers cannot originate
another copy chain. Active peers have a bounded freshness check.

Visible actors require actual owner visibility and map/instance matching. Cached
points follow the same moving actor. Static spawn hints resolve a real visible
actor before interaction; absent actors eventually back off. Arrival differs from
navigation failure, combat/rest/travel pauses do not consume the stuck timer,
and three unsuccessful native interactions exclude that actor/stage for 60 seconds.
Exclusion identity does not change when an actor moves. Native object tasks,
member loot rights and reservations remain authoritative; finishing loot is not
discarded to start a competing task. Required quest-drop mobs are included in
hunt candidates. Hunt routes now use the same 25m owner radius as initiation.
Tank-led pulling, safe-pack checks, the quest-combat toggle and existing party
assist remain in force. Planning does not grant credit, items or rewards.

Unknown branching dialogue stays unsupported without a verified owner witness.
This is local arbitration, not autonomous world/service/new-quest graph planning
(PB-PORT-011) or completed tank/pull readiness coordination (PB-PORT-007).
There is no automatic alteration of owned-alt equipment, class, builds or Stigmas.

## Installation and evidence

Initial arbitration receipt:
`backups/playerbots-recruitment-20261004-203357-938918`.
Final cumulative receipt:
`backups/playerbots-recruitment-20261004-203902-131350`.
These build on `...193448-980138`. The initial package changes 13 existing
methods, adds three helper classes and preserves 110 other entries byte-for-byte.
The follow-up changes only `PlayerBotQuestObjectives.peers`; 118 other entries
are byte-identical. Both receipt transactions retain rollback and hash guards.

The bounded existing methods are Session.tick; QuestRoutes.choose/trigger/close/
ids/leash; Quests.choose and its lambda plus the five-argument interact;
QuestConversations.tick; QuestObjects.tick and its selection lambda; and
PartyBehavior's hunt lambda. Original schemas, obsolete synthetic members and
enum-switch mappings survive. Future existing-helper changes require explicit
runtime SCOPES as well as any disk HELPERS entry. No VM was attached.

- `target/playerbots-objectives-full-source-validation.txt`: full server/commons/
  command compilation and existing companion/HTTP suite passed. Final small
  changes were recompiled and exercised against the cumulative package.
- `target/playerbots-objectives-effective-validation-v4.txt`: 35 production
  planner/executor-gate checks, 18 actual native intermediate-handler checks,
  17 route-policy checks and 52 party/Decorative Weapons native checks passed
  with `-Xverify:all`. The fixture uses world-free actors, real native quest
  metadata and ReportToMany progression. Actual Quests.choose consumes the
  committed reward NPC. No actor constructors, world registration, DB, item
  allocation/release, human writes or native casts occur.
- `target/playerbots-objectives-installed-integration.txt`: the same 35 arbitration
  checks passed against the installed override.
- `target/playerbots-objectives-postinstall.json`: three receipts/backups verified;
  all 31 client hashes, 35 settings/preset/archive files, base JAR, launcher,
  commands/media and deployed configuration retained. All 15 installed-mod checks
  pass; inventory now records 70 client and 72 server receipts.
- `target/playerbots-objectives-postinstall-audit-v4/`: pinned-source/effective
  method comparison against the compiled snapshot: 51 reference hashes and 96
  effective methods/lambdas match; eight old synthetic members are retained and
  the legacy rune gate remains inactive. Final report is authoritative.

The existing-class schema guard caught an extra captured mission argument during
staging. The code was changed to preserve the original lambda descriptor; the
guard was retained. The final peer correction follows upstream native eligibility
instead of requiring equal variable words. Earlier failed packages are not installs.

## Remaining acceptance — PB-VAL-002

After an authorized startup, exercise the actual complete Session.tick flow:
different-stage party members, moving/hidden NPCs, native intermediate pages,
reward choice/payment/turn-in, Decorative Weapons object task and member loot,
kill and drop objectives, no-pack/tank-led initiation, owner travel/combat pauses,
failure exclusions, unsupported branches and instance transitions. Verify native
geodata, announcement timing and formation regrouping in the real client.
The offline gates and handler fixture do not establish those behaviors in-game.
Do not close PB-PORT-003 or describe the full port as complete on these results.
