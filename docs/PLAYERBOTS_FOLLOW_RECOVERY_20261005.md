# PB-REPAIR-FOLLOW-001 — Stranded followers after short portals/gates (2026-10-05)

## Report
Steel Rake (300100000, instance created 08:21 in `server_console.log`): with 6 members the
companions had trouble passing the key gates; after entering the Central Engine Room
(Drana Generator Chamber, Engineer Lahulahu) the companions never moved again.

## Root cause (evidence)
- The chamber is entered through portal NPC 730202 at (657, 509, 873)
  (`spawns/Instances/300100000_Steel Rake.xml`), which uses loc 3001006
  (659.8, 509.1, 867.8) in `portals/portal_loc.xml`: a ~2.8 m horizontal teleport
  through the wall. The Brig entrance 730200 → 3001002 is similar (~2.6 m).
- `PlayerBotTravel.jumped` only detects owner jumps > max(60 m, 2×speed×elapsed + 25 m),
  and `PlayerBotPartyBehavior.catchUp` requires > 60 m (18 m in combat). Neither fires.
- Companions stay outside, `PlayerBotNavigation.follow/move` is blocked by geometry
  and the companion stands still indefinitely ("blocked by geometry").
- Gates/doors (staticdoor 39..94) block the same way when a companion is left on the
  wrong side; more followers means more outer formation slots and more chances of that.
  This part is mitigated by the same recovery, not separately proven.

## Fix (source, compiled against the installed cumulative override)
- New `PlayerBotFollowRecovery` is a local Aion movement adapter repair.
  Pinned `FollowActions.cpp::FollowAction::Execute` maps formation movement to
  `PlayerBotNavigation.follow`; the cached `MovementActions.cpp` teleport fallback
  is commented out and is not an active upstream parity implementation.
  `PlayerBotNavigation.follow` records progress while it still
  needs to follow; if a companion makes neither ≥3 m displacement nor ≥1.5 m owner-distance
  progress for 6 s, is > 6 m away (or > 1.6 m behind blocked native sight), both are out of combat/aggro, the owner is standing
  still and nobody is flying, `PlayerBotTravel.followTeleport` relocates it through the
  native `PlayerBotService.relocate` path. State clears on reset/close.
- Changed method bodies only: `PlayerBotNavigation.follow`, `PlayerBotTravel.followTeleport`,
  `PlayerBotTravel.close`; one new helper class. Deployed `PlayerBotNavigation`/`PlayerBotTravel`
  bytecode matched HEAD before the edit (same instruction count; only constant-pool shifts).

## Verification
- `javac` against `libs/playerbot-recruitment-fix.jar;libs/*`: pass.
- 6 original offline `stalled()` checks passed. Reviewed continuation: 14 focused
  offline timer/order/short-wall checks pass, and the source compiles.
- **Not installed.** Installing requires the cumulative override stager + preloading
  (helper is new; two existing classes need runtime SCOPES), next attach revision > 49,
  or disk install + restart. In-game acceptance is still pending.

## Diff review — 7 October 2026

Fresh stall state could survive an explicit Stay/Guard order, so recovery now
requires Follow/Passive. It rechecks displacement and owner-distance progress
before relocation, rejects backward/stale observations, and protects trading,
looting and native ITEM_USE. The old >6 m gate did not cover a short occluded portal;
native sight now permits recovery of a blocked >1.6 m gap only after the same full
stall interval. Nearby visible followers still do not teleport. Native relocation,
same-map/instance, movement, flight, owner stationary, combat and aggro gates remain.

This remains **source-only**, separate from the transmog package; no install or
live attach occurred. Full tick/geodata/gate traversal and actual party movement
acceptance are the user's tests. Current lifecycle policy keeps server/client off.

## Separate observation (not fixed here)
`PlayerBotGearPolicy$State.save` still throws `AccessDeniedException` on Windows
rename (log 03:23–08:11), aborting that tick; PB-REPAIR-SETTINGS-001's retry only
covers QuestSync. Track separately.

7 October continuity: gear save retry PB-REPAIR-SETTINGS-002 is now installed
offline in `081522-429924`; the care retry is retained. Existing uninstalled
packages must be restaged against that baseline. See
`PLAYERBOTS_SETTINGS_FILES_20261007.md`; no server/client startup or attach.
