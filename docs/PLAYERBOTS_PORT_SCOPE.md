# Full Playerbots port scope and iteration focus

Recorded 4 October 2026 at the user's explicit request to retain focus across
future chats and iterations. This is planning/continuity guidance, not a new
implementation or permission to start GameServer.

The destination is the original WoW Playerbots behavior adapted to native Aion
mechanics, alongside the user's established companion policies. The current
owner-bound companion system is a subset of that destination. The focused
`PLAYERBOTS_PORT_REVIEW_20261004.md` tracker reviews already attempted ports;
its twelve implementation entries are not a full upstream feature inventory.

## Scope gaps that must stay visible

| Scope ID | Original subsystem / intended Aion behavior | Current position | Dependency distinction |
| --- | --- | --- | --- |
| PB-SCOPE-001 | Independent world bots that remain active without a recruiting owner and choose roaming/grinding/quest/service activities | Not implemented. Current sessions require a connected owner and their party. | Needs independent lifecycle/population management and autonomous activity planning. Reuse existing combat/quest adapters; world travel is related to PB-PORT-011. Local quest arbitration alone does not supply this subsystem. |
| PB-SCOPE-002 | Players invite available world bots into parties; bots assign player control and transition back to independent behavior afterward | Not implemented. Owned-companion recruitment/automatic party placement is a different capability. | Needs native invitation decisions, availability and control/lifecycle transitions. Does not require every class rotation to be finished first. World-bot use depends on PB-SCOPE-001. |
| PB-SCOPE-003 | Native player/bot item and Kinah exchange with bot decisions and normal transaction/persistence semantics | Proper bot trade interaction not implemented. Inventory inspection, equipment controls and NPC purchases are existing separate capabilities. | Needs native exchange handling, useful-item decisions and custody/persistence checks. Related to PB-PORT-009; world navigation is not a prerequisite for trading with an existing companion. |

The **bounded full subsystem inventory is complete** in
[PLAYERBOTS_SUBSYSTEM_INVENTORY.md](PLAYERBOTS_SUBSYSTEM_INVENTORY.md), including
the untruncated pinned tree, further scope IDs and independent dependency tracks.
Update affected entries rather than repeating the inventory. Preserve existing
PB-PORT/PB-VAL IDs and separate unsupported native equivalents and local policy.
An installed repair must not stand in for a new subsystem.

The original feature distinctions, group invitation options and trading options
are confirmed by the
[configuration at the project's pinned revision](https://github.com/mod-playerbots/mod-playerbots/blob/037c01418b5d01506917a3db9b44fd56ac5f965c/conf/playerbots.conf.dist).
That confirms subsystem existence; their action/manager algorithms still need
source review before claiming a port. Do not attribute Aion behavior to an unread
upstream action or copy WoW spell IDs, talent trees, coordinates or opcodes.

## Iteration contract

1. Read this scope register, the focused tracker, current validation and installed
   mod records. Refresh actual receipts before any install; preserve all mods and
   owned-alt builds. Refresh actual process state and honor current lifecycle
   authorization; do not start or shut down GameServer automatically.
2. Select one concrete missing behavior from the dependency plan. State its ID,
   original source, native mapping, what changes and what later work it enables.
3. Classify work as **new port**, **repair**, **prerequisite** or **validation**.
   Explain prerequisites with actual dependency evidence. Avoid making unrelated
   feature tracks wait behind the entire companion backlog.
4. Bound repairs to defects demonstrated in the selected slice or its necessary
   dependencies. Log unrelated findings separately. Do not reopen installed work
   simply because native/client validation remains pending.
5. Implement and test the actual behavior at the strongest authorized level.
   Keep source/offline/installed/native/client stages distinct. Update the current
   table and next-work pointer together, so historical notes cannot restart old
   work. Report user-visible progress and explicit remaining dependencies.

PB-PORT-003 is installed/offline verified with native/client validation pending.
PB-PORT-004 is installed live/on disk in receipt `213711-193912`, with 49 tests
passing against loaded helpers and all five real session contexts observed.
Actual client combat acceptance is pending. Next companion implementation is PB-PORT-005, the first
complete native class strategy slice. Population, invitation and trade retain
independent tracks in the inventory; they do not wait behind the entire class
backlog. GameServer is now running following a separately approved position
update; the user then requested finishing this reviewed strategy update, which
was applied without changing the server lifecycle. The full port
remains unfinished.

Current continuation supersedes the preceding next-work pointer: **005A Sorcerer
single-target/MP/boost strategy is installed** in `234841-695964`, with offline and
loaded-method verification; user gameplay testing is pending. **Next 005B** is
Spiritmaster learned single-target/pet strategy, using GenericWarlock/Affliction
sources and native pet gates, with necessary coordination on PB-PORT-008. Full
class coverage and independent world/invitation/trade tracks remain incomplete.
See `PLAYERBOTS_SORCERER_STRATEGY_20261004.md`; do not repeat the full inventory.
