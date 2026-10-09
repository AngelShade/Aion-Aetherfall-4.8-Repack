# Companion update validation — 4 October 2026

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

## Complete resource delivery and workflow audit — installed offline 8 October 2026

**PB-BUILD-003** installs all ten remaining maintained handler/static-resource
differences with unchanged normal Maven output in receipt
`playerbots-source-build-20261008-225416-105523`. This includes zone API alignment
and previously undelivered 1 October quest/skill fixes. All 3,549 non-manifest
JAR entries remain byte-identical; NAV-004, Spiritmaster and earlier mods survive.
All 1,757 handlers compile, 14 world-free suites pass (1,818 checks), full static
merge/XSD/JAXB loading and 3,281-class linkage pass. Inventory: 20 checks/31 client
hashes. No startup, attach, gameplay or database operation. Actual startup and
PB-VAL-005/012 gameplay remain pending user testing; next independent class review
remains **PB-PORT-005C Cleric**. Every future GameServer release must use the
canonical locked workflow in `tools/release-game-server.py`. See
[audit, resource list and receipt](BUILD_DELIVERY_AUDIT_20261008.md).

## Current source/static-data compatibility - installed offline 8 October 2026

PB-BUILD-002 repaired the reported startup XML/schema mismatch without changing
any compiled gameplay class. Matching normal-builder resources were delivered
in `playerbots-source-build-20261008-220257-711786`. Full production XML merge,
XSD and JAXB loading passed before delivery and again against actual installed
data afterward: 102,012 items, 4,091 box definitions and 470 overrides. No server
startup, world actors, database access or attach occurred. NAV-004 is retained;
PB-VAL-005 actual movement and full startup acceptance remain user-tested. See
[receipt and validation boundary](SOURCE_BUILD_STATIC_DATA_20261008.md).

## Current shared ground recovery - installed offline 8 October 2026

**PB-REPAIR-NAV-004** is installed as the complete normal Maven GameServer
output, unchanged, in receipt `playerbots-source-build-20261008-214740-770720`.
The old reported elevated position made zero progress in all 24 directions;
destination-aware probes now recover to the physical floor and four movement
traces (0.07/0.15/0.3/0.8m steps) reach the lower reported position exactly.
57 existing floor/door/route checks pass. Full package linkage verifies 3,281
classes / 171,808 executable member references without game initialization.
3,542 unrelated JAR entries remain byte-identical; postinstall inventory passes
20 checks/31 client hashes. Shutdown inspection, backup and hash guards succeeded;
no server startup, restart, attach, native actors or DB fixtures were performed.
**PB-VAL-005 actual game acceptance remains pending user testing.** Next class
review remains PB-PORT-005C Cleric. See
[shared repair and evidence limits](PLAYERBOTS_GROUND_RECOVERY_20261008.md).

## Previous source-built Spiritmaster slice - installed offline 8 October 2026

**PB-PORT-005B / PB-BUILD-001**: only confirmed missing Spiritmaster single-target
HP/MP-restoring damage/DoT/follow-up ordering was added after checking current
source against three pinned Warlock references. Existing pet/utility/refresh/
threat/native cost and owned-alt behavior is preserved. Complete normal Maven
Commons/GameServer outputs are installed unchanged; the old override is archived
and removed from libs/classpath. Unfinished outfit integration remains disabled
in source. Receipt `playerbots-source-build-20261008-213553-086764`.
59 new checks, all 14 builder-output suites and full 3,281-class linkage pass;
20 installed-mod checks and 31 client hashes pass. **PB-VAL-012 gameplay pending
user testing.** Server/client remain off. Next class review: **PB-PORT-005C Cleric**;
compare existing healing/support first. Full port and other investigations remain open.
See [confirmed gap, native mapping and normal build delivery](PLAYERBOTS_SPIRITMASTER_20261008.md).


## Previous stationary caster repair - installed offline 8 October 2026

**PB-REPAIR-ENGINE-002** is installed in external receipt
`20261008-201132-984909`; override SHA-256
`d03fd3180a7b9615cbf06e66c4266972a3c9519973d1590419067c450798204c`.
Completed native item tasks no longer fabricate movement during combat and fail
stationary spells at cast completion. Active item cancellation uses native item
observers. Source Java is updated; eight reviewed compiled methods and one helper
are installed, with 172 other entries/unselected methods and all prior mods kept.
147 native task/observer/condition checks, existing effective cast/follow/recall/
trade checks and linkage pass; installed inventory passes 20 checks/31 client hashes.
**PB-VAL-009 actual Songweaver/Sorcerer/Cleric combat acceptance remains pending.**
GameServer/client stay off. Source release audit found unfinished appearance hooks;
Full source-build transition is now installed (PB-BUILD-001); see current status above.
See [root defect, source/build audit and preservation](PLAYERBOTS_ITEM_TASK_CASTING_20261008.md).


## Previous summon and distance recall baseline - installed offline 8 October 2026

**PB-REPAIR-SUMMON-001 / PB-CUSTOM-RECALL-001** are installed in external
receipt `20261008-195139-913545`; override SHA-256
`0524e84d93eae1aba6b0e966e47475e54b82f4f07362dc080e1f031b8b11b7ff`.
Manual Summon retains the owner's own combat restriction and native ownership/
party/lifecycle/configuration checks, but cancels bot casts, trade, loot and
channels rather than requiring every companion to be idle or out of combat.
Living owned bots beyond 60m or separated by map/instance recall before trade,
transfer, movement failure and strategy handling, regardless of their current
activity/order or owner combat, and resume FOLLOW. Native dead-bot recovery and
custody-held/dismissing sessions remain protected.

Actual Java source and maintained tests/staging/install scripts are updated;
the installed delivery changes eight methods in six existing definitions and
adds PlayerBotRecall. All 171 other JAR entries and unselected members survive,
including prior follow/formation/cast/Steel Rake and missing-appearance correction.
Full 2,424-source and 53-check-source compiles, 165 recall checks, prior
679/72/99/49/44/6/17/57/314 checks, and 175-class / 24,476-reference runtime
linkage audit pass. Current client exit-saved graphics preferences are preserved.
**PB-VAL-005 native/client Summon, cancellation, far-bot recall and following
acceptance remains pending user testing.** Server/client remain off; no native
world/DB/ID fixtures or startup/attach. Next class slice remains PB-PORT-005B.
See [recall behavior and source record](PLAYERBOTS_RECALL_20261008.md).


## Previous follow regression repair - installed offline 8 October 2026

**PB-REPAIR-PACKAGING-001** fixes the reported `NoClassDefFoundError` in
`PlayerBotSession.tick`: the previous follow package imported a tick hook to
source-only `PlayerBotAppearance`, which is absent from every runtime library.
The tick aborted before AI/follow actions. The source hook is removed until that
complete custom feature is explicitly integrated; no error swallowing or new
movement workaround was added. Appearance remains source/staged only.

Installed receipt: `20261008-191905-805583`; override SHA-256
`6f72603b8a385f5f9f39c3bdd9b9a8783c06ab17b549e648195e915b9503c5c4`.
Exactly one method changes; all 176 other JAR entries and all other Session
methods are preserved. Prior follow/summon controls, Steel Rake, native cast
safety, settings and owned-alt builds survive. A read-only baseline checkpoint
`191458-608072` records seven equivalent startup-recompiled Steel Rake cache
classes and the preserved legacy instance_follow=true setting.

The new shared staging/final-package/preinstall gate checks actual executable
class/member/lambda references against runtime libraries, excluding source build
classes. It reproduces the old failure and passes the installed 174 classes /
24,383 executable member references. Full 2,423-source compilation, 52 Playerbots
check-source compilation, five gate regressions and prior 679/72/99/49/44/6/17/57/
314 production checks pass. These isolated checks do not establish gameplay.
**PB-VAL-005 actual following remains pending user retest.** Both processes remain
off; no startup/attach or native gameplay/DB/ID execution occurred. Independent
next class port remains **PB-PORT-005B Spiritmaster**. See
[the root-cause and installation record](PLAYERBOTS_LINKAGE_REPAIR_20261008.md).


## Previous follow/summon implementation — installed offline 8 October 2026

PB-REPAIR-FORMATION-002 and PB-CONFIG-001 are installed in external receipt
`20261008-062930-344543`. Continuous direct follow intent refreshes the selected
formation on movement ticks; travel outranks optional idle actions; speed matches
the owner and recovers slot error. Native casts, collision, explicit orders and
role combat positioning remain authoritative. Both server enable/summon switches
are true, with shared ownership/party/combat/cast/trade/loot/channel preflight.
Automatic map/wipe recovery remains independent of the manual summon switch.

679 new effective checks plus core 72/engine 99/composition 49/spell planning 44/
speed packets 6/trail 17/ground 57/formation 314 pass. All 2,423 current server
sources compile externally; all Playerbots check sources compile. Initially thirteen
methods/six existing definitions changed, 166 earlier JAR entries retained. Latest
Steel Rake `061243-633233` and core execution `054856-494763` survive. Only the
cumulative JAR and main Playerbots config changed; base/launcher/geometry/scripts/
bundled UI/31 client hashes/alt builds retained. Inventory: 20 checks, 73 client/
102 server receipts. Override SHA-256:
`f4855888ee12bea913327c8be2462835e292cb3c4f5ec5db5cd4e891221c5a27`.

PB-VAL-005 actual rendered movement/formation/turns/flight/doors and summon control
acceptance remain the user's tests. The new config field is cold-load-only.
GameServer/client stayed off; no startup/restart/attach, native gameplay or real
DB/world/ID writes. This repair does not close full parity or block independent
tracks. **Next class port remains PB-PORT-005B Spiritmaster.** See
[follow/summon behavior, sources and acceptance](PLAYERBOTS_FOLLOW_SUMMON_20261008.md).


Final pre-handoff lock-order correction is included in that receipt: AI-resolved
local geometry is cached, so movement refresh and follow speed never acquire
service/session formation locks under the mover monitor. Three Formation methods
plus FollowIntent refresh/cache initialization and a new Geometry record changed;
174 other entries retained. Effective bytecode and world-free cache tests pass.
Cold-load-only helper schema; future existing helper edits require explicit SCOPES.


## Current offline review / user testing policy — 7 October 2026

The user reports GameServer and the game are off and will test actual gameplay
after each WoW Playerbots feature or group of features is ported. Keep both off;
no live attach, startup/restart or forced gameplay check. Offline source/build/
package evidence must remain distinct from installed/native/client acceptance.

PB-CUSTOM-APPEARANCE-001 review found and corrected item persistence (UPDATED
suppressed pending inserts/updates), broad costume filtering of real stat clothes,
and inconsistent expiring-source/panel-target eligibility. Full offline Maven
Commons/GameServer compile passes (2,410 server sources); 16 native-item checks
pass against source and the corrected effective staged package. Package
`target/playerbots-appearance/package-reviewed-20261007` preserves 144 prior
entries, changes five existing methods in three classes and adds two helpers.
Old package-v2 is superseded and must not be installed. No deployment occurred.

PB-REPAIR-FOLLOW-001's teleport fallback was rejected and removed in receipt
`171732-465446`; its timer checks did not establish the reported door/room cause.
Current [PB-REPAIR-NAV-002](PLAYERBOTS_NAVIGATION_20261007.md) uses generic native
dynamic-door and floor probes across movement states. User continued failure reopened
the follow executor; latest `190312-034119` repairs reached-breadcrumb progress
without per-map/door IDs (17 progress/40 geometry checks pass). Offline evidence
is distinct from actual room/combat/party acceptance, which remains the user's
test. Appearance/save/resummon acceptance remains pending. Broad next port stays
PB-PORT-005B.

Historical 4 October cumulative override receipt (current: `193213-430087`,
see [generic navigation record](PLAYERBOTS_NAVIGATION_20261007.md)):
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

## PB-DIAG-PERF-001 latency incident — observed, exact root cause open

At 23:44:40 Europe/Bucharest the AI update took 7072 ms and a native web HTTP
Exchange request took 6443 ms, ending 12 ms apart. Later JFR samples identify the
exact warning lambda as Service.tick. Source confirms companion HTTP/AI/save
monitor sharing; original route/call stack/monitor owner were not recorded.
The later bounded 45-second profile has no repeat warning: max GC pause 6.7565 ms,
no long companion-lock event, max captured DB socket read 153.3198 ms. Current
thread snapshot has no deadlock; exact earlier cause is not established.

Only read-only JVM/log inspection and automatically ended diagnostic recording
occurred; no source gameplay repair/install/lifecycle/client/character/DB write.
Preserve current Sorcerer receipt `234841-695964`, all prior mods and next broader
005B pointer. User activity/recurrence remains pending; see
`PLAYERBOTS_LATENCY_20261004.md` and `target/playerbots-latency-diagnosis`.

## PB-PORT-005A Sorcerer single-target strategy — installed; user testing pending

Receipt `234841-695964` retains latest tank `232048-163778`, custody, spacing,
engine and every earlier mod. Pinned Fire/Generic Mage ordering now maps to native
Sorcerer/Mage ONLYONE chain procs, vulnerability/DoT upkeep, movement/finishing
fillers, affordable/cooldown fallbacks, MP recovery and native caster boosts.
Evocation's raw WoW priority is normalized below Aion encounter actions. No skill,
build, Stigma, level, equipment or resource grants/replacement. AoE toggle/safety,
other classes, native defense/interrupt/threat and explicit orders remain intact.

Full source/command/offline suite passes; final refinements recompiled and tested
against the effective package: 54 new production/engine/native-template checks,
44 offense gates, 49 engine continuers, 38 position, 35 encounter and 85 custody
regressions. Old installed routine fails the new filler ordering. Four methods
in three classes plus one new helper; 133 previous entries byte-identical. Tick
review confirms only two strategy-name constants/calls changed in its flow.
Agent 45 preloaded originals and retained five companions/one human connection.
Six observed loaded definitions/193 methods match installed; source audit passes
51 hashes/163 effective methods. 46 settings, base/launcher/media and 31 client
hashes survive; all 15 inventory checks pass (70 client/80 server receipts).

**PB-VAL-009 native casts/MP/chain/client testing stays with the user**, as requested.
No forced tick, movement, cast or DB/ID writes; no lifecycle/client changes.
Complete class parity, Sorcerer AoE/CC/escape and world/invitation/trade/travel/
dungeon work remain unfinished. Next slice **PB-PORT-005B Spiritmaster learned
single-target/pet strategy** (native pet coordination on 008 as needed).
See `PLAYERBOTS_SORCERER_STRATEGY_20261004.md`. Historical ID release and separate
wipe/summon repairs remain open. Next attach update revision >45.

## Native bot owner gifts installed (5 October 2026)

Care settings repair PB-REPAIR-SETTINGS-001 is subsequently installed in receipt
`042546-945113`, retaining gifts `005644-110252` and separate Saendukal `025825`.
One State.save method now retries temporary Windows AccessDenied during replace
with 175 ms maximum total delay. Full source compilation/21 policy checks were
completed before the user instructed stopping tests; no later/native/client test
ran. Fresh agent 49 preserved five sessions/one human; gameplay acceptance stays
with user. See `PLAYERBOTS_SETTINGS_FILES_20261005.md`. Next attach revision >49;
broader next class slice remains PB-PORT-005B.

PB-SCOPE-003A / PB-VAL-010: main receipt `004941-367262`, current cumulative
`005644-110252`. Owners give items/Kinah to active owned alts or Temporary Bots
through the native trade window. Bots lock/complete their side and evaluate only
donated gear for legal class/role upgrades, serializing native binding/identification.
Normal item rights and custody persist; outgoing bot offers/world trading remain
partial. This explicit donation exception does not enable general alt auto-gear
or modify level/class/build/skills/Stigmas.

Full source/offline suite and 56 final production boundary/native-window tests
pass. Fresh agents 47/48 preserve ten companions/two humans, schemas and rollback;
no restart/client replacement/forced live trade, movement, cast, save or DB/ID tests.
All 218 methods in eight final observed definitions match disk; source audit
checks 51 pinned hashes/190 methods. All 15 mod checks and 31 client hashes pass;
base/launcher/media/prior mods/preferences survive. Current inventory records
70 client/82 server receipts. Actual native trade/persistence/client acceptance
stays with the user, PB-VAL-010; the full port remains unfinished.

See [trade behavior and validation](PLAYERBOTS_TRADING_20261005.md). Existing
005A stays installed; next companion class slice remains 005B Spiritmaster.
Economy, outgoing trading and world/invitation tracks retain independent scope.

## PB-REPAIR-SETTINGS-002 — installed offline 7 October 2026

The current care retry is confirmed retained; the pasted Tancul traceback matches
the older 04:15:05 incident. Current logs show the same Windows sharing denial
from gear provenance saves for MagicDps/LeMuse. GearPolicy.State.save now uses
the existing bounded atomic-replacement retry. One method changed; 146 other
cumulative entries remain byte-identical. No settings/build/item/quest policy
changes or error suppression. Persistent locks still report failure; the locking
process is not identified.

Current receipt `backups/playerbots-recruitment-20261007-081522-429924` retains
`042546-945113`, gifts, Sorcerer, tank, custody, native shield and every earlier
mod. Full offline source build and 36 focused source/effective/Windows private-file
checks pass. Server/client remained off; no startup/restart/attach or forced native
gameplay/DB/ID tests. User actual care/gear/supplies acceptance is pending.
See [settings diagnosis and installation](PLAYERBOTS_SETTINGS_FILES_20261007.md).

Next broader slice stays PB-PORT-005B Spiritmaster; independent scope tracks remain
open. Follow recovery PB-REPAIR-FOLLOW-001 was rejected and removed in `171732-465446`.
Generic navigation PB-REPAIR-NAV-002 is installed offline through `190312-034119`.
The user reported continued failure after `175949-485037`; the latest continuation
repairs reached-breadcrumb progress in the shared follow executor (17 progress and
40 geometry checks pass). Actual door/room/combat/party acceptance remains pending. Appearance remains source/staged only
and must be restaged against the current cumulative receipt before installation.
All earlier unfinished port/repair work survives.

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
repository namespaces and the full port remain partial. Appearance remains
source/staged only and must be restaged against the current receipt before install.


## Current continuation — PB-SCOPE-012B installed and 52 files retired

On the user's remaining-file report, preferences (17), behavior (13), spacing (7),
formation (2) and reward witnesses (13) moved to native MetadataDAO persistence.
All 52 rows imported and verified exactly from a separate read-only connection;
all 52 originals archived/hash-verified then removed offline. Nine media/preset/
removal files remain unchanged. Current cumulative receipt is `191742-640906`,
cleanup `191828-971151`; override SHA-256 `83a3143148a2e3a8f5b28894742399db05f8c302111990df41b2adf99b2dc978`.
The complete navigation repair and other mods survive (152 prior entries identical).
Eleven existing methods/six definitions changed, one helper added. Full compile,
35 existing metadata/31 preference checks, 16 mod checks/31 client hashes pass;
inventory 70 client/93 server receipts. GameServer/client remain off; no lifecycle
action or live attach occurred. Actual settings/reward/restart acceptance remains
PB-VAL-011; door/room acceptance remains PB-VAL-005. PB-PORT-005B stays next class
slice; Temporary creation gear-file gap PB-SCOPE-012A-R1 remains separate/open.
See [remaining preference namespaces and evidence](PLAYERBOTS_METADATA_PREFERENCES_20261007.md).


## Current continuation — PB-SCOPE-012C repository installed, runtime folders retired

Two saved-party documents and four removal markers now use native account/roster
repository tables; three UI files are bundled unchanged in the cumulative JAR.
Six exact native owned rows imported/verified read-only, nine originals externally
archived/hash-verified and retired; saved-parties/removed/media folders are absent,
runtime config/playerbots is empty. Creation gap PB-SCOPE-012A-R1 is also repaired:
pre-row generated provenance queues into the first native inventory checkpoint.
Current cumulative receipt `193213-430087`, SHA-256
`4a65e21ca22fff63a2d1cbbf7d0472bbb314894994045ceba5264894581de69b`.
Eight existing methods/five definitions changed; 154 prior entries byte-identical.
Full compile/24 repository/10 creation checks and 17 mod checks/31 client hashes
pass. Native read-only production loaders pass for both accounts/four removed
roster entries. Inventory: 70 client/94 server receipts. Server/client remained
off, no attach or lifecycle action. Actual preset/remove/UI/new-creation/restart
acceptance remains with user; PB-PORT-005B stays next independent class slice.
See [native repository and packaged interface](PLAYERBOTS_REPOSITORY_20261007.md).


## Current continuation � Steel Rake PB-SCOPE-007A installed offline

Requested native encounter mechanics and visible companion tactics are installed
in external cumulative receipt `20261007-223400-856173`: steam/bomb warnings,
protected phases, captain adds and single-operator native Mantutu food/water
channels. Phase callbacks and manual summons now reset/clean up reliably.
Full core/13 handler compilation and 39 isolated checks pass; 164 unrelated
JAR entries and bundled UI retained. Actual boss gameplay remains pending.
User confirms opened-door crossing resolved; new exact Central Engine Room
geometry stall is PB-REPAIR-NAV-003, next in this request after the boss slice.
See [encounter scope, native sources and limits](PLAYERBOTS_STEEL_RAKE_20261007.md).
PB-PORT-005B remains the independent next class slice, broader parity partial.


## Current continuation — PB-REPAIR-NAV-003 installed after Steel Rake encounters

Both requested slices are installed offline. Native Steel Rake boss lifecycle and
companion tactics PB-SCOPE-007A installed first (`223400-856173`), then general
physical-see-through floor support PB-REPAIR-NAV-003 (`224940-884362`). Final
encounter self-target correction (`225401-230689`) changes two script/class pairs
and retains all 171 cumulative JAR entries. Current override SHA-256:
`8653514fdf8956c3ffcf3e311c84ccd6341a8525e6832cf45e760fde8e4662e0`.
Exact reported native movement reproduction now reaches the owner; no production
map/room/model exception or teleport fallback. 57 floor/route + 17 trail + 39
encounter checks and all 15 door comparisons pass; full 2,420-source/13-handler
compile. Boss/room/combat gameplay acceptance remains pending with user.
GameServer/client are off; no startup or attach. Prior mods/bundled UI/owned alt
builds retained; independent next class slice remains PB-PORT-005B. See
[navigation mechanism/evidence](PLAYERBOTS_NAVIGATION_20261007.md) and
[native encounter slice/limits](PLAYERBOTS_STEEL_RAKE_20261007.md).


## Current repair — PB-REPAIR-ENGINE-001 installed offline 8 October 2026

The shared mover could execute stale work after casting began, and repeated orders
cancelled native casts. Unchanged preferences also discarded engine continuers.
Core moveStep/Session order/applyPreferences/CastAction.execute now enforce current
movement/casting state, atomic mover/cast admission and idempotent controls. Real
order/mission/attack changes still interrupt deliberately. No delay/spacing tuning,
global cancel suppression or change to native interruption rules was added.

Four baseline production failures reproduced; 72 effective core checks, 99 engine,
31 recording-JDBC preference, 49 composition, 44 spell-planning and 17 navigation
checks pass. All 2,420 sources compile externally. Four methods/three definitions
changed; 168 unrelated JAR entries and all earlier mods/owned-alt builds retained.
Five compiler-regenerated native handler cache classes were method/schema verified
against reviewed references and archived unchanged, without disabling hash guards.
All 20 inventory checks/31 client hashes pass; 73 client/99 server receipts.

Current core receipt is external `20261008-054856-494763`, override SHA-256
`77890851c3e6b15ca73558abb62266ffbf32b88722c5825ae73963ebb13cfa93`.
GameServer/client remained off; no startup/restart/attach, native casts, world/ID
operations or real DB writes. PB-VAL-009 actual cast/charge/chain/control gameplay
acceptance remains the user's test. PB-PORT-005B Spiritmaster remains the independent
next class slice; other port/validation tracks remain open. See
[core execution diagnosis and control review](PLAYERBOTS_CAST_EXECUTION_20261008.md).


## Current continuation — PB-SCOPE-007A-R1 original Steel Rake TODO behavior

Installed offline 8 October 2026 in external cumulative receipt
`playerbots-recruitment-20261008-061243-633233`, retaining concurrent engine repair
`054856-494763` and all earlier navigation/mods. Current override SHA-256:
`abe0403e86bc715d20d44625e87ffd0e5e56a9051b74e11d061d1bc88c56d76d`.
Captain towers, floor-correct add lanes, staircase retreat/return, native amplifier
activation/enhancement/root and sequenced pull/blast now run through owned phases;
gunner pause also blocks already queued attacks/casts. Companion tactics exclude
scenery and prioritize engaged caster/healer adds. All seven original comment
texts remain beside implementations; the historical packet-capture evidence gap
is explicit. Exact retail offsets/cadence are unverified native-asset adaptations.

Three effective methods/two definitions change, one tactic helper is added and
169 unrelated JAR entries are byte-identical. 2,423 core sources/15 handlers
compile externally; 479 isolated phase/tactic/callback/navigation/core regression
checks and read-only native floor/add-lane checks pass. Post-install package and
20 mod checks/31 client hashes pass; inventory 73 client/100 server receipts.
Bundled UI, native repositories, owned alt builds, generic floor routing, client,
launcher/base/geodata and source-only appearance work survive. Runtime bot config
remains empty. GameServer/client stayed off; actual encounter casts, movement,
wipes and client presentation remain user acceptance. PB-PORT-005B remains the
independent next class implementation; broader parity/other investigations remain
open. See [TODO mapping, sources and evidence limits](PLAYERBOTS_STEEL_RAKE_20261007.md).
