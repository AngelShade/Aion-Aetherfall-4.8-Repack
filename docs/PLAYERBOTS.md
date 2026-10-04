# Aion player companions

The requested destination is a full Playerbots-style companion system, with owned offline characters first, both generated recruitment models, and PvE first. This document distinguishes implemented server behavior from the substantial work still required for full parity. The current implementation is **not yet a complete replacement for AzerothCore Playerbots**.

Start further iterations with [full port scope and iteration focus](PLAYERBOTS_PORT_SCOPE.md).
The focused tracker covers already attempted ports; independent world bots,
native party invitation/control and player/bot trading remain additional gaps.
One complete upstream inventory/dependency pass is outstanding before the next
implementation slice. PB-PORT-004 is the next existing companion-track candidate,
not a prerequisite for every other missing subsystem.

## Current installed behavior — 4 October 2026

Latest cumulative receipt: `backups/playerbots-recruitment-20261004-203902-131350`.
This retains the upstream-based group/pet healing continuation (`...100323-698137`),
AI-reload/Kromede, custody, formations, supplies/recovery/revival, roster removal
and the offensive/target/local-route continuations. GameServer remains stopped
at the user's request. The canonical next-work/status register is
[PLAYERBOTS_PORT_REVIEW_20261004.md](PLAYERBOTS_PORT_REVIEW_20261004.md#reverification-tracker--4-october-2026).
The two final-cast contradictions found during reverification (PB-PORT-001/002)
are now corrected and installed with 44 passing final-action/native-planning
checks. PB-PORT-003 shared local quest destination/executor arbitration is also
installed with 35 effective/installed production-gate checks. Native/client
acceptance remains pending. Next is PB-PORT-004, strategy composition, positive
multiplier semantics and continuers, before expanding class compositions.
See [quest arbitration evidence](PLAYERBOTS_QUEST_ARBITRATION_20261004.md).
Helpers being installed or template counts passing do not establish full parity.
Native Healing Wind casting, recipient reservations and mana payment passed;
spell-specific legal combat resurrection selection and mixed-party persistence
also passed. Focused heal lists, full class strategies and real-client combat
acceptance remain unfinished. See `PLAYERBOTS_PORT_REVIEW_20261004.md` and
`PLAYERBOTS_VALIDATION_20261004.md` for current evidence and limits.
The current baseline supersedes historical generated level-1 choices and automatic
equipment management for owned alts described below. Create now distinguishes
**Temporary Bots** from **player-owned alts**. Temporary Bots use the owner's level,
native class skills, role-weighted Stigmas and class/role-appropriate armor/weapons.
They retain a dedicated roster record and progress after dismissal, while their
world actors are removed. Player-owned alts keep their existing setup; automatic
level, class, build, gear and Stigma replacement is disabled for them.

Temporary maintenance runs outside combat/casting/looting/death, preserves health
and mana fractions and learns new skills between gear tiers. Auto Gear supplies a
new legal tier at 20/30/40/50/60; the current native level cap is 65. Quality ceilings
are legendary at 20, unique at 30, epic at 40/50 and mythic at 60, with native
template availability determining the actual items. Level 65 stays in tier 60.
Explicit per-bot gear acquisition, weapon/profile preferences and Auto Gear On/Off
remain available. Purchases/enchanting/extraction remain per-bot opt-in with budget
and item protection. Automatically replaced factory gear is tracked separately
from earned gear; generated items cannot be extracted for economy farming.

Movement now maintains a continuous native movement clock, advertises its actual
follow speed to the client and uses damped, stable formation slots. Catch-up and
flight behavior preserve native combat/status constraints. Map/instance transfers
process every companion independently, recover despawned actors and preserve party
roles, equipment, skills and life values. Tank AI scores genuine native positive
enmity, opens with threat tools and recovers targets from party members/pets.

Quest handling uses native handlers for acceptance, intermediate NPC conversations,
object interaction/loot and completion. XML contact/report steps and verified owner
dialogue witnesses advance intermediate objectives without treating them as final
turn-ins. Successful owner completion can supply native requirements and choose a
class/build reward. Nearby quest combat remains a per-bot toggle and avoids pulling
unengaged packs. Custom branching scripts without a verified action remain visible
as requests for player guidance; arbitrary quest variables are not copied.

Temporary Bots can now be explicitly saved and summoned again with the same native
character identity/progress. The Party tab saves a named preset; the Roster tab
summons presets containing both owned alts and Temporary Bots. Presets restore
roles/orders and keep other human members/companions, subject to normal free slots.
Removing a preset never deletes its characters or saved bot progress. See
[Saved bots and parties](PLAYERBOTS_SAVED_PARTIES.md).

A focused upstream-only review corrected offensive threat-skill classification and
native support target eligibility. Continued target values now finish weaker
in-range enemies and let tanks reinforce weak hate without stealing other tanks'
targets. Caster lifetime, marking and combo target strategies remain partial.
The original/source comparison is in `PLAYERBOTS_PORT_REVIEW_20261004.md`.

The implementation follows pinned WoW Playerbots strategy/factory references through
Aion Java adapters and native APIs. It is not complete WoW parity. Optimized class
rotations, autonomous world travel/quest planning, broader dungeon/boss strategies
and dedicated PvP AI remain unfinished. Actual in-game motion, player-driven persisted recruitment,
pet/menu coexistence and player-led dungeon/quest acceptance remain pending;
native persistence/unsaved fixtures and isolated browser checks are recorded in
`PLAYERBOTS_VALIDATION_20261004.md`.

## Historical generated creation repair and starting level — 4 October 2026

The reported Babeey creation failures for IDs 15835 and 20529 came from native
skill auto-learning: a crafting/morph recipe callback accessed a missing recipe
list before the new character had a database row. `PlayerService.newPlayer` now
initializes a deferred `PlayerCreationRecipes` list before skill callbacks;
`storeNewPlayer` persists those recipes after the character INSERT so native
foreign keys remain valid. Companion creation also initializes the normal
position/known list, effect/flight controllers, equipment/passive stats and life
values needed by its strict persistence path. Cleanup preserves the original
failure if initialization was incomplete.

Create companion → Dedicated companion now offers **My current level** (compatible
bound common-quality equipment and native class skills) or **Level 1** (normal
starter equipment/skills). The selector changes the offered class list. The
server accepts only these two choices and reads the current level from the owner;
existing `.bot generate` calls still default to owner level. Account-character
creation stays level 1. Level-1 dedicated companions outside the configured
recruitment level difference are saved in the roster without falsely reporting
creation failure; they require an account character within that level range.

Six reviewed existing methods plus two helpers were applied on the actual latest
cumulative override, then one initialization method was corrected following the
native construction test. All other installed members/JAR entries, recruitment
rules, companion settings, launcher and client hashes were retained. Receipts:
`backups/playerbots-recruitment-20261004-031234-189829` and final
`backups/playerbots-recruitment-20261004-031433-971037`. Full compilation/offline
checks passed, as did 20 authenticated native-browser fixture actions and 56
production-data in-memory cases across both factions, level-1 starting classes
and level-12/65 advanced classes. These test actors were never stored or spawned.

Database inspection proved both failed IDs had no players, inventory, skill or
recipe rows. Only those empty, unready account-1 roster reservations were removed
inside a transaction; original metadata and restore SQL are backed up in
`backups/playerbots-pending-20261004-0316`. Actual character data was not deleted.
The live patches retained Babe and Queenbabe; the server subsequently entered its
native shutdown/save path at 03:15:40. Actual persisted creation, recruitment and
re-login remain a user/gameplay acceptance check after startup and retry.

## Installed management window redesign — 4 October 2026

The command bar's Companions action now opens a management page with Party,
Roster, Create companion and Party quests tabs. A compact active-party list
selects one companion detail panel in the same window. Its Overview, Equipment,
Quests, Care and Activity tabs retain individual orders, roles/settings, inventory
equipment, quest missions and catch-up answers, spending limits and announcements.
Offline roster entries open a truthful identity/recruitment panel in the same
window; live equipment and quest data become available after recruitment.

The roster supports search, availability/type filters, name/level/class sorting
and 12 entries per page. A large saved roster does not raise the normal active
party limit. Selection, tabs, roster navigation and care drafts survive refresh;
care drafts clear after a successful save. Narrow windows use a back button to
move between the party list and selected detail panel.

Only `config/playerbots/media/bots.html`, `bots.css` and `bots.js` were replaced
from the current deployment, with backup/receipt:
`backups/playerbots-ui-20261004-025259-127970`. Client files, server JARs,
cumulative recruitment/quest/care override and launcher hashes were verified
unchanged. Installed inventory now follows bounded UI receipts without treating
the historical companion receipt's earlier media hashes as the current page.

The actual installed Awesomium renderer passed isolated fixture checks at
1280×900, 900×700 and 390×844: 18 authenticated actions, five companions' grouped
quest progress, a 103-entry roster, paging/search/filtering, offline inspection,
selection/tab persistence, care drafts and non-equipment inventory items. The
rendered screenshots under `output/playwright/playerbots` were inspected. These
use fixture characters, not a signed-in game account. The server was stopped
during installation; live route and in-game acceptance remain pending startup
and reopening Companions.

## Recruitment and commands

Companions are real persisted Aion `Player` objects. They use normal character stats, learned skills, equipment, cooldowns, progression, movement speed, skill conditions and native player/party packets. They have no fabricated network connection and do not inherit the recruiting account's staff privileges.

```text
.bot list
.bot add MyOfflineCleric
.bot create NewPriest priest
.bot generate MyTank templar
.bot role MyTank tank
.bot follow all
.bot summon all
.bot summon MyTank
.bot stay MyOfflineCleric
.bot guard MyTank
.bot passive all
.bot attack all
.bot aoe on all
.bot supplies on all
.bot loot on all
.bot gear on MyTank
.bot inventory MyTank
.bot equip MyTank 123456 MAIN_HAND
.bot quests MyOfflineCleric
.bot share 1234 all
.bot questing on all
.bot answer MyOfflineCleric 1234 yes
.bot enchant on MyTank
.bot salvage on MyTank
.bot partysync on all
.bot mission MyOfflineCleric 1234
.bot mission MyOfflineCleric 0
.bot dismiss all
```

`add` recruits an eligible offline character from the player's own account. `create` creates a normal level-1 starting-class character, uses an ordinary account character slot and recruits it if the level-gap rule permits. Otherwise the character remains playable normally.

`generate` creates a persistent companion in a separate roster, matched to the owner's level **at creation**. Below level 10, choose a starting class; at level 10 or above, choose an advanced class. Class command names follow Aion's internal names: `spirit_master`, `rider` (Aethertech), `gunner`, `bard`, etc. Generated advanced characters receive compatible common-quality bound starter equipment. They learn normal automatic skills; they do not receive every stigma or unavailable skill. They earn progression after creation instead of repeatedly resetting to the owner's level.

Recruitment requires the same account/faction, an offline non-banned/non-deleting character, no prison for the recruited character, and the configured level difference (default 10). Dead companions can be recruited as corpses and wait for a normal resurrection; recruitment does not refill their health. The recruiting player must be connected and spawned, outside active combat, and have a valid normal party with a free slot (a solo owner creates one automatically). The native combat timer lasts ten seconds after attacking or being attacked. Map/PvP flags, duels, flight and map allowlists do not block recruitment. Alliance membership remains an unsupported party topology with a specific message, rather than the old generic PvP refusal. Defaults allow five companions per owner and 100 active companions server-wide. Separate generated rosters default to 20 per account. Failed generated creations remain pending and hidden; their IDs remain reserved across server restarts.

`stay` prevents voluntary movement while allowing skills in reach; `guard` keeps a position and uses a 20m guard leash; `passive` cancels attacks/casts and follows without casting or attacking. New companions default to automatic equipment and questing on; AoE, corpse collection, enchanting and extraction default off. Recovery consumables and party completion synchronization default on. Existing per-character choices are preserved. Role/AoE/supplies/gear/loot/questing and separate care/catch-up settings persist under `config/playerbots` with account ownership checks.

`questing on` automatically accepts the owner's eligible shareable quests through the native service. NPC visits select from NPCs known to and within 40m of the **owner**, including eligible ordinary quests the owner has not taken. A companion walks to the NPC, uses the actual acceptance/turn-in dialog, announces success and returns to the owner before another errand. Native pages 4/1011/4762 are supported; story-specific pages require guidance. Collection turn-ins check and consume actual native requirements. Ready hunting/report/collection quests turn in at the verified reward NPC. Ordinary reward selection retains class compatibility and conservative gear scoring. Extended rewards and custom campaign/story dialogs still need dedicated adapters. Failed interactions wait 30 seconds before retrying.

When the bot is behind the owner's completed quest chain, the panel and native chat present a named Yes/No request with the real prerequisite branch. Yes permits pursuing that quest and its prerequisites near the owner. No suppresses the selected pursuit and abandons only associated AI-managed quests which Aion permits abandoning; prerequisites needed by another approved chain are retained. These decisions persist.

Party completion synchronization captures completion counts for quests accepted together and persists that relationship. Turning in a new completion by the owner readies eligible native MonsterHunt, ReportTo and ItemCollecting quests for companion turn-in, supplying missing quest requirements through native item APIs and consuming collection requirements normally. It never copies rewards or marks historical unfinished quests complete merely because the owner completed them before recruitment. Actual reward selection, capacity, completion counters, progression and rewards remain in each companion's native NPC handler. Quest Kinah requirements use only the bot's balance; missing Kinah is not fabricated. Repeat acceptance obeys native repeat/cooldown rules. This is the user's requested Aion trigger, adapted from WoW's CompleteQuest routine, not a claim that WoW automatically mirrors every leader turn-in by default.

The party quest tracker groups named active quests across all five companions, shows individual readiness and verified NPC/map/XYZ/distance information. MonsterHunt objectives expose decoded native counters. Locations are verified for the current map; dungeon locations come from actual NPCs in the current instance. Unknown/custom locations say so rather than inventing coordinates. `.bot quests <name>` also reports names and destinations.

Following/passive companions mirror the owner's native powered-flight/glide state and move in three dimensions without snapping to ground paths. Flight points, native cooldowns, Daeva/transform/no-fly rules and collision/hazards remain authoritative. A controlled bot can follow its already-flying owner outside a normal flight zone without inheriting staff privileges. It lands after descending near real ground. Ordinary ground breadcrumbs are cleared while airborne.

All orders follow owner map/instance transfers; same-map position discontinuities are also detected. Transfers wait for owner and bot combat to end instead of dismissing a bot. Summon controls relocate an individual or the whole party through the same native path. Native death/combat/admission guards remain; guard anchors are reset at the new location. This is owner relocation, not independent world/portal/transport planning.

Automatic equipment uses native class/level/mastery/binding rules, handles empty hands and compares the full displaced weapon setup. Enchant/extraction controls are separate per-bot opt-ins. Care uses native item actions, animations and success/failure, ordinary armour toward +5, and actual nearby normal vendor inventories/prices. It uses only the bot's own Kinah, retains at least 10,000 Kinah and 25% of its balance, and has an editable daily purchase budget (default 50,000; zero disables purchases). Equipped items, original possessions, upgrades, future-level gear, quest/required items, valuable quality, enhanced/socketed/fused/skinned gear are protected from extraction. Only expendable new loot is extracted. Each eligible armour item has at most three attempts per care session/day; dismissing resets the session attempt cap, while the purchase budget persists. Advanced set/socket scoring, high enchant targets and general repair/selling/trading remain unfinished.

For native `MonsterHunt` handlers, companions can target incomplete kill objectives within 12m of the stationary owner when the party is out of combat and the companion has at least 85% HP and 60% MP. Candidates must be hostile, unengaged, normal/junk-rated NPCs no more than two levels above the companion, and pass the existing crowd-control/target/geodata policy. The query uses the handler's actual current-step and packed kill counters without changing progress. Kill credit still comes from normal combat.

`mission <name> <quest-id>` assigns one active ordinary MonsterHunt quest and enables questing. The companion selects native spawn locations on the current open-world map, walks to the objective, acquires eligible visible objective mobs, then approaches the handler's actual reward NPC and uses its normal reward dialog. Extended rewards remain excluded. Travel stays within 180m of a stationary owner by default (`mission_distance`, clamped to 45–500m); moving the owner pauses travel and resumes following. Low resources cause a return to the owner and normal recovery. Planning scans at most 2,048 spawn/group entries and keeps at most 256 candidates. A destination without distance progress for 12 seconds is skipped for 60 seconds; the assignment expires after ten minutes. Any new follow/stay/guard/passive order, questing-off, map transfer or `mission <name> 0` cancels it. It does not grant objective credit, teleport through terrain, cross maps or handle every scripted quest type. Missions are not persisted across dismissal.

## Implemented behavior and current limits

| Area | Implemented in source | Remaining for the full system |
| --- | --- | --- |
| Recruitment | Owned offline characters; normal generated level-1 characters; separate level-matched generated roster; login/delete/recruit exclusion | Recovery/admin management of partial generated creations; appearance selection; roster promotion and retirement |
| Decision engine | Ported strategy/trigger/action relevance logic; bounded arbitration; prerequisites, alternatives, multipliers; combat/noncombat/death states | Broad upstream strategy library and configuration presets |
| Combat | Party aggro assistance, tank pickup without taunting other tanks, leader/explicit targets, healing before damage, healing fit/speed, removable-debuff severity and ally-health priorities, mental/physical cleanse capability checks, resurrection skills, defensive skills, interrupts, native cooldowns/costs, available chains and counter windows; damage/cast-time/drain/finisher ranking; preferred higher ranks with lower-rank fallback; native charged release windows; bounded native-hate damage pauses | Optimized per-class/spec rotations, predictive threat budgeting, multi-target assignments, encounter-specific dispel decisions and predictive incoming damage |
| Class mechanics | Learned skills across all 17 classes; class-appropriate short combat preparations from native stat effects; Assassin rune-building/burst timing and collision-tested movement for back-position attacks; stable native buff/toggle capacities; Aethertech Embark setup; Chanter melee spacing; Spiritmaster native pet-order template selection and priorities, summoning/follow/attacks; direct ground-cast coordinates | Complete class-specific buff/stance/weapon/pet policies, complete positional combos, traps/totems, charged ground casts and transformations |
| Recovery | Normal HP/MP resting, owned recovery consumables, dead-character recruitment for received resurrection, existing rebirth effects and passive restoration after revival | Wipe planning, corpse runs and owner-approved bind/instance recovery |
| Navigation | Ground breadcrumbs/A*, three-dimensional leader flight/glide, native owner teleport follow on the same/different map, individual/party summon controls, same-map assigned quest routes | World path search/navmesh, robust formations/obstacle recovery, independent portals, doors and transports |
| Encounters | Protect sleep/fear/paralysis; coordinate interrupts; avoid visible circle/donut/cone/line casts; supported oil/blade/ice/firestorm and Drakenspire wave profiles; Vasharti reflective-shield protection and matching-flame approach; targeted area spreading, tank facing and hazard-aware companion/summon movement | Additional persistent emitters and boss mechanics, coordinated add assignments, phase strategies and complete dungeon/raid strategies |
| Quests | Automatic eligible leader sharing, player-centred nearby pickup, hunting/report/collection turn-in, Yes/No catch-up, requested party completion sync, named five-bot tracker and verified local NPC destinations; native credit/rewards/persistence | Independent world planning, custom campaigns/story dialogs, extended reward selections, escort/gather/use-item objectives and additional quest models |
| Inventory/loot | Native loot rights/Kinah distribution, opt-in corpse collection, conservative auto equipment, per-bot budgeted normal-shop enchant/extraction care | Owner-controlled need/greed, general trading/selling/repair, advanced gear/set/socket scoring, crafting and gathering |
| Persistence | Inventory and private progression in one SQL transaction; life stats, XP/DP, quests, learned skills, cooldowns, effects, NPC factions and rank changes; periodic/dismiss/logout/shutdown saves; retry reservation | Fault-injected database/live restart testing and recovery tooling |
| Client | Native character rendering, movement/skill effects, group panels/chat commands; dedicated authenticated companion browser panel; roster, both creation models, roles/orders/settings, equipment, quest sharing/missions; incremental Additional Functions and `/companions` package staged and checked | Actual in-game menu/pet/signing acceptance, native item tooltips/artwork, richer inspection and live state refresh |
| PvP/large groups | Recruitment allowed in PvP-capable maps/duels outside active combat; no PvP/flight dismissal or support/transfer veto; current offensive planner still uses NPC targets and native late-hit revalidation | Dedicated PvP target selection/strategies, battleground/arena tactics, alliances and raids |

Skill coverage checks enumerate real templates and faction/class skill trees; recognizing a template does **not** prove an optimized rotation or correct live use of every skill. Equipment scoring is a conservative heuristic and does not yet account for every socket, set bonus or conditional modifier. Generic cast avoidance is not a complete encounter strategy.

Hazard planning reads visible NPC casts and native area properties, including directional cones/lines, inner safe pockets and altitude. Persistent profiles cover `spilled_oil` (19658), `bladestorm` (20748), `malicious_ice_storm` (21180), Tahabata firestorm (20759; NPC 283102 uses 20753), and the native Drakenspire large 29m half-wave and small 22m spatial wave scripts. Scripted waves only affect player planning because their native scripts target players. Movement samples each proposed segment and rejects entering a field or increasing penetration into any existing field. Escape candidates are bounded to 96 directions/distances within 16m; native geometry still determines whether a step is possible. FOLLOW companions may cancel offensive casts to react; support casts finish, emergency recovery outranks generic avoidance, and blocked escape falls through to another valid action. Targeted area casts trigger local separation from nearby allies; tanks seek an anchor facing their target away from nearby non-tanks. STAY companions retain their position. These are conservative local policies, not a complete encounter strategy, predictive damage model or navigation mesh.

For native `brigade_general_vasharti` shields 20530/20531, new attacks require the matching 20535/20536 protection with enough remaining duration for the intended cast. Following companions approach a visible matching `dancing_flame`, wait for its normal pulse and avoid the opposite flame's resistance penalty. Summons check their own protection; AoE planning and charged releases recheck the shield before committing. Protection is never granted artificially, and already launched attacks retain native damage/reflection behavior. These source policies remain unverified in a live Rentus Base encounter.

The native skill engine remains authoritative. Planning never advances chains, consumes rune effects, reduces dispel power or pays resources. It checks weapon/form/target/position/abnormal conditions, resource/item costs and counter windows before scheduling a cast. Actual casts validate conditions and costs again, and use server motion/hit timing. Charged skills use native release stages and cast-speed thresholds, with animation timing for the actual released stage. Ground casts use native coordinate packets. Combat targets must be engaged hostile PvE NPCs, an explicitly commanded valid NPC, or a locally selected objective under the opt-in quest policy. Human players and their summons are excluded. AoE candidates reject unengaged or controlled enemies. Projectile spell hits and delayed auto-attacks recheck the current owner's/party/order/target policy at application time, including attacks by a companion's summon.

Automatic buffing retains existing conflicting preparations instead of endlessly replacing them. It respects the native mantra/toggle/chant and Ranger preparation capacities. This is a conservative policy; selecting the best buff combination for each encounter is still required. Assassin bursts wait for usable runes, a nearly defeated target or expiring runes; this is one class mechanic rather than a complete Assassin rotation.

Non-tanks pause new damage when their native hate (including their own spirit) reaches 85% of a living party tank's hate. The pause releases below 70% or after 2.5 seconds, followed by a 1.5-second damage interval. Healing, defense and interrupts remain available. Solo/no-tank fights and targets below 10% HP bypass it. Already committed hits retain native behavior. This limits aggro competition without claiming predictive threat control.

## Dedicated companion panel

Equipment acquisition is configured per companion in its Equipment tab, or with
`.bot gearpolicy <name> <mode> <profile> <quality> <level> <ratio> <weapon>
<vendors:true|false> <rolls>`. Existing characters default to `EARNED`, `AUTO`,
`LEGEND`, level `0`, ratio `1.1`, weapon `AUTO`, vendors `false` and `UPGRADES`
rolls. Turning Auto equipment off leaves manual equipment control.

| Acquisition | Behavior |
| --- | --- |
| EARNED | Uses inventory loot and native quest rewards. Optional normal-shop upgrades use the bot's own Kinah and the Care reserve/daily budget. |
| STARTER | Generates equipment for empty normal slots once, recording those slots persistently. Subsequent progression uses earned gear. |
| GENERATED | Generates useful upgrades within the chosen native quality and level ceiling, retaining previous possessions and better earned gear. |

Profiles are `AUTO`, `TANK`, `HEALER`, `DAMAGE` and `SUPPORT`, independent of the
combat role. Weapon preferences filter native weapon types; Auto tank gear keeps
a shield-capable setup. Quality choices are Common, Superior (`RARE`), Heroic
(`LEGEND`), Fabled (`UNIQUE`), Eternal (`EPIC`) and Mythic. Level 0 follows the
owner, capped at the companion's actual level. Generation uses ordinary native
equipment and excludes Abyss, temporary, limited-one, wings/plumes, world-bound
items and special acquisition currencies. It does not generate skill masteries,
enchants, sockets or quest completion. Generated items are real persisted items;
automatic extraction protects their recorded object IDs across recruitment/restart.

Decisions compare displaced hands, native dual-wield eligibility,
class/faction/level/mastery/rank/gender restrictions, class armor preference,
enchantment, identified bonus stats, manastones and fused bonus modifiers. Native
identification and binding use normal timed actions. The configured ratio requires
a meaningful improvement before replacement. Quest rewards use equipment gain,
then future usefulness/vendor value, without the former weapon-type bonus.
Normal party dice may be passed, rolled for upgrades or rolled for all items;
Kinah bids are passed. Aion has no separate WoW Need/Greed tiers. These are
weighted decisions, not a complete best-in-slot optimizer or dungeon loot planner.

The production page is `/market/companions`, served by the existing market HTTP listener. It reads actual `PlayerBotSession`, account roster, private inventory and quest state. Mutations call the same authoritative services as `.bot`. The native client opens it through Additional Functions → Player Companions or `/companions`. The panel uses English labels, responsive legacy WebKit styles, disclosures, individual/party summons, catch-up replies, care toggles/budgets and a grouped quest tracker. It refreshes every ten seconds while no input/select is focused, and retains manual Refresh; no demonstration account data is included in production media. Close/reopen an already-open panel to load updated scripts.

State access requires the logged-in human character's native web token. Actions require POST plus a short-lived, single-use ticket bound to that exact player and client connection; refresh replaces that player's previous ticket. Duplicate fields, oversized bodies, expired/replayed tickets and another supplied origin are rejected. An interrupted/uncertain submission clears the UI ticket and requires Refresh before another mutation. Production HTTP route checks exercise actual media and unauthenticated refusals; signed-in mutations against a real game/database remain live acceptance work.

`client-mods/playerbots/prepare.py` stages six files in a new directory outside the installed client. It reads the current installed DLL/menu baseline and rejects an unknown/newer browser implementation. Only the existing browser/authentication caves and `PrivateMenus.lua` entry change; other DLL bytes and every other archive entry are preserved. The three addon packages are re-signed together, and the stock `Pub.key` is preserved and omitted from installation payloads. The package from `target/playerbots-client-verified` is now installed. Its pre-installation verifier intentionally refuses the changed installed baseline; use the installed/backup verifier below for the current installation. These commands document the original staging and fixture checks:

```powershell
python client-mods/playerbots/verify_package.py --client '<CLIENT_ROOT>' --staged target/playerbots-client-verified
python client-mods/transmog-menu/tests/verify_market_browser.py --companions
python client-mods/playerbots/verify_browser.py --browser-bin '<CLIENT_ROOT>/bin64'
```

The native instruction verifier executes the generated x64 hooks in an isolated process, including exact URL matching, token handoff, missing-token fallback and old-route preservation. The browser verifier loads production HTML/CSS/JS in the installed Awesomium DLL offscreen with a clearly isolated HTTP fixture and exercises ten controls at 1280×900 and 390×844. Screenshots are under `output/playwright/playerbots`. This validates native browser compatibility and request wiring, not actual Aion gameplay.

## Native party command bar

The signed client now also includes a small movable companion bar. Attack,
Follow, Stay and Summon are visible initially; `+` expands Guard, Passive and an entry
to the existing companion panel. These six orders route through native chat
as `.bot <order> all`, retaining ownership, normal party and target checks.
Summon moves already active companions near the owner using the existing server
travel checks; it does not recruit dismissed characters.
Commands are never sent through a demonstration HTTP account or browser page.

Drag the title to move the bar. `x` shrinks the same dialog into a native party
icon; drag its top grip to move it and click it to restore the commands at that
anchor. `/botbar` also restores the bar. Position and mode persist locally in
`PlayerBotBar.ini`; viewport clamps keep the grip reachable after resizing.
The bar appears on entering the world and does not claim to distinguish bots
from human party members. With no recruited companions, the existing server
command returns its normal recruitment message.

Installed client receipt: `TransmogMenu-backups/playerbot-bar-20261004-013956-397`.
The six-file update changes only the native icon bridge, addon archive, addon
key and three signatures. The source baseline rebuilt identically to the
previous installed bridge apart from two PE timestamps. Existing archive entries,
pet/bundled Lua packages, Game.dll, base/English UI, server recruitment/inventory
override and graphics/cursor restore records remain intact.

Deterministic checks print `OK` or fail nonzero:

```powershell
python client-mods/playerbots/verify_bar_lua.py --client '<CLIENT_ROOT>'
& target/playerbots-bar-native.exe target/playerbots-bar-native-fixture
```

Real Lua widget checks cover four scales, moving between expanded/icon modes,
five order mappings, disconnect handling and the existing Wardrobe shared timer.
Native fixtures cover the allowlist, rate limit, queue clearing on disconnect,
viewport clamps and saved icon position. Actual Awesomium tests preserve native
icon decoding/callback chains and Wardrobe/Journey callbacks. Reopen the client
for actual appearance, dragging, party command and combined pet/menu acceptance.
The original package's pre-install hash verifier intentionally rejects the new
installed baseline; use the receipt and installed-mod inventory for preservation.

The user's 4 October screenshot exposed a flaw in the first layout: command
frames included the title offset even though the native dialog already adds it.
Read-only inspection at UI scale 1.125 confirmed a 28.125px content offset and
40.5px content height, while Attack occupied 30.375–61.875px within that content
area. The controls were therefore clipped by the parent, hiding their text.

The layout-2 repair places the first row at content y=4, the expanded row at
y=37 and the hint at y=66; expanded height is 116 and icon height is 52. It
preserves the existing native bridge and every other addon entry. Its geometry
verifier reproduces the observed clipping and executes the client's actual
coordinate and title-hit routines for 60 visible-control checks across compact,
expanded and icon modes at four scales. Real Lua checks also cover collapse,
hide/reopen, anchor preservation and all five party orders. This repair is now
installed from `target/playerbots-bar-layout-2-install-20261004-021542-885` with
Aion closed. Receipt: `TransmogMenu-backups/playerbot-bar-20261004-021558-165`.
Post-install verification confirms only the two bar entries changed in the
addon; native DLLs, layouts, current settings, saved anchor and deployed server
JAR/override/launcher remain byte-identical. All three addon signatures and
graphics/cursor launcher guards pass. Actual corrected rendering and order
behavior still require reopening Aion and an in-game check.

```powershell
python client-mods/playerbots/verify_bar_geometry.py --live-capture target/playerbots-bar-clipping-live.json
```

Layout-3 is installed from `target/playerbots-bar-layout3-final`. Its main row
includes Summon, with eight-pixel horizontal gaps. Both rows use 28-pixel button
heights and twelve pixels between rows; the secondary row has twelve-pixel
horizontal gaps. The bar is 400 by 70 compact and 400 by 128 expanded, retaining
the same movable 44 by 52 hidden icon and saved anchor. Receipt:
`TransmogMenu-backups/playerbot-bar-20261004-100628-843`.
The current source bridge rebuilt identically to the actual previous installed
bridge apart from PE timestamps before adding only `.bot summon all` to its
allowlist. Only the bar's Lua/XML entries changed in the existing addon archive;
every other client feature and the latest deployed server override were preserved.
All three signatures, native command fixture, actual Lua 5.1 controls and 68
native coordinate/hit checks at four scales pass. Geometry checks now require
space around button borders as well as containment. The isolated Awesomium
fixture also passes native icon, Wardrobe and Journey callback regressions.
The user's screenshot establishes layout-2's remaining spacing issue; layout-3
appearance and Summon gameplay still need verification after reopening Aion.

The next screenshot exposed a separate skin defect: `v5_dialog2` paints a footer
separator 34 pixels above its bottom. At expanded height 128 that line lands at
local dialog y=94, crossing the second row at y=71..99 after the title/content
offset. The row was inside the dialog bounds but overlapped this painted footer.
Layout-4 is installed from `target/playerbots-bar-layout4-final`, replacing only the root
preset with the stock `v5_dialog` skin, whose bottom artwork has no separator.
The native bridge, commands, dimensions, movement and icon behavior are retained.
`verify_bar_skin.py` checks both the base and active English skin definitions
and actual client DDS row contrast, reproducing the old divider and rejecting
its reintroduction. Native geometry, real Lua controls and signature/preservation
checks pass. Installed with Aion closed; receipt:
`TransmogMenu-backups/playerbot-bar-20261004-103350-467`.
Only the bar XML changed inside the addon; native DLLs, every other addon entry,
saved anchor and latest server baseline are preserved. Actual corrected rendering
remains pending the user's in-game test.

```powershell
python client-mods/playerbots/verify_bar_skin.py --client '<CLIENT_ROOT>'
```

## Lifecycle and persistence

### Owner-centered following formations (4 October)

The expanded native bar now has a third, evenly spaced row: **Circle, Box,
Line, Spread**. Each button is 90x28 with an eight-pixel gap, at content y=86;
the divider-free expanded frame is 400x168. Compact commands and the movable
hidden icon retain their dimensions and anchor. Client receipt:
`TransmogMenu-backups/playerbot-bar-20261004-124722-673`.

Circle is the default 3.8m ring around the owner. Box uses a square perimeter,
Line places companions on both sides with an odd companion covering the front,
and Spread uses a wider 6m ring. Slots use sorted native character IDs, keeping
positions stable while party membership is unchanged. Destinations follow the
owner's current position and altitude without the previous forward speed offset.
Heading damping, collision/path checks, flight, follow-speed packets and combat
strategies remain installed. Following bots regroup after combat; explicit Stay,
Guard and quest missions retain their behavior. Formation selection itself does
not recruit, summon or overwrite existing orders, gear, levels or builds.

`.bot formation circle|box|line|spread` saves an owner-character/account-checked
preference in `config/playerbots/formation-character-*.properties`. The buttons
send precisely those four commands through the native allowlist. Server receipt:
`backups/playerbots-recruitment-20261004-124615-036768`.
The live update preloaded the existing cumulative override and changed only
`PlayerBotFormation.destination` plus the native `.bot` constructor/execute,
adding `PlayerBotFormationLayout`. The actual deployed command source rebuilt
identically before insertion, preserving newer installed command behavior.

Validation: 1,200 spatial/translation/altitude/persistence checks, 84 real client
geometry/hit checks at four scales, actual Lua visibility/anchor controls, ten
native command routes, three addon signatures and native icon/Wardrobe/Journey
regressions pass. Read-only live bytecode capture confirms both effective methods
invoke the helper; 60 actual loaded destination checks pass. Records:
`target/playerbots-centered-formations-runtime/verification.txt` and client receipt
`verification.json`. No humans or companions were online during server install.
Actual in-game formation movement, geodata, button rendering and combat regrouping
remain pending the user's gameplay test.

```powershell
java -cp 'target/playerbots-centered-formations-classes;target-deploy/game-server/libs/playerbot-recruitment-fix.jar;target-deploy/game-server/libs/*' com.aionemu.gameserver.services.playerbot.PlayerBotFormationLayoutCheck
```

`PlayerBotLease` prevents two live copies of a character. Human login, deletion and recruitment share that reservation. Gameplay presence (`isPlaying`) is separate from network online state, allowing party XP, healing, group updates and loot participation without pretending a bot has a socket.

Recruitment loads through `PlayerService`, verifies that saved skills/quests/private inventory, active cooldowns and still-active saved effects actually loaded, strictly reloads life stats, reconstructs equipped/linked stigma skills through the normal login service, and preserves the character's original home for a later human login. Shared account, legion and market warehouses are excluded from bot writes. Dismissal stops movement/casts, releases summons and removes the bot from its party/world before saving and releasing the lease. A failed save keeps the character reserved and retries; process termination during an unresolved save can still lose progress since the last successful checkpoint. Shutdown reports unresolved failures.

The dedicated roster uses `playerbot_roster` only as ownership/visibility metadata; existing Aion character tables hold progress. Pending rows are inserted before creating the character so partial creation cannot leak into character selection. Normal character counts and faction counts exclude both ready and pending dedicated entries. ID allocation includes orphaned pending reservations. Missing-table fallback is permitted only for a confirmed missing table; other database errors refuse roster visibility changes.

Local obstacle routing searches at most 128 nodes within a 24m grid radius, with a 6ms deadline and a shared quota of one search per 100ms. Searches use native ground/obstacle probes and never teleport out of blocked terrain. These bounds constrain work but are not a guarantee of a route around every obstacle or a measured live-server performance result. A movement exception removes the affected controller from the shared movement task and dismisses the player companion or releases its summon.

## Build and offline verification

From the repository root, with the same JDK 25 used by the project:

```powershell
& game-server/tools/check-playerbots.ps1
# Optional dependency/JDK locations:
& game-server/tools/check-playerbots.ps1 -LibraryDirectory 'C:\path\to\server\libs' -JdkDirectory 'C:\path\to\jdk-25'
```

The verifier checks the pinned upstream file hashes, compiles all game-server and commons Java source plus the dynamically loaded `.bot` command, then runs deterministic engine/policy/lease/preferences/tactics checks, charged-release/offensive-ranking/dialog-isolation/native-objective scenarios, encounter shape/path/protection/priority scenarios, class preparation/threat/coordination/mission/ticket policies, real skill/charge/pet/item metadata coverage, and actual local HTTP/media checks. It prints `FAIL` and exits nonzero on failure, or `OK` after all checks. Compilation uses existing dependency jars; it does not replace deployed jars, modify the database or start the server. This route also permits verification when the repository's Maven plugin dependencies are unavailable offline. Quest and encounter scenarios verify planning and native metadata; actual quest rewards, geometry movement and dungeon completion still require a live database/server/client test.

Upstream source and attribution are tracked in `third-party/playerbots`, pinned to `037c01418b5d01506917a3db9b44fd56ac5f965c`. Engine and healing/assist priorities are adapted from that source. Its WoW C++ APIs, spell IDs and schemas are not directly linkable into the Java Aion emulator. Preserve the shipped GPL license/attribution when distributing the port.

## Installed runtime and remaining live acceptance

Party catch-up/object/turn-in update installed live on 4 October, receipt
`backups/playerbots-recruitment-20261004-050635-190915`: 19 reviewed methods in
11 existing classes, with every unselected installed method preserved. One human
and four existing companions retained their sessions and settings; production
panel serialization passed before/after. Auto followers catch up beyond 60m, or
18m when the owner starts combat and the bot itself is unengaged. Manual Stay/
Guard orders and actual bot combat remain respected. Bounded out-of-combat speed
assistance is an Aion adaptation; upstream's old group-speed snippet is commented
out and was not presented as active WoW behavior.

Quest objects use native `quest_use_item` AI, its action timer and per-member
loot rights, then loot only needed quest drops. Decorative Weapons (1136) uses
object 700116, item 182200511 (five), and turn-in NPC 203100; the journal now
reports collection counts/source coordinates. Successful human native turn-ins
are witnessed for bots already holding the quest, allowing party-sync preparation
and native NPC reward selection by class/equipment profile. NPC witnesses persist
for active rewards. Nearby quest combat defaults On with a per-bot persisted
toggle, a 25m owner range, one party target, tank-first pulls and conservative
12m pack clearance. Existing area-skill restrictions remain. Unsupported custom
reward dialogs and mismatched dual extended reward choices remain visible limits.
Compilation/existing regressions, 52 new native policy/real-quest checks and 21
native-browser fixture actions passed. Actual in-game object/pull/turn-in and
catch-up acceptance remains pending; no test altered a live character's quests.

The latest equipment expansion is installed live and persisted in the cumulative
override, receipt `backups/playerbots-recruitment-20261004-032705-060049`.
It updated ten reviewed methods in seven existing classes and added eleven gear
policy helper class entries. Every old override entry and unselected method was
retained, including the latest creation/starting-level repair. The base JAR,
launcher, client and deployed HTML/CSS/JS were unchanged. Full source compilation,
existing regressions and 116 new native equipment checks passed. The actual
Awesomium browser exercised 19 authenticated fixture actions across desktop and
mobile sizes, including saving equipment choices. Live read-only checks verified
the equipment adapter, default settings, all helper types, normal-player command
registration and production panel serialization; actual HTTP media hashes match.
There were zero connected humans/companions during installation and verification.
These checks establish installed linkage, not a live character's gear progression.

Equipment acceptance must cover useful loot rolls under native party rules,
automatic identification/binding, both hands and class mastery, starter history
across dismissal/re-login, generated quality/level caps and native gear upgrades,
shop spending/reserves with purchases enabled, and quest reward selection. The
profile weights are an adaptation of the pinned WoW factory/config logic, not a
complete best-in-slot optimizer or an autonomous dungeon gear farming planner.

The 4 October companion expansion is also installed live and persisted in the
cumulative first-classpath override. Main receipt:
`backups/playerbots-recruitment-20261004-022029-970392`; native repeat-quest follow-up:
`backups/playerbots-recruitment-20261004-022932-203190`. These added flight/gliding,
teleport/summon controls, shared quests, owner completion synchronization, catch-up
consent, native NPC turn-in, journal/tracker and opt-in equipment care. The base
server JAR, launcher order and client mods were retained. Source compilation and
2,782 checks passed for that expansion; live attachment verified production panel
serialization, with zero companions active at installation. Those checks did not
establish actual game flight, quest or enchantment acceptance.

The current implementation is installed in `target-deploy/game-server` and the English Aion client. The companion ownership table was applied and verified as InnoDB without modifying character/inventory rows. GameServer shut down through its native save path after confirming zero online characters and restarted successfully as PID 14208, listening on port 7777 and connected to LoginServer and ChatServer. The existing HTTP listener on port 8091 serves hash-matching companion HTML/CSS/JS and rejects anonymous state/action requests with HTTP 403. A read-only agent verified that companion configuration is registered/enabled, generated companions are enabled, the service/roster are available, and `.bot` is registered at access level 0.

The incremental server package replaced only the compiled companion cohort and preserved 3,249 unrelated original JAR entries. The original deployed projectile timing method was retained exactly; source arithmetic changes unrelated to the companion guards were not installed. The handler, access registration, configuration, schema file and production media were installed together. The client received six files; all three addon signatures, the stock model key, native menu archive entry preservation and the two permitted browser DLL ranges passed installation checks.

Backups and verification receipts:

- Server: `target-deploy/game-server/backups/playerbots-20261004-000440-258` (`manifest.json`, `installed.json`, `verification.json`, `runtime-verification.txt`).
- Client: `<CLIENT_ROOT>/TransmogMenu-backups/playerbots-20261004-000109-958` (`manifest.json`, `installed.json`, original replacement files).
- Server staging/method review: `target/playerbots-install-server`; normal shutdown/startup diagnostics: `target/playerbots-deployment-agent`.

```powershell
python client-mods/playerbots/verify_installation.py --client-backup '<CLIENT_ROOT>/TransmogMenu-backups/playerbots-20261004-000109-958' --server-backup target-deploy/game-server/backups/playerbots-20261004-000440-258
```

The initial six-file client install omitted rebasing graphics/cursor recovery,
causing the launcher to reject the newer companion DLL. On 4 October this was
repaired from its verified receipt without changing live binaries or addon
archives. All launcher preparation checks now pass. Future preparation stages
sixteen files including matching graphics package/tracking and both restore
baselines; isolated install/failure/restore checks passed. Details and the full
preservation inventory are in [INSTALLED_MODS.md](INSTALLED_MODS.md).

Open Additional Functions → Player Companions, or type `/companions`. `.bot help` remains available independently of the browser. A signed-in owned-character recruitment is now confirmed by the existing live companion; progression/combat and combined menu/pet visual behavior still need acceptance in the game. Installation/runtime startup does not establish that the full Playerbots system is complete.

On 4 October the user explicitly removed PvP/location blockers. A read-only
diagnostic confirmed the defect: even Sanctum's native map template carries the
PVP flag, and the old `inPvp` predicate rejected it as active PvP. Recruitment now
uses the native owner's combat timer. Automatic PvP/flight dismissal and PvP
support/transfer vetoes were removed, as were the instance map allowlist gates.
The legacy instance configuration fields remain for compatibility and no longer
gate entry/following. Owners enter through native admission; companion instance
script behavior and flight navigation still need live acceptance.

The six-method update was installed live without disconnecting the connected
human. The real owner was outside combat on a PvP-capable Eltnen map and passed
the recruitment guard after installation. No recruitment or character grant was
performed by the verifier. Full source compilation and 1,079 offline checks
passed, including five direct recruitment-guard checks. Persistent startup loads
`libs/playerbot-recruitment-fix.jar` before the unchanged deployed base JAR in
`start.bat`. Backup/receipt:
`target-deploy/game-server/backups/playerbots-recruitment-20261004-004849-747604`.
Future deployment must preserve this override or fold its reviewed methods
into the base JAR and retire the override/classpath entry together.

The panel subsequently failed with `slotIdMask cannot be 0` after the user's
recruitment. Read-only inspection confirmed Queenbabe was already spawned and
registered for Babe, with twenty ordinary inventory items. The inventory mapper
was decoding zero equipment-slot masks for those non-equipment items. It now
emits an empty `slots` list while preserving those items and their quantities;
equipment continues to expose its native valid slot choices.

This one-method repair was added to the effective two-class override and applied
live without a restart or disconnect. All six recruitment-policy methods and
the complete service class were preserved. The existing companion's complete
production panel snapshot and JSON response serialization now succeed (one
active companion, two roster entries). Full source compilation and 1,382 checks
passed, including 303 inventory regression checks across every native item group.
Original repair receipt: `target-deploy/game-server/backups/playerbots-recruitment-20261004-010232-348029`.
The base JAR, client, launcher and settings remain byte-identical. Click Refresh
in the existing panel; recruiting the same companion again is unnecessary.

Live acceptance must cover owned-character login/recruit/delete races; both generated modes and character-slot counts; all classes' actual casting, Aethertech setup and Spiritmaster orders; movement around corners/slopes; party heals/kill credit/loot under every party rule; quests and re-login progression; consumable quantities/cooldowns; deaths/resurrection; owner disconnect/transfer; save failure/retry/restart; and each enabled dungeon's entry/exit, scripts and boss mechanics. Native client visibility and null-connection paths must be exercised before declaring the runtime production-ready.
