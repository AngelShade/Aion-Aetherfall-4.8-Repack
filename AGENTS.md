# Aion project continuity and installed modifications

## Playerbots port focus at the start of every iteration

- Read `docs/PLAYERBOTS_PORT_SCOPE.md`, the current port tracker and validation
  record before choosing Playerbots work. The destination is the full upstream
  Playerbots behavior adapted to Aion, including independent world bots, native
  party invitations/control and player/bot trading. Owner-bound companions are
  the current subset; finishing PB-PORT-001..012 does not establish full parity.
- The bounded upstream subsystem inventory is complete in
  `docs/PLAYERBOTS_SUBSYSTEM_INVENTORY.md`, with the untruncated pinned tree and
  17 additional blob/SHA-verified references. Update affected rows and implement
  from its dependency plan; do not restart the inventory or substitute re-audits
  for forward porting progress.
- For each iteration, name the tracker/scope ID, concrete behavior being added,
  exact upstream source and native Aion mapping. Identify whether the work is a
  new port, a repair, a prerequisite or validation; explain the dependency.
  Do not imply every remaining feature must wait for every other backlog item.
- Pick one bounded implementation slice from the dependency plan. Expand the
  scope only for an evidenced defect or necessary dependency; record why it
  blocks that slice. Keep unrelated repairs and custom features on separate IDs.
- Installed/offline verified with native/client acceptance pending is distinct
  from unimplemented. Do not repeatedly re-port installed behavior solely because
  the server/client check is withheld. Reopen it only for concrete new evidence.
- Synchronize the current status table and next-work pointer with installation
  notes; historical receipts are evidence, not the current plan. Report the
  behavior gained and remaining dependencies, not only hashes and test counts.
- This continuity guidance does not cancel later implementation requests.
  Preserve all installed mods, owned-alt builds and explicit server lifecycle
  instructions. Refresh actual running state and latest receipts before deployment.

The user explicitly requires every new change to preserve all prior installed
mods and to remember unfinished work across conversations. Treat the complete
installed client/server as the baseline, including work from other chats.

- Start relevant work by reading `docs/INSTALLED_MODS.md` and
  `docs/INSTALLED_MODS.json`. Refresh current files/receipts before an install;
  the JSON is a dated snapshot, not permanent authority. Refresh it with
  `client-mods/diagnostics/inventory_mods.py` using the documented paths.
- Preserve unrelated dirty changes and deployed JAR entries/methods. Source
  builds and old staging packages can omit newer deployed work. Construct a
  bounded incremental update from the latest actual installation.
- Inventory all feature receipts, native DLL imports/hooks, base and active
  English UI archives, addon packages/signatures, artwork/indexes, launcher,
  renderer settings, configuration, static data and recovery baselines.
- A `Game.dll` change must also update active graphics package/state and native
  cursor tracking, plus both restore baselines, in the same verified transaction.
  Use `graphics_compat.prepare_incremental` where compatible. Never disable hash
  guards or copy an older DLL to make startup pass. Run the installed launcher
  preparation scripts and test restore preservation on a disposable client.
- Keep stock model `Pub.key` separate from custom addon `Addon.key`. Preserve
  the pet and bundled Lua archives and verify all three addon signatures. Native
  icons, English labels, Wardrobe and player-facing menu entries must survive.
- Keep the running client closed for file replacement. Existing user approval
  still applies; do not introduce repeated confirmations for authorized work.
- Maintain the feature inventory after installs. Record installed, removed by
  request, staged/source-only and unfinished work separately. Do not reinstall
  the rejected AFK extension or removed browser diagnostic by accident.
- Do not confuse installed hashes, server startup, isolated browser tests or a
  template coverage count with acceptance in the actual game. Keep unresolved
  feature/gameplay limits visible, especially the full Playerbots scope.
- Latest user policy: PvP flags, duels, location and flight must not prevent
  companion recruitment or automatically dismiss companions. Recruitment is
  blocked by the owner's native active-combat timer. Dedicated PvP AI is future
  work; keep normal ownership, persistence, roster limits and valid party slots.
- The live recruitment fix is also persisted in
  `target-deploy/game-server/libs/playerbot-recruitment-fix.jar`, explicitly first
  on `target-deploy/game-server/start.bat`'s classpath. This cumulative override is
  part of the installed baseline. Preserve it and its launcher order; when
  deploying a later complete JAR, compare effective override methods, fold the
  complete cumulative override into that JAR and remove the override/classpath
  entry in one transaction. It now also contains flight, quest synchronization,
  journal/catch-up, teleport/summon, gear/care adapters and HTTP/UI updates.
- That override also contains the companion inventory panel repair: non-equipment
  items have an empty slots list, never `ItemSlot.getSlotsFor(0)`. Preserve this
  seventh method change together with the six recruitment-policy methods. Repair
  receipt: `backups/playerbots-recruitment-20261004-022932-203190`; the main
  companion expansion receipt is `backups/playerbots-recruitment-20261004-022029-970392`.
- Latest cumulative equipment receipt:
  `backups/playerbots-recruitment-20261004-032705-060049`. Preserve per-bot
  earned/starter/generated acquisition, equipment profiles, native quality/level/
  weapon limits, upgrade threshold, loot rolls and opt-in real-shop purchases.
  Settings and generated-item protection persist in `config/playerbots/gear-character-*.properties`.
  It retains the creation/starting-level repair from `...031433-971037`, the
  tabbed companion window and native movable party bar. Full gameplay acceptance
  and the broader Playerbots scope remain unfinished.
- For live cumulative-JAR replacement, preload all original override classes
  before copying a changed JAR: the native classloader caches ZIP offsets.
  Use the compatible equipment agent's `prepare` mode and installer
  `--preload-override`; keep hash guards and rollback. Use a fresh agent class/JAR
  revision because already attached agent classes remain loaded. The bounded
  equipment stager is `client-mods/playerbots/stage_equipment_update.py`.

- Latest cumulative companion receipt is now
  `backups/playerbots-recruitment-20261004-080311-450862`. Preserve object quests,
  owner completion witnesses, nearby quest combat toggle, stable formation/native
  follow-speed packets, whole-party instance repair, intermediate conversations,
  tank hate logic and class gear selection. See `docs/PLAYERBOTS_VALIDATION_20261004.md`.
- Dedicated-roster companions are now **Temporary Bots**: owner-level scaling,
  native skills/Stigmas, role builds and gear tiers every ten levels from 20.
  Owned alts must never receive automatic class/level/equipment/build/Stigma
  replacement. Temporary world actors are removed on dismissal/logout, while
  dedicated roster progress persists. Automatic care remains opt-in and protected.
- Incremental stagers include formation, follow-speed, transfer, conversations,
  tank, armor, temporary and maintenance wrappers. When modifying an existing
  helper, include its changed methods in runtime SCOPES; HELPERS alone only writes
  its disk entry. Verify the effective live methods, not only JAR hashes.
  Cached enum-switch arrays survive class redefinition: avoid changing their
  mappings without explicit runtime handling (equipment weights use stat-name
  dispatch). Keep fresh agent revisions, preloading, hashes and rollback.

Client: `<CLIENT_ROOT>`.
Deployment: `C:/Git/aion-server/target-deploy/game-server`.
Related prototypes: `D:/Proiecte/Project Restructure`; inspect relevant project
instructions and current state before reusing them.

- Saved Temporary Bot checkpoints and mixed alt/Temporary party presets are installed.
  Preserve `PlayerBotPresets`, account-owned `config/playerbots/saved-parties/account-*.json`,
  HTTP savebot/saveparty/loadparty/deleteparty routes and the Roster/Party controls.
  Presets retain native character IDs, roles and orders; they do not copy/overwrite alt builds.
  Native DB save/dismiss/resummon with exact isolated fixture cleanup passed.
- The focused upstream review is `docs/PLAYERBOTS_PORT_REVIEW_20261004.md`.
  Preserve mixed offensive/threat classification, spell-specific support eligibility and
  native role target values. General DPS range/health and tank weak-hate/multi-tank
  protection are installed; caster lifetime/icon/combo strategy parity remains partial.
  Latest staged wrappers include `stage_port_review_update.py`, `stage_presets_update.py`
  and `stage_target_values_update.py`. The next attach agent revision must exceed 26.

- AI reload/Idian Depths/Kromede repair is installed in the cumulative override:
  core receipt `backups/playerbots-recruitment-20261004-102535-101269`, final script
  receipt `...102954-276296`. Preserve `AIRegistryReload`, asynchronous Reload,
  resilient portal scheduling, 50/50 instance method, encounter-local physical
  Verdict and completion-driven boss/trap scripts. See
  `docs/AI_RELOAD_KROMEDE_20261004.md`. New Fire Temple gameplay acceptance remains
  pending; existing NPCs retain their AI until respawn. Later cumulative receipt
  `...103105-414348` retains this repair and adds the companion inventory ownership
  transaction check. Preserve it together with the separate Kromede script receipt;
  Tanku's subsequent inventory persistence outcome is verified below.
- Native group/pet healing is installed in receipt
  `backups/playerbots-recruitment-20261004-100323-698137`. Preserve `PlayerBotHealing`,
  native affected-target scoring, hybrid heal/cleanse handling, in-flight coordination
  with emergency support, and spell-specific legal combat resurrection. Actual
  Healing Wind casting/MP/recipient reservations passed; focus lists, full class
  strategies and real-client combat acceptance remain unfinished.
- Tanku's unsaved colliding armour item was recovered without changing its attributes
  or the foreign row; receipt `backups/playerbots-inventory-collision-20261004-1032`.
  Its native inventory/progress checkpoint passed and normal save/dismiss retry
  cleared the held session. Never bypass custody checks or overwrite conflicting rows.
- Use `PlayerBotPresetPersistenceCheckAgent5` for saved-party native checks.
  Inventory has no player-owner delete cascade: delete only verified fixture
  private inventory and verify it is gone BEFORE releasing IDs. Exclude account,
  legion and market storage (2/3/125); never release shared item IDs.
- Class enemy utility continuation is installed in cumulative receipts
  `backups/playerbots-recruitment-20261004-114208-193698` and
  `backups/playerbots-recruitment-20261004-115019-456681`. Preserve
  `PlayerBotEnemyUtility`, native counter-purge classification/eligibility and
  engaged secondary interrupt selection. Native casts and exact pinned class bands
  passed in `PlayerBotEnemyUtilityCheckAgent4`; full class strategies remain partial.
  Preload and redefine already-loaded helper methods too, not only their disk JAR
  entries. Native NPC objects auto-release IDs through their Cleaner: fixture
  cleanup must not also release the same NPC IDs manually. See
  `docs/PLAYERBOTS_CLASS_STRATEGIES.md` and the current validation record.

- Owner-centered Circle/Box/Line/Spread follow formations are installed in server
  receipt `backups/playerbots-recruitment-20261004-124615-036768` and client
  `TransmogMenu-backups/playerbot-bar-20261004-124722-673`. Preserve
  `PlayerBotFormationLayout`, `.bot formation` routing, owner-account-checked
  `config/playerbots/formation-character-*.properties` and the third native bar
  row. Follow destinations stay centered on the owner without forward speed
  offset; combat strategies and explicit orders remain intact. The default is
  Circle. Real geometry/Lua/native routes and effective live methods passed;
  actual game movement/rendering/regrouping remains pending. Future updates to
  loaded `PlayerBotFormationLayout` methods must be in runtime SCOPES too.
  The next attach update agent revision must exceed 29.
- Defensive continuation is installed in cumulative receipt
  `backups/playerbots-recruitment-20261004-123520-131583`; later formation/command
  receipt `124615-036768` retains it. Preserve `PlayerBotDefense`, class health
  bands, native effect-slot protections, incoming-magical-cast relevance and
  engaged-pursuer root/snare/control reservations. Native cast fixture revision 5
  passed shield absorption, bow snare, root and finite resistance charges without
  DB/human writes. Real NPC casts require passive `NpcAI`, not `DummyAI`; elemental
  damage is required to test native magical resistance. Full class strategies,
  escape movement and client combat acceptance remain unfinished.

- Territory flag target repair is installed in cumulative receipt
  `backups/playerbots-recruitment-20261004-130544-238823`, retaining selection
  receipt `...130336-672982` and the full earlier installation. Preserve native
  `Npc.isFlag()` exclusion in `PlayerBotSession.validEnemy` and
  `PlayerBotService.allowsTarget`. Kaidan Watchpost's invisible/untargetable
  zero-damage Asmodian Territory flag was attracting all four companions.
  All 28 effective native guard checks passed and normal mob combat continued.
  The next attach update agent revision must exceed 31.

- The eight legacy capture-camp repair is installed in cumulative receipt
  `backups/playerbots-recruitment-20261004-132704-143251`, retaining every previous
  override entry byte-identically and adding three reviewed native method
  replacements in two appended class overrides plus the new helper. Preserve
  `LegacyCampBattle`, scoped explicit hostility in `TribeRelationService.isFriend`,
  native raid scheduling in `Base.spawnBySpawnHandler` and same-base NPC faction
  attribution in `Base.getOccupier(Creature)`. Preserve 47 terrain-height repairs
  and four exact ordinary-world overlap removals in five source/deployed XML files.
  The loaded data was updated without HP, quest, ownership or human writes.
  All 166 isolated native faction/movement/guard-damage assertions pass; actual
  client appearance, complete live raid/capture cycles and precise retail timers/
  routes remain unverified. See `docs/LEGACY_CAMP_REPAIR_20261004.md`. The next
  attach update agent revision must exceed 32; include changed helper methods
  in runtime SCOPES for subsequent live updates, not only disk HELPERS.
  The later `...133452-341528` ItemFactory/PlayerBotItemIds continuation was also
  verified to retain every camp package entry byte-identically; use that later
  cumulative receipt as the baseline and preserve its separate item-ID repair.


- Companion death/rebirth animation repair is installed in cumulative receipt
  `backups/playerbots-recruitment-20261004-140017-509159`, retaining native supplies/
  wipe recovery receipts `...134823-288459` and `...135149-590713` and all earlier mods.
  Preserve `PlayerBotRevival`, session tick/markClosing and the native revive hook.
  Native resurrection eligibility/penalties remain authoritative; delays block actions
  while death/rebirth animations finish and refresh an already living actor only.
  LOOTING shares the FLOATING_CORPSE bit: compare exact stance masks, not that bit alone.
  Runtime helper changes must include explicit SCOPES. Next attach update revision
  must exceed 37. See `docs/PLAYERBOTS_REVIVAL_20261004.md`; native lifecycle and stance
  checks passed, but final post-revive casting/client appearance remain pending.
  GameServer was shut down during verification and remains stopped pending the user's
  restart response; run `PlayerBotRevivalCheckAgent2` after startup. Its single-target
  Healing Light fixture corrects the earlier party-less Healing Wind casting fixture.

- Companion item custody, supplies and wipe recovery are installed through receipt
  `backups/playerbots-recruitment-20261004-140949-271624`. Preserve `PlayerBotItemIds`,
  native ItemFactory allocation checks, `PlayerBotSupplies`, `PlayerBotSupplyCatalog`,
  `PlayerBotRecovery`, selected/no-target summon routing and bounded Temporary stock
  provenance. Bardoca 106628 was recovered without altering foreign item 191241;
  do not repeat the pending recovery. Preserve concurrent `PlayerBotRevival` and
  native revive observer/animation changes, including its State class. The server
  stopped during concurrent work; final revision-3 MP/buff native checks await
  startup, along with client acceptance. See `docs/PLAYERBOTS_SUPPLIES_RECOVERY_20261004.md`.
  The next attach update agent revision must exceed 36. Existing helpers require
  runtime SCOPES when edited; retain hashes, preloading and effective rollback.

- Temporary Bot roster removal is installed on disk in cumulative receipt
  `backups/playerbots-recruitment-20261004-142907-454064`. Preserve account-owned
  `config/playerbots/removed/character-*.json` archive markers, `PlayerBotRosterRemoval`,
  roster filtering, saved-party pruning on load and the inline Remove/Confirm/Cancel
  controls. Removal saves/dismisses first, rejects held leases and protects owned
  alts. It retains native character/inventory rows and IDs, including pending bots;
  never release archived IDs or expose archived bots as ordinary character slots.
  GameServer remains stopped; native dismissal/removal and game acceptance await
  startup. Full source suite, 14 archive checks and 26 native browser fixture actions
  pass. See `docs/PLAYERBOTS_ROSTER_REMOVAL_20261004.md`. Next attach revision >37.
- The user explicitly asked to remember the repeated item primary-key collision
  during iteration. The ItemFactory persisted-ID allocation guard and Bardoca
  recovery are installed, but the root cause of IDs being released while persisted
  rows remain is NOT yet proven resolved. Keep that release-path investigation
  unfinished. Inventory owner deletion does not cascade: verify fixture-private
  inventory gone before ID release, exclude shared storage 2/3/125 and foreign
  rows, and never double-release NPC IDs already owned by the native Cleaner.

- Offensive/target/local quest-route continuation is installed on disk in cumulative
  receipt `backups/playerbots-recruitment-20261004-183335-252998`, retaining initial
  receipt `...181655-178651`, native correction `...182147-699768` and every previous installation. Preserve
  `PlayerBotOffense`, `PlayerBotTargetStrategies`, `PlayerBotQuestRoutes`, session
  priority/tick/closing and CastAction usefulness, target-value dispatch, and
  `PlayerBotHazards.lambda$visible$0` for native delayed Kromede trap anticipation.
  Preserve native positive reduced-damage zero-rune fallback when no builder is
  usable. The two-method correction retained the original enum switch class and
  all 115 other cumulative entries byte-identically. The final single-method
  QuestRoutes.leash correction keeps intermediate conversations inside the native
  25m owner interaction gate and also retains 115 other entries. Existing helpers/lambdas need
  explicit runtime SCOPES when edited. Next attach update revision still exceeds 37.
- The user explicitly requested **keep GameServer stopped; continue implementation
  and offline checks**. No restart or live attach occurred. Native revival fixture
  revision 2, supplies revision 3, roster dismissal/removal, new combat/route/trap
  checks and actual client acceptance await startup. Full class strategies, proc/
  pet/focus/icon systems, world travel and broader encounters remain unfinished.
  See `docs/PLAYERBOTS_STRATEGY_CONTINUATION_20261004.md` and validation record.
- The older preset fixture revisions 1/2 released IDs with surviving inventory;
  revision 4 did not verify row deletion before release. Rebuilt source now refuses
  both main/agent entrypoints (six offline checks). Historical compiled JARs were
  not rewritten and must not be run; use revision 5. This identifies one concrete
  unsafe release path but does not prove historical collision identity or all
  release paths resolved. Keep the item-ID root investigation unfinished; no
  database cleanup/recovery was repeated.

- The canonical pinned-upstream reverification and continuation backlog is now
  `docs/PLAYERBOTS_PORT_REVIEW_20261004.md`, section **Reverification tracker**.
  Read it before new Playerbots work and retain/update stable PB-PORT-001 through
  012 and separate PB-VAL-001 through 008. This review excludes custom user policy
  as a parity criterion and distinguishes defects, partial ports, source gates
  and pending acceptance. All 51 cached source hashes were verified; the cached
  subset is not an exhaustive upstream audit. Current installation is unchanged.
- Implement next in this order: PB-PORT-001 contradictory final rune gate,
  PB-PORT-002 blanket DAMAGE same-buff veto masking refresh/hybrid damage,
  PB-PORT-003 shared quest objective/destination arbitration. The installed
  `PlayerBotClassCombat.shouldBurst` still vetoes four positive offense decisions;
  helper/template counts do not close these defects. QuestRoutes is currently
  an idle fallback and earlier executors do not consume its Goal. Finish final
  CastAction/tick tests, then native/client acceptance when startup is authorized.
  Do not expand class helpers while leaving these integration errors hidden.
- Record addressed tracker IDs, original/native mappings, effective methods,
  receipt/hash evidence, tests and remaining acceptance in current docs after
  each iteration. Formation modes, group/pet healing, legal combat resurrection
  selection and caster/combo values already exist; the initial historical review
  matrix is superseded by the current tracker. Preserve prior mods and alt setup.
  Continue offline; this review does not change the keep-GameServer-stopped policy.

- PB-PORT-001/002 corrections are installed stopped in cumulative receipt
  `backups/playerbots-recruitment-20261004-193448-980138`, superseding the preceding
  next-work/rune-veto note. Preserve `PlayerBotOffense.useful` and
  `PlayerBotSession$CastAction.isUseful`: final finisher usefulness shares native
  resource priority, the legacy ClassCombat veto is no longer called, and periodic
  DAMAGE actions use effect-specific refresh/hybrid checks with stronger-effect
  protection. The old ClassCombat members remain byte-identical but unused here.
  All 114 other entries, original enum switches, prior mods, commands/media,
  base JAR, launcher, client and 35 settings/preset/archive files survive.
- Full source suite and 44 final CastAction/native-planning checks pass against
  the effective staged/installed JAR; this is not a native cast/MP/rune consumption
  or client acceptance claim. 51 source hashes and 76 selected effective methods/
  lambdas match; postinstall audit records legacy gate inactive. PB-PORT-001/002
  are installed/offline verified with PB-VAL-001 pending. **Next offline
  implementation is PB-PORT-003: shared quest objective arbitration.** See the
  canonical tracker and validation record. Keep GameServer stopped.
- Use `stage_offense_gates_update.py` for this bounded correction and include
  changed existing helper/nested-action methods in runtime SCOPES for later work.
  The offline installer fails closed if process inspection is denied; never
  interpret inspection failure as proof GameServer is stopped. Receipt/hash/
  rollback guards and the next attach revision >37 requirement remain intact.


- Shared local quest objective arbitration PB-PORT-003 is installed on disk in
  `backups/playerbots-recruitment-20261004-203902-131350`, retaining arbitration
  receipt `...203357-938918` and the complete `...193448-980138` baseline.
  Preserve `PlayerBotQuestObjectives` and Session/route/conversation/object/
  reward-job/new-hunt integration. Peer copies use each follower's native objective
  eligibility and its own status/full-variable stamp, not equal peer variables.
  Exact actors/points, moving visibility, map/instance, static hint resolution,
  pause/progress and failure identity are validated. Native quest handlers/loot
  rights, tank-led safe pulls, explicit missions and owned-alt builds survive.
- Initial package changes 13 existing methods/adds three helper classes and keeps
  110 other entries byte-identical. Final helper `peers` correction preserves
  118 other entries. 35 effective/installed production-gate checks and actual
  intermediate native handler/reward-job fixtures pass; the full source and
  companion suite passes. 51 source hashes and 96 effective methods/lambdas match;
  original schema/synthetics/enum switches remain. All 31 client hashes, base JAR,
  launcher and 35 settings/preset/archive files are preserved; current inventory
  passes 15 checks and records 70 client/72 server receipts.
- GameServer remains stopped by user instruction; native full-tick/geodata/object/
  reward/client acceptance PB-VAL-002 is pending. PB-PORT-003 is not closed. Next
  offline implementation is PB-PORT-004: expanded positive multipliers, bounded
  state-specific strategy composition and continuers. Keep all remaining class,
  world-travel/dungeon and item-ID release investigations open. See current port
  tracker, validation and `docs/PLAYERBOTS_QUEST_ARBITRATION_20261004.md`.
  Use `stage_quest_arbitration_update.py` and `install_quest_arbitration_offline.py`;
  existing helper changes require explicit runtime SCOPES as well as disk HELPERS.
  The next attach revision remains >37, with preloading, hash guards and rollback.

- Tank front-slot and ranged melee-rush repair is installed live/disk in cumulative
  receipt `backups/playerbots-recruitment-20261004-211935-035574`, retaining main
  repair `...211220-016216` and every earlier `...203902-131350` baseline entry.
  Preserve `PlayerBotCombatPosition`, role-sorted FormationLayout, stationary-turn
  Formation, native range-shell Navigation and final Session/CastAction/ReachAction
  gates. Ordinary learned offensive range prevents rare longer spells from
  stranding the main rotation. Support Chanters retain melee behavior; friendly
  recovery and explicit orders remain intact. Existing helper changes require
  explicit runtime SCOPES, not HELPERS alone. Next update agent revision >39.
- GameServer was already running (PID 24908, started 20:39) during this repair.
  Offline installer safely refused; automatic review initially rejected live
  mutation due to the older stopped-server policy. The user then explicitly
  approved applying this verified live update. Fresh agents 38/39 preloaded the
  original cumulative classes, redefined six then one definitions and preserved
  five companions, existing settings and one human connection. No server start,
  stop/restart or client replacement occurred. Do not infer future startup or
  shutdown authorization from this bounded live-update approval.
- Full source suite and 38 position checks pass, with existing offense/quest/
  formation checks preserved. 51 pinned hashes/115 effective methods match;
  22 real read-only role/range/geometry checks pass. All 15 mod checks, 31 client
  hashes, base/launcher and 114 earlier entries survive (120 retained in final
  one-method refinement). Forty settings files checked; two generated provenance
  lists grew during gameplay with preference values intact. Inventory: 70 client/
  74 server receipts. See `docs/PLAYERBOTS_POSITION_20261004.md`. PB-VAL-005 actual
  client movement/spacing/casting remains pending; full port, post-revive client
  acceptance and item-ID release-path investigation remain unfinished.

- PB-PORT-004 strategy composition/weighted expansion/fresh continuers are now
  installed live/on disk in cumulative receipt
  `backups/playerbots-recruitment-20261004-213711-193912`, preserving position
  `...211935-035574` and every earlier mod. Preserve `PlayerBotArbitration` and
  `PlayerBotStrategyComposition`, Session state/default/chain wiring, context
  invalidation and engine delegation. Six existing methods/two classes changed;
  nine helper classes added; 120 earlier entries byte-identical. Update agent 40
  preloaded originals and preserved five companions/settings/one human connection.
  User's subsequent “Finish it” authorized this reviewed live update; no server
  startup/shutdown/restart or client replacement occurred. Future lifecycle
  actions still require applicable authorization, not this historical receipt.
- 49 engine/native-chain tests pass offline and against actual loaded helpers;
  five naturally scheduled session contexts observed. 81 effective loaded
  methods match the package, 115 existing port methods match source. All 15 mod
  checks/31 client hashes/base/launcher survive; 43 settings files reviewed with
  only two ordinary gameplay provenance lists growing, preference values intact.
  Actual native chain casts/client combat/transitions remain PB-VAL-009. The full
  port, other PB-VAL acceptance and item-ID release investigation remain open.
  See `docs/PLAYERBOTS_ENGINE_COMPOSITION_20261004.md`.
- Full upstream inventory is complete in `docs/PLAYERBOTS_SUBSYSTEM_INVENTORY.md`:
  untruncated pinned tree/17 additional unchanged verified sources, scope IDs
  and independent world/invitation/trade dependencies. Do not repeat the pass.
  Next companion implementation is PB-PORT-005, the first complete native class
  strategy slice. Future changed helpers need explicit runtime SCOPES as well
  as HELPERS; next update agent revision must exceed 40.

- Owner-controlled ranged spacing and finite retreat are installed live/disk in
  latest cumulative receipt `backups/playerbots-recruitment-20261004-220541-625450`,
  retaining main `...215853-061213`, newer engine `...213711-193912`, earlier
  position `...211935-035574` and all prior mods. Preserve `PlayerBotSpacing`
  (including Values/Engagement), Session tick/snapshot, CombatPosition range/gates,
  FormationLayout destination, Formation close and HTTP action integration.
  Reopen Companions → ranged character → Overview → Ranged spacing: default 4 m
  owner spread (2–12), 10 m attack distance (4–18), capped by native reach.
  Account/character-checked `config/playerbots/spacing-character-*.properties`
  persist settings without changing alt builds. Line wings scale proportionally
  to retain distinct slots; tanks/melee/support Chanters retain normal positioning.
- One at-most-2 m retreat per engagement replaces sustained full-spell-range kiting.
  Owner leash bounds retreat; blocked movement consumes the allowance too. Target
  changes and short gaps cannot renew it; five seconds without a target reset it.
  Native hazard/movement/cast/resource/animation gates and friendly recovery survive.
  Existing helper changes need explicit runtime SCOPES, including Spacing.formation,
  not HELPERS alone. Fresh agents 41/42 preloaded originals and redefined five then
  one classes, preserving five companions/settings and one human connection.
  The retained explicit live-update approval applies; no startup/stop/restart or
  client replacement occurred. Next attach update revision must exceed 42.
- Full source suite, 35 effective spacing/HTTP/ownership/Line/finite-retreat checks,
  native WebKit controls and 27 fixture actions pass. 22 real policy/geometry reads,
  57 selected loaded-method comparisons and 51 hashes/135 disk-source methods pass.
  126 earlier entries survive; follow-up retains 133. All 15 mod checks/31 client
  hashes/base/launcher/preferences survive; 40 settings files reviewed, with five
  generated provenance lists growing through gameplay. Inventory: 70 client/77
  server receipts. See `docs/PLAYERBOTS_SPACING_20261004.md`. PB-VAL-005 real client
  follow/terrain/pursuer combat/shots, PB-VAL-009 native strategies/chains, post-revive
  client acceptance, full port and item-ID release-path investigation remain open.
  Preserve full subsystem inventory; next broad implementation remains PB-PORT-005.

- PB-REPAIR-INV-001 stale committed deletion records is installed live/disk in
  cumulative receipt `backups/playerbots-recruitment-20261004-224922-627052`,
  retaining spacing `...220541-625450`, engine composition and every prior mod.
  Preserve `InventoryDAO.storeCompanionInventory` filtering NEW/UPDATE_REQUIRED/
  DELETED before custody checks, SQL operations and post-commit bookkeeping.
  UPDATED/NOACTION deletion-queue objects may have legitimately reused IDs:
  never reissue these records, change foreign rows, or remove the custody guard.
  One method/class changed; 133 earlier entries byte-identical. Agent 43 preloaded
  originals and preserved five companion sessions/one human connection/44 settings.
  85 offline production checks pass; 31 captured loaded InventoryDAO methods match.
  15 mod checks/31 client hashes/base/launcher survive (70 client/78 server receipts).
  User explicitly requested repair/install and will do gameplay testing; no forced
  save/tick, restart or client replacement occurred. Periodic checkpoint/dismiss/
  resummon acceptance, separate Tanku wipe/summon error and historical ID release
  investigation remain open. See `docs/PLAYERBOTS_CUSTODY_DIAGNOSIS_20261004.md`.
  Next broad port remains PB-PORT-005; next attach update revision must exceed 43.

- Tank boss-dragging PB-REPAIR-TANK-001 is installed live/disk in cumulative
  receipt `backups/playerbots-recruitment-20261004-232048-163778`, retaining
  `...224922-627052`. Two Coordination methods remove the recursively recentered
  facing walk and skip targeted-cast spread only for a tank holding that caster.
  Native reach, real hazard escape, threat and all 134 earlier entries survive.
  Full source/offline checks and 22 regressions pass; old effective code fails.
  Automatic approval review initially rejected live installation because the
  earlier approval covered another repair; the user then explicitly approved
  this tank repair. Fresh agent 44 preloaded originals/updated one class and
  preserved five companions/one human connection. 57 selected loaded methods,
  51 pinned hashes/140 effective source methods pass; 44 settings, 31 client
  hashes, base/launcher/media and all 15 mod checks survive (70 client/79 server
  receipts). Tanku's real read-only policy check passed while idle; no boss-fight
  acceptance is claimed. No startup/stop/restart/client replacement/forced tick/
  movement/cast/DB/ID writes occurred. Next attach update revision must exceed 44;
  preserve preloading/hash/rollback and explicit runtime SCOPES for helper edits.
  See `docs/PLAYERBOTS_TANK_POSITION_20261004.md`.
  PB-VAL-005 actual boss position/terrain/hazard/attack acceptance stays pending;
  PB-PORT-005 is still next broad slice and PB-PORT-007 full tank/pull remains partial.

- PB-PORT-005A Sorcerer single-target strategy is installed live/disk in latest
  cumulative receipt `backups/playerbots-recruitment-20261004-234841-695964`,
  retaining tank `232048-163778`, custody, engine, spacing and every prior mod.
  Preserve `PlayerBotSorcerer`, Offense.routine single-target delegation,
  CombatBuffs native BOOST_SPELL_ATTACK/BOOST_CASTING_TIME_SKILL eligibility,
  Session priority and named strategy wiring. Learned native chain/upkeep/filler/
  affordable fallback, mana and boost decisions use pinned Fire/Generic Mage
  sources. Mana priority is normalized below native encounter protection/escape.
  No owned-alt or Temporary build/skill/Stigma/equipment replacement is involved.
- Four methods/three existing classes, one helper; 133 earlier entries unchanged.
  Agent 45 preloaded originals and retained five companions/one human/46 settings.
  Full source/offline suite and 54 new production checks pass; 193 observed loaded
  methods and 163 effective source methods/51 hashes match. All 31 client hashes,
  base/launcher/media and 15 mod checks survive (70 client/80 server receipts).
  User explicitly requested porting/installation and will do gameplay tests; no
  restart/client change/forced native tick, cast, movement or save occurred.
- Do not repeat 005A because actual native/client acceptance PB-VAL-009 is pending.
  Next concrete slice is **PB-PORT-005B Spiritmaster learned single-target/pet
  strategy**, from pinned GenericWarlock/Affliction sources and native pet gates;
  necessary pet coordination is PB-PORT-008. Full class/Sorcerer AoE/CC/escape,
  independent world/invitation/trade/travel/dungeon scope and separate historical
  ID release/wipe-summon investigations remain open. See
  `docs/PLAYERBOTS_SORCERER_STRATEGY_20261004.md`. Use `stage_sorcerer_update.py`;
  changed existing helpers require runtime SCOPES, not HELPERS alone. Next attach
  update agent revision must exceed 45, with preloading/hash guards/rollback.

- PB-SCOPE-003A native owner gifts are installed live/disk through latest
  `backups/playerbots-recruitment-20261005-005644-110252`, retaining main
  `004941-367262` and every earlier Sorcerer/tank/custody/engine/mod entry.
  Preserve PlayerBotTrade/TradeStore, ExchangeService begin/add/lock/confirm/cancel,
  CM_EXCHANGE_REQUEST and Session tick/markClosing/equip hooks. Owner item/Kinah
  gifts use durable exact custody before native success; no persisted split IDs
  are released on successful completion. Ambiguous commits hold inventories/IDs.
- The user's explicit gift request applies to owned alts and Temporary Bots:
  donated legal upgrades may equip/bind/identify through native rules and class/
  role scoring; old gear returns to cube. Other alt gear/build/class/level/skills/
  Stigmas are untouched. No general alt automatic gear policies are enabled.
  ITEM_USE binding/identification is serialized and pauses follow AI until done.
- Full source/offline and 56 final production tests pass; 218 captured loaded
  methods and 190 source audit methods match, all 51 hashes/15 mod checks/31 client
  hashes pass. Agents 47/48 preserve ten companions/two humans and preferences.
  User gameplay/persistence acceptance PB-VAL-010 is pending. Outgoing bot offers,
  group/world trades/value/discount/crafting remain partial. Next companion class
  slice stays PB-PORT-005B; trade work remains an independent track. See
  docs/PLAYERBOTS_TRADING_20261005.md. Future existing helper edits require runtime
  SCOPES (including Trade.equip/tick), not just HELPERS. Next attach revision >48.
  No lifecycle/client changes or forced live trade/DB tests occurred.

- Latest cumulative continuation `backups/playerbots-recruitment-20261005-042546-945113`
  installs PB-REPAIR-SETTINGS-001, retaining gift `005644-110252` and native shield
  `saendukal-strong-protection-20261005-025825` plus every earlier mod. Preserve
  PlayerBotSettingsFiles and QuestSync.State.save's atomic replace with four
  attempts/25-50-100 ms sharing-denial backoff; permanent errors stay visible.
  Agent 49 preloaded originals and preserved five companions/one human. The user
  explicitly requested fast implementation and no further tests; full compile/21
  offline policy checks predated that instruction, native/client checks remain
  with user. No restart/client/forced quest/save/DB/ID changes. Exact chained
  native shield receipts now participate in stager hash guards; never drop that
  overlay or relax guards. Next attach revision >49. See
  docs/PLAYERBOTS_SETTINGS_FILES_20261005.md; next broader class slice stays 005B.
