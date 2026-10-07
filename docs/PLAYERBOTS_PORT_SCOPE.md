# Full Playerbots port scope and iteration focus

Current user policy (7 October 2026): GameServer and the game are off. Keep both
off and perform source compilation and isolated offline review/checks only.
The user tests actual gameplay after each WoW Playerbots feature or feature group
is ported. Pending user acceptance must not cause installed features to be re-ported.
No live attach or server/client lifecycle action is authorized by this policy.

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
| PB-SCOPE-003 | Native player/bot item and Kinah exchange with bot decisions and normal transaction/persistence semantics | PB-SCOPE-003A native owner gifts of items/Kinah to active alts and Temporary Bots are installed; donated legal upgrades use class/role scoring. Outgoing bot offers, group-member/world-bot bargaining and crafting trade remain unimplemented. | Needs native exchange handling, useful-item decisions and custody/persistence checks. Related to PB-PORT-009; world navigation is not a prerequisite for trading with an existing companion. |

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

## PB-SCOPE-003A native owner gifts — installed 5 October 2026

This independent trade slice was selected at the user's request, without reopening
installed class strategies. Pinned TradeStatusAction/TradeAction maps to native
ExchangeService begin, lock, confirm and cancel; ItemUsageValue's equip purpose
uses existing Aion class/role/mastery scoring. Owners can give items/Kinah to
active owned alts or Temporary Bots through the normal trade window. Only donated
legal upgrades are considered for alts; other equipment/build/Stigmas remain owned
by the player. Old equipped items return to the cube through native equipment rules.

Item custody and wallets commit before native success; full item IDs/attributes
are retained, splits use fresh IDs and cancellation releases only uncommitted
splits. Normal trade rights, range, faction, combat and cube limits remain. Native
five-second binding/identification is serialized and pauses following until done.

See [trading installation/validation](PLAYERBOTS_TRADING_20261005.md). Full source
and 56 final production checks pass; actual user trade, equip/bind, periodic save,
dismiss/resummon and game UI acceptance remain **PB-VAL-010**. Broader trade is
partial: outgoing bot offers, group-member/world-bot security, bargaining,
discounts and crafting trade are not claimed. The next companion class slice
remains PB-PORT-005B; broader economy/trade can continue independently.

## PB-REPAIR-SETTINGS-002 — installed offline 7 October 2026

The current care retry is confirmed retained; the pasted Tancul traceback matches
the older 04:15:05 incident. Current logs show the same Windows sharing denial
from gear provenance saves for MagicDps/LeMuse. GearPolicy.State.save now uses
the existing bounded atomic-replacement retry. One method changed; 146 other
cumulative entries remain byte-identical. No settings/build/item/quest policy
changes or error suppression. Persistent locks still report failure; the locking
process is not identified.

Current receipt `backups/playerbots-recruitment-20261007-081522-429924` retains
`042546-945113`, gifts, Sorcerer, tank, custody, native shield and every earlier
mod. Full offline source build and 36 focused source/effective/Windows private-file
checks pass. Server/client remained off; no startup/restart/attach or forced native
gameplay/DB/ID tests. User actual care/gear/supplies acceptance is pending.
See [settings diagnosis and installation](PLAYERBOTS_SETTINGS_FILES_20261007.md).

Next broader slice stays PB-PORT-005B Spiritmaster; independent scope tracks remain
open. Appearance and follow recovery remain source/staged only; earlier packages
must be restaged against the new cumulative receipt before install. Do not copy an
older staging JAR over this repair. All earlier unfinished port/repair work survives.

## PB-SCOPE-012A native metadata — installed offline 7 October 2026

Care/gear state now uses native MetadataDAO/cache/checkpoints, mapped from pinned
PlayerbotRepository.cpp and PlayerbotsDatabase.cpp. AI/settings saves queue values
in memory; dirty metadata commits with native inventory/progress, including the
pre-trade checkpoint. Failure retains dirty state for periodic checkpoint retry.
Supply provenance marks the native cube dirty. Owned-alt builds and all existing
preferences, consent, quest witnesses and protected-item values are preserved.

After the user opened the database, the guarded installer verified native ownership,
created the metadata table and committed **32 imported care/gear rows**. A separate
read-only connection verified all 32 committed values exactly match retained legacy
files. Eight existing methods/six definitions changed, six new classes were added,
and 141 earlier JAR entries remain byte-identical. Full offline compile and 35
production checks pass against the installed package; 16 mod checks/31 client hashes
pass, with all 93 settings/media files unchanged. Inventory records 70 client/86
server historical receipts. No GameServer/client startup, restart or attach occurred.

Current recovery receipt is external:
`D:/Proiecte/Project Restructure/Aion Development Workspace/archives/server/game-server/backups/playerbots-recruitment-20261007-124827-848789`.
It retains `081522-429924` and every earlier installed mod. Override SHA-256:
`f49bd48bcfd6a0a7eabae4a8fe2213791dba7dcdab1cb1945d3a900451cd1a3e`.

See [native metadata port](PLAYERBOTS_METADATA_20261007.md). PB-VAL-011 migration and
disk installation are complete; actual care/gear/supplies/trade checkpoints,
failure/retry and dismissal/resummon/restart gameplay acceptance remain the user's
tests. PB-PORT-005B Spiritmaster is the next separate class implementation. Other
repository namespaces and the full port remain partial. Appearance/follow packages
remain source/staged only and must be restaged against this receipt before install.
