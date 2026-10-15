package com.aionemu.gameserver.services.playerbot;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.recast4j.detour.MeshData;
import org.recast4j.detour.MeshHeader;
import org.recast4j.detour.NavMesh;
import org.recast4j.detour.NavMeshParams;
import org.recast4j.detour.Poly;
import org.recast4j.detour.io.MeshDataReader;

/**
 * Lazy shared, immutable-after-publication tile windows. The supplied executor must
 * dispatch asynchronously; requests perform no filesystem work. A lease pins a
 * spatial working set, not a mutable query. Callers must never add/remove tiles or alter flags
 * on the returned mesh, and must close every lease after extracting route points.
 * Query nodes/corridors are separately bounded by the navigation owner.
 */
final class PlayerBotNavmeshStore implements AutoCloseable {
    enum Status { WAITING, READY, UNAVAILABLE }
    record Result(Status status, Lease lease, String reason) {}
    private static final int MAX_PENDING = 64, MAX_ENTRIES = 256, MAX_PROPERTIES = 1_048_576;
    private static final int MAX_MANIFEST = 65_536;
    // Includes the bounded catalog/properties decoding and admission metadata.
    private static final long CONTROL_BYTES = 32L * 1024 * 1024;
    private static final long RETRY_NANOS = 1_000_000_000L;
    private static final String COMPLETE = "BUILT_REQUIRES_RUNTIME_VALIDATION";
    /** Conservative sizes for this JVM, including reference arrays/growth slack.
     * Unknown VMs retain the original uncompressed bounds. No heap-limit changes. */
    private static final class Layout {
        static final long POLYGON, DETAIL, BV_NODE, LINK;
        static {
            long polygon=256,detail=64,bv=192,link=96;
            try {
                var vm=java.lang.management.ManagementFactory.getPlatformMXBean(com.sun.management.HotSpotDiagnosticMXBean.class);
                int alignment=Integer.parseInt(vm.getVMOption("ObjectAlignmentInBytes").getValue());
                if(alignment<8 || alignment>256 || (alignment&(alignment-1))!=0)throw new IllegalArgumentException("Unknown object alignment");
                int reference=Boolean.parseBoolean(vm.getVMOption("UseCompressedOops").getValue())?4:8;
                int header=Boolean.parseBoolean(vm.getVMOption("UseCompressedClassPointers").getValue())?12:16;
                try {if(Boolean.parseBoolean(vm.getVMOption("UseCompactObjectHeaders").getValue()))header=8;}catch(IllegalArgumentException unavailable) { /* older HotSpot */ }
                // Eight extra bytes per object cover field alignment/padding.
                polygon=aligned(header+2*reference+16+8,alignment)+2*aligned(header+4+24,alignment)+reference;
                detail=aligned(header+16+8,alignment)+reference;
                bv=aligned(header+2*reference+4+8,alignment)+2*aligned(header+4+12,alignment)+reference;
                link=aligned(header+8+20+8,alignment)+2*reference;
            } catch(RuntimeException | LinkageError unavailable) { /* conservative portable reservation */ }
            POLYGON=polygon;DETAIL=detail;BV_NODE=bv;LINK=link;
        }
        private static long aligned(long value,int alignment){return (value+alignment-1)/alignment*alignment;}
    }
    private final Path root;
    private final long memoryBytes;
    private final Consumer<Runnable> loaderExecutor;
    private final LinkedHashMap<Window, Entry> entries = new LinkedHashMap<>(16, .75f, true);
    private final ArrayDeque<Entry> pending = new ArrayDeque<>();
    private boolean scheduled, closed;
    private long epoch, residentBytes, reservedBytes, sequence;
    private Catalog catalog;
    private String catalogFailure;
    private PlayerBotAnimatedScene animatedScene;
    private String animatedSceneHash;

    // Shared 128m cells with a surrounding tile apron. Native tile coordinates
    // still come exclusively from the authenticated map manifest, never this key.
    private record Window(int map, int x, int y, int radius) {}
    private static final class Entry {
        final int map;
        final Window window;
        final long epoch;
        final java.util.Set<Request> requests = new java.util.LinkedHashSet<>();
        NavMesh mesh;
        PlayerBotNavmeshTopology topology;
        String fingerprint, failure, waiting = "queued for navmesh loading";
        long bytes, requiredBytes, retry;
        int pins;
        boolean retired, blocked, handoff, legacy;
        Entry(Window window, long epoch) { this.map = window.map; this.window=window; this.epoch = epoch; }
        Entry(int map,long epoch){this(new Window(map,0,0,0),epoch);}
        boolean ready(){return mesh!=null || topology!=null;}
    }
    private record Catalog(Path root, Properties report, String hash) {}
    private record Spec(Path directory, Properties manifest, String hash, String fingerprint,
                        int nx, int ny, float width, float[] origin, float[] maximum) {}
    private record Tile(int x, int y, Path file, Path receipt, String receiptHash,
                        String hash, long size, Header header) {}
    private record Header(int polygons, int vertices, int links, int detailVertices,
                          int detailTriangles, int bvNodes) {}
    private record Range(int x0,int y0,int x1,int y1) {
        long count(){return (long)(x1-x0)*(y1-y0);}
    }
    private record Plan(Spec spec, List<Tile> tiles, long bytes) {}
    private record Portal(float start, float end) {}
    private static final class Sides {
        final List<List<Portal>> sides = List.of(new ArrayList<>(),new ArrayList<>(),new ArrayList<>(),new ArrayList<>());
    }
    private static final class CapacityWait extends IOException {
        private static final long serialVersionUID = 1L;
        final long bytes;
        CapacityWait(long bytes) { super("navmesh capacity currently pinned"); this.bytes=bytes; }
    }

    PlayerBotNavmeshStore(Path root, long memoryBytes, Consumer<Runnable> loaderExecutor) {
        this.root = Objects.requireNonNull(root).toAbsolutePath().normalize();
        if (memoryBytes < 1) throw new IllegalArgumentException("Navmesh memory allowance must be positive");
        this.memoryBytes = memoryBytes;
        this.loaderExecutor = Objects.requireNonNull(loaderExecutor);
        residentBytes = Math.min(CONTROL_BYTES, memoryBytes);
    }

    Result acquire(int mapId) {
        Result result;
        synchronized (this) {
            if (closed) return unavailable("navmesh store closed");
            if (memoryBytes < CONTROL_BYTES) return unavailable("navmesh memory allowance below bounded store metadata requirement: " + CONTROL_BYTES);
            if (mapId < 1) return unavailable("invalid native map id");
            Window key=new Window(mapId,0,0,0); // Full-map verification API; runtime uses request().
            Entry entry = entries.get(key);
            if (entry != null && entry.mesh != null) {
                entry.pins++;
                return new Result(Status.READY, new Lease(entry), "ready");
            }
            if (catalogFailure != null) return unavailable(catalogFailure);
            if (entry != null && entry.failure != null) return unavailable(entry.failure);
            if (entry == null) {
                if (pending.size() >= MAX_PENDING) return waiting("navmesh admission queue full; retry");
                trimTerminalEntries();
                if (entries.size() >= MAX_ENTRIES) return waiting("navmesh map cache full; retry");
                entry = new Entry(mapId, epoch); entry.legacy=true;
                entries.put(key, entry);
                pending.addLast(entry);
            }
            result = waiting(entry.waiting);
        }
        kick();
        return result;
    }

    /** Cancellable demand survives queue waits; unclaimed publication cannot be
     * evicted before its waiting caller receives it. No polling-time disk I/O. */
    Request request(int map,float x,float y,int radius) {
        if(map<1 || !Float.isFinite(x) || !Float.isFinite(y) || Math.abs(x)>1_000_000
                || Math.abs(y)>1_000_000 || radius<1 || radius>8)
            throw new IllegalArgumentException("Invalid navmesh window request");
        Request request;
        synchronized(this){
            request=new Request(new Window(map,(int)Math.floor(x/128),(int)Math.floor(y/128),radius),++sequence);
            register(request);
        }
        kick();return request;
    }

    Request requestTopology(int map){
        if(map<1)throw new IllegalArgumentException("Invalid topology map");
        Request request;
        synchronized(this){request=new Request(new Window(map,0,0,-1),++sequence);register(request);}
        kick();return request;
    }

    private void register(Request request){
        if(request.cancelled || request.entry!=null || closed || catalogFailure!=null)return;
        Entry entry=entries.get(request.window);
        if(entry==null){
            if(pending.size()>=MAX_PENDING)return;
            trimTerminalEntries();
            if(entries.size()>=MAX_ENTRIES)return;
            entry=new Entry(request.window,epoch);entries.put(request.window,entry);pending.addLast(entry);
        }
        request.entry=entry;entry.requests.add(request);
    }

    final class Request implements AutoCloseable {
        private final Window window;
        private final long order;
        private Entry entry;
        private Lease lease;
        private boolean cancelled;
        private Request(Window window,long order){this.window=window;this.order=order;}
        Result poll(){
            Result result;
            synchronized(PlayerBotNavmeshStore.this){
                if(cancelled || closed)return unavailable("navmesh request closed");
                if(memoryBytes<CONTROL_BYTES)return unavailable("navmesh allowance below store metadata requirement");
                if(catalogFailure!=null)return unavailable(catalogFailure);
                register(this);
                if(entry==null)result=waiting("navmesh admission queue full; retry");
                else if(entry.retired)result=unavailable("navmesh resource reloaded");
                else if(entry.failure!=null)result=unavailable(entry.failure);
                else if(lease!=null)return new Result(Status.READY,lease,"ready");
                else if(entry.ready() && (entry.handoff || !olderBlocked(order,entry))){
                    entry.pins++;entry.handoff=false;entry.requests.remove(this);
                    lease=new Lease(entry);return new Result(Status.READY,lease,"ready");
                } else result=waiting(entry.ready()?"yielding navmesh memory to older tile demand":entry.waiting);
            }
            kick();return result;
        }
        @Override public void close(){
            synchronized(PlayerBotNavmeshStore.this){
                if(cancelled)return;cancelled=true;
                if(lease!=null){lease.close();lease=null;}
                if(entry!=null){
                    entry.requests.remove(this);
                    if(entry.requests.isEmpty()){
                        entry.handoff=false;
                        if(!entry.ready() && entry.failure==null && !entry.legacy){
                            entry.retired=true;pending.remove(entry);entries.remove(entry.window,entry);
                        }
                    }
                }
            }
            kick();
        }
    }

    private boolean olderBlocked(long order,Entry except){
        for(Entry entry:entries.values())if(entry!=except && entry.blocked && !entry.retired)
            for(Request request:entry.requests)if(request.order<order)return true;
        return false;
    }

    /** No I/O and no new threads. Existing leases survive close/reset until released. */
    @Override public synchronized void close() {
        closed = true;
        invalidate();
    }

    /** Explicit resource reload; never runs automatically for a failed/changed package. */
    void reset() {
        synchronized (this) { invalidate(); closed = false; }
    }

    private void invalidate() {
        epoch++;
        for (Entry entry : entries.values()) {
            entry.retired = true;
            if (entry.ready() && entry.pins == 0) {
                residentBytes -= entry.bytes;
                entry.mesh = null;
                entry.topology = null;
            }
        }
        entries.clear(); pending.clear(); catalog = null; catalogFailure = null;
        // A running loader retains its reservation until its finally block.
    }

    synchronized long accountedBytes() { return residentBytes + reservedBytes; }
    synchronized int pendingCount() { return pending.size(); }

    final class Lease implements AutoCloseable {
        private Entry entry;
        private Lease(Entry entry) { this.entry = entry; }
        NavMesh mesh() {
            synchronized (PlayerBotNavmeshStore.this) {
                if (entry == null) throw new IllegalStateException("Navmesh lease closed");
                return entry.mesh;
            }
        }
        String fingerprint() {
            synchronized (PlayerBotNavmeshStore.this) {
                if (entry == null) throw new IllegalStateException("Navmesh lease closed");
                return entry.fingerprint;
            }
        }
        PlayerBotNavmeshTopology topology(){
            synchronized(PlayerBotNavmeshStore.this){
                if(entry==null || entry.topology==null)throw new IllegalStateException("Topology lease closed or wrong kind");
                return entry.topology;
            }
        }
        @Override public void close() {
            synchronized (PlayerBotNavmeshStore.this) {
                if (entry == null) return;
                Entry released = entry; entry = null;
                if (--released.pins == 0 && released.retired) {
                    residentBytes -= released.bytes;
                    released.mesh = null;
                    released.topology = null;
                }
                // Only an eviction opportunity wakes capacity waiters. A partial
                // pin release cannot fund them and must not restart preflight I/O.
                if(released.pins==0)for (Entry next : pending) if(next.blocked)next.retry = 0;
            }
            kick();
        }
    }

    private void trimTerminalEntries() {
        Iterator<Entry> iterator = entries.values().iterator();
        while (entries.size() >= MAX_ENTRIES && iterator.hasNext()) {
            Entry entry = iterator.next();
            if (entry.requests.isEmpty() && entry.pins==0 && (entry.failure!=null || entry.ready())) {
                if(entry.ready()){residentBytes-=entry.bytes;entry.mesh=null;entry.topology=null;}
                entry.retired=true;iterator.remove();
            }
        }
    }

    private void kick() {
        final long generation;
        synchronized (this) {
            if (closed || scheduled || catalogFailure != null || pending.isEmpty()) return;
            long now = System.nanoTime();
            if (pending.stream().noneMatch(e -> e.retry <= now)) return;
            scheduled = true; generation = epoch;
        }
        Thread submitter = Thread.currentThread();
        AtomicBoolean submitting = new AtomicBoolean(true);
        try {
            loaderExecutor.accept(() -> {
                if (submitting.get() && Thread.currentThread() == submitter) {
                    synchronized (this) {
                        scheduled = false;
                        if (generation == epoch) catalogFailure = "navmesh loader requires an asynchronous executor";
                    }
                    return;
                }
                drain(generation);
            });
        } catch (RuntimeException | Error e) {
            synchronized (this) {
                scheduled = false;
                if (generation == epoch) catalogFailure = reason("navmesh executor rejected work", e);
            }
        } finally { submitting.set(false); }
    }

    private void drain(long generation) {
        try {
            Catalog current;
            synchronized (this) { if (closed || epoch != generation) return; current = catalog; }
            if (current == null) {
                current = readCatalog();
                synchronized (this) { if (closed || epoch != generation) return; catalog = current; }
            }
            int attempts;
            synchronized (this) { attempts = pending.size(); }
            while (attempts-- > 0) {
                Entry entry;
                synchronized (this) {
                    if (closed || epoch != generation) return;
                    entry = pending.pollFirst(); if (entry == null) break;
                    if (entry.retry > System.nanoTime()) { pending.addLast(entry); continue; }
                }
                load(entry, current);
            }
        } catch (Exception e) {
            synchronized (this) {
                if (!closed && epoch == generation) catalogFailure = reason("navmesh catalog unavailable", e);
            }
        } finally {
            synchronized (this) { scheduled = false; }
            kick();
        }
    }

    private void load(Entry entry, Catalog current) {
        long[] reservation = {0};
        try {
            Spec spec = readSpec(current, entry.map);
            if(entry.window.radius==-1){loadTopology(entry,current,spec,reservation);return;}
            // Covers the preflight side-count grid, tile descriptors, hashes and
            // properties. No large tile/mesh allocation occurs before reservation.
            Range range=range(spec,entry.window);
            long base = entry.legacy?metadataBytes(spec):metadataBytes(spec,range);
            synchronized (this) {
                if (closed || entry.retired || entry.epoch != epoch) return;
                long needed = Math.max(base, entry.requiredBytes);
                checkMapAllowance(needed);
                if (!reserve(needed,entry)) { defer(entry, needed); return; }
                reservation[0] = needed;
            }
            Plan plan = preflight(current, spec, entry, reservation);
            synchronized (this) {
                if (closed || entry.retired || entry.epoch != epoch) return;
                entry.requiredBytes = plan.bytes;
                checkMapAllowance(plan.bytes);
                long extra = Math.max(0, plan.bytes - reservation[0]);
                if (!reserve(extra,entry)) { defer(entry, plan.bytes); return; }
                reservation[0] += extra;
            }
            NavMesh mesh = decode(current, plan, entry.epoch);
            synchronized (this) {
                if (closed || entry.retired || entry.epoch != epoch) return;
                entry.mesh = mesh; entry.bytes = reservation[0]; entry.fingerprint = spec.fingerprint;
                entry.blocked=false;entry.handoff=!entry.requests.isEmpty();
                residentBytes += reservation[0]; reservedBytes -= reservation[0]; reservation[0] = 0;
            }
        } catch (CapacityWait e) {
            synchronized (this) { if (!closed && !entry.retired && entry.epoch == epoch) defer(entry,e.bytes); }
        } catch (Exception e) {
            synchronized (this) {
                if (!closed && !entry.retired && entry.epoch == epoch) {
                    entry.failure = reason("map " + entry.map + " navmesh unavailable", e);entry.blocked=false;
                }
            }
        } finally {
            synchronized (this) { reservedBytes -= reservation[0]; }
        }
    }

    private void loadTopology(Entry entry,Catalog current,Spec spec,long[] reservation)throws IOException{
        Path folder=inside(current.root,current.root.resolve("topology"));
        Properties index=properties(boundedBytes(inside(current.root,folder.resolve("topology.properties")),MAX_PROPERTIES));
        require("aion-topology-1".equals(index.getProperty("format")) && current.hash.equals(index.getProperty("report.sha256")),"topology/catalog mismatch");
        String key="map."+entry.map;
        require(spec.fingerprint.equals(index.getProperty(key+".fingerprint")),"topology/map mismatch");
        String digest=hashValue(index,key+".sha256");Path file=inside(current.root,folder.resolve(entry.map+".pbtopology"));
        long size=Files.size(file);require(size>0 && size<=256L*1024*1024,"topology file size");
        Path animatedFolder=inside(current.root,current.root.resolve("animated"));
        Properties animatedIndex=properties(boundedBytes(inside(current.root,animatedFolder.resolve("animated.properties")),MAX_PROPERTIES));
        require("aion-animated-navigation-1".equals(animatedIndex.getProperty("format")) && current.hash.equals(animatedIndex.getProperty("report.sha256"))
            && PlayerBotNavmeshTopology.hash(folder.resolve("topology.properties")).equals(animatedIndex.getProperty("topology.sha256")),"animated/topology/catalog mismatch");
        String sceneHash=hashValue(animatedIndex,"scene.sha256");Path sceneFile=inside(current.root,animatedFolder.resolve("animated-scene.json"));
        if(animatedScene==null){
            long sceneSize=Files.size(sceneFile);require(sceneSize>0 && sceneSize<=16L*1024*1024,"animated scene size");long sceneBytes=Math.addExact(1_048_576,Math.multiplyExact(sceneSize,8));
            synchronized(this){if(closed || entry.retired || entry.epoch!=epoch)return;checkMapAllowance(sceneBytes);if(!reserve(sceneBytes,entry)){defer(entry,sceneBytes);return;}reservation[0]=sceneBytes;}
            var scene=PlayerBotAnimatedScene.read(sceneFile,sceneHash);
            synchronized(this){if(closed || entry.retired || entry.epoch!=epoch)return;animatedScene=scene;animatedSceneHash=sceneHash;residentBytes+=sceneBytes;reservedBytes-=sceneBytes;reservation[0]=0;}
        }
        require(sceneHash.equals(animatedSceneHash),"animated scene changed after publication");
        Path ridesFile=inside(current.root,animatedFolder.resolve(entry.map+".pbrides"));long rideSize=Files.size(ridesFile);require(rideSize>0 && rideSize<=16L*1024*1024,"animated dock size");
        long needed=Math.addExact(131072,Math.addExact(Math.multiplyExact(size,3),Math.multiplyExact(rideSize,8)));
        synchronized(this){
            if(closed || entry.retired || entry.epoch!=epoch)return;
            checkMapAllowance(needed);if(!reserve(needed,entry)){defer(entry,needed);return;}reservation[0]=needed;
        }
        var graph=PlayerBotNavmeshTopology.read(file,spec.fingerprint,digest);
        require(graph.bytes()<=needed,"topology allocation reservation");
        graph.animated(PlayerBotAnimatedLinks.read(ridesFile,hashValue(animatedIndex,key+".sha256"),animatedScene,entry.map,graph.nodes));
        synchronized(this){
            if(closed || entry.retired || entry.epoch!=epoch)return;
            entry.topology=graph;entry.bytes=needed;entry.fingerprint=spec.fingerprint;
            entry.blocked=false;entry.handoff=!entry.requests.isEmpty();
            residentBytes+=needed;reservedBytes-=needed;reservation[0]=0;
        }
    }

    private void defer(Entry entry, long bytes) {
        entry.requiredBytes = bytes;
        entry.blocked=true;
        entry.waiting = "navmesh memory pinned by active queries; retry";
        entry.retry = System.nanoTime() + RETRY_NANOS;
        pending.addLast(entry);
    }

    private void checkMapAllowance(long bytes) throws IOException {
        if (bytes > memoryBytes-CONTROL_BYTES)
            throw new IOException("map exceeds navmesh memory allowance including metadata: " + bytes + " + " + CONTROL_BYTES + " > " + memoryBytes);
    }

    private synchronized void reservePreflight(Entry entry,long[] reservation,long bytes) throws IOException {
        if (closed || entry.retired || entry.epoch != epoch) throw new IOException("resource load cancelled");
        checkMapAllowance(bytes);
        long extra=Math.max(0,bytes-reservation[0]);
        if (!reserve(extra,entry)) throw new CapacityWait(bytes);
        reservation[0]+=extra;
    }

    /** Caller holds monitor; eviction never mutates a leased/visible NavMesh. */
    private boolean reserve(long bytes,Entry requester) {
        if(bytes==0)return true;
        // Do not let a later loader repeatedly consume memory released for an
        // older blocked request. Existing leases run to completion unchanged.
        long order=requester.requests.stream().mapToLong(r->r.order).min().orElse(Long.MAX_VALUE);
        if(olderBlocked(order,requester))return false;
        return reserve(bytes);
    }

    private boolean reserve(long bytes) {
        if (bytes > memoryBytes - reservedBytes) return false;
        Iterator<Entry> iterator = entries.values().iterator();
        while (bytes > memoryBytes - residentBytes - reservedBytes && iterator.hasNext()) {
            Entry victim = iterator.next();
            if (!victim.ready() || victim.pins != 0 || victim.handoff) continue;
            residentBytes -= victim.bytes; victim.mesh = null;victim.topology=null;victim.bytes=0;
            if(victim.requests.isEmpty()){victim.retired = true; iterator.remove();}
            else {victim.retry=0;victim.blocked=false;pending.addLast(victim);}
        }
        if (bytes > memoryBytes - residentBytes - reservedBytes) return false;
        reservedBytes += bytes;
        return true;
    }

    private Catalog readCatalog() throws IOException {
        Path canonical = root.toRealPath();
        require(canonical.toString().length() <= 4096,"navmesh root path too long");
        Path report = inside(canonical, canonical.resolve("report.properties"));
        byte[] bytes = boundedBytes(report, MAX_PROPERTIES);
        Properties p = properties(bytes);
        require(hashValue(p, "tool.sha256") != null, "missing tool provenance");
        value(p, "settings"); value(p, "world.bounds");
        return new Catalog(canonical, p, hash(bytes));
    }

    private Spec readSpec(Catalog current, int map) throws IOException {
        String key = "map." + map;
        require(COMPLETE.equals(current.report.getProperty(key + ".status")), "map absent or incomplete in navmesh report");
        Path advertised = Path.of(value(current.report, key + ".directory"));
        Path dir = inside(current.root, advertised.isAbsolute() ? advertised : current.root.resolve(advertised));
        String fingerprint = dir.getFileName().toString();
        require(isHash(fingerprint) && dir.getParent().equals(current.root.resolve(Integer.toString(map))), "invalid map revision directory");
        Path manifestFile = inside(current.root, dir.resolve("map.properties"));
        byte[] bytes = boundedBytes(manifestFile, MAX_MANIFEST); Properties p = properties(bytes);
        require("aion-detour-1".equals(p.getProperty("format")) && "1.5.8".equals(p.getProperty("recast4j"))
                && COMPLETE.equals(p.getProperty("status")), "incomplete or incompatible navmesh manifest");
        for (String name : List.of("tool.sha256", "settings", "world.bounds"))
            require(value(current.report, name).equals(value(p, name)), "manifest/report provenance mismatch: " + name);
        require(fingerprint.equals(hashValue(p, "fingerprint")), "revision fingerprint mismatch");
        String input = hashValue(p, "input");
        require(fingerprint.equals(hash((value(p,"tool.sha256") + value(p,"settings") + input + value(p,"world.bounds"))
                .getBytes(java.nio.charset.StandardCharsets.UTF_8))), "invalid derived fingerprint");
        int nx = integer(p,"tiles.x"), ny = integer(p,"tiles.y");
        require(nx > 0 && ny > 0 && (long)nx * ny <= 1_000_000, "invalid map tile count");
        require(integer(p,"tiles.processed") == (long)nx * ny && (nx + "x" + ny).equals(value(current.report,key + ".tiles")), "incomplete map receipts");
        float width = Float.parseFloat(value(p,"tile.metres"));
        require(Float.isFinite(width) && width > 0 && width <= 1024, "invalid tile width");
        float[] origin = vector(value(p,"origin")), maximum = vector(value(p,"bounds.max"));
        for (int axis = 0; axis < 3; axis++) require(maximum[axis] >= origin[axis], "inverted map bounds");
        require(nx == (int)Math.ceil((maximum[0]-origin[0])/width) && ny == (int)Math.ceil((maximum[2]-origin[2])/width), "grid/bounds mismatch");
        return new Spec(dir,p,hash(bytes),fingerprint,nx,ny,width,origin,maximum);
    }

    private static Range range(Spec spec,Window window) throws IOException {
        if(window.radius==0)return new Range(0,0,spec.nx,spec.ny);
        float margin=64*window.radius;
        int x0=Math.max(0,(int)Math.floor((window.x*128-margin-spec.origin[0])/spec.width));
        int y0=Math.max(0,(int)Math.floor((window.y*128-margin-spec.origin[2])/spec.width));
        int x1=Math.min(spec.nx,(int)Math.ceil(((window.x+1)*128+margin-spec.origin[0])/spec.width));
        int y1=Math.min(spec.ny,(int)Math.ceil(((window.y+1)*128+margin-spec.origin[2])/spec.width));
        require(x0<x1 && y0<y1,"requested window outside native map bounds");
        return new Range(x0,y0,x1,y1);
    }

    private static long metadataBytes(Spec spec,Range range) {
        return Math.addExact(131_072L,Math.multiplyExact(range.count(),
                2048L + (spec.directory.toString().length()+64L)*8));
    }

    /** Full-map verifier retains its separate conservative metadata reservation. */
    private static long metadataBytes(Spec spec) {
        return Math.addExact(2_097_152L,Math.multiplyExact((long)spec.nx*spec.ny,
                2048L + (spec.directory.toString().length()+64L)*8));
    }

    private Plan preflight(Catalog current, Spec spec, Entry entry, long[] reservation) throws IOException {
        List<Tile> tiles = new ArrayList<>();
        Map<Integer,Sides> portals = new HashMap<>();
        Range range=range(spec,entry.window);
        long estimate = entry.legacy?metadataBytes(spec):metadataBytes(spec,range);
        long encodedPeak = 0;
        for (int y = range.y0; y < range.y1; y++) for (int x = range.x0; x < range.x1; x++) {
            synchronized (this) { if (closed || entry.retired || epoch != entry.epoch) throw new IOException("resource load cancelled"); }
            Path receipt = inside(current.root,spec.directory.resolve(x + "_" + y + ".properties"));
            byte[] receiptBytes = boundedBytes(receipt,16_384); Properties p = properties(receiptBytes);
            Path candidate = spec.directory.resolve(x + "_" + y + ".navtile");
            if ("EMPTY".equals(p.getProperty("status"))) {
                tiles.add(new Tile(x,y,candidate,receipt,hash(receiptBytes),null,0,null));
                continue;
            }
            require("BUILT".equals(p.getProperty("status")), "unbuilt tile receipt");
            Path file = inside(current.root,candidate);
            long size = Files.size(file);
            require(size >= 100 && size <= Integer.MAX_VALUE - 8L, "invalid encoded tile size");
            Header header;
            MessageDigest payload;
            try {payload=MessageDigest.getInstance("SHA-256");}
            catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
            String expectedHash=hashValue(p,"sha256");
            try (InputStream in = new java.security.DigestInputStream(Files.newInputStream(file),payload)) {
                header = header(in.readNBytes(100),size,x,y,spec);
                long decoded = 4096L + header.vertices*16L + header.polygons*(Layout.POLYGON+Layout.DETAIL+8)
                        + header.detailVertices*16L + header.detailTriangles*24L + header.bvNodes*Layout.BV_NODE;
                estimate = Math.addExact(estimate,decoded);
                encodedPeak = Math.max(encodedPeak,Math.multiplyExact(size,4L));
                reservePreflight(entry,reservation,Math.addExact(estimate,encodedPeak));
                byte[] vertices = in.readNBytes(header.vertices * 12);
                require(vertices.length == header.vertices * 12,"truncated polygon vertices");
                ByteBuffer positions = ByteBuffer.wrap(vertices).order(ByteOrder.LITTLE_ENDIAN);
                byte[] records=in.readNBytes(header.polygons*28);
                require(records.length==header.polygons*28,"truncated polygon records");
                // Authenticate the retained header/vertices/records plus full remainder
                // before their exact link counts become a memory reservation.
                byte[] buffer=new byte[8192];long remaining=size-100L-header.vertices*12L-header.polygons*28L;
                while(remaining>0){int read=in.read(buffer,0,(int)Math.min(buffer.length,remaining));require(read>0,"truncated tile payload");remaining-=read;}
                require(in.read()==-1 && HexFormat.of().formatHex(payload.digest()).equals(expectedHash),"tile payload changed before memory accounting");
                ByteBuffer polygons=ByteBuffer.wrap(records).order(ByteOrder.LITTLE_ENDIAN);
                Sides sides = new Sides(); portals.put(y*spec.nx+x,sides);
                for (int i = 0; i < header.polygons; i++) {
                    int offset=i*28;
                    int count = Byte.toUnsignedInt(records[offset+26]); require(count >= 3 && count <= 6,"invalid polygon vertex count");
                    for (int edge = 0; edge < count; edge++) {
                        int neighbor = Short.toUnsignedInt(polygons.getShort(offset+12+edge*2));
                        if(neighbor!=0 && (neighbor&0x8000)==0) {
                            require(neighbor<=header.polygons,"dangling internal neighbor");
                            // Detour creates exactly one directed internal link for this edge.
                            estimate=Math.addExact(estimate,Layout.LINK);
                        }
                        if ((neighbor & 0x8000) != 0) {
                            int side = neighbor & 0xff;
                            require(side == 0 || side == 2 || side == 4 || side == 6,"invalid tile portal side");
                            int a=Short.toUnsignedInt(polygons.getShort(offset+edge*2)),c=Short.toUnsignedInt(polygons.getShort(offset+((edge+1)%count)*2));
                            require(a < header.vertices && c < header.vertices,"dangling portal vertex");
                            int axis=(side == 0 || side == 4) ? 2 : 0;
                            float first=positions.getFloat(a*12+axis*4),last=positions.getFloat(c*12+axis*4);
                            require(Float.isFinite(first) && Float.isFinite(last),"nonfinite portal interval");
                            estimate = Math.addExact(estimate,160L);
                            reservePreflight(entry,reservation,Math.addExact(estimate,encodedPeak));
                            sides.sides.get(side/2).add(new Portal(Math.min(first,last),Math.max(first,last)));
                        }
                    }
                }
                reservePreflight(entry,reservation,Math.addExact(estimate,encodedPeak));
            }
            require(header.polygons == integer(p,"polygons"),"tile receipt/header mismatch");
            // Actual directed internal edges are counted from the immutable tile.
            // Cross-tile links retain a separate interval-pair upper bound,
            // without height culling; both directions and allocation slack count.
            tiles.add(new Tile(x,y,file,receipt,hash(receiptBytes),expectedHash,size,header));
        }
        long pairs = 0;
        for (int y = range.y0; y < range.y1; y++) for (int x = range.x0; x < range.x1; x++) {
            Sides sides = portals.get(y*spec.nx+x); if (sides == null) continue;
            Sides east=x+1 < spec.nx ? portals.get(y*spec.nx+x+1) : null;
            Sides north=y+1 < spec.ny ? portals.get((y+1)*spec.nx+x) : null;
            if (east != null) pairs = Math.addExact(pairs,overlappingPairs(sides.sides.get(0),east.sides.get(2)));
            if (north != null) pairs = Math.addExact(pairs,overlappingPairs(sides.sides.get(1),north.sides.get(3)));
        }
        estimate = Math.addExact(Math.addExact(estimate,encodedPeak),Math.multiplyExact(pairs,4*Layout.LINK));
        reservePreflight(entry,reservation,estimate);
        validateEmptyPayloads(spec,tiles);
        require(tiles.stream().anyMatch(t -> t.header != null),"map has no generated supported ground tiles");
        return new Plan(spec,tiles,estimate);
    }

    /** Count all interval-overlap candidates, even different floors. Detour's
     * slab/height tests can only remove these candidates, never add others. */
    private static long overlappingPairs(List<Portal> a,List<Portal> b) {
        if (a.isEmpty() || b.isEmpty()) return 0;
        float[] starts=new float[b.size()],ends=new float[b.size()];
        for (int i=0;i<b.size();i++) { starts[i]=b.get(i).start; ends[i]=b.get(i).end; }
        java.util.Arrays.sort(starts); java.util.Arrays.sort(ends);
        long count=0;
        for (Portal p:a) count+=bound(starts,p.end,true)-bound(ends,p.start,false);
        return count;
    }

    private static int bound(float[] values,float value,boolean inclusive) {
        int low=0,high=values.length;
        while (low<high) { int mid=(low+high)>>>1;
            if (values[mid] < value || inclusive && values[mid] == value) low=mid+1; else high=mid;
        }
        return low;
    }

    private NavMesh decode(Catalog current, Plan plan, long generation) throws IOException {
        Spec spec = plan.spec;
        NavMeshParams params = new NavMeshParams();
        params.maxTiles = (int)plan.tiles.stream().filter(t -> t.header != null).count(); params.maxPolys = 32768;
        params.tileWidth = params.tileHeight = spec.width;
        System.arraycopy(spec.origin,0,params.orig,0,3);
        NavMesh mesh = new NavMesh(params,6);
        for (Tile tile : plan.tiles) {
            synchronized (this) { if (closed || epoch != generation) throw new IOException("resource load cancelled"); }
            require(hash(boundedBytes(inside(current.root,tile.receipt),16_384)).equals(tile.receiptHash),"tile receipt changed during load");
            if (tile.header == null) continue;
            byte[] bytes = boundedBytes(inside(current.root,tile.file),(int)tile.size);
            require(bytes.length == tile.size && hash(bytes).equals(tile.hash),"tile size/hash mismatch");
            require(header(bytes,tile.size,tile.x,tile.y,spec).equals(tile.header),"tile header changed during load");
            ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
            MeshData data = new MeshDataReader().read(buffer,6);
            require(!buffer.hasRemaining(),"unexpected trailing tile bytes");
            validate(data);
            require(mesh.addTile(data,0,0) != 0,"tile admission failed");
        }
        validateEmptyPayloads(spec,plan.tiles);
        require(hash(boundedBytes(inside(current.root,spec.directory.resolve("map.properties")),MAX_MANIFEST)).equals(spec.hash),"map manifest changed during load");
        require(hash(boundedBytes(inside(current.root,current.root.resolve("report.properties")),MAX_PROPERTIES)).equals(current.hash),"navmesh report changed during load");
        return mesh;
    }

    private static void validateEmptyPayloads(Spec spec,List<Tile> tiles) throws IOException {
        var empty=new java.util.HashSet<String>();
        for(Tile tile:tiles)if(tile.header==null)empty.add(tile.file.getFileName().toString());
        if(empty.isEmpty())return;
        // Directory enumeration does not throw an expected missing-file exception
        // for every EMPTY receipt. Retain the same unexpected-payload rejection.
        try(var files=Files.newDirectoryStream(spec.directory,"*.navtile")){
            for(Path file:files)require(!empty.contains(file.getFileName().toString()),"empty receipt has a tile");
        }
    }

    private static Header header(byte[] bytes,long size,int x,int y,Spec spec) throws IOException {
        require(bytes.length >= 100,"truncated tile header");
        ByteBuffer b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        require(b.getInt(0) == MeshHeader.DT_NAVMESH_MAGIC && b.getInt(4) == MeshHeader.DT_NAVMESH_VERSION_RECAST4J_LAST,"unsupported generated tile encoding");
        require(b.getInt(8) == x && b.getInt(12) == y && b.getInt(16) == 0,"wrong tile coordinates/layer");
        int p=b.getInt(24),v=b.getInt(28),links=b.getInt(32),dm=b.getInt(36),dv=b.getInt(40),dt=b.getInt(44),bv=b.getInt(48);
        require(p > 0 && p <= 32768 && v >= 3 && v < 65535 && links >= 0 && links <= (long)p*18
                && dm == p && dv >= 0 && dt > 0 && bv >= 0 && bv <= (long)p*2 && b.getInt(52) == 0 && b.getInt(56) == p,"invalid ground tile counts");
        long minimum = 100L + v*12L + p*28L + dm*10L + dv*12L + dt*4L + bv*28L;
        require(minimum <= size,"encoded tile cannot contain declared arrays");
        for (int offset=60;offset<100;offset+=4) require(Float.isFinite(b.getFloat(offset)),"nonfinite tile header");
        require(b.getFloat(60) > 0 && b.getFloat(64) >= 0 && b.getFloat(68) >= 0 && b.getFloat(96) > 0,"invalid actor/BV dimensions");
        require(Math.abs(b.getFloat(72)-(spec.origin[0]+x*spec.width)) < .01
                && Math.abs(b.getFloat(80)-(spec.origin[2]+y*spec.width)) < .01
                && Math.abs(b.getFloat(84)-(spec.origin[0]+(x+1)*spec.width)) < .01
                && Math.abs(b.getFloat(92)-(spec.origin[2]+(y+1)*spec.width)) < .01,"tile bounds mismatch");
        require(b.getFloat(76) <= b.getFloat(88),"inverted tile height bounds");
        return new Header(p,v,links,dv,dt,bv);
    }

    private static void validate(MeshData data) throws IOException {
        for (float v : data.verts) require(Float.isFinite(v),"nonfinite polygon vertex");
        for (float v : data.detailVerts) require(Float.isFinite(v),"nonfinite detail vertex");
        for (Poly p : data.polys) {
            require(p.getType() == Poly.DT_POLYTYPE_GROUND && p.vertCount >= 3 && p.vertCount <= 6,"invalid ground polygon");
            double area = 0;
            for (int i=0;i<p.vertCount;i++) {
                int a=p.verts[i],c=p.verts[(i+1)%p.vertCount];
                require(a >= 0 && a < data.header.vertCount && c >= 0 && c < data.header.vertCount,"dangling polygon vertex");
                int neighbor=p.neis[i];
                require((neighbor & 0x8000) != 0 || neighbor >= 0 && neighbor <= data.header.polyCount,"dangling internal neighbor");
                area += (double)data.verts[a*3]*data.verts[c*3+2]-(double)data.verts[c*3]*data.verts[a*3+2];
            }
            require(area != 0,"zero-area generated polygon");
            var d=data.detailMeshes[p.index];
            require(d.vertBase >= 0 && d.vertCount >= 0 && ((long)d.vertBase+d.vertCount)*3 <= data.detailVerts.length
                    && d.triBase >= 0 && d.triCount > 0 && ((long)d.triBase+d.triCount)*4 <= data.detailTris.length,"invalid detail descriptor");
            for (int i=0;i<d.triCount;i++) for (int c=0;c<3;c++) {
                int v=data.detailTris[(d.triBase+i)*4+c];
                require(v >= 0 && v < p.vertCount+d.vertCount,"dangling detail face");
            }
        }
        for (int i=0;i<data.bvTree.length;i++) {
            var node=data.bvTree[i];
            require(node.i >= 0 ? node.i < data.header.polyCount : (long)i-node.i <= data.bvTree.length,"invalid BV escape/reference");
        }
    }

    private static Path inside(Path root,Path file) throws IOException {
        Path path=file.toAbsolutePath().normalize(); require(path.startsWith(root),"navmesh path outside root");
        Path real=path.toRealPath(); require(real.startsWith(root),"navmesh path resolves outside root"); return real;
    }
    private static byte[] boundedBytes(Path file,int limit) throws IOException {
        require(Files.isRegularFile(file) && Files.size(file) <= limit,"oversized or missing navmesh resource");
        try (InputStream in=Files.newInputStream(file)) {
            byte[] bytes=in.readNBytes(limit+1); require(bytes.length <= limit,"resource grew while reading"); return bytes;
        }
    }
    private static Properties properties(byte[] bytes) throws IOException {
        Properties p=new Properties(); p.load(new java.io.ByteArrayInputStream(bytes)); return p;
    }
    private static float[] vector(String text) throws IOException {
        require(text.startsWith("[") && text.endsWith("]"),"invalid vector");
        String[] parts=text.substring(1,text.length()-1).split(",",-1); require(parts.length == 3,"invalid vector size");
        float[] v=new float[3]; for (int i=0;i<3;i++) { v[i]=Float.parseFloat(parts[i].trim()); require(Float.isFinite(v[i]),"nonfinite map bounds"); } return v;
    }
    private static int integer(Properties p,String name) throws IOException { return Integer.parseInt(value(p,name)); }
    private static String value(Properties p,String name) throws IOException {
        String v=p.getProperty(name); require(v != null && !v.isBlank(),"missing " + name); return v;
    }
    private static String hashValue(Properties p,String name) throws IOException {
        String v=value(p,name); require(isHash(v),"invalid " + name); return v;
    }
    private static boolean isHash(String s) { return s.matches("[0-9a-f]{64}"); }
    private static String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException e) { throw new AssertionError(e); }
    }
    private static void require(boolean condition,String why) throws IOException { if (!condition) throw new IOException(why); }
    private static Result waiting(String why) { return new Result(Status.WAITING,null,why); }
    private static Result unavailable(String why) { return new Result(Status.UNAVAILABLE,null,why); }
    private static String reason(String prefix,Throwable e) {
        String text=prefix + ": " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        return text.length() <= 512 ? text : text.substring(0,512);
    }
}
