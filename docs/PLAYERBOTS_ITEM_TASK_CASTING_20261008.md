# PB-REPAIR-ENGINE-002 — stationary spells and completed item tasks

Installed offline on 8 October 2026 in receipt
`D:/Proiecte/Project Restructure/Aion Development Workspace/archives/server/game-server/backups/playerbots-recruitment-20261008-201132-984909`.
Override SHA-256: `d03fd3180a7b9615cbf06e66c4266972a3c9519973d1590419067c450798204c`.
GameServer/client remain off. Actual Songweaver/Sorcerer/Cleric combat acceptance
remains pending user testing (PB-VAL-009).

## Evidenced root defect

The user reports stationary casters start a spell, then stop after a few seconds.
Native `CreatureController.hasTask` checks the registered task map, including
completed, failed and cancelled futures. Native identification/socket operations
can leave finished `ITEM_USE` futures registered. `hasScheduledTask` instead tests
the current future's unfinished state.

`PlayerBotSession.tick` previously treated a registered finished item task as an
active item operation. During combat it called `notifyMoveObservers` on every tick.
That reaches the native spell's `StartMovingListener` without actual movement.
At cast completion, native `PlayerMovedCondition` rejects a spell with
`move_casting allow="false"`. This concrete path reproduces the reported timing
and stationary behavior; gameplay must still establish the outcome for the
reported characters and any other legitimate interruptions.

The real Java source now delegates this boundary to `PlayerBotItemUse.pause`:
only unfinished item tasks pause planning, and interrupting an active item uses
native `PlayerController.cancelUseItem`/`ItemUseObserver` cancellation. It never
fabricates a movement notification or grants spell cancellation immunity.
Care completion/eligibility, equipment acquisition, donated equipment, supplies
and the shared busy predicate use the same native unfinished-task distinction.
The source-only appearance eligibility uses it too; that feature is not installed.
Native movement, damage, target/range/MP checks, hazards, explicit orders and
distance/manual recall interruptions remain authoritative.

This is a repair of the Aion native adapter, not a new upstream class strategy.
Pinned upstream `037c01418b5d01506917a3db9b44fd56ac5f965c`,
`src/Bot/PlayerbotAI.cpp` committed current-spell handling maps to retaining the
native Aion cast while ordinary planning waits. The previous shared mover repair
PB-REPAIR-ENGINE-001 remains installed. PB-PORT-005B Spiritmaster remains the next
independent class implementation slice.

## Source/build/deployment record

Maintained Java, tests and scripts remain in `C:/Git/aion-server`. Full source is
compiled first. The reviewed eight methods in six existing definitions plus the
new helper are then merged into the latest effective cumulative JAR, retaining
172 other entries byte-identically and every unselected method. This avoids
installing unfinished source hooks or reverting cumulative runtime changes.
The runtime remains `C:/Git/aion-server/target-deploy/game-server`; the external
development workspace contains generated artifacts and recovery receipts only.

The maintained `audit_source_parity.py` compares every deployed override class
with freshly compiled source methods, without initializing server classes.
The pre-repair audit compared 175 classes: 164 matched completely. Four changed
methods across three classes are unfinished appearance integration in HTTP action,
gear eligibility and Session snapshot/closing. Other differences are retained old
synthetics/obsolete nested definitions from cumulative updates. This does not
establish that installed features are absent from source. It also does not make
an unrestricted full-JAR replacement safe: unfinished feature hooks, class schema,
base-JAR methods, scripts and static/client data require an explicit release audit.
Current reports are external `diagnostics/playerbots-caster-20261008`.

Scripts: `stage_item_use_update.py`, `verify_item_use_package.py`,
`install_item_use_offline.py`. Staging/compilation is external under
`staging/output/playerbots-caster-20261008` and
`staging/target/playerbots-caster-20261008`.

## Verification and remaining acceptance

- Full 2,425 GameServer Java sources and 54 companion check sources compile.
- 147 world-free checks call actual native task, observer and standing-still
  condition entrypoints: absent/unfinished/completed/cancelled/failed item tasks,
  combat/noncombat pause, selective item cancellation and the old false-movement
  failure. No native spell execution, world/DB/ID operation or scheduler startup.
- Effective package checks pass: recall 165, travel/config/formation 679,
  core casting 72, native trade/custody 56, engine 99, composition 49,
  offense 44, follow speed 6, breadcrumbs 17, navigation 57, formation 314.
- Source-selected methods match effective compiled instructions. Runtime linkage
  passes for 176 effective classes / 24,477 executable member references.
- Offline installer refreshed host CIM state, guarded current hashes and rollback,
  and verified the installed effective JAR. Base JAR, launcher/classpath, client,
  geodata, settings, presets, archive markers and owned-alt builds survive.
- All 20 installed-mod checks and 31 client hashes pass; inventory records
  73 client / 106 server historical receipts. No lifecycle action was performed.

User acceptance: stationary Songweaver, Sorcerer and Cleric spells should complete
after identification/soulbinding/item care; item operations should still cancel
normally when combat starts. Ordinary movement and genuine native interruption
remain possible. Full Playerbots parity, other validation rows and the item-ID
release investigation remain unfinished.
