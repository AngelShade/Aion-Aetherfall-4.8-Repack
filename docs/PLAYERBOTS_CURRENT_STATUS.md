# Playerbots current work map

Reviewed 18 October 2026. Start here for current priorities. Scope, subsystem and
validation documents retain dated evidence; their older “current”/“next” headings
are historical and must not override later source or delivery receipts.

This is a focused source, receipt and log audit, not a new full upstream parity
audit or gameplay certification. No gameplay code, configuration, population,
database or runtime file was changed by this review. No build or tests were run.
Main reviewed movement/lifecycle and reconciled the tracker; Secondary Playerbots
Implementation independently reviewed quest crafting/material/item boundaries.

## Verified baseline

- Root `release.json` is **0.93**. This label alone does not identify the latest
  installed Playerbots code; subsequent affected-file repairs have separate receipts.
- Latest original GameServer JAR matches SHA-256
  `4f259ab2b81e35bfe90ec30331629ac6e1f69c25a4d823f5030c43f06ce6ce1a` and external
  `archives/server/backups/companion-direct-pursuit-20261018-retry1/manifest.json`.
  The follow-up installs companion direct pursuit ahead of prepared journeys and
  shared world planner admission, retaining fast catch-up and immutable travel
  queries. Details and acceptance are linked below.
  Earlier lift/warning and continuity repairs are included, not awaiting delivery.
- Bounded original log observation: **21:21:30–21:25:40**, 618 lines, no ERROR or
  Playerbots WARN. Population was still loading (276/411 in the captured report),
  with five companions. This short startup interval does not establish smooth
  movement, successful revival, full population performance or absence of later errors.
- Repack/test-repack state was not refreshed or changed. This map concerns source
  and the original development installation only.

Evidence: **A** observed live symptom/result; **B** current source or installed
artifact/offline evidence; **C** user report not reproduced on the latest build;
**D** unknown/not reviewed. A delivery can be B while its live result remains C/D.
“Installed; acceptance open” is not missing code and is not permission to re-port it.

## Immediate repair and acceptance queue

**19 October Find Group extension:** all three native tabs now have a separate
core social service for real quest/XP needs, native party invitations and chat
about starting, waiting, helping, leadership and departure. Normal final build and
130 inert checks passed; installed on the original after normal shutdown saved all
469 bots. Source/build/baseline guards, backups and two copied hashes passed.
Current movement/combat
classes are unchanged. Entrance eligibility and the existing local-party instance
flow remain authoritative; cross-server ready-check/teleport is not included.
See [Find Group behavior and acceptance](PLAYERBOTS_FIND_GROUP_20261019.md) and
`project-notes/current-state.md` for exact package/validation pointers.
Owner reported an empty Find Group window on this installed version. A follow-up
removes the unintended dependency on a selected movement goal: real local quest
or below-cap leveling needs can be advertised while in town/dormant. Installed
build fails the added no-goal regression; repaired normal build passes 138 inert
checks. Bounded startup/posting counts added. Original-only repair installed after
normal shutdown saved all 474 bots; backup and copied hash verified. Actual
visible-post acceptance remains open; exact receipt is in current-state.
Latest follow-up: owner saw field posts but solo organizers/empty instance tab.
Native bot-to-bot grouping now gathers compatible quest/XP parties, reserves human
space and publishes actual native team IDs/counts. Autonomous party members share
existing combat/support and gather behind their organizer through normal navigation.
Instance organizers choose a verified same-map portal and walk there before posting.
Human control and chat retain their existing flow. Normal delivery build and 170
focused inert/native-model checks passed; original JAR installed with closure,
backup and copied hash after all 488 bots saved normally. Actual gathering,
populated native listings and cooperative gameplay remain owner acceptance.
Receipt: external `archives/server/backups/playerbots-cooperative-findgroup-20261019/receipt.json`.
Latest publication/invitation repair is installed on original GameServer and
ChatServer: actual subscribed faction LFG channel announcements now carry native
bot identities and current offer/member counts, staggered at one per faction per
20 seconds. Removed bot-only combat-timer rejection and shared invitation cooldown;
native admissions apply in both directions. Normal two-component build and 27
focused inert channel/bridge/invitation checks passed, including independent
applicants and retained native dead-player rejection. Both processes closed;
external backups and both copied hashes verified. Existing cooperative grouping,
entrance planning and all three list paths retained. Live LFG chat display,
invitation acceptance and cooperative gameplay remain owner acceptance.
Receipt: external `archives/server/backups/playerbots-lfg-publication-invites-20261019/receipt.json`.

**19 October architecture rework (latest, supersedes the follow notes below):**
after repeated point fixes, the companion design was reviewed against WoW
Playerbots. Following is now a persistent follow motion owned by the native
mover (WoW MoveFollow), not AI-renewed planner leases; companion combat gates use
actual party attackers instead of the 10-second combat timer; one follow rule
serves every follow site; companions travel behind a moving owner. Full
navigation and the 5 s stuck regroup remain as fallbacks. Installed in the
original GameServer and committed to 4.8 (`75d9dd1`). **Owner accepted companion
following in game on 19 October ("works perfectly")**; keep this design as the
baseline for PB-NOW-01/02. Next: world-bot travel continuity (built, awaiting
install) and the companion/world decision-loop split.
Details and next slices: [architecture review](PLAYERBOTS_ARCHITECTURE_REVIEW_20261019.md).
Receipt: external `archives/server/backups/companion-follow-motion-20261019/manifest.json`.

19 October blocked-follow report: nearby companions could remain stuck until
the 60 m recall or manual summon. Owner authorized ordinary walking/jumping first,
then teleport if still stuck. Added five-second physical-progress recovery through
native nearby placement, preserving distance recall and combat/action/transport
custody. Normal build, 334 focused checks and original-only copied hashes passed.
Actual obstacle recovery remains PB-NOW-01 acceptance. Receipt: external
`archives/server/backups/companion-stuck-regroup-20261019/manifest.json`.

19 October follow-up: companion scheduling is now independent of the world
population's scan, worker saturation, terrain debt, prepared-query slots and map
loading queue. Separate companion saves retain native per-character exclusion.
Invited world bots and summons follow the current control domain. Installed in
the original GameServer after normal build, focused isolation/budget/shutdown
checks and copied hash verification. See [scheduling evidence](PLAYERBOTS_COMPANION_SCHEDULING_20261019.md).
Actual following/combat and responsiveness under population load remain open.
The 18 October baseline/log paragraph above is historical; latest binary/receipt
are in `project-notes/current-state.md` and `docs/INSTALLED_MODS.json`.

Latest owner direction: very fast companion catch-up is authorized; a small live
CPU reduction is reported but remaining CPU is high near 500 bots / 3.8 GB RAM.
The follow-up was built and installed on the original; evidence is tracked in
[fast catch-up and destination queries](PLAYERBOTS_FAST_CATCHUP_20261018.md).
This report supersedes “no live improvement measured” as user feedback; no
controlled before/after CPU comparison is available.

Follow-up report: companions still separated around Artisans Hall; LeMuse's menu
showed an outstanding terrain-work wait. The companion-only direct pursuit,
obstruction fallback and travel handoff repair is installed, tracked in
[companion pursuit](PLAYERBOTS_COMPANION_PURSUIT_20261018.md). The fast speed patch
does not by itself close the movement/admission issue. No new elevator rework or
independent world-routing change is authorized by this report.

| ID / priority | Current issue and state | Evidence / source ownership | Next action and completion boundary |
| --- | --- | --- | --- |
| PB-NOW-01 / P1 | **Companions lag, overlap or stand still.** Multiple real regressions were repaired; continuous following is installed, but latest gameplay outcome is not established. | Earlier live floor mismatch and queue starvation; B latest `PlayerBotSession`, `PlayerBotFormation`, `PlayerBotNavigationTrail`, `PlayerBotNavigation`, native `PlayerBotMoveController`. [Follow evidence](PLAYERBOTS_COMPANION_FOLLOW_20261018.md). | Keep this as the first behaviour to protect. If it recurs, identify the failed handoff: party control, selected movement intent, queue admission, corridor publication, physical acknowledgement or native obstruction. Retain assigned role slot, moving/stopped owner regroup and narrow-entrance trail. No new random offsets or competing probe queue. |
| PB-NOW-02 / P1 | **Combat and following replace each other; Attack previously rejected valid selections.** Explicit Attack/Passive transition and stale combat snapshot fixes are installed. Exact original screenshot rejection was not captured. | C original report; B `PlayerBotSession.attackSelectedTarget`, `validEnemy`, `followOwner`, `runDecision`, `PlayerBotFollowIntent`. | Confirm valid fight retains combat positioning; manual Follow withdraws; combat end returns to the assigned slot; Attack resumes Passive without bypassing native hostility/visibility. A build alone cannot close the reported behaviour. |
| PB-NOW-03 / P1 | **Lift clustering, jitter and incomplete crossing.** Queue reservation, ticket-age retention and boarding/carry/disembarking repairs are installed in the evening build. | C reported crossings; B `PlayerBotPlatformQueue`, `PlayerBotPlatformRide`, navigation/mover handoff. [Lift evidence](PLAYERBOTS_PLATFORM_QUEUE_20261018.md). | Confirm waiting spacing, boarding a departing cabin, standing while carried, cast/root interruption, exit and continued activity. Unsupported partial-footprint transitions and every extracted lift's physical/wire presentation are not certified. |
| PB-NOW-04 / P1 | **World stalls and stop/start travel; CPU bursts.** Prepared routes, continuous corridors, bounded admission, route reuse and catalogue prewarming exist. Sustained live improvement remains unmeasured. | Earlier A Delenamela stall was a shared all-bot defect, not a name-specific patch. B `PlayerBotNavigationBudget`, `PlayerBotNavmeshTopology`, `PlayerBotRoadNetwork`, `PlayerBotService`, quest catalogues. [Corridor contract](PLAYERBOTS_CONTINUOUS_CORRIDORS_20261018.md), [latest CPU evidence](PLAYERBOTS_WARNING_REPAIR_20261018_EVENING.md). | Use a current recurrent stall/slow warning to distinguish actual CPU, monitor wait, native work and elapsed pauses. Do not blame the last-action label alone or assume RAM settings solve computation. Concurrent material-site cache misses can still duplicate discovery; cost is a review candidate, not a proven current bottleneck. |
| PB-NOW-05 / P1 | **Jump/drop/glide reliability across terrain.** Implemented shared traversal and owner glide mirroring; the repeated ledge reports are not universally closed by offline route generation. | C reported failures; B `PlayerBotHeightTraversal`, `PlayerBotFlight`, `PlayerBotHeadlessFlight`, navigation/mover custody. | Confirm the actual takeoff, landing and transition back to following for both companions and independent bots. Ground-first proposals are not physical proof. Companion owner-glide support exists; universal autonomous glide routing and airborne lift boarding are not established as complete. |
| PB-NOW-06 / P1 | **Party preset rejected because a preferred formation slot was obstructed.** Source defect repaired and installed; latest user retry pending. | C report; B `PlayerBotFormation.arrival`, `PlayerBotService.recruit`, preset rollback path. Latest receipt above. | Preferred connected slot → nearby positions → owner's current position. Formation preference must not cancel recruitment. Temporary overlap at arrival is allowed; normal following resolves slots afterward. Only a real native placement failure remains an error. |
| PB-NOW-07 / P2 | **Saved knockback restored before spawn; REDEEM warning; multi-second quest decisions.** Source repairs installed, not warning suppression. | B saved-effect restoration path; quest3074 native reward hook/payment; static recipe/material prewarming. [Warning repair](PLAYERBOTS_WARNING_REPAIR_20261018_EVENING.md), [REDEEM](REDEEM_QUEST_REWARDS.md). | Preserve fresh-hit knockback and native reward/payment behavior. Future warnings need new evidence. Exact proprietary REDEEM random probabilities are unverified; no invented table. The short current log is insufficient to declare these cases accepted. |
| PB-NOW-08 / P2 | **Revival, dormancy and return to activity at scale.** Existing lifecycle/revival mechanisms are present; no new failure proven by the dead count. | B `PlayerBotRecovery`, `PlayerBotRevival`, `PlayerBotWorldPopulation`, `PlayerBotPopulationLifecycle`, session dormancy. | Track an individual actor through death/revival/resumption if it remains stuck. Dead/dormant/combat totals overlap; empty-human-map dormancy is intentional and must not suspend owned companions with an active owner. Do not change revival policy from an INFO count alone. |

## Unfinished engineering and parity

**Owner direction, 18 October:** PB-GAP-01 through PB-GAP-05 are deferred to the
far future. Preserve their existing implementations; do not schedule expansion
of quest coverage, classes, encounters, professions or social/economy behaviour
while working on following/navigation/performance. PB-GAP-06 remains a policy
boundary. These rows retain the map of unfinished work, not an active assignment.

| ID / priority | Present now | Remaining work / evidence boundary |
| --- | --- | --- |
| PB-GAP-01 / P2: quest item choices | Owned item-start/use, explicit enemy/self/pet/friendly and dead-friendly resurrection targeting, native async completion and custody. | Source-confirmed: `PlayerBotQuestItems.skillTarget` has no point/remote-party choice adapter; item execution omits Dye, MultiReturn and InstanceTimeClear without an explicit choice. Add only native, quest-relevant choice handling after establishing the actual required action; never fabricate targets/items/progress. |
| PB-GAP-02 / P2: custom quest/event coverage | Declared ordinary/custom objectives, reviewed master chains, timed objects, story branches and registered handler contracts. | `PlayerBotQuestCapabilities` still returns unsupported when no executable current-stage declaration exists. Exceptional movie/instance/custom-event stages need handler-by-handler coverage. Old metadata/handler counts are not complete-chain proof. [Detailed ledger](PLAYERBOTS_AUTONOMOUS_QUESTS_20261011.md) contains historical next-seam entries already superseded by later item/CC work. |
| PB-GAP-03 / P2: acquisition and professions | Native quest-driven recipe/design learning, paid training, recursive craft planning, tool travel, timed crafting, gathering and mob-drop material acquisition. These are implemented, not “professions absent.” | Acquisition does not reverse-plan arbitrary decomposition/box/exchange output chains. A source-class gap is not proof a named quest is blocked without tracing its items. Broader independent profession selection, market decisions and crafting/trading policy remain partial. Delivered master chains must not be reopened solely for missing gameplay acceptance. |
| PB-GAP-04 / P2: class/encounter breadth | Shared native skill/role/chain/resource gates, seven earlier class strategy slices, later Ranger mode handling and Aethertech adaptations; Steel Rake, Theobomos and Adma tactics. | Complete class/build strategies and a broad encounter library remain partial. Ranger is not wholly absent; existing modes are not full Hunter strategy parity. Audit a concrete missing decision before adding another class/tank/movement override. No class is certified optimal merely because it has a helper. |
| PB-GAP-05 / P3: autonomous social/economy | Requested party/alliance control, legion invitations/name-addressed requests, supplies, custody-aware public trade, warehouse, requested mail and legion deposits. | Broader autonomous matching/group formation, legion task lifecycle, world COD/automatic mail and general economy policies remain historical open port areas. Recheck their precise native source seams before implementation; this review did not certify every social/economy feature absent or complete. Separate broker/market work is not proof of autonomous bot market policy. |
| PB-GAP-06 / policy: PvP and WoW-specific features | Native PvE policy and legal Aion events remain authoritative. | Dedicated PvP AI remains deferred in the scope record; native PvP world/zone quest objectives cannot be replaced with monster kills. Do not silently enable it. WoW-only battleground/vehicle/guild systems need an Aion equivalent, not literal copying. |

## Regression causes and preservation contract

Two concrete regressions are established in the recent receipts:

1. Extra formation probes competed for the same bounded work admission as follow
   requests. Replacing queued destinations discarded waiting age and starved later
   companions. The delivered fix removed that competing probe queue and retained
   request age. More geometry checks were not automatically better behaviour.
2. A spacing preference became a mandatory recruitment condition. One obstructed
   slot rejected a complete preset. This was an unrequested policy change, repaired
   by the latest fallback, not an intended native rule.

Current source still concentrates many early returns and strategy triggers in
`PlayerBotSession`: follow, combat, support, quest items, material gathering,
recovery, transport and appearance all meet there. World and companion control
are distinguished (`autonomous()`), while geometry, movement and native custody
are intentionally shared. Separate decision policy does not require duplicating
the native movement engine. Overlapping writers/handoffs are the review focus;
this audit does not claim every remaining symptom has one proven root cause.

For subsequent Playerbots changes, use this small impact record in the relevant
feature document/handoff, not another long historical completion journal:

- **Intent and owner:** companion vs independent policy, affected entry points,
  movement/interaction owner before and after the change, and exact affected files.
- **Preserved transitions:** moving/stopped owner → assigned role slot; valid fight
  → combat positioning → regroup; manual Follow → withdrawal; native interaction
  → earned completion/cancellation; lift/jump/fall/glide → physical completion;
  recruitment → placement fallback. Choose the transitions actually affected.
- **Shared invariants:** retain queued request age and current corridor until a
  real invalidation; only the physical mover acknowledges arrival. Do not add a
  second independent formation search, renew blocked/expired paths, erase native
  collision/door checks, steal interaction custody or alter owned-alts' builds.
- **Scope:** no incidental population/CPU-limit/appearance/loot/reward policy
  changes to make a navigation repair look successful. No new summon refusal.
  A deliberately changed player-facing policy must be explicit in the requested scope.
- **Evidence and delivery:** compare against the latest source/runtime receipt,
  choose only a necessary focused check, normal build for gameplay source edits,
  then affected-file backup/copy/hash. Record what the check cannot establish.
  No full suites, new test files or repeated passing checks by default.
- **Parallel work:** assign disjoint files or agree a stable shared-file handoff;
  process incoming messages after the current coherent unit. Build the combined
  stable source once and preserve newer runtime changes. Do not wait idle for chat.

Do not mark a reported behaviour “closed” merely because its package was copied.
Conversely, do not label delivered code “missing” because live acceptance is pending.
If a new failure occurs, reopen the corresponding ID with current evidence instead
of accumulating another unrelated “latest fix” that replaces the old contract.
