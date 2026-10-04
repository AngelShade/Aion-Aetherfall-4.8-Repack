# AzerothCore Playerbots adaptation

Source: https://github.com/mod-playerbots/mod-playerbots

Pinned revision: `037c01418b5d01506917a3db9b44fd56ac5f965c` (retrieved 2026-10-03).

`upstream/` contains unchanged reference files used in the port. Their SHA-256 hashes are
recorded in `SHA256SUMS`. `LICENSE` and `AUTHORS.md` are unchanged upstream copies.
The original source headers permit GPL version 2 or any later version; this repository
uses GPL version 3. Preserve upstream attribution when distributing the adaptations.

All 51 cached reference hashes were reverified on 2026-10-04. This subset is not
the complete upstream repository. Current per-system findings and ordered
continuation IDs are in
[the port tracker](../../docs/PLAYERBOTS_PORT_REVIEW_20261004.md#reverification-tracker--4-october-2026).
The mapping table below describes attempted adaptations, not complete parity:
the final rune/periodic gate contradictions are now corrected and installed
(PB-PORT-001/002, native/client acceptance pending). Shared local quest
arbitration is now installed/offline verified (PB-PORT-003; native/client pending).
Each follower validates native objective eligibility, rather than requiring equal
peer variable words. PB-PORT-004 is now installed: state strategies/defaults,
evaluation multipliers, fresh bounded continuers and native chain-successor keys.
Next: PB-PORT-005, the first complete native class strategy slice.

`scope-references/` adds the complete untruncated pinned repository tree and 17
unchanged source references for the one-time full subsystem inventory. Its
`manifest.json` records SHA-256 and upstream Git blob identities separately from
the original `SHA256SUMS`; original references/license/authors remain intact.
See `docs/PLAYERBOTS_SUBSYSTEM_INVENTORY.md` for all scope IDs/dependency tracks
and `docs/PLAYERBOTS_ENGINE_COMPOSITION_20261004.md` for the installed engine work.
These additional sources retain their upstream GPL headers and attribution.

The Aion game server is Java. These C++ files depend on AzerothCore's WoW player,
spell, world/session, navigation and combat APIs, so they are reference source rather
than a library linked into Aion. The Java adapters use Aion's real Player, skill engine,
equipment, progression, geodata and native packets.

| Upstream source | Aion adaptation |
| --- | --- |
| Engine.cpp, Action.h, Trigger.h, Strategy.h | PlayerBotEngine: strategy/trigger/action arbitration, usefulness/possibility checks, prerequisites, alternatives and relevance multipliers |
| HealPriestStrategy.cpp, HealthTriggers.cpp | PlayerBotRules and PlayerBotSession: health bands, emergency healing, healing before damage |
| TankAssistStrategy.cpp, DpsAssistStrategy.cpp | PlayerBotSession: party aggro, tank pickup and leader target assistance |
| PartyMemberToHeal.cpp, HealthTriggers.cpp, HealPriestStrategy.cpp | PlayerBotHealing/Support/Session: native affected-target group healing, eligible party pets, health/distance priorities, in-flight coordination with emergency support and hybrid cleanse/heal scoring; focused lists and full class strategies remain partial |
| DpsTargetValue.cpp, TankTargetValue.cpp | PlayerBotTargetValues/TargetStrategies: General DPS and tank range/hate/other-tank selection, caster lifetime/current-target stability and native rune-target preference; icons/focus controls remain partial |
| AcceptResurrectAction.cpp | PlayerBotSession/ResurrectionService: native Aion skill/rebirth acceptance, rather than WoW resurrection packets |
| BuffAction.cpp | Unchanged consumable-command reference for the revision; full food/consumable automation is not claimed as ported |
| TankWarriorStrategy.cpp, WarriorTriggers.cpp | PlayerBotTank/Session: opening threat and party recovery using actual Aion skill effects and native hate |
| GenericMageStrategy.cpp, GenericWarlockStrategy.cpp, GenericHunterStrategy.cpp, AssassinationRogueStrategy.cpp | PlayerBotEnemyUtility/Skills/Session: native useful offensive purges, exact class base interrupt bands and reachable already-engaged secondary casters; full class rotations and pet utility coordination remain partial |
| GenericMageStrategy.cpp, FrostMageStrategy.cpp, GenericHunterStrategy.cpp, AssassinationRogueStrategy.cpp | PlayerBotDefense/Skills/Session: proactive learned barriers, class health/relevance bands, native finite magical resistance and roots/snares against engaged pursuers; no WoW debuff cleansing is attributed to resistance, and escape movement/full rotations remain partial |
| AfflictionWarlockStrategy.cpp, FireMageStrategy.cpp, AssassinationRogueStrategy.cpp | PlayerBotOffense/Session: native periodic refresh, rank/conflict protection and rune builder/finisher decisions; native reduced-damage zero-rune fallback remains. Full class rotations are not claimed. |
| ChooseTravelTargetAction.cpp/.h | PlayerBotQuestRoutes/Session: group-coordinated nearby active objectives, progress retry/backoff and destination reporting through native stages/handlers. Full world travel and service/new-quest route graphs remain incomplete. |
| MovementActions.cpp, FollowActions.cpp | PlayerBotFlight/Navigation/Travel: mirror the leader's flight state and follow in three dimensions; Aion teleport detection and summon controls are local additions |
| FollowActions.cpp, MovementActions.cpp | Formation/FollowSpeed/Transfers: stable slots, continuous movement, native speed packets and instance recovery are Aion adaptations; commented WoW speed cheats are not claimed as active logic |
| AcceptQuestAction.cpp, ShareQuestAction.cpp, QuestAction.cpp, QueryQuestAction.cpp, DropQuestAction.cpp | PlayerBotQuestSync/Quests/Metadata/Journal: native acceptance, shared quests, reports and abandonment. CompleteQuest supplies missing requirements; automatic leader completion and Yes/No catch-up are the user's requested Aion policies |
| EquipAction.cpp, ItemUsageValue.cpp | PlayerBotGear/Equipment/Care: equip useful inventory upgrades before considering expendable loot; preserve future/quest/useful items |
| BuyAction.cpp, ItemUsageValue.cpp | PlayerBotCare: native nearby vendor purchasing and extraction/enchantment adapters, with per-character opt-in, own Kinah, reserve and daily budget |
| PlayerbotFactory.cpp/.h, RandomPlayerbotFactory.cpp, playerbots.conf.dist | PlayerBotGearPolicy: bounded generation, persistent incremental upgrades, earned progression, class armor preferences, upgrade thresholds and role-based loot/reward choices. One-time empty-slot starter gear and Aion controls are local adaptations |
| PlayerbotFactory.cpp/.h, RandomPlayerbotFactory.cpp | Temporary/BuildRules: generated-only native skills, role Stigmas and legal gear; alt isolation and ten-level tiers are explicit Aion policies |

Intentional differences: each decision rebuilds a bounded queue from current state,
ties are stable, and failed movement prerequisites cannot authorize a cast. Bounded
continuation keys are resolved through fresh admitted actions next tick; actor/action
references do not survive combat/death/map transitions. WoW spell IDs, rotations,
opcodes and database schemas are not portable
and are not treated as Aion equivalents. The rest of Playerbots has not been ported;
see `docs/PLAYERBOTS.md` for the implementation and validation boundaries.
