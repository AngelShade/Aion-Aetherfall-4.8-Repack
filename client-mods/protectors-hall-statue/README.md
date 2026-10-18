# Protectors Hall Teleport Statue

Custom addition requested at Sanctum map **110010000**, instance 1:
**1876.0426, 1526.5166, 811.08057**, heading **71**. The supplied upper-floor
height is retained. The landing lies two metres in front of the statue, at
1874.365259, 1525.427322, 811.08057, to avoid arriving inside its model.

The maintained `statue.json` describes the addition. Server XML defines NPC
990180, one permanent spawn, hotspot 86 and native airport/teleporter 9001.
The NPC copies the existing Divine Road statue's model, scale, guide and
interaction rather than using a different object or a chat-command shortcut.
Base and active English string tables give it its own displayed name.

The native city teleport map has matching airport, airline and map-marker
entries. Elyos Square, Divine Road and Sanctum Outer Port have both outgoing
and return routes, with matching server/client fees based on the existing city
statues. Existing routes and the other world-map hotspots are retained. The
airport marker is placed using the original three city map anchors; the world
hotspot uses the actual landing coordinates and retains the upper-floor Z.

`prepare.py --client ... --timing ... --output ...` composes these resources
with the prepared server-driven hotspot-timing cohort. It verifies the matching
server definitions and existing client ownership hashes, then preserves the
exact compressed bytes of every unchanged PAK member. English labels are also
applied to the matching graphics package and restore image; pet/Lua/key and
native cursor/graphics ownership remain. No live files are written by preparation.

Normal server builder output is prepared with
`client-mods/hotspot-timing/server_delivery.py prepare --with-statue ...`.
The client uses that feature's guarded `install.py`; the server uses its guarded
`server_delivery.py install`. Both require fresh closure verification, external
backups and copied-file hashes. Install this combined package only after the
owner finishes gathering additions and closes the original Aion/GameServer.
Repacks are outside this request.

19 October 2026: **installed on the original client and GameServer**, together
with automatic timing and Cursed Zirius. Joined receipt: external
`archives/delivery/teleport-statue-zirius-20261019.json`. Backups and all copied
hashes verified; no repack changes or component starts. Normal Maven build,
native model/map/route/string checks and unchanged archive-member checks passed.
The prior timing checks still apply: the native Game.dll is identical to the
checked timing payload, and the new normal JAR differs from that checked build
only in build metadata. No full suites, new test files, component starts or
gameplay checks were performed. User acceptance remains statue appearance and
interaction, native labels/markers, outgoing/return trips, actual landing and
the server-configured progress timing.
