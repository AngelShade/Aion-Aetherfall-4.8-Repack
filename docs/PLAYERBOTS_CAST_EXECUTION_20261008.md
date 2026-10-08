# PB-REPAIR-ENGINE-001 — committed casts and bot controls

Installed offline on 8 October 2026. This repairs the core native execution
boundary. It adds no decision delay, spacing workaround or cancellation immunity.
GameServer/client stayed off; user gameplay acceptance remains PB-VAL-009.

## Root defects and correction

The shared movement scheduler iterates a concurrent player collection. Removing a
bot cannot revoke an already acquired callback. `PlayerBotMoveController.moveStep`
previously checked neither its started/moving flags nor the active native cast.
A stale callback could therefore move or stop a bot after `CastAction` stopped it
to cast. Native movement observers and `PlayerController.onStopMove` cancel skills.
Increasing delays or changing caster spacing does not correct this competing path.

The mover now requires current started/moving state and no active native cast
before accessing movement state or invoking callbacks. It discards stale work
without stop/cancel callbacks. Actual `CastAction.execute` holds the same mover
monitor across legality checks, movement shutdown and native `useSkill`, closing
the stop-to-cast race. Normal movement and native interruption rules remain.

`Session.order` previously cancelled skills even when that order was already
selected. Repeated FOLLOW/STAY/GUARD/PASSIVE with no explicit mission/attack to
clear now preserves the cast, charge release and decision lock. A changed order,
or FOLLOW clearing a mission/attack override, retains intentional stop/cancel/
replan behavior. Closing and invalid-order checks remain authoritative.

`applyPreferences` previously discarded engine continuers whenever a menu value
was reapplied. Identical values now preserve pending skill-chain intention.
Actual changes invalidate future planning without cancelling the active native
cast or its decision lock. Metadata coalescing and owned-alt protections remain.

The generic priority queue and positive multipliers were not the contradictory
path: the native execution boundary and unconditional context resets were.

## Reviewed settings and commands

| Control | Cast/execution behavior |
| --- | --- |
| Repeated current order | No-op when no explicit mission/attack requires clearing; retains current cast/charge and continuer. |
| Changed order / clear explicit mission or attack | Intentional interruption/context reset remains. |
| Role, area skills, supplies, gear, loot, questing | Identical values retain future chain intent; changes replan future actions without cancelling the current spell. Questing off clears its mission/target and stops actual movement. |
| Formation and owner/attack spacing | Update destination policy; ordinary movement remains gated while casting. No spacing tuning was used to conceal the race. |
| Nearby quest combat, party sync, enchant/salvage and gear profile/acquisition/rolls | Native metadata/policy updates; automated execution retains casting/combat/item-use guards. |
| Equipment/appearance actions | Existing legality/casting/trading/combat restrictions remain; no alt builds replaced. |
| Attack target | Updates future target context; current native spell recipient remains independent. |
| Dismissal, death, transfers, hazards | Existing explicit lifecycle/encounter interruption rules retained. |

Normal native damage/control/target/resource failures and deliberate encounter
reactions can still interrupt casts. Actual gameplay must distinguish these
reasons from unwanted command/movement cancellation.

## Exact upstream/native mapping

Pinned `037c01418b5d01506917a3db9b44fd56ac5f965c`:

- `src/Bot/PlayerbotAI.cpp`, current-spell/preparing/channel branch in `UpdateAI`:
  yield/wait for the committed spell before normal selection, with explicit
  interruption/invalid-target handling.
- `src/Bot/Engine/Engine.cpp`, `DoNextAction`/continuations: resolve useful actions
  while retaining successful follow-up intention across normal decisions.

Both cached source hashes match `third-party/playerbots/SHA256SUMS`. Aion maps this
purpose to native `Skill` identity, session decision lock, shared mover monitor,
native callbacks and existing continuer/context arbitration. No WoW skill IDs,
opcodes or artificial timing values were copied. This is not full class parity.

## Verification and preservation

- All 2,420 GameServer sources compile externally.
- Four independent negative regressions fail on the pre-repair installed JAR:
  stale/active-cast movement admission, repeated-order execution, unchanged-value
  continuer loss and unprotected actual `CastAction` admission.
- **72 production entrypoint checks** pass on source and effective installed code:
  scheduler state combinations, a contending thread through actual `CastAction`,
  unchanged controls/continuers and intentional order/mission/attack interruption.
- 99 engine/policy/lease checks, 31 recording-JDBC preference checks, 49 strategy/
  continuation checks, 44 final spell-planning gates and 17 breadcrumb checks pass
  on the effective package. The engine test's obsolete file-preference invocation
  now uses the maintained native metadata fixture.
- No real spell casts, world registration, allocated/released IDs, real DB writes,
  startup/restart/attach occurred. World-free probe actors and recording JDBC are
  distinct from native/client gameplay acceptance.
- Four methods in three existing definitions changed. **168 other JAR entries**,
  unselected methods, bundled UI, native dungeon handlers, launcher, base JAR,
  geodata and runtime settings remain. No owned-alt equipment, inventory, Stigmas,
  skills or builds were edited.

The initial inventory comparison found five dungeon cache classes regenerated
by the normal native script compiler. Every effective method and full declared
schema matches hash-verified reviewed references; handler sources are unchanged.
External read-only recovery receipt `20261008-054444-184495` records current bytes
with zero deployed replacements. The repair preserves them without disabling hash
guards or restoring older script classes.

Core receipt:
`D:/Proiecte/Project Restructure/Aion Development Workspace/archives/server/game-server/backups/playerbots-recruitment-20261008-054856-494763`.
Installed override SHA-256:
`77890851c3e6b15ca73558abb62266ffbf32b88722c5825ae73963ebb13cfa93`.
All 20 postinstall mod checks/31 client hashes pass; 73 client/99 server receipts.
External evidence: `diagnostics/playerbots-cast-execution-20261008` and
`staging/output/playerbots-cast-execution-20261008-v2`.

User acceptance: uninterrupted casts while following; reapply unchanged controls
during a spell; change an order intentionally; verify movement resumes afterward.
Check charged spells and chains too. No runtime test was forced. PB-PORT-005B
Spiritmaster remains the next independent class slice; the full port is unfinished.
