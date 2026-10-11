# Companion update validation — 4 October 2026

## Weapon visibility and shared height traversal - installed offline - 11 October 2026

PB-REPAIR-WEAPON-039 / PB-REPAIR-HEIGHT-040 is installed through unchanged complete
normal Maven output. All native classes use native drawn-weapon state/emotion
before bot attack/cast. Quiet idle restores neutral only after casting/combat,
AI action and native animation deadlines expire, including manual Follow reset.
Equipment, active weapon sets, skins and other state flags remain.

A separate supported jump/drop path supplements strict walking for companions
and independent bots: native floor/body/door/landing checks, bounded actual speed,
native JUMP/movement/FALL packets and fall damage. Death/despawn/transfer clean
transient intent; interrupted motion retains gravity. Controlled Follow/PASSIVE
height regrouping also runs between native actions in combat, above ordinary
offense and below emergency support/encounter safety. Stay/Guard/missions and
independent goals remain. Local limits are 1.6m rise/12m descent/8m horizontal;
this is an Aion host adapter, not a full navmesh or exact client physics claim.
No coordinate exceptions, teleport escape, equipment reroll, SQL migration or
settings changes were introduced. Original ground clear method remains intact;
a separate wrapper preserves installed compatibility and all delivery guards.

Receipt: external archives/server/game-server/backups/
playerbots-source-build-20261011-052341-513574. Review: external staging/output/
release-20261011-051631-963923. Installed GameServer SHA:
fa2a7ca7da8b6ff3ffc32038d691aa2650c3b0add2c0fdf1aa4040e4e40da79e.
105 fixture sources / 68 artifact-bound suites /64,811 checks / 28 tooling tests,
169 weapon/84 height checks, 1,757 warning-free handlers and 43 exact native-browser
actions/33 screenshots pass. Full XML/schema/JAXB and 3,388-class/185,295-reference
linkage pass; 3,636 prior JAR entries remain identical. All 56 original/82 scope SHA
hashes and 81 declared Git blobs verify. Prior 045911/050516/050901/051532 candidates
were superseded or rejected without installation; final complete evidence alone
was delivered. The instant-animation failure reproduces on the first candidate.

One complete normal GS JAR copied unchanged; Commons/static resources/configs/
client remain. Zero generated caches present to invalidate. Independent installed
verification passes exact builder/source/recovery/static/linkage/regression/browser
evidence and 71 guards/93 configs/30 protected client hashes. Inventory passes 20
checks/74 client/159 historical server receipts; root system.cfg is informational
and untouched. All 43 settings retain worldcap1500,target1200..1500,budget120,
controlledcap3000,personal23/23,city0,appearance disabled. SQL IDs/progress/builds
and all preceding mods remain. Fresh closed-component guards passed; no agent
component lifecycle/attach/dump/gameplay/production SQL or native World/ID fixture
occurred. Login/Chat untouched. PB-VAL-060 weapon visibility and 061 height/ledge
acceptance remain user gameplay. Full quest audit and wider parity remain unfinished
engineering; every route, photographed stall and boss timing is not claimed solved.
See docs/PLAYERBOTS_WEAPON_STANCE_20261011.md and
 docs/PLAYERBOTS_HEIGHT_TRAVERSAL_20261011.md.

## Coordinated allied support/recovery and manual Follow - installed offline - 11 October 2026

PB-SCOPE-SUPPORT-037 / PB-CUSTOM-FOLLOW-038 is installed through unchanged complete
normal Maven output. Both independent bots and companions can select nearby legal
allied humans/bots for learned healing, cleansing, mana, buffs, protection and
resurrection; native party-only spells retain real membership. Native admission
and effect-time rechecks use the same friendly eligibility. Enemy/loot/custody,
PvP and class/resource/cooldown restrictions remain. Recovery uses useful native
self-heals/mana restoration through95/95, retaining transient intent while standing
to cast; sitting/native regeneration remains fallback. Original revive/recall code
remains identical. Resting ignores stale pull/combat classification; observed urgent
deadlines avoid retroactive idle backlog while genuine queue/stall warnings remain.

The existing Follow button and .bot follow explicitly interrupt combat/casts,
recovery, items, loot and missions even if FOLLOW was already selected. Owned spirits
withdraw too. Normal native formation/geometry, hazards, immobilization, exchange
commit custody, transport, resurrection and map transfer remain. Regrouping resumes
normal activity after safe stationary arrival out of combat; Attack/another order
or a new mission can resume earlier. No new client button or persistent state is
introduced. Both chats coordinated shared source and one cumulative delivery.

Receipt: playerbots-source-build-20261011-043735-264032; review:
release-20261011-042743-973271. GameServer SHA:
b35bbbde73b602cb2d3beb4f17ac5f66d7971064ad1aa7e509e9ac0ec021f678.
103 fixture sources /66 artifact-bound suites /64,558 checks /28 tooling tests,
1,757 warning-free handlers,43 exact native-browser actions/33 screenshots and full
XML/schema/JAXB pass. Linkage:3,383 classes/184,619 references;3,633 prior JAR entries
remain identical. All56 original/82 scope SHA hashes and81 declared Git blobs verify.
Only the complete normal GS JAR and unchanged builder Bot.java handler were copied;
Commons/static resources/configs/client remain.2,044 generated caches backed up and
invalidated. Independent installed checks from both chats pass exact builder/source/
recovery/static/linkage/regression/browser evidence and70 other guards/93 configs/
30 protected client hashes. Inventory passes20 checks/74 client/158 historical server
receipts. Root client-generated system.cfg stays informational and untouched.
All43 settings retain worldcap1500,target1200..1500,budget120,controlledcap3000,
personal23/23,city0,appearance disabled. Saved SQL IDs/progress/builds remain.

Closed-component guards passed. No agent lifecycle/attach/dump/gameplay/production
SQL or native World/ID fixture occurred; Login/Chat untouched. PB-VAL-058 support/
recovery and PB-VAL-059 Follow remain user gameplay. Genuine long native operations
are still possible; this does not claim every photographed delay or full WoW parity
is resolved. Full quest audit and wider port remain unfinished engineering. See
 docs/PLAYERBOTS_SUPPORT_RECOVERY_20261011.md and
 docs/PLAYERBOTS_FORCED_FOLLOW_20261011.md.

## Compact inventory and combat gear/save repair - installed offline - 11 October 2026

PB-REPAIR-GEAR-SAVE-035 / PB-CUSTOM-UI-036 is installed through unchanged normal
Maven output. The roster is 900px; character positions, all equipped items,
carried cube, private warehouse and Legion storage are separate compact views.
Existing native hover uses actual count/enchant/tempering; selected details show
saved native bonus/fusion/socket/godstone data. Combat selection excludes statless
cosmetic skins independently of appearance settings; displaced skins remain owned.
Automatic checkpoints/logout include pending native items even when their private
container is clean. Human saves/shared custody, SQL IDs/progress, prior 033/034
behavior and all 43 settings remain.

Receipt: external archives/server/game-server/backups/
playerbots-source-build-20261011-035314-522287. Review: external staging/output/
release-20261011-034100-742995. Installed GameServer SHA:
6b3429a6377ec8c38af34b62387623e34098d5f4542d4d977bc413f46c723be1.
101 fixture sources / 64 exact-artifact suites / 64,482 checks / 28 tooling tests,
43 native-browser actions / 33 screenshots and 1,757 warning-free handlers pass.
Complete XML/schema/JAXB and 3,380-class/184,229-reference linkage pass; 3,627 prior
JAR entries remain identical. Both old-artifact cosmetic-selection and omitted
native-dirty-item failures reproduce; corrected XML 29, recording-JDBC 94 and
native-item 29 checks pass.

One complete normal GS JAR copied unchanged; Commons/static resources/configs/client
unchanged. 2,044 generated caches backed up and invalidated. Independent installed
verification passes exact builder/recovery/source/static/linkage/browser/regression
evidence and 71 guards / 93 configs / 30 protected client hashes. Inventory passes
20 checks / 74 client / 157 historical server receipts; current root system.cfg
remains informational and untouched. All 43 settings retain worldcap1500,
target1200..1500,budget120,controlledcap3000,personal23/23,city0,appearance disabled.
Fresh closed-component guards passed; no lifecycle/attach/production SQL/gameplay
or World/ID fixture occurred. Login/Chat untouched. PB-VAL-056 gear/logout and
PB-VAL-057 UI remain user acceptance. Full autonomous quest-chain audit and wider
parity remain unfinished engineering. See
 docs/PLAYERBOTS_COMBAT_GEAR_PERSISTENCE_20261011.md and
 docs/PLAYERBOTS_COMPACT_ITEMS_20261011.md.

## Combat/loot/navigation responsiveness — installed offline — 11 October 2026

PB-REPAIR-DECISION-033 isolates active world decisions from idle planning and
uses nonblocking last-committed population snapshots for placement checks.
PB-REPAIR-NAV-CAMP-034 addresses shared search admission and native camp landing.
One complete normal build/delivery owns both; exact evidence and current status:
[decision responsiveness](PLAYERBOTS_DECISION_RESPONSIVENESS_20261011.md).
Gameplay acceptance PB-VAL-054/055, full quest audit and wider port remain open.
All installed031/032 and preceding features/settings/SQL identities remain.

Installed unchanged normal GameServer SHA:
0d05a405672362c205c8b98a881ae810ddfc97f67b9c1c3343b5f387faa31152.
Current receipt: playerbots-source-build-20261011-031352-404231; review:
release-20261011-025619-405037-client-guard. 99 fixture sources /61 artifact-bound
suites /64,330 checks,28 tooling tests,43 browser actions and full static/linkage
gates pass. Independent installed verification passes71 guards /93 configs /
30 protected client hashes. Root client-generated system.cfg is informational
only and untouched; every other client file remains guarded. Inventory passes
20 mod checks /74 client /156 historical server receipts. All43 settings retain
worldcap1500,target1200..1500,budget120,controlledcap3000,personal23/23,city0,
appearance disabled. No lifecycle/attach/gameplay/production SQL changes occurred.

## PB-VAL-052 UI / PB-VAL-053 native alliance — installed; gameplay pending

PB-CUSTOM-UI-031 / PB-SCOPE-ALLIANCE-032 is installed offline through complete
unchanged normal Maven output. The full-screen visual overhaul supplies seven
companion tabs/subtabs, keyboard/focus/draft preservation and native party/alliance
management. Duplicated Follow/Attack/Stay/Guard/Passive/recall quick commands are
removed from this panel; the separate existing in-game command window and its
installed client files remain untouched. The former overflowing Dismiss strip is
gone; individual roster dismissal retains confirmation and native saving.

Native alliance controls cover party conversion, four groups of six, safe move/
swap, captain/vice rights, invitations, ready responses and confirmed remove/leave.
Native headless roster health/presence, per-actor conversion exclusion and shutdown
draining are included; normal shared combat/follow/support and human rights remain.
No persistent state is introduced in files, and saved SQL identities/progress stay.

External receipt: playerbots-source-build-20261011-023859-456008; review:
release-20261011-022527-509381. GameServer SHA:
e6516b8c82aded5e34d7804e80ccfa160c16d9bee39706840f8859e8611919ca.
96 fixture sources / 58 artifact-bound suites / 63,042 checks, 25 release-tool tests
and 43 exact-builder native-browser actions pass. All 1,757 handlers compile
warning-free; 2,533 managed resources remain unchanged; complete XML/schema/JAXB
and 3,373-class / 183,817-reference linkage pass. All 56 original / 71 pinned
scope hashes and 70 declared Git blobs verify. 3,623 prior JAR entries remain
identical; public APIs and previous package entries remain. Only the complete
normal GameServer JAR was copied; Commons and client files remain unchanged.

Independent verification passes exact source/builder/recovery/static/linkage/
regression/browser-media/renderer/screenshot evidence, 71 guards / 93 configs /
31 client hashes and 2,044 backed-up invalidated handler caches. Inventory passes
20 mod checks / 74 client / 154 historical server receipts. Client exit system.cfg
was preserved after full inventory refresh; all other client hashes remain.
All 43 explicit settings retain worldcap1500, target1200..1500, budget120,
controlledcap3000, personal23/23, city0 and disabled appearance. The initial browser
attempt is retained in review/failed-invitation-fixture: synthetic invitation entry
omitted the input event. The corrected fixture explicitly verifies typed-name
retention across refresh; unchanged product/builder media passed all 43 actions.

Fresh closed-component guards passed. No component lifecycle/attach/dump, gameplay,
production SQL or native World/ID fixture occurred; Login/Chat stay untouched.
PB-VAL-052 UI and PB-VAL-053 alliance acceptance remain user gameplay. Full
quest-chain audit and wider Playerbots parity remain active unfinished engineering.
See docs/PLAYERBOTS_UI_BEHAVIOR_20261011.md and docs/PLAYERBOTS_ALLIANCE_20261011.md.

## PB-VAL-050 legion presence / PB-VAL-051 Cassius - pending user gameplay

LEGION-029-CASSIUS-030 is installed and independently verified in external receipt playerbots-source-build-20261010-233223-856383; review release-20261010-232624-253950. All 57 artifact-bound suites / 62,960 checks and preservation gates pass. Actual prior native roster code reproduces spawned bots appearing offline; 44 new checks cover corrected cache/lifecycle/native invitation/wire/SQL/filter behavior. Native geometry reproduces Cassius's old underground spawn and verifies corrected ground plus eight nearby approach directions. No production World/ID/gameplay/SQL writes occurred.

PB-VAL-050: invite active companions and an independent bot, reopen roster, verify online status and map/level updates; dismiss/rotate/reuse and verify departure time and same-identity return. Native ranks/titles/rights, human presence, warehouse custody and bonus behavior must remain.

PB-VAL-051: visit Cassius801189 in Heiron; verify supported placement, approach/talk and native quest16904 requirements/rewards, retaining stationary guard behavior. No dedicated AI or linked partner is claimed. User performs startup/gameplay; full quest-chain engineering and wider parity remain open separately.

See [legion evidence](PLAYERBOTS_LEGION_PRESENCE_20261010.md) and [Cassius evidence](CASSIUS_PLACEMENT_20261010.md).

## PB-VAL-049 shared native name resolution - pending user gameplay

NAMELOOKUP-028 is installed offline in the coordinated QUEST-027 cumulative
receipt playerbots-source-build-20261010-231349-474859; review224757-712697.
53 actual-index/formatter checks cover reported names, human/custom names,
language independence, rename/removal/collision and concurrent registrations.
All56 bound suites/62916 checks and independent installed-delivery guards pass.
User acceptance: legion invitations to LeMuse/MagicDps resolve the actual actors
and reach native rights/membership/bot responses; party/alliance/league, whispers,
friend requests, information and other shared name searches accept mixed case
while retaining native denials and exact display names. No gameplay was performed.
QUEST-027 adds40 native capability/accepted ItemOrders checks; complete native
item-start/custom/campaign chains remain engineering-open, not merely gameplay.
See [source, baseline and delivery](PLAYER_NAME_LOOKUP_20261010.md) and
[quest continuation](PLAYERBOTS_QUEST_CAPABILITIES_20261010.md).

## Independent field combat continuation - installed offline - 10 October 2026

PB-REPAIR-FIELD-026 fixes reproduced field decision defects: fresh autonomous
pulls no longer inherit the NPC ten-second combat dialog gate; recovery starts
when existing HP85/MP60 pull readiness is unavailable and retains95/95 hysteresis.
Pending pulls enter real noncombat arbitration after earned loot/recovery/useful
preparation, then normal native offense runs at its existing combat reaction.
Committed independent targets remain inside the existing45m leash; acquisition
and private targets retain25m. Spirit buffs check actual beneficiaries in combat
as well as idle. Needed heals/buffs, native interaction restrictions, pet control,
loot/custody, MANTRA-025 and all user settings/features/IDs/progress remain.

Exact screenshots lack live status traces; logs cannot establish which defect
caused each photographed bot. Old MANTRA-025 normal output reproduces the filled
PC-aura combat-buff failure; recovery tests reproduce the previous resource dead
band. All56 original/69 pinned scope hashes verify.59 field/114 idle-core checks,
54 artifact-bound suites/62823 checks,19 release-tool tests,1757 warning-free
handlers,2533 unchanged resources,full XML/schema/JAXB/linkage pass.3625 prior JAR
entries and109 other Session/14 Spiritmaster methods remain identical; all
existing anonymous-class bodies remain. Source -> complete normal Maven output
-> guarded unchanged JAR copy; no method transplant/override or package removal.

Installed external receipt:playerbots-source-build-20261010-223156-617438;
review:release-20261010-222634-587572. GameServer SHA:
7009303e57fe4a0d6bddabb24e5282e4e98be926c6435b3150cf8566d688a198.
Independent installed verification passes exact source/builder/recovery/static/
linkage receipts and all54 bound suites/62823 checks.
Commons unchanged; zero generated caches present.70 guards/93 configs/31 client
hashes retained; inventory20 checks/74 client/151 historical server receipts passes.
All43 explicit settings retain worldcap1500,target1200..1500,budget120,controlled
cap3000,personal23/23,city0,appearance disabled. Prior221621/222227 candidates were
superseded without install; final named action passes preservation guards.
No agent component lifecycle/attach/dump,gameplay,production SQL or native World/ID
fixture occurred. GameServer/Aion were closed for replacement; Login/Chat untouched.
PB-VAL-048 field kill/loot/recovery/buff/retention/restart acceptance remains user
playtesting. Full audit quest-handler/chain/action work remains active engineering;
this repair does not close it or full WoW parity. See
[cause, exact mapping and acceptance](PLAYERBOTS_FIELD_COMBAT_20261010.md).

## Port audit and coordinated class repair - installed offline - 10 October 2026

PB-REPAIR-AUDIT-022 implements the authorized release/scheduling/travel/quest/
diagnostic/record findings from the project audit. Canonical delivery now requires
regression evidence bound to the exact artifacts, runner, fixtures and reports.
Controlled/world/maintenance/checkpoint work uses separate bounded workers and
per-character exclusion; native custody and shutdown draining remain. Complete
indexed travel destinations replace first-32 sampling. Native declared quest pages
and independent XML intermediate choices preserve handler authorization. Headless
health retains missing-lease/registration/custody/stall warnings and human checks.
Full custom campaign/profession/Ranger/autonomous economy parity remains partial.

The explicitly coordinated CAST-023/ANTIHACK-024 source repairs are included in the
same unchanged complete normal Maven JAR. Spiritmaster idle buff checks use actual
native beneficiaries and require no pet for summon preparation. Other classes'
casting paths remain separate. Bots skip core client movement/hit-time accusations;
native timing, pet attack rejection and human checks remain. The user's exact idle
animation and native latency still require gameplay acceptance.

Verified: 91 fixture sources / 53 artifact-bound offline suites / 62,748 checks,
19 release-tool tests, 1,757 warning-free handlers, 2,533 unchanged managed resources,
complete XML/schema/JAXB and linkage gates. Exact coverage records all 8,043 native
quest templates: 3,385 ordinary XML-family templates, one XML conversation subset,
798 specialized XML and 3,859 custom/no-XML entries. This measures metadata coverage,
not complete custom Java handler availability or native quest-chain acceptance.
All 56 original / 69 scope reference SHA hashes verify. 3,593 prior JAR entries and
100 other service / 110 other session methods remain identical; no installed public
methods or package entries were removed.

Receipt: external archives/server/game-server/backups/
playerbots-source-build-20261010-212205-483239. Review: external staging/output/
release-20261010-211439-905427. GameServer SHA:
23a00ca1f8b88d0c99ae42bdc5f1bfd67913466c3dd2f58763243ff49c74a621.
Commons unchanged. One normal JAR copied unchanged; 2,044 handler caches backed up
and invalidated. Independent verification passes 70 guards / 93 configs / 31 client
hashes plus exact source/builder/recovery/static/linkage/regression/coverage receipts.
Inventory passes 20 mod checks / 74 client / 149 server historical receipts. Current
user system.cfg was preserved and the successful current inventory refreshed; every
other client hash remains preserved. All 43 explicit Playerbots settings remain:
world cap1500, target1200..1500, budget120, controlled cap3000, personal quotas23/23,
city0, appearance disabled; the deprecated per_map value does not set a cap.

Fresh offline guards passed. No component start/stop/restart/attach/dump, gameplay,
production SQL or native World/ID fixture occurred. Components remain off; Login/
Chat untouched. PB-VAL-046 audit behavior and PB-VAL-047 idle/antihack acceptance
remain user testing. See PLAYERBOTS_AUDIT_REPAIR_20261010.md and
PLAYERBOTS_IDLE_CASTING_ANTIHACK_20261010.md for exact mapping and boundaries.

## Whole-population admission repair - installed offline - 10 October 2026

PB-REPAIR-POP-021 addresses the user's over-target population report. Native logs
showed1308 active global bots against target1289, below the separate hard world
cap1500. Geographic admission subtracted only on-player-map actors, omitting
occupied off-map slots. Its installed helper permits120 more admissions for that
captured snapshot. Saved loading/publication checked the hard cap but did not
compare the computed global target with existing global sessions.

Pinned RandomPlayerbotMgr UpdateAIInternal/AddRandomBots/GetBots/ProcessBot/
OnPlayerLoginError plus factory/config dependencies were read before editing.
Admission now always subtracts the entire global admitted set, including pending
and off-map residents. Pre-load and locked final-publication checks separately
bound whole global/session totals, including closing/save-held slots. Geographic
demand chooses destinations, never extra slots. Existing excess is safely retired
before relocation; pending on-map excess can release residency without spawning.
Protected/control/combat/visibility/custody actors remain until safe, so target
reductions can temporarily leave protected excess; no new spawn can grow it.
No character deletion/reroll, count/config override or feature removal occurs.
The saved reserve remains separately bounded and retains older identities.

Installed complete unchanged normal Maven output: external receipt
playerbots-source-build-20261010-193907-521380; review
staging/output/release-20261010-193349-087609. GameServer SHA
2a051dcaf09c960298ee38fc0c9cad40b739c8590765648e49f88aa26e226aac;
Commons unchanged. 88 fixture sources /48 offline suites /62516 checks (39024 new
admission checks),16 tooling tests,1757 warning-free handlers,2533 unchanged
resources, complete static/JAXB/linkage pass (3360 classes /182246 references).
All prior entries remain;3623 entries byte-identical. All56 original hashes /
63 scope references verify. Only normal GS JAR copied;zero caches present to
invalidate. Delivery guards preserve70 other files /93 configs /31 client hashes.
Independent installed verification passes builder/source/recovery/static/linkage
receipts and all48 suites /62516 checks. Inventory passes20 mod checks /74 client /
148 server historical receipts.

Current live config remains population1200..1500/worldcap1500/budget120 and
companions23/23; all 43 values remain. Source/runtime paths unchanged; external
workspace holds evidence/output/recovery. No component start/stop/restart/attach,
new dump, production World/DB/ID fixture or DB mutation occurred. Both tested
components stayed closed for guarded delivery; Login/Chat untouched. Preserve
installed PERF-020 and all prior progression/class/economy/raid/custody/shutdown
features, appearance-disabled state and saved IDs/builds. PB-VAL-045 actual
fill/excess convergence/map hops/rotation/restart is user gameplay acceptance;
full WoW parity remains partial. See [cause and exact port mapping](PLAYERBOTS_POPULATION_ADMISSION_20261010.md).

## Companion responsiveness repair - installed offline - 10 October 2026

PB-REPAIR-PERF-020 addresses the user's delay across following, casts/heals and
menu commands. Exactly three newly authorized read-only Thread.print -l samples
from GameServer PID21144 are consumed/exhausted. Every sample caught world AI
holding the shared service monitor while saves waited: two terrain collision/
world-travel paths and one quest-planning path. Logs independently recorded
27.4-second overlapping AI/save warnings and a TimSort comparator-contract failure.
The samples do not establish one continuous 27-second terrain call or an executing
SQL save as their cause. Evidence is external diagnostics/companion-delay-20261010-190355.

Pinned UpdateAI/GetReactDelay, MoveTo/duplicate/wait movement and TravelMgr catalog
source was read before editing. Immutable deadline snapshots fix mutable sorting;
due controlled actors precede autonomous work to honor the existing 100ms master
reaction. Progressing native same-goal movement is reused; fully supported direct
segments avoid speculative detours. Native per-step floor/body/door checks remain.
Static starter catalogs are cached by bot level while native prerequisites stay
dynamic; unreachable goals are rejected early. Conservative finite-segment scene
indexing retains native contact order, collision/door checks and supported bounds
invalidation. Queue priority and scene indexing are Aion host adaptations, not
claimed literal WoW queue/BVH ports. The global monitor remains; individual slow
SQL operations and other unobserved hotspots remain possible.

Installed complete unchanged normal Maven output: external recovery receipt
playerbots-source-build-20261010-192847-400315; review
staging/output/release-20261010-191620-637849. GameServer SHA
5c9e861edf67b1eb0a23c58d62c4f92d08eb0e4b34823c044d2f20a3a3eb8ea1;
Commons unchanged. 87 offline sources /47 suites /23492 checks (6013 responsiveness,
448 collision), 16 tooling tests, 1757 warning-free handlers, 2533 unchanged
resources, full static/JAXB/linkage pass (3360 classes /182187 member references).
All prior entries remain; 3617 entries and131 other service methods byte-identical.
All56 original upstream hashes /63 scope references verify. Only normal GS JAR
copied;2044 handler caches backed up/invalidated. Delivery guards preserve70 other
files /93 configs /31 client hashes. Independent installed-delivery verification
passes builder/source/recovery/static/linkage hashes and all47 suites /23492 checks.
Current client system.cfg was preserved after inventory refresh;20 mod checks /
74 client /147 server historical receipts pass.

No bot count/config changes, features disabled, actor rerolls or DB mutations.
Existing IDs/progress, field/quest/loot, class/raid/economy/custody/shutdown mods and
appearance-disabled state remain. Source is C:/Git/aion-server; runtime remains
target-deploy/game-server; external development workspace holds outputs/recovery.
Both tested components were closed by the user for guarded delivery. No component
start/stop/restart, agent load or gameplay fixture occurred; the three authorized
read-only dumps were the sole live attach exception. PB-VAL-044 actual follow,
combat/heal/menu responsiveness with full population and restart remains user
acceptance. Full upstream parity and unrelated backlogs remain partial.
See [diagnosis, exact mapping and limits](PLAYERBOTS_RESPONSIVENESS_20261010.md).

## Configured population target repair - installed offline - 10 October 2026

PB-REPAIR-POP-019 fixes the user's `[465/160]` regression. The agent-added 018
per-map quota was not authorized by the geographic redistribution request.
Player focus preserves the complete configured global target and distributes it
among occupied maps. Login/logout/teleports do not shrink it; population.min/max
and world.max_active remain authoritative. The old player_focus.per_map setting
is retired/ignored even if an existing file says160; legacy helper signatures
remain for compatibility. All43 source/live config entries are explicitly explained.
Native bot-level/faction/camp, visibility/150m/control/combat/custody, single worker,
SQL/reserve identity and all earlier progression/economy/class features remain.

Installed unchanged complete normal Maven output: external recovery receipt
playerbots-source-build-20261010-182754-884695; review
staging/output/release-20261010-181208-585192. GS SHA
48712ca279d6f9e3bd493013fdfa7c7effa09d371bb7188d0aecc632fa5c590e;
Commons unchanged. 86 offline sources /46 suites /17476 checks (8067 focus),
16 tooling tests, 1757 warning-free handlers, 2533 unchanged resources, complete
native static/JAXB/linkage pass. 3620 prior entries and132 other service methods
remain byte-identical. All56 original hashes /63 scope references verify.
The first review180926 was superseded before delivery to retain legacy helper
signatures; only181208 was delivered. One normal GS JAR copied;2044 handler caches
backed up/invalidated. Independent verification passes70 guards /93 configs /
31 client hashes and source/artifact/recovery/static/linkage receipts.

Value-preserving configuration documentation receipt:
playerbots-config-docs-20261010-182828-582666, external server backups. All43 explicit
values remain, including live population1200..1500/worldcap1500/budget120 and
companions23/23. Updated user/client system.cfg was preserved after the complete
inventory passed20 mod checks. Final inventory records74 client /146 server receipts.
Runtime remains target-deploy/game-server. Fresh closed-component guards passed;
no component start/stop/restart/attach/dump, production World/DB/ID fixture or
production database writes occurred. PB-VAL-043 actual counts/activity/hops/restart
remains user gameplay acceptance; full WoW parity and unrelated backlogs stay partial.
See [mapping and repair](docs/PLAYERBOTS_POPULATION_TARGET_REPAIR_20261010.md).

## Responsive player-area population - installed offline - 10 October 2026

PB-CUSTOM-POP-018 addresses the user's 34-Heiron-bot screenshot and request for
local activity and prompt map/area teleport response. Real-player maps/positions
anchor demand; player level is absent from its snapshot and does not govern
placement. Pinned manager zone/teleport/reserve/presence flow was read before
source edits. This prompt per-map quota is explicitly an Aion extension, not
claimed exact WoW parity. Each bot keeps native faction/level/camp eligibility.
Existing suitable unseen idle actors move first; excess/off-map/incompatible
actors save offline through the single native background worker and may reuse
the same ID/progress without demand cooldown. Native timed absence remains.
Known visible/150m/group/control/combat/trade/custody/closing actors stay protected.
Only Sanctum/Pandaemonium may use service camps without a native field band.
AI/checkpoint callbacks exclude the active relocation/retirement job; complete
shutdown drain and save-failure leases remain. Native camp compatibility is
cached by faction/bot-level/map rather than shuffling camps per reserve identity.

Complete unchanged normal Maven JAR installed offline: source receipt
playerbots-source-build-20261010-174509-604558, external server backups; review
staging/output/release-20261010-173926-577729. GS SHA
73efe6cc52663671928957f3e529e319a7ad5a486171f355abe1e3322e12997e;
Commons unchanged. 86 offline sources /46 suites /11470 checks (2061 new),
16 release-tool tests, 1757 warning-free handlers, 2533 unchanged resources,
full static/JAXB/linkage pass. All 130 native faction/bot-level catalogs and 260
focus compatibility selections pass. 3617 prior entries /95 other service /17
other placement /12 existing roster methods remain identical. 56 original hashes
and 63 scope references verify. One normal JAR copied; 2044 handler caches
backed up/invalidated. All earlier field/names/legion/storage/mail/class/raid/
population/custody/shutdown mods remain; appearance override stays disabled.

Configuration documentation receipt playerbots-config-docs-20261010-174703-757330
backs up and explains all 43 settings, preserving all40 explicit user values,
including current population1200..1500/worldcap1500/budget120 and companions23/23.
New defaults: player_focus.enable=true, per_map=160, radius=900m. Global requested
native SQL target/finite reserve remain separate from this transient local quota.
Existing user limit edits were backed up/verified without runtime writes in
staging/output/playerbots-user-population-limits-20261010-172833-968457.
Updated client system.cfg was preserved after full inventory validation.
Independent delivery/recovery/source/artifact and exact subsequent documentation
checks verify 70 guards /93 configurations /31 client hashes. Inventory20 /74
client /144 server historical receipts passes. Runtime remains target-deploy.
GameServer/Aion remain off; Login/Chat untouched. No agent start/stop/restart/
attach/dump, production World/DB/ID fixture or database mutation occurred.
PB-VAL-042 actual visibility, native kills/rewards, fast map/area hops, protected
actors, performance and restart with retained progress remains user testing.
Full parity is partial; the independent economy/profession/class backlog remains.
See [exact mapping, controls and acceptance](docs/PLAYERBOTS_PLAYER_FOCUS_20261010.md).

## Generated world-bot names - installed and migrated offline - 10 October 2026

PB-REPAIR-NAMES-017 replaces ID-encoded Wander names with code-generated,
pronounceable native-gender names. Pinned RandomPlayerbotFactory name cache,
CreateRandomBotName native checks and conlang fallback were read before edits.
Native name pattern/forbidden words/reserved names and collision retries remain;
SQL errors fail closed. Generated names persist in players, without a runtime
name database or spawn/restart reroll. Human/player-chosen private names remain.

Normal source release: playerbots-source-build-20261010-163434-147481, external
server backups. Review: external staging/output/release-20261010-163002-781882.
GS SHA 7ad7b791ae8216b201dd6d80dea0ab3f6cbdb4b06cea8aa01353b1b4e438ee9d;
Commons unchanged. 85 offline sources / 45 suites / 9409 checks (46 naming,
12000 simulated unique reserve), 16 synthetic-JDBC migration failure-boundary
checks, 13 release-tool tests, 1757 warning-free handlers, 2533 unchanged resources
and complete static/linkage gates pass. 3618 prior entries / 111 other service /
eight other population methods remain identical. 56 original hashes / 63 refs
verify. One unchanged normal JAR copied; zero caches present. Independent delivery
passes 70 guards / 93 configs / 31 client hashes and exact recovery/source/artifact/
static/linkage receipts. Inventory20 / 74 client / 142 server receipts passes.

Name-only native SQL receipt: playerbots-names-20261010-163631-012877, external
server backups. Exactly3147 confirmed global legacy world-bot names and native
old_names entries committed in one transaction; a separate JVM/connection verified
all changed names/history, every other players column, previous history and all
six roster/population tables against the reviewed snapshot. IDs/ownership/progress/
items/legion membership/config remain. External exact mappings, fingerprints,
history IDs and recovery SQL are retained; no runtime file/schema was added.
All preceding field/legion invitations/storage/mail/class/raid/population/custody/
shutdown mods remain; appearance disabled. GameServer/Aion remain closed, Login/
Chat untouched. No start/stop/restart/attach/dump/World/actor/character-ID fixture
occurred. Production SQL writes only requested names/native rename history.
PB-VAL-041 actual menu/world names, future creations/restart remain user testing;
full parity remains partial. See [mapping and receipts](PLAYERBOTS_NAMES_20261010.md).

## Native bot legion invitation responses - installed offline - 10 October 2026

PB-REPAIR-LEGION-016 fixes unanswered native legion invitations. Pinned
GuildAcceptAction, outgoing packet trigger and security(ignoreGroup=true) were
read before editing source. Bots consume the registered native response once;
existing LegionService membership/capacity/VOLUNTEER/DAO/history/title paths run.
World bots accept eligible human invitations while retaining independence and
current controller/activity. Private companions retain actual-owner enrollment.
Native rights/faction/denial and lifecycle/custody gates remain. Human requests
stay client-driven. Membership SQL runs outside the population service monitor.

Normal source -> complete Maven output -> unchanged offline runtime copy is
installed: playerbots-source-build-20261010-162009-822859, external server backups.
Review: external staging/output/release-20261010-161118-759618. GS SHA:
55cb2a35d92ac5a91e7f8433f0f8f4e0731b01e3d100761a6e01414a58466f8e.
84 offline sources / 44 suites / 9363 checks (157 invitation), 13 release-tool
checks, 1757 warning-free handlers, 2533 unchanged resources and complete static/
linkage gates pass. 3611 prior entries / 111 existing bot-service / 69 other
LegionService methods remain identical. All 56 original hashes / 63 references
verify. Commons unchanged; one normal JAR copied; 2044 caches backed up/invalidated.
Independent delivery verification passes 70 guards / 93 configs / 31 client hashes
and exact source/artifact/recovery/static/linkage receipts. Inventory 20 checks /
74 client / 141 server receipts passes. Current client system.cfg and all 40 live
config values remain, including world2800..3000/cap3000/admission120 and companion
23/23/city0. All preceding field/storage/mail/class/raid/population/shutdown mods
remain; appearance stays disabled. Runtime remains target-deploy/game-server.
GameServer/Aion are closed; Login/Chat untouched. No start/stop/restart/attach/dump
or production World/DB/ID fixture occurred. PB-VAL-040 actual invitation/member/
restart gameplay remains user acceptance; full parity remains partial. See
[exact mapping and receipt](PLAYERBOTS_LEGION_INVITATIONS_20261010.md).

## Native legion deposits and field repair - installed offline - 10 October 2026

PB-SCOPE-004C ports pinned GuildBankAction requested whole-stack deposits through
native Aion legion storage. Equipment has Deposit to legion and a shared inventory
view; .bot legionbank / .bot gb use exact object IDs and paged lists. Both owned
characters need actual membership/rights in the same legion and an accessible
keeper, current party/alliance and idle gates. Native exclusive lease, shared
mutation/save serialization and transactional character -> legion custody precede
native projection. Partial/stale shared images refuse. Ambiguous outcomes retain
items/IDs and hold companion/legion saves, mutations and disband. No runtime
file/schema, automatic donation, legion membership/rights grant or companion
withdrawal was added; pinned GuildBankAction contains deposit only.

The same verified build installs PB-REPAIR-FIELD-015: actual monster camps instead
of service hubs/lobbies, sparse/per-level spawn coverage, native XP/solo +4 target
bounds, local hunting before travel and distinct saved-reserve progress logging.
All eight field class entries match its separately verified candidate. All prior
population/class/raid/private warehouse/mail/custody/shutdown mods remain;
appearance stays disabled. Full parity remains partial.

83 offline sources / 43 suites / 9206 checks (98 legion / 534 field), 161 real-browser
simulated-response checks, 13 release-tool tests, all 1757 warning-free handlers,
2533 managed-resource audit, full static XML/schema/JAXB and linkage pass. All
130 native faction/level field selections and ten faction starts verify. 3581
prior JAR entries / 109 other service / 109 other session methods remain identical.
All 56 original upstream hashes / 59 scope references verify; Commons unchanged.

Receipt: playerbots-source-build-20261010-154208-865301, external server backups.
Review: external staging/output/release-20261010-153316-224774. GS SHA:
86c5a719eb2e1bdba8bc0a4c5c9db6d15b80a99b47b67f16eb0a2eba9d2e2af4.
Two exact unchanged normal builder files copied: GameServer JAR and Bot.java;
zero generated caches present. Post-install verifies recovery/source/artifact/
static/linkage hashes, 69 other guards / 93 configs / 31 client hashes. Inventory
passes 20 mod checks / 74 client / 140 server historical receipts. All 40 latest
values remain: world 2800..3000/cap3000/admission120, companion23/23, instance
follow true and authorized field city_percent=0. Exact documented config recovery:
playerbots-config-docs-20261010-130146-116003. Both components remain off; no
start/stop/restart/attach/dump or live World/DB/ID fixture was used.

PB-VAL-039 legion/native-human withdrawal/restart and PB-VAL-038 actual field
kills/rewards/rotation remain user gameplay acceptance. World COD/guild-task mail
(004B2), professions (005), broad selectors/autonomous economy and Ranger remain
separate source-gated work. Do not re-port installed slices for pending gameplay.
See [upstream mapping, controls, custody and acceptance](PLAYERBOTS_LEGION_STORAGE_20261010.md)
and [field evidence and acceptance](PLAYERBOTS_FIELD_ACTIVITY_20261010.md).

## Field placement/hunting repair - installed cumulative - 10 October 2026

PB-REPAIR-FIELD-015 is installed in the cumulative PB-SCOPE-004C normal source
release above. Its older field-only candidate is superseded and must not be
installed. Exact class equivalence, current native catalog, 534 field checks and
complete cumulative delivery/recovery verify. Native field kills/rewards/rotation
remain PB-VAL-038 user gameplay acceptance. Full upstream parity remains partial.
See [field causes, upstream mapping and acceptance](PLAYERBOTS_FIELD_ACTIVITY_20261010.md).

## Native companion mail - installed offline - 10 October 2026

PB-SCOPE-004B is installed from complete unchanged normal Maven output. Pinned
MailAction/SendMailAction requested listing/read/collection/empty-delete and
item/Kinah mail are adapted through native Aion postboxes, mail, prices and
inventory. Owned alts/Temporary Bots have a Mail tab and .bot mail commands;
both characters must share a regular accessible mailbox and meet native party/
alliance/idle/ownership gates. Exact whole stacks and own wallet/postage are
committed with native mail/inventory SQL before publication. Incoming native
mail and lazy mailbox load share a lock; overflow/partial-load verification,
custody quarantine and shutdown safety remain. No new runtime file/schema or
population polling was added. Human MailService methods remain byte-identical.

81 offline sources / 41 suites / 8574 checks (87 mail), 131 simulated-response
real-browser checks, 12 release-tool tests, all 1757 warning-free handlers,
2533 resource audit and complete static-data/linkage gates pass. 3597 prior JAR
entries / 110 other session methods / all 12 human MailService methods remain.
All 56 original upstream hashes and 57 scope references verify.

Receipt: playerbots-source-build-20261010-124307-762421, external server backups.
Review: external staging/output/mail-20261010-123234-3802677. GameServer SHA:
a5713642c848fcc554f71df3a37567ca179b533bd9f2af0fb9d5b89294ba5427.
Commons unchanged. Normal JAR and builder Bot.java copied unchanged; 2044
handler caches backed up/invalidated. 69 other guards / 93 configs / 31 client
hashes and recovery originals verify. All 40 explicit live config values were
preserved by the documentation receipt playerbots-config-docs-20261010-124029-950064.
User world settings remain 2300-2500, world cap 2500 and admission budget 120;
active/saved personal companion capacities remain 23, instance follow true.
Inventory passes 20 mod checks / 74 client / 136 server historical receipts.
No component start/stop/restart/attach/dump or live World/DB/ID fixture was used.
Both components remain off; PB-VAL-037 native mail gameplay remains user testing.

Full parity remains partial. World-bot mailed sales need native COD custody
(PB-SCOPE-004B2), and automatic CheckMailAction needs native guild-task integration.
Normal mail/exact-ID whole stacks and explicit empty-letter deletion are the
bounded adaptation; packed/courier mail and broad WoW selectors remain separate.
Existing gameserver.playerbots.enable governs mail; no separate send-only toggle
is claimed. Next independent economy port is PB-SCOPE-004C native shared legion
storage, using pinned GuildBankAction and native access/custody first. Professions
and Ranger remain separate. Preserve all installed population/class/raid/private
warehouse/custody/shutdown/appearance-disabled mods. See [exact mapping, controls,
differences and acceptance](PLAYERBOTS_MAIL_20261010.md).

## Private warehouse and startup target - installed offline - 10 October 2026

PB-SCOPE-004A ports pinned master-requested BankAction listing/deposit/withdraw
through native Aion private character storage, banker restrictions and item moves.
Owned alt/Temporary Bot menu controls show capacity and Deposit/Withdraw; .bot bank
uses exact object IDs and paged lists. Whole stacks retain ownership/IDs/attributes;
native SQL custody commits before native projection. Pending unequip changes and
separate ordinary/special cube capacity are handled. Ambiguous outcomes hold the
companion and retain its lease/items. World bots retain private ownership.

PB-REPAIR-POP-014 validates the saved target against config before resident loading,
matching pinned UpdateAIInternal bot_count -> GetBots. Valid targets/events, native
IDs/progress, manual capacity and the single worker/continuation lifecycle remain.
All prior 012D/3000-world/23-companion/class/shared/custody/shutdown/mods are retained.

Verified complete unchanged normal Maven output: 80 offline sources / 40 suites /
8487 checks, 117 warehouse and 21 pipeline checks, 79 real-browser checks with
simulated responses, nine release-tool tests, all 1757 warning-free handlers,
2533 audited resources, complete static-data/linkage and preservation gates pass.
3594 prior JAR entries / 102 other service methods / 110 other session methods
remain. All 56 original upstream hashes and 51 scope references verify.

Review: external staging/output/release-20261010-113447-358376.
GS SHA 25a709fa04fb6131b9cc0fba8611cc39e39cb61b671dd6a73c2abdf64c602259.
Installed offline receipt: playerbots-source-build-20261010-120216-800919,
external archives/server/game-server/backups. Commons unchanged. Two unchanged
builder files copied: normal GameServer JAR and Bot.java; 2044 generated caches
backed up/invalidated. All 69 other guard files / 93 configs / 31 client hashes,
source/output hashes and recovery originals verify. Current system.cfg and
SystemOptionGraphics.cfg were preserved after refreshing the complete inventory:
20 mod checks / 74 client / 134 server historical receipts pass. Final verification
passes 40 suites / 8487 checks. The delivery verifier now checks recorded resource
replacements against their installed builder hash while retaining their original
baseline/backup checks; all 12 tooling tests pass, including three new regressions.
Both components remain off. No component start/stop/restart/attach/dump or live
World/DB/ID test was performed. Installed 012D and all preceding features remain.

PB-VAL-035 warehouse and PB-VAL-036 target/restart gameplay remain user acceptance.
Full parity remains partial. Next economy source review is native mail 004B;
shared storage/professions/full autonomous economy and Ranger remain separate.
Do not re-port this installed slice solely because gameplay acceptance is pending.
See [exact source mapping and acceptance](PLAYERBOTS_WAREHOUSE_20261010.md).

## Native population SQL repository - installed offline - 10 October 2026

**PB-SCOPE-012D is installed** through unchanged normal Maven output. Global
and manual account pools, ordered native-ID intents, residency, target lifetime
and presence/offline/relocation timers now use native SQL, a committed cache and
changed-row transactions. Actual settings remain in config/main/playerbots.properties.
Three state files were archived, independently verified against committed SQL and
retired; the optional manual world-population.json was absent. All existing bot
characters/progress/roster IDs and the 40 user settings remain preserved.

Pinned RandomPlayerbotMgr::FindEvent/SetEventValue/AssignAccountTypes, database
statements and factory/configuration dependencies were read before implementation.
Native import preserved 98 pools / 2100 IDs and events / 1385 residents / target
2952 exactly, retaining millisecond deadlines, pending custody and signed accounts.
Only the new population tables were written; no character/progress/ID mutation.
3000-world/120-budget/12000-reserve, 23/23 quotas, rotation/zone placement, checkpoint
pacing, shared behavior, native custody and safe shutdown remain.

Normal receipt: playerbots-source-build-20261010-104219-362220. SQL/file recovery:
playerbots-population-import-20261010-104131-210660, external server backups.
GS SHA 63d72e44740698bc5fdb0170c4802615c183d658716c47f62b295cc20a312942;
Commons unchanged. 79 offline sources / 39 suites / 8362 checks, five migration
failure-boundary and nine tooling tests pass. All 1757 warning-free handlers and
complete resource/static/linkage/preservation gates pass; 3591 prior JAR entries
and all 108 existing service methods remain identical. Only the service constructor
binds the SQL stores. One normal JAR copied; 2044 caches backed up/invalidated.
Post-install verifies 70 guards / 93 configs / 31 client hashes plus recovery/
source/builder hashes; inventory 20 checks / 74 client / 133 server receipts passes.
Both components remained closed; no startup/restart/attach or native actor/DB/ID
fixture occurred. Production SQL writes were limited to the requested schema/import.

PB-VAL-034 actual restart without legacy files, retained IDs/timers, admission/
rotation, shared gameplay and shutdown remains user testing. Individual SQL latency
remains possible; general event/context/class/world parity is still partial. The
next independent world/economy slice remains warehouse PB-SCOPE-004A; Ranger stays
separate. Do not re-port 012D solely for pending gameplay acceptance.
See [upstream mapping, migration and recovery](PLAYERBOTS_POPULATION_DATABASE_20261010.md).

| Validation | Current installation | User gameplay acceptance |
| --- | --- | --- |
| PB-VAL-034 | PB-SCOPE-012D installed; 69 JDBC / 8362 total offline checks, five migration and nine tooling tests; SQL import independently verified and legacy files retired | User restart without files, retained IDs/target/millisecond timers, global/manual admission/rotation, player-zone placement, combat/follow/quests and shutdown; SQL latency may remain |
| PB-VAL-033 | POP-013 installed; 64 scale / 8293 total offline checks, nine tooling tests and full preservation/static/linkage gates pass; all 40 live settings documented | Actual 2800–3000 population fill, both factions, reserve completion/rotation, retained save/restart identity, placement and host responsiveness; no live performance guarantee from offline checks |
| PB-VAL-032 | PB-PORT-RAID-012 installed; 1,221 raid checks / 8,229 total, 49 simulated real-browser and six tooling checks plus complete gates pass | Actual native sixth-companion conversion, retained human members, captain/vice-captain and full/special eligibility, all 23 active companions, follow/recall/support/combat and saved preset restart/reuse |
| PB-VAL-030 | PERF-010 installed; 25 JDBC batch / 22 deadline+creation checks and full gates pass | Population-scale checkpoint cadence, AI/chat/trade responsiveness, save/restart retention; individual SQL latency remains possible |
| PB-VAL-031 | UI-CREATE-011 installed; 33 real-browser checks with simulated data pass | Configured personal roster count, create at full party, pending/lowered caps, later recruit and persistent restart |
| PB-VAL-028 | PB-SCOPE-001C installed in cumulative 001D; 537 rotation checks and full gates pass | Finite reserve completion, target-preserving rotation, saved identity reuse/restart and failed native admissions |
| PB-VAL-029 | PB-SCOPE-001D installed; 144 zone checks and full gates pass | Encounters in an occupied suitable player zone, both factions/shared maps, reused entry and safe scheduled relocation |

## Player-zone world population — installed offline — 10 October 2026

**PB-SCOPE-001D** implements pinned `RandomTeleportForLevel` /
`GetPlayerZoneTeleportLocations` through native Aion geographic SUB zones.
The existing `population.concentrate_player_maps` setting now defaults true
by user request. Field placements prefer occupied real-player map/zone pairs,
retaining level/faction/map/landing guards and worldwide fallback. Existing
city probability remains separate. Visible staff playing normally participate;
hidden staff observers and instance players do not. Both factions are covered.
Reused global characters apply the same decision before visibility; existing
active bots retain safe scheduled relocation. Shared combat/quests/loot, saved
IDs/builds, manual/owned homes, caps and shutdown remain.

**001D and 001C are installed offline** through unchanged normal Maven output
using existing user authorization and fresh successful closed-component checks.
All 73 offline test sources / 34 suites / 6,961 checks pass, including 144 player-zone
and 537 rotation checks. All 1,757 warning-free handlers, 2,533 unchanged resources,
complete static-data/linkage and three method-preservation tooling checks pass.
3,587 prior JAR entries, 91 other service methods and 18 other placement methods
are preserved. All pinned source reference hashes verify.

Receipt: `playerbots-source-build-20261010-023929-118116`, external
`archives/server/game-server/backups`. GameServer SHA-256:
`91366e684396ddc6a7a635a417e1ccc0a439b495742487fafdabc47107f785a1`.
Commons remains `b7d6786f38696b9d81425cee04c4ebf16aa242d76e6c20c1754385168ec2cc6c`.
Only the normal GameServer JAR was copied. Post-install/source/recovery hashes,
70 guard files, 96 configuration files, 31 client hashes and 2,044 backed-up/
invalidated generated caches verify. Inventory passes 20 mod checks; 74 client /
125 server historical receipts remain. Both components remain closed; no start,
restart, attach, native World/SQL/ID fixture or database mutation was used.
The former standalone 001C review is preserved as evidence and superseded by
cumulative installed 001D. PB-VAL-029 player-zone entry/encounter/relocation,
028 reserve rotation/restart and 027 responsiveness remain user gameplay testing.

Full upstream parity remains partial. The next population dependency is
PB-SCOPE-012D native population event/account repository integration; private
warehouse/item usage and Ranger remain independent tracked slices. See the exact upstream/native mapping in
[the player-zone port record](PLAYERBOTS_PLAYER_ZONE_PORT_20261010.md).

## Full upstream port: persistent rotation — verified staged — 10 October 2026

The user renewed the requirement to port all applicable WoW Playerbots behavior,
using the pinned implementation/dependencies/configuration as the authority.
**PB-SCOPE-001C** is the next bounded population port, not a claim of full parity.
It imports maximum-times-ratio persistent factory capacity, shuffled account
cohorts, eligibility/native reuse and failed-login admission release. Default
ratio 2.0 provisions 2,000 persisted characters for the maximum 1,000 active;
existing saved IDs/builds remain and only the finite factory shortfall is created.
Full occupancy no longer blocks reserve provisioning. Native cooldown/readiness,
leases/custody, 1,000 world/100 companion caps and safe shutdown stay enforced.
Failed global entry releases its residence slot while retaining identity and a
completion-based 30-second retry; manual/owned lifecycle remains unchanged.

Fully verified and staged: 72 offline test sources / 33 suites / 6,817 checks,
including 537 rotation checks and 100 synthetic rotations. All 1,757 handlers
compile without warnings; 2,533 resources have no changes; complete static-data,
linkage and preservation gates pass. 3,588 prior JAR entries and 92 other service
methods are preserved. Four new pinned configuration/database dependencies and
all existing source references are hash verified.

Normal builder output SHA-256:
`3f358bfd1e9846edbd9532f7bb0733bdb14ac9e1396b308c19b8db2e70c3a414`.
Review: external `staging/output/release-20261010-021741-870189`.
Runtime remains installed PB-REPAIR-PERF-009 (`d752a8a2...`). GameServer was
restarted by the user; delivery requires fresh closed-component confirmation.
Only the normal GameServer JAR is planned for replacement; 2,044 generated
handler caches will be backed up and invalidated at delivery. No runtime files
were replaced. No component restart/attach/native World/DB/ID fixture was used.
PB-VAL-028 live pool completion/rotation/restart remains user testing.

Full parity remains partial. Native population event/account repository storage
is a subsequent 012 dependency; private warehouse/item-usage and Ranger remain
tracked slices, alongside unfinished travel/quest/class/economy/social/encounter
work. Do not substitute another inventory/re-audit for these implementations.
See [exact upstream flow, mapping and limits](PLAYERBOTS_ROTATION_PORT_20261010.md).

## Collision lag repair and logging bundle — installed offline — 10 October 2026

**PB-REPAIR-PERF-009 / PB-DIAG-SPAWN-007 / PB-DIAG-REPORT-008 are installed**
from unchanged normal Maven output after the user's explicit "Install it" request.
Bot walking probes now reject distant geometry outside their finite endpoints,
retaining native collision/terrain/door/floor/body safety. Successful entries show
`[spawned/target]`; automatic summaries print only changed lines, while manual
`.bot worldstats` retains its full snapshot. Combat, quests, rewards, saved IDs,
owned-alt builds, configuration, appearance-disabled state and shutdown remain.

Receipt: `playerbots-source-build-20261010-020621-826864`, external
`archives/server/game-server/backups`. Installed GameServer SHA:
`d752a8a2a38c82e5971b6f13ce1b2fe3af766ad8c644c47d7c1b6bb7be01a8d4`.
Commons unchanged. All 71 offline sources / 32 suites / 6,280 checks, 1,757
warning-free handlers, full resource/static-data/linkage and preservation gates
pass. 3,584 prior JAR entries remain identical. Fresh offline guards, backup,
installed hashes and rollback originals pass: one normal JAR copied; 2,044
generated caches backed up/invalidated; 70 guard files, all configuration and
31 client hashes preserved. Inventory: 20 checks, 74 client / 124 server receipts.
Both components remain closed; no restart/agent/native World/DB/ID fixture used.
The three authorized read-only thread dumps were a bounded diagnostic exception,
now exhausted. PB-VAL-026 console and PB-VAL-027 population-scale responsiveness
remain user gameplay acceptance. This fixes an evidenced collision hotspot;
other remaining lag sources are not declared resolved by offline tests.

Next evidenced population gap: timed presence has a pool equal to the active
target (806 identities, 713 residents and 93 offline cooldowns in the sampled
files), so it cannot replace departures from an eligible persistent reserve.
This reserve/admission repair is not included here. Full upstream world/class
parity remains partial; warehouse PB-SCOPE-004A and Ranger remain separate.
See [evidence, upstream mapping and limits](PLAYERBOTS_COLLISION_LAG_20261010.md).

## Population continuation repair — installed offline — 10 October 2026

**PB-REPAIR-POP-006 is installed** from unchanged normal Maven output. A pending
native load no longer consumes the next five-second population-manager interval;
successful creation/load resumes global/manual admission and saved loading at
the next existing bounded maintenance poll. One native initialization worker,
failure retries, saved identities, shutdown draining and all shared combat,
quest, loot and movement repairs remain. This addresses the user's large-count
spawn-delay evidence; actual native throughput remains user acceptance.

Receipt: `playerbots-source-build-20261010-010213-634062`, external
`archives/server/game-server/backups`. GameServer SHA-256:
`58bf3f792d3b8a8d61a61d871244c630a1cc6e110dc73c336555e611876d4bc5`;
Commons unchanged. All 70 offline sources / 31 suites / 5,833 checks, 1,757
warning-free handlers and complete resource/static-data/linkage gates pass.
3,589 prior JAR entries and 95 existing service methods remain unchanged; only
three population lifecycle methods and two added deadline helpers differ.
Only the normal GameServer JAR was copied; 0 generated caches invalidated.
Fresh offline process/hash/backup/recovery/preservation checks pass; installed
inventory passes 20 mod checks / 31 client hashes, 74 client / 123 server receipts.
No component startup/attach/native World/DB/ID fixture was used. PB-VAL-025
population fill timing and PB-VAL-024 shared gameplay/saved-ID reuse remain
user-tested. Full upstream parity remains partial. Continue from evidenced
shared-system gaps against pinned source; warehouse PB-SCOPE-004A and Ranger
remain separately tracked work, rather than displacing a demonstrated regression.
See [upstream flow, scheduling evidence and validation](PLAYERBOTS_POPULATION_PIPELINE_20261010.md).

## Shared behavior and persistent identity repair — installed offline — 10 October 2026

**PB-REPAIR-SHARED-001 is installed from unchanged normal Maven output.** Concrete
user gameplay evidence reopens shared movement/response and world activity
integration ahead of the independent warehouse backlog. Per-character reaction
deadlines replace the population-dependent batch sleep; native floor tolerance
and one-pass support retain obstacle guards. Native training dummies are excluded
from grind/field bands; quest intent precedes unrelated grinding. An idle resident
without any reachable activity can use the guarded field relocation, preserving
its character. Creation fills only a genuine persistent-pool shortfall; timed
absence/restart reuses native database characters instead of allocating
replacement identities. Both modes retain the same combat/movement/loot/quest
engine and all prior mods. All 69 offline check sources / 30 suites / 5,820 checks, 1,757 warning-free
handlers, complete static-data/linkage and preservation gates pass. 3,568 prior
JAR entries and every existing GeoMap method remain unchanged. The installed
normal GameServer JAR SHA is 22ae677f9f39b924a65475956bef9fe216d06e1b6ee203dca4f1493af94bf7d9.
Receipt: `playerbots-source-build-20261010-005234-053007`, external
`archives/server/game-server/backups`. Only the normal GameServer JAR was copied;
2,044 generated handler caches were backed up and invalidated. Fresh closed-process,
source/artifact/hash/backup and preservation guards pass; installed inventory passes
20 mod checks / 31 client hashes, with 74 client / 122 server historical receipts.
Commons remains unchanged. All WORLD-004 / NAV-005 and earlier features remain.
No startup, attach, native world, database or ID fixture was run. PB-VAL-024 is
user gameplay acceptance; full upstream parity remains partial.
See [exact source mapping and repair evidence](PLAYERBOTS_SHARED_REPAIR_20261010.md).


## World population and navigation repair — installed offline 10 October 2026

**PB-REPAIR-WORLD-004 / PB-REPAIR-NAV-005 are installed** from unchanged normal
Maven output. World population has its independent 1,000-resident cap; the
100-companion quota remains separate. Failed admissions retry after 30 seconds
without blocking other residents. Console spawn messages and 30-second summaries
report actual spawned bots, target/remaining count, factions, activities and maps.
`.bot worldstats` and paged `.bot worldlist [page] [here]` expose these counts and
coordinates. Distant local detours, supported body-clear walking, safe population
landings, five-metre service camps, saved-position repair before visibility and
upstream destination visitor limits/point spreading repair field activity travel.
Native combat, loot, quests and earned progression remain responsible for rewards.
User defaults (800–1,000 target, timed presence on, city chance 35%) are retained;
no saved character or owned-alt build is rerolled. All prior mods/shutdown repairs
remain; appearance stays disabled and the override remains retired.

Receipt: `playerbots-source-build-20261010-000108-172472` under external
`archives/server/game-server/backups`. GameServer SHA `e1a2cfc01bc31816c1de4056dd80f033a1cb9fd15efe74f60c80d24a4ff7e74f`;
Commons unchanged `b7d6786f38696b9d81425cee04c4ebf16aa242d76e6c20c1754385168ec2cc6c`. All 68 offline test sources / 29 suites / 5,674
checks, 1,757 warning-free handlers, complete static-data/resource/linkage gates
and three method-preservation tests pass; 3,567 prior JAR entries are
byte-identical. Only the normal GameServer JAR and builder's Bot.java were copied;
0 generated caches were backed up/invalidated. Fresh process/hash/backup/rollback
and inventory gates pass (20 mod checks / 31 client hashes). No startup, attach,
world actor, database or native ID fixture was used. **PB-VAL-023** actual
population above 100, both factions, field combat/XP/loot/quest progress, obstacle
movement and 1,000-bot performance remain user gameplay acceptance. Full world
and class parity remains partial. Next independent world/economy work remains
PB-SCOPE-004A private warehouse; Ranger remains separate. See
[exact upstream mapping and validation](PLAYERBOTS_WORLD_REPAIR_20261009.md).

## Safe bot shutdown repair — installed offline 9 October 2026

**PB-REPAIR-WORLD-003 is installed** through unchanged normal Maven output.
Headless bots no longer force the existing 120-second human grace period or
block staff-only fast shutdown. Native creation/load is drained outside the
service monitor, then every bot is saved and logged out before NIO/pool closure.
Failed saves retain reservations and retry; shutdown waits rather than dropping
unsaved progress. Pinned WoW shutdown/login/logout dependencies were read and
blob verified. All earlier population, class, gear, trade, invitation and compiler
repairs remain; appearance stays disabled and the override remains retired.

All 67 test sources / 28 world-free suites / 5,644 checks, 1,757 warning-free
handlers, complete static-data and linkage gates pass. Service's other 73 methods
and 3,582 prior JAR entries are unchanged. Only the normal GameServer JAR was
copied; 2,044 generated handler caches were backed up/invalidated. Fresh process,
backup, hash, rollback and inventory checks pass: 20 mod checks / 31 client hashes,
74 client / 120 server receipts. User-updated system.cfg was preserved after a
full inventory refresh; initial guard refusal copied nothing. No components were
started/attached and no native world/DB/ID fixtures were run.

Receipt: `playerbots-source-build-20261009-233127-865286`, external
`archives/server/game-server/backups`. GameServer SHA-256:
`433e2d250018719e37af7a1d3d184060088a985e13f3cc99cb4676f9b71c6349`.
Commons is unchanged. PB-VAL-022 actual shutdown/save/restart acceptance remains
user testing. The separate missing-in-game report awaits `.bot worldlist` evidence.
See [shutdown mapping and validation](PLAYERBOTS_SHUTDOWN_20261009.md).
Next forward port remains PB-SCOPE-004A; Ranger stays separate.

## Starter placement and handler compiler repairs — installed 9 October 2026

**PB-REPAIR-WORLD-002 and BUILD-REPAIR-HANDLERS-001 are installed** through the
canonical normal Maven release. Native race starting positions remain preferred
hubs for levels 1..5, correcting the reproduced Elyos level-3 omission. All native
placement safety gates remain. The 38 deprecated help implementations and one
unchecked handler cast are repaired; all 1,757 handlers compile with
`-Xlint:deprecation,unchecked -Werror` and zero warnings.

Baseline regression failed at Elyos level 3; the repaired real catalog passes all
10 starter selections and 130 faction/level combinations. Three focused suites
pass 3,179 checks, with 65 test sources compiled. Complete static data and linkage
pass. Only Placement.locations changes; 18 other methods and 3,586 prior JAR
entries are preserved. Guarded backup/copy/rollback delivery installed the normal
GameServer JAR and 39 handlers, archiving/invalidation of 2,044 generated handler
cache files. Inventory: 20 mod checks / 31 client hashes; 74 client / 119 server
receipts. Components were verified closed and remain off.

Receipt: `playerbots-source-build-20261009-225816-372177` under external
`archives/server/game-server/backups`. GameServer SHA-256:
`6701269ad1f5e711b7e089951431b55136358e54c75aa6ba6d36b683932b5abf`. Commons unchanged.

PB-VAL-021 startup/low-level creation and command help remain user testing; no
startup, attach, native world actor, database or ID fixture was run. Next forward
port remains **PB-SCOPE-004A private warehouse decisions**; Ranger stays separate.
See [placement repair](PLAYERBOTS_STARTER_PLACEMENT_20261009.md) and
[handler repair](HANDLER_COMPILER_REPAIR_20261009.md).

## World performance and native alliance repair - installed 9 October 2026

**PB-REPAIR-WORLD-001 is implemented and installed** through unchanged normal Maven output. It repairs measured multi-second population callbacks and chat/trade lock contention, adds missing headless native alliance acceptance, and makes trade refusals specific. Creation/loading use one background native job; callbacks reschedule after completion; ticks release the service lock between bots; destination scoring uses one fresh route table per decision. Existing population/progression/appearance/gear/travel/trade and all prior class/mod behavior remain.

Pinned WoW RandomPlayerbotMgr, TravelMgr, AcceptInvitationAction, TradeAction, TradeStatusAction and newly downloaded blob-verified PlayerbotSecurity.cpp were read and mapped to native Aion services before implementation. Public priced world-bot trading is the user's deliberate policy broader than upstream master/group access; discounts/crafting remain partial. Native team restrictions, ownership, recipients, range/combat and durable custody remain authoritative.

All **26 world-free suites / 5,614 checks**, 65 companion test sources, complete normal build/resources/handlers/static-data/linkage and three release method-preservation tests pass. Real catalog route benchmark: 60 passes, 1,043,400 scores, 6,120 permission callbacks, 111ms. This is offline planning verification, not live server latency acceptance. **PB-VAL-020** is user startup/latency, party/alliance join/follow/release and trading acceptance; 018/019 remain pending.

Receipt: `playerbots-source-build-20261009-185835-746215`, external `archives/server/game-server/backups`. GameServer SHA: `ba4b121bf182ae5bedecb55cd7e8d631ca250f7c550454a72e07340143a15787`; Commons unchanged. 3,565 prior JAR entries remain byte-identical; 3,319 effective classes / 176,708 member references link; 1,757 handlers compile; 2,533 managed resources have no changes. Fresh offline installation/backup/hash/rollback and inventory passed: 20 mod checks / 31 client hashes, 74 client / 118 server receipts. Only GameServer JAR copied. No startup/attach/native world/casts/DB/IDs; components remain off.

See [root cause, exact mapping and acceptance](PLAYERBOTS_WORLD_PERFORMANCE_20261009.md). Next forward world/economy review remains **PB-SCOPE-004A private warehouse decisions**; Ranger is a separate class queue. Do not re-port installed features solely because gameplay remains user-tested.

## Previous global population layer — installed offline 9 October 2026

**PB-SCOPE-001B is implemented and installed** from unchanged normal Maven output. It adds a server-wide random target, dedicated headless candidate pools, faction-balanced admissions, configurable initial level distribution, persisted presence/relocation events and level-appropriate native world placement. PB-SCOPE-001A's quests/combat, earned progression, gear, public trade, invitations/control release, randomized creation looks and headless teleport completion remain installed. Full upstream parity remains partial; **PB-VAL-018/019 gameplay acceptance is pending user testing**.

Defaults: 40–60 global residents, levels 1–65, approximately 50% Elyos, up to 12 admissions/creations per maintenance interval, eligible native open-world maps. Initial random levels/gear are applied only to newly created server-pool characters; saved residents and owned alts are not rerolled. Target counts change every 30–120 minutes; safe idle relocation every 1–5 hours or after earned level changes. Optional timed presence is disabled by default, matching upstream permanent-presence mode. Existing manual `.bot worldpool` pools retain level-one creation.

Receipt: `playerbots-source-build-20261009-181729-863984` under external `archives/server/game-server/backups`. GameServer SHA-256: `c5967690b13a6ac90846bccbae64b2f718aa468c06f4aabc53efebafa564615f`; Commons unchanged. All 25 world-free suites / 2,620 checks, complete resource/handler/static-data/linkage gates and 20 installed-mod checks / 31 client hashes pass. Only the normal GameServer JAR was copied; no managed data or client changes. Components remain off; no startup/attach/live world/database/ID fixtures.

Read [exact upstream mapping, defaults, preservation and remaining differences](PLAYERBOTS_POPULATION_GAP_20261009.md). The active direction remains the world/economy dependency plan. Next bounded source review is **PB-SCOPE-004A: native private warehouse decisions**, using pinned BankAction/item-usage/service dependencies before implementation; mail, shared legion storage and professions remain separate slices. Ranger remains a separate class queue. Gameplay acceptance does not require re-porting already installed behavior.


## Previous PB-SCOPE-001A delivery — 9 October 2026

**PB-SCOPE-001A** is the current user-requested implementation: persistent level-one population, autonomous native quest/combat progression and travel, earned gear management, native invitations/control release, public priced trades and randomized creation appearance. The missing headless teleport/map-entry and XP/Ascension integrations are necessary dependencies. Installed offline from unchanged normal builder output on 9 October 2026. **PB-VAL-018 gameplay remains user-tested.** See [behavior, commands, mapping and verification](PLAYERBOTS_WORLD_20261009.md).

This is the historical 001A delivery record. The current 001B installation and next-work pointer are recorded above; preserve both slices. PB-VAL-018 gameplay remains pending.

Receipt: `playerbots-source-build-20261009-171332-276258` under external `archives/server/game-server/backups`. GameServer SHA-256: `889085448588d0049bbde35982cd591e53034d6bbf3539f9b228221f35385354`. Commons remains `b7d6786f38696b9d81425cee04c4ebf16aa242d76e6c20c1754385168ec2cc6c`.

All 22 world-free suites pass (2,478 checks, 63 test sources), including 102 world lifecycle/planning, 90 public trade and 32 progression/appearance checks. Complete Maven build, 1,757 handler compilation, 2,533 managed-resource audit, production static-data load and linkage of 3,307 classes / 175,145 executable member references pass. 3,515 prior JAR entries remain byte-identical. Only the normal GameServer JAR and builder-produced Bot.java handler were copied. All 20 installed-mod checks and 31 client hashes pass; current inventory records 74 client / 116 server receipts.

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
