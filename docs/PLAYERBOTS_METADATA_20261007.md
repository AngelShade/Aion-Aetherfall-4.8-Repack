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
appearance/follow packages remain staged/source-only, not installed incidentally.

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
