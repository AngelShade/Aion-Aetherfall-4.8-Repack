# Playerbots subsystem inventory and dependency plan

Completed once on 4 October 2026 against **037c01418b5d01506917a3db9b44fd56ac5f965c**.
This is the full subsystem register, replacing the outstanding inventory task.
The focused PB-PORT tracker still owns its existing implementation and validation
IDs. Do not restart this pass each iteration; update the affected row and proceed.

Evidence: the untruncated [pinned repository tree](../third-party/playerbots/scope-references/tree.json)
contains 1,669 entries. The [reference manifest](../third-party/playerbots/scope-references/manifest.json)
records 17 additional unchanged files checked against their upstream Git blob
hashes, alongside the original 51 SHA-256 references. The complete tree covers
class, base, world, dungeon, raid, manager, factory, command, script and database
families. File coverage establishes subsystem existence, **not algorithm parity**.
Rows marked source gate require their actual implementation to be read before
porting; this bounded inventory does not claim to have audited every source line.
GPL attribution in `third-party/playerbots` applies to these additional sources.

All source paths below are relative to that pinned repository. A partial installed
adapter is distinct from an absent subsystem and from withheld native/game tests.
User policies (Temporary scaling, protected alts, recruitment, catch-up mirroring,
saved parties, optional spending and future PvP) remain separate from upstream parity.

| ID | Upstream subsystem and source evidence | Native Aion mapping / current state | Next dependency or deliberate difference |
| --- | --- | --- | --- |
| PB-PORT-004 | Engine/action/trigger/default/multiplier/continuation registries; `src/Bot/Engine`, `src/Bot/Factory/AiFactory.cpp`, `ChangeStrategyAction.cpp` | Installed composition/defaults/evaluated expansion/native threat/fresh continuers in 213711-193912; 49 loaded-engine tests pass | Enables class composition, not a prerequisite for all trade/world lifecycle work. Actual client combat PB-VAL-009; full custom chat strategy editor/persistence remains separate below. |
| PB-PORT-005 | Class/spec rotations, procs, resources, executes and fallback nodes; `src/Ai/Class/*` | 005A Sorcerer single-target chains/upkeep/fillers/MP/boosts installed `234841-695964`; 54 offline checks, actual native/client pending; full class coverage partial | Next 005B Spiritmaster single-target/pet strategy from cached GenericWarlock/AfflictionWarlock and native pet gates; pet coordination 008 only as needed. |
| PB-PORT-006 | Target/heal/focus/icon/AoE values; `src/Ai/Base/Value`, `SetFocusHealTargetsAction.cpp`, `RtiAction.cpp` | Native targets/hate/health/LOS, caster lifetime/runes installed; focus/icon/AoE partial | Source/native marker gate; preserve existing target admission. |
| PB-PORT-007 | Tank/assist/threat/pull/ready/CC; `TankAssistStrategy.cpp`, `ThreatStrategy.cpp`, `PullActions.cpp`, `ReadyCheckAction.cpp` | Native group/hate/opening/pickup/DPS hold and PB-REPAIR-TANK-001 boss-drag adapter repair installed; actual fight acceptance pending; pull/assignment partial | 004/006 and exact pull source; no WoW threat thresholds substituted for Aion hate. |
| PB-PORT-008 | Heals/buffs/debuffs/pets/summons/food coordination; class strategies, `PetsAction.cpp`, `UseFoodStrategy.cpp` | Native affected-recipient reservation/effect stacking/pet tools and supplies partial | Class slices and focus controls; no unsupported summon/totem imitation. |
| PB-PORT-009 | Item usage, loot rights/rolls, bag pressure, selling/buying/repair; `ItemUsageValue.cpp`, `Loot*`, `SellAction.cpp`, `RepairAllAction.cpp` | Players already have native cubes/equipment; decisions/services partial, protection/custody installed | Useful-item categories and native service adapter gate. Having inventory is not autonomous management or trade. |
| PB-PORT-010 | Player/random factories, talents, skills, BiS/stat weights, level maintenance; `src/Bot/Factory`, `src/Mgr/Talent`, `src/Mgr/Item` | Temporary native skills/Stigmas/legal gear/ten-level tiers installed; optimized coherent profiles partial | 005/009; no automatic overwrite of owned-alt gear/build/Stigmas. WoW talents/glyphs have no literal equivalent. |
| PB-PORT-011 | Shared travel/quest/service/grind destination planning; `TravelMgr`, `TravelNode`, `ChooseTravelTargetAction.cpp`, `src/Ai/World` | Shared nearby native quest arbitration installed; world graph/services/route planner absent | Native destination graph and handler gates; owner leash applies to companions, independent bots need different context. |
| PB-PORT-012 | Local pathing/reach/flank/kite/flee/transport, travel skills, death/release/corpse; `MovementActions.cpp`, `FleeManager`, `DeadStrategy.cpp`, `TaxiAction.cpp`, `VehicleActions.cpp` | Follow/flight/formations/teleport/whole-party instance and revive adapters installed; remaining escape/transport/travel skills partial | Aion geodata/flight/teleport/resurrection services, not WoW MotionMaster/opcodes or ghost corpse rules. |
| PB-PORT-001/002/003 | Class final gates; individual/group quest acceptance/reports/object use/rewards/drop; `QuestAction.cpp`, `ChooseTravelTargetAction.cpp`, class strategies | Corrections and shared local execution installed/offline verified | Actual server/client acceptance is PB-VAL, not a reason to re-port these adapters. |
| PB-SCOPE-001 | Autonomous population/account pool/login/logout/activity/level scheduling; `RandomPlayerbotMgr.h`, `RandomBotLevelMgr`, random factory/config | **Absent** independent lifecycle; current PlayerBotService requires owner and party | Build native autonomous session context/population lifecycle first; local activities can reuse combat, world roaming later uses 011. Manager implementation source gate. |
| PB-SCOPE-002 | Native invite/leave/leader/control changes; `AcceptInvitationAction.cpp`, `InviteToGroupAction.cpp`, `LeaveGroupAction.cpp`, `PlayerbotMgr.h` | **Absent** world-bot invitations/control transfer; owned recruitment/groups/presets are installed | Invitation reads security, accepts through native handler, sets inviter as master for random bot, resets strategies/follows/summons. Needs 001 for world bots, not complete rotations. |
| PB-SCOPE-003 | Native trade accept/cancel/offers/item-use/value/discount/crafting; `TradeAction.cpp`, `TradeStatusAction.cpp`, `TradeValues.cpp` | **003A installed** native owner gift begin/lock/confirm/cancel, guarded durable item/Kinah custody and donated upgrades for alts/Temporary Bots; outgoing/world-bot offers, value/discount/crafting remain absent | Verified upstream checks master/group/security, item usefulness and native acceptance. Adapt native ExchangeService and custody; no world graph prerequisite for existing companions. |
| PB-SCOPE-004 | Bank, guild bank, mail and item economy; `BankAction.cpp`, `GuildBankAction.cpp`, `MailAction.cpp`, `SendMailAction.cpp`, `src/Mgr/Item` | Native warehouses/mail and item service decisions **absent** | 009 useful-item/service rules and exact service source gate; retain shared storage custody. No separate auction action was established by this tree/reference pass; do not promise an upstream auction port without source evidence. |
| PB-SCOPE-005 | Crafting, professions, gathering/reveal/fishing/training; `SetCraftAction.cpp`, `TrainerAction.cpp`, `LootNonCombatStrategy.cpp`, `RpgSubActions.cpp` | Aion crafting/extraction/gathering/skill learning equivalents; automatic companion skill maintenance partial, professions **absent** | Native recipe/material/skill/services and source gates. WoW fishing bobber is not a proven Aion 4.8 feature; leave N/A mapping unresolved explicitly. |
| PB-SCOPE-006 | Independent RPG NPC/service/rest/social/wander choices and grind XP; `src/Ai/World/Rpg`, `GrindingStrategy.cpp`, `RpgSubActions.cpp` | Owner quest missions/local services partial; independent activity state machine **absent** | 001 local context first, 011 destination graph for travel; no fictitious world activity completion. |
| PB-SCOPE-007 | Dungeon and raid encounter strategies/actions/triggers/values; `src/Ai/Dungeon`, `src/Ai/Raid`, dungeon repository | Aion hazard/encounter coordination and several boss adapters installed; broad encounter library **partial** | Exact Aion encounter mechanics + class/role coordination; WoW boss scripts cannot be copied. Native alliance topology is a separate adapter. |
| PB-SCOPE-008 | LFG/matchmaking/queues/ready groups; `LfgStrategy.cpp`, `LfgActions.cpp`, config and manager | Companion native group placement installed; autonomous queue/group formation **absent** | 001/002 and native matching service contract; no need to finish every dungeon strategy before queue decisions. |
| PB-SCOPE-009 | Guild/task/petition/arena-team social lifecycle; `src/Mgr/Guild`, `Guild*Action.cpp`, `PetitionSignAction.cpp`, `ArenaTeamActions.cpp` | Native legion equivalent; autonomous decisions **absent** | Native legion authorization/persistence + source gate. WoW arena-team organization is not a direct legion equivalent. |
| PB-SCOPE-010 | Battleground/arena/outdoor PvP/duels/enemy players; `BattlegroundStrategy.cpp`, `DuelStrategy.cpp`, `BattleGroundTactics.cpp`, `NewRpgOutdoorPvP.cpp` | Dedicated PvP AI **deferred by user policy**, native PvE targeting retained | Later native faction/arena/fortress mechanics and source review. Must not restore recruitment PvP/location/flight blockers. |
| PB-SCOPE-011 | Commands/chat shortcuts/filters/security/tells/emotes/strategy editing; `src/Bot/Cmd`, `ChatCommandHandlerStrategy.cpp`, `ChangeStrategyAction.cpp`, `PlayerbotSecurity`, `PlayerbotTextMgr` | Native .bot/HTTP/menu/party commands and account ownership installed; full vocabulary/editor/security/social replies **partial** | Native authorized command routes; strategy editing/persistence is separate from engine composition. No literal WoW command/parser expectation. |
| PB-SCOPE-012 | External packet/event observers, values/cache, world-thread scheduling, repositories, save/reset/debug/performance | `src/Bot/Engine/WorldPacket`, `AiObjectContext`, `src/Script/WorldThr`, `src/Db`, `src/Bot/Debug`, `src/Util` | Native headless lifecycle/tasks/handler adapters/settings/presets installed; care/gear native metadata repository PB-SCOPE-012A installed/offline verified with 32 committed imported rows; user gameplay PB-VAL-011 pending; independent context/event/value lifecycle and diagnostics **partial** | Needed incrementally per subsystem; never emulate WoW session opcodes or call a disconnected helper “ported.” |
| PB-SCOPE-013 | Mount/home/meeting-stone/vehicles/racials/reputation/world buffs/outfits/custom cheats | `CheckMountStateAction.cpp`, `SetHomeAction.cpp`, `UseMeetingStoneAction.cpp`, `VehicleActions.cpp`, `RacialsStrategy.cpp`, `WorldBuffAction.cpp`, `OutfitAction.cpp`, `CheatAction.cpp` | Native flight/teleports/supplies/appearance partial. Companion transmog PB-CUSTOM-APPEARANCE-001 has a corrected source/staged package reviewed 7 October, uninstalled; no live update. Upstream `OutfitAction.cpp` remains a source gate. | Native mount/bind point/effect/appearance where present; WoW-only racials, world-buff schedules, glyphs, meeting stones and vehicle seats not blindly ported. Cheats are explicit policy, not ordinary behavior. |

## Ordered next work and independent tracks

1. **PB-PORT-004 installed:** upstream evaluation multipliers, named state strategies
   and bounded freshly resolved continuers. New port + engine translation repair
   completed; actual client combat/transitions remain PB-VAL-009.
2. **PB-PORT-005A installed:** Sorcerer single-target chain/upkeep/filler/fallback,
   MP and native boost decisions. User in-game testing pending; do not repeat.
   **Next 005B:** Spiritmaster learned single-target/pet strategy from pinned
   GenericWarlock/AfflictionWarlock and native pet-order eligibility. Necessary
   pet coordination is 008; focus/roles/build slices remain independent as needed.
3. **Independent world track:** 001 lifecycle/context -> 006 local activities ->
   011 global destinations/travel. 002 invitation/control can follow lifecycle
   before the world planner and full class coverage are complete.
4. **Independent economy track:** 009 useful-item classification/native services ->
   003 native trade for existing companions -> 004 warehouses/mail and
   005 professions. Does not wait behind the whole world/class backlog.
5. **Encounter/group track:** 007 encounter library, 008 matching and 009 legion
   adapters when corresponding native/source contracts are reviewed. PvP 010 is
   explicitly later. Utility 013 maps only verified native mechanics.
6. **PB-VAL tests remain separate:** server startup, native casts/transitions/
   persistence, live geodata and actual client behavior. GameServer is now running;
   this iteration applied only the reviewed live update after the user requested
   finishing it, with no lifecycle change. Loaded-engine/disk success does not
   close actual-client tests.

Cross-cutting 012 is integrated when a selected subsystem actually needs it,
not repeatedly rebuilt as an excuse to postpone a feature. Existing item-ID
release-path investigation remains an independent documented repair, not proof
that all Playerbots implementation must stop. No parity or “full system done”
claim follows from this inventory.

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
