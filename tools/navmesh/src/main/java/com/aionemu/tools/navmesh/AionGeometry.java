package com.aionemu.tools.navmesh;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferUShort;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import javax.imageio.ImageIO;

/** Offline Beyond-Aion geometry reader. No server objects, IDs, tasks or bootstrap.
 * Native data are Z-up metres; output is Recast (x,z,y). See GeoWorldLoader,
 * Terrain and Geometry.setTransform for the preserved binary/transform contract.
 */
public final class AionGeometry {
    private static final int PHYSICAL = 1 | 128, DYNAMIC_FLAGS = 16 | 32 | 64;
    private static final Pattern GEO = Pattern.compile("([0-9]+)\\.geo");
    private static final Pattern PNG = Pattern.compile("([0-9]+(?:,[0-9]+)*)(_materials)?\\.png", Pattern.CASE_INSENSITIVE);
    private final Path directory;
    private final Map<String, List<Part>> models;
    private final String decodedModelsHash;
    private final List<Integer> ids;

    public AionGeometry(Path geo) throws IOException {
        directory = geo.toRealPath();
        if (!Files.isDirectory(directory)) throw new IOException("Geometry input is not a directory: " + geo);
        Path mesh = directory.resolve("models.mesh");
        decodedModelsHash = Artifacts.hash(mesh);
        models = readModels(mesh);
        if (!decodedModelsHash.equals(Artifacts.hash(mesh))) throw new IOException("models.mesh changed during decoding");
        TreeSet<Integer> discovered = new TreeSet<>();
        for (Path file : files()) discovered.addAll(mapNames(file));
        ids = List.copyOf(discovered);
    }

    public List<Integer> mapIds() { return ids; }

    public String fingerprint(int mapId, String sharedModelsHash) throws IOException {
        if (!decodedModelsHash.equals(sharedModelsHash)) throw new IOException("Shared models hash does not match decoded input");
        StringBuilder text = new StringBuilder("native-geometry-1\nmap=").append(mapId).append("\nmodels.mesh=").append(sharedModelsHash).append('\n');
        List<Path> relevant = relevant(mapId);
        if (relevant.isEmpty()) throw new IOException("No geometry inputs for map " + mapId);
        for (Path file : relevant) text.append(file.getFileName()).append('=').append(Artifacts.hash(file)).append('\n');
        return Artifacts.hashText(text.toString());
    }

    public MapGeometry load(int mapId) throws IOException {
        if (!ids.contains(mapId)) throw new IOException("Undiscovered map " + mapId);
        List<PlacedPart> statics = new ArrayList<>();
        List<DynamicPlacement> dynamic = new ArrayList<>();
        Bounds bounds = new Bounds();
        int placements = 0, ignored = 0;
        Path world = directory.resolve(mapId + ".geo");
        if (Files.isRegularFile(world)) {
            try (DataInputStream in = input(world)) {
                String name;
                while ((name = name(in)) != null) {
                    placements++;
                    float[] position = floats(in, 3), rotation = floats(in, 9), scale = floats(in, 3);
                    int type = in.readUnsignedByte(), id = in.readShort(), level = in.readUnsignedByte();
                    if (type > 8 || type == 5 && level > 8 || type != 5 && level != 0)
                        throw new IOException("Invalid native entity type/level for " + name);
                    List<Part> parts = models.get(name);
                    if (parts == null) throw new IOException("Map " + mapId + " references missing model " + name);
                    if (parts.isEmpty()) throw new IOException("Map " + mapId + " references empty model " + name);
                    Transform transform = new Transform(rotation, position, scale);
                    Bounds all = new Bounds();
                    int flags = 0;
                    for (Part part : parts) {
                        flags |= part.flags;
                        if (part.vertices.length == 0) continue;
                        Bounds placedBounds = transform.bounds(part.bounds);
                        all.add(placedBounds);
                        if ((part.flags & PHYSICAL) != 0) bounds.add(placedBounds);
                        if (type != 0 || (part.flags & DYNAMIC_FLAGS) != 0) {
                            if (type == 0) dynamic.add(new DynamicPlacement(name, type, id, level, part.flags,
                                    placedBounds.min, placedBounds.max));
                        } else if ((part.flags & PHYSICAL) != 0 && part.indices.length != 0) {
                            statics.add(new PlacedPart(part, transform, placedBounds));
                        } else ignored++;
                    }
                    if (type != 0 && all.present()) dynamic.add(new DynamicPlacement(name, type, id, level, flags, all.min, all.max));
                }
            } catch (IOException e) { throw new IOException("Invalid " + world.getFileName() + ": " + e.getMessage(), e); }
        }
        Path heightFile = null, materialFile = null;
        for (Path file : relevant(mapId)) {
            Matcher matcher = PNG.matcher(file.getFileName().toString());
            if (!matcher.matches()) continue;
            if (matcher.group(2) == null) {
                if (heightFile != null) throw new IOException("Multiple heightmaps for map " + mapId);
                heightFile = file;
            } else {
                if (materialFile != null) throw new IOException("Multiple materialmaps for map " + mapId);
                materialFile = file;
            }
        }
        Heightmap heightmap = heightFile == null ? null : heightmap(heightFile);
        if (materialFile != null) validateMaterials(materialFile, heightmap);
        if (heightmap != null) bounds.add(heightmap.bounds());
        if (!bounds.present() || statics.isEmpty() && heightmap == null)
            throw new IOException("Map " + mapId + " has no static physical/terrain input; dynamic geometry cannot supply a permanent floor");
        // World origin is stable at zero for nonnegative native coordinates. Preserve negative geometry.
        bounds.min[0] = (float)Math.floor(Math.min(0, bounds.min[0]));
        bounds.min[2] = (float)Math.floor(Math.min(0, bounds.min[2]));
        bounds.max[0] = (float)Math.ceil(bounds.max[0]);
        bounds.max[2] = (float)Math.ceil(bounds.max[2]);
        String description = "placements=" + placements + ",staticPhysicalParts=" + statics.size()
                + ",dynamicExcluded=" + dynamic.size() + ",nonPhysicalParts=" + ignored
                + ",terrain=" + (heightmap == null ? "absent(mesh-only)" : heightmap.width + "x" + heightmap.height
                + ",holeSamples=" + heightmap.holes) + ",coordinates=Recast(x,z,y),nativeDoubleSided=true";
        return new MapGeometry(bounds.min, bounds.max, description, statics, heightmap, dynamic);
    }

    private List<Path> files() throws IOException {
        try (var stream = Files.list(directory)) { return stream.filter(Files::isRegularFile)
                .sorted(Comparator.comparing(p -> p.getFileName().toString())).toList(); }
    }

    private List<Path> relevant(int map) throws IOException {
        return files().stream().filter(p -> mapNames(p).contains(map)).toList();
    }

    private static List<Integer> mapNames(Path file) {
        String name = file.getFileName().toString();
        Matcher world = GEO.matcher(name), png = PNG.matcher(name);
        String numbers = world.matches() ? world.group(1) : png.matches() ? png.group(1) : "";
        if (numbers.isEmpty()) return List.of();
        List<Integer> result = new ArrayList<>();
        for (String value : numbers.split(",")) {
            try {
                int id = Integer.parseInt(value);
                if (id <= 0) throw new NumberFormatException();
                result.add(id);
            } catch (NumberFormatException e) { throw new IllegalArgumentException("Invalid map input filename " + name, e); }
        }
        return result;
    }

    private static Map<String, List<Part>> readModels(Path file) throws IOException {
        Map<String, List<Part>> result = new HashMap<>();
        try (DataInputStream in = input(file)) {
            String name;
            while ((name = name(in)) != null) {
                int count = in.readUnsignedByte();
                List<Part> parts = new ArrayList<>();
                for (int i = 0; i < count; i++) {
                    int vertexCount = in.readUnsignedShort();
                    float[] vertices = floats(in, vertexCount * 3);
                    int faces = in.readUnsignedShort(), size = in.readUnsignedByte();
                    if (size != 1 && size != 2) throw new IOException("Invalid index size for model " + name);
                    int[] indices = new int[faces * 3];
                    for (int j = 0; j < indices.length; j++) {
                        indices[j] = size == 1 ? in.readUnsignedByte() : in.readUnsignedShort();
                        if (indices[j] >= vertexCount) throw new IOException("Out-of-range vertex index in model " + name);
                    }
                    int material = in.readUnsignedByte(), flags = in.readUnsignedByte();
                    Bounds bounds = new Bounds();
                    for (int j = 0; j < vertices.length; j += 3) bounds.point(vertices[j], vertices[j + 2], vertices[j + 1]);
                    parts.add(new Part(vertices, indices, material, flags, bounds));
                }
                List<Part> immutable = List.copyOf(parts);
                for (String alias : name.split("\\|", -1))
                    if (alias.isEmpty() || result.putIfAbsent(alias, immutable) != null)
                        throw new IOException("Empty/duplicate model alias " + alias);
            }
        } catch (IOException e) { throw new IOException("Invalid models.mesh: " + e.getMessage(), e); }
        return Map.copyOf(result);
    }

    private static DataInputStream input(Path file) throws IOException {
        return new DataInputStream(new BufferedInputStream(Files.newInputStream(file)));
    }

    private static String name(DataInputStream in) throws IOException {
        int first = in.read();
        if (first == -1) return null;
        int length = first * 256 + in.readUnsignedByte();
        if (length == 0 || length > Short.MAX_VALUE) throw new IOException("Invalid native name length " + length);
        byte[] bytes = new byte[length]; in.readFully(bytes);
        for (byte b : bytes) if (b < 32 || b > 126) throw new IOException("Invalid native ASCII model name");
        return new String(bytes, StandardCharsets.US_ASCII);
    }

    private static float[] floats(DataInputStream in, int count) throws IOException {
        float[] result = new float[count];
        for (int i = 0; i < count; i++) {
            result[i] = in.readFloat();
            if (!Float.isFinite(result[i])) throw new IOException("Non-finite native coordinate/transform");
        }
        return result;
    }

    private static Heightmap heightmap(Path file) throws IOException {
        BufferedImage image = ImageIO.read(file.toFile());
        if (image == null || image.getRaster().getNumBands() != 1
                || !(image.getRaster().getDataBuffer() instanceof DataBufferUShort buffer))
            throw new IOException("Not a native 16-bit grayscale heightmap: " + file.getFileName());
        short[] values = buffer.getData().clone();
        if ((long)image.getWidth() * image.getHeight() != values.length || buffer.getNumBanks() != 1)
            throw new IOException("Unexpected heightmap raster layout: " + file.getFileName());
        return new Heightmap(image.getWidth(), image.getHeight(), values);
    }

    private static void validateMaterials(Path file, Heightmap heightmap) throws IOException {
        BufferedImage image = ImageIO.read(file.toFile());
        if (heightmap == null || image == null || image.getRaster().getNumBands() != 1
                || !(image.getRaster().getDataBuffer() instanceof DataBufferByte)
                || image.getWidth() > heightmap.width || image.getHeight() > heightmap.height)
            throw new IOException("Materialmap requires compatible native terrain dimensions: " + file.getFileName());
    }

    private record Part(float[] vertices, int[] indices, int material, int flags, Bounds bounds) {}
    private record PlacedPart(Part part, Transform transform, Bounds bounds) {}

    /** Typed state-dependent input is retained for future native instance-aware overlays, never baked as floor. */
    public record DynamicPlacement(String model, int type, int id, int level, int flags, float[] min, float[] max) {
        public DynamicPlacement { min = min.clone(); max = max.clone(); }
        @Override public float[] min() { return min.clone(); }
        @Override public float[] max() { return max.clone(); }
    }

    public record Triangles(float[] vertices, int[] indices) {}

    private static final class Bounds {
        final float[] min = {Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY};
        final float[] max = {Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY};
        void point(float x, float y, float z) {
            min[0] = Math.min(min[0], x); min[1] = Math.min(min[1], y); min[2] = Math.min(min[2], z);
            max[0] = Math.max(max[0], x); max[1] = Math.max(max[1], y); max[2] = Math.max(max[2], z);
        }
        boolean present() { return Float.isFinite(min[0]); }
        void add(Bounds other) { if (other.present()) { point(other.min[0], other.min[1], other.min[2]); point(other.max[0], other.max[1], other.max[2]); } }
        boolean intersects(float x1, float z1, float x2, float z2) { return max[0] >= x1 && min[0] <= x2 && max[2] >= z1 && min[2] <= z2; }
    }

    private static final class Transform {
        final float[] matrix = new float[12];
        Transform(float[] rotation, float[] position, float[] scale) {
            for (int row = 0; row < 3; row++) {
                for (int column = 0; column < 3; column++) matrix[row * 4 + column] = rotation[row * 3 + column] * scale[column];
                matrix[row * 4 + 3] = position[row];
            }
        }
        void point(float x, float y, float z, float[] output, int offset) {
            output[offset] = matrix[0] * x + matrix[1] * y + matrix[2] * z + matrix[3];
            output[offset + 1] = matrix[8] * x + matrix[9] * y + matrix[10] * z + matrix[11];
            output[offset + 2] = matrix[4] * x + matrix[5] * y + matrix[6] * z + matrix[7];
        }
        Bounds bounds(Bounds local) throws IOException {
            Bounds result = new Bounds(); float[] point = new float[3];
            // Part bounds are stored in Recast order; convert back before native transform.
            for (int i = 0; i < 8; i++) {
                point((i & 1) == 0 ? local.min[0] : local.max[0],
                        (i & 2) == 0 ? local.min[2] : local.max[2],
                        (i & 4) == 0 ? local.min[1] : local.max[1], point, 0);
                for (float value : point) if (!Float.isFinite(value)) throw new IOException("Transform overflows native coordinates");
                result.point(point[0], point[1], point[2]);
            }
            return result;
        }
    }

    private static final class Heightmap {
        final int width, height, holes;
        final short[] samples;
        Heightmap(int width, int height, short[] samples) {
            this.width = width; this.height = height; this.samples = samples;
            int count = 0; for (short value : samples) if (value == -1) count++; holes = count;
        }
        float z(int x, int y) {
            if (x == 0 || y == 0 || x == width || y == height) return 0;
            int value = Short.toUnsignedInt(samples[y + x * height]);
            return value == 65535 ? Float.NaN : value / 32f;
        }
        Bounds bounds() {
            Bounds result = new Bounds(); result.point(0, 0, 0); result.point(width * 2f, 0, height * 2f);
            for (int x = 1; x < width; x++) for (int y = 1; y < height; y++) {
                float z = z(x, y); if (Float.isFinite(z)) result.point(x * 2f, z, y * 2f);
            }
            return result;
        }
        void append(Buffer target, float x1, float z1, float x2, float z2) {
            int startX = Math.max(0, Math.min(width, (int)Math.floor(x1 / 2)));
            int startY = Math.max(0, Math.min(height, (int)Math.floor(z1 / 2)));
            int endX = (int)Math.max(0, Math.min(width, Math.floor((double)x2 / 2) + 1));
            int endY = (int)Math.max(0, Math.min(height, Math.floor((double)z2 / 2) + 1));
            float[] points = new float[12];
            for (int x = startX; x < endX; x++) for (int y = startY; y < endY; y++) {
                float p2 = z(x, y + 1), p3 = z(x + 1, y);
                if (!Float.isFinite(p2) || !Float.isFinite(p3)) continue;
                float p1 = z(x, y), p4 = z(x + 1, y + 1);
                set(points, 0, x * 2f, p1, y * 2f); set(points, 3, x * 2f, p2, (y + 1) * 2f);
                set(points, 6, (x + 1) * 2f, p3, y * 2f); set(points, 9, (x + 1) * 2f, p4, (y + 1) * 2f);
                if (Float.isFinite(p1)) target.triangle(points, 0, 3, 6);
                if (Float.isFinite(p4)) target.triangle(points, 3, 6, 9);
            }
        }
    }

    private static void set(float[] array, int offset, float x, float y, float z) { array[offset] = x; array[offset + 1] = y; array[offset + 2] = z; }

    /** Immutable placed-mesh BVH avoids a full map scan/rebuild for every tile. */
    private static final class Index {
        final Bounds bounds = new Bounds();
        final Index left, right;
        final List<PlacedPart> leaf;
        Index(List<PlacedPart> input) {
            for (PlacedPart part : input) bounds.add(part.bounds);
            if (input.size() <= 12) { leaf = List.copyOf(input); left = right = null; }
            else {
                int axis = bounds.max[0] - bounds.min[0] >= bounds.max[2] - bounds.min[2] ? 0 : 2;
                var ordered = new ArrayList<>(input);
                ordered.sort(Comparator.comparingDouble(p -> (double)p.bounds.min[axis] + p.bounds.max[axis]));
                int half = ordered.size() / 2;
                left = new Index(ordered.subList(0, half)); right = new Index(ordered.subList(half, ordered.size())); leaf = null;
            }
        }
        void append(Buffer output, float x1, float z1, float x2, float z2) {
            if (!bounds.present() || !bounds.intersects(x1, z1, x2, z2)) return;
            if (leaf == null) { left.append(output, x1, z1, x2, z2); right.append(output, x1, z1, x2, z2); return; }
            float[] triangle = new float[9];
            for (PlacedPart placed : leaf) {
                if (!placed.bounds.intersects(x1, z1, x2, z2)) continue;
                Part part = placed.part;
                for (int i = 0; i < part.indices.length; i += 3) {
                    for (int vertex = 0; vertex < 3; vertex++) {
                        int offset = part.indices[i + vertex] * 3;
                        placed.transform.point(part.vertices[offset], part.vertices[offset + 1], part.vertices[offset + 2], triangle, vertex * 3);
                    }
                    if (Math.max(triangle[0], Math.max(triangle[3], triangle[6])) < x1 || Math.min(triangle[0], Math.min(triangle[3], triangle[6])) > x2
                            || Math.max(triangle[2], Math.max(triangle[5], triangle[8])) < z1 || Math.min(triangle[2], Math.min(triangle[5], triangle[8])) > z2) continue;
                    output.triangle(triangle, 0, 3, 6);
                }
            }
        }
    }

    private static final class Buffer {
        float[] values = new float[1024]; int size;
        void triangle(float[] data, int a, int b, int c) {
            double abx = data[b] - data[a], aby = data[b + 1] - data[a + 1], abz = data[b + 2] - data[a + 2];
            double acx = data[c] - data[a], acy = data[c + 1] - data[a + 1], acz = data[c + 2] - data[a + 2];
            double nx = aby * acz - abz * acy, ny = abz * acx - abx * acz, nz = abx * acy - aby * acx;
            if (nx == 0 && ny == 0 && nz == 0) return;
            // Native BIHNode folds normals >90 degrees back into [0,90]: physical floors are double-sided.
            if (ny < 0) { int swap = b; b = c; c = swap; }
            if (size > Integer.MAX_VALUE - 9) throw new IllegalArgumentException("Tile geometry exceeds Java array bounds");
            if (size + 9 > values.length) values = Arrays.copyOf(values, (int)Math.min(Integer.MAX_VALUE - 8L, Math.max(size + 9L, values.length * 2L)));
            System.arraycopy(data, a, values, size, 3); System.arraycopy(data, b, values, size + 3, 3); System.arraycopy(data, c, values, size + 6, 3); size += 9;
        }
        Triangles finish() {
            int[] indices = new int[size / 3]; for (int i = 0; i < indices.length; i++) indices[i] = i;
            return new Triangles(Arrays.copyOf(values, size), indices);
        }
    }

    public static final class MapGeometry {
        private final float[] min, max;
        private final String description;
        private final Index index;
        private final Heightmap heightmap;
        private final List<DynamicPlacement> dynamic;
        private MapGeometry(float[] min, float[] max, String description, List<PlacedPart> statics, Heightmap heightmap, List<DynamicPlacement> dynamic) {
            this.min = min.clone(); this.max = max.clone(); this.description = description;
            this.index = new Index(statics); this.heightmap = heightmap; this.dynamic = List.copyOf(dynamic);
        }
        public float[] min() { return min.clone(); }
        public float[] max() { return max.clone(); }
        public String description() { return description; }
        public List<DynamicPlacement> dynamics() { return dynamic; }
        public Triangles triangles(float minX, float minZ, float maxX, float maxZ) {
            if (!Float.isFinite(minX) || !Float.isFinite(minZ) || !Float.isFinite(maxX) || !Float.isFinite(maxZ) || minX > maxX || minZ > maxZ)
                throw new IllegalArgumentException("Invalid tile/border bounds");
            Buffer output = new Buffer(); index.append(output, minX, minZ, maxX, maxZ);
            if (heightmap != null) heightmap.append(output, minX, minZ, maxX, maxZ);
            return output.finish();
        }
    }
}
