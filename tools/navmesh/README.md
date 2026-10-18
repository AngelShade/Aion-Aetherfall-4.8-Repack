# Aion offline navigation mesh builder

Maintained source for an all-map pipeline using **recast4j 1.5.8**. Reads the
same Aion 4.8 `models.mesh`, map `.geo` and terrain PNGs as `GeoWorldLoader`.
No WoW map dimensions, coordinate format, terrain files or actor rules are used.
No GameServer, World, database, ID factory or running-JVM attachment is involved.

## Build and run

One command builds the tool, generates every discovered map, then independently
reloads and verifies every tile and its source/build provenance:

```powershell
./tools/navmesh/Generate-AionNavmesh.ps1 -GeoDirectory ./target-deploy/game-server/data/geo -BuildRoot 'D:/Proiecte/Project Restructure/Aion Development Workspace/staging/target/navmesh059' -OutputDirectory 'D:/Proiecte/Project Restructure/Aion Development Workspace/staging/output/navmesh059-all' -Workers 2
```

Java 17 or newer (including `javac`) and Maven must be on PATH. `-Plan` only
enumerates the workload. `-HeapMB` controls each Java process heap, default 2048;
the processes run sequentially. `-Workers` defaults to 1. Repeating the command
resumes verified tiles from an identical build; a changed build/input creates a
separate revision. No runtime files are copied or services started.

Use the normal Maven builder, with generated outputs outside the repository:

```powershell
mvn -f tools/navmesh/pom.xml '-Daion.build.root=D:/Proiecte/Project Restructure/Aion Development Workspace/staging/target/navmesh059' verify
$navJar = 'D:/Proiecte/Project Restructure/Aion Development Workspace/staging/target/navmesh059/navmesh/aion-navmesh-1.0-SNAPSHOT.jar'
$navOutput = 'D:/Proiecte/Project Restructure/Aion Development Workspace/staging/output/navmesh059'
java -Xmx2g -jar $navJar --geo target-deploy/game-server/data/geo --output $navOutput --plan
java -Xmx2g -jar $navJar --geo target-deploy/game-server/data/geo --output $navOutput --workers 1
```

The second command builds **all discovered maps**. `--map ID` and `--tile X,Y`
are optional diagnostics, not requirements to implement maps individually.
One worker is the default; the heap limit bounds the process. Worker count does
not promise a hard host CPU percentage. Each worker holds only one tile's build
at a time. Maps are processed sequentially with shared decoded model templates.

Each map has a directory keyed by hashes of its source geometry, builder and
settings. The input directory must have its sibling `static_data/world_maps.xml`
and native server configuration available: map extents use allocated native world
regions (including the terminal region), with `main/world.properties` and `mygs`
region-size precedence. Distant decorative geometry cannot create tens of thousands
of extra map tiles. Missing nonzero-size native maps fail explicitly; absent
zero-size native maps are reported separately.

Complete tile receipts contain content hashes. Restarting reuses verified
tiles; changed input/build settings use a separate revision. Writes are atomic,
one process owns the output directory, and failures remain in `report.properties`
with a nonzero exit status. A selected tile build is explicitly partial.

## Geometry and limits

Aion Z-up is mapped to Recast Y-up. Terrain uses native two-metre samples, its
native diagonal and hole sentinel. Placed collision geometry retains scale,
rotation, translation, separate floors and physical collision classification.
Dynamic doors/events/town objects cannot be baked as permanently closed geometry.
They require live instance-aware validation or overlays; moving platforms, flight
and native transports require separate traversal integration. No invented links.

All155 geometry maps were generated and installed in release059; release060 loads
local tile windows asynchronously into a bounded shared cache and slices queries
under the terrain budget. Mesh paths are candidates: existing native floor/body/
door checks remain authoritative. Generation alone does not prove live movement
or autonomous quest completion. Temporary implementation tests stay outside Git.

Inspect a complete map revision and optionally query two Aion XYZ coordinates:

```powershell
java -Xmx2g -cp $navJar com.aionemu.tools.navmesh.Inspect MAP_REVISION_DIRECTORY
java -Xmx2g -cp $navJar com.aionemu.tools.navmesh.Inspect MAP_REVISION_DIRECTORY '1370,1510,569' '1877,1486,805'
```

Partial/missing/corrupt tiles fail inspection. A disconnected path is reported as
partial; it never means successful arrival. Dynamic exclusions are listed alongside
each map for later instance-aware integration.

## Design references

- AzerothCore `src/tools/mmaps_generator/MapBuilder.cpp`: discovery, tiled offline
  Recast generation, Detour serialization (architecture reference, no copied C++).
- Pinned Playerbots `037c01418b5d01506917a3db9b44fd56ac5f965c`:
  `MovementActions.cpp` MoveTo/SearchForBestPath delegates to native pathfinding.
- Beyond GeoBuilder `e0ce8375c525c86f264ff93352652bfc4723e682`: native extraction.
- Monono2 `25eabe693a02ad999ec7574155e1448611c0a04a`: inspected Aion precedent;
  its custom floor-grid format and component pruning are not reused.
- [recast4j](https://github.com/recast4j/recast4j), Zlib license; library notices are
  explicitly bundled in `META-INF/LICENSE-recast4j.txt`. This tool does not claim
  to implement Recast.

Acceptance requires real geometry coverage, tile seam/path checks, native route
comparison, runtime integration, bounded query/memory measurements and user gameplay.

Release061 adds a compact directed tile-component itinerary for long journeys.
Its offline derivative is built from the already authenticated Detour links;
it does not regenerate geometry or invent traversal links. From the normal
GameServer build and its assembly dependencies, run:

```powershell
java -Xmx2g -cp "$normalBuild/game-server/game-server-4.8-SNAPSHOT.jar;$normalBuild/assembly-libs/*" com.aionemu.gameserver.services.playerbot.PlayerBotNavmeshTopology "$runtime/data/navmesh" "$externalOutput"
```

Add `--verify` to reconstruct every map's directed connectivity and compare all
serialized arrays. Canonical release `--navmesh-topology $externalOutput` performs
this normal-artifact verification and includes the derivative in guarded delivery.
Release061 remains under implementation until its delivery report records otherwise.
Static ground topology does not prove a native flight/elevator/door traversal;
actual native movement and transport checks remain authoritative.

Release061 also exports native synchronized CGA moving physics from the matching
client with `animated-platforms.py --client CLIENT --output EXTERNAL_SCENE`.
Its immutable scene retains the native entity ID, ordered physical-part membership,
node hierarchy, authored controllers/materials and server-uptime animation phase.
The normal GameServer product utility prepares static-floor docking proposals:

```powershell
java -Xmx2g -cp "$normalBuild/game-server/game-server-4.8-SNAPSHOT.jar;$normalBuild/commons/commons-4.8-SNAPSHOT.jar;$normalBuild/assembly-libs/*" com.aionemu.gameserver.services.playerbot.PlayerBotAnimatedLinks "$runtime/data/navmesh" "$externalTopology" "$externalScene/animated-scene.json" "$externalRides"
```

`--verify` reconstructs all155 docking packages and checks their complete binding.
Canonical release supplies `--navmesh-topology` and `--navmesh-animated` together;
it verifies the authored scene against current native client inputs and uses the
same normal artifact for all-map verification and guarded delivery. Dock proposals
never authorize a teleport: fresh body/support/door/hazard checks govern walking
onto a physical platform, following its actual pose and walking onto static ground.
Runtime keeps separate metric vehicle offsets and compact physics-part indices.

## Prepared main corridors and local connections

The shared Playerbot planner also accepts an offline road-network derivative.
"Road" here means a connected, traversable main corridor, rather than a guessed
texture/material tag. Each directed tile component has a hub on a real polygon.
Prepared hub-to-seam-to-hub corridors retain the native link direction and full
Detour path; their local spokes favour clearance and gentler slopes. Global
search uses corridor distance and authored lift travel/wait costs instead of
counting every seam and lift as one equal hop. Arbitrary origins, destinations
and boarding points connect through the normal local mesh/native queries.
The retained runtime search first tries ground corridors and authored lifts.
Only when that bounded pass cannot resolve the route does it admit jump/drop
proposals under the same work budget and memory bounds. A shorter unproved
height shortcut cannot displace a ground road resolved by the first pass.

From a complete normal GameServer build:

```powershell
java -Xmx2g -cp "$normalBuild/game-server/game-server-4.8-SNAPSHOT.jar;$normalBuild/assembly-libs/*" com.aionemu.gameserver.services.playerbot.PlayerBotRoadNetwork "$runtime/data/navmesh" "$externalRoadOutput"
```

This processes every map in the authenticated topology catalog, without rebaking
terrain triangles or starting the game/database. Output contains one `.pbroads`
per map and `roads.properties`; each record binds the original map fingerprint
and topology hash, and generation reloads every output. Deliver the complete
directory as `data/navmesh/roads` together with the matching GameServer build.
Keep existing mesh, topology and animated data unchanged. Partial output without
the final catalog is not an installable network.

Runtime copies a bounded main-corridor horizon and earns fresh native floor,
body, door and hazard proof before publishing motion. Native local detours remain
available for dynamic obstacles; an exhausted local alternative can temporarily
exclude its proposed main arc. Jump/drop/glide and authored lift execution remain
separate physical adapters. No road corridor grants a teleport or substitutes
standing on the wrong floor for arrival. Generation/verification does not certify
live smoothness, CPU cost or every gameplay destination.

The same derivative catalogs short directed jump/drop proposals between nearby
boundary floors in disconnected ground regions. These retain actual mesh takeoff
and landing points, maximum rise/range and bounded descent; they are deliberately
proposals, since a static navmesh cannot certify an airborne body through a wall
or a live door. The runtime funds the existing native ballistic/body/landing proof
before launch, retains incomplete calculation work, and temporarily excludes a
failed proposal. It acknowledges the link only after physical landing. Optional
third argument `PREVIOUS_GROUND_CACHE` reuses authenticated main-corridor outputs
from a previous offline run while preparing the added height derivative.

Height proposals use directed ground regions rather than duplicating every
nearby tile pair. A sparse backbone retains directed in/out trees inside each
candidate strongly connected region and real bridges between those regions;
short directional entrances/exits and separated physical alternatives supplement
it. Generation compares the retained and original directed connectivity before
writing. This controls runtime branching without dropping a required candidate
bridge just because another nearby exit is shorter. Candidate connectivity is
not proof of an unobstructed airborne arc; native execution can still reject it.
Per-map `ground-cache` checkpoints are external generation recovery data, not an
additional runtime input. Install only the final catalog and map `.pbroads` files.
