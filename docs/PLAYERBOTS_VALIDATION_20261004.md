# Companion update validation — 4 October 2026

Current deployed cumulative override receipt:
`backups/playerbots-recruitment-20261004-183335-252998`.
This retains the later item custody, camp, supplies/recovery, revival and roster
archive work, and adds offensive decisions, target values and local quest routes.
The override remains first on the launcher classpath. The base server JAR,
launcher and client files were preserved. GameServer remains stopped at the user's
explicit request. The latest update is installed on disk; no native attach or
restart was performed. Earlier native tests below are historical isolated
evidence. Tests do not establish actual game acceptance.

## Installed areas and evidence

| Area | Behavioral evidence | Log |
| --- | --- | --- |
| Existing work: catch-up, object loot, owner completion, nearby quest combat | 52 cases, including real Decorative Weapons native objective/consumption and isolated pull policy | `target/playerbots-final-validation.txt` |
| Continuous follow and formation | 314 movement-clock/slot/trajectory cases: no stop ticks in measured follow trajectories; max gap about 5.6m | `target/playerbots-final-validation.txt` |
| Server/client follow speed | Six native packet equality and human/combat/stopped/expiry restoration checks | `target/playerbots-final-validation.txt` |
| Whole-party dungeon transfer | Five actual native world actors transferred between Dark Poeta instance fixtures, including an already-despawned member; owner combat did not strand the group | `target/playerbots-transfer-native-runtime-v3.txt` |
| Intermediate NPC conversations | 18 actual native handler transitions, next-NPC selection, repeat guards and final reward separation | `target/playerbots-final-validation.txt` |
| Tank threat | Eight real HostileUp/hate checks, opening priority, lost-aggro priority and negative-threat protection | `target/playerbots-final-validation.txt` |
| Class armor/starter construction | 56 actual native creation/equip cases across classes and factions | `target/playerbots-armor-native-runtime-v3.txt` |
| Temporary builds and tier progression | 210 native class/faction/level cases, actual equipment/skills/Stigma effects and meaningful 20/30/40/50/60 tier improvements; 12 literal native role-weight checks | `target/playerbots-temporary-native-runtime-v8.txt` |
| Owned-alt isolation | 34 native fingerprints unchanged: level, class, equipment, skills and Stigmas, plus automatic management rejection boundaries | `target/playerbots-temporary-native-runtime-v8.txt`, final suite |
| Actual maintenance tick | Separate fixture exercises owner 20/24/30/31/40/50/60/65, promotion, skill learning between tiers, Stigmas, HP fraction, no between-tier gear churn and isolated provenance persistence | `target/playerbots-level-native-runtime-v2.txt` |
| Management UI | 20 authenticated native Awesomium fixture actions, desktop/compact layouts, five bots/103 roster entries, Temporary naming and disabled owned-alt automation | `target/playerbots-temporary-browser-check-v5.txt` |
| Focused upstream correction | Five production Ripclaw ranks and two native support conditions, including invalid first target and no planning side effects | `target/playerbots-port-native-runtime-v3.txt` |
| Saved bots and mixed presets | 22 persistence/ownership/concurrent-write checks; real native DB creation → checkpoint → dismiss → re-recruit preserves same IDs, Temporary gear/skills/Kinah/quest progress and owned-alt setup | `target/playerbots-presets-full-validation.txt`, `target/playerbots-presets-native-runtime-v4.txt` |
| Preset controls in native browser | 25 authenticated actions, saved filter and mixed member cards, save/load/remove, desktop and compact visual inspection | `target/playerbots-presets-browser.txt` |
| Role target values | 18 behavioral comparisons; installed session tested with native NPCs, real hate/victims/health/range, other tank protection and direct command | `target/playerbots-target-values-full-validation.txt`, `target/playerbots-target-values-native-runtime-v2.txt` |
| Group/pet healing | 30 comparisons; native party/pet, radius/flight eligibility and emergency reservations; actual Healing Wind healed two members, consumed mana and reserved both | `target/playerbots-healing-custody-full-validation.txt`, `target/playerbots-healing-native-runtime-v11.txt` |
| Class purges and interrupts | 26 production/category/level/priority checks; actual Ignite Aether removal/damage/MP and Sigil of Silence cancellation of a native enemy heal; engaged secondary targets, reservations and exact Mage/Rogue interrupt bands | `target/playerbots-enemy-utility-full-validation.txt`, `target/playerbots-enemy-utility-native-runtime-v4.txt` |
| Legal combat resurrection | Native learned spell selectable during combat; CombatCheck-restricted spell rejected. Selection test, not an actual resurrection cast | `target/playerbots-healing-native-runtime-v11.txt` |
| Inventory custody/recovery | Rollback-only foreign-item update rejected; Tanku recovered with identical armour attributes/provenance, native inventory/progress commit and unchanged conflicting row; normal dismissal cleared held session | `backups/playerbots-inventory-collision-20261004-1032/runtime-verification.txt`, `target/playerbots-inventory-save-inspect-v4.txt` |
| Saved-party regression/fixture cleanup | Native mixed save/dismiss/resummon and exact alt preservation; private inventory deletion verified before ID release, shared storage excluded | `target/playerbots-presets-native-runtime-v5.txt` |
| Offensive resources/periodic refresh | 19 decisions, 954 production-template checks, four native zero-rune family checks and coverage assertion; actual casts pending | `target/playerbots-strategy-effective-validation-v5.txt` |
| Caster/combo target continuation | 19 interval/command/rune-target comparisons against effective package; actual session combat pending | `target/playerbots-strategy-effective-validation-v5.txt` |
| Local quest destinations | 17 coordinated destination, priority, toggle and leash comparisons; actual geodata/quest actions pending | `target/playerbots-strategy-effective-validation-v5.txt` |
| Kromede delayed trap anticipation | Actual hazard adapter tested before casting with observed world-free NPCs; native shape, crossing/retreat and destroyed/instance exclusions pass; actual encounter pending | `target/playerbots-strategy-effective-validation-v5.txt` |
| Source integration | Full Java server/commons/command compilation and all companion/HTTP regression checks pass | `target/playerbots-strategies-final-source-validation.txt` |
| Prior mods | 15 preservation checks pass; 31 current client hashes, 70 client and 69 historical server receipts inventoried | `docs/INSTALLED_MODS.json` |

Native build/maintenance fixtures are unsaved, unspawned characters. Transfer
fixtures create isolated native world actors and remove them afterward. No human
character/account/quest/gear data is changed. Maintenance fixture provenance files
are uniquely named and removed afterward. Only its own fixture cadence is advanced;
live bot caches are not reset.

The actual maintenance fixture passed all 16 ticks across both factions, including
no gear replacement at 24/31/65 and skill learning between tier thresholds.

## Receipt chain

Receipts are relative to `target-deploy/game-server`, not the repository root.

| Change | Receipt suffix (all under `backups/playerbots-recruitment-20261004-`) |
| --- | --- |
| Catch-up/object quests/owner completion/toggle | `050635-190915` |
| Continuous formation movement | `052350-503188` |
| Full-party instance repair | `053208-432161` |
| Intermediate conversations | `054436-158309` |
| Tank threat | `055642-652635` |
| Armor/build starter and native starting rank repair | `060252-618747`, `060554-806153` |
| Temporary builds and maintenance | `062149-199650` |
| Native cube template filter | `062738-130389` |
| Native identification and effective runtime helper repair | `063222-757813`, `063705-592823` |
| Matching native follow speed packets | `064226-912374` |
| Native stat weights and maintenance | `065038-460000` |
| Canonical weapons and recruitment/Stigma initialization | `070135-716404` |
| Focused upstream port corrections | `072922-921933` |
| Saved Temporary Bots and mixed party presets | `074432-111599` |
| Continued native role target values | `080311-450862` |
| Native group/pet healing and legal combat resurrection selection | `100323-698137` |
| Companion inventory custody, retaining concurrent AI-reload/Kromede work | `103105-414348` |
| Native class purge and engaged secondary interrupt decisions | `114208-193698` |
| Exact pinned class interrupt bands, including loaded helper redefinition | `115019-456681` |
| Proactive defenses, native resistance and close-pursuer root/snare decisions | `123520-131583` |
| Concurrent later formation/command update retaining defensive classes | `124615-036768` |
| Territory target guards, legacy camps and persisted item-ID guard | `130544-238823`, `132704-143251`, `133452-341528` |
| Supplies/wipe recovery and corpse/rebirth timing | `134823-288459`, `135149-590713`, `140017-509159`, `140949-271624` |
| Temporary roster archive/removal installed stopped | `142907-454064` |
| Offensive decisions, caster/combo targets, local quest routes and delayed traps installed stopped | `181655-178651` |
| Native zero-rune fallback correction, preserving existing enum switch class | `182147-699768` |
| Native intermediate conversation radius compatibility | `183335-252998` |

Installers compare the latest actual JAR and transplant bounded methods, preserve
unselected entries/methods and preload original classes before ZIP replacement.
Existing helpers require explicit runtime method scopes: writing their disk entry
alone does not redefine an already-loaded class. Cached enum-switch mappings also
survive redefinition; equipment scoring now uses stat-name dispatch. Native tests
detected these runtime differences and the final receipt repairs them.

Automatic approval review rejected a proposed live cache reset. That step was
removed; the code correction and isolated native verification proceeded without
resetting active companions. No rejected live mutation was performed.

## Saved-party fixture boundary

The preset fixture used account ID 2000000000 only after confirming it had no
characters/roster, saved-party settings or world presence. It created its own
native test characters, exercised real DB save/load and removed its exact rows,
world actors, leases, item IDs and settings afterward. No human accounts changed.
An unavailable alt rejects the whole preset before recruitment; repeated loading
is idempotent; native active combat rejects recruitment; preset removal keeps saved
progress. The alt's configured class/level/gear/skills fingerprint and role/Stay
order survived. Native fixtures are separate from a human's client playthrough.

One proposed retry reset fixture ownership through reflection and was rejected by
automatic approval review. It was not run. The successful fixture removed the reset
and used native fresh-character ownership rules throughout.

## Remaining acceptance and full-system work

Defensive continuation: 34 production-template/class/health/role comparisons
passed in `target/playerbots-defense-final-validation.txt`. Installed native casts
passed in `target/playerbots-defense-native-runtime-v5.txt`: Stone Skin shield
absorption and MP, Root immobilization, bow-based Entangling Shot snare/MP/control
reservations, and Spelldodging resisting two magical hits and consuming both native
charges. Planning preserved resources and owned-alt setup fingerprints. These
were isolated unsaved actors, with no DB/human writes; real-client acceptance and
broader defensive/escape strategies remain unfinished.

Reopen the companion window and verify continuous motion while running/flying,
corners and real geodata; five-bot dungeon entry/return; native quest object loot,
intermediate dialogue and reward turn-in; tank recovery during an actual pack;
Temporary creation/recruitment/dismissal/re-login; owner-level/tier maintenance;
owned-alt equipment/skills/Stigmas retained; saved bot/preset controls through a real
client login, logout and reload; and stock pet/menu coexistence.

Custom branching quest scripts without a verified owner action still require
guidance. The full requested Playerbots system remains unfinished: optimized class
rotations, autonomous world travel/quest planning, broader boss mechanics, dedicated
PvP AI and alliance topology have not received full implementation/gameplay acceptance.
Pinned WoW C++ references guide Java adaptations; Aion native APIs remain authoritative.

## Kaidan Watchpost territory-flag target repair

Baby reported an invisible/untargetable mob at Kaidan Watchpost. Read-only native
inspection identified all four companions targeting Asmodian Territory, template
701800/object 191099, with Tanku hate 3,089,340 while its HP stayed 23,691. Its
position (1811.875, 1101.75, 435.2568) matches terrain height 435.2578; FlagNpcAI
returns zero for all damage. The live target was a base-control flag.

Two bounded transactions installed native isFlag exclusion in validEnemy and
allowsTarget. Receipts: playerbots-recruitment-20261004-130336-672982 and cumulative
playerbots-recruitment-20261004-130544-238823. Agents 30/31 preloaded every original
override class, retained hashes/rollback, and changed only the named methods.
Existing companion preferences, characters, group, panel, inventory and quests
remained live; client files, base server JAR, media and launcher were retained.

EltnenFlagTargetCheckAgent3 passed 28 effective live checks across both native
Eltnen flag types and four companions: automatic/explicit selection rejects flags,
effect-time eligibility rejects flags even after faction changes, and a nearby
real enemy remains eligible. Baby had moved away before final validation; all four
companions were observed targeting Raging Kraterr (211715/object 21606). The agent
removed only this party's stale flag threat/targets where present; it made no DB,
item, quest or flag-ownership changes. Output: target/eltnen-underground/live-flag-check3.txt.
All 15 refreshed installed-mod checks pass (70 client/56 server receipts).
Client visual confirmation and broader Playerbots gameplay remain unfinished.


## Companion corpse/rebirth visual repair

Installed cumulative baseline is `playerbots-recruitment-20261004-140017-509159`.
Read-only native evidence found Templaru alive/ACTIVE and later casting skill 3024
while the supplied client screenshot showed a corpse. The animation repair delays
automatic native revive acceptance and holds actions through recovery, then refreshes
native living observer state. See `PLAYERBOTS_REVIVAL_20261004.md` for receipts,
passing lifecycle/stance/source checks and precise limits. The first native casting
fixture lacked party targets for Healing Wind; its casting assertion failed.
The corrected Healing Light fixture could not attach after GameServer shut down.
Final casting and real-client animation acceptance remain pending; the server has
not been restarted; the user explicitly requested that it stay stopped.

## Stopped-server strategy continuation

See `PLAYERBOTS_STRATEGY_CONTINUATION_20261004.md` for the pinned original/native
mapping, effective package tests and the current receipt. Local approved quest
destinations are implemented; full autonomous world travel remains unfinished.
The full class/encounter scope remains incomplete in addition to pending runtime
acceptance. Superseded persistence fixture revisions 1/2/4 are fail-closed in source;
historical compiled JARs must not be run. The concrete inventory-release defect
does not prove every earlier collision's root cause. No DB recovery was repeated.
Native revival/supplies/roster and new combat/route/trap checks await startup.

## Upstream / effective-disk reverification — 4 October 2026

Canonical next work and closure criteria:
[PLAYERBOTS_PORT_REVIEW_20261004.md](PLAYERBOTS_PORT_REVIEW_20261004.md#reverification-tracker--4-october-2026).
This is a documentation/audit pass, with no gameplay fix, deployment, attach,
restart, database recovery or client replacement. GameServer stays stopped.

Pinned reference hashes, the current receipt/base/override hashes, classpath
order and selected effective installed methods were checked by
`client-mods/playerbots/audit_port_state.py`. Evidence and method fingerprints
are in `target/playerbots-port-reaudit-20261004/report.json`; scalar fixture
output is `gate-output.txt`, with full inspected bytecode in `*.javap.txt`.
All 51 cached primary-source hashes match the existing pin. This subset does
not prove the uncached upstream repository was exhaustively reviewed.
All 76 selected effective methods, including associated lambdas, match the
compiled source snapshot. Two retained obsolete hunt lambdas are recorded
separately; they are schema remnants, not the active delegated hunt path.

New confirmed integration limits:

- **PB-PORT-001:** installed offense priority and final old class gate disagree
  on zero/no-builder, four-of-five, partial/no-builder and cast-aware expiry.
  Six installed scalar comparisons reproduce four vetoes and two controls.
- **PB-PORT-002:** final DAMAGE same-buff veto masks same-ID/stack final-window
  refresh and hybrid direct attacks accepted by the offense helper. Confirmed
  source/installed control flow; no native actor cast occurred in this audit.
- **PB-PORT-003:** conversations/object jobs/NPC jobs/hunts precede the idle
  quest-route trigger; existing executors do not consume its selected Goal.
  Same-quest preference is not shared stage/destination execution arbitration.

The earlier 978 offense checks, 19 target comparisons and 17 route comparisons
remain valid for their helper/template scope. They **do not close these findings**.
Add final CastAction/tick integration checks, then native and client behavior,
before advancing their acceptance status. Installed-disk helpers are not proof
of end-to-end behavior. Other source-gated partial areas are PB-PORT-004 through
012, with dependencies and original/native evidence in the canonical tracker.

The tracker separately records pending validation as PB-VAL-001 through 008:
native combat/quest behavior, corrected revival revision 2, supplies revision 3,
roster dismissal/removal, real movement/formations, all-bot dungeon transitions,
class/pet/encounter combat and Temporary/preset/alt/client preservation. These
do not authorize starting GameServer. Existing passing native fixtures are
historical evidence for named behavior, not acceptance of the whole system.

Refreshed installed inventory passes all 15 preservation checks (31 client
hashes, 70 client receipts, 69 server receipts). Current cumulative receipt is
`backups/playerbots-recruitment-20261004-183335-252998`. No earlier mod was
replaced by this review. Item-ID release root investigation remains open;
retired historical fixture JARs must not be run.

## PB-PORT-001/002 final-action corrections installed stopped

Current receipt: `backups/playerbots-recruitment-20261004-193448-980138`.
This supersedes the two combat integration failures described in the previous
audit section at source/offline/installed-disk stages. It changes exactly
`PlayerBotOffense.useful` and `PlayerBotSession$CastAction.isUseful`; 114 earlier
entries are byte-identical, with no class/schema/enum changes. The old rune veto
is no longer called; priority and usefulness share the native resource decision.
Periodic DAMAGE actions honor expiry/rank/hybrid usefulness while stronger effects
and nonperiodic duplicates remain protected. Native casting legality and the
existing support/utility/reservation/cooldown/chain/area/animation paths remain.

Passing evidence:

- Full server/commons/command compilation and companion/HTTP suite:
  `target/playerbots-offense-gates-full-source-validation.txt`.
- Effective bounded JAR: 978 existing offense metadata/helper checks and **44
  final CastAction.isUseful/isPossible checks** with native planning:
  `target/playerbots-offense-gates-effective-validation.txt` and the final
  conflict-ID-only regression `target/playerbots-offense-gates-final-integration.txt`.
- The same 44 final-action checks pass against the installed JAR. They include
  the six rune cases, builder suppression, native caps/unrelated stacks,
  same-ID/stack DoT refresh, stronger rank/level, conflict IDs, hybrid/pure DoT
  low-health cases, nonperiodic duplicate protection and native MP/cooldown/
  chain/area/casting/dead/despawned safeguards. Observed world-free fixture
  effects and mana remain unchanged; no DB, world actors or IDs are used.
- `target/playerbots-offense-gates-postinstall.json`: two-receipt chain and
  backups verified; 31 client hashes, 35 settings/preset/archive files, launcher
  and base JAR retained. All 15 mod checks pass (70 client/70 server receipts).
- `target/playerbots-offense-gates-postinstall-audit/report.json`: 51 pinned
  source hashes, 76 effective methods/lambdas match the compiled source snapshot;
  `activeLegacyRuneGate=false`. Legacy helper scalar vetoes remain as historical
  members and are not current final-action behavior.

The baseline integration fixture reproduced both failures before the fix.
One fixture retry added the learned skill records that native Skill construction
requires even for a provoked fixture template; no production skill-engine change
was needed. The final conflict test uses different stacks/effect IDs with the
same conflict ID, so it specifically exercises that native matching path.

The offline install guard now fails on denied process inspection. A successful
process check confirmed no GameServer before replacing the override. No server
startup/attach, client replacement or database recovery occurred. Native casts,
MP/rune consumption, effect application and client party combat remain PB-VAL-001.
At this earlier receipt the next offline implementation was PB-PORT-003; 001/002 were installed/offline verified,
not fully accepted. Broader class/travel/encounter and item-ID investigations stay open.


## Shared quest objective execution — PB-PORT-003

Installed on disk with GameServer kept stopped. Initial receipt:
`backups/playerbots-recruitment-20261004-203357-938918`; final cumulative receipt:
`backups/playerbots-recruitment-20261004-203902-131350`.
Final override SHA-256:
`d912207d266d5b7cedc98a242fc6cdda1b7e2d62504a39df34c4e87cac954f7f`.
Base JAR remains
`31948ed051a7d896ee377f5eaf4dbc7d9f9ba5416067139c8fe4f975c6c2d5b2`.

One committed local objective now gates conversations, objects, actual native
reward-job selection and new hunt initiation. Exact actors/points, each bot's
native quest-variable/status stamp, moving visibility and map validity are
rechecked. Group copies come from active original peers and must satisfy each
follower's own native objective; equal peer variable words are not required.
Cache progress, paused time, arrival and failed native interactions are separate.
Static hints resolve actual actors. Required quest-drop mobs are included and
hunt planning uses the initiation policy's 25m owner range. Native quest handlers,
object tasks/rights, tank-led safe pulling, explicit missions and alt builds survive.

Evidence:

- Full source/commons/command and companion/HTTP suite passed in
  `target/playerbots-objectives-full-source-validation.txt`; the final small
  changes were recompiled and exercised against the cumulative package.
- Initial package: 13 bounded methods, three added helper classes and 110 earlier
  entries retained byte-for-byte. Final correction: only existing helper `peers`,
  118 earlier entries retained. All existing schemas/enum switches survive.
- `target/playerbots-objectives-effective-validation-v4.txt`: **35 production
  planner/executor-gate checks**, 18 actual native ReportToMany handler checks,
  17 route checks and 52 existing party/Decorative Weapons checks passed with
  strict JVM verification. Actual reward-job chooser consumes the selected actor;
  native intermediate progression stays START and advances one objective.
- `target/playerbots-objectives-installed-integration.txt`: the same 35 checks
  passed against the installed override. World-free actors skip constructors:
  no DB, world registration, native casts or ID allocation/release. The isolated
  native reward-NPC registry entry is checked absent beforehand and cleaned up.
- `target/playerbots-objectives-postinstall.json`: three receipts/backups verified;
  31 client hashes, 35 settings/preset/archive files, base/launcher/config/media
  retained. All 15 inventory checks pass; 70 client/72 server receipts recorded.
- `target/playerbots-objectives-postinstall-audit-v4/report.json`: 51 pinned hashes,
  96 effective methods/lambdas match the source snapshot. Eight old unused
  synthetic members remain. `activeLegacyRuneGate=false`.

The schema guard caught a changed captured-lambda descriptor during staging;
code was corrected to retain its original descriptor rather than bypassing the
check. The final peer correction follows upstream native destination eligibility.
All earlier installed mods remain the baseline; failed staging directories are
not installs. Details: `PLAYERBOTS_QUEST_ARBITRATION_20261004.md`.

**PB-VAL-002 remains pending:** actual complete Session.tick behavior, native
object-task/loot timing and cancellation, tank/DPS pull coordination, quest reward
payment and choice, moving NPCs, real geodata, instance transfers, announcements
and client party regrouping. No server restart/attach or live/client acceptance
occurred. PB-PORT-003 is installed/offline verified, not accepted/closed.
Next offline work is PB-PORT-004, strategy composition, expanded multipliers and
continuers. The remaining class, world-travel, dungeon and item-ID investigations
stay unfinished in the canonical tracker.

## Tank front slot and ranged melee-rush correction — installed live

Receipts `...211220-016216` and `...211935-035574` preserve complete `...203902-131350`
cumulative baseline. The server was already running; offline refusal and initial
approval-review rejection made no changes. The user explicitly approved the
verified live update; agents 38/39 preloaded the original override before replacing
and redefining six then one classes. No server restart or client modification.

Full source/companion suite passed; final scalar range refinement was compiled
and 38 position/cast-prerequisite checks passed against the effective package.
The staged main repair also passed 44 offense gates, 35 quest-objective gates and
1,200 formation checks. Final installed audit matches 51 upstream hashes and
115 effective methods/lambdas. Live native read-only inspection confirms Tanku's
first slot and learned ranged policy for all four ranged companions (22 checks).
Both runtime receipts retain five spawned companions/preferences and one human
connection. Forty setting files checked: only two generated-item provenance lists
grew through ordinary gameplay. All 15 inventory checks, 31 client hashes,
base/launcher and 114 earlier entries survive; one-method follow-up retains 120.

PB-VAL-005 actual movement, terrain/LoS, combat spacing and client casting remain
pending. Read-only policy/geometry inspection does not close client acceptance.
Post-revive appearance/casting, full port and item-ID release investigation remain
open. Details and exact evidence: `PLAYERBOTS_POSITION_20261004.md`.


## State strategies and action continuers — PB-PORT-004

Installed live/on disk in `backups/playerbots-recruitment-20261004-213711-193912`,
preserving the separately installed position update `211935-035574` and every
prior mod. The user requested finishing the reviewed update; GameServer remained
running throughout, with no start/stop/restart or client replacement.

The original engine contract now includes named state strategies/defaults,
combined native threat multipliers, evaluation-time weights for all expansions,
bounded continuers and successful movement-parent intentions. Only keys/weights/
expiry survive; fresh native gates resolve actions. Native chain metadata provides
exact successors, with actual asynchronous proc/count/expiry checked next tick.

- Full source/companion/HTTP suite passed; final chain adapter/fixture refinements
  recompiled and checked against the final effective package.
- 49 composition/weighted-expansion/continuation/native-chain cases pass offline
  and against actual loaded helpers. Existing effective engine (99), offense (44),
  intermediate conversation (18), shared quest arbitration (35) and position (38)
  regressions pass. No fixture native casts/world/DB/ID operations.
- Six existing methods in two classes, nine helper classes; 120 earlier cumulative
  entries byte-identical. Agent 40 preloaded every original override class before
  replacing the JAR and retained explicit rollback definitions.
- All five naturally scheduled session contexts observed after install; five
  companions, native session/party identity, roles/orders/preferences and one
  human connection preserved. No real tick/movement/cast was forced.
- Eleven effective loaded definitions captured; 81 methods match the package.
  115 existing port methods match source. All 15 mod checks/31 client hashes,
  base JAR/launcher/config survive; 43 settings files reviewed with only two
  ordinary generated-item provenance lists growing and all preference values equal.
- Current inventory: 70 client/75 server receipts. Installed override SHA-256:
  `5f9cc6bff2bdb9802dc43dd1dc0de17264c5784cb294341dd0cf80345fc153c4`.

Evidence: `target/playerbots-composition-effective-checks-final.log`,
`target/playerbots-composition-runtime/live-v1/runtime-check.txt`,
`target/playerbots-composition-runtime/postinstall.json`,
`target/playerbots-composition-postinstall-audit/report.json`, and the receipt's
preflight/runtime files. Details: `PLAYERBOTS_ENGINE_COMPOSITION_20261004.md`.

**PB-VAL-009 remains pending:** actual native chain casts/procs/MP/animations,
emergency preemption, orders/strategy changes and map/death/party transitions in
real client combat. Loaded isolated fixtures and observed contexts are not that
acceptance. The full upstream subsystem inventory is complete and should not be
repeated. **Next implementation: PB-PORT-005**, the first source-reviewed complete
native class strategy slice. World population/invitations/trade retain separate
tracks; broader travel/dungeons, other PB-VAL checks and item-ID release-path
investigation remain unfinished.

## Owner-controlled ranged spread and finite retreat — installed live

Latest cumulative receipts `...215853-061213` and `...220541-625450` retain newer
engine composition `...213711-193912`, original tank/range fix and all prior mods.
The user clarified that RangeDps shooting worked; the remaining compact-distance/
endless-kiting request is addressed. Defaults are 4 m follow spread and 10 m combat
distance, per-ranged-companion Overview controls, account-owned atomic persistence
and native reach caps. Inner/outer Line slots remain distinct. Only one short
retreat per engagement is permitted; no movement/target switch/brief target gap
can keep renewing it. Five seconds of quiet combat reset the allowance.

Full source/companion suite passed. Final focused effective checks: 35 spacing/
HTTP routing/persistence/ownership/Line/finite-retreat cases, plus existing 38
position, 44 offense, 35 quest and 49 engine checks. Offscreen actual Aion WebKit
passes desktop/narrow controls and 27 authenticated fixture actions; inspected
screenshots retain readable labels and no horizontal overflow. Read-only real
companion inspection confirms four ranged actors at 10 m with 4/10 exposed controls
and tank first (22 checks). Eight actual loaded definitions captured by a null
transformer; 57 selected loaded methods match disk. 51 pinned reference hashes and
135 effective disk/source methods match. No forced live movement, spell or AI tick.

Fresh agents 41/42 preload originals before JAR copying and redefine five then one
classes, preserving five companions/preferences and one human connection. Earlier
126 entries and follow-up 133 entries remain byte-identical; engine continuation
survives. All 31 client hashes, base/launcher and preferences remain; five generated
provenance lists grew normally during gameplay. All 15 mod checks pass, 70 client/
77 server receipts. No server lifecycle or client archive/DLL modification.

PB-VAL-005 native terrain, following, actual pursuer/caster combat/shots and client
UI acceptance remain pending. PB-VAL-009 full native chains/strategy transitions,
post-revive appearance/casting, full port and item-ID release investigation remain
open. Exact artifacts/receipts: `PLAYERBOTS_SPACING_20261004.md`.

## Party save failures — PB-REPAIR-INV-001 installed; user gameplay testing pending

Read-only live private inventory/deletion-queue snapshots and current error logs
identify a false custody failure in MagicDps, Healeru, LeMuse and Tanku. Consumed
items already committed as UPDATED remain in deletion queues; their freed IDs
are reused by legitimate newer items. The companion guard checks those obsolete
records even though its SQL write filters exclude them. RangeDps had no mismatch
in the captured snapshots. Whole inventory/progress checkpoints roll back and
retry every two minutes. No rows/items/IDs/loaded methods were changed.

Receipt `224922-627052` installs one native transaction method filtering only
NEW/UPDATE_REQUIRED/DELETED before checks/writes/commit bookkeeping. 85 offline
production-method cases pass against the cumulative package; all 31 captured
loaded InventoryDAO methods match installed. 133 earlier entries, 44 settings,
five companions, one human connection, base/launcher and 31 client hashes survive.
All 15 mod inventory checks pass (70 client/78 server receipts). Agent 43 preloaded
the originals; no restart/client change or forced native checkpoint occurred.

See `PLAYERBOTS_CUSTODY_DIAGNOSIS_20261004.md` for evidence/verification. User
performs in-game periodic save/dismiss/resummon testing. Keep the older item-ID
release-path investigation and separate Tanku wipe/summon rejection open. No
reissuing or foreign-row changes were needed. PB-PORT-005 remains next broad port
implementation. Next attach update revision >43.

## Tank boss dragging — PB-REPAIR-TANK-001 installed; actual boss fight pending

The user reported tanks dragging bosses beyond spawn and getting stuck/dying.
The continuously recentered `Coordination.tankFacing` destination and active-tank
targeted-cast spread are repaired in a two-method installed package. All 134 earlier
override entries and existing native reach/hazard/cast/hate/custody logic survive.
Full source compile/all offline checks and 22 moving-boss/party/large-body/cast
regressions pass; the old actual effective code fails the regression. Eight hate,
35 encounter, 38 position and 85 custody checks pass against the effective package.

Automatic approval review initially rejected live deployment because the earlier
approval covered a different repair; the user explicitly approved this tank fix.
Receipt `232048-163778` records fresh agent 44 preloading and one atomic class
update, preserving five companion sessions/one human connection. All 57 selected
loaded tank/movement/attack/hazard methods match; Tanku's read-only policy check
passes while idle. The postinstall audit passes 51 hashes/140 effective methods.
All 44 settings, 134 earlier entries, base/launcher/media, 31 client hashes and
15 mod checks survive (70 client/79 server receipts). No lifecycle/client/DB/ID
change or forced tick/cast/movement occurred; actual boss fight acceptance is
pending. See `PLAYERBOTS_TANK_POSITION_20261004.md`. PB-VAL-005 remains open;
PB-PORT-005 remains next broader slice and PB-PORT-007 tank/pull coordination is
still partial. Preserve latest installed `232048-163778` and every prior mod.
Next attach update revision >44; existing helper edits require runtime SCOPES.
