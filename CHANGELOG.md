# Aion Aetherfall 4.8 Repack — Changelog

## Latest published release — 0.98.3 (26 October 2026)

[Download 0.98.3](https://github.com/AngelShade/Aion-Aetherfall-4.8-Repack/releases/tag/v0.98.3), or open Aetherfall Setup and choose **Update Repack**. This cumulative update includes the earlier 0.97 quests and interface repairs, Find Group announcements, Boons/target artwork, first-use tooltips and the quit-countdown crash repair.

Install directly over 0.86 or any later repack. Setup/Hub remains 0.90.1022.2; the original 0.86 archive remains the compatible base download. Existing accounts, characters, settings and profiles are retained. GitHub publication was verified on 27 October 2026; gameplay acceptance remains with the owner.

## Newer 4.8 source work (27 October 2026)

These maintained changes are separate from the published 0.98.3 payload. A source commit does not publish a repack update:

- Restricted controlled companions' healing, buffs and resurrection to their party/alliance and pets. Independent world bots retain bounded public assistance. Party combat takes priority over optional looting, quest work, rest and equipment changes.
- Let nearby earned corpse loot take priority over formation while the owner is stationary, releasing that priority when the owner moves or combat/support requires it. This companion repair has a normal build and focused offline checks; installation and gameplay acceptance are pending.
- Reduced repeated interface work: opacity applies to new or changed windows, unchanged native policies return early, and safe layout holds survive zone/loading transitions.
- Corrected Move UI reset positions and grouped shortcut movement. Added a one-time conversion of older saved world-tooltip Mover defaults to Cursor; later explicit choices remain saved.
- Updated cursor tooltips to use the live mouse position, added native right-drag camera turning and kept window backdrop hooks from blocking the tooltip placement policy.
- Added quest-tracker layout preparation that reduces its full-screen frame to its content height so it can move vertically. Current maintained versions are native Core 1.2.77 and Lua 1.2.79; visual, input and transition behavior still require in-game acceptance.
- Made the release workspace configurable outside source and used the maintained repack's matching changelog when preparing its downloader. Local workspace settings remain private.

## Newer 4.8 source work (25 October 2026)

The 0.97 game changes listed here are published. The website remains a separate installation:

- Added **The Unbroken Field**, sixteen original quests (14980–14995) continuing The Writing on the Wall across Verteron, including native dialogue, objectives, rewards and the Copied Seal Impression quest item.
- Added quest-target tracking and further quest text, completion-dialogue and interface refinements.
- Updated open-world group scaling to react to nearby parties within 100 metres and refresh visible HP without respawning monsters. This extends the earlier scaling shipped in 0.96.6.
- Repaired native selected-boon Account details, companion-menu artwork refresh after theme changes and companion-window sizing for clearer text. Visual results remain in-game checks.
- Added a separate Aetherfall website project with native account/character integration, news, guides, downloads, gallery and administration. Its portable build tools are included in source; the website is not bundled in the repack.

## Earlier development updates — Companions, PvP and client repairs (22 October 2026)

These changes were included in the published 0.95 repack and remain included in 0.96.6. Pushing newer source does not publish another game release.

### Playerbots and combat

- Added configurable faction PvP against enemy players, world bots and their pets, using native Aion combat rules. Companions assist actual party attacks or explicit orders and defend against aggression; simply selecting an enemy during an unrelated monster fight does not start PvP. Passive orders still prevent attacks.
- Added independent bot trips through real open rifts, with normal entry restrictions, physical travel, saved rest periods and group members following their bot leader. Bots continue ordinary world activity between trips.
- Added faction-correct homeland placement and shared-map camp selection. World bots appear away from human players and opposing camps instead of being moved into the wrong homeland.
- Added `.bot pull` and `.bot mark kill|cc|clear`: a tank tags a selected monster and brings it back while the party waits; kill marks focus an engaged target, and crowd-control marks are held with sleep, paralysis or fear while companions avoid damaging them. Tanks can gather loose enemies with safe area-threat skills even when ordinary area attacks are off.
- Added class-specific skill priorities for Ranger, Gunner, Songweaver and Aethertech, including available follow-ups, useful debuffs, periodic damage, close-range control and recovery. Learned skills, equipment, cooldowns and native casting checks remain authoritative.
- Repaired bot-owned spirit following, waypoint completion and return-to-master behavior during combat, quests and travel. Spirits beyond the normal distance limit are released; human-controlled spirits retain their existing behavior.

### Movement and world activity

- Improved formation spacing on stairs and slopes so companions use reachable ground beside the owner. Descending stairs no longer causes an ordinary walking step to be mistaken for a fall.
- World-bot parties now gather behind their bot leader without inheriting human-companion catch-up speed or crowding into the same formation slot.
- Navigation learns blocked terrain spots, avoids repeatedly failed destinations and checks already loaded navigation meshes before expensive ground probes. Native collision and hazard checks still govern actual movement.
- Reduced repeated quest-material and travel-destination calculations, and added optional route diagnostics to help locate remaining terrain problems.

### Client, quests and server repairs

- Corrected camera jumps and reversed movement at the start of a drag, including repeated drags with the native UI cursor. Corrected, upstream and disabled camera-fix modes remain available.
- Disabled ReShade's screenshot shortcut so Print Screen remains available to the game's normal screenshot behavior.
- Improved the Player Companions window's fit at different UI scales and reduced repeated resize work. Restored the shared Wardrobe/interface timer after world transitions so its updates can resume reliably.
- Quest acceptance now closes the dialog correctly after the native quest-capacity refusal instead of treating the acceptance action as a page number. The normal quest limit and refusal message remain in place.
- Reduced Central Market database contention and repeated catalog scans, and separated web-window requests from shared gameplay workers. Account-window artwork can be cached, and cancelled browser downloads no longer appear as server errors.
- Fixed camp raiders stopping after their first movement step and prevented movement requests for NPCs that have already despawned.

Normal builds and focused offline checks are recorded for these changes. Stair
following and the camera correction have owner confirmation; broader PvP, rift,
combat, spirit, quest and performance acceptance remains in-game testing.

## Downloader kit for new installations (introduced in 0.96.6)

- Each update now prepares a complete downloader ZIP with the current Setup,
  mirror list, extraction/download tools and clean portable settings. New users
  can receive the MEGA-to-Google Drive repair before downloading the base game.
- Publish Update includes this ZIP and changes the public download links to
  follow the latest release. The large 0.86 base archive remains compatible.
- The complete downloader ZIP was first published with 0.96.6. Use the kit from the latest release for new installations; Setup 0.90.1022.2 includes the shared mirror retry repair and retains the compatible 0.86 base archive.

## Setup download mirror fallback (21 October 2026)

- Standalone Setup now loads the standard mirror list when no mirror URL was
  saved, allowing Google Drive to take over when MEGA refuses a download.
- The original base archive has both mirrors built in if mirror metadata cannot
  be loaded. Existing custom mirror settings remain supported.
- Initially prepared as Setup build 0.90.1021.1; the published 0.94 release
  includes the later Setup build 0.90.1022.1 with this fallback retained.

## Hub automatic updates (18 October 2026)

- Update now uses the saved online channel, including the client's update
  settings, or the standard Aetherfall channel when none is configured.
- Download and apply updates automatically; the file picker is reserved for
  the separate manual patch action. Connection errors remain visible.
- Included in the published cumulative updates through 0.94.

## Aetherfall Hub icon (18 October 2026)

- Added an original generated Aetherfall emblem to the Hub/Setup executable,
  including Explorer and taskbar icon sizes. The aether crystal, contrasting
  wings and fractured-world base represent the repack's world and factions.
- Included in the published Hub/Setup. The icon does not change gameplay.

This history includes the full initial-release changelog for 0.86, followed by
the updates through 0.98.3. Earlier development work is recorded under 0.86,
because that is the first public repack in which those changes were bundled.

| Version | Release | Download |
| --- | --- | --- |
| [0.86](https://github.com/AngelShade/Aion-Aetherfall-4.8-Repack/releases/tag/v0.86) | Initial complete repack | Full base archive: 36.35 GiB / 39.03 GB |
| [0.87](https://github.com/AngelShade/Aion-Aetherfall-4.8-Repack/releases/tag/v0.87) | Multiclient, installer detection and server repairs | Cumulative patch: 18 files, about 15 MB |
| [0.88](https://github.com/AngelShade/Aion-Aetherfall-4.8-Repack/releases/tag/v0.88) | Fresh-install world-bot startup repair | Cumulative patch: 23 files, about 15 MB |
| [0.89](https://github.com/AngelShade/Aion-Aetherfall-4.8-Repack/releases/tag/v0.89) | Saved world-bot reserve placement | Cumulative patch: 23 files, about 15 MB |
| [0.90](https://github.com/AngelShade/Aion-Aetherfall-4.8-Repack/releases/tag/v0.90) | Quest reward upgrades, Setup server controls and start-stage repair | Cumulative patch: 25 files, about 20.1 MB |
| 0.91 | Pet mood reactions and saved bot ranks | Included in the cumulative 0.92 patch; no separate repack download |
| [0.92](https://github.com/AngelShade/Aion-Aetherfall-4.8-Repack/releases/tag/v0.92) | Party UI, legion invitations, bot lifts and Setup update fixes | Published cumulative patch; includes 0.91 |
| [0.92.1](https://github.com/AngelShade/Aion-Aetherfall-4.8-Repack/releases/tag/v0.92.1) | Missing broker sellers in fresh and existing repacks | Published cumulative patch |
| [0.92.2](https://github.com/AngelShade/Aion-Aetherfall-4.8-Repack/releases/tag/v0.92.2) | Revert the pet interaction changes introduced in 0.91 | Published cumulative patch |
| 0.92.3 | Setup server shutdown and leftover console repair | Included in 0.93; no separate release download |
| [0.93](https://github.com/AngelShade/Aion-Aetherfall-4.8-Repack/releases/tag/v0.93) | Bot navigation, companion spacing and Profile Stigma controls | Published cumulative patch; includes 0.92.3 |
| [0.93.1](https://github.com/AngelShade/Aion-Aetherfall-4.8-Repack/releases/tag/v0.93.1) | Companion follow, lifts, quest turn-in and memory controls | Published cumulative patch |
| [0.93.2](https://github.com/AngelShade/Aion-Aetherfall-4.8-Repack/releases/tag/v0.93.2) | Companion pursuit, teleport statue timing, Protectors Hall and Zirius | Published cumulative patch |
| [0.94](https://github.com/AngelShade/Aion-Aetherfall-4.8-Repack/releases/tag/v0.94) | Account Fame, native companion/account windows, Interface and world textures | Published cumulative patch, Setup and current downloader ZIP |
| [0.95](https://github.com/AngelShade/Aion-Aetherfall-4.8-Repack/releases/tag/v0.95) | Marketplace window, interface controls, navigation, quests and encounter repairs | Published cumulative patch |
| 0.96.5 | World-bot population, nearby presence, legions, water safety and recovery | Included in 0.96.6; no separate release download |
| [0.96.6](https://github.com/AngelShade/Aion-Aetherfall-4.8-Repack/releases/tag/v0.96.6) | Verteron quests, loot ownership, monster difficulty and interface controls | Published cumulative patch, Setup and downloader ZIP |
| [0.97](https://github.com/AngelShade/Aion-Aetherfall-4.8-Repack/releases/tag/v0.97) | Current original quests, loot, scaling, native windows and artwork/launcher repairs | Published cumulative patch |
| 0.98 | Find Group, Boons/DP artwork, file verification and recovery parity | Included in 0.98.3; no separate release download |
| 0.98.1–0.98.2 | First-use tooltip initialization and original-to-repack sync | Included in 0.98.3; no separate release downloads |
| [0.98.3](https://github.com/AngelShade/Aion-Aetherfall-4.8-Repack/releases/tag/v0.98.3) | Quit-countdown crash repair and earlier parity/tooltip updates | Latest published cumulative patch, Setup and downloader ZIP |

## 0.86 — Initial release: full changelog

### Complete portable repack

- Bundled the Aion 4.8 NA client, GameServer, LoginServer, ChatServer, Java 25 and MariaDB in one package.
- Included fresh database setup without development accounts, characters or gameplay history.
- Configured local connections and automatic creation of normal player accounts on first login.
- Added graphical Setup with installation, download progress, server controls and client launch.
- Supported resumable torrent/HTTP downloads and installation from an existing local archive, including archives downloaded manually from the mirrors.
- Added archive checksums, extraction and small update patches through Setup. The database data directory is excluded from patch payloads.
- Supplied numbered startup/shutdown scripts alongside the graphical controls.

### Party companions and account alts

- Recruit your own offline account characters or temporary companions into your party.
- Manage companions through the in-game dashboard and companion toolbar, including roles, formations, combat behavior, skills and equipment.
- Added party quest completion sharing for owned alts: catch up on the main player's previously completed quests and share future completions while grouped.
- Shared completions grant the quest's normal rewards, including XP, Kinah and applicable items. Reward choices account for the companion's class.
- Added duplicate-reward protection and retained repeatable-quest completion limits. Unrelated players and independent world bots do not inherit the main player's quest history.
- Added companion Journey choices: begin at level 1 or take the level 10 ascension route, with faction-appropriate class, skill, reward and destination handling.
- Corrected companion body generation to retain the owner's proportions and use supported native face, hair and colour choices. Existing affected companions are repaired on recruitment.
- Added recruitment into native alliances, including expansion from a full ordinary party where leadership and eligibility allow it. The native 24-person alliance capacity still includes human members.
- Added companion resurrection, summon/recall and post-wipe recovery handling, retaining normal recovery conditions and costs.
- Added confirmed roster removal for Temporary Bots. Removed bots are archived from the roster rather than deleting their saved character and inventory rows; owned alts remain protected.

### Companion equipment, supplies and personal services

- Added equipment policies for earned gear, a one-time starter set for empty slots and optional generated upgrades.
- Added configurable equipment profiles, quality/level/weapon limits, upgrade thresholds and loot-roll choices. Earned equipment is the default policy.
- Added optional real-shop gear purchases using the bot's own funds and spending budget.
- Added finite consumable replenishment for Temporary Bots while retaining owned alts' existing supplies and builds.
- Added native owner-to-companion item and Kinah gifts through the trade window.
- Added companion private-warehouse listing, deposits and withdrawals through an accessible warehouse keeper.
- Added a Mail tab and commands for listing, reading, collecting and managing companion mail, plus sending items/Kinah with native postage and mailbox requirements.
- Added requested whole-stack legion-warehouse deposits for eligible companions, retaining same-legion membership, permissions and keeper requirements.
- Added responses to eligible native legion invitations. Independent world bots retain their own controller/activity, while private companion enrollment remains owner-controlled.
- Preserved item identities and saved ownership across inventory, warehouse, mail and legion transfers.

### World bots, movement and combat

- Included independent world bots with configurable population, faction balance, levels, presence periods and world activity.
- Included autonomous questing, combat, gathering, crafting and town services. Activity pauses on maps and instances without human players by default.
- Added persistent population pools and schedules so saved bot identities, levels, equipment and progress can be reused across sessions and population rotation.
- Added population admission checks that count pending and off-map residents as well as visible bots, preventing geographic redistribution from creating extra population slots.
- Added player-area population placement and regional/level spreading without changing the configured global target.
- Added generated, pronounceable names that persist across restarts; human-selected character names are retained.
- Separated controlled-companion work from background world/maintenance work and reduced repeated navigation, support-target and quest-planning calculations.
- Added bounded navigation workers, retained pending route progress and reduced repeated terrain probes after movement or combat transitions.
- Improved terrain navigation, intermediate route connections, slope and ledge descent, height following and moving-target routes.
- Added owner-glide following and corrected conflicts between travel, combat and height-follow behavior.
- Improved manual withdrawal and safe regrouping so companions can return to normal combat behavior after regrouping.
- Added shared corridor movement with checks for blocked or expired routes and cancellation when commands or casting take priority.
- Added playerbot fall-damage immunity.
- Corrected Aethertech Charging Activation classification, Recharge use and travel/combat handling of Mobility, Stability and hate-related toggles.
- Updated player search to include matching self and eligible online bots while retaining normal filters and privacy rules.
- Corrected dormant bot deadlines so waking bots do not count their entire sleeping period as overdue decision work.

### Class combat and party support

- Improved melee/tank positioning, ranged spacing, finite retreat and shared target selection.
- Added native cast admission, resource/cooldown checks, interruption/cancellation handling and heal/cleanse reservations.
- Corrected stationary caster and Spiritmaster preparation, including summon preparation without requiring an already active pet.
- Improved Cleric recovery ordering to distinguish immediate heals, delayed healing ticks and conditional HP recovery.
- Added Chanter mantra maintenance based on party needs, preserving active mantras and native limits.
- Improved Templar defensive choices under pressure while retaining shield, resource and cooldown rules.
- Prioritized Gladiator counterattacks during genuine native counter opportunities.
- Corrected Assassin rune-finisher ordering relative to ordinary builders and chain attacks.
- Retained native skill execution, pet checks and human movement/attack validation while removing inappropriate client-packet timing accusations against headless bots.

### Loot, trading and custom services

- Added compact Need / Greed / Pass loot panels, with up to five visible item panels, native item artwork, tooltips, countdowns and displayed choices.
- Added companion loot decisions based on item usability and role, with independent roll rounds and normal server-side grants. Native bidding remains available.
- Included Central Market buying, selling, trade history and market storage.
- Included the in-game Cash Shop, Wardrobe and Season Pass/Aetherpass interfaces.
- Updated Cash Shop quantity entry and Max controls, with server checks for total price, balance, item quantities and delivery capacity before payment.
- Added native item artwork to custom menus and corrected sprite sizing and layout handling.
- Removed bundle-item reuse cooldowns across matching server and client item data, covering bags, boxes, pouches and selectable chests. Normal opening animation/casting remains.

### Inventory and storage

- Expanded the unified inventory to 189 base slots, with supported expansion up to 288 slots. Previously earned expansion credits are retained.
- Updated native inventory layout, search, filtering and slot bounds to match the server capacity.
- Enabled expanded warehouses: 360 Character Warehouse slots and 540 Account Warehouse slots.
- Added scrollable warehouse layouts and separate Search/Clear controls while retaining native item positions, sorting, transfers and tooltips.

### Quests, dungeons and encounters

- Included repairs to Theobomos Lab and Adma Stronghold quest chains, scripted encounters, boss behavior, loot and exits.
- Corrected Lannok and Triroan boss classification and matching native boss-frame presentation.
- Repaired the Triroan completion exit and campaign confirmation flows after cinematics.
- Corrected Pocaching's encounter profile.
- Restored Silikor's existing loot rules through a correction specific to that NPC.
- Restored affected Adma prerequisite quest chains, including collection, kill, object-drop, reporting and normal reward steps.
- Corrected dungeon AI/instance script bindings that could prevent affected encounter scripts from compiling during startup.
- Included Steel Rake encounter work for steam/bomb warnings, protected phases, captain adds/towers, Mantutu's food/water interaction, amplifier phases and attack pauses.
- Added Steel Rake companion target choices that exclude scenery and prioritize relevant engaged adds.
- Corrected the MuMu lookout's spawn/patrol height and MuMuLu's supported placement; retained availability of the six MuMu quest targets.
- Reduced the affected MuMu quest-target and Rampaging Mosbears respawn timers to 30 seconds, including Komu Silverclaw.
- Disabled dungeon entry limits by default while retaining level, quest, party and other entry requirements.
- Included the reviewed upstream 4.8 fixes to quests, skills, movement, PvP, crafting, appearance, networking and trade/shop behavior.

### Client, artwork and launcher

- Added the graphical Aetherfall launcher with custom artwork, borders, logo, launch settings, connection status and an Installed Mods page.
- Added a loading bar and native login-ready handoff. The game stays hidden through its splash/loading stages and appears when the account/password screen is ready.
- Corrected unwanted startup taskbar previews and launcher focus behavior, including the launcher staying above other applications.
- Added Aetherfall login/character-selection branding and login news, with corrected logo outline and login-form readability/alignment.
- Added optional remembered-login support and speech bubbles.
- Added native HUD shortcuts for Central Market, Aetherpass, Journey and Wardrobe; Wardrobe sits beside Journey.
- Included Direct3D 9/Vulkan selection through DXVK, in-game graphics controls, environment texture/visual packages and renderer recovery support.
- Included native cursor corrections and the client-side free-flight setting.
- Organized client support tools under feature-specific Mods folders, keeping DLLs, archives and required settings at the native paths used by Aion.
- Included links and hashes for the scanned DLL versions in Mods/Security. The reports describe those specific files and are not a blanket safety certificate.

### Server compatibility and administration

- Integrated the reviewed upstream 4.8 changes while retaining Aetherfall companions and custom services.
- Updated fresh database schemas for PvP EXP, recipe production limits and native appearance-column changes.
- Replaced the previous JSON dependency with supported Gson-based serialization across the server components.
- Restored staff enchanting/manastone completion behavior and expanded skill-removal command handling, including protection for stigma skills and confirmation for bulk removal.

## 0.87 — Multiclient, installer and server fixes

### Client and Setup

- Restored multiclient launching. Reopen the launcher and press Play to start another client; use a separate game account for each session.
- Added the Multi-client entry and instructions to Installed Mods.
- Fixed false missing-mod warnings when a mod's files existed but optional checksum metadata was absent.
- Corrected the Player Companions mod's tracked file paths.
- Fixed Setup detecting a completed installation as incomplete and offering another full download. It now checks the actual native GameServer location.
- Retained saved/discovered installation paths when a newer release uses a different version or expected folder name.
- Prevented repeated installation of an already completed repack and checked extraction completion before deleting the downloaded archive.
- Updated Setup/Hub delivery and small-patch metadata so existing users can receive the installer correction through self-update.

### Server

- Fixed Season Pass date/time JSON handling for ZoneId and Instant using supported public APIs.
- Corrected patrol respawn ownership and formation membership handling.
- Prevented stale patrol returns from referring to replaced actors or missing formation shifts.
- Updated GameServer, LoginServer, ChatServer and their matching Commons libraries together.

## 0.88 — World-bot startup repair

- Fixed fresh repacks failing to start world bots with: **"Population import is not committed; no residents were started."**
- Corrected the fresh database setup: it had created the population tables without the required initial state row.
- Added the initial native population state to the fresh schema and bootstrap.
- Added a repair before server startup for already installed repacks whose population tables and bot roster are still empty.
- Added checks for database ownership, connected game servers, legacy population files and existing bot records before initialization.
- Preserved existing populations, imports, accounts and characters. Existing bot data is never replaced with an empty population to suppress an error.
- Kept the repair repeatable: once the initial state exists, running the startup repair again retains it.
- Included all 0.87 changes in the cumulative 0.88 patch, allowing a direct update from 0.86.

## 0.89 — Saved world-bot reserve placement

- Corrected repeated Elyos creation failures when the only online player is in an Asmodian area, and the equivalent case for the other faction.
- Separated saved reserve creation from visible population admission. Saved characters can use valid faction-and-level locations while visible population placement retains its player-area restrictions.
- Retained configured map permissions, faction/level eligibility, safe ground, nearby-human and landing-occupancy checks.
- Corrected budget-limited placement scans being reported as failed creation. Deferred scans retain their progress and allocate no character ID or population event until a destination is ready.
- Retained explicit error reporting when the eligible destinations are genuinely exhausted.
- Preserved existing bot identities, levels, equipment, quest progress and owner population settings.
- Includes the 0.87 and 0.88 fixes in the cumulative update.

## 0.90 — Quest rewards and Setup server controls

- Updated Quest `29048` (*Seriphim's Teachings*) to reward the permanent full Ninja motion card (`188508001` — idle, run, jump and rest motions) instead of the 1-day temporary version (`188508000`).
- Updated Quest `29045` (*A Passion for Pets*) to reward the permanent level 1 Tiamat Whelp egg (`190020208`, pet `900192` with auto-loot and doping food/drink/three scrolls) instead of the Purple Ribbit (`190000055`).
- Added separate graceful Stop GameServer, Stop LoginServer and Stop ChatServer controls, with automatic console confirmations after the selected server exits.
- Organized Start and Stop actions into aligned sections with consistent button sizes.
- Made Start database & servers the primary startup action, with Database only and Servers only beneath it.
- Placed Stop database last and gave it a distinct style to make the shutdown order clear.
- Added a dedicated status area for operation progress and errors, with full long messages available on hover.
- Fixed Start database & servers and Database only leaving Setup stuck after the database started. The servers now start right after the database is ready, and Setup can be closed again.
- A failed startup stage now stops the sequence and shows that stage's actual error instead of continuing.

## 0.91 — Pet reactions and saved bot ranks

- Restored the different pet mood reactions and fixed interactions that were incorrectly rejected.
- Fixed saving newly created bot ranks and later rank updates without resetting earned points or saved ranks.
- Included these repairs in the cumulative 0.92 repack update; there is no separate 0.91 repack download.

## 0.92 — Party UI, legion invitations, lifts and Setup updates

- Aligned quest reward previews with the permanent Ninja motion card and Tiamat Whelp egg awarded by the server.
- Fixed unreadable empty-state text in Saved Parties and made room for the native loot-roll footer.
- Added requests addressed to a bot by name for legion invitations, while retaining legion permissions and player acceptance.
- Added spaced lift queues and distinct passenger positions for world bots and companions. Bots retain their ride while the lift moves and step off at the destination.
- Prevented Setup from replacing itself with an older build or choosing an outdated cached patch when checking online for updates.
- Kept update failures visible so players can see why a patch could not be installed.
- Included the earlier pet mood and bot rank repairs. This cumulative update is published through Setup's Update Repack button.

## 0.92.1 — Missing broker sellers

- Fixed fresh repacks starting with no reserved broker sellers, which prevented the market from filling and produced a warning for each faction.
- Added the six offline market sellers to fresh database setup: three for Elyos and three for Asmodians.
- Added a repair before server startup for existing installations with missing sellers. Existing working sellers are retained; conflicting character identities stop the repair rather than being overwritten.
- Preserved player accounts, characters, items and existing market listings. Repeating the repair does not create duplicate sellers.
- Corrected the repair's database text comparisons to use the existing player table's settings.
- Updated the full repack and local installed copy. The cumulative patch is published on GitHub and included in Setup updates.

Broker stock fills through the normal market service after the servers start. The seller repair was verified in the installed repack; the next in-game market check remains pending.

## 0.92.2 — Restore previous pet interactions

- Reverted the pet interaction changes introduced in 0.91 after they were reported to break pet interactions.
- Restored the previous handling of interaction requests and the previous pet reaction response. Removed the newly randomized reaction result.
- Kept the other fixes from 0.91, 0.92 and 0.92.1, including saved bot ranks, quest rewards, party UI, lift handling, Setup updates and broker sellers.
- Existing pets, mood progress, gift cooldowns, items and character data are retained. No database reset or client DLL replacement is part of this rollback.

Published on GitHub and available through Setup’s Update Repack button. Pet interactions still need in-game confirmation after restarting GameServer.

## 0.92.3 — Setup server shutdown

- Fixed Setup's Stop buttons leaving GameServer, LoginServer and ChatServer windows repeating “Terminate batch job (Y/N)?” after Java had stopped.
- Setup now supplies the confirmation when the prompt repeats, instead of sending blank answers. Console input goes directly to the selected server's console.
- Pressing Stop again also closes that server's finished batch window when Java has already exited. Interactive terminals and consoles containing another running program are left alone.
- Kept normal Java shutdown and saving, the existing shutdown wait and the running database. No force termination was added.
- Updated Setup and the repack Hub to build 0.90.1018.2. The cumulative repack update retains the pet rollback and earlier repairs.

Included in the published 0.93 cumulative patch; there is no separate 0.92.3 release download. Stopping all three running servers still needs user confirmation with the updated Setup.

## 0.93 — Navigation, companions and Stigma controls

- Added prepared main routes and local connections for all 155 maps in the navigation catalogue.
- Added continuous route handoff so bots can prepare the next section while following their current path.
- Preferred ground routes and lifts before considering jump or drop connections.
- Added prepared jump/drop connections with collision checks before launch and completion only after landing.
- Retained bounded planning work and improved admission fairness for waiting bots.
- Preserved the requested 0.92.2 pet-interaction rollback.
- Added Stigma equip/remove controls in Profile and an Upgrade button opening the native Stigma charging window. Costs, materials and slot restrictions remain. Fixed the button and tooltip showing ??? by adding their English text entries.
- Improved companion following across slower and faster leader movement, with stable role spacing and party-wide 2/4/8 m distance buttons.
- Let companion travel continue during post-action waits, and refreshed hostile approach and casting admission.
- Restored the magical reward choices for 34 affected quests.
- Coalesced repeated movement/zone notifications and reduced scheduler scanning overhead.
- Carried the original's matching client signing, native companion controls and graphics restore resources into the repack, with portable ownership paths.
- Retained Setup's server shutdown repair and the earlier broker and population startup repairs.

Applied from the original development installation to the repack and published as the cumulative 0.93 patch. Existing accounts, characters, database contents and local connection settings are retained. Normal builder outputs and copied files are verified; in-game movement, Stigma controls, quest choices and CPU acceptance remain the player's checks.

## 0.93.1 — Companion movement and quest repairs

- Fixed party preset recruitment failing when a full formation could not fit nearby. Companions prefer nearby placement and can fall back to the owner's position.
- Improved companion following on slopes, structures and changing floors. Short movement requests retain their turn while the player moves, and followers keep a bounded trail of the player's footsteps.
- Fixed explicit Attack orders being blocked by Passive mode or a selected controlled target; rejected orders now explain why.
- Improved stationary regrouping and the transition from combat back to following. Walking is preferred before optional route searches.
- Shared prepared route results between bots and reduced repeated route work and temporary allocations.
- Fixed pending lift queue and riding state handling, retained crowd-control state during forced movement, and repaired the reward/payment hook for quest 3074, Dangerous Probability.
- Prebuilt recipe/material lookup indexes to avoid repeated scans during decisions.
- Removed the unused companion dashboard header decoration that could appear as a dark rectangle.
- Added configurable GameServer heap settings based on available RAM, plus navigation memory and per-map route-cache controls. Existing explicit owner limits remain available.
- Retained the current Hub automatic-update action and generated icon, Stigma controls, pet rollback and earlier cumulative repairs.

Copied from the original's normal build into the full and installed test repacks and published as the cumulative 0.93.1 patch. Database contents, characters, credentials and local connection settings are retained. Actual movement, lifts, quest rewards and CPU/RAM behavior still need in-game confirmation.

## 0.93.2 — Companion pursuit, teleport statues and Cursed Zirius

- Improved direct companion pursuit toward the owner and role position, keeping route fallback for real obstructions and retaining lift travel.
- Added configurable faster catch-up, including regrouping while the owner is stopped, and reduced contention during destination lookups.
- Made the native teleport progress bar follow the server's configured teleport cast duration.
- Added the Protectors Hall teleport statue in Sanctum, with native map markings, English labels and connected city routes.
- Repaired Cursed Zirius's day/night forms, low-health spring cure and human reward NPC while retaining ordinary aggro and reset behavior.
- Fixed the Cursed Zirius quest handler failing to load during server startup.
- Retained the earlier companion, Stigma, pet rollback, broker, graphics recovery and Setup fixes.

Applied from recorded original normal builder outputs and the matching native client cohort and published as the cumulative 0.93.2 patch. Existing database contents, accounts, characters and local settings are retained. Companion movement, teleport timing/routes and Zirius quest acceptance remain in-game checks.

## Release-tool changes made alongside these updates

These are maintainer workflow changes, separate from the player patch contents.

- Grouped original downloads under Release/0.86 and cumulative updates under Patches/<version>.
- Kept Setup and the preparation/publication commands at the top level, with baseline data, history and logs under Maintenance.
- Added support for the organized Setup folder layout while retaining compatibility with older downloader bundles. Distributable bundles exclude the maintainer's installed game and local installation paths.
- Moved generated release files into the repository's Git-ignored `releases/` folder.
- Connected Prepare Patch and Publish Update directly to the repository's maintained distribution tools, replacing dependence on copied standalone scripts.
- Made patch preparation use the current repack version and inventory without rebuilding the full archive or automatically increasing the version.
- Kept generated archives, installed games, database data and credentials out of Git.
- Retained manual publication: committing or pushing source does not itself publish a repack release.

## 0.94 — Account, companions, interface and world textures (22 October 2026)

- Added Account Fame with Combat, Life and Legend progress, tiered bonuses and daily Kinah stipends. The Account window shows Fame, Boons, Chronicle progress and account characters.
- Added separate movable and resizable Account and Player Companions windows. Companions have a redesigned equipment panel, party Pull/Kill/CC/Clear orders and configurable Stigma builds. Automatic gear selection skips test and prop items.
- Added the switchable Aetherfall Interface, HUD profiles, action-bar layout controls and the MoveUI editor with Save/Lock, Cancel, snapping and screen guides. Includes item comparison placement, map-tooltip overlap and target/aura mover repairs.
- Updated the launcher with its Mods window and theme choices, retaining each repack's connection settings.
- Restored Noble Garden shops, Protectorate/Recruit/Commando equipment exchanges and faction Ceramium fragment exchanges. Corrected native dialog and purchase eligibility checks.
- Improved companion navigation, combat and quest handling from the current original server installation. Retained previous companion, broker, pet and Stigma repairs.
- Prepared a separate optional 2x HD world-texture pack for Poeta/Ishalgen, Verteron/Altgard, Eltnen/Morheim, Sanctum/Pandaemonium, Heiron/Beluslan, Theobomos/Brusthonin and Inggison/Gelkmaros. The core update uses standard textures and retains the other graphics improvements and engine quality defaults.
- Added **Mods & tools** inside Aetherfall Hub, with the optional HD pack download, ZIP installation and an HD/standard texture switch. Shared graphics files and the selected profile are checked before launch. Added a Make Admin account control using the native Admin rank 9, replacing the old standalone scripts. Development rollback history is excluded from both downloads; active native graphics recovery remains included.
- Repaired fresh database verification to recognize the six reserved broker sellers while still requiring no player accounts or personal characters. Existing databases are preserved.
- Includes the Setup mirror fallback repair. Published as 0.94 with the cumulative patch, Setup 0.90.1022.1 and the current complete downloader ZIP. Publication does not establish in-game acceptance of every included feature.

## 0.95 — Marketplace, interface, navigation and encounter repairs (24 October 2026)

- Redesigned Black Cloud Marketplace as a movable, resizable window matching Player Companions, with categories, item details, purchase review and history. Fixed competing window controllers and repeated browser destruction when opening the shop. Existing prices, eligibility, quantities, previews and mail delivery remain.
- Improved MoveUI selection and nested widgets, Save/Cancel behavior, feature controls, tooltip positioning, flight-meter opacity, menu anchoring and submenu interaction. Restored clear target and target-of-target numbers above their bars, including the player's DP display.
- Includes the companion PvP, rift travel, class combat priorities, spirit-following, camera-drag, quest-limit dialogue and camp-raid repairs described in the development notes above.
- Improved independent world-bot route planning, shared route and obstruction memory, continuation between prepared path sections and recovery after failed lookahead. Companion following retains its separate movement controls.
- Repaired Taloc's Hollow boss progression, timed mechanics, eggs and debuffs, and Gellmar's Protective Barrier. Added a native guard for the Mosqua cinematic's delayed connection callback.
- Restored verified quest progress, item checks and rewards in Altgard, Pandaemonium, Morheim and Beluslan, along with Abyss-entry and other affected quests. Includes underground-arena progression and Macunbello encounter repairs.
- Updated Setup and Hub to 0.90.1022.2. Download workers share temporary mirror-failure cooldowns, retain a working alternate across resumed downloads, validate archive ranges and explain failures from all attempted sources.
- Preserved local connections, settings, accounts, characters, database contents and existing mods. Standard world textures remain the full repack default; the existing optional HD pack and the installed copy's HD selection remain unchanged.

Published as 0.95 on 24 October 2026 and included in 0.96.6. Marketplace appearance, movement, quests, encounters and download behavior remain in-game acceptance checks. The optional HD pack’s later lamp-transparency repair is not part of this core update.

## 0.96.5 — World-bot population, water safety and recovery (24 October 2026)

- Added generated world-bot population balancing, level resets and timed refresh/revival. Generated bots can be rebuilt with level-appropriate gear, skills, Stigmas and quests. The new management configuration enables balancing and resets for generated global bots; normal owned alts and manual pools retain their separate rules and progress.
- Added automatic generated-bot legions using native membership, faction and capacity rules, with a configurable pool of 20 legions and 15 members each. Existing memberships are retained. Generated-character rebuilds now save their recipe changes with the rest of the character.
- Added nearby bot presence around actual player areas by reusing existing world bots. Active quests, parties and queue reservations keep control of their bots. Added a 25% city destination preference while retaining each repack's existing explicit settings.
- Added native queue demand, invitation acceptance and refill handling with normal level, ticket, cooldown and registration restrictions.
- Improved deep-water avoidance along walking paths and prepared routes. Submerged bots interrupt resting and attempt a bounded route to shore before healing or sitting.
- Changed field recovery to start below 45% HP or 15% MP. Bots use learned recovery skills, Treatment and finite real supplies before sitting as a last resort, and remain standing while useful regeneration works. Healthy bots continue their activity without repeatedly topping one another off; native costs and cooldowns remain.
- Retained the 0.95 marketplace/interface, quest and encounter fixes, Setup/Hub 0.90.1022.2 and the existing optional HD pack. Existing connections, owner population limits, profiles and database contents are retained during installation.

Included in the published 0.96.6 cumulative update; there is no separate 0.96.5 download. No manual database migration is required. Population balance, bot legions, nearby activity, water escape, recovery and performance remain in-game acceptance checks.

## 0.96.6 — Verteron, loot, monster difficulty and interface controls (24 October 2026)

- Restored Verteron quests 14110–14114, including gathering and hunt progress, Kato's appearance after Bubblegut's defeat, and Khidia's Plumis scouting route. Native level requirements, rewards, drops, spawns and existing progress remain authoritative.
- Improved bot corpse discovery, approach and looting before optional travel or another pull. Repaired solo ownership, party roll handling, retries for won items, Kinah collection and stack/bag capacity checks.
- Repaired quest-item ownership after closing a corpse. Quest items retain their eligible looters and avoid ordinary party rolls; shared quest drops prefer an eligible human before a bot, while personal bot quest progress remains supported.
- Added configurable open-world monster difficulty, initially 2x native HP and 1.25x PvE damage, with additional scaling for nearby party or alliance members including playerbots. Older monster templates are recognized correctly. Native XP stays unchanged; instances, friendly/functional NPCs, guards, summons and siege actors are excluded from this open-world scaler. Existing separate instance scaling and owner overrides are retained.
- Added Countdown Timer to MoveUI, including before a countdown appears, with saved positioning, Save/Cancel and Reset support.
- Repaired chat backdrop opacity across all seven chat tab skins and the Player Companions command-bar backdrop. Existing controls, profiles, native timer behavior and marketplace fixes remain.
- Retained each repack's interface theme, graphics recovery files, connections, player data, population settings and optional HD selection. Setup/Hub remains 0.90.1022.2.

Published on 24 October 2026 with the cumulative patch, Setup/Hub 0.90.1022.2 and the complete downloader kit. No manual database migration is required. Quest progression, loot distribution, difficulty, timer positioning and opacity remain in-game acceptance checks.

## 0.97 — Quest continuation, native windows and current original repairs (25 October 2026)

- Added **The Unbroken Field**, sixteen Verteron quests continuing The Writing on the Wall, with native dialogue, rewards, quest tracking and completion pages. Added native quest-object loot for the seal, swamp root, stone, Tursin cargo and unclaimed shipment while retaining their existing quest uses.
- Updated nearby party/alliance monster scaling to refresh visible HP directly before combat within 100 metres, including bot-only groups, while preserving native XP and separate instance rules.
- Included the current original's quest dialogue corrections and independent server-status endpoint. The standalone website remains a separate installation.
- Repaired native window identity and inventory layout alongside custom movement and opacity controls. First-open windows now receive saved positions and opacity before appearing, preventing the initial opaque flash and position jump. The owner reported no problems in the final Direct3D 9 test; the first-open repair was also confirmed during Vulkan testing.
- Included current quest-target markers and hover progress, confirmation/background repairs, companion-window text sizing and selected-boon Account details. Refreshed companion-menu artwork indexing and launcher checks so theme changes retain the matching artwork index.
- Preserved portable connections, server overrides, interface profiles, renderer/camera settings, database contents and optional HD selection. Updated active graphics recovery metadata and custom-quest portability together with the affected assets. Setup/Hub remains 0.90.1022.2.

Published on 25 October 2026 as a cumulative update from 0.86. No manual database
migration is required. Original-client UI acceptance does not replace gameplay
acceptance of the updated repack, quests or scaling.

## 0.98.3 — Quit-countdown crash repair (26 October 2026)

- Included the original's native 1.2.71 repair for a crash when the quit countdown completes after UI windows or their widgets have been retired/recreated. Invalid mover references now defer to native layout safely.
- Retained the shared tooltip initialization repair, Lua 1.2.70, package signatures, profiles, bar positions and graphics/cursor recovery. No profile or database reset is required.

Published on 26 October 2026. The normal source-built repair was installed and checked in the original before this repack update. Owner acceptance remains the normal quit countdown, relog and UI/tooltips in game.

## 0.98.2 — Shared tooltip repair from the original build (26 October 2026)

- Synced the new-character tooltip repair from the maintained source and the original development installation. Both clients now use the same normal native/Lua Core 1.2.70 output and signing cohort.
- Preserved existing bar positions, profile choices, interface artwork and graphics/cursor recovery. No profile, character or database reset is required.

Included in published 0.98.3; no separate release download. Original installation and compatibility checks completed before repack transfer. Live first-use hovering remains the owner's acceptance check.

## 0.98.1 — Tooltip anchoring on new accounts (26 October 2026)

- Fixed tooltip anchoring initialization for new characters. World and UI tooltips now receive the selected Cursor, Mover, Native or Hidden policy from first use, without depending on previously opened tooltip windows.
- Preserved separate world/UI choices, bar positions, saved layouts, opacity, item-comparison spacing and map tooltip grouping. No account, character or profile reset is required.
- Includes native/Lua Core 1.2.70 and its verified signing cohort. The original client, server and maintained source remain unchanged.

Included in published 0.98.3; no separate release download. First-use behavior passes focused native/Lua checks; live hover acceptance remains with the owner. The earlier 0.98 saved-setting correction did not resolve this initialization dependency.

## 0.98 — Complete repack parity and recovery repair (26 October 2026)

- Restored the original's ChatServer support for bot Find Group channel announcements.
- Included both Aetherfall Boons pack tables, removing the old retail pack effects/restriction declarations, and restored the target DP-bar texture.
- Matched the original GameServer database connection limit of 20 while preserving each repack's connection credentials and addresses.
- Corrected the local repack's saved world-tooltip setting from Mover to Cursor so hovering follows the mouse, matching the original. Both alternating saved slots are corrected; unrelated profile settings and layouts remain intact. New profiles already default to Cursor.
- Repaired delivery inventories and generated-state handling so normal shader-cache regeneration, guild-emblem caching and interface/quest state do not appear as missing or damaged immutable payloads.
- Removed obsolete environment-package tooling from the distribution and corrected legacy world-art rollback declarations; current graphics/cursor recovery and installed artwork remain intact.
- Prepared optional HD textures 2026.10.26.1 with the 33-lamp, 313-mip transparency repair. Existing HD selections remain supported during upgrade; standard world textures stay optional and unchanged.
- Preserved accounts, characters, database contents, profiles, renderer/camera settings and the newer repack cash-shop catalogue. Setup/Hub remains 0.90.1022.2.

The parity repairs are included in published 0.98.3; no separate 0.98 release download. The optional HD package was prepared separately. No manual SQL migration is required. Repack gameplay and visual acceptance remain with the owner.

## Updating an existing repack

Close Aion and all three game servers normally. Keep the repack database running,
open Aetherfall Setup and choose **Update Repack**. Start the servers again after
the update finishes. The 0.88 population repair creates the missing initial state
before GameServer starts. The published 0.92.1 broker repair also adds missing
reserved sellers before startup.

Patches are cumulative from 0.86, so existing users do not need to download the
full repack again. Setup offers the latest published patch; preparing a patch
locally does not make it available to other users. The original 0.86 archive
remains the base download.

Development continues on terrain routes, quests and encounters; the included
repairs do not mean every route or dungeon is finished.
