# PB-SCOPE-012B — remaining property settings moved to native metadata

**Installed and legacy files retired offline on 7 October 2026.** The user listed
remaining `.properties` files after care/gear migration and requested completing
that cleanup. This is a continuation of PB-SCOPE-012A and the upstream repository
purpose, with actual game persistence acceptance remaining PB-VAL-011.

## Behavior, source and native mapping

Pinned upstream `037c01418b5d01506917a3db9b44fd56ac5f965c`:
`src/Db/PlayerbotRepository.cpp` (`968c2c983813f25961ba58ae9db4ab446eef723c`)
and `src/Db/PlayerbotsDatabase.cpp` (`e574fec8fda801e101727f2717051bff667cf5b4`).
Cached source Git blobs were reverified. Their GUID/key/value repository purpose
maps to Aion MetadataDAO, native player/account ownership, versioned snapshots
and existing native checkpoint transactions; no WoW schema/opcodes are copied.

| Legacy namespace | Files | Native persistence adapter |
| --- | ---: | --- |
| character-* (preferences) | 17 | Preferences.load/save; Metadata cache and bot checkpoint |
| behavior-character-* | 13 | PartyBehavior.State constructor/save; same bot checkpoint |
| spacing-character-* | 7 | Spacing.values/configure; same bot checkpoint |
| formation-character-* | 2 | FormationLayout selected/configure; explicit owner command commits MetadataDAO transaction |
| party-rewards-character-* | 13 | PartyCompletion load/remember; witnesses share native bot quest checkpoint |

All 52 snapshots, roles, flags, distances, formations and reward NPC witnesses
were imported exactly and validated against native character/account ownership.
No defaults replace existing DB rows. DB failure refuses loading/configuration;
optimistic revisions prevent stale overwrites. Bot settings queue in memory and
commit with inventory/progress; failure retains dirty snapshots for retry. The
formation owner is a human, so it has no companion checkpoint: explicit formation
changes commit their own native metadata transaction before acknowledgement.

Existing default values and native quest REWARD filtering remain. No AI strategy,
build/equipment/Stigma/level replacement or client/UI change was added. Maintained
read-only legacy load paths remain migration compatibility; listed namespaces
never write new properties files. Saved-party JSON and removed-roster records
remain active; their six files and three media files are preserved.

## Installation, retirement and preservation

Current cumulative receipt:
`D:/Proiecte/Project Restructure/Aion Development Workspace/archives/server/game-server/backups/playerbots-recruitment-20261007-191742-640906`.
Override SHA-256: `83a3143148a2e3a8f5b28894742399db05f8c302111990df41b2adf99b2dc978`.
Package: `D:/Proiecte/Project Restructure/Aion Development Workspace/staging/output/playerbots-metadata-preferences-20261007`.

Eleven existing methods in six definitions changed, with original schemas and
unselected methods preserved. One helper, MetadataConfiguration, was added.
**152 earlier JAR entries are byte-identical**, including generic door/floor and
follow-progress navigation, care/gear cache/checkpoints, all prior mods and alt
build protections. Base JAR, launcher, geometry, commands, media and client survive.
Changed existing methods have explicit runtime SCOPES; future helper changes also
require runtime SCOPES/preloading/fresh agent revision under applicable lifecycle
authorization. No live attach happened here.

The offline importer committed **52 new rows** in one transaction; zero existing
rows were replaced. A separate native **read-only connection verified all 52**
committed maps/owners/revisions exactly match their files (zero differences).
Then retirement archived/hash-verified each original file and removed only those
52 paths under file-use exclusion guards. Recovery archive:
`D:/Proiecte/Project Restructure/Aion Development Workspace/archives/server/game-server/backups/playerbots-legacy-settings-cleanup-20261007-191828-971151`.
All nine remaining media/preset/removal files and the new installed JAR remain
unchanged by retirement. Successful host CIM checks prove GameServer/client off;
the installer rechecks process state before replacement. No server/client/database
startup, shutdown/restart, attach, world actor/DB fixture or ID allocation occurred.
Only the authorized native metadata import wrote the existing database.

## Verification and outstanding work

Full offline Commons/GameServer compile passes (2,414 server sources). **35**
existing metadata and **31** preference adapter/validation checks pass against the
installed JAR with isolated recording JDBC, no live database/world fixture. All
11 installed changed methods match compiled source. Inventory passes **16** checks
and preserves all **31** prior client hashes; 70 client/93 server historical
receipts are recorded. Evidence is external `diagnostics/playerbots-metadata-preferences-20261007`.

User gameplay acceptance remains pending for configure/checkpoint/failure/retry,
logout/dismissal/resummon/restart and reward turn-in witnesses (PB-VAL-011). The
reported door/room failure remains PB-VAL-005 acceptance pending; its installed
repair is byte-identical here. PB-PORT-005B Spiritmaster remains the independent
next class slice. Full world/invitation/trade/preset/archive repository parity and
the item-ID release investigation remain unfinished. Known PB-SCOPE-012A-R1
Temporary.persistCreation still writes initial gear provenance before the player
row exists; that separate creation checkpoint dependency is not part of these
five namespaces and remains open. Do not claim the entire bot folder file-free.


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
