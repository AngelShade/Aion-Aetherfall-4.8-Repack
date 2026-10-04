# Class strategy continuation — 4 October 2026

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
