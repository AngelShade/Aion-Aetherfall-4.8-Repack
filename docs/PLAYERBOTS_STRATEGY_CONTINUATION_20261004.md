# Offensive strategies, target values and local quest routes

Installed on disk with GameServer stopped at the user's explicit request.
Initial receipt: `backups/playerbots-recruitment-20261004-181655-178651`.
Native zero-rune correction: `backups/playerbots-recruitment-20261004-182147-699768`.
Original continuation receipt: `backups/playerbots-recruitment-20261004-183335-252998`.
Current cumulative receipt: `backups/playerbots-recruitment-20261004-203902-131350`.
Paths are relative to `target-deploy/game-server`. No server restart, attach,
database mutation, human-character mutation or client replacement was performed.

**Reverification correction:** PB-PORT-001/002 final-cast contradictions are now
corrected in source and installed stopped in receipt `193448-980138`. All 44 final
CastAction/native-planning checks pass; native casts/client acceptance remain
pending. PB-PORT-003 now connects shared local quest arbitration to existing
conversation/NPC/object/new-hunt executors, with 35 effective/installed production
gate checks. Native complete-tick/geodata/client acceptance remains pending.
See `PLAYERBOTS_QUEST_ARBITRATION_20261004.md`. Next is PB-PORT-004 in the canonical
[port tracker](PLAYERBOTS_PORT_REVIEW_20261004.md#reverification-tracker--4-october-2026).
The helper/template checks below remain passing evidence for their narrow scope;
they do not establish final CastAction or coordinated quest execution acceptance.

The first update transplants six existing methods, including the actual hazard
lambda, and adds three helpers with their nested classes. It retains 101 earlier
cumulative entries byte-for-byte. The native zero-rune correction transplants
only `PlayerBotOffense.finisher/useful` and retains all 115 other entries
byte-for-byte, including the original enum switch class. The final single-method
conversation leash correction likewise preserves 115 other entries, and keeps
destinations inside the existing native 25m owner interaction gate. Base JAR, launcher order,
command source/cache, media and per-character/account settings are preserved.
Receipts include hashes, original files, effective rollback and settings snapshots.

## Pinned original and native adaptation

The pin remains `037c01418b5d01506917a3db9b44fd56ac5f965c`. Unchanged
`ChooseTravelTargetAction.cpp/.h` were fetched at that exact revision, with hashes
added to `third-party/playerbots/SHA256SUMS`. The upstream authors/license remain.

| Original purpose | Implemented Aion behavior | Boundary |
| --- | --- | --- |
| Affliction Warlock / Fire Mage missing periodic effects | Final action honors native tick+cast refresh and same-rank hybrid direct damage, while preserving stronger effects | PB-PORT-002 installed/offline verified; actual native casts remain pending. |
| Assassination Rogue four-point and dying-target finishers | Final usefulness and priority share native rune/builder/expiry decisions; obsolete class veto removed | PB-PORT-001 installed/offline verified, including legal zero-rune fallback without a builder. Native casting remains authoritative and pending acceptance. |
| CasterFindTargetSmartStrategy | Original 5–30 second estimated-lifetime interval, range preference and stable nearly-dead current target | Only existing eligible enemies; commands/owner assistance remain first. Lifetime is an estimate from native equipment/learned skills, not measured damage. |
| ComboFindTargetSmartStrategy | Reachable Assassin rune target preference, then existing range/health selection | Native rune ownership replaces WoW combo ownership; native burst mechanics remain authoritative. Like upstream, these strategies activate with more than three nearby living group members. |
| ChooseTravelTargetAction group/active destination and retry behavior | Shared exact local destination/actor, follower-specific native eligibility, moving/stale/pause/retry handling and executor gates installed (PB-PORT-003) | Complete native tick/geodata/client acceptance pending. Full world/service/new-quest graphs remain PB-PORT-011. Owner leash/mission precedence are intentional. |
| Aion Kromede delayed trap | Visible `kromede_trap` actor produces native skill 17050 hazard before its 5.5-second cast delay ends | Verified Aion handler explicitly self-targets despite TARGET template metadata. Native 5m area and floor reach drive existing avoidance. This is an Aion encounter continuation, not an invented WoW boss mapping. |

The quest planner reads native current stages and approved catch-up decisions.
It never directly advances quest variables or manufactures rewards. Existing
native handlers still perform conversations, acceptance, object use, kills, loot
and completion. Branching scripts require a verified owner choice. The quest
combat switch invalidates cached hunt routes immediately; flags and non-isolated
packs are excluded. Known actors in the actual instance are required for hunts.
Static non-temporary open-world spawn points are only interaction hints.
Owner movement, combat, flight, rest, explicit orders and existing safety gates
take precedence. Bots report the selected NPC/quest destination.

All new combat/travel helpers read current bot state. They do not change class,
level, learned skills, build, equipment or Stigmas on player-owned alts. Automatic
Temporary Bot maintenance and opt-in protected care retain their earlier boundaries.

## Passing offline evidence

- `target/playerbots-strategies-final-source-validation.txt`: full Java server,
  commons, command and companion-check compilation; full companion/HTTP suite.
- `target/playerbots-strategy-effective-validation-v5.txt`: effective bounded
  JAR with actual installed dependencies, separate test classes and Java verifier;
  978 offense checks include 19 decision comparisons, 954 production-template
  checks, four native zero-rune family checks and one coverage assertion. The
  886 periodic templates/68 finishers are metadata coverage, **not cast acceptance**.
- Same effective log: 19 caster/combo comparisons, 17 quest destination/toggle/
  leash comparisons, 35 encounter checks. An observed world-free trap exercises
  the actual visible-hazard adapter before casting, including destroyed/despawned/
  other-instance exclusions. Geometry checks reject crossing the warning and
  permit retreat. This fixture creates no world actor or ID reservation.
- `target/playerbots-fixture-guards-validation.txt`: six rebuilt retired fixture
  main/agent entrypoints reject before native/database/ID access.
- `target/playerbots-strategy-postinstall.json`: installed/backup/payload hashes,
  client/settings/launcher/base preservation.
- `docs/INSTALLED_MODS.json`: 15 current preservation checks, 31 client hashes,
  70 client receipts and 69 historical server receipts.

## Item-ID release investigation

Two older preset fixtures deleted character rows and released fixture item IDs
without deleting inventory rows. Inventory ownership has no delete cascade;
therefore this is a concrete unsafe release path. Revision 4 deleted private
inventory but did not verify absence before releasing IDs. Rebuilt revisions
1/2/4 now refuse both CLI and agent entrypoints. Historical compiled JARs in old
staging/backup directories have not been rewritten and must not be run.

Use `PlayerBotPresetPersistenceCheckAgent5`: verify exact private inventory rows
gone before releasing IDs; exclude account/legion/market storage 2/3/125; protect
foreign/shared IDs. NPC Cleaner IDs must never be manually released a second time.
The installed ItemFactory persisted-ID allocation guard remains unchanged.
No SQL cleanup or repeat Bardoca/Tanku recovery was attempted. The historical
collision-to-release identity has not been proven, and other release paths remain
an unfinished investigation. Do not call the root cause fully resolved.

## Pending actual behavior

GameServer stays stopped. Native rune casts/MP/consumption and periodic refresh,
session target choices, route progress through real geodata, native quest stage
transitions and trap avoidance in a real encounter remain pending startup.
Also pending: corrected post-revive Healing Light check (RevivalCheckAgent2),
final supplies MP/buff fixture revision 3, native roster dismissal/removal and
actual game appearance/movement/combat acceptance.

Complete optimized class strategies, proc/resource/pet coordination, focus/icon
controls, full world travel, broader dungeon strategies, dedicated PvP and alliance
support remain unfinished. These are explicit implementation limits, not merely
untested features. This continuation does not claim the full Playerbots port complete.
