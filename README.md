# Aion Aetherfall 4.8 Repack Source

<p align="center">
  <img src="client-mods/login-branding/assets/aetherfall-subtitle.png" alt="Aetherfall" width="760">
</p>

<p align="center">
  <a href="https://github.com/AngelShade/Aion-Aetherfall-4.8-Repack/releases/latest/download/Aetherfall-Downloader.zip"><img src="https://img.shields.io/badge/Download-Current_Aetherfall_Setup_Hub-2563eb?style=for-the-badge&logo=windows" alt="Download Aetherfall Setup Hub"></a>
  <a href="https://github.com/AngelShade/Aion-Aetherfall-4.8-Repack/releases/tag/v0.94"><img src="https://img.shields.io/badge/Latest_Release-v0.94-eab308?style=for-the-badge" alt="Latest Release v0.94"></a>
  <a href="https://mega.nz/file/hJt0hYTC#fr0RDjsLl4CriZ5dw8MljBhrlP6nD4oYkaErD2iW-vQ"><img src="https://img.shields.io/badge/MEGA_Mirror-Base_Repack_0.86_(36.35_GB)-d9272e?style=for-the-badge" alt="MEGA Mirror"></a>
  <a href="https://drive.google.com/file/d/1kzr5xNIezRPlFHVDQrV1P6B7nNScS_R2/view?usp=sharing"><img src="https://img.shields.io/badge/Google_Drive-Base_Repack_0.86_(36.35_GB)-16a34a?style=for-the-badge&logo=googledrive" alt="Google Drive Mirror"></a>
  <a href="https://discord.com/invite/cpRYCRsSnR"><img src="https://img.shields.io/badge/Discord-Join_Community-5865F2?style=for-the-badge&logo=discord&logoColor=white" alt="Discord"></a>
</p>

**Aion 4.8 NA server source, client modifications, and portable repack tools — featuring party companions, shared quest progress, world playerbots, dungeon repairs and custom client systems.**

Aetherfall lets you build a party from your own alternate characters or temporary companions. Take them through quests and dungeons, manage their equipment and combat behavior, and keep your alts caught up with your main. Independent playerbots also quest, fight and visit towns around the world.

**Published repack: [0.94](https://github.com/AngelShade/Aion-Aetherfall-4.8-Repack/releases/tag/v0.94), released 22 October 2026.** It includes Account Fame, native Account and Player Companions windows, the Aetherfall Interface, launcher Mods controls and the current downloader.

The `4.8` branch also contains newer companion combat, faction PvP, rift travel and client repairs. See the [full changelog](CHANGELOG.md) for published releases through `0.94` and the separate development updates. A source commit does not update a repack installation or publish a new release.

The Windows repack brings the client, GameServer, LoginServer, ChatServer, Java 25 and MariaDB together in one portable folder. The server source, client modifications and distribution tools are maintained here on branch `4.8`.

Local server installations (`target-deploy`), installation inventories, recovery receipts,
and private property overrides are excluded from Git. Keep database passwords in the
ignored `config/mygs.properties`, `config/myls.properties`, and `config/mycs.properties`
files of each installation. The development database stop helper reads an optional
ignored `.aion-dev/mariadb-admin.ini` with a MariaDB `[client]` section; otherwise it
prompts for a password. Never commit that file, environment files, or private keys.

## What's included

This overview covers the current `4.8` source. For the exact contents of a downloaded repack, check its [release notes](https://github.com/AngelShade/Aion-Aetherfall-4.8-Repack/releases).

| Feature | What you get |
| --- | --- |
| Party companions | Bring your own alts or recruit temporary companions. Use the movable, resizable Player Companions window to manage equipment, combat roles, Stigma builds and party orders. |
| Shared quest completion | With **Party completion sync** enabled, your owned alts in the party catch up on quests your main has completed and receive future completions too, with the quest rewards. |
| World playerbots | Independent characters that quest, fight, gather, craft and use town services, with saved identities and progress, configurable faction balance and player-area population. Empty-map activity is configurable; rift activity can keep the opposing homeland awake. |
| Faction PvP & rifts | Configurable fights with enemy players, bots and pets under native combat rules. Independent bots can travel through real open rifts; companions defend their party and assist attacks while respecting Passive orders. |
| Account Fame | Combat, Life and Legend progress, tiered Boons, Chronicle goals and daily Kinah stipends, with account characters shown in the Account window. |
| Dungeon and campaign repairs | Work on Theobomos Lab and Adma Stronghold includes quest chains, scripted encounters, boss behavior, loot and dungeon exits. |
| Repeat dungeon entry | Entry limits are disabled by default. Level, quest, party and instance requirements still apply. |
| Central Market & Cash Shop | Buy and sell items through the in-game player marketplace with 3,000 simulated traders, plus the integrated in-game Cash Shop. |
| Wardrobe | Customize your character's appearance through the in-game Wardrobe. |
| Season Pass | An in-game progression and reward interface. |
| Starting-area Journey | Poeta and Ishalgen journey options (Level 1 story or Level 10 Daeva Ascension skip) for both main characters and companions. |
| Inventory and warehouse | Expanded 288-slot storage with search controls for finding your items. |
| Aetherfall Interface | Switchable HUD profiles, action-bar layouts and a MoveUI editor with movable windows, snapping, screen guides, Save/Lock and Cancel. |
| Multiclient & custom launcher | Graphical launcher with Mods and theme controls, DXVK (Vulkan) / D3D9 switcher, seamless startup curtain, and concurrent multi-client support across separate accounts. |
| Setup & Server Control Hub | Native `Aetherfall Setup.exe` / `Aetherfall Hub.exe` with resumable MEGA + Google Drive + BitTorrent downloads, 1-click delta updates, combined or staged server startup, and graceful per-service (`GameServer`, `LoginServer`, `ChatServer`, `Database`) shutdown. |
| Optional HD world textures | Standard textures remain the default. Hub's **Mods & tools** provides optional 2x HD world textures and an HD/standard switch. |
| Client improvements | Speech bubbles, remembered login, extra graphics controls, native menu shortcuts and Aetherfall login artwork and news. |

The extra services are available through **Additional Functions** in the client. Companion controls are also available through the playerbot interface.

### Recent development on `4.8`

- Companion Pull, Kill and crowd-control orders, safer tank threat skills, and class-specific priorities for Ranger, Gunner, Songweaver and Aethertech.
- Configurable faction PvP, faction-correct world-bot placement and physical rift travel with saved rest periods.
- Better companion spacing on stairs and slopes, separate world-bot party formations, blocked-route learning and bot-owned spirit following.
- Camera-drag and interface-timer repairs, correct quest-limit dialog handling, and reduced Central Market and web-window contention.

These are development updates recorded after the published `0.94` package. Builds and focused offline checks have passed; gameplay acceptance remains ongoing. See the [development changelog](CHANGELOG.md#development-updates--companions-pvp-and-client-repairs-22-october-2026) for details and validation limits.

### How quest sharing works

Put your owned alts in your party and enable **Party completion sync**. They catch up on your main's completed quests, then share new completions while they remain in the party. Catch-up is processed gradually rather than paying every past quest at once.

Rewards use the quest's normal reward system: XP, Kinah, items and other rewards where the quest provides them. Item choices account for the companion's class. Rejoining the party does not pay the same completion again, and repeatable quests retain their completion limits. Unrelated players and independent world bots do not inherit your quest history.

## Download

The full playable base repack (**Release 0.86** — `36.35 GiB` packed / `197,739` files) is a separate download from this repository's source code. Install it with the current downloader, then use **Update Repack** to apply the latest cumulative patch. Existing installations can update directly from `0.86` or any later release; intermediate patches are not required. A complete package contains `Client`, `Server`, `Control` and the launchers described below.

The published `0.94` patch is about **639 MB** and the current downloader ZIP is about **8.65 MB**. Patch sizes vary as client assets and features change. Downloading this repository's source ZIP does not provide the playable client or an installed server.

| Download Option | Link | Details |
| --- | --- | --- |
| **1. Aetherfall Setup & Auto-Updater (Recommended)** | [**Download the current `Aetherfall-Downloader.zip`**](https://github.com/AngelShade/Aion-Aetherfall-4.8-Repack/releases/latest/download/Aetherfall-Downloader.zip) | Current Setup with MEGA + Google Drive fallback and resume, SHA-256 verification, extraction/download tools and cumulative updates. The compatible base archive remains `0.86`. |
| **2. MEGA Direct Mirror** | [**Download `Aetherfall-0.86.7z` (MEGA)**](https://mega.nz/file/hJt0hYTC#fr0RDjsLl4CriZ5dw8MljBhrlP6nD4oYkaErD2iW-vQ) | `36.35 GiB` single base archive (`SHA-256: 7b5599837887a49d2b2bebb2ef73f4a9435b42b9686da62aec6354f9954af885`). |
| **3. Google Drive Direct Mirror** | [**Download `Aetherfall-0.86.7z` (Google Drive)**](https://drive.google.com/file/d/1kzr5xNIezRPlFHVDQrV1P6B7nNScS_R2/view?usp=sharing) | `36.35 GiB` single base archive (`SHA-256: 7b5599837887a49d2b2bebb2ef73f4a9435b42b9686da62aec6354f9954af885`). |
| **4. BitTorrent P2P** | [**Download `Aetherfall-0.86.torrent`**](https://github.com/AngelShade/Aion-Aetherfall-4.8-Repack/releases/download/v0.86/Aetherfall-0.86.torrent) | BitTorrent Swarm (`infoHash: ac60eed8cc0438ebd3e8f237fa3cf27700ce740f`). |
| **5. GitHub Releases & Cumulative Patches** | [**View Releases (`v0.86` – `v0.94`)**](https://github.com/AngelShade/Aion-Aetherfall-4.8-Repack/releases) | Base release assets, cumulative patches (`Aetherfall-Patch-0.94.zip`), current Setup/downloader and checksums. |
| **Community Discord** | [**Join the Aetherfall Discord**](https://discord.com/invite/cpRYCRsSnR) | Updates, support, playerbot tips and bug reports. |

## Start the repack

You need 64-bit Windows, a suitable graphics driver and the complete repack folder. Java and MariaDB are bundled; no database Windows service is required. GameServer memory is configurable; leave additional memory available for the client, database and Windows.

### Option A: Using `Aetherfall Setup.exe` / `Aetherfall Hub.exe` (All-in-One Control Hub)

1. Extract **`Aetherfall-Downloader.zip`** to a folder of your choice and run **`Aetherfall Setup.exe`**.
2. Click **Download & Install** (if you pause or close the window midway, reopening `Aetherfall Setup.exe` and clicking **Resume** continues from the exact chunk where it stopped). Use **Update Repack** on the **Install & Update** tab to apply the latest cumulative patch. For an older downloader, click **Update Repack** before starting or resuming the base download to update Setup first. Keep its existing settings and Downloads folder to retain saved progress.
3. Open the **Server Control** tab to start and stop services safely:
   - **Start**: Click **Start database & servers** to launch MariaDB and all three server consoles in order (or use **Database only** / **Servers only** individually).
   - **Play**: Once GameServer finishes loading, click **PLAY** to open the **Aetherfall Launcher**.
   - **Stop**: Use **Stop GameServer**, **Stop LoginServer** and **Stop ChatServer** for graceful `CTRL+C` shutdowns with automatic console confirmation, then click **Stop database** last.

### Option B: Using the numbered `.cmd` scripts inside the repack folder

1. Extract the whole package. Keep `Client`, `Server` and `Control` together.
2. Run **`1 - Start Database.cmd`**. On first launch it creates the database and imports the schemas. Wait for **Database ready**.
3. Run **`2 - Start Servers.cmd`**. Wait for GameServer startup to finish.
4. Run **`3 - Start Client.cmd`**. Enter a new account name and password; LoginServer creates a normal player account on first login.
5. When finished, close the game and all three server components normally, then run **`4 - Stop Database.cmd`**.

The package uses its own database directory at `Server/database/data`, with loopback port `33306`. It starts with no accounts, characters or gameplay history from the development server. Package database credentials are in `Server/database/credentials.json`.

Move the entire repack folder only while its components are closed. Use its own client launcher so the graphics and recovery paths follow the folder.

## Settings

Inside the repack, server settings are under `Server/game-server/config`:

| File | Purpose |
| --- | --- |
| `playerbots/playerbots.properties` | Population, companion and bot budget settings. |
| `playerbots/features.properties` | Quest automation, crafting, gathering, flight and world activity switches. |
| `playerbots/pvp.properties` | Faction fights, pursuit limits, rift trips and world-bot faction placement in current source builds. |
| `playerbots/navigation.properties` | Navigation work budgets, mesh memory, route caches and blocked-route learning. |
| `main/instance.properties` | Instance entry limits and other dungeon settings. |
| `mygs.properties` | Owner overrides, which take precedence over the other GameServer property files. |

Restart GameServer after changing these settings. Disabling bot automation does not erase saved characters, items or quest progress.

The bundled connection settings are for local play. Hosting for other computers also requires configuring the server addresses, client connection and in-game service URLs.

### Updating an existing repack

Close the client and all three game servers normally. Keep the repack database running, open Setup's **Install & Update** tab and choose **Update Repack**. Start the servers again after the update finishes. Use the current downloader ZIP if an old Setup cannot update itself, keeping the existing settings and Downloads folder for saved progress.

Patches are cumulative and preserve the repack's database contents and local connection settings. They do not run arbitrary SQL migrations; the source-build schema notes below apply when bringing an older database to the newer server source yourself.

### Building for an older database

The October 17 upstream merge changes server dependencies and the GameServer database schema. Keep the GameServer, LoginServer, ChatServer and their packaged libraries from the same build together. A JAR-only replacement over the older release is not sufficient.

For an existing Aetherfall 076 database, back it up and stop the servers normally before applying the following schema changes to the GameServer database. Fresh databases created from the updated `game-server/sql/aion_gs.sql` already contain them:

```sql
ALTER TABLE players ADD COLUMN pvp_exp bigint NOT NULL DEFAULT '0' AFTER reposte_energy;
ALTER TABLE player_recipes ADD COLUMN production_limit tinyint NOT NULL DEFAULT '0' AFTER recipe_id;
ALTER TABLE player_appearance
  RENAME COLUMN jaw_height TO chin_height,
  RENAME COLUMN chin_jut TO cheek_bones;
```

These changes retain existing rows. Apply them once, only if the database still has the older schema. The general upstream `update.sql` also contains older changes that may already be installed; do not rerun it indiscriminately. Merging this repository does not update an installed repack or run database commands.

## Build the `4.8` source

| Directory | Contents |
| --- | --- |
| `game-server` | Gameplay code, configuration, quests, encounters and static data. |
| `login-server` | Accounts and login connections. |
| `chat-server` | Chat service. |
| `commons` | Shared server code. |
| `client-mods` | Client modification sources, assets and packaging tools. |
| `tools` | Build and delivery tools. |
| `docs` | Public feature notes and configuration guidance. |
| `third-party/playerbots` | Pinned Playerbots references and license notices. |

To build the server components on Windows, install **JDK 25**, **Maven**, **Python 3** and **Git**, with their commands available on your PATH. From PowerShell:

```powershell
git clone --branch 4.8 --single-branch https://github.com/AngelShade/Aion-Aetherfall-4.8-Repack.git
cd Aion-Aetherfall-4.8-Repack
$env:AION_DEV_ROOT = Join-Path $env:USERPROFILE 'Aetherfall-build'
powershell -NoProfile -File .\tools\build-components.ps1 -Modules 'game-server,login-server,chat-server' -Online
```

This builds all three server components and their required shared module, then writes complete packages and dependencies under the external build folder. It does not start the servers or replace an installation. Maven tests are skipped by default. Client modifications have their own instructions under `client-mods`.

The builder uses `AION_DEV_ROOT` when set, then the local, Git-ignored
`.aion-dev/workspace-root.txt` setting, and otherwise `Aetherfall-build` under your
user folder. An explicit `-OutputRoot` must be inside that external workspace.

The source checkout does not include an installed client, a populated database or local server installations. Keep generated builds, logs, backups, database contents, private configuration and owner workflow records outside version control. See [Playerbots](docs/PLAYERBOTS.md), [saved bot parties](docs/PLAYERBOTS_SAVED_PARTIES.md) and [class strategies](docs/PLAYERBOTS_CLASS_STRATEGIES.md) for public gameplay and configuration details.

## Reporting a problem

Include the repack release, what you were doing, where it happened and what you expected. For quest or dungeon problems, include the quest name, NPC or boss, and the step that failed. Attach the relevant server log lines and a screenshot when useful.

Development is ongoing. Some movement routes, quests and encounters still need work; the included repairs do not mean every dungeon or route is finished.

## Credits and license

Aetherfall builds on the [Beyond Aion server](https://github.com/beyond-aion/aion-server) and the earlier Aion emulator projects in its history. The repository retains that history and the original contributors' work. Playerbot development also uses the references maintained under `third-party/playerbots`.

The server code is licensed under [GNU GPL v3](LICENSE). Client modifications and third-party components retain their applicable licenses and notices.
