# Focused WoW Playerbots port revision — 4 October 2026

## Current source-built Spiritmaster slice - installed offline 8 October 2026

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


Current lifecycle policy (7 October): server/client off; no live attach or startup.
The user performs gameplay testing after each feature or feature group port.
PB-REPAIR-FOLLOW-001 was rejected and removed in receipt `171732-465446`.
Current navigation includes PB-REPAIR-NAV-002/003 generic door progress and floor
support; the user confirms the earlier opened-door crossing works. Central Engine
Room/combat/party acceptance remains pending. Latest cumulative receipt is external
`20261008-061243-633233`: PB-SCOPE-007A-R1 original Steel Rake TODO behavior,
retaining the concurrent engine repair `054856-494763`, navigation, native bot
repositories, bundled UI and all earlier mods. Custom appearance remains
source/staged. Next upstream class slice: PB-PORT-005B; boss gameplay acceptance
and exact retail capture evidence remain pending.

Read [full port scope and iteration focus](PLAYERBOTS_PORT_SCOPE.md) first.
This tracker covers already attempted ports, not every upstream subsystem.
Independent world populations and native invitations/control transitions remain
unimplemented. PB-SCOPE-003A owner item/Kinah gifts are now installed; broader
bot outgoing/value/crafting/world trade remains partial. The bounded full inventory/dependency
pass is complete in `PLAYERBOTS_SUBSYSTEM_INVENTORY.md`; do not restart it.

Comparison baseline: unchanged upstream sources at
`037c01418b5d01506917a3db9b44fd56ac5f965c`, tracked in
`third-party/playerbots/SHA256SUMS`. This pass reviews already attempted ports;
it does not judge custom recruitment/PvP, completion mirroring, temporary tiers,
spending policies or other user-requested changes against WoW defaults.

The [current reverification tracker](#reverification-tracker--4-october-2026)
is authoritative for next work. The following matrix preserves the first pass's
historical findings; it is superseded where later sections report continuations.
PB-PORT-001/002 now have source, final-action offline and installed-disk
corrections in receipt `193448-980138`; native/client acceptance remains pending.
PB-PORT-003 now has shared local execution arbitration installed in receipt
`203902-131350`; native/client acceptance remains pending. PB-PORT-004 is now
installed live/on disk in `213711-193912`, with loaded-engine fixtures and session
context checks passing; real client combat acceptance remains pending.
PB-PORT-005A Sorcerer single-target/MP/boost strategy is installed in `234841-695964`;
user native/client testing is pending. Next companion slice is PB-PORT-005B,
Spiritmaster learned single-target/pet strategy. Full class coverage remains open.

Final pre-handoff lock-order correction is included in that receipt: AI-resolved
local geometry is cached, so movement refresh and follow speed never acquire
service/session formation locks under the mover monitor. Three Formation methods
plus FollowIntent refresh/cache initialization and a new Geometry record changed;
174 other entries retained. Effective bytecode and world-free cache tests pass.
Cold-load-only helper schema; future existing helper edits require explicit SCOPES.


## Historical first-pass matrix

| Ported area | Original behavior | Current Aion equivalent / finding | Correction or effect on other ports |
| --- | --- | --- | --- |
| Engine/actions/triggers/values | Strategy queues, useful/possible checks, weighted prerequisites/alternatives/continuers, cached context values | Fresh bounded snapshot queue, stable ties and prerequisite guards; intentional stale-target protection. Configurable strategy composition, positive multiplier weighting of expanded actions and continuers are partial. Production passes no multipliers. | No wholesale engine rewrite in this pass. Missing composition affects extensibility rather than current empty multiplier policy. |
| Combat/skills/roles | Class/spec action strategies, resource/cooldown checks, chains and typed actions | Native learned skills/effects, roles, charge/pet/rune adapters and animation locks. Rotations are metadata-ranked rather than fully ported class strategies. | **Mistake fixed:** mixed damage/threat skills were tank-only taunts. Five Ripclaw Strike ranks must remain Assassin offensive actions; pure native threat tools remain taunts. Affects rotations, Stigma usefulness and DPS. |
| Healing/buff/cleanse target selection | PartyMemberToHeal checks same world/LOS/distance and healing in progress; picks urgent valid candidates | Health bands/reservations/native cleanse and buff capacity are present. Selection previously chose a friend before testing native spell target eligibility. | **Mistake fixed:** prefilter each spell's recipients through native planning, world/LOS/reachable radius. An invalid first friend must not starve all other heal/buff/cleanse candidates. |
| Group healing/pets | Group-health triggers, pet/charm healing with lower priority, focused heal lists | Per-member native heals; own-pet dedicated skills supported. Broad pet healing, focus lists and group-heal aggregate ranking are partial. | Remaining work affects healer coordination and AoE spell choice; do not claim fully ported. |
| DPS/tank target selection and assist | DpsTargetValue/TankTargetValue score threat, distance, expected life, raid icons and current targets; tanks pick loose/low-threat enemies | Owner/explicit target and nearby engaged targets; tanks recover non-tank party/pet victims. Native hate used. Rich icon/lifetime/main-tank target values are partial. | Aion native marking/topology needs its own adapter. Existing assistance is connected, but not full target-value parity. |
| Threat coordination | Tank taunts/threat strategies and DPS suppression | Positive HostileUp/BoostHate, actual native hate and recovery; damage waits use real tank attention | Correct behavioral purpose; WoW spell IDs/threat API are not copied. Multi-tank assignment remains partial. |
| Buffs/debuffs/interrupts | Effect-aware spell strategies, stack/availability checks, target debuffs | Native effect stack/capacity, dispel severity, interrupts, rune/DoT checks | Connected. Full per-class debuff priorities and weapon buffs/food automation are incomplete; Aion effect rules differ. |
| Following/formation | Formation target, continuous MotionMaster follow, transport support | Stable damped Aion slots, real movement controller/geometry and matching client/server speed | Correct native adaptation. Formation modes and moving transport boarding not ported; boats/zeppelins have no direct universal Aion equivalent. |
| Navigation/combat positioning | WoW path generator/navigation values, LOS/range, reach/flank/kite | Collision-probed bounded local routes, breadcrumbs, flight, reach/flank/kite and hazard geometry | Local navigation is partial versus global travel graph. Wrong-world/stale targets are checked. Real geodata acceptance remains separate. |
| Pulling/tank/healer/DPS group behavior | Explicit pull/assist strategies, wait for setup, threat and support | Native engaged-target assistance, tank opening and short DPS pause | Connected but full pulling strategy/CC assignment not yet ported. User quest-pull toggle is outside this review. |
| Quests/NPC dialogues | Accept/share/query/drop/complete actions call real handlers; individual journals and reward choice | Native Aion handlers, journal/collection/object loot, report/contact conversations and class reward scoring | Correct equivalent. WoW quest stages cannot map to Aion campaign custom dialogue automatically; unsupported branches stay explicit. User completion mirroring is excluded from judgement. |
| Loot/inventory/equipment | Loot rights, quest/useful item classification, equipment comparison, bag pressure and shops | Native drop rights/rolls/cube slots, class/role equipment, protection and optional native shop/care | Connected. Broader useful-item categories, automatic junk selling/trading/bank and economy strategies remain partial; no silent disposal added. |
| Skill learning/factory | Native training/talents/spec/gear factory | Native skill tree/mastery, Stigma slots/effects and class gear | Stigmas are the correct Aion build equivalent, not WoW talents. Detailed optimized builds remain partial; auto-alt policy is excluded from review. |
| Party/commands/state transitions | Bot manager commands, group invitation/removal and engine transitions | Owned leases, native groups, explicit orders, save/dismiss, combat/dead transitions | Connected. Raid/alliance topology, complete command vocabulary and strategy persistence remain partial. |
| Teleports/maps | Native teleport/session acknowledgements and master following | Native world/region/instance registration and whole-party recovery | Correct headless Aion equivalent. WoW packet opcodes are inapplicable; five-member native fixture already passed. |
| Death/resurrection | AcceptResurrectAction calls native acceptance; other strategies include reclaim/release and combat resurrection where legal | Native skill/rebirth acceptance and alive/dead state; healer resurrection currently outside combat | Acceptance adapter correct. Corpse/release navigation and spell-specific legal combat resurrection are incomplete, not fully ported. |
| Previously disconnected paths | Upstream systems are strategy/action/context registered | Aion session connects gear, care, quests, pets, threat, flight and formation helpers | Earlier helper disk/RAM mismatch repaired before this pass. No newly discovered unreachable adapter beyond listed partial features. |

The table above is the **historical first-pass matrix**, not current completion
status. Subsequent installed continuations are recorded below. The authoritative
current status and next-work order are in the [reverification tracker](#reverification-tracker--4-october-2026)
at the end of this document. In particular, group/pet healing, legal combat
resurrection selection, formation modes and caster/combo target values have since
been implemented; new downstream offense and quest-arbitration gaps remain.

The first pass's changes stayed within the two proven translation errors. Missing subsystems are
continuation work, not an excuse to copy WoW mechanics that Aion does not have.
Saved bots and mixed party presets are subsequent user features, outside this review.

## Applied corrections and subsequent continuation

Review corrections were installed in receipt `072922-921933` (deployment backup
prefix `playerbots-recruitment-20261004-`). Five production damage/threat ranks and
two native support-condition cases passed, with no planning HP/MP/effect changes.

After the focused review, continuation receipt `080311-450862` added the original
GeneralFindTargetSmartStrategy purpose: prefer in-range targets with less remaining
health; otherwise reach the closest. Direct player commands/leader assistance stay
explicit. Tanks recover party/pet victims, then reinforce weak actual native hate
in range while avoiding enemies already held by another tank. Emergency victim
triage is retained as an existing Aion behavior. Native tests exercised the installed
session with real NPC victims/hate/range/health, plus 18 deterministic selection cases.
This is not full Caster/Combo/Rti target parity: estimated lifetime, marking and full
main/off-tank assignments remain continuation work. No new pulls or crowd-control
bypasses were added; the existing enemy admission path still runs before selection.

Saved Temporary Bots and mixed presets (receipt `074432-111599`) are the user's
subsequent feature, not criteria used to judge the original port.

## Healer continuation after the review

Receipt `100323-698137` adapts `PartyMemberToHeal.cpp`, `HealthTriggers.cpp` and
`HealPriestStrategy.cpp` into `PlayerBotHealing` and installed session actions.
The source pin above is unchanged. Upstream prefers urgent reachable party players,
penalizes distance and pets, avoids healing already underway above medium health,
and uses wounded-group triggers for area heals. Aion now evaluates the actual native
affected list, species/range/LOS and first-target rules. A healthy healer can anchor
a self-centred party heal for injured allies; pets receive ordinary friendly heals
only when the native spell allows them. PARTY and PARTY_WITHPET remain distinct.
Hybrid Bard cleansing/healing keeps its classification but scores both effects.
Reservations cover actual native affected recipients without suppressing emergency
healing below the upstream medium-health boundary.

The old outside-combat resurrection veto was an incomplete translation. Learned
resurrection now consults each Aion spell's native conditions, including CombatCheck.
Thirty comparisons and isolated native players/summon passed, with an actual
installed Healing Wind action healing two members, paying mana and reserving both.
Native resurrection selection accepted a legal spell in combat and rejected a
CombatCheck-restricted one. This does not claim an actual resurrection cast, every
hybrid spell, focus lists, raid/charm handling, corpse travel or full class strategies.

The subsequent local inventory custody guard/recovery is data integrity work,
not an original WoW subsystem or an evaluation of user-requested design changes.

## Continued class enemy utility

The pinned Mage/Warlock/Hunter/Rogue strategies give offensive dispels and caster
interrupts dedicated relevance. The Aion classifier had omitted native
`DispelBuffCounterAtkEffect`; several actual Spiritmaster skills were therefore
unavailable to the action queue. Ordinary purges also lacked per-target useful-buff
checks, and only the primary enemy was considered for interrupts. These gaps
affected class skill usage, enemy buff handling and group caster coordination.

The installed continuation restores counter-purge classification, reads native
dispel eligibility/power/count/category/level without changing effects during
planning, and selects reachable party-engaged secondary casters. Mage/Warlock/
Hunter purge base bands and the Rogue normal/healer interrupt ordering retain the
pinned source values. Native buff relevance is an explicit Aion fit adaptation;
Spellsteal becomes native removal, not buff theft. Existing threat/area/ownership
guards are retained. Only learned native skills are eligible. WoW pet/shard/energy
mechanics are not copied onto unrelated Aion systems.

Twenty-six comparisons and actual installed Ignite Aether and Sigil of Silence
casts passed. The final native fixture verifies counter removal/damage/MP,
interruption of a real native healing cast, reservation fallback, stable ties,
category/level/designation exclusions and exact class interrupt bands. Full class
strategies and pet purge casting remain incomplete. Details and receipts are in
`PLAYERBOTS_CLASS_STRATEGIES.md`; live client gameplay remains separate acceptance.

## Continued class defenses

Pinned Generic/Frost Mage, Hunter and Assassination Rogue strategies defend before
panic and control an enemy pursuing a ranged bot. The Aion implementation had
offered all defenses only below 35% HP, omitted pure native magical resistance and
snare classification, and assigned roots interrupt relevance despite roots not
cancelling Aion casts. This affected class action selection, defensive utility and
party control coordination.

Receipt `123520-131583` repairs these focused gaps with `PlayerBotDefense`: native
learned barriers/avoidance and MP shields at the relevant original class bands;
engaged-pursuer roots/snares with role/duplicate/control-reservation guards; and
finite native resistance only for incoming hostile magical spells. Aion resistance
is not translated as WoW Cloak's debuff removal. Native effect conflicts, weapon
conditions, resources, target safety and the previously installed panic policy are
retained. No alt builds are replaced. Later receipt `124615-036768` retains the
defensive implementation. Thirty-four comparisons and actual shield absorption,
root/snare casts and consumption of both magical-resistance charges passed. Escape
movement, all other class rotations and client gameplay remain unfinished.

## Continued original offensive and destination behavior

The already attempted native rune/periodic adapters had scored every finisher
uniformly and lacked a connected periodic refresh decision. Pinned Affliction/
Fire Mage and Assassination Rogue strategies prioritize missing effects and mature
or expiring resources. The continuation connects native effect conflict/rank/tick
checks and rune builder/finisher selection. Aion zero-rune damage is positive;
therefore a weak native fallback is retained when no builder is available.
This is a correction of the original behavioral purpose, not a copy of WoW energy.
It affects skill use, MP economy and effect retention.

The earlier General DPS target adapter had left caster/combo variants disconnected.
The continuation adds original lifetime intervals/current-target stability and
reachable native rune preference at the original group-size boundary. Existing
explicit assist, native hate, tank protection and admitted enemy set remain.
Native markers/focus lists and complete class strategies still require work.

Unchanged pinned `ChooseTravelTargetAction.cpp/.h` provides group-first destination,
active objective, progress/retry and reporting behavior. Aion now connects this to
approved nearby native turn-in/conversation/object/hunt objectives. Owner leash and
explicit mission precedence are intentional companion-policy constraints, not
original-port mistakes; they are not evaluated against upstream roaming defaults.
Full world travel/new-quest/service destination graphs remain incomplete.
Quest handlers still own progress; no new blanket completion bypass is introduced.

Current receipts `181655-178651`/`182147-699768`/`183335-252998` were installed stopped. Source
and effective package checks pass; actual combat/travel/quest behavior remains
pending. Native delayed Kromede traps are a separate Aion encounter continuation.
Details: `PLAYERBOTS_STRATEGY_CONTINUATION_20261004.md`.

## Reverification tracker — 4 October 2026

This is the canonical continuation backlog. Consult it before the next port,
update the existing IDs rather than replacing this review with another summary,
and retain unresolved findings across installations. The initial pass documented
defects without gameplay changes; the correction record below now tracks the
installed PB-PORT-001/002. GameServer remains stopped by
the user's instruction; no live attach or client replacement occurred.

### Evidence and scope

- Original: the **51 cached primary-source references**, including class triggers,
  strategy files, engine, factories, target values, quest actions and destination
  planner, at `037c01418b5d01506917a3db9b44fd56ac5f965c`. Every hash in
  `third-party/playerbots/SHA256SUMS` was recomputed and matched. This is a review
  of that pinned implementation, not a claim about the newest upstream release
  or a complete audit of uncached upstream subsystems.
- Installed disk baseline at the initial review, before corrections: cumulative receipt
  `backups/playerbots-recruitment-20261004-183335-252998`, relative to deployment.
  Receipt file/base hashes and first-on-classpath override order match.
  Override SHA-256 at review:
  `6182a97b9787bd83aa5fdfcac9d9ddc047ee5db3ecf94e16d68e89724300d6f2`.
  `audit_port_state.py` verified **76 effective methods, including associated
  lambdas**, across session/cast,
  offense, old class-combat gates, quest executors/routes and engine against
  compiled snapshot `target/playerbots-validation/check-caca4c7a687e467d994018c149414d51/classes`.
  These are disk-method checks, not claims about an attached running JVM.
  Two old hunt lambdas are retained schema members absent from the current
  source snapshot; the active `Quests.hunt` delegates to PartyBehavior instead.
  They are recorded separately, not counted as current behavior or new defects.
- Six scalar cases executed directly against the installed helpers reproduce
  four positive-priority rune decisions vetoed by the old class gate, with two
  accepted controls. No native actors, character database or ID reservation were
  involved. The DoT and quest findings below are source/control-flow findings
  confirmed in inspected installed methods, not native cast/quest acceptance.
- Evidence: `target/playerbots-port-reaudit-20261004/report.json`, per-class
  `*.javap.txt`, `gate-output.txt` and `PortGateCheck.java`. Reproduce with:

  ```powershell
  python client-mods/playerbots/audit_port_state.py --classes target/playerbots-validation/check-caca4c7a687e467d994018c149414d51/classes --output target/playerbots-port-reaudit-20261004
  ```

- Refreshed `INSTALLED_MODS.json`: 15 preservation checks, 31 client hashes,
  70 client receipts and 69 server receipts. Existing installed files are the
  baseline; this audit did not replace them.

The original behavioral contract is the criterion. Recruitment restrictions,
owner leashes, completion witnesses/mirroring, Yes/No catch-up, opt-in spending,
Temporary tiers, saved parties and alt isolation are intentional local policies,
not defects because they differ from WoW. Preserve native Aion skills, hate,
effects, quest handlers, movement, persistence and item custody. Never transplant
WoW spell IDs, talent trees, aura semantics, opcodes or bot population rules.

### Current coverage correction

| Area | Current finding | Classification / next item |
| --- | --- | --- |
| Engine, state, action queue | Installed session composes named state strategies/defaults and native threat multiplier; engine applies weights at evaluation and freshly resolves bounded continuers/parent intentions. | PB-PORT-004 installed/live fixture verified; actual native chain combat/client acceptance PB-VAL-009 pending. Full chat strategy editing is PB-SCOPE-011. |
| Offense and class skills | Native classifier, chains, runes, periodic helper, purge/interrupt/defense and animation locks connected. PB-PORT-001/002 final gates corrected in source/on disk; native casts pending. | Final-action offline checks pass; native/client acceptance remains PB-VAL-001. Broader class strategy coverage PB-PORT-005. |
| Healing/cleanse/resurrection | Actual affected-target group/pet scoring, hybrid heals/cleanse, emergency reservations and spell-specific combat resurrection selection exist. Healing Wind native cast passed historically. | Correct native adaptation; focus lists incomplete PB-PORT-006. Actual resurrection/revival acceptance remains validation work. |
| Target selection/assist | General/caster/combo values, range/health/lifetime, owner assist and other-tank weak-hate protection connected. | Icon/focus and dedicated AoE target values incomplete PB-PORT-006. Native main/off-tank coordination PB-PORT-007. |
| Following/formation/flight | Continuous native movement, speed packets and owner-centred Circle/Box/Line/Spread modes exist. | Formation modes are **not missing**. Geometry differs intentionally; real movement acceptance PB-VAL-005. |
| Navigation/positioning | Local collision probes, breadcrumbs, reach/flank/kite, flight and hazards exist. No full world destination graph. | Local native adaptation; global planning PB-PORT-011; special adapters PB-PORT-012. |
| Quests, NPCs, object loot | Shared local objective and exact actor/point consumed by conversations, objects, NPC turn-in jobs and new hunt initiation; follower-specific native eligibility, pause/retry and invalidation gates installed. | PB-PORT-003 installed/offline verified; full native tick/geodata/client acceptance PB-VAL-002 pending. Unknown branching dialogue remains explicit. |
| Loot, equipment, care | Native rights/rolls, armor/weapon/mastery limits, class/role scoring, upgrade thresholds and opt-in protected native care exist. | No current evidence that every class receives cloth. Broader item-use/economy policy PB-PORT-009; builds PB-PORT-010. |
| Learning/generated factory | Temporary-only native skill/Stigma/gear maintenance and tiers exist; Stigma selection is role-scored. | Native Stigmas are the appropriate adaptation; coherent optimized class builds remain PB-PORT-010. |
| Party, commands, persistence | Owned leases, groups, orders, summon, saved bots/mixed presets and archive controls connected. Engine composition is installed. | Full vocabulary/editor/persistent strategy choices PB-SCOPE-011; native world invites/control PB-SCOPE-002. Engine work does not provide those features. |
| Maps and death | Native whole-party transfer/recovery, revival delay and native skill/rebirth handling exist. | Five-bot native transfer fixture passed historically. Real dungeon entry and corrected revival/supplies/removal checks are acceptance gaps, not proof these adapters are absent. |
| Pets/buffs/debuffs | Learned native summons/orders, legal friendly pet heals, stacks, purge and secondary interrupts exist. | Assignment/refresh/resource/pet coordination partial PB-PORT-008; neither resistance nor native purge is WoW Cloak/Spellsteal. |

### Ordered implementation backlog

Status meanings: **OPEN — defect** has a specific contradictory path;
**OPEN — partial** has a connected subset and a missing original purpose;
**SOURCE GATE** requires further exact pinned source/native-contract review before
claiming a specific port requirement. **PENDING VALIDATION** is implemented and
must not be described as missing. **INSTALLED — offline verified; native pending**
has passed source/final-action/package checks and is on disk, but is not fully
accepted or closed.

| Order / ID | Status | Next concrete work | Dependencies |
| --- | --- | --- | --- |
| PB-PORT-005B | INSTALLED - normal Maven output; gameplay pending | Confirmed missing Spiritmaster restorative single-target/DoT/native chain ordering; existing pet/utility adapters kept. | PB-VAL-012; next class review 005C Cleric, check existing support first. See PLAYERBOTS_SPIRITMASTER_20261008.md. |
| PB-BUILD-001 | INSTALLED - full source/build/runtime checked | Complete normal builder JARs replace cumulative override; unfinished outfit source gated off. | Future delivery uses source -> builder -> copy; preserve native mods/settings. |
| PB-REPAIR-ENGINE-002 | INSTALLED - source/effective verified; gameplay pending | Completed ITEM_USE tasks no longer fabricate movement and fail stationary casts; native selective item cancellation and active-task gates. Receipt 201132-984909. | PB-VAL-009 reported ranged casters; PB-PORT-005B remains independent. See PLAYERBOTS_ITEM_TASK_CASTING_20261008.md. |
| PB-REPAIR-ENGINE-001 | INSTALLED - source/effective verified; gameplay pending | Corrected stale mover/cast race, repeated order cancellation and unchanged-preference continuer loss in core execution. | PB-VAL-009; PB-PORT-005B independent next class slice. See PLAYERBOTS_CAST_EXECUTION_20261008.md. |
| PB-SCOPE-007A-R1 | INSTALLED - offline verified; gameplay pending | Original Steel Rake tower/staircase/amplifier/root/pull-blast and gunner-pause behaviors implemented; original comments retained. Exact retail capture evidence unavailable. | Existing encounter/hazard/role engine; 479 isolated checks. PB-PORT-005B remains independent next class slice. See PLAYERBOTS_STEEL_RAKE_20261007.md. |
| 1 · PB-PORT-001 | INSTALLED — offline verified; native pending | Native finisher cast/MP/consumption acceptance. Final resource gate is corrected. | PB-VAL-001; actual cast acceptance still outstanding. |
| 2 · PB-PORT-002 | INSTALLED — offline verified; native pending | Native refresh/hybrid/stronger-effect cast acceptance. Final DAMAGE veto is corrected. | PB-VAL-001; actual cast acceptance still outstanding. |
| 3 · PB-PORT-003 | INSTALLED — offline verified; native pending | Validate complete native tick/geodata/interactions through the committed objective; local executor gates are connected. | PB-VAL-002; preserve native handlers/witnesses/loot and no quest progress bypass. |
| 4 · PB-PORT-004 | INSTALLED — offline/loaded-engine verified; client combat pending | State strategies/defaults, native threat policy, weighted expansion and freshly resolved continuers installed in 213711-193912. Validate actual native chain casts/transitions. | Latest position receipt 211935-035574 retained; PB-VAL-009. Next implementation 005. |
| 5 · PB-PORT-005 | OPEN — partial; 005A/005B installed | Sorcerer ONLYONE chain/upkeep/filler/MP/boost strategy installed `234841-695964`; native/client pending. 005B confirmed missing Spiritmaster single-target/recovery ordering installed from normal source build; existing pets preserved. Next 005C Cleric review; broader class coverage remains open. | PB-PORT-004 installed; cached GenericWarlock/AfflictionWarlock mapping and native pet gates for 005B; necessary pet coordination PB-PORT-008. |
| 6 · PB-PORT-006 | OPEN — partial; marker SOURCE GATE | Focus-heal/attack controls and distinct AoE target policy; verify native marker contract. | PB-PORT-004/005; import RtiTargetValue dependencies at same pin. |
| 7 · PB-PORT-007 | OPEN — partial; pull SOURCE GATE | Main/off-tank responsibility, pull readiness and CC/assist coordination. | PB-PORT-004/006; exact upstream pull actions/triggers first. |
| 8 · PB-PORT-008 | OPEN — partial | Role-aware buff/debuff assignments and pet utility/recovery/resource coordination. | Class slices PB-PORT-005; native pet/effect legality. |
| 9 · PB-PORT-009 | OPEN — partial | Shared useful-item classification and native bag-pressure/service execution. | Proven custody; preserve opt-in care/owned-alt policy. |
| 10 · PB-PORT-010 | OPEN — partial | Explicit coherent Temporary class/build presets linking skills, Stigmas, weapons and gear scoring. | PB-PORT-005/009; native slot/prerequisite legality. |
| 11 · PB-PORT-011 | OPEN — partial; graph SOURCE GATE | Native world destination graph and quest/service planning beyond local fallback. | PB-PORT-003/009; pinned TravelMgr/actions review before design. |
| 12 · PB-PORT-012 | SOURCE GATE / partial | Review special positioning/transport/escape/corpse-travel and unsupported skill adapters. | Exact upstream files and an actual Aion mechanic per adapter. |
| PB-SCOPE-012A | INSTALLED — offline/native import verified; gameplay pending | Care/gear metadata uses native DB checkpoint; 32 imported rows checked read-only. Next class implementation remains 005B. | PB-VAL-011 user checkpoint/retry/dismiss/resummon acceptance; other repository namespaces separate. |
| PB-SCOPE-012A-R1 | OPEN — creation-path gap; legacy retirement complete | Temporary.persistCreation still writes gear provenance files; migrate initialization checkpoint before claiming all care/gear paths file-free. User-authorized one-time retirement removed 32 unused migrated files with native DB/read-write exclusion guards; other files/JAR preserved. | Native creation inventory/provenance transaction; no owned-alt changes. See PLAYERBOTS_METADATA_20261007.md. |

The following details preserve original findings and the acceptance contract.
001/002/003 implementation and offline/disk stages are complete; acceptance remains
open. 004 source/package/install and loaded-engine work is complete; continue with
005's reviewed first native class slice. Do not bypass final
integration checks by adding more helper-only coverage. Later
source-gated items may be researched offline, but their production behavior must
not be invented from names or attributed to unreviewed WoW code.

**PB-PORT-001 — contradictory rune gate.** Original
`Ai/Class/Rogue/Strategy/AssassinationRogueStrategy.cpp::InitTriggers` prioritizes
four-point and nearly-dead finishers (25/24). Native
`PlayerBotOffense.finisher/routine/useful` adapts rune caps, builders and expiry,
including Aion's legal positive zero-rune damage. However
`PlayerBotSession.CastAction.isUseful` subsequently calls
`PlayerBotClassCombat.useful/shouldBurst`, which demands positive runes and either
the native maximum, HP <25%, or a fixed <=2s expiry. Installed cases:

| Native input (cap 5, healthy unless specified) | Offense priority | Old final gate |
| --- | --- | --- |
| 0 runes, no usable builder | 8 | Reject |
| 4 runes, builder available | 25 | Reject |
| 2 runes, no usable builder | 12 | Reject |
| 2 runes, 3s remaining, 2s cast commitment | 24 | Reject |
| 5 runes | 25 | Accept |
| 2 runes, target HP 24% | 24 | Accept |

The zero-rune rule is an Aion adaptation; an old gate blocking it is a wiring
mistake, not an upstream behavior to restore. Use one consistent resource-aware
decision for priority/usefulness while retaining native start/weapon/resource/
target conditions. Affects Assassin damage, MP economy and class build usefulness.
Close only after **final CastAction** checks and native casts cover all six cases,
builder suppression, ownership/caps, expiry during casting, MP payment and rune
consumption. Planning must not mutate MP/effects; player-owned builds must remain
unchanged. Existing 978 offense checks do not exercise this final veto.

**PB-PORT-002 — blanket active-effect veto.** Original Fire Mage/Affliction
strategy effect/proc triggers make offensive usefulness spell-specific, not a
universal ban on attacks with an existing debuff. The intended Aion last-tick
refresh policy is not a claim that WoW refreshes every DoT identically.
`PlayerBotOffense.refresh/useful` permits final native tick+cast-window refresh
and preserves direct damage of hybrid attacks, but final `CastAction.isUseful`
also requires `!hasActualBuff(recipient, actual)` for every DAMAGE skill.
`hasActualBuff` rejects matching skill IDs or non-NONE stacks regardless of expiry
or instant payload. Thus same-ID/stack DoT refresh and hybrid direct damage are
still blocked. Affects damage, debuff uptime and follow-up strategy selection.
Make offensive effect checks agree without allowing stronger effects to be
downgraded, pointless early recasts or redundant pure debuffs. Close after final
session/native cases for absent/early/final-window effects, stronger ranks,
conflicts, hybrid direct damage, nearly-dead targets and area/MP protections.

**PB-PORT-003 — destination is not execution arbitration.** Original
`ChooseTravelTargetAction::SetGroupTarget` checks an active non-group-copy
destination valid for the follower and copies **destination plus point**;
`SetCurrentTarget` validates current destination/retries;
`setNewTarget` updates the active target and clears competing RPG/pull targets.
At the reverification baseline (before the correction below), `Session.tick` performed conversation/object early returns and selected the
nearest quest NPC job and admits quest hunts before invoking `QuestRoutes.trigger`
only with no job/mission/combat and a stopped owner. The planner is reachable as
idle travel, but actual executors do not consume its Goal. Its group preference
matches only quest ID, not the same stage/NPC/point; cached peer goals are not
validated for their current world/stage first. Known-list membership is labelled
visible without `owner.sees`, and cached points do not track a moving actor.
Distance-only 12s timeout/60s exclusion conflates arrival/interaction wait with
navigation failure. These are incomplete translations of active group objectives;
the owner's leash/approval rules remain intentional.

Connect a validated shared objective (quest stage, kind, native actor/point,
world/instance) to existing executors. Validate each follower independently;
keep explicit missions and native safety gates authoritative. Separate progress,
arrival, handler retry and genuine stuck backoff; announce the executed/committed
destination. Keep conversation destinations within the existing owner 25m gate
(current planner leash 24m). Close after final tick fixtures and native handlers
prove two bots on the same stage converge, different stages do not blindly copy,
NPC/object/hunt/turn-in actions use the chosen goal, hidden/moved/despawned actors
and instance changes invalidate correctly, and rejected dialogue does not create
false progress or endless notices. Affects quest acceptance, intermediate stages,
object loot, turn-in, hunting, navigation and reporting.

**PB-PORT-004 — engine strategy contract.** Original `Engine::DoNextAction`
multiplies each evaluated action's relevance; successful actions enqueue
continuers. `Strategy.h`/engine registry build state-specific strategies/defaults.
Aion engine applies positive weights to seeds but only suppression to expanded
prerequisites/alternatives, has no continuers, and Session submits one strategy
with `List.of()` multipliers. Positive expanded weighting is a **dormant defect**,
not a demonstrated current production failure. Add a bounded, revalidated
continuation/state-composition contract before relying on it for class chains.
Preserve snapshot lifetime, stable ties and failed-movement cast protection.
Close with tests for weighted seeds and expansions, suppression, successful
continuers, cycles/budget, strategy enable/disable and combat/death/map changes,
then final session class selection. Do not retain stale native actor references.

The paragraph above records the original finding/closure criteria. The correction
is now installed in `213711-193912`; see `PLAYERBOTS_ENGINE_COMPOSITION_20261004.md`.
State composition/defaults/native threat multiplier, evaluated expansion and
freshly resolved bounded continuers/parent intentions are connected. Forty-nine
behavior/native-metadata tests pass against the package and loaded runtime; all
five naturally scheduled session contexts are observed. PB-VAL-009 still needs
actual native chain casts/client combat and transition acceptance.

**PB-PORT-005 — complete class strategies.** Cached Generic/Frost/Fire Mage,
Rogue, Hunter, Warlock, Shaman and Warrior strategies/triggers express distinct
filler, movement, proc, debuff, execute and resource decisions. Examples: Fire
defaults 5.3/5.2/5.1/5, Scorch 19, Living Bomb 18.5, Hot Streak Pyroblast 25;
Affliction attacker DoTs 19.5/19 versus primary 18/17.5, proc 16, execute 15.5,
Life Tap glyph 29.5 versus fallback 5.1. Aion has selected utility/defense bands,
generic fitness, follow-ups 23 and shared periodic family bands; that does not
provide each native class/build's complete strategy. First publish a per-native-
class matrix of learned skill families, prerequisites, real resource/proc signals,
targets, fallback and unsupported cells. Implement/test one coherent slice at a
time. WoW energy/shards/talents are not Aion resources, and missing native skills
must not be invented or granted to alts. Each slice needs final action traces,
real cast/MP/cooldown/chain/effect evidence, empty-resource and moving-target
fallbacks, plus party coordination. No template count closes a class.

**PB-PORT-006 — focused targets and AoE values.** Original
`PartyMemberToHeal::Calculate` honors an enabled focus-heal list before normal
triage. `DpsTargetValue::Calculate` and `DpsAoeTargetValue::Calculate` first consult
RtiTargetValue; AoE fallback selects highest health and avoids group icon 4.
Aion has native affected-target healing and general/caster/combo values but no
equivalent focus list or distinct AoE target value. Marker dependencies are not
cached/reviewed and the native marker command/ownership contract is not yet
identified: import/review it at the same pin before promising UI/packet parity.
Preserve emergency/native spell legality and explicit owner orders. Close with
focus enabled/disabled/out-of-range cases, urgent exceptions documented from the
original source, valid/invalid marker targets, CC protection and separate safe
single/AoE target traces. WoW icon numbers are not native Aion IDs.

**PB-PORT-007 — tank/pull group coordination.** Cached TankTargetValue,
TankWarrior and Tank/DPS assist strategies already underpin native hate pickup,
opening threat and short DPS suppression. Aion lacks a full main/off-tank
assignment and explicit readiness/pull/CC action lifecycle. This is partial
coverage; detailed original pull timing/strategy dependencies are **not yet
reviewed in the cached set**. Obtain those primary files first, then adapt
readiness to native heal resources, range, controlled enemies and hate. Close
with two tanks avoiding assignment churn, a target escaping to healer/pet,
resource-unready healer, interrupted/failed pull, CC-safe DPS assistance and
native pack testing. Never infer exact WoW threat math for Aion hate.

**PB-PORT-008 — support, debuffs and pets.** Generic class references separate
class support, control, pet utility and resource decisions. Aion implements
native buff slots/stacks, learned pet modes/orders, group/pet healing and engaged
secondary interrupts/purges; generic combat-buff rules and out-of-combat summon
eligibility are not a complete coordinated pet/class strategy. Review exact
pet/buff actions and trigger dependencies before expanding them. Add effect-
specific assignment/refresh, legal pet recovery and utility reservations, then
native resource/proc integration. Close with duplicate prevention, stronger-effect
retention, pet death/map recovery, actual pet utility casts and party support
selection. Native purge is removal, not Spellsteal; resistance is not cleansing.

**PB-PORT-009 — useful inventory and services.** `ItemUsageValue` distinguishes
equip/use/keep/quest/disenchant/ammo/AH/vendor categories and defers some master
quest items; `EquipAction`/`BuyAction` use that classification. Aion already uses
native loot rights, role scoring, gear provenance, future/quest/high-value
protection and opt-in purchases/extraction/enchanting, but lacks a unified
classification shared by broader bag-pressure/service strategies. Build that
classification first; then native service destinations/actions with verified
stock, capacity, budgets and custody. Do not add unsupported WoW ammo/professions
or silently activate trading/selling/banking. Close with full bag, conflicting
item owner, quest/future upgrade, generated extraction protection, foreign/shared
storage exclusion and native transaction/persistence tests; prove planning is
read-only. Touching owned-alt builds remains prohibited.

**PB-PORT-010 — coherent factory builds.** Original factories configure skills,
talents/spec and scored equipment/enchants together. Temporary/BuildRules already
uses native skills/Stigma legality and class/role armor/weapons; Stigmas are the
correct equivalent, but `PlayerBotTemporary.stigmaScore` is role/kind scoring,
not a versioned synergistic class build. Introduce explicit legal native build
profiles consumed by skills/Stigmas/weapon/stat choices and strategies. Preserve
current tier/provenance policy; this review does not challenge ten-level upgrades.
Close with representative native class/role profiles, skill/Stigma prerequisites,
weapon/armor legality, level/tier maintenance, saved/resummoned identity and
unchanged owned-alt fingerprints. Do not reset earned progress or replace alt
setups merely to obtain a better strategy score.

**PB-PORT-011 — world quest/service planning.** ChooseTravelTargetAction orders
valid group/current objectives, quest start/work/end and service destinations.
Current QuestRoutes only finds approved local active objectives/known hunt actors
and static non-temporary open-world interaction hints. Full native destination
graph, transition costs, service/new-quest planning and blocked-route recovery
are absent. The graph/TravelMgr dependencies require exact pinned review before
implementation. First finish local arbitration PB-PORT-003; then use verified
Aion spawns, handler stages, transport/instance entry and native visibility,
never generic quest text or WoW map coordinates. Close with reachable/unreachable
multi-map objectives, quest prerequisite/branch changes, native transition costs,
party coordination and no stale actor references. Owner leashes stay in force
unless the user explicitly changes that policy. Upstream's PvP activation is
false and mail travel is commented out in this pin; neither is a missing active
behavior to implement from this file.

**PB-PORT-012 — special adapters and source boundary.** Follow/MovementActions
refer to transport/navigation mechanics; AcceptResurrectAction proves native
resurrection acceptance, not a full corpse-route port. Aion local navigation and
recovery are installed; unsupported skills explicitly include traps/totems,
shapechange, teleport/gates, nonvisible party targets and charged POINT releases.
Review each relevant original strategy/action and actual Aion mechanic separately
before adding an adapter. WoW boats/zeppelins, corpse release, charms and talent
mobility have no universal native counterpart. Record N/A with evidence where
appropriate, and test remaining native escape/ground-target/path/recovery actions
through real geometry and death transitions. Broad Aion dungeon strategies and
PvP/alliance support remain separate implementation scopes, not defects inferred
from these reference excerpts or a reason to mark this focused audit complete.

### Pending validation register (not missing implementation)

| ID | Evidence needed before closure | Current boundary |
| --- | --- | --- |
| PB-VAL-001 | Native rune/DoT casts, effect/MP/consumption and party selection | 44 final CastAction/native planning checks pass. Actual native/client cast acceptance remains outstanding; engine fixtures do not close it. |
| PB-VAL-002 | Native quest stages and real geodata through the committed group objective | PB-PORT-003 local gates installed/offline verified; complete tick, native object/NPC/kill/reward execution and real-client acceptance pending. |
| PB-VAL-003 | Corrected `PlayerBotRevivalCheckAgent2` final post-revive Healing Light casting and client rebirth appearance | Corrected native/client check remains outstanding; older party-less Healing Wind fixture is not success. |
| PB-VAL-004 | Supplies/recovery revision 3 MP/buff checks and roster save/dismiss/remove behavior | Actual native lifecycle checks remain outstanding; browser/archive/engine checks do not close them. |
| PB-VAL-005 | Smooth ground/flight travel, all four formations, regrouping, corners, combat positioning, doors and summon controls | Latest summon/distance recall installed offline `195139-913545`; early 60m recall, bot activity cancellation and FOLLOW reset; 679 new checks, 314 formation/6 speed-packet/57 floor/17 trail regressions pass. NAV-002/003 retained. | User actual rendered movement, whole-party formation and summon/dungeon acceptance pending. |
| PB-VAL-006 | Entire five-bot party entering/returning from real dungeons with state/build preserved | Prior native transfer fixture passed; real dungeon transitions still pending. |
| PB-VAL-007 | Learned utility/defense/heal/pet casts and delayed Kromede trap avoidance in a client party encounter | Historical isolated casts passed for named skills; full class/dungeon acceptance absent. |
| PB-VAL-008 | Temporary maintenance, save/preset/remove/relogin, untouched owned-alt fingerprints, native pet/menu coexistence | Historical selected fixtures pass; complete actual-client regression remains pending. |
| PB-VAL-009 | Actual native chain/proc/MP/animation execution, emergency preemption, strategy changes and death/map/party/order transitions in a client encounter | PB-PORT-004 installed, 49 loaded-engine fixtures and five actual scheduled contexts pass; actual client combat/transition acceptance pending. |
| PB-VAL-010 | Owner-to-bot native item/Kinah exchange and equip/bind persistence | Installed; 56 final production checks pass. User gameplay acceptance pending. |
| PB-VAL-011 | Native metadata checkpoint rollback/retry, care/gear/preferences/behavior/spacing/formation/reward witnesses/supply/trade saves, dismissal/resummon/restart persistence | PB-SCOPE-012A/B/C installed offline; 84 property imports plus six account/archive documents verified separately read-only; 35 metadata/31 preference/24 repository/10 creation checks pass. User gameplay acceptance pending. |

No validation above authorizes a restart. Historically on 4 October, GameServer
was running after the separately approved position repair; “Finish it” authorized
that reviewed PB-PORT-004 live update. Current 7 October policy keeps server/client
off. Do not infer future startup/shutdown authorization. Older startup-wait notes remain historical
validation boundaries, not current process state. Keep the **item-ID release investigation**
open independently (`PLAYERBOTS_STRATEGY_CONTINUATION_20261004.md`): the unsafe
retired fixtures were identified, but every collision/release path is not proven
resolved. Use preset fixture revision 5; never run old compiled fixtures or
release IDs while native inventory rows remain.

### PB-PORT-001/002 correction record

Installed stopped in cumulative receipt
`backups/playerbots-recruitment-20261004-193448-980138`. Only two existing methods
changed: `PlayerBotOffense.useful` and `PlayerBotSession$CastAction.isUseful`.
All 114 other cumulative entries are byte-identical; no class/schema additions,
cached enum changes, base JAR, launcher, command, media or client replacements.

Finisher usefulness now uses the same `finisher` decision as priority, with native
rune cap/stack, available learned builders, health and cast-aware expiry. The
final action no longer calls the old ClassCombat veto. That helper and scalar
method are retained as unused installed members; their old scalar results are
historical evidence, not current session behavior. Aion SignetBurst looks up the
matching target stack without WoW per-caster combo ownership; this correction
does not invent a new ownership restriction.

Periodic DAMAGE actions defer expiry/rank usefulness to Offense instead of the
blanket same-buff veto. Pure DoTs cannot refresh early or monopolize nearly-dead
targets. Hybrid direct damage can execute on an ongoing same-rank periodic
effect; a matching stronger rank/learned level blocks the cast to protect that
effect. Nonperiodic duplicate-debuff checks, support, utility, reservations,
native conditions/resources, cooldown, chain, area and animation safeguards stay.
Pet-order strategy expansion remains PB-PORT-008; not every pet/proc adapter is
claimed corrected.

`PlayerBotOffenseIntegrationCheck` reproduced both failures on the prior installed
JAR, then passed **44 checks through actual CastAction.isUseful/isPossible** with
world-free observed fixtures and native planning. Cases cover the six rune inputs,
builder suppression, unrelated/capped stacks, absent/early/same-ID/same-stack
refresh, stronger rank/level, native conflict ID, low-health pure/hybrid behavior,
nonperiodic duplicates, casting/dead/despawned targets, cooldown, missing chains,
area off and insufficient native MP. Planning retains effects and mana. No native
damage cast or consumption is claimed. Full source/companion suite and the
effective package's 978 helper/template offense checks also pass.

Evidence: `target/playerbots-offense-gates-full-source-validation.txt`,
`target/playerbots-offense-gates-effective-validation.txt`,
`target/playerbots-offense-gates-final-integration.txt`,
`target/playerbots-offense-gates-postinstall.json` and
`target/playerbots-offense-gates-postinstall-audit/report.json`.
The postinstall audit matches 51 reference hashes and 76 effective methods,
including lambdas, and records `activeLegacyRuneGate=false`. The old scalar
vetoes are not connected to the current final action.

Postinstall checks preserve 31 client hashes, 35 settings/preset/archive files,
base JAR and launcher; all 15 mod checks pass (70 client/70 server receipts).
The offline installer now stops on process-inspection errors; denied inspection
cannot be interpreted as a stopped server. Next source/install work is
**PB-PORT-004**. 001/002/003 remain open for native and actual-client acceptance.

### Required update discipline

For every continuation, record IDs addressed, pinned source/functions, native
mapping, changed source and effective installed methods, receipt/hash evidence,
tests and their limits. Move an item through source implementation, offline
integration, installed-disk verification, native runtime and client acceptance;
state which stages remain. Do not turn one passing helper or template count into
whole-system completion. Split partial class/quest work under its existing ID.
Update this tracker, `PLAYERBOTS_VALIDATION_20261004.md`, relevant subsystem docs,
`INSTALLED_MODS.md/.json` and AGENTS continuity together after an actual install.


### Shared quest arbitration installed — PB-PORT-003

Current cumulative receipt: `backups/playerbots-recruitment-20261004-203902-131350`.
`PlayerBotQuestObjectives` now commits one native objective before independent
conversation/object/NPC/hunt executors can select another destination. Copy
actual original peer destinations, validate each follower's native objective and
capture its own quest status/variables. Same quest ID alone is insufficient;
identical peer variable words are not required when both objectives remain active.
Moving/hidden actors, exact instance identity, progress, pause, handler failures
and static-hint resolution are distinct. Native turn-in jobs consume the same
actor. Native loot rights, safe tank-led pull policy, explicit missions, owner
leashes and unknown-story safeguards survive. No quest credit/rewards are fabricated.

The initial package changes 13 bounded methods and adds three helper classes;
110 other entries are byte-identical. The final one-method peer eligibility
correction preserves 118 other entries. Effective/installed checks pass: 35
production arbitration gates, actual intermediate native handler progression,
actual reward-job selection, route policy and existing Decorative Weapons/party
fixtures. Three receipt/backups, all 31 client hashes and 35 bot setting/preset/
archive files are preserved; 15 installed-mod checks pass. GameServer stayed stopped.

Details, exact source mapping and evidence:
[PLAYERBOTS_QUEST_ARBITRATION_20261004.md](PLAYERBOTS_QUEST_ARBITRATION_20261004.md).
PB-VAL-002 still needs the complete native tick, asynchronous object tasks, real
geodata, native reward delivery and client acceptance. PB-PORT-003 is not closed.
Next offline work is **PB-PORT-004**; full class/world graph/dungeon scope remains
unfinished. Keep all other backlog IDs and the item-ID release investigation open.

## PB-VAL-005 corrective continuation — tank front and ranged melee rush

The user supplied actual-client evidence of incorrect formation slots and ranged
companions entering melee. This is a correction to existing formation/combat
execution, not a claim that those strategies were absent. Tank role now receives
the front slot in all four shapes, stationary turns update heading, native range
shells replace enemy-center approach destinations and short hostile spells cannot
force ranged builds into melee. The common learned offensive band prevents rare
long-range spells stranding ordinary attacks. Support Chanters remain melee.

Installed live/disk after explicit user approval in receipts `...211220-016216`
and `...211935-035574`, retaining the complete objective/offense/earlier baseline.
Already running server was not restarted. Full source suite, 38 focused position
checks, existing offense/quest/formation checks and 22 read-only real-companion
policy/geometry checks pass; 115 effective methods match source. Inventory and
cumulative preservation checks pass. Details: `PLAYERBOTS_POSITION_20261004.md`.

**PB-VAL-005 remains pending** for actual native movement/LoS/terrain/corners,
combat spacing and client casting. Read-only live policy checks are not gameplay
acceptance. All other tracker IDs, PB-PORT-004 next offline implementation and
the persisted item-ID release-path investigation remain open.


## Current continuation — PB-PORT-004 installed

Receipt `213711-193912` supersedes earlier next-work pointers: state strategies,
defaults, evaluated expansion, native threat multiplier and bounded fresh
continuers are installed. 49 loaded-engine tests and five natural session context
checks pass; actual client combat/transitions remain PB-VAL-009. The user requested
finishing this reviewed live update; no server lifecycle/client change occurred.
All current position/mod/alt protections survive. See
`PLAYERBOTS_ENGINE_COMPOSITION_20261004.md` for native mapping and exact evidence.

The full subsystem inventory/dependency pass is complete in
`PLAYERBOTS_SUBSYSTEM_INVENTORY.md`; update it instead of restarting it.
**Next implementation: PB-PORT-005**, first complete native class strategy slice.
Independent world populations/invitations/trade retain their own dependency
tracks; full class/travel/dungeon scope and item-ID investigation remain open.

## PB-VAL-005 owner-controlled spacing continuation

Installed live/disk in `...215853-061213` and latest `...220541-625450`, preserving
engine composition `...213711-193912` and all earlier mods. RangeDps shooting was
confirmed working by the user; compact formation/combat spread and the endless
retreat/tank chase loop were the remaining request. Per-ranged-character Overview
controls persist 4 m default owner spread and 10 m default target distance with
native reach caps. Line keeps distinct inner/outer slots; one bounded retreat per
engagement replaces sustained kiting, with a five-second quiet reset. This is a
bounded combat policy, not completion of full strategies or PvP AI.

Full source/effective fixtures, native browser controls, real policy reads and
57 loaded-method comparisons pass; 135 effective methods match source. Previous
engine helpers and all installed mods/preferences survive. Details:
`PLAYERBOTS_SPACING_20261004.md`. PB-VAL-005 actual movement/terrain/pursuer combat/
client shots and PB-VAL-009 native engine/chain acceptance remain pending. Full
subsystem inventory is complete; PB-PORT-005 remains the next broader slice.
Keep every other open tracker ID and the persisted item-ID release investigation.

## Current repair status

| ID | Behavior | Status | Next verification |
| --- | --- | --- | --- |
| PB-REPAIR-SUMMON-001 / PB-CUSTOM-RECALL-001 | Cancel busy bot activity for Summon; early 60m distance recall and resume FOLLOW | Installed offline `195139-913545`; 165 recall checks, 171 earlier entries retained and native linkage passes | PB-VAL-005 actual Summon, cancellation and distance recall; owner combat still gates manual Summon |
| PB-REPAIR-PACKAGING-001 | Restore bot tick linkage and guard deployed dependencies | Installed offline `191905-805583`; exact one-hook removal, 176 entries retained; 174 classes / 24,383 references pass | PB-VAL-005 user follow retest; appearance remains source/staged only |
| PB-REPAIR-FORMATION-002 | Persistent direct follow intent, travel priority and formation-slot speed recovery | Follow regression reported; missing appearance-hook integration repaired in `191905-805583`; prior follow checks retained | PB-VAL-005 actual client movement and party geometry |
| PB-CONFIG-001 | Existing master module switch documented; enabled manual summon switch with native interaction/party safety checks | Installed offline `062930-344543`; config schema cold-load-only | User module/summon toggle and summon acceptance |
| PB-REPAIR-INV-001 | Exclude already-committed native deletion records from companion custody/save bookkeeping; preserve checks for actual pending writes | Installed live/disk `224922-627052`; 85 offline production checks, 31 loaded inventory methods match | User in-game periodic checkpoint/dismiss/resummon |
| PB-REPAIR-TANK-001 | Remove continuously recentered boss-facing walking goal; active tank does not spread its own caster's targeted area attack | Installed live/disk `232048-163778`; two methods, 134 earlier entries preserved; 22 regressions, 57 loaded method matches | PB-VAL-005 actual boss fight/terrain/hazard escape/attacks |
| PB-REPAIR-SETTINGS-001 | Short bounded retry of transient Windows care-file replacement denial, retaining atomic save and permanent-error reporting | Installed live/disk `042546-945113`; one method and new helper; existing trade/Sorcerer/tank/shield retained | User quest/settings acceptance; further tests stopped at user's request |
| PB-REPAIR-FOLLOW-001 | Previous stalled-follower teleport fallback | Rejected and removed in `171732-465446`; complete metadata baseline and incidental UI restored | Do not reinstall historical recovery packages |
| PB-REPAIR-NAV-002 | Generic opened-door collision recognition, finer ground routes, floor-following movement and reached-breadcrumb progress | Installed offline through `190312-034119`; initial failure reported by user. 17 progress/40 geometry checks pass; 156 prior entries preserved in continuation | PB-VAL-005 actual summoned-bot room movement/combat/party acceptance |
| PB-DIAG-PERF-001 | Diagnose paired 23:44:40 AI-update/HTTP stalls | AI tick lambda confirmed; later 45-second profile has no recurrence, long companion-lock wait or large GC pause; no gameplay change | Original root cause/activity context and recurrence capture; preserve current 005A installation |

This repairs Aion transaction integration, not an upstream strategy feature.
Native source is `InventoryDAO.storeCompanionInventory` plus storage deletion
queues and `Player.getDirtyItemsToUpdate`; no new WoW-specific algorithm is
claimed. Recycled IDs in obsolete UPDATED queue entries were blocking four
companions' checkpoints. One method changed; all earlier mods/alt setups remain.
See `PLAYERBOTS_CUSTODY_DIAGNOSIS_20261004.md`. The broader release-path
investigation and separate Tanku recovery rejection remain open. The next port
slice stays **PB-PORT-005**, not another inventory/re-audit pass. Native/client
acceptance remains separate and the user will test this repair.

Tank movement is a separate evidenced adapter repair, not closure of PB-PORT-007
pull/main-offtank coordination. `PLAYERBOTS_TANK_POSITION_20261004.md` records
the exact upstream/native mapping and concrete staged package. Automatic review
initially rejected the live tank deployment because prior approval was specific
to another repair. The user then explicitly approved this tank update; fresh agent
44 installed it without a restart. All 44 settings, 31 client hashes and 15 mod
checks pass. The 51-source/140-method audit passes; actual boss combat acceptance
remains pending. PB-PORT-005 remains the next broad port.

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

## PB-CUSTOM-APPEARANCE-001 companion transmog — staged 6 October 2026

This is a requested Aion feature extension, not an upstream parity claim. It maps
the existing `WardrobeRules` compatibility/race/gender/expiry checks and native
`Item.setItemSkinTemplate`, `SM_INVENTORY_UPDATE_ITEM`, and
`SM_UPDATE_PLAYER_APPEARANCE` flow to owned companion gear. An appearance item
stays in the companion cube while its compatible combat item retains all stats.
Per-character appearance selections persist separately and reapply to replacement
gear in the same slot. The panel offers restore-original-look, and costume-only
items cannot be auto-equipped as stat gear.

Source/UI compile and incremental staging completed in
`target/playerbots-appearance/package-reviewed-20261007`; this corrected package
supersedes package-v2 and preserves the current
cumulative override/launcher chain and deployed client baseline. This remains
**source/staged only** under the user's no-live instruction; no live attach,
deployed-file replacement, or restart occurred. Native appearance broadcast and
persistence after restart need gameplay acceptance. PB-PORT-005B Spiritmaster remains the next upstream implementation;
other Playerbots parity and validation IDs remain open.

Review correction (7 October): use the native item-skin setter's NEW/UPDATE_REQUIRED
persistence semantics; do not mark pending changes UPDATED. Retain stat-bearing
clothing, exclude only statless costumes from auto-gear, reject expiring source
instances, and share panel/action target eligibility. Full Commons/GameServer
compile and 16 effective-package appearance checks pass. The package retains 144
earlier cumulative entries byte-identically, changes five existing methods in
three classes and adds two helper classes. No deployment occurred.

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
