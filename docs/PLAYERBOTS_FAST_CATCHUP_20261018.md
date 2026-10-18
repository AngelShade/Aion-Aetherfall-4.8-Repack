# Fast companion catch-up and shared destination queries

18 October 2026. User requested very fast companion regrouping, reported a small
CPU improvement but high remaining cost at roughly 500 bots / 3.8 GB, and deferred
quest/class/encounter/social/economy expansion to the far future.

## Behaviour and movement ownership

The earlier follow speed allowed up to 1.5 times the leader's speed and removed
the bonus when the leader stopped. Following now accelerates toward the faster
of the native companion/leader speeds multiplied by the configurable
`gameserver.playerbots.companion.catchup_multiplier` (default **4**). It reaches
that speed at 9 m from the assigned role slot and eases down inside that distance.
Within 1 m it matches a moving leader or uses native speed for a stopped leader.
Setting 1 removes the extra catch-up acceleration. This is an explicitly custom
speed policy requested by the owner, not a claim of retail or exact WoW speed.

The multiplier requires the current admitted follow intent. A distant trail
waypoint no longer loses the bonus just because it is over 20 m from the owner.
The intent records an active walking corridor generation: physical corner handoff
retains that same speed lease, without redirecting corners, acknowledging arrival,
extending expiry or retaining another generation. Native combat/new movement
ownership clears the intent. Independent world activity, combat movement, fear,
confusion, slows, casting, jumping/falling and lift carry retain existing rules.
The same native speed publication feeds server motion and client interpolation;
no teleport or geometry bypass is introduced. High-speed catch-up still requires
a reachable route and normal bounded planning; it cannot repair missing routes.

## CPU evidence and shared immutable memory

A five-second OS sample of original GameServer PID 32012 measured **38.52% of
total host CPU**, 23.67 CPU-seconds, working set **3.71 GiB**, private memory
**3.79 GiB**. A short sample is not a sustained benchmark or heap measurement.
The 21:30:02 Dorirason warning was 1501 ms wall time, **31 ms thread CPU**, zero
session-lock wait. Its last-action label does not establish material discovery
as the source of the whole delay. Other blocking, scheduling and GC remain unknown.

Source inspection found `PlayerBotWorldTravel.places/near` serialized immutable
NPC spawn discovery and every cached spatial query under the same class monitor
as transport catalogue construction. Secondary Playerbots Implementation moved
place/spatial catalogues into source-generation-owned concurrent maps. A short
separate lock swaps the holder only when native map/spawn data identities change;
warm lookups and exact spatial filtering run without the shared global monitor.
Cold discovery coalesces per NPC. Native spawn filters, deduplication, destination
ordering, transport code, prices and live actor eligibility remain unchanged.

This reuses existing immutable data in memory and reduces global serialization;
it does not increase heap settings or cache live actor positions, inventory,
combat decisions or transport eligibility. A cold scan still costs CPU, and
concurrent hash-bin construction can briefly serialize. No measured post-repair
CPU reduction is claimed. The remaining performance issue stays open.

## Verification and delivery

Secondary's six focused source-equivalence checks passed; details are external
`diagnostics/world-travel-catalog-20261018/source-equivalence.json`.
Combined normal Maven build passed, with tests skipped. Ten actual-artifact speed
checks passed (moving/stopped catch-up, slow leader, easing, configurable maximum,
disabled/invalid multiplier and zero native speed). The first Java shell invocation
could not open its Windows preferences under sandboxing; the same offline check
then passed with desktop permission. No game actor, SQL or runtime attachment.

Installed on the original after the owner's closure confirmation and fresh process
verification. Exact normal JAR comparison preserved all entries outside the four
affected class families and build metadata. Existing owner feature settings were
retained; only the new catch-up setting/comment block was appended. Backup and
copied hashes passed. No full suites, browser tests, new test files or gameplay
fixtures. Client/repack/database unchanged.

JAR SHA-256: `cdfa65e3f117f039cb8464313db82327d16d306ec40d796581d01cf3379784b6`.
Build: external `staging/target/companion-fast-catchup-catalog-20261018`.
Receipt: external `archives/server/backups/companion-fast-catchup-catalog-20261018/manifest.json`.
User acceptance remains fast following, corridor turns, stationary regroup,
combat/withdrawal transitions and actual CPU performance. No CPU gain is inferred
from installation or the numeric speed checks.
