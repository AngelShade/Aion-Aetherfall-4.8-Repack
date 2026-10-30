# Aetherfall repack distribution

This packages the **portable repack**, not the original playable client or the
development server. It does not upload, start seeding, run tests, or start any game
component. Keep the repack stopped and unchanged while it is packed.

`pack.py` reads the repack's existing `FILES.json`, adds the inventory itself,
and creates a store-mode 7z archive split into **1900 MiB** volumes. Store mode
avoids spending hours recompressing native game archives. It needs space for a
second copy of the packaged data on the output drive. Existing output is never
overwritten. The copy in the repack remains the input.

The archive volumes are hashed in one pass for SHA-256 and cross-file BitTorrent
v1 pieces. The output contains the volumes, torrent/magnet, checksum list, a small
`Aetherfall-Downloader.zip`, mirror publisher and a seeding command. No repeated
full-client preservation scan or extraction test is part of packing.

Example in this workspace:

```powershell
python tools/distribution/pack.py `
  --package 'D:/path/to/Aetherfall-Repack' `
  --output '<DISTRIBUTION_ROOT>' `
  --tools 'D:/path/to/staging/distribution-tools'
```

The tools directory contains the official extracted distributions:

```text
aria2/aria2-1.37.0-win-64bit-build1/aria2c.exe
aria2/aria2-1.37.0-win-64bit-build1/{COPYING,LICENSE.OpenSSL,README.mingw,AUTHORS}
7zip/x64/7za.exe
7zip/{License.txt,readme.txt}
```

7-Zip's installed `7z.exe` creates the archives; `--sevenzip` changes that path.
The downloader includes standalone `7za.exe` and aria2, licenses, source links and
recorded tool hashes. It requires Windows PowerShell 5.1, not Python. The owner
uses Python only to pack or publish mirror metadata.

Once uploaded, edit `Aetherfall-Downloader/mirrors.json`, then:

```powershell
python publish.py 'C:/path/to/distribution' --mirrors-url 'https://host/release/mirrors.json'
```

`WebSeeds` is an array of direct HTTPS folder bases **above** the torrent's root
folder. aria2 combines these with peers. `Files` maps each exact volume name to
an array of unrelated direct HTTPS URLs; that uses HTTP mode. `SharePages` is for
manual download links and is not passed to aria2. Keep the release manifest and
hashes fixed when changing URLs. New content requires a new release/torrent.

Private Google Drive home and MEGA `/fm/` URLs are not public download URLs.
Browser shares, authentication, MEGA decryption and transfer quotas are not
bypassed. No functioning public mirrors are claimed until real share/direct
links exist. With no mirrors, the downloader uses torrent peers and needs an
online seed. The packer does not silently publish an initial seed.

`publish.py` changes only outer torrent webseed metadata, retaining the info hash,
updates the local torrent checksum, emits a standalone HTTP Metalink when every
part has a URL, and rebuilds the small ZIP. A stable live mirror URL lets existing
downloaders pick up later URL changes; it cannot replace their pinned content
hashes.

The downloader checks free space for archives plus extracted data, resumes with
aria2, verifies all volume hashes, extracts through 7-Zip, checks the extracted
inventory hash, and retains parts for recovery/seeding. Existing installs are
not overwritten; only an interrupted extraction bearing this release's own
marker can be resumed. It never launches downloaded programs or game components.

References: [aria2 manual](https://aria2.github.io/manual/en/html/aria2c.html),
[BEP19 webseeds](https://www.bittorrent.org/beps/bep_0019.html),
[GitHub asset limits](https://docs.github.com/en/repositories/releasing-projects-on-github/about-releases),
[7-Zip redistribution](https://www.7-zip.org/faq.html).

## Prepared incremental releases

Every prepared update also builds `Aetherfall-Downloader.zip` with the exact
current Setup EXE, pinned base manifest/torrent, verified tools/licenses, shared
changelog and newly generated portable settings. Owner settings, Games, Downloads,
logs and profiles are never copied. Publish Update checks the ZIP/Setup hashes,
uploads the complete kit and replaces the public README's old 0.86 downloader
links with `releases/latest/download/Aetherfall-Downloader.zip`. Share this stable
link for new installations; the original 0.86 archive and historical ZIP stay
unchanged. `--prepared --no-upload` does not change public files or links.

Maintain the full portable repack folder separately from the original0.86 archive.
After an installed release is copied into that folder, build_patch.py compares
its FILES.json to baseline-files-0.86.json and packages only cumulative changes.
Updates use `Aetherfall-Patch-<version>.json` and content-addressed chunk ZIPs.
Each uncompressed chunk is at most 32 MiB; small files share bounded bundles.
The updater verifies actual installed files, reuses matching chunks at the same
offset in large files, and downloads only the required bundles. Verified bundles
are cached for retry. Each upload must be under 2 GiB, with at most 990 chunk
assets per release (leaving room for Setup, downloader and the patch manifest).
Older Hubs self-update before applying this format. Old offline ZIPs still work;
new offline updates use the JSON manifest with its chunk ZIPs alongside it.
The updater plans missing packages before transfer, excluding verified local and
cached data from its download total. Setup shows byte-based percentage, speed,
remaining size, ETA and package counts, then separate checking/preparation/
verification/backup/installation progress. Counters use 64-bit values. A versioned
progress stream is throttled to four updates per second plus explicit boundaries;
the same transport supports Setup self-update. Setup carries both the updater and
its `UpdateTransfer.cs` helper so older installations receive the matching pair.
The local latest-update.json includes the patch and Setup EXE hashes/asset URLs.
The release folder Publish Update.cmd runs publish_update.py --prepared: no
whole-package rescan, version bump or full archive rebuild. It validates the
prepared inventory, manifest and chunk assets and uploads through the existing release/channel flow.
--prepared --no-upload prepares or checks local metadata only. New Setup detection
uses the native game-server/libs layout and finds completed older installations
before considering a download. Existing users get this repair by Setup self-update
when the prepared release is published; the original base archive remains0.86.

The organized workspace keeps `Aetherfall Setup.exe`, `Prepare Patch.cmd`,
`Publish Update.cmd` and `Start Seeding.cmd` at the top level. Base downloads live
under `Release/0.86`; cumulative updates live under `Patches/<version>`. Scripts,
baseline, history and the local Git checkout live under `Maintenance`.
`Maintenance/release-layout.json` identifies the authoritative repack folder.
Prepare Patch builds or reuses its current version; Publish Update does the same
and publishes the files from that version's patch folder. Neither advances the
version or recreates the full archive. Record repack changes in `FILES.json`
through the normal repack updater before preparing a patch.

Original-to-repack syncs preserve the repack's network configuration and client
connection file. Original-only firewall restrictions and listener/port changes
must stay on the development installation; do not transfer them through default
merging. Changing a repack's connections requires a separate explicit request.

Hub updates replace patch files and do not run arbitrary SQL migrations. A new
database imports its bundled bootstrap on first database start. Existing databases
retain their contents; server startup runs only the included guarded population
and broker-seller initializers. Other schema/data changes need an explicit upgrade
step for existing users; replacing bootstrap.sql alone does not apply them.

Setup supports both a `Setup` support folder in new bundles and the existing
`Aetherfall-Downloader` folder in the owner's workspace. The owner's installed
game stays at its existing path. Public downloader ZIPs exclude installed games,
download caches, logs and local installation paths.

The owner's release workspace is configured outside the source repository.
Run `python tools/distribution/release_workspace.py` to print its location, or
add `--layout` for the active layout file. Resolution uses `AION_RELEASE_ROOT`,
then the Git-ignored `tools/distribution/release-workspace.local.json` configuration.
Without local configuration, `AION_DEV_ROOT` locates the repack parent's
`Distribution` folder or an existing legacy `distribution` workspace. Existing legacy
workspaces remain usable until migration; a missing legacy folder is never
recreated automatically. The local configuration has schema `1`, a `releaseRoot`
path and optional `legacyRoots` aliases for earlier command-line paths.

The repack parent's Prepare Patch and Publish Update entry points read the
distribution layout and call the maintained
`tools/distribution/publish_update.py` through `Maintenance/Run-Publisher.ps1`,
so source changes apply on the next invocation. Historical standalone copies
remain inactive. Generated archives, settings and retained installations stay
local; source and distribution tools stay in the existing repository.

After the normal build/install and full repack refresh, run `Prepare Patch.cmd`
beside the maintained repack folder, or `Publish Update.cmd` when ready to publish it.
Commits and pushes do not create releases. The original 0.86 archive stays
unchanged and incremental patches remain cumulative.

The layout's `repackFolder` points to the maintained game payload. Its
`setupExecutable` may reference that payload's Hub directly; `setupSupport`
locates the clean downloader inputs under Maintenance. The baseline file index
is required for cumulative patches. Old installed test copies, download caches,
past patch versions, logs and the release Git checkout are not publisher inputs.
The full base archive is needed locally only for seeding or a local base install.

## Native world-bot population initialization

Setup's Server Control tab has separate Stop GameServer, Stop LoginServer and
Stop ChatServer buttons. They select only that installed repack's bundled Java
process, request native CTRL+C shutdown, and wait for it to exit before answering
the launcher's Y/N or PAUSE prompts. A shared console containing unrelated
processes is refused. Timeouts report unfinished shutdown without killing Java;
the database and other components stay running. No component is stopped during
the installer build or delivery.

The panel groups Start and Stop actions in aligned columns. Starting everything
is the primary action; individual startup stages are underneath. Stop Database
is last and visually separate from the three server stops. A dedicated status
strip displays progress/errors; hover it to read any message that exceeds two
lines. Button names and existing server-control actions are retained.

Start stages run the installed `Control/*.ps1` scripts in order and each stage
must exit successfully before the next one starts; a failure shows the script's
own error message. Setup treats a stage as finished when its PowerShell process
exits, not when its output pipe closes. `Start-Database.ps1` launches `mysqld`
with a redirected `Start-Process`, which inherits Setup's output pipe, so the
pipe stays open for as long as the database runs. Waiting for that pipe kept
Setup busy forever and never started the servers (fixed after 0.90's layout
build). Remaining output is read for at most 1.5 seconds after the script exits.

The 0.86/0.87 fresh schema omitted the required `playerbot_population_state`
singleton. `repack_population.py` adds the native empty-import state to the fresh
GS schema/bootstrap. Its digest matches the native migration's four absent legacy
files. `Initialize-PlayerbotPopulation.ps1` runs before server startup to repair
already installed empty databases. It verifies database ownership and refuses
initialization while game servers are connected or legacy files, population
records, roster entries or native world-bot identities exist. Imported states
are retained. The repair inserts one initial state row; it does not reset or
import characters, alter settings, or disable the native repository validation.

`fix_repack_population.py` delivers this correction to the maintained fresh
repack and updates only changed inventory entries. Existing downloads receive
the cumulative 0.88 patch through the usual publication/update flow, without
replacing the original archive or requiring a new full download.

## Optional language packs

English is included in the core client. **Languages** in the repack Aetherfall Hub
owns per-component downloads, pause/resume and verified offline Install ZIP. The
original development Hub retains its server dashboard; original language selection
belongs only to its game launcher. The repack Hub and both game launchers share
themed text/voice dropdowns and the same saved choices. Dropdown
changes save immediately; page/library refreshes retain them. The game launcher
contains selection controls only. Native Korean text stays included; five
adapted text packs and the original Korean/Japanese voices are separate downloads
from `AngelShade/Aion-Aetherfall-4.8-Repack` GitHub release assets.

`client-mods/launcher/prepare_text_pack.py` builds only current native translations,
compatible-path dialogue and required font assets. Existing identical English fonts
are reused. Foreign server overrides, layouts, settings, branding and unused fonts
are excluded. `build_language_packs.py` creates the optional ZIPs and core catalog;
`deliver_language_packs.py` installs exact normal outputs without changing choices.

The core inventory excludes language Packs, Cache and Installed state. Cumulative
updates never delete previously installed optional packs. `publish_update.py
--prepared --no-upload` validates their hashes alongside the core patch; only an
explicit Publish Update uploads the ZIPs and makes their catalog URLs available.
Keep each optional asset below the publisher's asset-size guard. Source archives,
diagnostics, personal settings and download caches are not optional-pack payloads.

The prepared core builder retains player-edited mutable settings in the maintained
repack. When their bytes differ from the recorded shipped defaults, `stock_defaults.py`
recovers the exact defaults from a prior normal chunk manifest, verifying the
manifest, asset, part and full-file hashes. It packages those recorded defaults,
never the owner's saved choices. Missing verified defaults or changed immutable
files fail preparation; no owner settings are rewritten to make validation pass.


## Backward launcher and downloaded-pack compatibility

Keep the standard update-channel URL and schema-1 `setupExeUrl`, `setupExeBytes`,
`setupExeSha256` and `setupVersion` fields. Earlier Hubs use that contract to verify
and replace themselves before their legacy ZIP-only updater sees a chunked patch.
Publication checks the fields against the exact normal Setup/Hub build; missing
or mismatched launcher upgrades block preparation and publication.

The Hub consumes an earlier Hub's `.old` replacement handoff and continues its
requested update. New self-updates also pass `--continue-update`. A failed launcher
upgrade pauses the operation instead of falling through into a game update.
Fresh extraction continues into the cumulative update before declaring completion.
The fixed 0.86 archive, install path and player settings remain. That earliest
archive has no installed Hub: current Setup supplies its bootstrap. Historical
launchers without any configured self-update channel require current Setup to
select the existing installation, without downloading the full game again.

Optional ZIPs remain importable when they match trusted current or retained pack
metadata. Changed content receives a new pack version; unchanged content keeps its
version, checksum and download identity across core updates. Never bypass ZIP
checksums or replace a published archive in place.
