# PB-SCOPE-012C — native saved-party/removal repository and packaged UI

**Installed offline on 7 October 2026; all nine loose runtime files retired.**
This continues PB-SCOPE-012A/012B at the user's request to handle `saved-parties`,
`removed` and `media`. GameServer/client remained off; no lifecycle change/attach.

## Native behavior and dependency

The same pinned repository purpose is used: upstream
`src/Db/PlayerbotRepository.cpp` (blob `968c2c983813f25961ba58ae9db4ab446eef723c`)
and `src/Db/PlayerbotsDatabase.cpp` (blob `e574fec8fda801e101727f2717051bff667cf5b4`)
at `037c01418b5d01506917a3db9b44fd56ac5f965c`. Native mapping uses Aion
DatabaseFactory, prepared statements, existing player/account/roster identity and
explicit command transactions. Presets/bookmarks and removed-roster quarantine are
Aion adapter features; no identical WoW saved-party UI/parity claim is made.

- Two account documents moved into `playerbot_saved_parties`. Save/load preserve
  actual character IDs, Temporary/owned-alt distinction, roles, orders, bookmark
  IDs, preset identities/names and limits. Loading continues to prune removed
  Temporary Bots. Native owner/roster checks refuse foreign documents/identities.
- Four removal markers moved into `playerbot_removed`. Native dedicated roster
  identity is required, including pending roster entries without complete player
  rows. The foreign key pins the roster record; no character/inventory/ID deletion
  or release is introduced. Existing native archive marker wins; pruning stays
  authoritative after a failure between marker commit and preset cleanup.
- Three current installed UI files (`bots.html`, `bots.css`, `bots.js`) are bundled
  byte-for-byte under JAR `playerbots/media`. HTTP routes now read PlayerBotMedia
  resources with the same handlers/content types/authentication/action behavior.
  Media is executable interface content, not DB settings. No UI redesign or client
  replacement occurred. Maven resource/assembly rules bundle maintained source UI
  and omit loose runtime media. Source appearance UI remains separately staged;
  the bounded install bundles actual deployed UI, preserving its current behavior.

The additional evidenced dependency PB-SCOPE-012A-R1 is repaired in this package:
Temporary.persistCreation previously recreated a gear file before a player row
existed. It now queues validated generated-ID provenance in CreationMetadata;
Persistence.save loads/queues it into the existing Metadata cache once native
player insertion succeeds. It then shares the first inventory/progress commit.
Failed loads retain deferred state, failed transactions retain dirty metadata;
only committed checkpoints clean it. Unexpected existing metadata refuses
creation replacement. No automatic owned-alt build change or ID release occurs.
Existing care/gear and five property namespaces retain their installed adapters.
The runtime `config/playerbots` directory is empty; the three requested
subdirectories are absent. Source UI/build files remain maintained in the repo.

## Transaction, installation and recovery

Schema is `game-server/sql/playerbot_repository.sql`. The guarded installer
checks actual host shutdown, exact baseline/payload/tool/schema/media hashes and
all active files. Native read-only preflight validates every document and account
/character/roster identity before installation. Six documents import in **one
transaction**, existing rows are never overwritten during migration. The commit
boundary is recorded; uncertain acknowledgement keeps installation held for review.
A separate **read-only connection verifies all six committed JSON values exactly**
and their native identities before file retirement.

All nine original files are externally archived and hash-verified. Windows file
handles exclude reads/writes throughout bounded retirement, with rollback of
missing files on failure. Only exact expected paths are removed; directories use
nonrecursive empty-folder removal. Base JAR, launcher, current client, commands
and all prior installed mods remain unchanged. No native world fixture or actor
ID allocation occurred. SQL writes were limited to the authorized repository
schema/document import.

Current cumulative receipt and full file recovery archive:
`D:/Proiecte/Project Restructure/Aion Development Workspace/archives/server/game-server/backups/playerbots-recruitment-20261007-193213-430087` (`preferences-before`).
Override SHA-256: `4a65e21ca22fff63a2d1cbbf7d0472bbb314894994045ceba5264894581de69b`.
Final package:
`D:/Proiecte/Project Restructure/Aion Development Workspace/staging/output/playerbots-repository-final-20261007`.
Earlier package `playerbots-repository-20261007` is superseded; it lacks the
creation provenance dependency. Do not install it.

Eight existing methods in five definitions changed: Presets.Store load/write,
RosterRemoval removed/archive/remove (remove log text), HttpService handle,
Temporary.persistCreation and Persistence.save. Original schemas/unselected
methods retained; **154 prior JAR entries are byte-identical**. Four new helpers/
DAO definitions plus three UI resources were added. Native door/floor/follow
progress, care/gear/preferences/formation/spacing/rewards, alt restrictions,
custody, revival, class/trade/quest and all other earlier mods survive. Changed
existing/helper methods have explicit runtime SCOPES; future live work needs
appropriate authorization/preloading/fresh agent revision. No live attach here.

Shared stager/formation wrappers now preserve bundled resources without requiring
retired runtime media; new receipts carry bundled UI hashes. Installed inventory
checks packaged UI. A maintained navigation restage reproduces the current JAR
without method/file differences or UI restoration.

## Verification and acceptance

- Full offline Commons/GameServer compilation passes (2,418 sources).
- **24** isolated repository/document/identity/transaction/media checks and **10**
  creation provenance/checkpoint checks pass against staged and installed classes.
- All eight selected source/staged methods match; installed JAR matches final
  payload. Preserved entries and UI resource hashes verified.
- Actual read-only production repository loaders read both accounts and exclude
  removed Temporary Bots from bookmarks/presets; all four native removed markers
  and dedicated roster identities remain. No GameServer/World initialization.
- **17** installed-mod checks pass, retaining all **31** earlier client hashes,
  base JAR and launcher. Inventory: 70 client/94 server historical receipts.
- External evidence: `diagnostics/playerbots-repository-20261007`, including
  committed-files.txt, native-production-read.txt, preservation-final.json and
  postinstall.json.

Actual UI rendering, save/loadparty/remove commands, dismissal/restart, new
Temporary Bot creation and native transaction failure/retry acceptance remain
with the user (PB-VAL-011/earlier preset/removal acceptance). The reported doorway/
room behavior still requires PB-VAL-005 game acceptance. No server/client startup
was performed. PB-PORT-005B Spiritmaster remains the next independent class slice;
full world/invitation/trade/context repository parity and historical item-ID
release-path investigation remain unfinished.
