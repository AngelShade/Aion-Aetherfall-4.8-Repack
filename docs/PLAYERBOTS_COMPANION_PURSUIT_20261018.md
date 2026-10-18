# Companion pursuit before world journey planning

18 October 2026. The owner reported Healerul trying another approach near a
column and MagicDps remaining around a lift while the owner was elsewhere in
Artisans Hall. The request is companion control, explicitly not another elevator
implementation change. Screenshots establish separation, not the exact selected
route or active mover state.

Secondary received a native menu capture for LeMuse: SUPPORT/FOLLOW,
NON_COMBAT, questing disabled, full health, “waiting for terrain work budget”
around 18 m away. That means outstanding terrain work, not proof which token was
waiting or that the budget was exhausted: the old status includes running work.

## Changed policy and gates

- Only a recruited companion with the matching owner ID, native team, map and
  instance enters direct pursuit. Independent world activity does not enter it.
- Ordinary ground following first proves a direct native walking connection to
  its role slot on the owner's actual floor. Furniture may clip the preferred
  slot; the formation is a preference, not a reason to keep an unreachable goal.
  Visibility alone is not walking proof. If obstructed, existing trail/route
  planning finds a real detour rather than ignoring walls or doors.
- These two bounded native straight-walking probes do not join the shared world
  graph-search quota. GroundNavigation's existing 128-step sweep limit remains;
  every physical mover step still checks current support/body/door clearance.
  This is not an unlimited per-companion pathfinder. Obstructed fallback retains
  the existing measured search budget and queued age. Failed identical direct
  approaches are briefly reused while the fallback proceeds.
- A proved direct approach releases obsolete road/trail/queued-trip proposals.
  A current single direct edge is refreshed without stopping or changing its
  corridor generation; only actual mover arrival consumes points. Stopped-owner
  regroup can retain its direct lease, and arrival stops ordinary travel.
- A companion already committed to another trip can hand back to Follow only
  with actual static support at its current feet and a complete native walking
  connection. Otherwise physical carry/flight/jump completion retains custody.
  No elevator implementation, authored link, moving-floor packet or shared mover
  was changed. There is no airborne detachment or teleport shortcut.
- For a same/higher-floor blocked companion target, walking detours precede
  speculative climbing. Optional buff/summon casts can yield when the companion
  is still out of formation after the owner stops. Actual combat, urgent aid,
  native interactions and explicitly assigned missions retain their own gates.

## Scope and preservation

Main owns Navigation, FollowIntent and Session. Secondary independently reviewed
the companion gates read-only; stale intermediate findings were corrected against
the final source. Existing configured fast catch-up remains. Shared world routing,
world population, planner budgets, independent activity, elevator internals,
appearance, rewards and persistent SQL state remain outside this repair.
Broader quest/class/social/economy work stays deferred.

Normal Maven build passed with tests skipped. Exact JAR comparison changed only
Navigation, Session and FollowIntent families plus build metadata; all other
classes/resources, including elevator implementations and the physical mover,
matched the installed original. No full suites, new test files, gameplay fixtures
or runtime attachment.

Installed original-only after owner closure confirmation and fresh process checks.
Windows initially rejected the JAR write with a mapped-section error; the original
hash remained intact and no subsequent files were written. The failed-attempt
backups were retained. After Windows reported no holder, a fresh guarded retry
succeeded, with separate backup and copied hashes. No forced component shutdown.

JAR SHA-256: `4f259ab2b81e35bfe90ec30331629ac6e1f69c25a4d823f5030c43f06ce6ce1a`.
Build: external `staging/target/companion-direct-pursuit-20261018`.
Receipt: external `archives/server/backups/companion-direct-pursuit-20261018-retry1/manifest.json`.
No client, repack, config or SQL change. User acceptance remains direct approach,
obstacles/doorways, stopped regroup, travel handoff and independent world movement.

## Nearby stuck recovery - 19 October 2026

The owner reported companions remaining blocked among rocks until manually
summoned or distant enough for recall, and explicitly chose walking/jumping first,
then teleport if still stuck. This adds a fallback; it does not establish the
specific failed geometry/path from the screenshot or universal obstacle routing.

Ordinary companion Follow/Passive observes actual position. After five seconds
without at least .75 m cumulative displacement from the retained anchor, native
nearby arrival placement regroups the companion and normal following resumes.
Walking away around an obstacle still counts as progress. New path publications
and a moving owner do not reset the timer. Arrival, changed orders, map/instance
changes, native activity and observation gaps over two seconds reset it.
Combat/aggro, rooted movement, casting, trading, looting, item/object interaction,
assigned missions, flight/glide and active jump/lift/transport custody are excluded.
The normal 60 m recall stays unchanged. No forced recall scope bypasses combat.
Failed placement waits through another complete follow grace period before retry.

Owner control: `gameserver.playerbots.companion.stuck_recall_ms = 5000` in
`config/playerbots/features.properties`; 0 disables only this nearby fallback.
Only a missing setting is appended to the original owner's existing configuration.
Native team/placement/character custody and current Follow/Passive orders remain.

Normal Maven build and 334 focused recall/progress checks passed, including
stationary retries, walking detours, activity/arrival reset, observation gaps,
instance changes, clock rollback, disable and invalid positions. These checks
use inert actors/position playback without World, IDs, gameplay tasks or SQL.
The existing formation helper's bind calls were updated to the current API;
its stopped-owner assertion now reflects the already-installed regroup contract.
Only Config/Navigation/Recall/Session JAR families and build metadata differ from the latest
original; dependencies and other JAR resources match. Original JAR/config copied
after successful process enumeration, with backups and copied hashes verified.
Receipt: external `archives/server/backups/companion-stuck-regroup-20261019/manifest.json`.
Build: external `staging/target/companion-stuck-regroup-20261019-final`.
No components started, client/repack changes, new test files or full suites.
User acceptance remains the actual obstacle, fallback timing and follow resumption.
