# Aion Aetherfall 4.8 Repack — Changelog

## Local preparation — Hub automatic updates (18 October 2026)

- Update now uses the saved online channel, including the client's update
  settings, or the standard Aetherfall channel when none is configured.
- Download and apply updates automatically; the file picker is reserved for
  the separate manual patch action. Connection errors remain visible.
- Installed locally and included in the prepared patch; not published.

## Local preparation — Aetherfall Hub icon (18 October 2026)

- Added an original generated Aetherfall emblem to the Hub/Setup executable,
  including Explorer and taskbar icon sizes. The aether crystal, contrasting
  wings and fractured-world base represent the repack's world and factions.
- Applied locally to the original Hub and repack copies; matching patch prepared.
  This does not publish a new game release or change gameplay.

This history includes the full initial-release changelog for 0.86, followed by
the 0.87 through 0.92.3 repack updates and original-only 0.93 navigation, in version order. Earlier development work is recorded under 0.86,
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
| 0.92.3 | Setup server shutdown and leftover console repair | Local cumulative patch prepared; not yet published |
| 0.93 | Bot navigation, companion spacing and Profile Stigma controls | Local cumulative patch prepared; not yet published |

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

Prepared locally; publication through Setup remains separate. Stopping all three running servers still needs user confirmation with the updated Setup.

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

Applied from the current original development installation to the repack. The cumulative 0.93 patch is prepared locally; publication remains separate. Existing accounts, characters, database contents and local connection settings are retained. Normal builder outputs and copied files are verified; in-game movement, Stigma controls, quest choices and CPU acceptance remain the player's checks.

## Release-tool changes made alongside these updates

These are maintainer workflow changes, separate from the player patch contents.

- Grouped original downloads under Release/0.86 and cumulative updates under Patches/<version>.
- Kept Setup and the preparation/publication commands at the top level, with baseline data, history and logs under Maintenance.
- Added support for the organized Setup folder layout while retaining compatibility with older downloader bundles. Distributable bundles exclude the maintainer's installed game and local installation paths.
- Moved the local release workspace to C:/Git/aion-server/releases.
- Connected Prepare Patch and Publish Update directly to the repository's maintained distribution tools, replacing dependence on copied standalone scripts.
- Made patch preparation use the current repack version and inventory without rebuilding the full archive or automatically increasing the version.
- Kept generated archives, installed games, database data and credentials out of Git.
- Retained manual publication: committing or pushing source does not itself publish a repack release.

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
