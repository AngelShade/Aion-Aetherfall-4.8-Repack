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

Maintain the full portable repack folder separately from the original0.86 archive.
After an installed release is copied into that folder, build_patch.py compares
its FILES.json to baseline-files-0.86.json and packages only cumulative changes.
The local latest-update.json includes the patch and Setup EXE hashes/asset URLs.
The release folder Publish Update.cmd runs publish_update.py --prepared: no
whole-package rescan, version bump or full archive rebuild. It validates the
prepared inventory/ZIP and uploads through the existing release/channel flow.
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

The owner's workspace now lives at `C:/Git/aion-server/releases`. Its entry
points call this repository's `tools/distribution/publish_update.py` directly,
so changes to the maintained scripts apply on the next invocation. The former
standalone copies are historical only. Generated release files and the owner's
installed game are ignored by Git; source and distribution tools stay in the
existing repository. No background watcher or automatic publication is used.

After the normal build/install and full repack refresh, run `releases/Prepare
Patch.cmd` to prepare the current version, or `releases/Publish Update.cmd` when
ready to publish it. Commits and pushes do not create releases. The original
0.86 archive stays unchanged and incremental patches remain cumulative.

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
