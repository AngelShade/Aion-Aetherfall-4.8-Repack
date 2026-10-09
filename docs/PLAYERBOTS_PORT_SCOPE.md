# Full Playerbots port scope and iteration focus

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

PB-BUILD-002 resolves the startup mismatch that prevented NAV-004 game testing.
The complete normal builder JAR now has its matching maintained box XML/schema
and command resources, installed in `220257-711786`. Full static-data production
loading passes offline before and after delivery; all gameplay class entries
remain byte-identical. This is a delivery prerequisite repair, not a class port.
PB-VAL-005 remains user-tested; next class review remains PB-PORT-005C Cleric.
See [complete data/code gate](SOURCE_BUILD_STATIC_DATA_20261008.md).

## Current shared ground recovery - installed offline 8 October 2026

**PB-REPAIR-NAV-004** is an evidenced shared movement repair: intended altitude
is retained through planning/follow/controller probes; unsupported elevated
origins recover only after verified floor/vertical obstruction checks. No map
or coordinate exceptions. Complete normal Maven GameServer output is installed
unchanged in `playerbots-source-build-20261008-214740-770720`. Actual mesh traces,
57 floor/door/route checks and full linkage pass; prior features remain preserved.
**PB-VAL-005 game acceptance is pending user testing.** This does not close full
Playerbots parity. Next independent class review remains PB-PORT-005C Cleric.
See [source repair and offline delivery](PLAYERBOTS_GROUND_RECOVERY_20261008.md).

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


Current user policy (7 October 2026): GameServer and the game are off. Keep both
off and perform source compilation and isolated offline review/checks only.
The user tests actual gameplay after each WoW Playerbots feature or feature group
is ported. Pending user acceptance must not cause installed features to be re-ported.
No live attach or server/client lifecycle action is authorized by this policy.

Recorded 4 October 2026 at the user's explicit request to retain focus across
future chats and iterations. This is planning/continuity guidance, not a new
implementation or permission to start GameServer.

The destination is the original WoW Playerbots behavior adapted to native Aion
mechanics, alongside the user's established companion policies. The current
owner-bound companion system is a subset of that destination. The focused
`PLAYERBOTS_PORT_REVIEW_20261004.md` tracker reviews already attempted ports;
its twelve implementation entries are not a full upstream feature inventory.

Final pre-handoff lock-order correction is included in that receipt: AI-resolved
local geometry is cached, so movement refresh and follow speed never acquire
service/session formation locks under the mover monitor. Three Formation methods
plus FollowIntent refresh/cache initialization and a new Geometry record changed;
174 other entries retained. Effective bytecode and world-free cache tests pass.
Cold-load-only helper schema; future existing helper edits require explicit SCOPES.


## Scope gaps that must stay visible

| Scope ID | Original subsystem / intended Aion behavior | Current position | Dependency distinction |
| --- | --- | --- | --- |
| PB-SCOPE-001 | Independent world bots that remain active without a recruiting owner and choose roaming/grinding/quest/service activities | Not implemented. Current sessions require a connected owner and their party. | Needs independent lifecycle/population management and autonomous activity planning. Reuse existing combat/quest adapters; world travel is related to PB-PORT-011. Local quest arbitration alone does not supply this subsystem. |
| PB-SCOPE-002 | Players invite available world bots into parties; bots assign player control and transition back to independent behavior afterward | Not implemented. Owned-companion recruitment/automatic party placement is a different capability. | Needs native invitation decisions, availability and control/lifecycle transitions. Does not require every class rotation to be finished first. World-bot use depends on PB-SCOPE-001. |
| PB-SCOPE-003 | Native player/bot item and Kinah exchange with bot decisions and normal transaction/persistence semantics | PB-SCOPE-003A native owner gifts of items/Kinah to active alts and Temporary Bots are installed; donated legal upgrades use class/role scoring. Outgoing bot offers, group-member/world-bot bargaining and crafting trade remain unimplemented. | Needs native exchange handling, useful-item decisions and custody/persistence checks. Related to PB-PORT-009; world navigation is not a prerequisite for trading with an existing companion. |

The **bounded full subsystem inventory is complete** in
[PLAYERBOTS_SUBSYSTEM_INVENTORY.md](PLAYERBOTS_SUBSYSTEM_INVENTORY.md), including
the untruncated pinned tree, further scope IDs and independent dependency tracks.
Update affected entries rather than repeating the inventory. Preserve existing
PB-PORT/PB-VAL IDs and separate unsupported native equivalents and local policy.
An installed repair must not stand in for a new subsystem.

The original feature distinctions, group invitation options and trading options
are confirmed by the
[configuration at the project's pinned revision](https://github.com/mod-playerbots/mod-playerbots/blob/037c01418b5d01506917a3db9b44fd56ac5f965c/conf/playerbots.conf.dist).
That confirms subsystem existence; their action/manager algorithms still need
source review before claiming a port. Do not attribute Aion behavior to an unread
upstream action or copy WoW spell IDs, talent trees, coordinates or opcodes.

## Iteration contract

1. Read this scope register, the focused tracker, current validation and installed
   mod records. Refresh actual receipts before any install; preserve all mods and
   owned-alt builds. Refresh actual process state and honor current lifecycle
   authorization; do not start or shut down GameServer automatically.
2. Select one concrete missing behavior from the dependency plan. State its ID,
   original source, native mapping, what changes and what later work it enables.
3. Classify work as **new port**, **repair**, **prerequisite** or **validation**.
   Explain prerequisites with actual dependency evidence. Avoid making unrelated
   feature tracks wait behind the entire companion backlog.
4. Bound repairs to defects demonstrated in the selected slice or its necessary
   dependencies. Log unrelated findings separately. Do not reopen installed work
   simply because native/client validation remains pending.
5. Implement and test the actual behavior at the strongest authorized level.
   Keep source/offline/installed/native/client stages distinct. Update the current
   table and next-work pointer together, so historical notes cannot restart old
   work. Report user-visible progress and explicit remaining dependencies.

PB-PORT-003 is installed/offline verified with native/client validation pending.
PB-PORT-004 is installed live/on disk in receipt `213711-193912`, with 49 tests
passing against loaded helpers and all five real session contexts observed.
Actual client combat acceptance is pending. Next companion implementation is PB-PORT-005, the first
complete native class strategy slice. Population, invitation and trade retain
independent tracks in the inventory; they do not wait behind the entire class
backlog. GameServer is now running following a separately approved position
update; the user then requested finishing this reviewed strategy update, which
was applied without changing the server lifecycle. The full port
remains unfinished.

Current continuation supersedes the preceding next-work pointer: **005A Sorcerer
single-target/MP/boost strategy is installed** in `234841-695964`, with offline and
loaded-method verification; user gameplay testing is pending. **005B Spiritmaster**
confirmed missing single-target/resource ordering is now installed from a full
normal source build; existing native pet/utility adapters were retained after
comparison with GenericWarlock/Affliction. **005C** Cleric first-heal timing is installed/offline verified; gameplay remains PB-VAL-013. **005D** Chanter missing-mantra combat/support ranking is installed/offline verified; gameplay remains PB-VAL-014. **005E** Templar pressure protection is installed/offline verified; gameplay remains PB-VAL-015. **005F** Gladiator counter-window ordering is installed/offline verified; gameplay remains PB-VAL-016. **005G** Assassin chain fitness/finisher ordering is installed/offline verified; gameplay remains PB-VAL-017. **Next separate review 005H** is Ranger, comparing existing ranged positioning/chains/control/buffs/recovery with pinned Hunter before choosing missing behavior. Full
class coverage and independent world/invitation/trade tracks remain incomplete.
See `PLAYERBOTS_SORCERER_STRATEGY_20261004.md`; do not repeat the full inventory.

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
