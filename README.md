# Aion Aetherfall 4.8 Repack

<p align="center">
  <img src="client-mods/login-branding/assets/aetherfall-subtitle.png" alt="Aetherfall" width="760">
</p>

<p align="center">
  <a href="https://github.com/AngelShade/Aion-Aetherfall-4.8-Repack/releases/download/v0.86/Aetherfall-Downloader.zip"><img src="https://img.shields.io/badge/Download-Aetherfall_Downloader_v0.86_(7.7_MB)-2563eb?style=for-the-badge&logo=windows" alt="Download Aetherfall Downloader"></a>
  <a href="https://mega.nz/file/hJt0hYTC#fr0RDjsLl4CriZ5dw8MljBhrlP6nD4oYkaErD2iW-vQ"><img src="https://img.shields.io/badge/MEGA_Mirror-Full_Repack_0.86_(36.35_GB)-d9272e?style=for-the-badge" alt="MEGA Mirror"></a>
  <a href="https://drive.google.com/file/d/1kzr5xNIezRPlFHVDQrV1P6B7nNScS_R2/view?usp=sharing"><img src="https://img.shields.io/badge/Google_Drive-Full_Repack_0.86_(36.35_GB)-16a34a?style=for-the-badge&logo=googledrive" alt="Google Drive Mirror"></a>
  <a href="https://discord.com/invite/cpRYCRsSnR"><img src="https://img.shields.io/badge/Discord-Join_Community-5865F2?style=for-the-badge&logo=discord&logoColor=white" alt="Discord"></a>
</p>

**Aion 4.8 NA with party companions, shared quest progress, dungeon repairs and custom client features.**

Aetherfall lets you build a party from your own alternate characters or temporary companions. Take them through quests and dungeons, manage their equipment and combat behavior, and keep your alts caught up with your main. Independent playerbots also quest, fight and visit towns around the world.

See the [full changelog](CHANGELOG.md) for the initial 0.86 release and subsequent updates.

The Windows repack brings the client, GameServer, LoginServer, ChatServer, Java and MariaDB together in one portable folder. The source and client modifications are maintained here.

## What's included

| Feature | What you get |
| --- | --- |
| Party companions | Bring your own alts or recruit temporary companions. Manage their combat behavior, skills and equipment through the companion interface. |
| Shared quest completion | With **Party completion sync** enabled, your owned alts in the party catch up on quests your main has completed and receive future completions too, with the quest rewards. |
| World playerbots | Independent characters that quest, fight, gather, craft and use town services. By default, their autonomous activity pauses on maps and instances without human players. |
| Dungeon and campaign repairs | Work on Theobomos Lab and Adma Stronghold includes quest chains, scripted encounters, boss behavior, loot and dungeon exits. |
| Repeat dungeon entry | Entry limits are disabled by default. Level, quest, party and instance requirements still apply. |
| Central Market | Buy and sell items through the in-game player marketplace. |
| Wardrobe | Customize your character's appearance through the in-game Wardrobe. |
| Season Pass | An in-game progression and reward interface. |
| Starting-area Journey | Poeta and Ishalgen journey options for starting characters. |
| Inventory and warehouse | Expanded storage with search controls for finding your items. |
| Client improvements | Speech bubbles, remembered login, extra graphics controls, native menu shortcuts and Aetherfall login artwork and news. |

The extra services are available through **Additional Functions** in the client. Companion controls are also available through the playerbot interface.

### How quest sharing works

Put your owned alts in your party and enable **Party completion sync**. They catch up on your main's completed quests, then share new completions while they remain in the party. Catch-up is processed gradually rather than paying every past quest at once.

Rewards use the quest's normal reward system: XP, Kinah, items and other rewards where the quest provides them. Item choices account for the companion's class. Rejoining the party does not pay the same completion again, and repeatable quests retain their completion limits. Unrelated players and independent world bots do not inherit your quest history.

## Download

The full playable repack (**Release 0.86** — `36.35 GB` packed / `197,739` files) is a separate download from this repository's source code. A complete package contains `Client`, `Server`, `Control` and the launchers described below.

| Download Option | Link | Details |
| --- | --- | --- |
| **1. Aetherfall Setup & Auto-Updater (Recommended)** | [**Download `Aetherfall-Downloader.zip`**](https://github.com/AngelShade/Aion-Aetherfall-4.8-Repack/releases/download/v0.86/Aetherfall-Downloader.zip) | `7.7 MB` — Native GUI setup hub (`Aetherfall Setup.exe`) with multi-stream MEGA + Google Drive resume, SHA-256 verification, auto-extraction, 1-click server/client control, and delta update patching. |
| **2. MEGA Direct Mirror** | [**Download `Aetherfall-0.86.7z` (MEGA)**](https://mega.nz/file/hJt0hYTC#fr0RDjsLl4CriZ5dw8MljBhrlP6nD4oYkaErD2iW-vQ) | `36.35 GB` single archive (`SHA-256: 7b5599837887a49d2b2bebb2ef73f4a9435b42b9686da62aec6354f9954af885`). |
| **3. Google Drive Direct Mirror** | [**Download `Aetherfall-0.86.7z` (Google Drive)**](https://drive.google.com/file/d/1kzr5xNIezRPlFHVDQrV1P6B7nNScS_R2/view?usp=sharing) | `36.35 GB` single archive (`SHA-256: 7b5599837887a49d2b2bebb2ef73f4a9435b42b9686da62aec6354f9954af885`). |
| **4. BitTorrent P2P** | [**Download `Aetherfall-0.86.torrent`**](https://github.com/AngelShade/Aion-Aetherfall-4.8-Repack/releases/download/v0.86/Aetherfall-0.86.torrent) | BitTorrent Swarm (`infoHash: ac60eed8cc0438ebd3e8f237fa3cf27700ce740f`). |
| **5. GitHub Releases Page** | [**View Release `v0.86`**](https://github.com/AngelShade/Aion-Aetherfall-4.8-Repack/releases/tag/v0.86) | Release notes, checksums and downloader assets. |
| **Community Discord** | [**Join the Aetherfall Discord**](https://discord.com/invite/cpRYCRsSnR) | Updates, support, playerbot tips and bug reports. |

## Start the repack

You need 64-bit Windows, a suitable graphics driver and the complete repack folder. Java and MariaDB are bundled; no database Windows service is required. The GameServer allows up to 4 GiB of heap, so leave additional memory available for the client, database and Windows.

### Option A: Using `Aetherfall Setup.exe` (All-in-One Control Hub)
1. Extract **`Aetherfall-Downloader.zip`** to a folder of your choice and run **`Aetherfall Setup.exe`**.
2. Click **Download & Install** (if you pause or close the window midway, reopening `Aetherfall Setup.exe` and clicking **Resume** continues from the exact chunk where it stopped).
3. Once installed, use the **Repack Control Hub** buttons inside `Aetherfall Setup.exe` to **Start Database**, **Start Servers**, **Launch Aion**, and **Check for Updates**.

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
| `main/instance.properties` | Instance entry limits and other dungeon settings. |
| `mygs.properties` | Owner overrides, which take precedence over the other GameServer property files. |

Restart GameServer after changing these settings. Disabling bot automation does not erase saved characters, items or quest progress.

The bundled connection settings are for local play. Hosting for other computers also requires configuring the server addresses, client connection and in-game service URLs.

### Updating an existing server

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

## Source and development

| Directory | Contents |
| --- | --- |
| `game-server` | Gameplay code, configuration, quests, encounters and static data. |
| `login-server` | Accounts and login connections. |
| `chat-server` | Chat service. |
| `commons` | Shared server code. |
| `client-mods` | Client modification sources, assets and packaging tools. |
| `tools` | Build and delivery tools. |
| `docs` | Feature notes and installed-mod records. |

To build the server components on Windows, install **JDK 25**, **Maven**, **Python 3** and **Git**, with their commands available on your PATH. From PowerShell:

```powershell
git clone https://github.com/AngelShade/Aion-Aetherfall-4.8-Repack.git
cd Aion-Aetherfall-4.8-Repack
$env:AION_DEV_ROOT = Join-Path $env:USERPROFILE 'Aetherfall-build'
powershell -NoProfile -File .\tools\build-components.ps1 -Modules 'game-server,login-server,chat-server' -Online
```

This runs Maven and writes the component packages under the external build folder. It does not start the servers or replace an installed repack. Client modifications have their own instructions under `client-mods`.

## Reporting a problem

Include the repack release, what you were doing, where it happened and what you expected. For quest or dungeon problems, include the quest name, NPC or boss, and the step that failed. Attach the relevant server log lines and a screenshot when useful.

Development is ongoing. Some movement routes, quests and encounters still need work; the included repairs do not mean every dungeon or route is finished.

## Credits and license

Aetherfall builds on the [Beyond Aion server](https://github.com/beyond-aion/aion-server) and the earlier Aion emulator projects in its history. The repository retains that history and the original contributors' work. Playerbot development also uses the references maintained under `third-party/playerbots`.

The server code is licensed under [GNU GPL v3](LICENSE). Client modifications and third-party components retain their applicable licenses and notices.
