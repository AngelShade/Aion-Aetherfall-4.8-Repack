# PB-SCOPE-012A — native Aion database metadata checkpoint

**Installed offline on 7 October 2026.** After the user opened the database, the
guarded installer created the schema and committed 32 care/gear metadata imports.
A separate read-only native connection verified all 32 committed rows, values,
ownership and revision. GameServer and client remained off, with no startup or
attach. User gameplay acceptance PB-VAL-011 remains pending.

## Behavior and upstream mapping

The user explicitly requested replacing the file-backed care/gear adapter with
Aion's database/framework. This bounded persistence port supersedes the file retry
workaround for those two namespaces after installation; it does not redesign AI
or revisit installed class strategies.

Exact upstream sources at `037c01418b5d01506917a3db9b44fd56ac5f965c`:

- [PlayerbotRepository.cpp](https://github.com/mod-playerbots/mod-playerbots/blob/037c01418b5d01506917a3db9b44fd56ac5f965c/src/Db/PlayerbotRepository.cpp):
  Load reads persisted AI values and combat/noncombat/dead strategies by native
  bot GUID. Save collects context values/strategies and commits prepared deletes/
  inserts in one repository transaction.
- [PlayerbotsDatabase.cpp](https://github.com/mod-playerbots/mod-playerbots/blob/037c01418b5d01506917a3db9b44fd56ac5f965c/src/Db/PlayerbotsDatabase.cpp):
  prepared SELECT/DELETE/INSERT target `playerbots_db_store(guid,key,value)`.

Both downloaded files match the exact Git blob identities in the already pinned
untruncated tree (Repository `968c2c983813f25961ba58ae9db4ab446eef723c`, Database
`e574fec8fda801e101727f2717051bff667cf5b4`). These are implementation-source
continuations, not a restarted inventory/audit. Evidence and source bytes are in
the external development workspace's `diagnostics/playerbots-metadata-20261007`.

Aion mapping: **PlayerBotMetadataDAO**, using native `DatabaseFactory`, prepared
statements and the existing companion checkpoint transaction. The new InnoDB
`playerbot_metadata` table has native player/account identity, care/gear sections,
versioned value snapshots, timestamp and a foreign key to `players(id)`. No WoW
GUIDs, session packets, databases, strategy names or ownership model are copied.
Care/budget/provenance fields are Aion adapter state, not claims of identical WoW
features. Upstream's persistent context purpose is preserved.

## Cache, durability and failure behavior

- Care/gear load queries their DAO once per cached character/section. An existing
  DB row wins over retained legacy files. Invalid ownership/values, missing schema
  or DB failures refuse recruitment/loading; they never quietly apply defaults.
- `QuestSync.State.save` and `GearPolicy.State.save` now serialize into the in-memory
  cache only. Repeated identical changes coalesce. They perform **no filesystem
  write/replace and no SQL** in the AI/settings action path.
- `PlayerBotPersistence.save` writes dirty metadata alongside native inventory,
  life/skills/quests/cooldowns/effects/progress and commits once. Cache snapshots
  become clean only after commit; queued newer values remain dirty. Owner checks
  and optimistic revisions refuse stale/foreign overwrites.
- SQL failure rolls back the checkpoint, restores native inventory dirty marking,
  and retains pending metadata for the existing periodic retry. This failure
  belongs to the checkpoint/save path, not AI `failed()`/dismissal logic. The
  normal checkpoint cadence is `max(30, PlayerBotConfig.SAVE_SECONDS)` seconds;
  a queue acknowledgement is not an immediate durable-save claim. Dismissal/
  logout/saved-bot checkpoints use that same boundary.
- Dismissal retains the cache/lease through failed saves. Only a successful clean
  checkpoint releases cached metadata. The existing state cleanup during closing
  does not erase the pending repository values.
- **Necessary integration:** pre-trade bot inventory checkpoints also store/commit
  metadata, and supply provenance recording marks the cube dirty. Thus generated
  item protection accompanies saved inventory even when the native supply path
  uses `onLoadHandler`. Grants stay protected in memory immediately; their first
  durable native checkpoint includes the protection snapshot.

No level, equipment, Stigma, skill or class replacement was added. Owned-alt
restrictions, explicit donation behavior, quest witnesses, consent, protected IDs,
spending preferences, budgets and native transaction custody remain.

## Migration and guarded installation

`game-server/sql/playerbot_metadata.sql` creates only the metadata table.
`PlayerBotMetadataMigration` uses native configuration processing/DatabaseFactory
without initializing GameServer. It validates file and character/account identity,
then imports all care/gear values in one transaction. Re-running it preserves
existing database rows. Legacy files are retained byte-for-byte as recovery input;
no rename or delete is required. All **32 actual legacy care/gear files** parse and
validate offline. Native DB ownership checks, import and read-only committed-row verification now pass.

Installer `client-mods/playerbots/install_metadata_offline.py`:

1. Requires successful process inspection proving server/client stopped and exact
   base/payload/tool/schema hashes matching the reviewed package.
2. Requires a read-only DB/owner preflight before any deployment replacement.
3. Archives verified baseline/settings outside the installation, replaces the
   bounded package while stopped, then commits the import. Pre-commit failure
   restores the previous installation. An uncertain commit is explicitly held
   for DB review rather than assumed rolled back.
4. Publishes a successful external receipt only after import and installed hashes
   agree. It never starts a service or modifies MariaDB's startup mode.

Package:
`D:/Proiecte/Project Restructure/Aion Development Workspace/staging/target/playerbots-metadata-20261007/package`.
It transplants **eight methods in six existing definitions**, adds six DAO/cache
definitions and preserves **141 other cumulative entries byte-identically**.
Original schemas/synthetics, client files, command/media, launcher and base JAR
are retained. This preserves prior installed `081522-429924` and every mod; the
appearance remains staged/source-only. Follow recovery receipt
`playerbots-recruitment-20261007-170333-991220` was rejected and restored in
`171732-465446`; the complete metadata baseline survives. Generic navigation is
tracked separately as [PB-REPAIR-NAV-002](PLAYERBOTS_NAVIGATION_20261007.md).

External receipt discovery was added to the stager/inventory/audit tools so future
updates can preserve externally archived installations. This is a necessary
artifact-containment prerequisite; it does not execute the pending bulk migration.

## Verification and PB-VAL-011

Full offline Commons/GameServer compilation passes (2,412 GameServer sources).
**35 production checks** pass against source and the effective staged package:
native DAO owner/revision guards, unavailable database load, strict legacy import,
DB-over-file precedence, no I/O from actual care/gear serializers, deterministic
dirty coalescing, failed-save retention, clean release and newer snapshot retention.
These production checks use private files and recording JDBC, with no actors
registered in the world, native casts/movement or ID allocation/release. The
authorized installer additionally validated native DB ownership, created the schema
and imported all 32 legacy snapshots in one transaction. A new read-only connection
verified committed values exactly match legacy files at revision 1.

Effective installed bytecode checks verify both inventory checkpoints write metadata
before commit, clean it afterward and restore inventory dirty state on failure.
All unselected methods/entries and schema are preserved. The 51 previous pinned
hashes and two new source blobs pass. All 16 installed-mod checks/31 client hashes
pass after install; all 93 settings/media files remain unchanged. Inventory now
records 70 client/86 server historical receipts.

Recovery receipt:
`D:/Proiecte/Project Restructure/Aion Development Workspace/archives/server/game-server/backups/playerbots-recruitment-20261007-124827-848789`.
Installed override SHA-256: `f49bd48bcfd6a0a7eabae4a8fe2213791dba7dcdab1cb1945d3a900451cd1a3e`.
External evidence: `diagnostics/playerbots-metadata-20261007/postinstall.json`,
`committed-rows.txt`, package `verification.json` and recovery `migration.txt`.

**PB-VAL-011 remains pending for actual gameplay:** database checkpoint failure/
rollback/retry, quest/care/supplies/gear/trade saves, dismissal and resummon/restart
persistence. Migration and disk installation are complete. The user performs game
tests; neither GameServer nor the client was started. MariaDB was left as the user
opened it. The original unavailable-DB preflight failed before mutation, then the
successful authorized run completed after DB availability; no service startup mode
was changed.

Full PB-SCOPE-012 is partial: formation, spacing, behavior/command preferences,
presets/archive metadata and independent bot contexts still have their existing
storage/lifecycle adapters. This slice migrates the two hot care/gear namespaces;
it does not claim all bot files or upstream repository features are complete.
PB-PORT-005B remains the separate next class slice. Further repository namespaces
can follow independently; this native persistence slice is now installed.

## Legacy file retirement completed — 7 October 2026

The user requested removing the per-character files. The bounded cleanup for the
already migrated `care-character-*` and `gear-character-*` namespaces is implemented
in `client-mods/playerbots/retire_metadata_files_offline.py`. Its native read-only
DAO preflight verifies each file has valid character/account-owned database state,
archives and hash-verifies the exact files externally, then removes only those
files with Windows handles excluding readers/writers throughout deletion. Default
execution requires GameServer/client off. Other settings/media and the deployed JAR
must remain unchanged. A missing DB row refuses retirement; authoritative DB
values are never replaced from an old file.

The user explicitly authorized **this time only**, removing files while services
run if unused. All 32 files passed Windows read/write exclusion guards; all 32 had
valid native owned DB rows. Seven DB snapshots had advanced from the stale files
during gameplay; authoritative DB values were retained, with no DB writes.
**32 legacy files removed**, 61 other settings/media files and the deployed JAR
unchanged. All 16 installed-mod checks/31 client hashes pass; inventory records
70 client/90 server historical receipts. No stop/start/restart/attach occurred.
This narrow exception does not grant future running-server changes.

Recovery archive (all 32 files hash-verified before removal):
`D:/Proiecte/Project Restructure/Aion Development Workspace/archives/server/game-server/backups/playerbots-legacy-settings-cleanup-20261007-182907-195194`.
Evidence is external `diagnostics/playerbots-metadata-retirement`. The previous
32-row/zero-difference readiness report predates later gameplay saves. Default
future cleanup remains offline and must refresh process/file/DB state.

**Remaining PB-SCOPE-012A creation-path gap:** `PlayerBotTemporary.persistCreation`
still writes generated gear provenance to a legacy gear file before recruitment.
The migrated session care/gear serializers do not write files, but new Temporary
Bot creation can recreate a gear file. Port that initialization/checkpoint
dependency separately before claiming the namespace completely file-free. This
new evidence does not undo the installed session migration or require re-porting
its verified methods. Do not remove active preferences, behavior/formation/spacing,
party reward witnesses, saved parties or removed-roster records until each native
database/lifecycle adapter preserves its state.


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
