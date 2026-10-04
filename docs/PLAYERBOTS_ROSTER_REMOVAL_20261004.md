# Temporary Bot roster removal and recurring item-ID tracking

Installed on disk in cumulative receipt
`target-deploy/game-server/backups/playerbots-recruitment-20261004-142907-454064`.
GameServer was stopped before this update and remains stopped. The new route and
filter become active at the next startup. No real player's bot was removed during
validation.

In Playerbots Window → Roster, each Temporary Bot has **Remove from roster**.
An inline confirmation explains dismissal and archiving; **Cancel** sends no
mutation. Pending creations can also be archived when not reserved. Active bots
must finish their native save/dismissal before removal succeeds. Failed saves keep
their lease and remain visible for retry. Account characters have no removal
control and the server accepts only the current account's dedicated roster IDs.

Removal archives the roster entry; it does not erase native character/inventory
rows, transfer the character to ordinary account slots, or release IDs. Names and
progress remain reserved in the archive. Account-owned tombstones are stored under
`config/playerbots/removed/character-*.json`. They are read on restart and exclude
the bot from recruitment and active-roster limits. Corrupt, unreadable or mismatched
archive records fail closed.

Saved bot bookmarks and party memberships are pruned. Mixed presets retain every
other character with the same IDs, roles and orders; empty presets disappear.
Preset loads apply the marker before use, including if the server stops between
the atomic archive write and the subsequent saved-party cleanup. There is no
automatic restore or progress deletion action.

The bounded package replaced exactly four methods: `PlayerBotRoster.list`,
`PlayerBotPresets.remove`, `PlayerBotPresets.Store.load`, and HTTP `action`. It
added `PlayerBotRosterRemoval` and appended the native roster class override.
All 99 other existing cumulative override entries are byte-identical; the source
base JAR, launcher/classpath, command cache/source, HTML/CSS, client and prior
supplies/revival/camp/formation changes were retained. Only the JAR and `bots.js`
changed among deployed files.

Full server/command compilation and the existing offline companion suite passed.
Fourteen isolated archive checks passed for owned IDs, pending entries, durable
markers, mixed-party pruning, restart-like reads, interrupted cleanup, corrupt
records and account separation. Actual native Awesomium/WebKit controls passed
26 authenticated fixture actions, including confirm/cancel/refresh, other-bot and
owned-alt preservation, desktop and narrow layouts. These fixtures use no real
game account, character or database writes. Logs:
`target/playerbots-roster-check.log`, `target/playerbots-roster-browser-check.log`,
and `target/playerbots-roster-stage.log`. Native save/dismissal integration and
actual in-game panel acceptance remain pending startup.

## Repeated collision is still tracked at its root

The user explicitly asked to remember the repeating primary-key failure during
iteration. `PlayerBotItemIds.nextId` is installed and guards allocation against
persisted item/character IDs. Bardoca 106628 was recovered and foreign item 191241
was preserved. **Why native ID reservations were released while persisted rows
remained is still unproven and unresolved.** The allocation guard and recovery do
not establish that the root release path was repaired.

Continue tracing premature/double ID release and orphan fixture inventory before
claiming a root fix. Player deletion has no inventory owner cascade. Fixture
cleanup must delete and verify only its own private inventory before releasing
IDs; shared storage 2/3/125 and foreign rows are protected. Native NPC Cleaner
already releases NPC IDs: do not manually release them again. This roster action
retains native data and IDs specifically to avoid creating another release path.
