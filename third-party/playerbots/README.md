# AzerothCore Playerbots adaptation

Source: https://github.com/mod-playerbots/mod-playerbots

Pinned revision: `037c01418b5d01506917a3db9b44fd56ac5f965c` (retrieved 2026-10-03).

`upstream/` contains unchanged reference files used in the port. Their SHA-256 hashes are
recorded in `SHA256SUMS`. `LICENSE` and `AUTHORS.md` are unchanged upstream copies.
The original source headers permit GPL version 2 or any later version; this repository
uses GPL version 3. Preserve upstream attribution when distributing the adaptations.

The current snapshot contains 56 original references and 69 scope references,
verified at the pinned revision on 10 October 2026. This is a selected source
subset plus a complete repository tree index, not a complete source checkout.
Missing source/dependencies must be retrieved at the pin before their behavior
is implemented. The source manifests retain independent SHA-256/Git blob IDs.

The authoritative current feature and delivery status is
[the subsystem matrix](../../docs/PLAYERBOTS_SUBSYSTEM_INVENTORY.md).
The whole-project review is [the audit](../../docs/PLAYERBOTS_PORT_AUDIT_20261010.md).
Seven bounded class slices, native independent population, SQL persistence,
requested private/shared storage and mail are present; full class, quest,
professions, autonomous economy/social/matching and encounter parity remain
partial or absent as listed there. Older tables below record mapping history.

`scope-references/manifest.json` records unchanged upstream source hashes and
Git blob identities separately from original `SHA256SUMS`. Attribution and GPL
headers remain intact. No WoW maps, spells, packets or database schema are
substituted for native Aion behavior.

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
