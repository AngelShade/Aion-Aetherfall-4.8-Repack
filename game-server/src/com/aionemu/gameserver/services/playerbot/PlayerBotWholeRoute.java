package com.aionemu.gameserver.services.playerbot;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.LongAdder;

import org.recast4j.detour.DefaultQueryFilter;
import org.recast4j.detour.MeshTile;
import org.recast4j.detour.NavMeshQuery;
import org.recast4j.detour.Poly;

import com.aionemu.gameserver.controllers.movement.PlayerBotMoveController.WalkPoint;
import com.aionemu.gameserver.controllers.movement.PlayerBotMoveController.WalkStatus;
import com.aionemu.gameserver.services.playerbot.PlayerBotNavigation.Point;

/** Optional whole-trip ground corridor. The mover, rather than repeated AI orders,
 * consumes its corners. No learned routes, learned costs, or segmented journey state. */
final class PlayerBotWholeRoute implements AutoCloseable {
    enum Result { WAITING, MOVING, ARRIVED, FAILED, FALLBACK,
        /** A different goal that is already known unreachable was asked for while a live route exists: that route is kept untouched. */
        REFUSED }
    /** The offline proof uses this exact planner/delivery owner with real native
     * ground walking; it does not construct World, players, or movement tasks. */
    interface Environment {
        int world(); int instance(); Point position(); float minimumZ();
        Point ground(Point point);
        boolean safe(Point from,Point to);
        boolean reachable(Point from,Point to);
        WalkStatus walkStatus();
        boolean publish(long generation,List<WalkPoint> points);
        long nextGeneration();
        void abort(long generation);
        default boolean recentlyUnreachable(Point start,Point goal){return PlayerBotRouteSearch.recentlyUnreachable(world(),start,goal);}
        default void failed(Point start,Point goal){PlayerBotRouteSearch.failed(world(),start,goal);}
        default void succeeded(Point start,Point goal){PlayerBotRouteSearch.succeeded(world(),start,goal);}
    }
    private static final int MAX_NODES=32768,MAX_EXPANSIONS=65536,MAX_CORNERS=8192,SLICE_EXPANSIONS=4096,SLICE_CORNERS=256,MAX_DISPLACEMENTS=8;
    /** A failed goal may be asked for again later (a hazard passes, a door opens); real dead ends stay refused by the unreachable memory. */
    private static final long FAILURE_RETRY_MS=10000;
    private long failedAt;
    /** No more than this many fresh plans per bot inside the window; a bot whose orders keep changing waits instead of searching the map again. */
    private static final int PLAN_BURST=4;
    private static final long PLAN_WINDOW_MS=10000;
    /** A new goal closer than this to the current one, asked while the bot is still farther than that from it, does not start a new trip. */
    private static final double DRIFT=15;
    private long planWindowAt;
    private int planWindowCount;
    /** Destinations the ground map cannot bring a walker close to, shared by every bot: each is searched once in a quarter hour,
     * not once per bot every two minutes. A goal with no ground under it is remembered for any start; a route that ended short is
     * remembered only for starts in the same 64 m cell, because another part of the map may still reach it. */
    private static final java.util.concurrent.ConcurrentHashMap<String,Long> OFF_MAP=new java.util.concurrent.ConcurrentHashMap<>();
    private static final long OFF_MAP_MS=900000;
    private static String goalKey(int world,Point goal){return world+":"+Math.round(goal.x())+":"+Math.round(goal.y())+":"+Math.round(goal.z());}
    private static String startKey(Point from){return "@"+(int)Math.floor(from.x()/64)+":"+(int)Math.floor(from.y()/64);}
    private static boolean offMap(int world,Point from,Point goal){
        long now=System.currentTimeMillis();String key=goalKey(world,goal);
        Long any=OFF_MAP.get(key),here=OFF_MAP.get(key+startKey(from));
        return any!=null && any>now || here!=null && here>now;
    }
    private void rememberOffMap(Point from){
        if(OFF_MAP.size()>20000)OFF_MAP.clear();
        OFF_MAP.put(goalKey(world,goal)+(from==null?"":startKey(from)),System.currentTimeMillis()+OFF_MAP_MS);
    }
    private static final StackWalker WALKER=StackWalker.getInstance();
    private static String caller(){
        return WALKER.walk(frames->frames.map(f->{String c=f.getClassName();return c.substring(c.lastIndexOf('.')+1)+"."+f.getMethodName();})
            .filter(n->!n.startsWith("PlayerBotWholeRoute") && !n.startsWith("PlayerBotNavigation") && !n.startsWith("PlayerBotWorldTravel")).findFirst().orElse("unknown"));
    }
    /** Places where the walking map shows ground and the game has no floor at all. Learned from blocked steps,
     * shared by every bot on the map and kept for the server run, so later routes are planned around them. */
    private static final class Holes {
        private final List<Point> points=new ArrayList<>();
        private final java.util.Set<Long> refs=java.util.concurrent.ConcurrentHashMap.newKeySet();
        private java.lang.ref.WeakReference<Object> mesh=new java.lang.ref.WeakReference<>(null);
        private int resolved;
        synchronized boolean add(Point hole){
            if(points.size()>=MAX_HOLES)return false;
            for(Point p:points)if(horizontal(p,hole)<1 && Math.abs(p.z()-hole.z())<=2.5)return false;
            points.add(hole);return true;
        }
        /** Polygon ids belong to one loaded map; a reloaded map resolves the remembered places again. */
        synchronized java.util.Set<Long> blocked(Object loaded,NavMeshQuery query){
            if(mesh.get()!=loaded){mesh=new java.lang.ref.WeakReference<>(loaded);refs.clear();resolved=0;}
            var any=new DefaultQueryFilter();
            for(;resolved<points.size();resolved++){
                var near=query.findNearestPoly(position(points.get(resolved)),new float[]{.3f,3,.3f},any);
                if(!near.failed() && near.result.getNearestRef()!=0)refs.add(near.result.getNearestRef());
            }
            return refs;
        }
        synchronized int size(){return points.size();}
    }
    private static final int MAX_HOLES=4096;
    private static final double HOLE_REACH=4,HOLE_STEP=.35;
    private static final java.util.concurrent.ConcurrentHashMap<Integer,Holes> HOLES=new java.util.concurrent.ConcurrentHashMap<>();
    static int holes(int world){var holes=HOLES.get(world);return holes==null?0:holes.size();}
    /** After a blocked step: the first place within four metres toward the next corner where the game has no floor is remembered. */
    private void learnHole(Point at,Point corner){
        double length=horizontal(at,corner);if(length<.05)return;
        for(double walked=HOLE_STEP;walked<=Math.min(length,HOLE_REACH)+HOLE_STEP;walked+=HOLE_STEP){
            float t=(float)Math.min(1,walked/length);
            Point probe=new Point(at.x()+(corner.x()-at.x())*t,at.y()+(corner.y()-at.y())*t,at.z()+(corner.z()-at.z())*t);
            if(env.ground(probe)==null){
                if(HOLES.computeIfAbsent(world,k->new Holes()).add(probe)){
                    count("learned a hole in the game floor (map "+world+")");
                    org.slf4j.LoggerFactory.getLogger(PlayerBotWholeRoute.class).info("Playerbot floor hole learned: map {} at {} {} {} (bot stood at {} {} {}); routes now go around it",world,probe.x(),probe.y(),probe.z(),at.x(),at.y(),at.z());
                }
                return;
            }
            if(t>=1)return;
        }
    }
    private static final double ARRIVAL=.15,REJOIN=3,LOOKAHEAD=48;
    private static final LongAdder PLANNED=new LongAdder(),ARRIVED=new LongAdder(),NEAR_END=new LongAdder(),BLOCK_REPLANS=new LongAdder(),FAILED=new LongAdder();
    /** Why routes were dropped, re-planned or failed, so a live log can be read without a debugger. */
    private static final java.util.concurrent.ConcurrentHashMap<String,LongAdder> REASONS=new java.util.concurrent.ConcurrentHashMap<>();
    /** A trip that is being planned or walked on the current map: not finished, not failed, not handed to the older navigator. */
    boolean live(){return initialized && !failed && !arrived && !fallback && goal!=null && world==env.world() && instance==env.instance();}
    Point goal(){return goal;}
    static void note(String reason){count(reason);}
    private static void count(String reason){if(REASONS.size()<256 || REASONS.containsKey(reason))REASONS.computeIfAbsent(reason,k->new LongAdder()).increment();}
    static String summary(){
        var text=new StringBuilder("Whole-route navigation: planned="+PLANNED.sum()+", arrived="+ARRIVED.sum()+", near-end="+NEAR_END.sum()+", block-replans="+BLOCK_REPLANS.sum()+", failed="+FAILED.sum());
        REASONS.entrySet().stream().sorted((a,b)->Long.compare(b.getValue().sum(),a.getValue().sum())).limit(16).forEach(e->text.append("; ").append(e.getValue().sum()).append(" x ").append(e.getKey()));
        return text.toString();
    }
    private final Environment env;
    private final PlayerBotNavmeshStore store;
    private final PlayerBotMeshRoute.Admission admission;
    private final PlayerBotNavigationBudget budget;
    private final Object token=new Object();
    private PlayerBotNavmeshStore.Request full,topology;
    private NavMeshQuery query;
    private Point goal,start,requestStart,endpoint,firstBlock;
    private int world,instance,cursor,expanded,proofCursor,displacements;
    private long generation=-1;
    private boolean initialized,queryStarted,near,paused,ownsWalk,failed,arrived,fallback,retained,complete;
    private List<Point> corners=List.of(),candidates=List.of();
    private String status="whole route ready";
    private int plans,generations;
    private double plannedLength;

    PlayerBotWholeRoute(Environment env,PlayerBotNavmeshStore store,PlayerBotMeshRoute.Admission admission,PlayerBotNavigationBudget budget){
        this.env=env;this.store=store;this.admission=admission;this.budget=budget;
    }
    boolean ownsWalk(){return ownsWalk;}
    boolean planning(){return initialized && !paused && !failed && !arrived && !fallback && corners.isEmpty();}
    boolean waiting(){return planning();}
    boolean nominated(){return budget.nominated(token);}
    String status(){return status;}
    int plans(){return plans;}
    int generations(){return generations;}
    double plannedLength(){return plannedLength;}
    Point endpoint(){return endpoint;}
    boolean nearEnd(Point requested){return matches(requested) && arrived && near && !failed;}
    boolean terminal(Point requested){return matches(requested) && arrived && !failed && endpoint!=null && horizontal(env.position(),endpoint)<=.3 && Math.abs(env.position().z()-endpoint.z())<=2;}
    private boolean matches(Point requested){return initialized && world==env.world() && instance==env.instance() && distance(goal,requested)<.25;}
    boolean interactionFailed(Point requested){
        if(!matches(requested) || !arrived)return false;
        fail("whole route endpoint cannot reach the requested interaction");return true;
    }

    Result move(Point requested){
        Point at=env.position();
        if(initialized && failed && System.currentTimeMillis()-failedAt>=FAILURE_RETRY_MS)discard();
        if(!matches(requested) && initialized && !failed && !arrived && !fallback && world==env.world() && instance==env.instance()){
            // A followed leader or a road point that moved a little while the bot is still far away is the same trip:
            // keep the route and retarget only once the bot is near its end.
            double shift=distance(goal,requested);
            if(shift<DRIFT && distance(at,goal)>Math.max(DRIFT,3*shift)){count("kept route: goal drifted while the bot was still far");requested=goal;}
        }
        if(!matches(requested) && initialized && !failed && !arrived && !fallback && world==env.world() && instance==env.instance() && env.recentlyUnreachable(at,requested)){
            // One task asking for a dead end must not destroy the trip another task has in flight.
            count("refused known dead end, kept the live route; asked by "+caller());return Result.REFUSED;
        }
        if(!matches(requested)){
            if(initialized && world==env.world() && instance==env.instance()){
                double shift=distance(goal,requested);String state=failed?"failed":arrived?"arrived":fallback?"legacy":corners.isEmpty()?"planning":"walking";
                count("new goal while "+state+(shift<3?", moved under 3 m":shift<15?", moved 3-15 m":", elsewhere")+"; asked by "+caller());
            }
            discard();initialized=true;world=env.world();instance=env.instance();goal=requested;start=requestStart=at;firstBlock=null;displacements=0;
            if(env.recentlyUnreachable(start,goal)){fail("whole route recently unreachable");return Result.FAILED;}
            if(offMap(world,start,goal)){fail("whole route destination already found off the walkable map",false);return Result.FAILED;}
        }
        if(failed)return Result.FAILED;
        if(fallback)return Result.FALLBACK;
        if(arrived){
            if(terminal(requested))return Result.ARRIVED;
            arrived=false;replan(at,"replanning whole route after arrival displacement");
        }
        if(corners.isEmpty()){paused=false;return plan();}
        var walk=env.walkStatus();
        if(walk.generation()==generation){
            acknowledge(walk);
            if(walk.blocked()){
                ownsWalk=false;
                if(cursor<corners.size())learnHole(at,corners.get(cursor));
                if(firstBlock!=null && horizontal(firstBlock,at)<=3 && Math.abs(firstBlock.z()-at.z())<=2.5){fail("whole route blocked twice at the same ground step");return Result.FAILED;}
                firstBlock=at;env.failed(requestStart,goal);BLOCK_REPLANS.increment();replan(at,"replanning whole route after blocked native step");return Result.WAITING;
            }
        }
        if(cursor>=corners.size() || horizontal(at,endpoint)<=ARRIVAL && Math.abs(at.z()-endpoint.z())<=2){
            arrive();return Result.ARRIVED;
        }
        if(paused || !ownsWalk || walk.generation()!=generation || !walk.active()){
            // Only move forward along the retained corridor. Projection handles
            // an interruption between acknowledgments without revisiting a corner.
            if(!rejoin(at)){
                if(displacements++>=MAX_DISPLACEMENTS){fail("whole route displaced twice from its ground corridor");return Result.FAILED;}
                replan(at,"replanning whole route after displacement");return Result.WAITING;
            }
            paused=false;generation=env.nextGeneration();generations++;ownsWalk=true;
        }
        if(!env.safe(at,corners.get(cursor))){fail("whole route interrupted by current hazard",false);return Result.FAILED;}
        if(!env.publish(generation,tail(at))){
            var after=env.walkStatus();ownsWalk=after.generation()==generation && after.blocked();paused=true;
            status="whole route waiting for native movement admission";return Result.WAITING;
        }
        status="walking whole route";return Result.MOVING;
    }

    /** Stop/rest/cast releases physical ownership while retaining a copied route.
     * An unfinished query is released and restarted only when the same goal resumes. */
    void pause(){
        if(!initialized)return;
        if(ownsWalk){acknowledge(env.walkStatus());env.abort(generation);}ownsWalk=false;paused=true;
        if(corners.isEmpty()){releaseQuery();candidates=List.of();proofCursor=expanded=0;}
        budget.cancel(token);status="whole route paused for native action";
    }
    private void replan(Point at,String why){
        if(ownsWalk)env.abort(generation);ownsWalk=false;releaseQuery();budget.cancel(token);corners=List.of();candidates=List.of();cursor=proofCursor=expanded=0;
        start=at;endpoint=null;near=false;queryStarted=false;generation=-1;status=why;count(why+" (map "+world+")");
    }
    private Result plan(){
        var priority=horizontal(start,goal)<=30?PlayerBotNavigationBudget.Priority.NEAR:PlayerBotNavigationBudget.Priority.WORLD;
        // Loading owns demand only; never reserve a terrain worker while waiting on disk.
        if(full==null){
            if(!admission.demand(token)){status="whole route waiting for map admission";return Result.WAITING;}
            full=store.requestFull(world);topology=store.requestTopology(world);
        }
        var map=full.poll();var graph=topology.poll();
        if(map.status()==PlayerBotNavmeshStore.Status.WAITING || graph.status()==PlayerBotNavmeshStore.Status.WAITING){status="whole route waiting for authenticated map";return Result.WAITING;}
        if(map.status()!=PlayerBotNavmeshStore.Status.READY || graph.status()!=PlayerBotNavmeshStore.Status.READY){
            useLegacy("whole route map unavailable");return Result.FALLBACK;
        }
        if(!retained){if(!admission.retain(token)){status="whole route waiting for query memory";return Result.WAITING;}retained=true;}
        if(!admission.begin(token)){status="whole route waiting for calculation turn";return Result.WAITING;}
        try(var lease=budget.begin(token,priority)){
            if(lease==null){status="whole route waiting for terrain turn";return Result.WAITING;}
            long deadline=lease.deadlineNanos();
            if(query==null){
                long now=System.currentTimeMillis();
                if(now-planWindowAt>=PLAN_WINDOW_MS){planWindowAt=now;planWindowCount=0;}
                if(planWindowCount>=PLAN_BURST){count("plan held: too many fresh plans for one bot");status="whole route waiting: orders changed too often";budget.request(token,priority);return Result.WAITING;}
                planWindowCount++;
                query=new NavMeshQuery(map.lease().mesh());admission.releaseDemand(token);
                final NavMeshQuery bounded=query;float minimum=env.minimumZ();
                var known=HOLES.get(world);final java.util.Set<Long> holes=known==null?java.util.Set.of():known.blocked(map.lease().mesh(),query);
                var filter=new DefaultQueryFilter(){
                    @Override public boolean passFilter(long ref,MeshTile tile,Poly poly){
                        var nodes=bounded.getNodePool().getNodeMap();
                        return super.passFilter(ref,tile,poly) && !holes.contains(ref) && PlayerBotWater.polygon(tile.data.verts,poly.verts,poly.vertCount,minimum)
                            && (nodes.containsKey(ref) || nodes.size()<MAX_NODES);
                    }
                };
                Point grounded=env.ground(start),destination=env.ground(goal);
                var a=query.findNearestPoly(position(grounded==null?start:grounded),new float[]{2,2.5f,2},filter);
                var b=query.findNearestPoly(position(destination==null?goal:destination),new float[]{2,2.5f,2},filter);
                if(b.failed() || b.result.getNearestRef()==0)b=query.findNearestPoly(position(destination==null?goal:destination),new float[]{6,2.5f,6},filter);
                if(a.failed() || a.result.getNearestRef()==0){useLegacy("whole route origin not on ground map");return Result.FALLBACK;}
                if(b.failed() || b.result.getNearestRef()==0){rememberOffMap(null);fail("whole route goal has no ground polygon within six metres");return Result.FAILED;}
                Point resolved=point(b.result.getNearestPos());
                if(!nearGoal(resolved,goal)){rememberOffMap(null);fail("whole route goal has no nearby ground polygon");return Result.FAILED;}
                var topology=graph.lease().topology();int ac=topology.component(map.lease().mesh(),a.result.getNearestRef()),bc=topology.component(map.lease().mesh(),b.result.getNearestRef());
                // A separate substantial floor may require the existing jump/lift
                // journey. A tiny goal island instead admits the nearest reachable end.
                if(topology.usableGround(ac) && topology.usableGround(bc) && topology.groundRegion(ac)!=topology.groundRegion(bc)){
                    useLegacy("whole route endpoints need another ground region");return Result.FALLBACK;
                }
                projectedStart=a.result.getNearestPos();projectedGoal=b.result.getNearestPos();goalRef=b.result.getNearestRef();
                var result=query.initSlicedFindPath(a.result.getNearestRef(),goalRef,projectedStart,projectedGoal,filter,0);
                if(result.isFailed()){fail("whole route path initialization failed");return Result.FAILED;}
                queryStarted=true;plans++;PLANNED.increment();status="planning whole ground route";
            }
            if(candidates.isEmpty() && queryStarted){
                int work=0;
                while(work<SLICE_EXPANSIONS && System.nanoTime()<deadline){
                    var update=query.updateSlicedFindPath(Math.min(8,SLICE_EXPANSIONS-work));int used=update.result==null?1:Math.max(1,update.result);work+=used;expanded+=used;
                    if(update.failed()){fail("whole route path query failed");return Result.FAILED;}
                    if(update.status.isInProgress() && expanded<MAX_EXPANSIONS && query.getNodePool().getNodeMap().size()<MAX_NODES)continue;
                    var corridor=query.finalizeSlicedFindPath();
                    if(corridor.failed() || corridor.result.isEmpty()){fail("whole route has no reachable corridor");return Result.FAILED;}
                    float[] end=projectedGoal;complete=corridor.result.getLast()==goalRef;
                    if(!complete){var closest=query.closestPointOnPoly(corridor.result.getLast(),end);if(closest.failed()){fail("whole route partial endpoint unavailable");return Result.FAILED;}end=closest.result.getClosest();}
                    endpoint=point(end);near=!complete || horizontal(endpoint,goal)>.15 || Math.abs(endpoint.z()-goal.z())>2;
                    if(!nearGoal(endpoint,goal)){rememberOffMap(start);fail("whole route ends too far from destination");return Result.FAILED;}
                    var straight=query.findStraightPath(projectedStart,end,corridor.result,MAX_CORNERS,NavMeshQuery.DT_STRAIGHTPATH_ALL_CROSSINGS);
                    if(straight.failed() || straight.result.isEmpty() || straight.result.size()>=MAX_CORNERS
                        || (straight.result.getLast().getFlags() & NavMeshQuery.DT_STRAIGHTPATH_END)==0){fail("whole route corner capacity or extraction failed");return Result.FAILED;}
                    candidates=new ArrayList<>();Point previous=start;
                    for(var corner:straight.result){
                        Point next=point(corner.getPos());int pieces=Math.max(1,(int)Math.ceil(horizontal(previous,next)/16));
                        if(candidates.size()+pieces>MAX_CORNERS){fail("whole route delivery point capacity reached");return Result.FAILED;}
                        for(int piece=1;piece<=pieces;piece++){float t=(float)piece/pieces;candidates.add(new Point(previous.x()+(next.x()-previous.x())*t,previous.y()+(next.y()-previous.y())*t,previous.z()+(next.z()-previous.z())*t));}
                        previous=next;
                    }
                    queryStarted=false;break;
                }
            }
            if(!candidates.isEmpty()){
                // Floor normalization is native and charged, at most 16 corners
                // per callback. Neither this nor extraction scans the world.
                for(int work=0;proofCursor<candidates.size() && work<SLICE_CORNERS && System.nanoTime()<deadline;work++,proofCursor++){
                    Point p=candidates.get(proofCursor),ground=env.ground(p);if(ground!=null)p=ground;
                    candidates.set(proofCursor,p);
                    Point from=proofCursor==0?start:candidates.get(proofCursor-1);
                    if(!env.safe(from,p)){fail("whole route crosses a current hazard",false);return Result.FAILED;}
                }
                if(proofCursor==candidates.size()){
                    var route=new ArrayList<>(candidates);
                    while(!route.isEmpty() && horizontal(start,route.getFirst())<=ARRIVAL && Math.abs(start.z()-route.getFirst().z())<=2)route.removeFirst();
                    // Replace the final projected point rather than append a tiny
                    // backtrack. Only a fresh native ground edge admits exact XY.
                    Point exact=env.ground(goal);Point previous=route.size()>1?route.get(route.size()-2):start;
                    if(complete && exact!=null && nearGoal(exact,goal) && horizontal(exact,goal)<=.01 && env.safe(previous,exact) && env.reachable(previous,exact)){
                        if(route.isEmpty())route.add(exact);else route.set(route.size()-1,exact);near=false;
                    }
                    endpoint=route.isEmpty()?start:route.getLast();
                    if(!nearGoal(endpoint,goal)){fail("whole route normalized endpoint too far from destination");return Result.FAILED;}
                    plannedLength=0;Point last=start;for(Point p:route){plannedLength+=horizontal(last,p);last=p;}
                    corners=List.copyOf(route);candidates=List.of();releaseQuery();budget.cancel(token);paused=true;
                    if(corners.isEmpty()){arrive();return Result.ARRIVED;}
                    return move(goal);
                }
            }
        }finally{admission.end(token);}
        budget.request(token,priority);return Result.WAITING;
    }
    private float[] projectedStart,projectedGoal;
    private long goalRef;
    private List<WalkPoint> tail(Point at){
        var result=new ArrayList<WalkPoint>();Point previous=at;double distance=0;
        for(int i=cursor;i<corners.size() && result.size()<64;i++){
            Point p=corners.get(i);double edge=horizontal(previous,p);
            // Long straight edges are safe mover destinations. The tail beyond
            // them is withheld until it enters the regular lookahead horizon.
            if(!result.isEmpty() && distance+edge>LOOKAHEAD)break;
            result.add(new WalkPoint(i,p.x(),p.y(),p.z()));distance+=edge;previous=p;
        }
        return result;
    }
    private boolean rejoin(Point at){
        // Search only the remaining corridor; a passed corner can never return.
        // Closest forward segment handles push/displacement across a corner.
        int best=-1;double nearest=Double.POSITIVE_INFINITY;
        Point previous=cursor==0?start:corners.get(cursor-1);
        for(int i=cursor;i<corners.size();i++){
            Point next=corners.get(i);double dx=next.x()-previous.x(),dy=next.y()-previous.y(),length=dx*dx+dy*dy;
            double t=length<.0001?1:Math.max(0,Math.min(1,((at.x()-previous.x())*dx+(at.y()-previous.y())*dy)/length));
            double z=previous.z()+(next.z()-previous.z())*t;
            double separation=Math.hypot(at.x()-(previous.x()+dx*t),at.y()-(previous.y()+dy*t));
            if(Math.abs(at.z()-z)<=2.5 && separation<nearest){nearest=separation;best=i;}
            if(i==cursor && nearest<=.3)break;
            previous=next;
        }
        if(best<0 || nearest>REJOIN)return false;
        // A near corner is dropped only when physically there; projections never
        // cut from the preceding segment across the inside of a turn.
        cursor=Math.max(cursor,best);
        while(cursor<corners.size()-1 && horizontal(at,corners.get(cursor))<=ARRIVAL && Math.abs(at.z()-corners.get(cursor).z())<=2)cursor++;
        return true;
    }
    private void arrive(){
        if(!arrived){arrived=true;ARRIVED.increment();if(near)NEAR_END.increment();env.succeeded(requestStart,goal);}
        if(ownsWalk)env.abort(generation);ownsWalk=false;status=near?"at nearest reachable whole-route endpoint":"at whole-route destination";
    }
    private void fail(String why){fail(why,true);}
    /** A passing hazard is not a dead end: it must not teach the unreachable memory. */
    private void fail(String why,boolean unreachable){
        if(!failed){failed=true;failedAt=System.currentTimeMillis();FAILED.increment();count("failed: "+why+" (map "+world+")");if(unreachable)env.failed(requestStart,goal);}
        if(ownsWalk)env.abort(generation);ownsWalk=false;releaseQuery();budget.cancel(token);status=why;
    }
    private void useLegacy(String why){if(!fallback)count("legacy: "+why);fallback=true;releaseQuery();budget.cancel(token);status=why;}
    private void releaseQuery(){
        query=null;queryStarted=false;
        if(full!=null){full.close();full=null;}if(topology!=null){topology.close();topology=null;}
        admission.releaseDemand(token);if(retained){admission.releaseMemory(token);retained=false;}admission.cancel(token);
    }
    private void discard(){
        if(ownsWalk)env.abort(generation);releaseQuery();budget.cancel(token);initialized=ownsWalk=failed=arrived=fallback=near=paused=false;
        corners=List.of();candidates=List.of();cursor=expanded=proofCursor=0;generation=-1;goal=start=endpoint=null;
    }
    @Override public void close(){discard();}
    private void acknowledge(WalkStatus walk){if(walk.generation()==generation)while(cursor<corners.size() && cursor<=walk.reachedIndex())cursor++;}
    private static float[] position(Point p){return new float[]{p.x(),p.z(),p.y()};}
    private static Point point(float[] p){return new Point(p[0],p[2],p[1]);}
    private static double horizontal(Point a,Point b){return Math.hypot(a.x()-b.x(),a.y()-b.y());}
    private static double distance(Point a,Point b){return a==null || b==null?Double.POSITIVE_INFINITY:Math.hypot(horizontal(a,b),a.z()-b.z());}
    private static boolean nearGoal(Point end,Point goal){return horizontal(end,goal)<=6 && Math.abs(end.z()-goal.z())<=2.5;}
}
