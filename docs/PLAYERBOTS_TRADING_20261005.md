
## Player behavior

Use the normal player trade action on your own active companion within 5 metres,
outside combat. Add tradeable items and/or Kinah, lock, then confirm. The bot
opens its native side and locks automatically; it does not need a client session.
Normal faction, concealment, death, item trade rights, packaging and inventory
limits apply. Leaving range/changing map, combat, dismissal/logout, cancellation
or a two-minute timeout cancels an unfinished trade. Following/combat/quest AI
pauses while the window is active and resumes afterward.

The bot evaluates only received gear with the existing native class/armor/
weapon/mastery/role/stat/upgrade policy. Usable upgrades are equipped, with
native soul-binding/identification where required. These five-second ITEM_USE
tasks run one at a time and pause following so movement or another gift cannot
cancel them. Unusable items and non-upgrades remain in the cube. Native equipment
failure is reported to the owner. Consumables and Kinah are available to existing
bot policies; this change does not enable spending or extraction preferences.

**Owned alts:** explicitly donated upgrades may replace equipped items, which
return to the cube through native equipment rules. Automatic gear generation,
level/class changes, skills, builds, Stigmas and general inventory re-gearing
remain disabled for alts. Temporary Bots retain their existing maintenance policy.
Delayed gift decisions are held for the active session; the real donated inventory
persists normally even if that session is dismissed before a decision finishes.

## Original behavior and Aion mapping

Pinned upstream: `037c01418b5d01506917a3db9b44fd56ac5f965c`.

| Exact source | Original purpose | Native adaptation |
| --- | --- | --- |
| `scope-references/src/Ai/Base/Actions/TradeStatusAction.cpp` | Verify master/group/security; begin native trade; check offer and accept through native session handler; cancel unauthorized trades | Current owner-bound companions authorize their actual owner/session. Native CM_EXCHANGE_REQUEST/ExchangeService packets open and lock the window; confirmation uses a guarded item/Kinah transaction because a headless bot has no connection. Group/world-bot authority remains a later slice. |
| `scope-references/src/Ai/Base/Actions/TradeAction.cpp` | Initiate trade and offer selected items or money through native handlers | Owner initiation and donation use native Aion item/Kinah offer packets. Bot outgoing-item commands and money offers remain unimplemented. |
| `upstream/src/Ai/Base/Value/ItemUsageValue.cpp` | Distinguish useful equipment/skill/quest/service inventory | Existing Aion GearPolicy/Equipment legal-upgrade scoring selects only explicitly donated equipment. WoW item/talent/currency IDs are not copied. Broader useful-item economy remains PB-PORT-009. |

Native integration: ExchangeService's six participant/add/lock/confirm/cancel
methods; CM_EXCHANGE_REQUEST.runImpl; Session.tick/markClosing/equip; new
PlayerBotTrade and PlayerBotTradeStore. Human/human trades follow their original
service path. Client archives, Lua, menus and native DLLs are unchanged.

## Custody and concurrency

Native client packets already serialize on the donor connection. Companion
operations then use the service/session monitors shared by AI/save/dismiss.
Callbacks never acquire a client monitor under those monitors. Native item-use
tasks remain authoritative.

Pre-existing owner/bot inventory changes are checkpointed before ownership moves.
The actual gift transaction locks and verifies exact source owner, template,
private cube, count and unequipped state. Every offered source is validated before
offered-item writes. Full items keep their IDs and all attributes/stones; only
owner/cube slot/pack state change. Partial stacks debit the original and insert
the native fresh split item. Wallet debits/credits use exact private custody and
signed overflow guards in the same transaction. Transfer failures roll back
before publishing any in-memory gift or native success.

After commit, native storage reflects the committed transfer, then the exchange
maps clear without releasing persisted split IDs. Native quest notifications
follow the committed inventory. An uncertain commit acknowledgement or failed
committed projection quarantines both inventories, retains every ID, holds the
bot lease and disconnects the owner for custody review; it is never claimed to
be a successful rollback. Existing periodic companion custody guards remain intact.

## Installation and verification

- Main receipt: `backups/playerbots-recruitment-20261005-004941-367262`, retaining
  Sorcerer `234841-695964`, tank, custody, spacing, quests, engine and all prior mods.
  Ten existing methods in three definitions; two helper classes plus the
  Indeterminate exception; 136 earlier entries byte-identical.
- Binding continuation: `backups/playerbots-recruitment-20261005-005644-110252`.
  Only PlayerBotTrade.equip/tick change; all 141 other entries byte-identical.
  Explicit runtime SCOPES include these already loaded helper methods.
- Fresh agents 47/48 preload original override definitions, preserve hashes and
  rollback, and update three then one classes. Both retain ten companions and two
  human connections. No server lifecycle/client changes or forced trade, tick,
  cast, movement, save, human-character or live DB/ID test writes.
- Full server/commons/command source compile and entire offline companion suite
  pass. Final **56 production checks** pass against source and effective package:
  owner boundaries, exact custody/storage/count/equipped rejection, missing rows,
  full/split transfer SQL, wallets/overflow, human fallthrough, native pair
  locking/cancellation/completion cleanup, alt/Temporary handling and ITEM_USE
  pause/resume. These use world-free actors and recording JDBC, not a live DB.
- Initial loaded capture verifies all 218 methods across eight definitions.
  Final helper refinement is captured/compared separately. Source audit checks
  51 pinned references and 190 selected methods; the final audit/capture outputs
  live under `target/playerbots-trade`. Original schemas/synthetics remain.
- All 15 installed-mod checks and 31 client hashes pass. Base JAR, launcher,
  command/media and other feature receipts remain. Preferences remain unchanged;
  ordinary active-gameplay generated-item provenance grows independently.

Current cumulative override SHA-256 and final capture/setting details are recorded
in `target/playerbots-trade/final-verification.json` and the final receipt manifest.

## PB-VAL-010: user acceptance remains pending

The user will test the actual game. Confirm native trade opening/lock/OK with an
alt and a Temporary Bot; appropriate/wrong-class/worse gear; multiple binding
items; partial-stack and Kinah transfers; cancel/full cube/range/map/combat
boundaries; and normal periodic save/dismiss/resummon preserving inventory/gear.
No actual in-game transaction or durable trade round-trip was exercised here.

Full PB-SCOPE-003 remains **partial**: bot outgoing offers/withdrawals, other group
members, world-bot security, pricing/discounts and crafting trades remain. Those
can continue independently of PB-PORT-005B Spiritmaster and the world planner.
Preserve the full subsystem inventory and all other unfinished port/repair work.

# PB-SCOPE-003A: native owner gifts to playerbots

Installed live and on disk on 5 October 2026. The user requested trading with
owned alts and Temporary Bots so they can equip supplied gear. This is a new
bounded slice of the independent trading track, not a replacement for the next
class strategy or a claim of full upstream trading parity.
