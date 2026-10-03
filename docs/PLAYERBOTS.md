# Aion player companions

The requested destination is a full Playerbots-style companion system, with owned offline characters first, both generated recruitment models, and PvE first. This document distinguishes implemented server behavior from the substantial work still required for full parity. The current implementation is **not yet a complete replacement for AzerothCore Playerbots**.

## Recruitment and commands

Companions are real persisted Aion `Player` objects. They use normal character stats, learned skills, equipment, cooldowns, progression, movement speed, skill conditions and native player/party packets. They have no fabricated network connection and do not inherit the recruiting account's staff privileges.

```text
.bot list
.bot add MyOfflineCleric
.bot create NewPriest priest
.bot generate MyTank templar
.bot role MyTank tank
.bot follow all
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
.bot mission MyOfflineCleric 1234
.bot mission MyOfflineCleric 0
.bot dismiss all
```

`add` recruits an eligible offline character from the player's own account. `create` creates a normal level-1 starting-class character, uses an ordinary account character slot and recruits it if the level-gap rule permits. Otherwise the character remains playable normally.

`generate` creates a persistent companion in a separate roster, matched to the owner's level **at creation**. Below level 10, choose a starting class; at level 10 or above, choose an advanced class. Class command names follow Aion's internal names: `spirit_master`, `rider` (Aethertech), `gunner`, `bard`, etc. Generated advanced characters receive compatible common-quality bound starter equipment. They learn normal automatic skills; they do not receive every stigma or unavailable skill. They earn progression after creation instead of repeatedly resetting to the owner's level.

Recruitment requires the same account/faction, an offline non-banned/non-deleting character, no prison, and the configured level difference (default 10). Dead companions can be recruited as corpses and wait for a normal resurrection; recruitment does not refill their health. The recruiting player must be alive, grounded and outside PvP, and lead a normal party with a free slot. Defaults allow five companions per owner and 100 active companions server-wide. Separate generated rosters default to 20 per account. Failed generated creations remain pending and hidden; their IDs remain reserved across server restarts.

`stay` prevents voluntary movement while allowing skills in reach; `guard` keeps a position and uses a 20m guard leash; `passive` cancels attacks/casts and follows without casting or attacking. AoE, automatic gear changes, corpse collection and quest automation default off. Recovery consumables default on. Role/AoE/supplies/gear/loot/questing preferences persist per character under `config/playerbots` with account ownership checks. Older settings retain their values and default quest automation off.

`questing on` enables nearby ordinary quest actions while following a stationary owner. Acceptance is limited to quests the owner already has in progress. Companions approach registered quest NPCs within 15m of the owner, request native dialogs, and submit acceptance/rewards only after the matching handler emits the expected page. Ordinary reward choices use class compatibility and conservative equipment scoring, preferring the current weapon style. Extended reward lists, campaigns, class choices and unsupported custom dialogs remain under human control. Failed interactions wait 30 seconds before retrying that NPC/quest.

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
| Navigation | Owner breadcrumbs, collision-tested short steps, bounded local A* obstacle routing, ranged spacing/kiting, same-map assigned quest routes using native spawns, ground travel and safe owner map transfer | World path search/navmesh, robust obstacle recovery, formations, portals, doors, transports, flight and travel planning |
| Encounters | Protect sleep/fear/paralysis; coordinate interrupts; avoid visible circle/donut/cone/line casts; supported oil/blade/ice/firestorm and Drakenspire wave profiles; Vasharti reflective-shield protection and matching-flame approach; targeted area spreading, tank facing and hazard-aware companion/summon movement | Additional persistent emitters and boss mechanics, coordinated add assignments, phase strategies and complete dungeon/raid strategies |
| Quests | Native quest sharing; opt-in nearby acceptance of the owner's ordinary quests and companion turn-in through native dialogs; conservative ordinary reward selection; local normal-mob objective targeting using native MonsterHunt counters; explicit same-map hunt/turn-in assignments; party kill credit/progression, inspection and persistence | Independent/world quest selection and travel; campaigns/custom story dialogs; extended reward selections; escort/gather/use-item objectives and route planning |
| Inventory/loot | Normal party loot rights, equal Kinah distribution, opt-in corpse collection, automatic pass on rolls/bids, inventory inspection and manual equipment, conservative opt-in gear upgrades | Owner-controlled need/greed rules, trading, shopping/selling/repair, advanced gear/set/socket scoring, crafting and gathering |
| Persistence | Inventory and private progression in one SQL transaction; life stats, XP/DP, quests, learned skills, cooldowns, effects, NPC factions and rank changes; periodic/dismiss/logout/shutdown saves; retry reservation | Fault-injected database/live restart testing and recovery tooling |
| Client | Native character rendering, movement/skill effects, group panels/chat commands; dedicated authenticated companion browser panel; roster, both creation models, roles/orders/settings, equipment, quest sharing/missions; incremental Additional Functions and `/companions` package staged and checked | Actual in-game menu/pet/signing acceptance, native item tooltips/artwork, richer inspection and live state refresh |
| PvP/large groups | PvE-only target guards, late-hit revalidation, dismiss on detected PvP/flight or loss of the owner's party | PvP, battleground/arena strategies, alliances and raids |

Skill coverage checks enumerate real templates and faction/class skill trees; recognizing a template does **not** prove an optimized rotation or correct live use of every skill. Equipment scoring is a conservative heuristic and does not yet account for every socket, set bonus or conditional modifier. Generic cast avoidance is not a complete encounter strategy.

Hazard planning reads visible NPC casts and native area properties, including directional cones/lines, inner safe pockets and altitude. Persistent profiles cover `spilled_oil` (19658), `bladestorm` (20748), `malicious_ice_storm` (21180), Tahabata firestorm (20759; NPC 283102 uses 20753), and the native Drakenspire large 29m half-wave and small 22m spatial wave scripts. Scripted waves only affect player planning because their native scripts target players. Movement samples each proposed segment and rejects entering a field or increasing penetration into any existing field. Escape candidates are bounded to 96 directions/distances within 16m; native geometry still determines whether a step is possible. FOLLOW companions may cancel offensive casts to react; support casts finish, emergency recovery outranks generic avoidance, and blocked escape falls through to another valid action. Targeted area casts trigger local separation from nearby allies; tanks seek an anchor facing their target away from nearby non-tanks. STAY companions retain their position. These are conservative local policies, not a complete encounter strategy, predictive damage model or navigation mesh.

For native `brigade_general_vasharti` shields 20530/20531, new attacks require the matching 20535/20536 protection with enough remaining duration for the intended cast. Following companions approach a visible matching `dancing_flame`, wait for its normal pulse and avoid the opposite flame's resistance penalty. Summons check their own protection; AoE planning and charged releases recheck the shield before committing. Protection is never granted artificially, and already launched attacks retain native damage/reflection behavior. These source policies remain unverified in a live Rentus Base encounter.

The native skill engine remains authoritative. Planning never advances chains, consumes rune effects, reduces dispel power or pays resources. It checks weapon/form/target/position/abnormal conditions, resource/item costs and counter windows before scheduling a cast. Actual casts validate conditions and costs again, and use server motion/hit timing. Charged skills use native release stages and cast-speed thresholds, with animation timing for the actual released stage. Ground casts use native coordinate packets. Combat targets must be engaged hostile PvE NPCs, an explicitly commanded valid NPC, or a locally selected objective under the opt-in quest policy. Human players and their summons are excluded. AoE candidates reject unengaged or controlled enemies. Projectile spell hits and delayed auto-attacks recheck the current owner's/party/order/target policy at application time, including attacks by a companion's summon.

Automatic buffing retains existing conflicting preparations instead of endlessly replacing them. It respects the native mantra/toggle/chant and Ranger preparation capacities. This is a conservative policy; selecting the best buff combination for each encounter is still required. Assassin bursts wait for usable runes, a nearly defeated target or expiring runes; this is one class mechanic rather than a complete Assassin rotation.

Non-tanks pause new damage when their native hate (including their own spirit) reaches 85% of a living party tank's hate. The pause releases below 70% or after 2.5 seconds, followed by a 1.5-second damage interval. Healing, defense and interrupts remain available. Solo/no-tank fights and targets below 10% HP bypass it. Already committed hits retain native behavior. This limits aggro competition without claiming predictive threat control.

## Dedicated companion panel

The production page is `/market/companions`, served by the existing market HTTP listener. It reads actual `PlayerBotSession`, account roster, private inventory and quest state. Mutations call the same authoritative services as `.bot`. The native client opens it through Additional Functions → Player Companions or `/companions` after installing the staged menu. The panel uses English labels, responsive legacy WebKit styles and manual Refresh; no demonstration account data is included in production media.

State access requires the logged-in human character's native web token. Actions require POST plus a short-lived, single-use ticket bound to that exact player and client connection; refresh replaces that player's previous ticket. Duplicate fields, oversized bodies, expired/replayed tickets and another supplied origin are rejected. An interrupted/uncertain submission clears the UI ticket and requires Refresh before another mutation. Production HTTP route checks exercise actual media and unauthenticated refusals; signed-in mutations against a real game/database remain live acceptance work.

`client-mods/playerbots/prepare.py` stages six files in a new directory outside the installed client. It reads the current installed DLL/menu baseline and rejects an unknown/newer browser implementation. Only the existing browser/authentication caves and `PrivateMenus.lua` entry change; other DLL bytes and every other archive entry are preserved. The three addon packages are re-signed together, and the stock `Pub.key` is preserved and omitted from installation payloads. The current verified staging directory is `target/playerbots-client-verified`; **it has not been installed**. Recheck it against current installed hashes before any future installation:

```powershell
python client-mods/playerbots/verify_package.py --client '<CLIENT_ROOT>' --staged target/playerbots-client-verified
python client-mods/transmog-menu/tests/verify_market_browser.py --companions
python client-mods/playerbots/verify_browser.py --browser-bin '<CLIENT_ROOT>/bin64'
```

The native instruction verifier executes the generated x64 hooks in an isolated process, including exact URL matching, token handoff, missing-token fallback and old-route preservation. The browser verifier loads production HTML/CSS/JS in the installed Awesomium DLL offscreen with a clearly isolated HTTP fixture and exercises ten controls at 1280×900 and 390×844. Screenshots are under `output/playwright/playerbots`. This validates native browser compatibility and request wiring, not actual Aion gameplay.

## Lifecycle and persistence

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

## Deployment and live acceptance still required

The current work changes source/configuration and creates separate staging/QA artifacts. It has not deployed the compiled server, installed the companion menu, applied SQL, restarted the live server or verified gameplay in the Aion client.

Build/package the server and deploy the resulting server classes, the `.bot` handler, command registration, `config/main/playerbots.properties` and `config/playerbots/media` together. Apply `game-server/sql/playerbots.sql` before starting the new server for the separate generated roster. The optional-schema result is cached per process, so installing the table requires a restart. Ordinary offline recruitment and normal character creation work without this extra table.

Instance following defaults off. Enabling it also requires explicit map IDs and an instance registered to the owner's party. This configuration gate does not prove portal prerequisites, cooldown admission or script compatibility. Keep maps gated until their headless-player and encounter paths have been tested.

Live acceptance must cover owned-character login/recruit/delete races; both generated modes and character-slot counts; all classes' actual casting, Aethertech setup and Spiritmaster orders; movement around corners/slopes; party heals/kill credit/loot under every party rule; quests and re-login progression; consumable quantities/cooldowns; deaths/resurrection; owner disconnect/transfer; save failure/retry/restart; and each enabled dungeon's entry/exit, scripts and boss mechanics. Native client visibility and null-connection paths must be exercised before declaring the runtime production-ready.
