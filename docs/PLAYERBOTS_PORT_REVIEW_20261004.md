# Focused WoW Playerbots port revision — 4 October 2026

## Current cumulative release - LEGION-029 / CASSIUS-030 - 10 October 2026

Installed PB-REPAIR-LEGION-029 supplies native roster presence for active companions and independent bots, native invitation/entry/logout/transfer updates, despawned save-held actor exclusion and players.last_online persistence. Human flags, membership/rights/custody and existing behavior remain. CASSIUS-030 corrects one underground Heiron spawn to matching-client XY422.566071/926.967834 and supported native ground Z124.968643; stationary guard/talk/quest16904 role remains. No unsupported custom AI or linked partner is invented.

External receipt: playerbots-source-build-20261010-233223-856383; review: release-20261010-232624-253950. GameServer SHA:
f582272d828b20b643941f2a927020d6c4c88aab3f05480e944fd1e828d23670.
95 fixture sources / 57 artifact-bound suites / 62,960 checks and 22 tooling tests pass. All 1,757 handlers compile warning-free; complete XML/schema/JAXB and 3,370-class / 183,234-reference linkage pass. All 56 original / 69 pinned scope hashes verify. 3,621 prior JAR entries, 150 other bot-service / 68 other LegionService methods remain identical; public APIs and existing anonymous bodies remain. Complete normal Maven JAR and spawn XML copied unchanged; Commons unchanged. Two static-data caches backed up/invalidated; zero handler caches present.

Independent installed verification passes exact source/builder/recovery/static/linkage/regression evidence and 70 guards / 93 configs / 31 client hashes. Inventory passes 20 checks / 74 client / 153 historical server receipts. All 43 explicit settings retain worldcap1500, target1200..1500, budget120, controlledcap3000, personal23/23, city0 and disabled appearance. No lifecycle/attach/dump/gameplay/production SQL writes or native World/ID fixture occurred. Fresh offline guards passed; GameServer/Aion remain closed and Login/Chat untouched.

PB-VAL-050 legion presence and PB-VAL-051 Cassius acceptance remain user gameplay. Full quest audit and wider Playerbots parity remain unfinished engineering; this release does not narrow either request. See docs/PLAYERBOTS_LEGION_PRESENCE_20261010.md and docs/CASSIUS_PLACEMENT_20261010.md.

## Previous cumulative release - QUEST-027 / NAMELOOKUP-028 - 10 October 2026

Source -> complete normal build -> guarded offline delivery -> independent
verification passes. Receipt:playerbots-source-build-20261010-231349-474859;
review:release-20261010-224757-712697. All56 bound suites/62916 checks pass.
Central shared name resolution preserves spelling/native restrictions and fixes
reported mixed-case legion targets plus other World name callers. Coordinated
handler capability diagnostics and accepted ItemOrders continuation are included.
All prior mods/settings/IDs/progress remain; no lifecycle/gameplay/SQL writes.
PB-VAL-049 is user name-lookup acceptance. Full audit quest-chain engineering
remains open. [Exact delivery](PLAYER_NAME_LOOKUP_20261010.md) /
[quest boundaries](PLAYERBOTS_QUEST_CAPABILITIES_20261010.md).

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

## Current next work: full audit remediation remains active

Continue the unfinished autonomous native quest finding under the existing full
audit-fix authorization. The installed AUDIT-022 release is a completed step, not
closure of the full request. Use the [active audit checklist](PLAYERBOTS_PORT_AUDIT_20261010.md)
for actual handler/chain coverage, missing specialized/custom/campaign actions,
specific unsupported reasons/fallback activities, complete chain checks and normal
guarded delivery. Do not substitute pending gameplay for missing source behavior
or move necessary dependencies into an optional future port.

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
companions23/23; all43 values remain. Source/runtime paths unchanged; external
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
backs up and explains all43 settings, preserving all40 explicit user values,
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

## PB-REPAIR-POP-013 — installed offline — 10 October 2026

Read pinned RandomPlayerbotMgr::UpdateAIInternal/AddRandomBots, factory
CalculateTotalAccountCount and configuration before edits. Reproduced old-JAR
target/budget/state/factory rejection. Expanded linked native bounds to
3000 active / 120 admission budget / 12000 reserve, retaining existing scheduling,
eligibility, identity and ownership. Every original account mapping is unchanged.
All 40 settings have inline maintained/live documentation; user values preserved.
Complete normal Maven JAR installed as `playerbots-source-build-20261010-100658-476969`;
8293 offline checks / nine tooling tests and full gates pass, 3592 old entries and
108 other service methods unchanged. Actual 3000-actor performance is PB-VAL-033.
Full parity and 012D/004A/Ranger remain unfinished. See
[configuration, upstream/native decisions and receipts](PLAYERBOTS_CONFIG_SCALE_20261010.md).

## PB-PORT-RAID-012 — installed offline — 10 October 2026

Read pinned PlayerbotMgr::OnBotLoginInternal, GroupInviteOperation::Execute,
invitation/security/leave/configuration and Formation/MoveFormation dependencies.
Reproduced installed owned-alliance rejection, then added native full-party
conversion and confirmed membership, 23-companion presets/formations/menu.
Installed active/saved quotas 23/23; native leadership/humans/special-group/
recipient/ownership/combat/global/custody gates retained. Complete normal Maven
JAR receipt `playerbots-source-build-20261010-031529-868535`; 8,229 offline,
49 simulated real-browser and six release-tool checks plus full gates pass.
Native admission boundary is simulated; PB-VAL-032 remains actual user gameplay.
Full raid/world/class parity remains partial. See
[mapping, deliberate differences and preservation](PLAYERBOTS_RAID_PORT_20261010.md).

## PB-REPAIR-PERF-010 / PB-UI-CREATE-011 — installed offline — 10 October 2026

Read pinned RandomPlayerbotMgr candidate/eligibility/budget flow, PlayerbotMgr
native save delegation and configuration against current Aion behavior. Batch
roster/archive SQL and staggered complete checkpoints are deliberate native
Aion repairs; no asynchronous core-save or full parity claim. Menu capacity
uses the existing generated.max_per_account setting independently of active
party slots. All 001C/001D and earlier mods remain; 7,008 checks / 36 suites and
33 offline browser checks pass. Canonical receipt
`playerbots-source-build-20261010-025721-771968`. PB-VAL-030/031 remain user tested.
See [exact source mapping and boundaries](PLAYERBOTS_CHECKPOINT_ROSTER_20261010.md).

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

PB-BUILD-002 repairs the startup failure that prevented testing NAV-004: the
complete source-built JAR and maintained box XML/schema/handlers now agree.
Receipt `playerbots-source-build-20261008-220257-711786`; the full actual runtime
static-data merge/schema/JAXB load passes offline (102,012 items / 4,091 boxes /
470 overrides). Every non-manifest JAR entry remains byte-identical, including
the movement repair. PB-VAL-005 gameplay remains pending user testing; independent
next class review remains PB-PORT-005C Cleric. See
[delivery repair and mandatory complete-data gate](SOURCE_BUILD_STATIC_DATA_20261008.md).

## Current shared ground recovery - installed offline 8 October 2026

**PB-REPAIR-NAV-004** retains intended destination altitude throughout bot
planning/follow/controller probes. Unsupported elevated origins recover only
onto verified floor after clear vertical sweeps, while normal edge/door guards
remain. Complete Maven GameServer output was copied unchanged in receipt
`playerbots-source-build-20261008-214740-770720`. Actual mesh movement traces,
57 existing ground/door/route checks and 3,281-class runtime linkage pass;
3,542 unrelated JAR entries remain byte-identical. Installed inventory passes
20 checks/31 client hashes. **PB-VAL-005 actual gameplay remains pending.**
Next independent class review remains **PB-PORT-005C Cleric**. See
[shared repair and delivery evidence](PLAYERBOTS_GROUND_RECOVERY_20261008.md).

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
| PB-PORT-005H | OPEN — gap review next | Ranger ranged positioning, chains, control, buffs and recovery; compare current code with pinned Hunter before choosing missing behavior. | Preserve installed classes and owned-alt builds. |
| PB-SCOPE-001B | INSTALLED — normal builder output; gameplay pending | Global random target, dedicated pool/faction admissions, initial levels and native placement, persisted idle/presence/teleport events; receipt 20261009-181729-863984. | PB-VAL-019; exact manager/factory/config/TravelMgr mapping documented. Existing residents/alts preserved; full scope partial. |
| PB-SCOPE-001C | INSTALLED — cumulative normal builder output; gameplay pending | Maximum-times-ratio persistent reserve factory, shuffled account cohorts, native saved-character reuse and failed-admission slot release; receipt 20261010-023929-118116. | PB-VAL-028; event/account repository migration remains 012D. |
| PB-SCOPE-001D | INSTALLED — normal builder output; gameplay pending | Occupied native geographic player-zone placement, worldwide fallback and reused-character pre-visibility entry; 001C reserve port retained in same receipt. | PB-VAL-029; native level/faction/landing/visibility/city and controlled-session guards remain. |
| PB-PORT-005G | INSTALLED — normal builder output; gameplay pending | Assassin single-target chain fitness bounded below mature/urgent rune finishers and existing defenses; 123 checks, installed-baseline gap reproduced. Receipt 20261009-061641-083362. | PB-VAL-017 pending; native rune/poison/chain/cost/cooldown/threat/interrupt/fallback preserved. Next separate review 005H Ranger. See PLAYERBOTS_ASSASSIN_20261009.md. |
| PB-PORT-005F | INSTALLED — normal builder output; gameplay pending | Gladiator native direct physical counter-window priority; 66 checks, installed-baseline defect reproduced first. Receipt 20261009-055725-016513. | PB-VAL-016 pending; parry/cost/cooldown/chain/threat/AoE/drain/fallback preserved. Full Gladiator strategy partial; 005G rune/chain ordering installed; next separate review 005H Ranger. See PLAYERBOTS_GLADIATOR_20261009.md. |
| PB-PORT-005E | INSTALLED — normal builder output; gameplay pending | Templar proactive block and low-health native pressure shields, including hybrid cleanse/shield admission; 71 checks. Receipt 20261009-053655-481400. | PB-VAL-015 pending; existing taunts/chains/reservations/active shields preserved. 005F counters installed; 005G rune/chain ordering installed; next separate review 005H Ranger. Full Templar strategy partial. See PLAYERBOTS_TEMPLAR_20261009.md. |
| PB-PORT-005D | INSTALLED — normal builder output; gameplay pending | Chanter missing-mantra combat admission and native nearby-party support ranking; 89 checks. Existing active set/three slots/healing/melee preserved. Receipt 20261009-051146-587613. | PB-VAL-014 pending; 005E pressure protection installed, 005F installed; 005G rune/chain ordering installed; next separate review 005H Ranger. Full Chanter assignment/replacement strategy remains partial. See PLAYERBOTS_CHANTER_20261009.md. |
| PB-PORT-005C | INSTALLED — normal builder output; gameplay pending | Cleric first-heal timing and native conditional thresholds connected to final scoring; 87 checks; group/reservations/Chanter preserved. Receipt 20261009-045131-933181. | PB-VAL-013 pending; 005D mantra slice installed, 005E/005F installed; 005G rune/chain ordering installed; next separate review 005H Ranger. See PLAYERBOTS_CLERIC_20261009.md. |
| PB-PORT-005B | INSTALLED - normal Maven output; gameplay pending | Confirmed missing Spiritmaster restorative single-target/DoT/native chain ordering; existing pet/utility adapters kept. | PB-VAL-012; 005C recovery and 005D mantra slices installed; 005E/005F installed; 005G rune/chain ordering installed; next separate review 005H Ranger. See PLAYERBOTS_SPIRITMASTER_20261008.md. |
| PB-BUILD-001 | INSTALLED - full source/build/runtime checked | Complete normal builder JARs replace cumulative override; unfinished outfit source gated off. | Future delivery uses source -> builder -> copy; preserve native mods/settings. |
| PB-REPAIR-ENGINE-002 | INSTALLED - source/effective verified; gameplay pending | Completed ITEM_USE tasks no longer fabricate movement and fail stationary casts; native selective item cancellation and active-task gates. Receipt 201132-984909. | PB-VAL-009 reported ranged casters; PB-PORT-005B remains independent. See PLAYERBOTS_ITEM_TASK_CASTING_20261008.md. |
| PB-REPAIR-ENGINE-001 | INSTALLED - source/effective verified; gameplay pending | Corrected stale mover/cast race, repeated order cancellation and unchanged-preference continuer loss in core execution. | PB-VAL-009; PB-PORT-005B independent next class slice. See PLAYERBOTS_CAST_EXECUTION_20261008.md. |
| PB-SCOPE-007A-R1 | INSTALLED - offline verified; gameplay pending | Original Steel Rake tower/staircase/amplifier/root/pull-blast and gunner-pause behaviors implemented; original comments retained. Exact retail capture evidence unavailable. | Existing encounter/hazard/role engine; 479 isolated checks. PB-PORT-005B remains independent next class slice. See PLAYERBOTS_STEEL_RAKE_20261007.md. |
| 1 · PB-PORT-001 | INSTALLED — offline verified; native pending | Native finisher cast/MP/consumption acceptance. Final resource gate is corrected. | PB-VAL-001; actual cast acceptance still outstanding. |
| 2 · PB-PORT-002 | INSTALLED — offline verified; native pending | Native refresh/hybrid/stronger-effect cast acceptance. Final DAMAGE veto is corrected. | PB-VAL-001; actual cast acceptance still outstanding. |
| 3 · PB-PORT-003 | INSTALLED — offline verified; native pending | Validate complete native tick/geodata/interactions through the committed objective; local executor gates are connected. | PB-VAL-002; preserve native handlers/witnesses/loot and no quest progress bypass. |
| 4 · PB-PORT-004 | INSTALLED — offline/loaded-engine verified; client combat pending | State strategies/defaults, native threat policy, weighted expansion and freshly resolved continuers installed in 213711-193912. Validate actual native chain casts/transitions. | Latest position receipt 211935-035574 retained; PB-VAL-009. Next implementation 005. |
| 5 · PB-PORT-005 | OPEN — partial; 005A..005G bounded slices installed | Sorcerer ONLYONE chain/upkeep/filler/MP/boost strategy installed `234841-695964`; native/client pending. 005B confirmed missing Spiritmaster single-target/recovery ordering installed from normal source build; existing pets preserved. 005C recovery, 005D mantra support, 005E pressure protection and 005F Gladiator counter-window ordering installed/offline verified; 005G Assassin ordering installed/offline verified; next separate review 005H Ranger. Broader class coverage remains open. | PB-PORT-004 installed; cached GenericWarlock/AfflictionWarlock mapping and native pet gates for 005B; necessary pet coordination PB-PORT-008. |
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
| PB-VAL-005 | Smooth ground/flight travel, all four formations, regrouping, corners, combat positioning, doors and summon controls | Latest NAV-004 ground recovery installed as normal source-built JAR `214740-770720`; intended Z retained; reported native mesh traces and 57 floor/door/route checks pass. NAV-002/003, formation, flight, Summon and early 60m recall preserved. | User actual rendered movement, whole-party formation and summon/dungeon acceptance pending. |
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
| PB-REPAIR-NAV-004 | Preserve destination altitude and recover unsupported elevated bot origins with verified vertical ground sweeps | Installed offline complete normal Maven JAR `214740-770720`; actual reported mesh traces complete at four movement step sizes; 57 floor/door/route checks and full linkage pass; 3,542 unrelated entries preserved | PB-VAL-005 user follow, movement and other-location acceptance; no coordinate exceptions |
| PB-BUILD-002 | Align normal source-built GameServer with its required static XML/schema and command resources | Installed offline `220257-711786`; full actual static-data merge/schema/JAXB loading passes; all non-manifest JAR entries including NAV-004 retained byte-identically | User server startup and gameplay acceptance; complete static-data load required for future JAR deliveries |
| PB-BUILD-003 | Complete source/build/resource delivery and cross-chat release guards | Installed offline `225416-105523`; all ten remaining resource differences delivered; complete static load, all handler compilation, 14 suites and linkage pass | User startup/gameplay acceptance; canonical locked release required |
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
