package com.aionemu.gameserver.services.playerbot;

import java.lang.ref.WeakReference;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.function.BooleanSupplier;
import org.recast4j.detour.*;
import com.aionemu.gameserver.configs.main.PlayerBotConfig;
import com.aionemu.gameserver.services.playerbot.PlayerBotNavigation.Point;
import com.aionemu.gameserver.utils.ThreadPoolManager;

/** Prepared ground graph, sliced under the native terrain budget; every emitted step still earns native proof. */
final class PlayerBotMeshRoute implements AutoCloseable {
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(PlayerBotMeshRoute.class);
    private static final int MAX_EXPANSIONS=4096, MAX_NODES=8192;
    /** How often prepared corridors pass or fail the native ground check, so the
     * log shows whether the navmesh can be trusted like WoW mmaps. Reset on read. */
    private static final java.util.concurrent.ConcurrentHashMap<String,java.util.concurrent.atomic.LongAdder> OUTCOMES=new java.util.concurrent.ConcurrentHashMap<>();
    private static void count(String outcome){
        if(!PlayerBotConfig.NAVMESH_DIAGNOSTICS)return;OUTCOMES.computeIfAbsent(outcome,k->new java.util.concurrent.atomic.LongAdder()).increment();}
    /** Where prepared corridors pass or fail, in 50 m cells per map, so a session
     * shows whether native-terrain disagreement is local (towns, buildings) or
     * map-wide. Cumulative since start; written to log/navmesh_areas.csv every
     * five minutes. Bounded to 200k cells. */
    private record AreaKey(int world,int cx,int cy) {}
    private static final class Area {
        final java.util.concurrent.atomic.LongAdder passed=new java.util.concurrent.atomic.LongAdder(),partly=new java.util.concurrent.atomic.LongAdder(),failed=new java.util.concurrent.atomic.LongAdder();
        volatile String example="";
    }
    private static final java.util.concurrent.ConcurrentHashMap<AreaKey,Area> AREAS=new java.util.concurrent.ConcurrentHashMap<>();
    private static volatile long nextAreaFlush=System.currentTimeMillis()+300_000;
    private static Area area(int world,float x,float y){
        if(AREAS.size()>200_000)return null;
        return AREAS.computeIfAbsent(new AreaKey(world,(int)Math.floor(x/50),(int)Math.floor(y/50)),k->new Area());
    }
    private static String fmt(Point p){return p==null?"":String.format(Locale.ROOT,"%.1f %.1f %.1f",p.x(),p.y(),p.z());}
    private void recordArea(String outcome,String why){
        if(!PlayerBotConfig.NAVMESH_DIAGNOSTICS)return;
        Point at=blockedAt!=null?blockedAt:start;
        var a=area(world,at.x(),at.y());if(a==null)return;
        switch(outcome){case "passed"->a.passed.increment();case "partly"->a.partly.increment();default->a.failed.increment();}
        if(!outcome.equals("passed") && a.example.isEmpty())
            a.example=why+" | from "+fmt(blockedFrom)+" toward "+fmt(blockedAt)+" reached "+fmt(blockedReached)+" | route "+fmt(start)+" -> "+fmt(goal);
    }
    static void flushAreas(java.nio.file.Path file){
        if(!PlayerBotConfig.NAVMESH_DIAGNOSTICS)return;
        long now=System.currentTimeMillis();if(now<nextAreaFlush)return;nextAreaFlush=now+300_000;
        var rows=new ArrayList<Map.Entry<AreaKey,Area>>();
        for(var e:AREAS.entrySet()){var a=e.getValue();if(a.failed.sum()+a.partly.sum()>0 || a.passed.sum()>=20)rows.add(e);}
        rows.sort(Comparator.comparingLong((Map.Entry<AreaKey,Area> e)->-(e.getValue().failed.sum()+e.getValue().partly.sum())));
        var lines=new ArrayList<String>();
        lines.add("map,cell_x,cell_y,center_x,center_y,passed,partly,failed,fail_percent,example");
        var maps=new TreeMap<Integer,long[]>();
        for(var e:AREAS.entrySet()){var a=e.getValue();var m=maps.computeIfAbsent(e.getKey().world(),k->new long[3]);m[0]+=a.passed.sum();m[1]+=a.partly.sum();m[2]+=a.failed.sum();}
        for(var e:rows.subList(0,Math.min(5000,rows.size()))){
            var k=e.getKey();var a=e.getValue();long p=a.passed.sum(),pt=a.partly.sum(),f=a.failed.sum();long n=p+pt+f;
            lines.add(k.world()+","+k.cx()+","+k.cy()+","+(k.cx()*50+25)+","+(k.cy()*50+25)+","+p+","+pt+","+f+","
                +(n==0?0:Math.round(100.0*(f+pt)/n))+",\""+a.example.replace("\"","'")+"\"");
        }
        try{java.nio.file.Files.write(file,lines,java.nio.charset.StandardCharsets.UTF_8);}
        catch(java.io.IOException e){LOG.warn("Could not write navmesh area report {}: {}",file,e.getMessage());}
        var worst=maps.entrySet().stream().filter(e->e.getValue()[1]+e.getValue()[2]>0)
            .sorted(Comparator.comparingLong((Map.Entry<Integer,long[]> e)->-(e.getValue()[1]+e.getValue()[2]))).limit(8)
            .map(e->{long[] v=e.getValue();long n=v[0]+v[1]+v[2];return "map "+e.getKey()+" "+(v[1]+v[2])+"/"+n+" ("+Math.round(100.0*(v[1]+v[2])/n)+"%)";})
            .collect(java.util.stream.Collectors.joining("; "));
        if(!worst.isEmpty())LOG.info("Navmesh blocked or partial routes by map since start (details in {}): {}",file.getFileName(),worst);
    }
    /* Learned blocked spots: where a prepared corridor failed the native ground check,
     * routes through that spot cost 25x more, so later searches go around it (WoW bots
     * likewise avoid known-bad path spots). Learned at runtime from every failure and
     * kept in data/navmesh/learned-blocks.csv (map,x,y,z), loaded at start and appended
     * as new spots are found. A cost, not a wall: if no other way exists the spot is
     * still usable, and the native step check stays authoritative. Bounded to 200k. */
    private static final java.nio.file.Path LEARNED_FILE=java.nio.file.Path.of("data","navmesh","learned-blocks.csv");
    private static final java.util.concurrent.ConcurrentHashMap<Long,java.util.concurrent.CopyOnWriteArrayList<float[]>> LEARNED=new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.ConcurrentLinkedQueue<String> LEARNED_PENDING=new java.util.concurrent.ConcurrentLinkedQueue<>();
    private static final java.util.concurrent.atomic.AtomicInteger LEARNED_COUNT=new java.util.concurrent.atomic.AtomicInteger();
    private static volatile boolean learnedLoaded;
    private static long learnedCell(int world,float x,float y){return ((long)world<<40)^(((long)Math.floor(x/4)&0xFFFFF)<<20)^((long)Math.floor(y/4)&0xFFFFF);}
    private static synchronized void loadLearned(){
        if(learnedLoaded)return;learnedLoaded=true;
        if(!java.nio.file.Files.exists(LEARNED_FILE))return;
        try{for(String line:java.nio.file.Files.readAllLines(LEARNED_FILE)){
            String[] v=line.split(",");if(v.length!=4)continue;
            try{remember(Integer.parseInt(v[0].trim()),Float.parseFloat(v[1]),Float.parseFloat(v[2]),Float.parseFloat(v[3]),false);}catch(NumberFormatException ignored){}
        }}catch(java.io.IOException e){LOG.warn("Could not read learned navmesh blocks {}: {}",LEARNED_FILE,e.getMessage());}
        LOG.info("Playerbot navigation: {} learned blocked spots loaded",LEARNED_COUNT.get());
    }
    private static boolean nearLearned(int world,float x,float y,float z){
        for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++){
            var list=LEARNED.get(learnedCell(world,x+dx*4,y+dy*4));if(list==null)continue;
            for(float[] p:list)if(Math.hypot(p[0]-x,p[1]-y)<2.5 && Math.abs(p[2]-z)<3)return true;
        }
        return false;
    }
    private static void remember(int world,float x,float y,float z,boolean persist){
        if(!PlayerBotConfig.NAVMESH_LEARN_BLOCKS || LEARNED_COUNT.get()>=200_000 || dedupe(world,x,y,z))return;
        var spot=new float[]{x,y,z};
        LEARNED.computeIfAbsent(learnedCell(world,x,y),k->new java.util.concurrent.CopyOnWriteArrayList<>()).add(spot);
        var order=LEARNED_ORDER.computeIfAbsent(world,k->new ArrayList<>());synchronized(order){order.add(spot);}
        LEARNED_COUNT.incrementAndGet();
        if(persist)LEARNED_PENDING.add(world+","+String.format(Locale.ROOT,"%.1f,%.1f,%.1f",x,y,z));
    }
    /** Polygon area for mesh polygons within a learned spot's 2.5 m/3 m radius. Default area costs stay 1. */
    static final int LEARNED_AREA=1;
    // Spots per map in learning order; each loaded mesh remembers how many it has tagged.
    private static final java.util.concurrent.ConcurrentHashMap<Integer,List<float[]>> LEARNED_ORDER=new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<NavMesh,int[]> TAGGED=java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    /**
     * Detour area marking (WoW/Recast dtPoly area): polygons touching a learned
     * spot carry LEARNED_AREA, so route cost only does the exact spot lookup on
     * those polygons instead of on every expanded edge. New spots are tagged on
     * the next search of a loaded mesh; a reloaded mesh is tagged from the start.
     */
    private static void tagLearned(int world,NavMesh mesh,NavMeshQuery query){
        if(!PlayerBotConfig.NAVMESH_LEARN_BLOCKS)return;
        var order=LEARNED_ORDER.get(world);if(order==null)return;
        int[] done=TAGGED.computeIfAbsent(mesh,k->new int[1]);
        List<float[]> fresh;
        synchronized(done){
            synchronized(order){if(done[0]>=order.size())return;fresh=new ArrayList<>(order.subList(done[0],order.size()));done[0]=order.size();}
            var all=new DefaultQueryFilter();float[] extents={2.5f,3,2.5f};
            for(float[] p:fresh)query.queryPolygons(new float[]{p[0],p[2],p[1]},extents,all,(tile,poly,ref)->poly.setArea(LEARNED_AREA));
        }
    }
    private static boolean dedupe(int world,float x,float y,float z){
        var list=LEARNED.get(learnedCell(world,x,y));if(list==null)return false;
        for(float[] p:list)if(Math.hypot(p[0]-x,p[1]-y)<1.5 && Math.abs(p[2]-z)<2)return true;
        return false;
    }
    static void flushLearned(){
        if(LEARNED_PENDING.isEmpty())return;
        var lines=new ArrayList<String>();for(String l;(l=LEARNED_PENDING.poll())!=null;)lines.add(l);
        try{java.nio.file.Files.write(LEARNED_FILE,lines,java.nio.charset.StandardCharsets.UTF_8,java.nio.file.StandardOpenOption.CREATE,java.nio.file.StandardOpenOption.APPEND);}
        catch(java.io.IOException e){LOG.warn("Could not save learned navmesh blocks {}: {}",LEARNED_FILE,e.getMessage());}
    }
    static String outcomes(){
        flushLearned();
        var parts=new java.util.TreeMap<String,Long>();
        for(var e:OUTCOMES.entrySet()){long n=e.getValue().sumThenReset();if(n>0)parts.put(e.getKey(),n);}
        return parts.isEmpty()?"":parts.entrySet().stream().sorted(Map.Entry.<String,Long>comparingByValue().reversed())
            .map(e->e.getValue()+" x "+e.getKey()).collect(java.util.stream.Collectors.joining("; "));
    }
    private static Runtime runtime;
    private static Runtime companionRuntime;
    private static Admission sharedAdmission;
    private static Admission companionAdmission;
    static synchronized Admission admission(){
        if(sharedAdmission==null)sharedAdmission=new Admission(PlayerBotConfig.NAVMESH_MAX_QUERIES);
        return sharedAdmission;
    }
    static synchronized Admission companionAdmission(){
        if(companionAdmission==null)companionAdmission=new Admission(PlayerBotConfig.COMPANION_NAVMESH_MAX_QUERIES);
        return companionAdmission;
    }
    /** Loading demand, retained search state and runnable callbacks have separate custody.
     * Pinned Playerbots 037c014 TravelMgr::getPathStepFrom keeps PathGenerator local to an attempt;
     * Aion slices that work, so its memory reservation must outlive a callback without occupying it. */
    static final class Admission {
        private final Waiting demands,memory,calculations;
        final Semaphore slots;
        Admission(int limit){
            if(limit<1 || limit>256)throw new IllegalArgumentException("Invalid prepared calculation limit");
            demands=new Waiting(limit);memory=new Waiting(limit);calculations=new Waiting(limit);
            slots=new Semaphore(limit);
        }
        boolean demand(Object owner){return demands.acquire(owner);}
        void releaseDemand(Object owner){demands.release(owner);}
        boolean retain(Object owner){return memory.acquire(owner);}
        void releaseMemory(Object owner){memory.release(owner);}
        boolean begin(Object owner){
            if(!calculations.acquire(owner))return false;
            if(slots.tryAcquire())return true;
            calculations.release(owner);return false;
        }
        void end(Object owner){slots.release();calculations.release(owner);}
        void cancel(Object owner){demands.release(owner);memory.release(owner);calculations.cancel(owner);}
        /** One small weak ticket per pending owner; no mesh, query, graph or actor is stored here.
         * Ready waiters cannot be overtaken by new demand. A nominated owner that never returns
         * yields its place after the same five-second callback grace used by terrain admission. */
        private static final class Waiting {
            private static final long GRACE=5_000_000_000L;
            private static final class Ticket {
                final WeakReference<Object> owner;
                long nominated;
                boolean selected;
                Ticket(Object owner){this.owner=new WeakReference<>(owner);}
            }
            private final int limit;
            private final Map<Object,Boolean> active=new WeakHashMap<>();
            private final Map<Object,Ticket> waiting=new WeakHashMap<>();
            private final ArrayDeque<Ticket> queue=new ArrayDeque<>();
            Waiting(int limit){this.limit=limit;}
            synchronized boolean acquire(Object owner){
                if(active.containsKey(owner))return true;
                long now=System.nanoTime();int available=limit-active.size();
                Ticket mine=waiting.get(owner);
                if(mine==null){mine=new Ticket(owner);waiting.put(owner,mine);queue.addLast(mine);}
                int rank=0;boolean admitted=false;
                for(var iterator=queue.iterator();iterator.hasNext();){
                    Ticket ticket=iterator.next();Object token=ticket.owner.get();
                    if(token==null || waiting.get(token)!=ticket || ticket.selected && now-ticket.nominated>GRACE){
                        iterator.remove();if(token!=null && waiting.get(token)==ticket)waiting.remove(token);
                        continue;
                    }
                    if(rank++>=available)break;
                    if(!ticket.selected){ticket.selected=true;ticket.nominated=now;}
                    if(ticket==mine){iterator.remove();waiting.remove(owner);active.put(owner,Boolean.TRUE);admitted=true;break;}
                }
                return admitted;
            }
            synchronized void release(Object owner){active.remove(owner);cancel(owner);}
            synchronized void cancel(Object owner){Ticket ticket=waiting.remove(owner);if(ticket!=null)queue.remove(ticket);}
        }
    }
    private static final class Runtime {
        final PlayerBotNavmeshStore store;
        final Set<Integer> reported=java.util.concurrent.ConcurrentHashMap.newKeySet();
        Runtime(boolean companion) {
            long memory=memoryAllowance(companion?PlayerBotConfig.COMPANION_NAVMESH_MEMORY_MB:PlayerBotConfig.NAVMESH_MEMORY_MB,java.lang.Runtime.getRuntime().maxMemory());
            int queries=companion?PlayerBotConfig.COMPANION_NAVMESH_MAX_QUERIES:PlayerBotConfig.NAVMESH_MAX_QUERIES;
            if(queries<1 || queries>256)throw new IllegalArgumentException("Invalid playerbot mesh query limit");
            LOG.info("Playerbot {} navigation: lazy cache allowance={} MiB, road route cache={} KiB per loaded map, concurrent queries={}",companion?"companion":"world",memory,PlayerBotConfig.NAVMESH_ROUTE_CACHE_KB,queries);
            store=new PlayerBotNavmeshStore(Path.of("data/navmesh"),memory*1024L*1024L,
                task->ThreadPoolManager.getInstance().executeLongRunning(task));
        }
    }
    static long memoryAllowance(long configured,long heapBytes){
        int percent=PlayerBotConfig.NAVMESH_HEAP_PERCENT;
        if(percent<1 || percent>99)throw new IllegalArgumentException("Automatic navmesh heap share must be 1..99 percent");
        long heapMiB=heapBytes/(1024L*1024L);
        long memory=configured==0?heapMiB*percent/100:configured;
        if(memory<64 || memory>=heapMiB)throw new IllegalArgumentException("Navmesh allowance must fit inside the configured JVM heap with room for server state (minimum 64 MiB)");
        return memory;
    }
    private static synchronized Runtime runtime(Admission admission){
        if(admission==companionAdmission){if(companionRuntime==null)companionRuntime=new Runtime(true);return companionRuntime;}
        if(runtime==null)runtime=new Runtime(false);return runtime;
    }
    static synchronized void shutdown(){if(runtime!=null)runtime.store.close();if(companionRuntime!=null)companionRuntime.store.close();}
    private final int world;
    private final Point start,goal;
    private Point target;
    private final PlayerBotMeshJourney journey;
    private final boolean ownsJourney;
    private final Admission admission;
    private final float horizon;
    private Runtime host;
    private PlayerBotNavmeshStore.Lease lease;
    private PlayerBotNavmeshStore.Request request;
    private NavMeshQuery query;
    private boolean done,fallback,partial,graphComplete,requestedEndpoint,coarseRequired;
    private long endRef;
    private float[] projectedStart,projectedEnd;
    private int expanded,work,cursor,windowRadius=1;
    private List<Point> candidates=List.of(),route=List.of();
    private final List<Point> proved=new ArrayList<>();
    private Point proofOrigin;
    /** Last native proof step, kept for the area report. */
    private Point blockedFrom,blockedAt,blockedReached;
    private double provedDistance;
    private boolean bakedCorridor;
    private int bakedStart;
    private String reason="waiting for prepared map";
    PlayerBotMeshRoute(int world,Point start,Point goal,float horizon){this(world,start,goal,horizon,null);}
    PlayerBotMeshRoute(int world,Point start,Point goal,float horizon,PlayerBotMeshJourney journey){
        this(world,start,goal,horizon,journey,admission());
    }
    PlayerBotMeshRoute(int world,Point start,Point goal,float horizon,PlayerBotMeshJourney journey,Admission admission){
        this.world=world;this.start=start;this.goal=goal;this.target=goal;this.horizon=horizon;proofOrigin=start;
        this.admission=Objects.requireNonNull(admission);
        ownsJourney=journey==null;this.journey=ownsJourney?new PlayerBotMeshJourney():journey;this.journey.bind(world,goal);
    }
    boolean pending(){return !done;}
    boolean fallback(){return fallback;}
    boolean partial(){return partial;}
    int work(){return work;}
    int expanded(){return expanded;}
    int retained(){return query==null?0:query.getNodePool().getNodeMap().size();}
    List<Point> route(){return route;}
    String reason(){return reason;}
    Point nativeTarget(){return target;}
    void advance(PlayerBotPathfinder.Ground ground,int allowance,BooleanSupplier expired) {
        if(done || allowance<4 || expired.getAsBoolean())return;
        int used=0;
        if(host==null)host=runtime(admission);
        // Short local movement has no cross-window journey to resolve. Long
        // travel owns the component itinerary; combat/local steering stays local.
        boolean journeyNeeded=coarseRequired || distance(start,goal)>horizon;
        if(journeyNeeded && !journey.ready()){
            // Component arcs and cached road points are cheap memory work, not
            // native collision probes. Reusing the 128-probe cap forced even
            // small itineraries to wait for many shared terrain grants. Keep
            // the same funded deadline/admission; only this pure graph batch
            // has a larger operation cap. Native edge proof below is unchanged.
            used=journey.advance(host.store,start,Math.max(allowance,8192),expired,admission);work+=used;reason=journey.reason();
            if(!journey.ready() || used>0)return;
        }
        target=journeyNeeded?journey.target(start):goal;
        if(candidates.isEmpty() && query==null && request==null && journeyNeeded){
            var baked=journey.preparedCorridor(start,horizon);
            if(baked!=null && !baked.points().isEmpty()){candidates=baked.points();bakedStart=baked.start();target=candidates.getLast();graphComplete=true;bakedCorridor=true;reason="proving prepared main corridor";}
        }
        if(query==null && candidates.isEmpty()) {
            if(!admission.demand(this)){reason="waiting for prepared window admission";return;}
            if(request==null)request=host.store.request(world,start.x(),start.y(),windowRadius);
            var access=request.poll();
            if(access.status()!=PlayerBotNavmeshStore.Status.READY){
                // Bounded I/O demand never occupies a calculation permit or query reservation.
                reason=access.reason();
                if(access.status()==PlayerBotNavmeshStore.Status.UNAVAILABLE){
                    if(host.reported.size()<256 && host.reported.add(world))LOG.warn("Prepared navigation unavailable on map {}: {}. Using native collision search.",world,reason);
                    useNative(reason);
                }
                return;
            }
            lease=access.lease();
            if(!admission.retain(this)){reason="waiting for prepared search memory";return;}
        }
        if(candidates.isEmpty()){
            if(expired.getAsBoolean())return;
            if(!admission.begin(this)){reason="waiting for prepared calculation turn";return;}
            try {used=advanceGraph(allowance,expired,used,journeyNeeded);}
            finally {admission.end(this);}
        }
        while(!candidates.isEmpty() && !done && used<allowance && !expired.getAsBoolean()){
            if(cursor>=candidates.size()){finish();break;}
            Point target=candidates.get(cursor);double xy=Math.hypot(target.x()-proofOrigin.x(),target.y()-proofOrigin.y());
            if(xy<.05 && Math.abs(target.z()-proofOrigin.z())<.75){cursor++;continue;}
            // Same XY but a different mesh height (the projected start polygon): a
            // native step there cannot move, which was counted as "made no native
            // progress". Within one step height it is the same spot; skip it and count it.
            if(xy<.05 && Math.abs(target.z()-proofOrigin.z())<2){count("note: skipped corner at same spot, mesh height off "+(Math.abs(target.z()-proofOrigin.z())<1.25?"0.75-1.25 m":"1.25-2 m"));cursor++;continue;}
            // WoW trusts its in-RAM navmesh. Check the prepared corridor in long
            // native strides (the mover still checks every real step) instead of
            // a 4 m proof walk that made route planning the server's CPU hot spot.
            float fraction=(float)Math.min(1,Math.min(12,Math.max(.05,horizon-provedDistance))/Math.max(.001,xy));
            float x=proofOrigin.x()+(target.x()-proofOrigin.x())*fraction,y=proofOrigin.y()+(target.y()-proofOrigin.y())*fraction;
            Point actual=ground.step(proofOrigin,x,y);work++;used++;
            blockedFrom=proofOrigin;blockedAt=new Point(x,y,target.z());blockedReached=actual;
            if(actual!=null && finite(actual) && Math.hypot(actual.x()-x,actual.y()-y)>.15){
                // The obstacle lies just beyond the furthest native point reached.
                double d=Math.max(.001,Math.hypot(x-actual.x(),y-actual.y()));double t=Math.min(1,.75/d);
                remember(world,(float)(actual.x()+(x-actual.x())*t),(float)(actual.y()+(y-actual.y())*t),actual.z(),true);
            }
            if(actual==null || !finite(actual) || Math.hypot(actual.x()-x,actual.y()-y)>.15
                    || fraction==1 && Math.abs(actual.z()-target.z())>2){
                // Mesh corner heights are approximate; the native floor reached at
                // the same XY within one step height is the same corridor.
                // Dynamic obstacles remain native authority. Replan using live collision geometry.
                useNative("prepared corridor blocked by current native terrain");return;
            }
            double travelled=distance(proofOrigin,actual);
            if(travelled<.01){useNative(xy<.05?"prepared corridor made no native progress (same spot, mesh height off over 2 m)":"prepared corridor made no native progress (native step blocked at start)");return;}
            provedDistance+=travelled;proved.add(actual);proofOrigin=actual;
            if(fraction==1)cursor++;
            if(provedDistance>=horizon || cursor>=candidates.size())finish();
        }
    }
    private int advanceGraph(int allowance,BooleanSupplier expired,int used,boolean journeyNeeded){
        if(query==null){
            if(!learnedLoaded)loadLearned();
            query=new NavMeshQuery(lease.mesh());admission.releaseDemand(this);
            tagLearned(world,lease.mesh(),query);
            final NavMeshQuery boundedQuery=query;
            var filter=new DefaultQueryFilter(){
                @Override public boolean passFilter(long ref,MeshTile tile,Poly poly){
                    // Sliced options=0 creates only state-0 nodes immediately after this predicate.
                    // Keep existing nodes traversable, but reject new allocations at the actor limit.
                    var nodes=boundedQuery.getNodePool().getNodeMap();
                    return super.passFilter(ref,tile,poly) && (nodes.containsKey(ref) || nodes.size()<MAX_NODES);
                }
                @Override public float getCost(float[] pa,float[] pb,long prevRef,MeshTile prevTile,Poly prevPoly,long curRef,MeshTile curTile,Poly curPoly,long nextRef,MeshTile nextTile,Poly nextPoly){
                    float cost=super.getCost(pa,pb,prevRef,prevTile,prevPoly,curRef,curTile,curPoly,nextRef,nextTile,nextPoly);
                    // Exact spot lookup only on polygons tagged as touching a learned spot.
                    if(curPoly.getArea()!=LEARNED_AREA && (nextPoly==null || nextPoly.getArea()!=LEARNED_AREA))return cost;
                    return nearLearned(world,pb[0],pb[2],pb[1]) || nearLearned(world,(pa[0]+pb[0])/2,(pa[2]+pb[2])/2,(pa[1]+pb[1])/2)?cost*25:cost;
                }
            };float[] extents={1,.75f,1};
            var a=query.findNearestPoly(position(start),extents,filter);
            var b=query.findNearestPoly(position(target),extents,filter);work+=2;used+=2;
            if(a.failed() || a.result.getNearestRef()==0){useNative("no prepared origin on the requested floor");return used;}
            requestedEndpoint=!b.failed() && b.result.getNearestRef()!=0;
            if(!requestedEndpoint){
                // The final destination can lie outside this immutable tile set.
                // Select an actual polygon toward it; never manufacture a floor
                // or treat this intermediate polygon as arrival at the world goal.
                float reach=256+128*windowRadius;
                b=query.findNearestPoly(position(target),new float[]{Math.abs(target.x()-start.x())+reach,
                    Math.abs(target.z()-start.z())+reach,Math.abs(target.y()-start.y())+reach},filter);
                work++;used++;
            }
            if(b.failed() || b.result.getNearestRef()==0){useNative("no prepared destination in the tile window");return used;}
            projectedStart=a.result.getNearestPos();projectedEnd=b.result.getNearestPos();endRef=b.result.getNearestRef();
            var status=query.initSlicedFindPath(a.result.getNearestRef(),endRef,projectedStart,projectedEnd,filter,0);
            if(status.isFailed()){useNative("prepared path initialization failed");return used;}
        }
        while(candidates.isEmpty() && !done && used<allowance-2 && !expired.getAsBoolean()) {
            // Small graph increments prevent a single actor monopolizing a terrain lease.
            var update=query.updateSlicedFindPath(Math.min(8,allowance-used-2));int count=update.result==null?0:update.result;
            expanded+=count;used+=Math.max(1,count);work+=Math.max(1,count);
            if(update.failed()){useNative("prepared path search failed");return used;}
            if(update.status.isInProgress() && expanded<MAX_EXPANSIONS && retained()<MAX_NODES)continue;
            var path=query.finalizeSlicedFindPath();work++;used++;
            if(path.failed() || path.result.isEmpty()){
                if(requestJourney())return used;
                useNative("prepared graph has no useful corridor");return used;
            }
            boolean reachedEndpoint=path.result.getLast()==endRef;
            if(!reachedEndpoint && !journeyNeeded && requestJourney())return used;
            graphComplete=requestedEndpoint && reachedEndpoint;
            float[] end=projectedEnd;
            if(!reachedEndpoint){
                var closest=query.closestPointOnPoly(path.result.getLast(),end);
                if(closest.failed()){useNative("prepared partial endpoint unavailable");return used;}end=closest.result.getClosest();
            }
            var straight=query.findStraightPath(projectedStart,end,path.result,4096,0);work++;used++;
            if(straight.failed() || straight.result.isEmpty()){useNative("prepared corridor extraction failed");return used;}
            candidates=straight.result.stream().map(p->point(p.getPos())).toList();
            graphComplete &= (straight.result.getLast().getFlags() & NavMeshQuery.DT_STRAIGHTPATH_END)!=0;
            // Polygon refs never survive eviction: retain only copied candidate coordinates.
            releaseGraph();reason="proving prepared corridor against native terrain";
        }
        return used;
    }
    private void finish(){
        Point corridorEnd=candidates.isEmpty()?proofOrigin:candidates.getLast();
        if(proved.isEmpty() || !graphComplete && distance(corridorEnd,target)>=distance(start,target)-1){
            if(windowRadius<8){
                // A detour can leave a small window. Widen only after an actual
                // non-advancing search, keeping the final goal and native proof.
                releaseGraph();windowRadius*=2;expanded=0;cursor=0;provedDistance=0;
                proved.clear();candidates=List.of();proofOrigin=start;
                reason="expanding prepared tile corridor";return;
            }
            useNative("prepared partial corridor does not advance the destination");return;
        }
        route=List.copyOf(proved);partial=!graphComplete || cursor<candidates.size() || distance(proofOrigin,goal)>.6;
        if(bakedCorridor)journey.provedCorridor(start,proofOrigin,bakedStart,cursor);else journey.provedSegment(start,proofOrigin,target);
        done=true;reason=partial?"native-proved prepared route segment":"native-proved prepared route";releaseGraph();
        count("passed");blockedAt=null;recordArea("passed","");
    }
    private boolean requestJourney(){
        if(coarseRequired || journey.ready())return false;
        // A short disconnected gap may need an authored platform too. Discover
        // global connectivity only after the cheap local ground search proves
        // it cannot connect the endpoints; ordinary combat steering stays local.
        coarseRequired=true;releaseGraph();expanded=cursor=0;provedDistance=0;
        candidates=List.of();proved.clear();proofOrigin=start;reason="planning native connectivity beyond the local corridor";return true;
    }
    private void useNative(String why){
        // A blocked later corner does not revoke the supported prefix already
        // proved from this origin. Let the actor earn it before replanning from
        // the new physical pose, rather than repeating the same full corridor.
        if(!proved.isEmpty() && provedDistance>=.6){
            route=List.copyOf(proved);partial=true;done=true;
            if(bakedCorridor)journey.provedCorridor(start,proofOrigin,bakedStart,cursor);else journey.provedSegment(start,proofOrigin,target);
            reason="native-proved prepared prefix before obstruction";count("partly passed: "+why);recordArea("partly",why);
            candidates=List.of();releaseGraph();return;
        }
        fallback=true;done=true;reason=why;proved.clear();candidates=List.of();releaseGraph();count("failed: "+why);recordArea("failed",why);
    }
    private void releaseSlot(){if(host!=null)admission.cancel(this);}
    private void releaseGraph(){query=null;if(request!=null){request.close();request=null;}if(lease!=null){lease.close();lease=null;}releaseSlot();}
    @Override public void close(){releaseGraph();if(ownsJourney)journey.close();done=true;}
    private static float[] position(Point p){return new float[]{p.x(),p.z(),p.y()};}
    /**
     * Detour raycast (dtNavMeshQuery::raycast) on an already loaded mesh: TRUE when
     * the mesh shows a wall/edge between the points, FALSE when the line is open,
     * null when no loaded mesh covers it. Costs no native collision work and never
     * loads a mesh; an open line still needs the native ground check.
     */
    static Boolean lineBlocked(int world,Point from,Point to){
        if(!PlayerBotConfig.NAVMESH_LINE_CHECK)return null;
        for(var r:new Runtime[]{runtime,companionRuntime}){
            if(r==null)continue;
            try(var lease=r.store.resident(world,from.x(),from.y(),to.x(),to.y())){
                if(lease==null)continue;
                var query=new NavMeshQuery(lease.mesh());var filter=new DefaultQueryFilter();
                var start=query.findNearestPoly(position(from),new float[]{1,.75f,1},filter);
                if(start.failed() || start.result.getNearestRef()==0)return null;
                var hit=query.raycast(start.result.getNearestRef(),start.result.getNearestPos(),position(to),filter,0,0);
                if(hit.failed())return null;
                boolean blocked=hit.result.t<1;
                count(blocked?"line check: mesh wall, ground probe skipped":"line check: mesh open, ground probe used");
                return blocked;
            }
        }
        return null;
    }
    private static Point point(float[] p){return new Point(p[0],p[2],p[1]);}
    private static boolean finite(Point p){return Float.isFinite(p.x())&&Float.isFinite(p.y())&&Float.isFinite(p.z());}
    private static double distance(Point a,Point b){return Math.sqrt(Math.pow(a.x()-b.x(),2)+Math.pow(a.y()-b.y(),2)+Math.pow(a.z()-b.z(),2));}
}
