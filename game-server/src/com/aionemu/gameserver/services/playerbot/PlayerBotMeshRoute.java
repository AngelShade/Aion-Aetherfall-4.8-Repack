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
    private static Runtime runtime;
    private static Admission sharedAdmission;
    static synchronized Admission admission(){
        if(sharedAdmission==null)sharedAdmission=new Admission(PlayerBotConfig.NAVMESH_MAX_QUERIES);
        return sharedAdmission;
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
        final Semaphore slots;
        final Admission admission;
        final PlayerBotNavmeshStore store;
        final Set<Integer> reported=java.util.concurrent.ConcurrentHashMap.newKeySet();
        Runtime() {
            int memory=PlayerBotConfig.NAVMESH_MEMORY_MB,queries=PlayerBotConfig.NAVMESH_MAX_QUERIES;
            if(memory<64 || memory>4096 || queries<1 || queries>256)throw new IllegalArgumentException("Invalid playerbot mesh memory/query limits");
            admission=admission();slots=admission.slots;
            store=new PlayerBotNavmeshStore(Path.of("data/navmesh"),memory*1024L*1024L,
                task->ThreadPoolManager.getInstance().executeLongRunning(task));
        }
    }
    private static synchronized Runtime runtime(){if(runtime==null)runtime=new Runtime();return runtime;}
    static synchronized void shutdown(){if(runtime!=null)runtime.store.close();}
    private final int world;
    private final Point start,goal;
    private Point target;
    private final PlayerBotMeshJourney journey;
    private final boolean ownsJourney;
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
    private double provedDistance;
    private String reason="waiting for prepared map";
    PlayerBotMeshRoute(int world,Point start,Point goal,float horizon){this(world,start,goal,horizon,null);}
    PlayerBotMeshRoute(int world,Point start,Point goal,float horizon,PlayerBotMeshJourney journey){
        this.world=world;this.start=start;this.goal=goal;this.target=goal;this.horizon=horizon;proofOrigin=start;
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
        if(host==null)host=runtime();
        // Short local movement has no cross-window journey to resolve. Long
        // travel owns the component itinerary; combat/local steering stays local.
        boolean journeyNeeded=coarseRequired || distance(start,goal)>horizon;
        if(journeyNeeded && !journey.ready()){
            used=journey.advance(host.store,start,allowance,expired,host.admission);work+=used;reason=journey.reason();
            if(!journey.ready() || used>0)return;
        }
        target=journeyNeeded?journey.target(start):goal;
        if(query==null && candidates.isEmpty()) {
            if(!host.admission.demand(this)){reason="waiting for prepared window admission";return;}
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
            if(!host.admission.retain(this)){reason="waiting for prepared search memory";return;}
        }
        if(candidates.isEmpty()){
            if(expired.getAsBoolean())return;
            if(!host.admission.begin(this)){reason="waiting for prepared calculation turn";return;}
            try {used=advanceGraph(allowance,expired,used,journeyNeeded);}
            finally {host.admission.end(this);}
        }
        while(!candidates.isEmpty() && !done && used<allowance && !expired.getAsBoolean()){
            if(cursor>=candidates.size()){finish();break;}
            Point target=candidates.get(cursor);double xy=Math.hypot(target.x()-proofOrigin.x(),target.y()-proofOrigin.y());
            if(xy<.05 && Math.abs(target.z()-proofOrigin.z())<.75){cursor++;continue;}
            float fraction=(float)Math.min(1,Math.min(4,Math.max(.05,horizon-provedDistance))/Math.max(.001,xy));
            float x=proofOrigin.x()+(target.x()-proofOrigin.x())*fraction,y=proofOrigin.y()+(target.y()-proofOrigin.y())*fraction;
            Point actual=ground.step(proofOrigin,x,y);work++;used++;
            if(actual==null || !finite(actual) || Math.hypot(actual.x()-x,actual.y()-y)>.15
                    || fraction==1 && Math.abs(actual.z()-target.z())>.75){
                // Dynamic obstacles remain native authority. Replan using live collision geometry.
                useNative("prepared corridor blocked by current native terrain");return;
            }
            double travelled=distance(proofOrigin,actual);
            if(travelled<.01){useNative("prepared corridor made no native progress");return;}
            provedDistance+=travelled;proved.add(actual);proofOrigin=actual;
            if(fraction==1)cursor++;
            if(provedDistance>=horizon || cursor>=candidates.size())finish();
        }
    }
    private int advanceGraph(int allowance,BooleanSupplier expired,int used,boolean journeyNeeded){
        if(query==null){
            query=new NavMeshQuery(lease.mesh());host.admission.releaseDemand(this);
            final NavMeshQuery boundedQuery=query;
            var filter=new DefaultQueryFilter(){
                @Override public boolean passFilter(long ref,MeshTile tile,Poly poly){
                    // Sliced options=0 creates only state-0 nodes immediately after this predicate.
                    // Keep existing nodes traversable, but reject new allocations at the actor limit.
                    var nodes=boundedQuery.getNodePool().getNodeMap();
                    return super.passFilter(ref,tile,poly) && (nodes.containsKey(ref) || nodes.size()<MAX_NODES);
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
        journey.provedSegment(start,proofOrigin,target);
        done=true;reason=partial?"native-proved prepared route segment":"native-proved prepared route";releaseGraph();
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
            journey.provedSegment(start,proofOrigin,target);
            reason="native-proved prepared prefix before obstruction";
            candidates=List.of();releaseGraph();return;
        }
        fallback=true;done=true;reason=why;proved.clear();candidates=List.of();releaseGraph();
    }
    private void releaseSlot(){if(host!=null)host.admission.cancel(this);}
    private void releaseGraph(){query=null;if(request!=null){request.close();request=null;}if(lease!=null){lease.close();lease=null;}releaseSlot();}
    @Override public void close(){releaseGraph();if(ownsJourney)journey.close();done=true;}
    private static float[] position(Point p){return new float[]{p.x(),p.z(),p.y()};}
    private static Point point(float[] p){return new Point(p[0],p[2],p[1]);}
    private static boolean finite(Point p){return Float.isFinite(p.x())&&Float.isFinite(p.y())&&Float.isFinite(p.z());}
    private static double distance(Point a,Point b){return Math.sqrt(Math.pow(a.x()-b.x(),2)+Math.pow(a.y()-b.y(),2)+Math.pow(a.z()-b.z(),2));}
}
