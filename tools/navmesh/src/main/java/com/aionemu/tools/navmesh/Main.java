package com.aionemu.tools.navmesh;

import java.io.*;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.recast4j.detour.io.*;

/** All-map, restartable offline generation. No server bootstrap or client attachment. */
public final class Main {
    public static void main(String[] args) throws Exception {
        Map<String,String> options = options(args);
        if (options.containsKey("help")) {
            System.out.println("--geo DIR --output EXTERNAL_DIR [--map ID] [--tile X,Y] [--workers 1..8] [--plan]\n"
                    + "Build all discovered maps by default; resume verified tiles. Does not install or alter GameServer.");
            return;
        }
        Path geo = Path.of(required(options, "geo")).toRealPath();
        Path out = Path.of(required(options, "output")).toAbsolutePath().normalize();
        if (out.startsWith(geo) || geo.startsWith(out)) throw new IllegalArgumentException("Output must be separate from geometry");
        for (Path p = out; p != null; p = p.getParent())
            if (Files.exists(p.resolve(".git"))) throw new IllegalArgumentException("Output must be outside the source repository");
        Files.createDirectories(out);
        out = out.toRealPath();
        if (out.startsWith(geo) || geo.startsWith(out)) throw new IllegalArgumentException("Output resolves inside geometry");
        for (Path p = out; p != null; p = p.getParent())
            if (Files.exists(p.resolve(".git"))) throw new IllegalArgumentException("Output resolves inside source repository");
        int workers = Integer.parseInt(options.getOrDefault("workers", "1"));
        if (workers < 1 || workers > 8) throw new IllegalArgumentException("workers must be 1..8");
        String tile = options.get("tile");
        if (tile != null && !options.containsKey("map")) throw new IllegalArgumentException("--tile requires --map");
        int[] chosenTile = tile == null ? null : Arrays.stream(tile.split(",", -1)).mapToInt(Integer::parseInt).toArray();
        if (chosenTile != null && (chosenTile.length != 2 || chosenTile[0] < 0 || chosenTile[1] < 0))
            throw new IllegalArgumentException("tile must be nonnegative X,Y");
        Settings settings = Settings.DEFAULT;
        try (var lockChannel = FileChannel.open(out.resolve(".generator.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             var lock = lockChannel.tryLock()) {
            if (lock == null) throw new IllegalStateException("Another generator owns this output");
            String modelHash = Artifacts.hash(geo.resolve("models.mesh"));
            var input = new AionGeometry(geo);
            var worlds = new WorldBounds(geo);
            if (!modelHash.equals(Artifacts.hash(geo.resolve("models.mesh"))))
                throw new IOException("Shared models changed while loading");
            List<Integer> maps = input.mapIds();
            if (maps.isEmpty()) throw new IOException("No maps discovered; input is incomplete");
            if (options.containsKey("map")) {
                int map = Integer.parseInt(options.get("map"));
                if (!maps.contains(map)) throw new IllegalArgumentException("Map absent from geometry: " + map);
                maps = List.of(map);
            }
            String tool = Artifacts.toolHash();
            Properties report = new Properties();
            report.setProperty("tool.sha256", tool); report.setProperty("settings", settings.toString());
            report.setProperty("maps.discovered", Integer.toString(maps.size()));
            report.setProperty("world.bounds", worlds.fingerprint);
            for (var entry : worlds.sizes.entrySet()) {
                if (!input.mapIds().contains(entry.getKey()) && entry.getValue() > 0)
                    throw new IOException("Missing geometry for configured map " + entry.getKey());
                if (!input.mapIds().contains(entry.getKey()))
                    report.setProperty("map." + entry.getKey() + ".status", "NO_GEOMETRY_ZERO_SIZE_NATIVE_MAP");
            }
            ExecutorService pool = Executors.newFixedThreadPool(workers);
            int failures = 0;
            try {
                for (int mapId : maps) {
                    try {
                        String stamp = input.fingerprint(mapId, modelHash);
                        String fingerprint = Artifacts.hashText(tool + settings + stamp + worlds.fingerprint);
                        var map = input.load(mapId);
                        float[] mapMin=worlds.min(map), mapMax=worlds.max(mapId,map);
                        int nx = (int)Math.ceil((mapMax[0] - mapMin[0]) / settings.tileMetres());
                        int ny = (int)Math.ceil((mapMax[2] - mapMin[2]) / settings.tileMetres());
                        if (nx < 1 || ny < 1 || (long)nx * ny > 1_000_000)
                            throw new IOException("Invalid map bounds: " + nx + "x" + ny);
                        if (chosenTile != null && (chosenTile[0] >= nx || chosenTile[1] >= ny))
                            throw new IllegalArgumentException("Tile outside map grid " + nx + "x" + ny);
                        String key = "map." + mapId;
                        report.setProperty(key + ".tiles", nx + "x" + ny);
                        report.setProperty(key + ".geometry", map.description());
                        if (options.containsKey("plan")) {
                            report.setProperty(key + ".status", "PLANNED");
                            System.out.println(mapId + " planned " + nx + "x" + ny + " " + map.description());
                        } else {
                            Path dir = out.resolve(Integer.toString(mapId)).resolve(fingerprint);
                            Files.createDirectories(dir);
                            Properties manifest = new Properties();
                            manifest.setProperty("format", "aion-detour-1");
                            manifest.setProperty("recast4j", "1.5.8");
                            manifest.setProperty("fingerprint", fingerprint);
                            manifest.setProperty("input", stamp);
                            manifest.setProperty("tool.sha256", tool);
                            manifest.setProperty("settings", settings.toString());
                            manifest.setProperty("world.bounds", worlds.fingerprint);
                            manifest.setProperty("origin", Arrays.toString(mapMin));
                            manifest.setProperty("bounds.max", Arrays.toString(mapMax));
                            manifest.setProperty("tiles.x", "" + nx); manifest.setProperty("tiles.y", "" + ny);
                            manifest.setProperty("tile.metres", "" + settings.tileMetres());
                            manifest.setProperty("geometry", map.description());
                            Properties dynamic = new Properties();
                            int di = 0;
                            for (var d : map.dynamics()) {
                                String dk = "entity." + di++;
                                dynamic.setProperty(dk, d.model() + ";type=" + d.type() + ";id=" + d.id()
                                        + ";level=" + d.level() + ";flags=" + d.flags()
                                        + ";min=" + Arrays.toString(d.min()) + ";max=" + Arrays.toString(d.max()));
                            }
                            dynamic.setProperty("count", "" + di);
                            Artifacts.store(dir.resolve("dynamic-exclusions.properties"), dynamic);
                            manifest.setProperty("status", "BUILDING");
                            Artifacts.store(dir.resolve("map.properties"), manifest);
                            List<Callable<String>> batch = new ArrayList<>(); int completed = 0, errors = 0;
                            for (int y = 0; y < ny; y++) for (int x = 0; x < nx; x++) {
                                if (chosenTile != null && (x != chosenTile[0] || y != chosenTile[1])) continue;
                                final int tx = x, ty = y;
                                batch.add(() -> bake(map, settings, dir, mapMin, mapMax, tx, ty));
                                if (batch.size() == workers) {
                                    for (var result : pool.invokeAll(batch)) { if (!printResult(mapId, result)) errors++; completed++; }
                                    batch.clear();
                                }
                            }
                            for (var result : pool.invokeAll(batch)) { if (!printResult(mapId, result)) errors++; completed++; }
                            if (!stamp.equals(input.fingerprint(mapId, modelHash))) throw new IOException("Map inputs changed during generation");
                            if (!worlds.fingerprint.equals(new WorldBounds(geo).fingerprint)) throw new IOException("Native map bounds changed during generation");
                            if (errors > 0) throw new IOException(errors + " failed tiles; inspect tile errors and rerun");
                            String status = chosenTile == null ? "BUILT_REQUIRES_RUNTIME_VALIDATION" : "PARTIAL_TILE_SELECTION";
                            manifest.setProperty("status", status); manifest.setProperty("tiles.processed", "" + completed);
                            Artifacts.store(dir.resolve("map.properties"), manifest);
                            report.setProperty(key + ".status", status); report.setProperty(key + ".directory", out.relativize(dir).toString().replace('\\', '/'));
                        }
                    } catch (Exception e) {
                        failures++; report.setProperty("map." + mapId + ".status", "FAILED: " + e);
                        System.err.println(mapId + " FAILED: " + e);
                    }
                    Artifacts.store(out.resolve("report.properties"), report);
                }
            } finally { pool.shutdownNow(); }
            if (!modelHash.equals(Artifacts.hash(geo.resolve("models.mesh"))))
                throw new IOException("Shared models changed during generation; results cannot be released");
            System.out.println("Maps=" + maps.size() + ", failures=" + failures + ", report=" + out.resolve("report.properties"));
            if (failures != 0) System.exit(1);
        }
    }
    private static boolean printResult(int map, Future<String> result) throws InterruptedException {
        try { System.out.println(map + " " + result.get()); return true; }
        catch (ExecutionException e) { System.err.println(map + " TILE FAILED " + e.getCause()); return false; }
    }
    static String bake(AionGeometry.MapGeometry map, Settings settings, Path dir, float[] mapMin, float[] mapMax, int x, int y) throws IOException {
        String stem = x + "_" + y;
        Path data = dir.resolve(stem + ".navtile"), receipt = dir.resolve(stem + ".properties");
        try { if (Files.isRegularFile(receipt)) {
            var p = Artifacts.read(receipt);
            if ("EMPTY".equals(p.getProperty("status")) && !Files.exists(data)) return stem + " empty (verified resume)";
            if ("BUILT".equals(p.getProperty("status")) && Files.isRegularFile(data) && Artifacts.hash(data).equals(p.getProperty("sha256"))) {
                try (var in = Files.newInputStream(data)) {
                    var decoded = new MeshDataReader().read(in, 6);
                    if (decoded.header.x == x && decoded.header.y == y && decoded.header.polyCount > 0)
                        return stem + " reused";
                }
            }
        }} catch (IOException | IllegalArgumentException | java.nio.BufferUnderflowException invalidSavedArtifact) {
            System.err.println(stem + " rebuilding invalid saved tile: " + invalidSavedArtifact);
        }
        long start = System.nanoTime();
        float size = settings.tileMetres(), border = settings.borderMetres();
        float minX = mapMin[0] + x * size, minZ = mapMin[2] + y * size;
        var triangles = map.triangles(minX - border, minZ - border, minX + size + border, minZ + size + border);
        var mesh = TileBaker.build(triangles.vertices(), triangles.indices(), settings, mapMin, mapMax, x, y);
        Properties p = new Properties();
        p.setProperty("triangles", "" + triangles.indices().length / 3);
        p.setProperty("milliseconds", "" + (System.nanoTime() - start) / 1_000_000);
        if (mesh == null) {
            Files.deleteIfExists(data); p.setProperty("status", "EMPTY");
        } else {
            Artifacts.atomic(data, out -> new MeshDataWriter().write(out, mesh, ByteOrder.LITTLE_ENDIAN, false));
            try (var in = Files.newInputStream(data)) {
                var check = new MeshDataReader().read(in, 6);
                if (check.header.x != x || check.header.y != y || check.header.polyCount != mesh.header.polyCount)
                    throw new IOException("Tile round-trip verification failed");
            }
            p.setProperty("status", "BUILT"); p.setProperty("sha256", Artifacts.hash(data));
            p.setProperty("polygons", "" + mesh.header.polyCount);
        }
        Artifacts.store(receipt, p);
        return stem + " " + p.getProperty("status") + " " + p.getProperty("milliseconds") + "ms";
    }
    private static Map<String,String> options(String[] args) {
        Map<String,String> result = new HashMap<>();
        Set<String> names = Set.of("geo", "output", "map", "tile", "workers", "plan", "help");
        for (int i = 0; i < args.length; i++) {
            String key = args[i].startsWith("--") ? args[i].substring(2) : "";
            if (!names.contains(key) || result.containsKey(key)) throw new IllegalArgumentException("Unknown/duplicate option: " + args[i]);
            String value = "true";
            if (!key.equals("plan") && !key.equals("help")) {
                if (++i == args.length || args[i].startsWith("--")) throw new IllegalArgumentException("Missing value for " + key);
                value = args[i];
            }
            result.put(key, value);
        }
        return result;
    }
    private static String required(Map<String,String> options, String name) {
        String value = options.get(name);
        if (value == null) throw new IllegalArgumentException("Missing --" + name + "; use --help");
        return value;
    }
}
