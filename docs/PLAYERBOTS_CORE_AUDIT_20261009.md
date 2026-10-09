# PB-PORT core implementation audit — 9 October 2026

This audit reuses the completed subsystem inventory and current tracker. It does
not restart the upstream inventory or implement a new port. Maintained source,
production call sites and the actual installed package were checked read-only.

Current installed baseline: PB-PORT-005G, receipt
`playerbots-source-build-20261009-061641-083362`; actual runtime JAR SHA matches
the receipt and inventory:
`95955312ddaafed4eccf1ccd34ca3aa017d3c5d1b982b6d2a6913585f76fefe2`.
All 97 maintained top-level companion classes are present in that JAR. This
establishes package presence, not complete upstream behavior or gameplay success.

## Implemented foundation

| ID | Implemented and connected | Remaining boundary |
| --- | --- | --- |
| PB-PORT-001 | Rune-aware offense priority and final CastAction usefulness; native resource admission | Actual native casts/consumption remain user acceptance |
| PB-PORT-002 | Periodic refresh windows, hybrid direct damage and stronger-effect protection at final admission | Actual native casts/refresh remain user acceptance |
| PB-PORT-003 | Shared local quest arbitration and connected objective execution through native eligibility/handlers | Native/client quest acceptance; global planning belongs to 011 |
| PB-PORT-004 | State-specific strategy composition/defaults, multipliers, bounded engine expansion and fresh native-chain continuers | Actual client combat/transitions; full custom strategy editor and independent context lifecycle are separate scope items |

These are installed implementations with pending acceptance, not missing ports.
Session.tick builds the strategy plan and skill continuers; final CastAction calls
Offense.useful, Skills.canPlan and existing native checks. QuestRoutes delegates
to QuestObjectives; the local executor is connected to Session.tick.

## Core ports that remain partial

| ID | Existing implementation | Missing full-port scope |
| --- | --- | --- |
| PB-PORT-005 | Installed bounded Sorcerer, Spiritmaster, Cleric, Chanter, Templar, Gladiator and Assassin slices, plus generic class rules | Ranger 005H is unimplemented as a dedicated reviewed slice; remaining class-specific behavior and complete rotations/specs remain open |
| PB-PORT-006 | General/caster/rune target values, owner assist, health/range/LOS/hate and target admission | Focus-heal/attack lists, native marker/icon policy and distinct AoE target selection |
| PB-PORT-007 | Native tank hate, taunts, opening/pickup, DPS holds and conservative shared local quest pulls | Full main/off-tank assignment, pull readiness and coordinated CC/assist policy |
| PB-PORT-008 | Group/pet heals, cleanse/resurrection reservations, native summons/orders and effect-stack protection | Full role-aware buff/debuff assignment, refresh and pet resource/recovery coordination |
| PB-PORT-009 | Loot rights/rolls, protected gear upgrades, consumables and opt-in native enchant/salvage/limited purchases | Shared useful-item/service policy and autonomous bag-pressure/service management |
| PB-PORT-010 | Temporary-only native skills/Stigmas, lawful armor/weapons and ten-level gear tiers | Coherent optimized class/build presets linking skills, Stigmas, weapons and scoring |
| PB-PORT-011 | Shared local quest destinations, owner-leashed routes and following completed teleports | Global destination graph and long-range quest/service/grind planning |
| PB-PORT-012 | Follow/formation, reach/flank/local kite and hazard escape, flight/instance transfer, recall and revival adapters | Remaining applicable special movement, transport/travel-skill and death/corpse-travel adapters; exact native mapping is still source-gated |

The gaps above are tracked implementation work. Existing generic behavior does
not mean the full port is finished; pending gameplay testing does not mean its
installed portions are absent. Some special upstream mechanics require a proven
Aion equivalent before implementation.

## Wider scope still absent or partial

Independent bot population/lifecycle (PB-SCOPE-001), world-bot invitations/control
(002), autonomous world activities (006) and queues/social lifecycle (008/009)
are absent. Current Session/Service still require a connected recruiting owner
and their party. Owner gifts are installed, while outgoing/world-bot trade is
unfinished (003). Bank/mail decisions and professions remain absent (004/005).
PvP AI remains explicitly deferred. Independent event/value context, full chat
strategy editing and the item-ID release-path investigation remain unfinished.

There is no reason to re-port 001–004 solely for pending user acceptance.
The current next class pointer remains 005H Ranger. Core 006–012 and independent
world/economy tracks can proceed in bounded slices as their actual dependencies
are met; they do not all wait for complete class parity.

No GameServer/client startup, attach, native cast/effect execution, database or ID
operation was performed. No runtime files changed in this audit.
