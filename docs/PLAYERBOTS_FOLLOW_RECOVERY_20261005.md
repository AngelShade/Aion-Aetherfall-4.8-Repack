# PB-REPAIR-FOLLOW-001 — removed by user request, 7 October 2026

The user reported companions unable to walk through opened doors and stationary
in several rooms in Steel Rake and Steel Rake Cabin, including after summoning
them inside. Room names are unknown. They can enter and leave combat during this
failure. The previous portal/idle-follow diagnosis did not establish the cause
of this report, and the user rejected the teleport recovery approach.

The package installed in `playerbots-recruitment-20261007-170333-991220` is
**removed**, not pending acceptance. Its helper/source/check and staging/install
entrypoints were removed. `Navigation.follow`, `Travel.followTeleport` and
`Travel.close` were restored to their pre-recovery behavior. The incidental
`bots.js` replacement in that package was restored too.

Removal receipt:
`D:/Proiecte/Project Restructure/Aion Development Workspace/archives/server/game-server/backups/playerbots-recruitment-20261007-171732-465446`.
The restored cumulative override exactly matches the native metadata baseline
SHA-256 `f49bd48bcfd6a0a7eabae4a8fe2213791dba7dcdab1cb1945d3a900451cd1a3e`.
Historical recovery packages/receipts remain available for evidence; do not
reinstall them. All earlier cumulative mods remain installed.

The replacement investigation/engine repair is [PB-REPAIR-NAV-002](PLAYERBOTS_NAVIGATION_20261007.md).
It handles native dynamic doors and floor movement generically, without a
dungeon/door/room allowlist or a stalled-follower teleport fallback.
GameServer/client remain off. Actual room/combat/party acceptance is pending.
