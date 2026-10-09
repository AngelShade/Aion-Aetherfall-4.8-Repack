# Class strategy continuation — 4 October 2026

## Assassin rune/chain ordering — installed offline 9 October 2026

**PB-PORT-005G** implements the confirmed missing finisher-before-builder
ordering purpose from pinned AssassinationRogueStrategy. Ordinary single-target
Assassin chain fitness now stays within its 23 band (23..23.875), below existing
mature/urgent rune finishers at 25/24. Low-health evasion, critical recovery,
interrupts, native rune eligibility, poison/bleed upkeep, cost/cooldown/chain/
target/AoE/threat gates and all prior classes remain preserved. Native execution
and rune/MP consumption are unchanged; full Assassin/class/world parity is partial.

The actual installed baseline reproduced a mature admitted finisher losing to an
ordinary chain builder before source edits. All 123 new Assassin checks and the
18 previous suites pass: **19 world-free suites / 2,254 checks**; 60 companion
sources compile. Complete normal Maven JAR/ZIP build, source/artifact binding,
assembly/resource audit, 1,757 handler compilations, full production XML/XSD/JAXB
load and 3,289-class / 172,235-reference linkage pass. 3,554 prior JAR entries are
byte-identical; only Offense.routine and Session.tick differ (23/100 other methods
preserved). No managed resources changed. The final fresh build supersedes the
preliminary output rejected after source refinement.

Canonical guarded installation receipt: `playerbots-source-build-20261009-061641-083362`, under external
`archives/server/game-server/backups`. GameServer SHA:
`95955312ddaafed4eccf1ccd34ca3aa017d3c5d1b982b6d2a6913585f76fefe2`. Installed Commons remains
`b7d6786f38696b9d81425cee04c4ebf16aa242d76e6c20c1754385168ec2cc6c`.
The unchanged complete GameServer builder output was the only copied file;
configuration, geometry, launcher, client settings and all prior mods remain.
Fresh shutdown/source/artifact/runtime/client guards, backup/copy/rollback and
inventory refresh passed via `tools/release-game-server.py`. Postinstall:
20 mod checks / 31 client hashes; 74 client / 115 server historical receipts.
Override remains retired and absent; appearance remains disabled. Server/client
remain off; no startup/attach/native casts/effects/world registration/DB/IDs.
**PB-VAL-017 actual Assassin rune/chain/poison/defense combat remains user-tested.**
See [mapping and receipt](PLAYERBOTS_ASSASSIN_20261009.md).

**Next separate review: PB-PORT-005H Ranger.** Compare existing native ranged
positioning, chains, control, buffs and recovery with pinned Hunter strategies
before choosing a confirmed missing behavior. Full class/world and item-ID
release investigation remain unfinished; installed 001/002 gates are preserved.

## Previous Gladiator counter opportunity — installed offline 9 October 2026

**PB-PORT-005F** adds the confirmed missing reactive-attack ordering purpose from
pinned Arms/Fury Warrior strategies. Direct single-target physical Gladiator
counterattacks now precede ordinary fillers/chains while their actual native
counter event is available. Spite Strike and Counter Leech retain the native
five-second parry window, final costs/cooldowns/chain gates and existing drain
fitness. No event is invented, extended or consumed during planning. Interrupts,
critical healing, threat holds, AoE safety and all prior class strategies remain
preserved. Full Gladiator/class/world parity remains partial.

Complete unchanged normal Maven GameServer output is installed in external receipt
`playerbots-source-build-20261009-055725-016513`, retaining Templar 005E, Chanter,
Cleric, PB-BUILD-003, NAV-004, Spiritmaster and all earlier mods. GameServer SHA
`8fd18d49937f696015098638397de80ac529802740b35da753249ac7429b30c1`;
installed Commons remains
`b7d6786f38696b9d81425cee04c4ebf16aa242d76e6c20c1754385168ec2cc6c`.
Only Offense.routine and Session.tick change existing behavior; 23 other Offense
methods, its original switch method, all 100 other Session methods and native
counter/cast/effect execution are preserved. 3,552 prior JAR entries remain
byte-identical. No managed runtime data/resources differ.

The actual installed baseline reproduced filler displacement in three checks
before source edits. All 66 new Gladiator checks plus 17 existing world-free
suites pass (2,131 checks); 59 companion sources compile. Complete assembly audit,
1,757 handler compilations, full production XML/schema/JAXB load and 3,288-class /
172,218-reference linkage pass. Initial process/client-hash guards refused before
copying. Fresh authorized inspection confirmed shutdown; the full passing
inventory refresh preserved changed system.cfg/SystemOptionGraphics.cfg settings.
The unchanged reviewed package then passed every canonical guard and backup/copy/
rollback check through `tools/release-game-server.py --scope PB-PORT-005F --review <review> --install`.
Only GameServer was copied; Commons/configuration/geometry/launcher/client files
remain unchanged by this delivery. Affected generated caches were archived and
invalidated. Postinstall inventory passes 20 mod checks / 31 client hashes;
74 client/114 server historical receipts recorded. Server/client remain off;
no startup/attach/native casts/effects/world registration/DB/ID operations occurred.
**PB-VAL-016 actual Gladiator counter activation, costs/cooldowns, recovery and
combat acceptance remain pending user testing.** See [mapping and receipt](PLAYERBOTS_GLADIATOR_20261009.md).

**Next separate review: PB-PORT-005G Assassin.** Compare existing native rune
builders/finishers, chains, poison upkeep, utility and defense with pinned Rogue
strategies before selecting a missing purpose. Do not repeat PB-PORT-001/002 final
gates solely for withheld gameplay acceptance. Full class/world and item-ID
release investigation remain unfinished.

## Previous Templar pressure protection — installed offline 9 October 2026

**PB-PORT-005E** adds the confirmed missing proactive tank-defense purpose from
pinned TankWarrior: physical block under pressure and earlier low-health native
shields. Iron Skin/Empyrean Shield keep their CLEANSE classification while their
protective payload can be selected without a debuff. Native hit coverage,
pressure, recipients, active-shield preservation, costs, equipment, DP, cooldowns
and final cast gates remain authoritative. Existing tank hate/taunts, melee,
chains, heal/cleanse reservations and earlier class strategies are preserved.
Full Templar/class/world parity remains partial.

Complete unchanged normal Maven GameServer output is installed in external receipt
`playerbots-source-build-20261009-053655-481400`, retaining Chanter 005D,
Cleric 005C, PB-BUILD-003, NAV-004, Spiritmaster and all earlier mods. GameServer SHA
`8dda6e9598f8027915613f1a93cfc610dbbdeb6ec8c2181a2b395ee9fadf650b`.
Installed Commons remains
`b7d6786f38696b9d81425cee04c4ebf16aa242d76e6c20c1754385168ec2cc6c`.
Only Defense.priority, Healing.priority and Session.tick change existing behavior;
ShieldEffect adds one read-only hit-coverage getter with its four existing methods
unchanged. 3,549 prior JAR entries remain byte-identical. No managed runtime data,
configuration, launcher, geometry or client files changed. The builder Commons
manifest differs only in build metadata; all content entries match, so its runtime
file remains unchanged under the canonical affected-component policy.

71 new Templar checks plus 16 existing world-free suites pass (2,065 checks).
Focused existing defense/healing checks add 64 passing assertions. Complete
assembly/resource audit, 1,757 handler compilations, production XML/schema/JAXB
loading and 3,287-class / 172,193-reference linkage pass. Fresh offline process/
source/artifact/runtime/client guards, backup/copy/rollback and cache invalidation
passed through `tools/release-game-server.py --scope PB-PORT-005E --review <review> --install`.
Postinstall inventory passes 20 mod checks and 31 client hashes; 74 client/113
server historical receipts are recorded. Server/client remain off; no startup,
attach, native casts/effects, world registration, database or ID operations occurred.
**PB-VAL-015 native Templar block/shield activation, costs, party benefit and combat
acceptance remain pending user testing.** See [mapping and receipt](PLAYERBOTS_TEMPLAR_20261009.md).

**Next separate review: PB-PORT-005F Gladiator.** Compare current offense, chains,
drains, AoE safety and defense with pinned Warrior damage strategies before
selecting an actual missing behavior. Full class/world and item-ID release
investigation remain unfinished.

## Previous Chanter mantra maintenance — installed offline 9 October 2026

**PB-PORT-005D** ports the confirmed missing Shaman party-support purpose to
native Chanter mantras. The generic combat-buff gate rejected every toggle;
the bounded Chanter exception now admits native self-targeted mantra auras.
Missing learned mantras are ranked by nearby party HP/MP needs, class support,
defense and travel relevance. Existing active mantras are never switched off or
replaced; native three-slot, conflict, range, cost/cooldown and final cast gates
remain authoritative. Healing, melee/chains, interrupts, pet support and owned-alt
builds remain preserved. Full Chanter/class/world parity remains partial.

Complete unchanged normal Maven GameServer output is installed in external receipt
`playerbots-source-build-20261009-051146-587613`, retaining Cleric 005C,
PB-BUILD-003, NAV-004, Spiritmaster and all earlier mods. GameServer SHA
`e23b0c3d33bf54bf7f13a802f1199a1b19af7b5a9db2388790a028a579d98ba2`;
Commons remains `b7d6786f38696b9d81425cee04c4ebf16aa242d76e6c20c1754385168ec2cc6c`.
Only Session.tick/priority and CombatBuffs.useful change existing behavior;
AuraEffect adds three read-only getters and preserves all six execution methods.
3,543 prior JAR entries remain byte-identical. No managed runtime data/resources,
launcher, configuration, geometry or client files were changed by this delivery.

89 Chanter checks plus 15 existing world-free suites pass (1,994 checks).
Complete assembly/resource audit, all 1,757 handler compilations, production
XML/schema/JAXB loading and 3,285-class / 172,096-reference linkage pass.
Postinstall inventory passes 20 mod checks and 31 client hashes; 74 client/112
server historical receipts are recorded. Fresh offline process/source/artifact/
runtime/client checks and guarded backup/copy/rollback passed through
`tools/release-game-server.py --scope PB-PORT-005D --review <review> --install`.
Server/client remain off. No startup/stop/restart, attach, native cast, world actor,
database or ID operation was performed. **PB-VAL-014 actual mantra activation,
party benefit and combat acceptance remain pending user testing.**
See [confirmed gap, pinned sources and evidence](PLAYERBOTS_CHANTER_20261009.md).

**Next separate class review: PB-PORT-005E Templar.** Compare existing native
tank hate/opening/taunt coordination, defenses, positioning and chains against
pinned TankWarrior before selecting a missing behavior. Its paired Gladiator
strategy, other class/world tracks and item-ID release investigation stay open.

## Previous Cleric recovery timing — installed offline 9 October 2026

**PB-PORT-005C** adds the confirmed missing Cleric distinction between immediate
recovery, delayed HoT ticks and conditional HP healing. Native first-heal timing
and useful amount drive critical/low-health ordering and almost-full HoT
maintenance. Existing group/pet targets, hybrid cleanses, reservations,
resurrection, cast/cost/cooldown gates and owned-alt builds are preserved.
Chanter scoring remains unchanged and is the next separate review, **PB-PORT-005D**.
This bounded recovery slice does not establish full Cleric or Playerbots parity.

Complete unchanged normal Maven GameServer output is installed in external receipt
`playerbots-source-build-20261009-045131-933181`, retaining PB-BUILD-003,
NAV-004, Spiritmaster and all earlier mods. GameServer SHA
`bc9de279a811fbd35c8ab1188ada2f07e41a4fa34fe6560efad32f50d9362d6c`;
Commons, launcher, configuration, geometry and all managed runtime data are unchanged.
Only Healing.priority and Session.tick change existing method behavior; CaseHeal
adds read-only metadata getters with all eight execution methods preserved.
3,544 prior JAR entries remain byte-identical.

87 Cleric checks plus 14 existing world-free suites pass (1,905 checks). Complete
assembly audit, all 1,757 handler compilations, production XML/schema/JAXB loading
and 3,284-class / 171,909-reference linkage pass. Postinstall inventory passes
20 mod checks and 31 current client hashes; 74 client/111 server historical receipts
are recorded. Two changed client settings hashes were refreshed through the full
passing inventory and preserved. Initial process/hash guards refused before any
copy; after the user's normal shutdown, fresh checks and guarded backup/copy
succeeded through `tools/release-game-server.py --scope PB-PORT-005C --review <review> --install`.
No startup/stop/restart, attach, native cast, world actor or database operation was
performed. **PB-VAL-013 actual Cleric casting/healing acceptance remains pending
user testing.** See [gap, native mapping and evidence](PLAYERBOTS_CLERIC_20261009.md).

Current status supersedes the historical receipts below: **PB-PORT-005A Sorcerer
single-target strategy is installed live/disk** in `234841-695964`, retaining tank,
custody, engine and all prior work. Chain/upkeep/filler/fallback, MP recovery and
native caster boost decisions are connected; 54 offline production checks and
loaded methods match. User gameplay testing is pending. Full Sorcerer AoE/CC/
escape and other class strategies remain incomplete. Next slice **PB-PORT-005B**
is Spiritmaster learned single-target/pet strategy. Source/mapping/verification:
`PLAYERBOTS_SORCERER_STRATEGY_20261004.md`.

Current cumulative receipt: `backups/playerbots-recruitment-20261004-183335-252998`
under `target-deploy/game-server`, installed on disk with GameServer stopped at the
user's request. Defensive continuation `123520-131583` follows `115019-456681` and retains the
healing, custody, saved-party, formation, quest, gear, Temporary Bot and concurrent
AI-reload/Kromede installations. Only the cumulative override JAR changed.

The later formation/command receipt `124615-036768` retains these defensive
entries and methods. Later receipts retain them; this continuation also ports
offensive effect/resource decisions and caster/combo target values.
Complete class rotations and
configurable strategy composition remain unfinished.

## Original decisions and Aion equivalents

All sources use the existing pinned mod-playerbots revision
`037c01418b5d01506917a3db9b44fd56ac5f965c`. Sixteen additional unchanged class
references are stored under `third-party/playerbots/upstream/src/Ai/Class`, with
SHA-256 entries. Fetching a reference does not mean its whole strategy is ported.

| Pinned original | Behavioral purpose | Installed Aion equivalent |
| --- | --- | --- |
| Mage `GenericMageStrategy.cpp`: Spellsteal 40, counterspell on enemy healer 40 | Remove useful enemy protection; interrupt a healer instead of ignoring a secondary caster | Sorcerer/Mage legal purge band 40; reachable party-engaged secondary caster selection. Aion removes the effect through its native dispel, without copying it onto the caster. |
| Warlock `GenericWarlockStrategy.cpp`: Devour Magic purge 50, Spell Lock 40 | Offensive dispelling and caster interruption | Spiritmaster's actual learned player skills and existing pet-order template adapters. Native pet purge casting is not newly validated by this fixture. |
| Hunter `GenericHunterStrategy.cpp`: Tranquilizing Shot enrage/magic 61 | Remove dangerous enemy enhancements before routine damage | Band 61 for an actually learned, legal Ranger purge, if available. No foreign Hunter spell or unsupported enrage category is granted. |
| Rogue `AssassinationRogueStrategy.cpp`: Kick 42, Kick on enemy healer 41 | Prioritize available interrupts while retaining class relevance ordering | Native Assassin interrupting effects use those exact base bands. No WoW energy or combo-point mutation is introduced. |

The shared adapter adds bounded native buff relevance to purge bands, favors full
removal over partial power reduction, and retains the current enemy on equal
utility scores. Other eligible casters/buff holders must already be fighting this
party; range, visibility, native start/resource/target conditions, owner/session
eligibility and interrupt reservations still apply. Existing threat waits and
healer support priority remain in effect. The area-safety gate still rejects
casts that could hit unrelated enemies. New target choice does not authorize pulls.

## Corrected translation gaps

`DispelBuffCounterAtkEffect` was missing from hostile skill classification.
Production Ignite Aether, Dispel Magic, Magic Implosion, Aegis Breaker and Disenchant
now participate through learned native metadata. Pure purge/counter-purge actions
need a useful dispellable buff. Hybrid attacks/launchers retain their other native
effects when no removable buff exists.

Planning reads the native dispel controller's eligibility predicate and spell
power/count/level metadata. It never calls the native removal calculation: that
calculation changes effect power and designates effects for removal. Planning
accounts for the counter-purge count quota, including its own debuffs, but does not
trigger the utility merely to erase its own DoT. Native Sanctuary, long-duration
exceptions, in-flight designations, player-buff categories and NPC_BUFF rules are
preserved. NPC_BUFF removal has the native NPC category behavior, rather than an
invented player dispel-level veto. Resistance, counter damage, actual removal,
cooldowns, animation timing and MP remain the skill engine's responsibility.

## Verification

`target/playerbots-enemy-utility-full-validation.txt`: full server/command compile,
all companion regression checks and 26 new production/category/level/priority
comparisons.

`target/playerbots-enemy-utility-native-runtime-v4.txt`: isolated unsaved native
party/NPC actors exercise the installed methods. Repeated planning preserved MP,
effect power and designations; partial power, full removal, level/category and
in-flight exclusions passed. A real installed Ignite Aether cast removed an attack
buff, dealt native counter damage and spent MP. Sigil of Silence cancelled an actual
native enemy healing cast, applied silence and spent MP. Secondary selection,
reservation fallback, stable ties and exact Mage/Rogue interrupt bands passed.

Earlier cast-fixture retries checked impact before the native 1,867 ms hit time;
the successful fixture waits for the actual effect. Use revision 4, not those
earlier timing attempts. Native NPC IDs are released by their Cleaner; the fixture
does not manually release those IDs a second time. Its players, group, session,
lease, effects and isolated instance are removed afterward. No database or human
character writes are made.

`docs/INSTALLED_MODS.json` records current hashes, receipts and preservation checks.
All pre-existing override entries and unselected methods were retained; original
classes were preloaded before each JAR replacement. Both runtime installs retained
the base JAR, launcher, client, UI, settings and zero active human connections.

## Remaining class work

Rune builder/finisher and periodic-refresh decisions are now connected as described
below. Complete class resource/chain rotations,
broader defensive cooldowns and escape movement, class pet utility coordination,
proc decisions, sustained mana management and strategy toggles remain partial.
This does not port Frost/Fire Mage, all Rogue/Hunter/Warlock/Shaman specializations
or every Aion class by virtue of the fetched references. Full world travel/quest
planning, broader dungeon strategies and a real client party combat playthrough
also remain unfinished. Owned-alt level, gear, skills and Stigmas are untouched by
these combat decision changes.

## Defensive continuation

`PlayerBotDefense` uses the same pinned unchanged class references:

| Original decision | Native Aion translation |
| --- | --- |
| Generic Mage: low-health Mana Shield 85; close Frost Nova 50 | Learned native MP shields below 45% health with available mana; legal roots against an engaged pursuer within 5m at band 50. Roots do not receive spell-interrupt priority. |
| Frost Mage: medium-health or attacked Ice Barrier 29 | Learned long-duration native shields, including Stone Skin, below 65% health or while targeted by a party-engaged enemy; native effect conflicts prevent redundant replacement. |
| Hunter: aggro Concussive Shot 20, close Wing Clip 21, low-health Deterrence 35 | Ranger learned snares against its pursuer, close roots, and native defenses below 45%; weapon, range, resource and start conditions remain authoritative. |
| Assassination Rogue: low-health Evasion 29; critical-health Cloak 27 | Assassin learned physical avoidance below 45%; finite native magical-resistance charges below 25% only when a hostile magical cast targets the recipient. Resistance does not remove existing debuffs like WoW Cloak. |

The prior Aion panic-defense threshold below 35% remains for previously supported
effects. Newly recognized pure `ALWAYSRESIST` defenses use incoming-spell relevance
instead. Mage/Sorcerer and Ranger control is suppressed for assigned tank/melee
roles, immobilized targets and another companion's active control reservation.
Damaging snares reserve control too. Candidates must already be engaged with the
party; area-safety and native targeting checks remain. No foreign skills are
granted and no player-owned builds, gear or Stigmas are changed.

Thirty-four production-template and class/health/role comparisons pass. Native
fixture revision 5 exercises installed actions on isolated unsaved actors: Stone
Skin absorbed a hit that otherwise dealt 95 HP and paid MP; Root immobilized a
secondary pursuer; bow-based Entangling Shot applied snare, paid MP and reserved
control; Spelldodging resisted two elemental magical hits and consumed both charges.
Repeated planning did not change resources, and owned-alt setup fingerprints
remained unchanged. Logs: `target/playerbots-defense-final-validation.txt` and
`target/playerbots-defense-native-runtime-v5.txt`.

Fixture retries corrected absent item data, a dummy NPC AI incompatible with the
native cast completion path, and a test hit missing its magical element. These
were fixture corrections; production skill-engine code was not changed. Native
NPC AI is passive in the isolated fixture, and its world actors, group, sessions,
leases, effects and instance are removed afterward without DB/human writes.
Escape skills, active kiting trajectories, all other class rotations and actual
client combat acceptance remain future work.

## Offensive effect/resource and target continuation

**Historical integration finding:** reverification of the prior JAR found that
`CastAction.isUseful` still applies the old `ClassCombat.shouldBurst` veto after
the new offense helper. Four positive-priority cases are rejected. It also
applies a blanket DAMAGE `hasActualBuff` veto, blocking same-ID/stack periodic
refresh and hybrid direct attacks. The next work is PB-PORT-001 then 002, before
expanding class rotations. See the canonical
[tracker](PLAYERBOTS_PORT_REVIEW_20261004.md#reverification-tracker--4-october-2026)
for native mappings, cases and final-action/cast closure criteria. Statements
below describe helper intent; existing passing counts do not prove these final
decisions execute. Full class strategy work is PB-PORT-004/005/008/010.
PB-PORT-001/002 have since been corrected and installed stopped in cumulative
receipt `193448-980138`. Only `Offense.useful` and `CastAction.isUseful` changed;
114 other entries and all prior class/gear/pet/quest helpers are byte-identical.
The final action now uses resource-aware finisher usefulness and effect-specific
periodic usefulness, retaining stronger-effect and nonperiodic duplicate checks.
All 44 final-action/native-planning checks pass against the installed JAR;
actual casts, MP/rune consumption and client acceptance remain pending. The next
offline implementation is PB-PORT-003; no alt setup was rewritten.

Receipts `181655-178651`, `182147-699768` and cumulative `183335-252998` retain every earlier
class, camp, custody, supplies, revival, roster and UI installation. GameServer
remains stopped. Only the cumulative JAR changed; no alt setup was rewritten.

`PlayerBotOffense` adapts the pinned Affliction Warlock missing periodic effect
band 18, Fire Mage 18.5 and Assassination Rogue finisher bands 25/24. Native Aion
effect stacks, rank/level conflicts, tick/expiry and rune caps replace WoW aura and
combo-point APIs. Higher periodic effects are not downgraded. Nearly dead targets
favor direct damage; hybrid spells retain their instant damage. Native signet data
defines positive reduced damage at zero runes, so a weak finisher remains legal
when no builder is usable. No planning calls native damage calculation or consumes
MP/effects. Existing interrupt/purge/threat/healing/area safety remains in charge.

`PlayerBotTargetStrategies` adds the original caster 5–30 second estimated-lifetime
interval and nearly-dead current-target stability, plus Assassin reachable native
rune-target preference. The upstream more-than-three nearby group-members boundary
is retained. It chooses only existing eligible enemies and preserves owner assist/
explicit commands. Estimated lifetime is not a measured damage history. Icons,
focus lists, full class/pet/proc/resource optimization remain incomplete.

Full source suite and effective cumulative package checks pass. The 978 offense
checks include 19 decision comparisons, 954 native template checks, four native
zero-rune families and one coverage assertion; 19 target comparisons pass. These
counts do not establish native casts or full class coverage. Startup/native/client
acceptance remains pending. Local quest-route and Kromede trap continuation are
documented in `PLAYERBOTS_STRATEGY_CONTINUATION_20261004.md`.
